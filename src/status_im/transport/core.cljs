(ns status-im.transport.core
  (:require [cljs.spec.alpha :as spec]
            [re-frame.core :as re-frame]
            [status-im.transport.message.core :as message]
            [status-im.transport.filters :as filters]
            [status-im.transport.utils :as transport.utils]
            [taoensso.timbre :as log]))

(defn stop-whisper! []
  #_(stop-watching-all!)
  #_(reset-all-pending-messages!)
  #_(reset-keys!))

(defn init-whisper!
  [{:keys [identity web3
           contacts pending-messages transport]
    :as   options}]
  #_{:pre [(spec/valid? ::options options)]}
  (log/debug :init-whisper)
  (stop-whisper!)

  (filters/add-filter! web3
                       {:privateKeyID identity
                        :topics [(transport.utils/get-topic identity)]}
                       (fn [js-error js-message]
                         (re-frame/dispatch [:protocol/receive-whisper-message js-error js-message])))
  (for [[chat-id {:keys [sym-key-id topic] :as chat}] transport]
    (when sym-key-id
      (filters/add-filter! web3
                           {:SymKeyID sym-key-id
                            :topics [topic]}
                           (fn [js-error js-message]
                             (re-frame/dispatch [:protocol/receive-whisper-message js-error js-message chat-id]))))))
