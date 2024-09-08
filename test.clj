(ns eth.gugen.defvalidated-test
  (:require [clojure.test :refer :all]
            [malli.core :as m]
            [malli.error :as me]
            [malli.dev :as dev]
            [eth.gugen.defvalidated :refer [defvalidated with-validation with-validation-debug]]))


(deftest test-schema-as-first-argument
  (defvalidated [:=> [:cat int? int?] pos-int?]
    add-positive
    "Add two numbers and ensure the result is positive"
    [a b]
    (+ a b))

  (is (= 5 (add-positive 2 3)))
  (is (thrown? Exception (add-positive 2 -3))))

(deftest test-malli-schema-in-metadata
  (defvalidated ^{:malli/schema [:=> [:cat string?] pos-int?]}
    string-length-malli
    "Get the length of a string (using :malli/schema)"
    [s]
    (count s))

  (is (= 5 (string-length-malli "hello")))
  (is (thrown? Exception (string-length-malli 123))))

(deftest test-schema-in-metadata
  (defvalidated ^{:schema [:=> [:cat string?] pos-int?]}
    string-length-schema
    "Get the length of a string (using :schema)"
    [s]
    (count s))

  (is (= 5 (string-length-schema "hello")))
  (is (thrown? Exception (string-length-schema 123))))

(deftest test-combined-schemas
  (defvalidated ^{:malli/schema [:=> [:cat int?] int?]
                  :schema [:=> [:cat string?] string?]}
    combined-schema-fn
    "Function with combined schemas"
    [x]
    (str (inc x)))

  (is (= "6" (combined-schema-fn 5)))
  (is (thrown? Exception (combined-schema-fn "hello"))))

(deftest test-separate-schemas
  (defvalidated ^{:malli/schema [:=> [:cat int?] int?]
                  :schema [:=> [:cat string?] string?]
                  :combine-schemas? false}
    separate-schema-fn
    "Function with separate schemas, :malli/schema takes precedence"
    [x]
    (inc x))

  (is (= 6 (separate-schema-fn 5)))
  (is (thrown? Exception (separate-schema-fn "hello"))))

(deftest test-schema-in-attr-map
  (defvalidated multiply
    "Multiply two numbers"
    {:malli/schema [:=> [:cat int? int?] int?]}
    [a b]
    (* a b))

  (is (= 6 (multiply 2 3)))
  (is (thrown? Exception (multiply "2" 3))))

(deftest test-basic-validation
  (testing "Basic function schema validation"
    (defvalidated [:=> [:cat int? int?] int?]
      add
      [a b]
      (+ a b))
    
    (is (= 5 (add 2 3)))
    (is (= -1 (add -2 1)))
    (is (= 0 (add 0 0)))
    (is (thrown? Exception (add "2" 3)))
    (is (thrown? Exception (add 2 "3")))
    (is (thrown? Exception (add 2 3 4)))
    (is (thrown? Exception (add)))))

(deftest test-map-schema-validation
  (testing "Map schema validation"
    (defvalidated {:args [:=> [:cat string? pos-int?]]
                   :ret string?}
      repeat-string
      [s n]
      (apply str (repeat n s)))
    
    (is (= "abcabcabc" (repeat-string "abc" 3)))
    (is (= "" (repeat-string "" 5)))
    (is (= "a" (repeat-string "a" 1)))
    (is (thrown? Exception (repeat-string 123 3)))
    (is (thrown? Exception (repeat-string "abc" -1)))
    (is (thrown? Exception (repeat-string "abc" 0)))
    (is (thrown? Exception (repeat-string "abc" "3")))
    (is (thrown? Exception (repeat-string nil 3)))))

(deftest test-complex-schema
  (testing "Complex schema validation"
    (def UserSchema
      [:map
       [:id uuid?]
       [:name string?]
       [:age [:int {:min 0 :max 150}]]
       [:email [:re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"]]
       [:roles [:set keyword?]]
       [:settings [:map-of keyword? any?]]])
    
    (defvalidated {:args [:=> [:cat UserSchema] any?]
                   :ret boolean?}
      create-user
      [user]
      true)
    
    (is (create-user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                      :name "Alice"
                      :age 30
                      :email "alice@example.com"
                      :roles #{:user}
                      :settings {:theme :dark}}))
    (is (create-user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                      :name ""
                      :age 0
                      :email "a@b.co"
                      :roles #{}
                      :settings {}}))
    (is (thrown? Exception (create-user {:name "Bob" :age 200})))
    (is (thrown? Exception (create-user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                                         :name "Charlie"
                                         :age 30
                                         :email "invalid-email"
                                         :roles #{:user}
                                         :settings {:theme :dark}})))
    (is (thrown? Exception (create-user {:id "not-a-uuid"
                                         :name "Dave"
                                         :age 40
                                         :email "dave@example.com"
                                         :roles #{:user}
                                         :settings {:theme :light}})))
    (is (thrown? Exception (create-user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                                         :name "Eve"
                                         :age -1
                                         :email "eve@example.com"
                                         :roles #{:user}
                                         :settings {:theme :light}})))
    (is (thrown? Exception (create-user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                                         :name "Frank"
                                         :age 30
                                         :email "frank@example.com"
                                         :roles #{:user "not-a-keyword"}
                                         :settings {:theme :light}})))))

