# defvalidated: A Robust Function Definition Macro with Schema Validation
- [WARNING] The source codes and this document in this repository are mostly made with Reflection-llama3.1-70B and Claude Sonnet. The codes are not tested yet! Do not trust the codes!

`defvalidated` is a powerful Clojure macro that extends the functionality of `defn` by adding schema validation, error handling, and debugging capabilities. It allows you to define functions with optional input and output validation using Malli schemas, providing enhanced type safety and robust error handling in your Clojure applications.

## Table of Contents

1. [Features](#features)
2. [Installation](#installation)
3. [Basic Usage](#basic-usage)
4. [Schema Types](#schema-types)
5. [Optional Parameters](#optional-parameters)
6. [Advanced Examples](#advanced-examples)
7. [Debugging](#debugging)
8. [Performance Considerations](#performance-considerations)
9. [Best Practices](#best-practices)
10. [Contributing](#contributing)
11. [License](#license)

## Features

- Schema validation for function arguments and return values
- Optional schema definition (use it only when you need it)
- Comprehensive error handling and reporting
- Debug mode for detailed insights into function execution and validation
- Performance optimization through validator caching
- Support for coercion, custom transformations, and extra key stripping
- Flexible error recovery strategies
- Compatible with Malli schemas and instrumentation

## Installation

Add the following dependency to your `project.clj` or `deps.edn`:

```clojure
[eth.gugen/defvalidated "0.1.0"]  ; Replace with actual library coordinates
```

## Basic Usage

First, require the macro in your namespace:

```clojure
(ns your-namespace
  (:require [eth.gugen.defvalidated :refer [defvalidated]]))
```

Define a function with input and output validation:

```clojure
(defvalidated {:args [:=> [:cat int? int?] any?]
               :ret pos-int?}
  add-positive
  "Add two numbers and ensure the result is positive"
  [a b]
  (+ a b))

(add-positive 2 3)  ; Returns 5
(add-positive 2 -3) ; Throws a validation error (result is not positive)
```

There are three ways to provide a schema to defvalidated:
- As the first argument to defvalidated
- In the metadata of the function name using :malli/schema or :schema
- In the attribute map after the docstring using :malli/schema or :schema

Examples:
```clojure
;; 1. Schema as first argument
(defvalidated [:=> [:cat int? int?] pos-int?]
  add-positive
  "Add two numbers and ensure the result is positive"
  [a b]
  (+ a b))

;; 2. Schema in function name metadata
(defvalidated ^{:malli/schema [:=> [:cat string?] pos-int?]}
  string-length
  "Get the length of a string"
  [s]
  (count s))

;; 3. Schema in attribute map
(defvalidated multiply
  "Multiply two numbers"
  {:schema [:=> [:cat int? int?] int?]}
  [a b]
  (* a b))
```

## Schema Types

The `defvalidated` macro supports two main types of schema definitions:

### 1. Function Schema

A function schema is a vector that describes the input and output of a function. It uses the `:=>` keyword to separate input and output schemas.

Syntax: `[:=> [:cat input-schema1 input-schema2 ...] output-schema]`

Example:
```clojure
(defvalidated [:=> [:cat int? int?] int?]
  add
  "Add two integers and return the result"
  [a b]
  (+ a b))

; Usage:
(add 2 3)  ; Returns 5
(add 2 "3")  ; Throws a validation error (second argument is not an integer)
```

In this example, the function expects two integers as input and returns an integer.

### 2. Map Schema

A map schema provides more flexibility, allowing you to specify schemas for arguments (`:args`) and return value (`:ret`) separately.

Syntax: `{:args [:=> [:cat input-schema1 input-schema2 ...]], :ret output-schema}`

Example:
```clojure
(defvalidated {:args [:=> [:cat string? pos-int?]]
               :ret string?}
  repeat-string
  "Repeat a string a given number of times"
  [s n]
  (apply str (repeat n s)))

; Usage:
(repeat-string "abc" 3)  ; Returns "abcabcabc"
(repeat-string "abc" -1)  ; Throws a validation error (second argument is not a positive integer)
```

This schema specifies that the function takes a string and a positive integer as arguments, and returns a string.

### Malli Schema Types

Within these schema structures, you can use any of the schema types provided by Malli. Here are some common ones:

- Basic types: `:string`, `:int`, `:boolean`, `:double`, `:keyword`, `:symbol`, `:any`
- Collections: `:vector`, `:list`, `:set`, `:map`
- Predicates: `pos-int?`, `neg-int?`, `nat-int?`, `float?`, `uuid?`, `inst?`
- Logical: `:and`, `:or`, `:not`
- Enum: `[:enum value1 value2 ...]`
- Maybe: `[:maybe schema]` (allows `nil` in addition to the specified schema)
- Tuple: `[:tuple schema1 schema2 ...]`
- Map of: `[:map-of key-schema value-schema]`

Example with complex schema:
```clojure
(require '[malli.core :as m])

(def UserSchema
  [:map
    [:id uuid?]
    [:name string?]
    [:age [:int {:min 0, :max 150}]]
    [:email [:re #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$"]]
    [:roles [:set keyword?]]
    [:settings [:map-of keyword? any?]]])

(defvalidated {:args [:=> [:cat UserSchema] any?]
               :ret boolean?}
  create-user
  "Create a new user and return success status"
  [user]
  (println "Creating user:" (:name user))
  true)

; Usage:
(create-user {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
              :name "Alice"
              :age 30
              :email "alice@example.com"
              :roles #{:user}
              :settings {:theme :dark}})
; Prints: Creating user: Alice
; Returns: true

(create-user {:name "Bob" :age 200})
; Throws a validation error (missing required fields and age out of range)
```

This example demonstrates a complex user schema with various Malli schema types, including `:map`, `uuid?`, `string?`, `:int` with constraints, `:re` for regex matching, `:set`, and `:map-of`.

Remember that you can nest these schema types to create more complex structures as needed for your specific use case.

## Optional Parameters

The `defvalidated` macro supports several optional parameters that can be specified as metadata on the function name. Here's a detailed explanation of each parameter with examples:

### :schema

Specifies the Malli schema for validation. Can be provided as the first argument to the macro or in the metadata.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat string?] any?]
                         :ret string?}}
  greet
  "Greet a person by name"
  [name]
  (str "Hello, " name "!"))

; Usage:
(greet "Alice")  ; Returns "Hello, Alice!"
(greet 123)  ; Throws a validation error (argument is not a string)
```

### :validate-dynamic?

Enables runtime checks for dynamic var bindings.

```clojure
(require '[malli.core :as m])

(def ^:dynamic *multiplier* 2)

(defvalidated ^{:schema {:args [:=> [:cat int?] int?]
                         :ret int?}
                :validate-dynamic? true}
  multiply-dynamic
  "Multiply a number by a dynamic multiplier"
  [x]
  (* x *multiplier*))

; Usage:
(multiply-dynamic 5)  ; Returns 10

(binding [*multiplier* "invalid"]
  (multiply-dynamic 5))  ; Throws a validation error (multiplier is not a number)
```

### :error-fn

Custom function to handle validation errors.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int?] pos-int?]}
                :error-fn (fn [e]
                            (println "Custom error:" (ex-message e))
                            0)}  ; Return 0 as a fallback value
  safe-increment
  "Increment a number, with custom error handling"
  [x]
  (inc x))

; Usage:
(safe-increment 5)  ; Returns 6
(safe-increment -1)  ; Prints custom error and returns 0
```

### :on-error

Function to call on validation error, receives error type, errors, and value.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int? int?] int?]
                         :ret pos-int?}
                :on-error (fn [type errors value]
                            (println "Error type:" type)
                            (println "Errors:" errors)
                            (if (= type :args) 0 1))}  ; Return 0 for arg errors, 1 for return errors
  safe-divide
  "Divide two numbers with custom error handling"
  [a b]
  (/ a b))

; Usage:
(safe-divide 10 2)  ; Returns 5
(safe-divide 10 0)  ; Prints error information and returns 0
(safe-divide 1 2)  ; Prints error information (result not positive) and returns 1
```

### :instrument?

Uses Malli's instrumentation for more detailed runtime checks.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int?] int?]
                         :ret pos-int?}
                :instrument? true}
  instrumented-inc
  "Increment a number with instrumentation"
  [x]
  (inc x))

; Usage:
(instrumented-inc 5)  ; Returns 6
(instrumented-inc -2)  ; Throws a detailed instrumentation error
```

### :debug?

Enables debug printing for the function.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat string?] string?]}
                :debug? true}
  uppercase
  "Convert a string to uppercase"
  [s]
  (clojure.string/upper-case s))

