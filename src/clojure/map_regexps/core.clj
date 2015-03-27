; Copyright (c) 2015 Oskar Kvist.
; All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
; which can be found in the file epl-v10.html at the root of this distribution.
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
; You must not remove this notice, or any other, from this software.

(ns map-regexps.core
  (:refer-clojure :exclude [re-seq])
  (:import (map_regexps.parser Parser)
           (map_regexps.lexer Lexer)
           (java.io PushbackReader StringReader)))

(defn parse [input]
  (-> input StringReader. PushbackReader. Lexer. Parser. .parse))

(defmulti add-to-labels* (fn [n inst] (first inst)))

(defmethod add-to-labels* :split [n [inst x y]]
  [:split (+ n x) (+ n y)])

(defmethod add-to-labels* :jmp [n [inst x]]
  [:jmp (+ n x)])

(defmethod add-to-labels* :default [n inst]
  inst)

(defn add-to-labels
  "Add n to all the labels of the instructions in e."
  [n insts]
  (map (partial add-to-labels* n) insts))

(defmulti compile-re
  "Compiles an abstract syntax tree (AST) to a list of instructions for the
   regexp virtual machine. pars is the number of parentheses used so far
   (needed for save instructions)."
  (fn [ast pars] (class ast)))

(defmethod compile-re map_regexps.node.Start [ast pars]
  ;; set pars to 1 because the whole match is saved as if it were a parenthesis
  (let [[insts p] (compile-re (.getPExp ast) 1)
        insts (add-to-labels 1 insts)]
    [(concat [[:save 0]] insts [[:save 1] [:match]]) p]))

