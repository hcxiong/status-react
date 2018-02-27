(ns status-im.protocol.handlers
  (:require [cljs.core.async :as async]
            [re-frame.core :as re-frame]
            [status-im.constants :as constants]
            [status-im.data-store.processed-messages :as processed-messages]
            [status-im.native-module.core :as status]
            [status-im.transport.message-cache :as message-cache]
            [status-im.utils.async :as async-utils]
            [status-im.utils.datetime :as datetime]
            [status-im.utils.ethereum.core :as utils]
            [status-im.utils.handlers :as handlers]
            [status-im.utils.web3-provider :as web3-provider]))

;;;; COFX
(re-frame/reg-cofx
  ::get-web3
  (fn [coeffects _]
    (assoc coeffects :web3 (web3-provider/make-web3))))

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

;;; INITIALIZE PROTOCOL
(handlers/register-handler-fx
  :initialize-protocol
  [re-frame/trim-v
   (re-frame/inject-cofx ::get-web3)
   (re-frame/inject-cofx :data-store/transport)]
  (fn [{:data-store/keys [transport] :keys [db web3]} [current-account-id ethereum-rpc-url]]
    (let [{:keys [public-key]} (get-in db [:accounts/accounts current-account-id])]
      (when public-key
        {;;TODO (yenda) remove once go implements persistence
         :shh/add-sym-keys {:web3 web3
                            :transport transport
                            :success-event ::sym-key-added}
         :transport/init-whisper {:web3       web3
                                  :public-key public-key
                                  :transport  transport}
         :db (assoc db
                    :web3 web3
                    :rpc-url (or ethereum-rpc-url constants/ethereum-rpc-url)
                    :transport/chats transport)}))))

;;TODO (yenda) remove once go implements persistence
(handlers/register-handler-fx
  ::sym-key-added
  (fn [{:keys [db]} [_ {:keys [chat-id sym-key sym-key-id]}]]
    (let [web3 (:web3 db)
          {:keys [topic] :as chat} (get-in db [:transport/chats chat-id])]
      {:db (assoc-in db [:transport/chats chat-id :sym-key-id] sym-key-id)
       :data-store.transport/save {:chat-id chat-id
                                   :chat (assoc chat :sym-key-id sym-key-id)}
       :shh/add-filter {:web3 web3
                        :sym-key-id sym-key-id
                        :topic topic
                        :chat-id chat-id}})))

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
