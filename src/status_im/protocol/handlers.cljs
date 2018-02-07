(ns status-im.protocol.handlers
  (:require [re-frame.core :as re-frame]
            [cljs.core.async :as async]
            [status-im.utils.handlers :as handlers]
            [status-im.data-store.contacts :as contacts]
            [status-im.data-store.messages :as messages]
            [status-im.data-store.pending-messages :as pending-messages]
            [status-im.data-store.processed-messages :as processed-messages]
            [status-im.data-store.chats :as chats]
            [status-im.constants :as constants]
            [status-im.i18n :as i18n]
            [status-im.utils.random :as random]
            [status-im.utils.async :as async-utils]
            [status-im.transport.message-cache :as message-cache]
            [status-im.chat.models :as models.chat]
            [status-im.transport.inbox :as inbox]
            [status-im.utils.datetime :as datetime]
            [taoensso.timbre :as log]
            [status-im.native-module.core :as status]
            [clojure.string :as string]
            [status-im.utils.web3-provider :as web3-provider]
            [status-im.utils.ethereum.core :as utils]
            [cljs.reader :as reader]))

;;;; COFX

(re-frame/reg-cofx
  ::get-web3
  (fn [coeffects _]
    (assoc coeffects :web3 (web3-provider/make-web3))))

(re-frame/reg-cofx
  ::get-chat-groups
  (fn [coeffects _]
    (assoc coeffects :groups (chats/get-active-group-chats))))

(re-frame/reg-cofx
  ::get-pending-messages
  (fn [coeffects _]
    (assoc coeffects :pending-messages (pending-messages/get-all))))

;;;; FX

(def ^:private protocol-realm-queue (async-utils/task-queue 2000))

(re-frame/reg-fx
  ::web3-get-syncing
  (fn [web3]
    (when web3
      (.getSyncing
       (.-eth web3)
       (fn [error sync]
         (re-frame/dispatch [:update-sync-state error sync]))))))

