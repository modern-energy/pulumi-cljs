(ns pulumi-cljs.core
  "Macros for working with Pulumi from CLJS")

(defmacro all
  "Give an binding vector (similar to let) of symbols and Pulumi Outputs,
  execute the body when the concrete values are available, yielding a
  new Output.

  Under the hood, this macro wraps the pulumi.all and Output.apply
  functions."
  [bindings & body]
  (let [binding-pairs (partition 2 bindings)
       binding-forms (map first binding-pairs)
       output-exprs (map second binding-pairs)]
    `(.apply
       (all* (cljs.core/clj->js [~@output-exprs]))
       (fn [[~@binding-forms]]
         ~@body))))

