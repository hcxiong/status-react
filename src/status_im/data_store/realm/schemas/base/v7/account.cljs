(ns status-im.data-store.realm.schemas.base.v7.account)

(def schema {:name       :account
             :primaryKey :address
             :properties {:address             :string
                          :public-key          :string
                          :updates-public-key  {:type     :string
                                                :optional true}
                          :updates-private-key {:type     :string
                                                :optional true}
                          :name                {:type :string :optional true}
                          :email               {:type :string :optional true}
                          :status              {:type :string :optional true}
                          :debug?              {:type :bool :default false}
                          :photo-path          :string
                          :signing-phrase      {:type :string}
                          :last-updated        {:type :int :default 0}
                          :last-sign-in        {:type :int :default 0}
                          :signed-up?          {:type    :bool
                                                :default false}
                          :network             :string
                          :networks            {:type       :list
                                                :objectType :network}
                          :wnode               :string
                          :settings            {:type :string}
                          :sharing-usage-data? {:type :bool :default false}}})