(re-frame/reg-fx
  ::save-processed-messages
  (fn [processed-message]
    (async/go (async/>! protocol-realm-queue #(processed-messages/save processed-message)))))

(defn system-message [message-id timestamp content]
  {:from         "system"
   :message-id   message-id
   :timestamp    timestamp
   :content      content
   :content-type constants/text-content-type})


(re-frame/reg-fx
  ::chats-add-contact
  (fn [[group-id identity]]
    (chats/add-contacts group-id [identity])))

(re-frame/reg-fx
  ::chats-remove-contact
  (fn [[group-id identity]]
    (chats/remove-contacts group-id [identity])))

(re-frame/reg-fx
  ::status-init-jail
  (fn []
    (status/init-jail)))

(re-frame/reg-fx
  ::load-processed-messages!
  (fn []
    (let [now      (datetime/now-ms)
          messages (processed-messages/get-filtered (str "ttl > " now))]
      (message-cache/init! messages)
      (processed-messages/delete (str "ttl <=" now)))))

(re-frame/reg-fx
  ::add-peer
  (fn [{:keys [wnode web3]}]
    (inbox/add-peer wnode
                    #(re-frame/dispatch [::add-peer-success web3 %])
                    #(re-frame/dispatch [::add-peer-error %]))))

(re-frame/reg-fx
  ::fetch-peers
  (fn [{:keys [wnode web3 retries]}]
    ;; Run immediately on first run, add delay before retry
    (let [delay (cond
                  (zero? retries) 0
                  (< retries 3)   300
                  (< retries 10)  1000
                  :else           5000)]
      (if (> retries 100)
        (log/error "Number of retries for fetching peers exceed" wnode)
        (js/setTimeout
          (fn [] (inbox/fetch-peers #(re-frame/dispatch [::fetch-peers-success web3 % retries])
                                    #(re-frame/dispatch [::fetch-peers-error %])))
          delay)))))

(re-frame/reg-fx
  ::mark-trusted-peer
  (fn [{:keys [wnode web3 peers]}]
    (inbox/mark-trusted-peer web3
                             wnode
                             peers
                             #(re-frame/dispatch [::mark-trusted-peer-success web3 %])
                             #(re-frame/dispatch [::mark-trusted-peer-error %]))))

(re-frame/reg-fx
  ::request-messages
  (fn [{:keys [wnode topic sym-key-id web3]}]
    (inbox/request-messages web3
                            wnode
                            topic
                            sym-key-id
                            #(re-frame/dispatch [::request-messages-success %])
                            #(re-frame/dispatch [::request-messages-error %]))))


;;;; Handlers

;; NOTE(dmitryn): events chain
;; add-peer -> fetch-peers -> mark-trusted-peer -> get-sym-key -> request-messages
(handlers/register-handler-fx
  :initialize-offline-inbox
  (fn [{:keys [db]} [_ web3]]
    (log/info "offline inbox: initialize")
    (let [wnode-id (get db :inbox/wnode)
          wnode    (get-in db [:inbox/wnodes wnode-id :address])]
      {::add-peer {:wnode wnode
                   :web3  web3}})))

(handlers/register-handler-fx
  ::add-peer-success
  (fn [{:keys [db]} [_ web3 response]]
    (let [wnode-id (get db :inbox/wnode)
          wnode    (get-in db [:inbox/wnodes wnode-id :address])]
      (log/info "offline inbox: add-peer response" wnode response)
      {::fetch-peers {:wnode wnode
                      :web3  web3
                      :retries 0}})))

(handlers/register-handler-fx
  ::fetch-peers-success
  (fn [{:keys [db]} [_ web3 peers retries]]
    (let [wnode-id (get db :inbox/wnode)
          wnode    (get-in db [:inbox/wnodes wnode-id :address])]
      (log/info "offline inbox: fetch-peers response" peers)
      (if (inbox/registered-peer? peers wnode)
        {::mark-trusted-peer {:wnode wnode
                              :web3  web3
                              :peers peers}}
        (do
          (log/info "Peer" wnode "is not registered. Retrying fetch peers.")
          {::fetch-peers {:wnode   wnode
                          :web3    web3
                          :retries (inc retries)}})))))

(handlers/register-handler-fx
  ::mark-trusted-peer-success
  (fn [{:keys [db]} [_ web3 response]]
    (let [wnode-id (get db :inbox/wnode)
          wnode    (get-in db [:inbox/wnodes wnode-id :address])
          password (:inbox/password db)]
      (log/info "offline inbox: mark-trusted-peer response" wnode response)
      {::get-sym-key {:password password
                      :web3     web3}})))



(handlers/register-handler-fx
  ::get-sym-key-success
  (fn [{:keys [db]} [_ web3 sym-key-id]]
    (log/info "offline inbox: get-sym-key response" sym-key-id)
    (let [wnode-id (get db :inbox/wnode)
          wnode    (get-in db [:inbox/wnodes wnode-id :address])
          topic    (:inbox/topic db)]
      {::request-messages {:wnode      wnode
                           :topic      topic
                           :sym-key-id sym-key-id
                           :web3       web3}})))

(handlers/register-handler-fx
  ::request-messages-success
  (fn [_ [_ response]]
    (log/info "offline inbox: request-messages response" response)))

(handlers/register-handler-fx
  ::add-peer-error
  (fn [_ [_ error]]
    (log/error "offline inbox: add-peer error" error)))

(handlers/register-handler-fx
  ::fetch-peers-error
  (fn [_ [_ error]]
    (log/error "offline inbox: fetch-peers error" error)))

(handlers/register-handler-fx
  ::mark-trusted-peer-error
  (fn [_ [_ error]]
    (log/error "offline inbox: mark-trusted-peer error" error)))

(handlers/register-handler-fx
  ::get-sym-key-error
  (fn [_ [_ error]]
    (log/error "offline inbox: get-sym-key error" error)))

(handlers/register-handler-fx
  ::request-messages-error
  (fn [_ [_ error]]
    (log/error "offline inbox: request-messages error" error)))

(handlers/register-handler-fx
  :handle-whisper-message
  (fn [_ [_ error msg options]]
    {::handle-whisper-message {:error error
                               :msg msg
                               :options options}}))

;;; INITIALIZE PROTOCOL
(handlers/register-handler-fx
  :initialize-protocol
  [re-frame/trim-v
   (re-frame/inject-cofx ::get-web3)
   (re-frame/inject-cofx ::get-chat-groups)
   (re-frame/inject-cofx ::get-pending-messages)
   (re-frame/inject-cofx :get-all-contacts)]
  (fn [{:keys [db web3 groups all-contacts pending-messages]} [current-account-id ethereum-rpc-url]]
    (let [{:keys [public-key]}
          (get-in db [:accounts/accounts current-account-id])]
      (when public-key
        {:transport/init-whisper {:web3 web3 :public-key public-key :groups groups
                                  :pending-messages pending-messages :contacts all-contacts}
         :db (assoc db :web3 web3
                    :rpc-url (or ethereum-rpc-url constants/ethereum-rpc-url))}))))

(handlers/register-handler-fx
  :load-processed-messages
  (fn [_ _]
    {::load-processed-messages! nil}))

;;; NODE SYNC STATE

(handlers/register-handler-db
  :update-sync-state
  (fn [{:keys [sync-state sync-data] :as db} [_ error sync]]
    (let [{:keys [highestBlock currentBlock] :as state}
          (js->clj sync :keywordize-keys true)
          syncing?  (> (- highestBlock currentBlock) constants/blocks-per-hour)
          new-state (cond
                      error :offline
                      syncing? (if (= sync-state :done)
                                 :pending
                                 :in-progress)
                      :else (if (or (= sync-state :done)
                                    (= sync-state :pending))
                              :done
                              :synced))]
      (cond-> db
        (when (and (not= sync-data state) (= :in-progress new-state)))
        (assoc :sync-data state)
        (when (not= sync-state new-state))
        (assoc :sync-state new-state)))))

(handlers/register-handler-fx
  :check-sync
  (fn [{{:keys [web3]} :db} _]
    {::web3-get-syncing web3
     :dispatch-later [{:ms 10000 :dispatch [:check-sync]}]}))

(handlers/register-handler-fx
  :initialize-sync-listener
  (fn [{{:keys [sync-listening-started network networks/networks] :as db} :db} _]
    (when (and (not sync-listening-started)
               (not (utils/network-with-upstream-rpc? networks network)))
      {:db (assoc db :sync-listening-started true)
       :dispatch [:check-sync]})))
