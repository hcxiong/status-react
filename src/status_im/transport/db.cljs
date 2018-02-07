(ns status-im.transport.db
  (:require-macros [status-im.utils.db :refer [allowed-keys]])
  (:require [cljs.spec.alpha :as spec]))

;; required
(spec/def ::ack (spec/coll-of string? :kind vector?))
(spec/def ::seen (spec/coll-of string? :kind vector?))
(spec/def ::pending-ack (spec/coll-of string? :kind vector?))
(spec/def ::pending-send (spec/coll-of string? :kind vector?))
(spec/def ::topic string?)

;; optional
(spec/def ::sym-key-id string?)

(spec/def :transport/chat (allowed-keys :req-un [::ack ::seen ::pending-ack ::pending-send ::topic]
                                        :opt-un [::sym-key-id]))

(spec/def :transport/chats (spec/map-of :global/not-empty-string :transport/chat))


(defn create-chat [topic]
  {:ack []
   :seen []
   :pending-ack []
   :pending-send []
   :topic topic})