(deftest test-validate-dynamic
  (testing "Dynamic var validation"
    (def ^:dynamic *multiplier* 2)
    
    (defvalidated ^{:schema {:args [:=> [:cat int?] int?]
                             :ret int?}
                    :validate-dynamic? true}
      multiply-dynamic
      [x]
      (* x *multiplier*))
    
    (is (= 10 (multiply-dynamic 5)))
    (is (= 0 (multiply-dynamic 0)))
    (is (= -10 (multiply-dynamic -5)))
    (is (thrown? Exception
                 (binding [*multiplier* "invalid"]
                   (multiply-dynamic 5))))
    (is (thrown? Exception
                 (binding [*multiplier* nil]
                   (multiply-dynamic 5))))
    (binding [*multiplier* 3]
      (is (= 15 (multiply-dynamic 5))))))

(deftest test-error-handling
  (testing "Custom error handling"
    (defvalidated ^{:schema {:args [:=> [:cat int?] pos-int?]}
                    :error-fn (fn [e] [:error (ex-message e)])}
      safe-increment
      [x]
      (inc x))
    
    (is (= 6 (safe-increment 5)))
    (is (= 1 (safe-increment 0)))
    (is (= [:error "Input validation error"] (safe-increment -1)))
    (is (= [:error "Input validation error"] (safe-increment "not-a-number")))))

(deftest test-on-error
  (testing "On-error function"
    (defvalidated ^{:schema {:args [:=> [:cat int? int?] int?]
                             :ret pos-int?}
                    :on-error (fn [type errors value]
                                [:error type errors])}
      safe-divide
      [a b]
      (/ a b))
    
    (is (= 5 (safe-divide 10 2)))
    (is (= 1 (safe-divide 1 1)))
    (is (= [:error :args "Invalid input"] (safe-divide 10 0)))
    (is (= [:error :ret "Invalid output"] (safe-divide 1 2)))
    (is (= [:error :ret "Invalid output"] (safe-divide -10 2)))
    (is (= [:error :args "Invalid input"] (safe-divide "10" 2)))))

(deftest test-coercion
  (testing "Argument and return value coercion"
    (defvalidated ^{:schema {:args [:=> [:cat int?] string?]
                             :ret keyword?}
                    :coerce-args? true
                    :coerce-ret? true}
      number-to-keyword
      [x]
      (str "key-" x))
    
    (is (= :key-42 (number-to-keyword "42")))
    (is (= :key-0 (number-to-keyword 0)))
    (is (= :key--1 (number-to-keyword -1)))
    (is (thrown? Exception (number-to-keyword "not-a-number")))
    (is (thrown? Exception (number-to-keyword nil)))
    (is (thrown? Exception (number-to-keyword [])))))

(deftest test-strip-extra-keys
  (testing "Stripping extra keys from map arguments"
    (defvalidated ^{:schema {:args [:=> [:cat [:map [:name string?] [:age int?]]] any?]}
                    :strip-extra-keys? true}
      process-user
      [user]
      user)
    
    (is (= {:name "Alice" :age 30}
           (process-user {:name "Alice" :age 30 :extra "data"})))
    (is (= {:name "Bob" :age 25}
           (process-user {:name "Bob" :age 25 :hobbies ["reading" "cycling"]})))
    (is (thrown? Exception (process-user {:name "Charlie"})))
    (is (thrown? Exception (process-user {:age 35})))
    (is (thrown? Exception (process-user {:name 123 :age "35"})))))

(deftest test-transform
  (testing "Custom transformation"
    (defvalidated ^{:schema {:args [:=> [:cat string?] int?]}
                    :transform [:string :number]}
      parse-and-increment
      [x]
      (inc x))
    
    (is (= 43 (parse-and-increment "42")))
    (is (= 1 (parse-and-increment "0")))
    (is (= 0 (parse-and-increment "-1")))
    (is (thrown? Exception (parse-and-increment "not-a-number")))
    (is (thrown? Exception (parse-and-increment "")))
    (is (thrown? Exception (parse-and-increment nil)))))

