(ns pulumi-cljs.subnet
  "Tools for calculating and splitting subnets"
  (:require [clojure.zip :as zip]))

;; Implementation notes:

;; JavaScript numerics can safely represent all integers up to 2^53-1,
;; and therefore all IPv4 addresses (unsigned 32 bit ints). However,
;; JavaScript bitwise operations first cast their arguments to a 32
;; bit signed int, and are therefore unusable for manipulation of IPV4
;; addresses, which is why we make sure to use ES2020 BigInts for all
;; bit shift operations.

;; ClojureScript is not aware of BigInt types, but mathematical
;; operations still work fine provided all operands are BigInt
;; values. Mixed operands do not work, so some explicit casting is
;; needed.

(def ip-re (js/RegExp "(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})"))

(defn ip->num
  "Parse an IP string into a numeric."
  [ip]
  (let [[match p1 p2 p3 p4] (re-matches ip-re ip)]
    (when-not match
      (throw (ex-info (str  "Could not parse IP address " ip) {:ip ip})))
    (js/Number
      (+ (bit-shift-left (js/BigInt p1) (js/BigInt 24))
         (bit-shift-left (js/BigInt p2) (js/BigInt 16))
         (bit-shift-left (js/BigInt p3) (js/BigInt 8))
         (js/BigInt p4)))))

(defn num->ip
  "Convert a numeric into an IP string"
  [n]
  (let [n (js/BigInt n)
        mask (js/BigInt "0xFF")]
    (let [p4 (bit-and n mask)
          p3 (bit-and (bit-shift-right n (js/BigInt 8)) mask)
          p2 (bit-and (bit-shift-right n (js/BigInt 16)) mask)
          p1 (bit-and (bit-shift-right n (js/BigInt 24)) mask)]
      (str p1 "." p2 "." p3 "." p4))))

(def cidr-re (js/RegExp "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})"))

(defn parse-cidr
  "Parse a CIDR into a map with :ip and :mask keys, the values of which are numeric"
  [cidr]
  (let [[_ ip mask] (re-matches cidr-re cidr)]
    {:ip (ip->num ip)
     :mask (js/Number mask)}))

(defn cidr-string
  "Convert an CIDR map to a standardized CIDR string"
  [{:keys [ip mask]}]
  (str (num->ip ip) "/" mask))

(defn split
  "Given a CIDR map, return the two CIDRs resulting from its split as
  CIDR maps."
  [{:keys [ip mask]}]
  (when (<= (- 32 mask) 1)
    (throw (ex-info "Not enough address space to split" {})))
  [{:ip ip :mask (inc mask)}
   {:ip (+ ip (js/Math.pow 2 (- 32 (inc mask)))) :mask (inc mask)}])

;; Implementation strategy: Construct a Clojure Zipper and use it to
;; perform a depth-first walk of the binary tree formed by recursively
;; splitting subnets.

(defn- zipper
  "Construct a new unallocated zipper from the given CIDR"
  [cidr]
  (zip/zipper
    (fn [node] (and (not (:claimed node)) (< (:mask node) 31)))
    (fn [node] (or (:children node)
                   (split node)))
    (fn [node children] (assoc node :children children))
    cidr))

(defn- zip-top
  "Return the zipper with loc at the root"
  [loc]
  (if (nil? (zip/up loc))
    loc
    (zip-top (zip/up loc))))

(defn- allocate-1
  "Given a zipper, go to the top of the tree then perform a depth-first
  traversal. If an unclaimed cidr of the right mask size is found,
  return a zipper with loc at that node, annotated with :claimed key.

  If no such cidr exists, throw an exception."
  [tree mask]
  (loop [loc (zip-top tree)]
    (let [cidr (zip/node loc)]
      (cond
        ;; Success case
        (and (= mask (:mask cidr)) (not (:claimed cidr)) (not (:children cidr)))
        (zip/edit loc assoc :claimed true)

        ;; Failure case
        (zip/end? loc) (throw (ex-info (str "Could not allocate subnet with mask /"
                                         mask ", insufficent remaining space.")
                                {:tree loc}))
        ;; Recursive case
        :else (recur (zip/next loc))))))


(defn allocate
  "Given a CIDR and a sequence of subnet masks repeatedly apply
  allocate-1 until all subnets are allocated, returning a sequence of
  CIDR strings."
  [cidr subnets]
  (->> subnets
    (reductions allocate-1 (zipper (parse-cidr cidr)))
    (drop 1)
    (map zip/node)
    (map cidr-string)
    (doall)))