; Usage:
(uppercase "hello")
; VALIDATION DEBUG: Function called with args: ("hello")
; VALIDATION DEBUG: Function returned: "HELLO"
; => "HELLO"
```

### :before-fn

Function to call before validation, receives args.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int?] int?]}
                :before-fn #(println "Processing:" %)}
  double-it
  "Double a number"
  [x]
  (* 2 x))

; Usage:
(double-it 5)
; Prints: Processing: (5)
; Returns: 10
```

### :after-fn

Function to call after validation, receives result.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int? int?] int?]}
                :after-fn #(println "Result:" %)}
  multiply
  "Multiply two numbers"
  [a b]
  (* a b))

; Usage:
(multiply 3 4)
; Prints: Result: 12
; Returns: 12
```

### :coerce-args?

Coerces input arguments using Malli's coercion.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int?] int?]}
                :coerce-args? true}
  increment
  "Increment a number, with argument coercion"
  [x]
  (inc x))

; Usage:
(increment 5)  ; Returns 6
(increment "5")  ; Returns 6 (string "5" is coerced to integer 5)
```

### :coerce-ret?

Coerces return value using Malli's coercion.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat int?] string?]
                         :ret keyword?}
                :coerce-ret? true}
  number-to-keyword
  "Convert a number to a keyword"
  [x]
  (str "key-" x))

