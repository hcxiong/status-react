(ns status-im.data-store.transport
  (:require [re-frame.core :as re-frame]
            [status-im.data-store.realm.transport :as data-store]
            [cljs.tools.reader.edn :as edn]))


(defn deserialize-chat [serialized-chat]
  (-> serialized-chat
      (dissoc :chat-id)
      (update :ack edn/read-string)
      (update :seen edn/read-string)
      (update :pending-ack edn/read-string)
      (update :pending-send edn/read-string)))

(defn get-all []
  (reduce (fn [acc {:keys [chat-id] :as chat}]
            (assoc acc chat-id (deserialize-chat chat)))
          {}
          (data-store/get-all)))

(re-frame/reg-cofx
  :data-store/transport
  (fn [cofx _]
    (assoc cofx :data-store/transport (get-all))))

(defn save [chat-id chat]
  (let [serialized-chat (-> chat
                            (assoc :chat-id chat-id)
                            (update :ack pr-str)
                            (update :seen pr-str)
                            (update :pending-ack pr-str)
                            (update :pending-send pr-str))]
    (data-store/save serialized-chat)))

(re-frame/reg-fx
  :data-store.transport/save
  (fn [[chat-id chat]]
    (save chat-id chat)))
