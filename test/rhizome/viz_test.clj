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

(def pause 2000)

(deftest test-viz
  (view-graph (keys g) g
    :node->descriptor (fn [n] {:label n})
    :edge->descriptor (fn [src dst] {:label dst}))
  (Thread/sleep pause)

  (view-graph (keys g) g
    :node->descriptor (fn [n] {:label n})
    :node->cluster identity
    :cluster->parent {:a :b})
  (Thread/sleep pause)

  (view-tree sequential? seq t-0
    :node->descriptor (fn [n] {:label (when (number? n) (str n))}))
  (Thread/sleep pause)

  (view-tree list? seq t-1
    :node->descriptor (fn [n] {:label (when (vector? n) n)}))
  (Thread/sleep pause)

  (view-tree list? seq t-1
    :node->descriptor (fn [n] {:label (when (vector? n) n)})
    :vertical? false)
  (Thread/sleep pause)

  (view-tree list? seq t-1
    :node->descriptor (fn [n] {:label (when (vector? n) n)})
    :options {:rankdir :RL})
  (Thread/sleep pause)

  

  )