; Usage:
(number-to-keyword 42)  ; Returns :key-42 (string is coerced to keyword)
```

### :cache?

Caches validator functions for better performance.

```clojure
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
  "Process complex data structure with cached validation"
  [data]
  (println "Processing data")
  true)

; Usage:
; First call compiles the schema
(time (process-complex-data {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                             :data [{:name "Item" :value 100}]
                             :metadata {:source "API"}}))

; Subsequent calls are faster due to caching
(time (process-complex-data {:id #uuid "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
                             :data [{:name "Item" :value 200}]
                             :metadata {:source "DB"}}))
```

### :strip-extra-keys?

Removes extra keys from map arguments.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat [:map [:name string?] [:age int?]]] any?]}
                :strip-extra-keys? true}
  process-user
  "Process user data, ignoring extra fields"
  [user]
  (str "Processing user: " (:name user) ", age " (:age user)))

; Usage:
(process-user {:name "Alice" :age 30 :extra "data"})
; => "Processing user: Alice, age 30"
; Note: :extra key is stripped before function execution
```

### :transform

Custom transformation to apply to args and return value.

```clojure
(defvalidated ^{:schema {:args [:=> [:cat string?] int?]}
                :transform [:string :number]}
  parse-and-increment
  "Parse a string to a number and increment it"
  [x]
  (inc x))

; Usage:
(parse-and-increment "42")  ; Returns 43
```

### :combine-schemas?

By default, if both :malli/schema and :schema are provided, they are combined. You can disable this behavior with :combine-schemas? false.
When :combine-schemas? is false, :malli/schema takes precedence over :schema.

## Error Reporting

For prettier runtime error messages, use malli.dev.pretty:

```clojure
(require '[malli.dev :as dev]
         '[malli.dev.pretty :as pretty])

(dev/start! {:report (pretty/reporter)})

;; Your function calls here

(dev/stop!)
```


## Advanced Examples

### 1. API Endpoint Validation

```clojure
(def UserSchema
  [:map
    [:id :string]
    [:name :string]
    [:age :int]])

; Assume db/insert-user! is a function that inserts a user into a database
(defn db/insert-user! [user]
  ; Implementation details omitted
  true)

(defvalidated {:args [:=> [:cat UserSchema] any?]
               :ret [:map [:status keyword?] [:message string?]]}
  create-user
  "Create a new user and return status"
  [user]
  (let [result (db/insert-user! user)]
    {:status :success
     :message (str "User " (:name user) " created successfully")}))

; Usage:
(create-user {:id "123" :name "Alice" :age 30})
; => {:status :success, :message "User Alice created successfully"}

(create-user {:id "456" :name "Bob" :age "35"})
; Throws input validation error (age is not an integer)
```