(defn set-comp [node-type inst-name]
  (defmethod compile-re node-type [ast pars]
    (let [events-list (map #(read-string (.getText %)) (.getEvents ast))]
      [[[inst-name events-list]] pars])))

(set-comp map_regexps.node.ASetExp :match-set)
(set-comp map_regexps.node.ANegsetExp :match-negset)

(defmethod compile-re map_regexps.node.AEventExp [ast pars]
  [[[:term (-> ast .getEvent .getText read-string)]] pars])

(defmethod compile-re map_regexps.node.AConcatExp [ast pars]
  (let [[insts1 p1] (compile-re (.getLeft ast) pars)
        [insts2 p2] (compile-re (.getRight ast) p1)
        len (count insts1)
        insts2 (add-to-labels len insts2)]
    [(concat insts1 insts2) p2]))

(defmethod compile-re map_regexps.node.AUnionExp [ast pars]
  (let [[insts1 p1] (compile-re (.getLeft ast) pars)
        [insts2 p2] (compile-re (.getRight ast) p1)
        len1 (count insts1)
        len2 (count insts2)
        insts1 (add-to-labels 1 insts1)
        insts2 (add-to-labels (+ len1 2) insts2)]
    [(concat [[:split 1 (+ len1 2)]] insts1 [[:jmp (+ len1 len2 2)]] insts2)
     p2]))

(defmethod compile-re map_regexps.node.AQmarkExp [ast pars]
  (let [[insts p] (compile-re (.getExp ast) pars)
        len (count insts)
        insts (add-to-labels 1 insts)]
    [(concat [[:split 1 (+ len 1)]] insts) p]))

(defmethod compile-re map_regexps.node.AStarExp [ast pars]
  (let [[insts p] (compile-re (.getExp ast) pars)
        len (count insts)
        insts (add-to-labels 1 insts)]
    [(concat [[:split 1 (+ len 2)]] insts [[:jmp 0]]) p]))

(defmethod compile-re map_regexps.node.APlusExp [ast pars]
  (let [[insts p] (compile-re (.getExp ast) pars)
        len (count insts)]
    [(concat insts [[:split 0 (+ len 1)]]) p]))

(defmethod compile-re map_regexps.node.ADotExp [ast pars]
  [[[:match-any]] pars])

(defmethod compile-re map_regexps.node.AParenExp [ast pars]
  (let [[insts p] (compile-re (.getExp ast) (inc pars))
        len (count insts)
        insts (add-to-labels 1 insts)]
    [(concat [[:save (* 2 pars)]] insts [[:save (inc (* 2 pars))]]) p]))

(defn nfa [re]
  "Construct a non-deterministic finite automaton (NFA) from the regexp re."
  (let [[insts pars] (compile-re (parse re) 0)]
    {:nfa (vec insts) :pars pars}))

(defn terminal-matches? [terminal input]
  (when (associative? input)
    (every? (fn [[k v]] (= (input k) v))
            terminal)))

(defmulti step (fn [inst input-token thread sp] (first inst)))

(defmethod step :jmp [[_ to] _ t _]
  {:curr-threads [(assoc t :pc to)]})

(defmethod step :split [[_ x y] _ t _]
  {:curr-threads [(assoc t :pc x) (assoc t :pc y)]})

(defmethod step :term [[_ terminal] input t _]
  (when (terminal-matches? terminal input)
    {:next-threads [(update-in t [:pc] inc)]}))

(defmethod step :match-set [[_ terminals] input t _]
  (when (some #(terminal-matches? % input) terminals)
    {:next-threads [(update-in t [:pc] inc)]}))

(defmethod step :match-negset [[_ terminals] input t _]
  (when-not (some #(terminal-matches? % input) terminals)
    {:next-threads [(update-in t [:pc] inc)]}))

(defmethod step :match [_ _ t sp]
  {:match {:sp sp :saved (:saved t)}})

(defmethod step :match-any [_ _ t _]
  {:next-threads [(update-in t [:pc] inc)]})

(defmethod step :save [[_ i] _ t sp]
  {:curr-threads [(update-in (assoc-in t [:saved i] sp) [:pc] inc)]})

(defn make-new-thread [pc pars]
  {:pc pc :saved (vec (repeat (* 2 pars) nil))})

(deftype ThreadList [pc-set threads]
  clojure.lang.IPersistentStack
  (cons [this thread]
    (if (contains? pc-set (:pc thread))
      this
      (ThreadList. (conj pc-set (:pc thread)) (conj threads thread))))
  (pop [this]
    (ThreadList. pc-set (pop threads)))
  (peek [this]
    (peek threads))
  (seq [this] (seq threads)))

(defn thread-list []
  (ThreadList. #{} (clojure.lang.PersistentQueue/EMPTY)))

(defn run-one-step
  "Simulates the NFA on the current input symbol (each thread will run a
   'step'). Returns the list of threads that are still alive after this
   step, and a match if there was one."
  ([nfa input-token sp clist]
   (run-one-step nfa input-token sp (thread-list) clist nil))
  ([nfa input-token sp nlist clist match]
   (if (empty? clist)
     {:nlist nlist :match match}
     (let [thread (peek clist)
           clist (pop clist)
           inst (nfa (:pc thread))
           step-output (step inst input-token thread sp)
           match (or match (:match step-output))
           clist (into clist (:curr-threads step-output))
           nlist (into nlist (:next-threads step-output))]
       (recur nfa input-token sp nlist clist match)))))

(defn run-from-beginning
  "Simulates the NFA on an input sequence, starting always at the beginning of
   the input, and returns every match found from the starting position."
  ([{:keys [nfa pars]} input sp input-length]
   (run-from-beginning nfa (concat input [nil]) sp input-length
                       (conj (thread-list) (make-new-thread 0 pars))))
  ([nfa input sp input-length clist]
   (if (or (>= sp (inc input-length)) (empty? clist))
     []
     (let [{:keys [nlist match]} (run-one-step nfa (first input) sp clist)]
       (let [tail (lazy-seq
                    (run-from-beginning nfa (rest input) (inc sp)
                                        input-length nlist))]
         (if match
           (cons match tail)
           tail))))))

(def long-by-default? true)

(defn re-seq-pointers
  "Returns a lazy sequence of successive matches (not parts of the input seq,
   but pointers) of pattern in the input seq."
  ([re input]
   (re-seq-pointers (nfa re) input 0 (count input) long-by-default?))
  ([re input long?]
   (re-seq-pointers (nfa re) input 0 (count input) long?))
  ([automaton input sp input-length long?]
   (let [matches (run-from-beginning automaton input sp input-length)
         match ((if long? last first) matches)
         ;; (:sp match) is the string pointer to the end of the match.
         ;; msp can be equal to sp if the re can match 0 maps, like '.*'
         new-sp (if-let [msp (:sp match)]
                  (if (= msp sp) (inc sp) msp)
                  (inc sp))]
     (if (>= new-sp input-length)
       (if match [match] nil)
       (let [tail (lazy-seq
                    (re-seq-pointers automaton (drop (- new-sp sp) input)
                                     new-sp input-length long?))]
         (if match (cons match tail) tail))))))

(defn get-submatch
  "Return the part of the input that matched the n:th parenthesis of the
   regexp. Using n = 0 returns the whole match."
  [input n match]
  (let [getter (fn [f] (get (:saved match) (f (* 2 n))))
        start (getter identity)
        stop (getter inc)]
    (subvec input start stop)))

(defn re-seq
  "Returns a lazy sequence of successive matches of pattern in the input seq."
  ([re input] (re-seq re input long-by-default?))
  ([re input long?]
   (map #(get-submatch input 0 %) (re-seq-pointers re input long?))))
