(ns rhizome.viz-test
  (:use
    rhizome.viz
    clojure.test))

(def g
  {:a [:b :c]
   :b [:c]
   :c [:a]})

(def t-0
  [[1 [2 3]] [4 [5]]])

(def t-1
  '([1 2] ([3 4] ([5 6 7]))))


(deftest test-viz
  (view-graph (keys g) g
    :node->descriptor (fn [n] {:label n}))
  (Thread/sleep 1000)

  (view-tree sequential? seq t-0
    :node->descriptor (fn [n] {:label (when (number? n) n)}))
  (Thread/sleep 1000)

  (view-tree list? seq t-1
    :node->descriptor (fn [n] {:label (when (vector? n) n)}))
  (Thread/sleep 1000))