(deftest test-with-validation
  (testing "Disabling validation"
    (defvalidated [:=> [:cat pos-int?] pos-int?]
      always-positive
      [x]
      x)
    
    (is (= 5 (always-positive 5)))
    (is (thrown? Exception (always-positive -5)))
    (is (thrown? Exception (always-positive 0)))
    
    (with-validation false
      (is (= -5 (always-positive -5)))
      (is (= 0 (always-positive 0)))
      (is (= "not-a-number" (always-positive "not-a-number"))))))

(deftest test-with-validation-debug
  (testing "Debug mode"
    (defvalidated ^{:schema {:args [:=> [:cat int?] int?]}
                    :debug? true}
      double-it
      [x]
      (* 2 x))
    
    (let [output (with-out-str
                   (with-validation-debug true
                     (double-it 5)))]
      (is (string? output))
      (is (.contains output "VALIDATION DEBUG: Function called with args: (5)"))
      (is (.contains output "VALIDATION DEBUG: Function returned: 10")))
    
    (let [output (with-out-str
                   (with-validation-debug true
                     (try
                       (double-it "not-a-number")
                       (catch Exception e
                         (str "Caught: " (.getMessage e))))))]
      (is (string? output))
      (is (.contains output "VALIDATION DEBUG: Function called with args: (\"not-a-number\")"))
      (is (.contains output "Input validation failed")))))

(deftest test-instrumentation
  (testing "Malli instrumentation"
    (defvalidated ^{:schema {:args [:=> [:cat int?] int?]
                             :ret pos-int?}
                    :instrument? true}
      instrumented-inc
      [x]
      (inc x))
    
    (is (= 6 (instrumented-inc 5)))
    (is (= 1 (instrumented-inc 0)))
    (is (thrown? Exception (instrumented-inc -2)))
    (is (thrown? Exception (instrumented-inc "not-a-number")))
    (with-validation false
      (is (= 0 (instrumented-inc -1))))))

(deftest test-before-and-after-fn
  (testing "Before and after functions"
    (def counter (atom 0))
    
    (defvalidated ^{:schema {:args [:=> [:cat int?] int?]}
                    :before-fn (fn [args] (swap! counter inc))
                    :after-fn (fn [result] (swap! counter inc))}
      increment-counter
      [x]
      (inc x))
    
    (is (= 6 (increment-counter 5)))
    (is (= 2 @counter))
    (is (= 1 (increment-counter 0)))
    (is (= 4 @counter))
    (try
      (increment-counter "not-a-number")
      (catch Exception e
        (is (= 5 @counter))))  ; Before-fn should be called, but not after-fn
    ))

(deftest test-cache
  (testing "Validator caching"
    (def ComplexSchema
      [:map
       [:id uuid?]
       [:data [:vector [:map
                        [:name string?]
                        [:value number?]]]]
       [:metadata [:map-of keyword? any?]]])
    
    (defvalidated ^{:schema {:args [:=> [:cat ComplexSchema] boolean?]}
                    :cache? true}
      process-complex-data
      [data]
      true)
    
    (let [valid-data {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                      :data [{:name "Item" :value 100}]
                      :metadata {:source "API"}}
          [first-time second-time]
          (for [_ (range 2)]
            (let [start (System/nanoTime)]
              (process-complex-data valid-data)
              (- (System/nanoTime) start)))]
      (is (> first-time second-time)))
    
    (is (thrown? Exception
                 (process-complex-data {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                                        :data [{:name "Item" :value "not-a-number"}]
                                        :metadata {:source "API"}})))
    (is (thrown? Exception
                 (process-complex-data {:id "not-a-uuid"
                                        :data [{:name "Item" :value 100}]
                                        :metadata {:source "API"}})))
    (is (thrown? Exception
                 (process-complex-data {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                                        :data [{:name 123 :value 100}]
                                        :metadata {:source "API"}})))))

(deftest test-nested-schemas
  (testing "Nested schema validation"
    (def NestedSchema
      [:map
       [:person [:map
                 [:name string?]
                 [:age pos-int?]]]
       [:address [:map
                  [:street string?]
                  [:city string?]
                  [:zip [:re #"\d{5}"]]]]
       [:tags [:vector keyword?]]])
    
    (defvalidated ^{:schema {:args [:=> [:cat NestedSchema] boolean?]}}
      process-nested-data
      [data]
      true)
    
    (is (process-nested-data {:person {:name "Alice" :age 30}
                              :address {:street "123 Main St" :city "Anytown" :zip "12345"}
                              :tags [:customer :vip]}))
    (is (thrown? Exception
                 (process-nested-data {:person {:name "Bob" :age -1}
                                       :address {:street "456 Elm St" :city "Somewhere" :zip "12345"}
                                       :tags [:customer]})))
    (is (thrown? Exception
                 (process-nested-data {:person {:name "Charlie" :age 25}
                                       :address {:street "789 Oak St" :city "Nowhere" :zip "1234"}
