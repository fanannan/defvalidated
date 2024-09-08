(ns eth.gugen.defvalidated
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]
            [malli.transform :as mt]
            [malli.dev.pretty :as pretty]
            [clojure.string :as str]))

(def ^:dynamic *enable-validation* true)
(def ^:dynamic *validation-debug* false)

(defn- validate-and-explain [schema value]
  (try
    (when-let [error (m/explain schema value)]
      (me/humanize error))
    (catch Exception e
      (str "Validation error: " (.getMessage e)))))

(defn- debug-print [& args]
  (when *validation-debug*
    (apply println "VALIDATION DEBUG:" args)))

(defn- safe-apply [f & args]
  (try
    (apply f args)
    (catch Exception e
      {:error (str "Error in function application: " (.getMessage e))})))

(defn- wrap-validation [schema f {:keys [error-fn before-fn after-fn on-error cache?]}]
  (let [args-schema (:args schema)
        ret-schema (:ret schema)
        validate-args (if cache?
                        (try (m/validator args-schema)
                             (catch Exception e
                               (fn [_] (str "Invalid args schema: " (.getMessage e)))))
                        (partial m/validate args-schema))
        validate-ret (when ret-schema
                       (if cache?
                         (try (m/validator ret-schema)
                              (catch Exception e
                                (fn [_] (str "Invalid return schema: " (.getMessage e)))))
                         (partial m/validate ret-schema)))]
    (fn [& args]
      (when *enable-validation*
        (debug-print "Function called with args:" args)
        (when before-fn
          (safe-apply before-fn args))
        (let [args-valid? (try (validate-args args)
                               (catch Exception e
                                 (debug-print "Args validation error:" (.getMessage e))
                                 false))]
          (when-not args-valid?
            (let [errors (validate-and-explain args-schema args)]
              (debug-print "Input validation failed:" errors)
              (if on-error
                (safe-apply on-error :args errors args)
                (error-fn (ex-info "Input validation error" {:errors errors})))))))
      (let [result (safe-apply f args)]
        (if (:error result)
          (do
            (debug-print "Function execution failed:" (:error result))
            (if on-error
              (safe-apply on-error :execution (:error result) args)
              (error-fn (ex-info "Function execution error" {:error (:error result)}))))
          (do
            (when *enable-validation*
              (when after-fn
                (safe-apply after-fn result))
              (when validate-ret
                (let [ret-valid? (try (validate-ret result)
                                      (catch Exception e
                                        (debug-print "Return validation error:" (.getMessage e))
                                        false))]
                  (when-not ret-valid?
                    (let [errors (validate-and-explain ret-schema result)]
                      (debug-print "Output validation failed:" errors)
                      (if on-error
                        (safe-apply on-error :ret errors result)
                        (error-fn (ex-info "Output validation error" {:errors errors}))))))))
            (debug-print "Function returned:" result)
            result))))))








(defmacro defvalidated
  "Define a function with optional input and output validation using Malli schemas.
   Usage: 
     (defvalidated name doc-string? attr-map? [params*] prepost-map? body)
     (defvalidated schema name doc-string? attr-map? [params*] prepost-map? body)

   The schema can be provided in three ways:
   1. As the first argument to defvalidated
   2. In the metadata of the function name using :malli/schema or :schema
   3. In the attribute map after the docstring using :malli/schema or :schema

   The schema, if provided, can be either:
   1. A function schema (e.g., [:=> [:cat int? int?] int?])
   2. A map with :args and optional :ret keys (e.g., {:args [:=> [:cat int? int?]], :ret int?})

   If :ret is not provided, only input validation will be performed.
   If no schema is provided, no validation will be performed.

   Options (specified as metadata on the function name):
   :validate-dynamic? - If true, enables runtime checks for dynamic var bindings (default: false)
   :error-fn - Custom function to handle validation errors (default: pretty/thrower)
   :on-error - Function to call on validation error, receives :args/:ret/:execution, errors, and value (default: nil)
   :instrument? - If true, uses Malli's instrumentation for more detailed runtime checks (default: false)
   :debug? - If true, enables debug printing for this function (default: false)
   :before-fn - Function to call before validation, receives args (default: nil)
   :after-fn - Function to call after validation, receives result (default: nil)
   :coerce-args? - If true, coerces input arguments using Malli's coercion (default: false)
   :coerce-ret? - If true, coerces return value using Malli's coercion (default: false)
   :cache? - If true, caches the validator functions for better performance (default: false)
   :strip-extra-keys? - If true, removes extra keys from map arguments (default: false)
   :transform - Custom transformation to apply to args and return value (default: nil)
   :combine-schemas? - If true, combines :malli/schema and :schema (default: true)
                       If false, :malli/schema takes precedence over :schema"
  [& args]
  (let [[schema args] (if (or (map? (first args)) (vector? (first args)))
                        [(first args) (rest args)]
                        [nil args])
        [name & fdecl] args
        [docstring fdecl] (if (string? (first fdecl))
                            [(first fdecl) (rest fdecl)]
                            [nil fdecl])
        [attr-map fdecl] (if (map? (first fdecl))
                           [(first fdecl) (rest fdecl)]
                           [nil fdecl])
        [params & body] fdecl
        {:keys [malli/schema schema validate-dynamic? error-fn instrument? debug?
                before-fn after-fn coerce-args? coerce-ret?
                cache? strip-extra-keys? transform on-error combine-schemas?]
         :or {validate-dynamic? false
              error-fn `pretty/thrower
              instrument? false
              debug? false
              coerce-args? false
              coerce-ret? false
              cache? false
              strip-extra-keys? false
              combine-schemas? true}} (merge (meta name) attr-map)
        effective-schema (cond
                           (and combine-schemas? (or schema malli/schema)) (or schema malli/schema)
                           combine-schemas? (or schema malli/schema (:malli/schema attr-map) (:schema attr-map))
                           :else (or malli/schema schema))  ; Priority to :malli/schema when not combining
        effective-schema (if (vector? effective-schema)
                           {:args effective-schema}
                           effective-schema)
        strip-fn (when strip-extra-keys? `(mu/strip-extra-keys ~effective-schema))
        transform-fn (when transform `(mt/transformer ~transform))
        wrapped-body (if effective-schema
                       `(wrap-validation ~effective-schema
                                         (fn ~params ~@body)
                                         {:error-fn ~error-fn
                                          :before-fn ~before-fn
                                          :after-fn ~after-fn
                                          :on-error ~on-error
                                          :cache? ~cache?})
                       `(fn ~params ~@body))
        coerced-body (cond-> wrapped-body
                       (and effective-schema (or coerce-args? coerce-ret?)) (list `m/coerce effective-schema)
                       (and effective-schema strip-extra-keys?) (list `m/decode effective-schema strip-fn)
                       (and effective-schema transform) (list `m/decode effective-schema transform-fn))]
    `(def ~(with-meta name (merge (meta name)
                                  (when effective-schema
                                    (if combine-schemas?
                                      {:malli/schema effective-schema}
                                      (if malli/schema
                                        {:malli/schema malli/schema}
                                        {:schema schema})))
                                  (when docstring {:doc docstring})
                                  (dissoc attr-map :malli/schema :schema)))
       ~@(when docstring [docstring])
       ~(if (and effective-schema instrument?)
          `(m/instrument ~coerced-body ~effective-schema)
          coerced-body))))
