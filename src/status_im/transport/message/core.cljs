(ns status-im.transport.message.core
  (:require [status-im.chat.models :as models.chat]))

(def ping-topic "0x01010202")

(def ttl 10000)

(defprotocol StatusMessage
  "Protocol for transport layed status messages"
  (send [this chat-id cofx])
  (receive [this chat-id signature cofx]))

;; TODO (yenda) implement
;; :group-message
;; :public-group-message
;; :pending
;; :group-invitation
;; :update-group
;; :add-group-identity
;; :remove-group-identity
;; :leave-group
;; :contact-request
;; :discover
;; :discoveries-request
;; :discoveries-response
;; :profile
;; :online


;;TODO (yenda) this is probably not the place to have these
(defn- receive-contact-request
  [public-key
   {:keys [name profile-image address fcm-token]}
   {{:contacts/keys [contacts] :as db} :db :as cofx}]
  (when-not (get contacts public-key)
    (let [contact-props {:whisper-identity public-key
                         :public-key       public-key
                         :address          address
                         :photo-path       profile-image
                         :name             name
                         :fcm-token        fcm-token
                         :pending?         true}
          fx            {:db           (update-in db [:contacts/contacts public-key] merge contact-props)
                         :save-contact contact-props}
          chat-props    {:name         name
                         :chat-id      public-key
                         :contact-info (prn-str contact-props)}]
      (merge fx (models.chat/add-chat (assoc cofx :db (:db fx)) public-key chat-props)))))

(defn- receive-contact-request-confirmation
  [public-key {:keys [name profile-image address fcm-token]}
   {{:contacts/keys [contacts] :as db} :db :as cofx}]
  (when-let [contact (get contacts public-key)]
    (let [contact-props {:whisper-identity public-key
                         :address          address
                         :photo-path       profile-image
                         :name             name
                         :fcm-token        fcm-token}
          fx            {:db           (update-in db [:contacts/contacts public-key] merge contact-props)
                         :save-contact contact-props}
          chat-props    {:name    name
                         :chat-id public-key}]
      (merge fx (models.chat/upsert-chat (assoc cofx :db (:db fx)) chat-props)))))