### 2. Data Transformation Pipeline

```clojure
(defvalidated ^{:schema {:args [:=> [:cat map?] map?]
                         :ret map?}
                :coerce-args? true
                :transform [:string :number]}
  parse-metrics
  "Parse and transform raw metrics data"
  [raw-data]
  (-> raw-data
      (update :timestamp #(java.time.Instant/parse %))
      (update :value #(* % 1.5))))

(parse-metrics {:timestamp "2023-01-01T00:00:00Z" :value "100"})
; => {:timestamp #inst"2023-01-01T00:00:00.000-00:00", :value 150.0}
```

### 3. Conditional Validation

```clojure
(def PaymentSchema
  [:map
    [:amount pos-int?]
    [:method [:enum :credit :debit :paypal]]
    [:credit-card {:optional true} :string]
    [:paypal-email {:optional true} :string]])

(defvalidated ^{:schema {:args [:=> [:cat PaymentSchema] boolean?]}
                :validate-dynamic? true}
  process-payment
  "Process a payment with dynamic validation"
  [payment]
  (let [validator (case (:method payment)
                    :credit (m/validator [:map [:credit-card :string]])
                    :paypal (m/validator [:map [:paypal-email :string]])
                    (constantly true))]
    (if (validator payment)
      (do
        (println "Processing payment:" payment)
        true)
      (throw (ex-info "Invalid payment details" {:payment payment})))))

(process-payment {:amount 100 :method :credit :credit-card "1234-5678-9012-3456"})
; => true

(process-payment {:amount 50 :method :paypal :paypal-email "user@example.com"})
; => true

(process-payment {:amount 75 :method :credit}) ; Throws validation error
```

### 4. Integration with Existing Systems

```clojure
(defn legacy-calculate-total
  [items]
  (reduce + (map :price items)))

(def ItemSchema
  [:map [:id string?] [:price pos-int?]])

(defvalidated ^{:schema {:args [:=> [:cat [:vector ItemSchema]] number?]
                         :ret pos-int?}
                :instrument? true}
  calculate-total
  "Calculate total price with validation"
  [items]
  (legacy-calculate-total items))

(calculate-total [{:id "A1" :price 100} {:id "B2" :price 150}]) ; => 250
(calculate-total [{:id "C3" :price -50}]) ; Throws input validation error
```

## Debugging

Use the `with-validation-debug` macro to enable debug mode for a specific block of code:

```clojure
(with-validation-debug true
  (add-positive 2 3))  ; Prints detailed debug information
```

## Performance Considerations

- Use the `:cache?` option for frequently called functions with complex schemas
- Consider using `:coerce-args?` and `:coerce-ret?` instead of manual type conversions
- For maximum performance, you can disable validation entirely using `(with-validation false ...)`

## Best Practices

1. **Start Simple**: Begin with basic schema validation and gradually add more advanced features as needed.
2. **Use Debug Mode**: Enable debug mode during development to gain insights into function behavior and validation.
3. **Optimize Performance**: Use caching for complex schemas and frequently called functions.
4. **Graceful Degradation**: Implement custom error handling to recover from validation errors when appropriate.
5. **Combine with Coercion**: Use coercion to automatically convert input data to the expected types.
6. **Document Schemas**: Keep your schemas well-documented for better code maintainability.
7. **Test Thoroughly**: Write unit tests that cover both valid and invalid inputs to ensure robust behavior.
8. **Leverage Transformations**: Use the `:transform` option to preprocess data before validation.
9. **Dynamic Validation**: Utilize `:validate-dynamic?` for functions that depend on runtime state.
10. **Instrumentation in Development**: Use `:instrument?` during development for more detailed error messages.

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Copyright © 2024 Takahiro Sawada

Distributed under the Eclipse Public License, the same as Clojure.
