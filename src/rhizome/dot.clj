(ns rhizome.dot
  (:require [clojure.string :as str]))

;;;

(def ^:private escapable-characters "|{}\"")

(defn- escape-string [s]
  (reduce
    #(str/replace %1 (str %2) (str "\\" %2))
    s
    escapable-characters))

(def ^:private edge-fields
  [:label
   :style
   :shape
   :ltail
   :lhead
   :arrowhead
   :fontname])

(def ^:private node-fields
  [:label
   :fontcolor
   :color
   :width
   :height
   :fontname
   :arrowhead
   :style
   :shape
   :peripheries])

(def ^:private default-options
  {:dpi 150})

(def ^:private default-node-options
  {:fontname "Monospace"})

(def ^:private default-edge-options
  {:fontname "Monospace"})

(defn- format-options-value [v]
  (cond
    (string? v) (str \" (escape-string v) \")
    (keyword? v) (name v)
    (coll? v) (if (= ::literal (-> v meta :tag))
                (str "\"" (first v) "\"")
                (str "\""
                  (->> v
                    (map format-options-value)
                    (interpose ",")
                    (apply str))
                  "\""))
    :else (str v)))

(defn- format-options [separator m]
  (->> m
    (remove (comp nil? second))
    (map
      (fn [[k v]]
        (str (name k) "=" (format-options-value v))))
    (interpose ", ")
    (apply str)))

(defn- format-edge [src dst options]
  (str src " -> " dst "["
    (->> edge-fields 
      (select-keys options)
      (format-options ", "))
    "]"))

(defn- format-node [id {:keys [label shape] :as options}]
  (let [shape (or shape
                (when (sequential? label)
                  :record))
        label (if-not (sequential? label)
                (or label "")
                ^::literal
                [(str "{"
                   (->> label
                     (map pr-str)
                     (map escape-string)
                     (interpose " | ")
                     (apply str))
                   "}")])
        options (assoc options
                  :label label
                  :shape shape)]
    (str id "["
     (->> node-fields 
       (select-keys options)
       (format-options ", "))
     "]")))

;;;

(defn graph->dot
  "Takes a description of a graph, and returns a string describing a GraphViz dot file.

   The two required fields are `nodes`, which is a list of nodes in the graph, and `adjacent`,
   which is a function that takes a node, and returns a list of adjacent nodes."
  [& {:keys [directed?
             vertical?
             nodes
             adjacent
             options
             node->descriptor
             edge->descriptor]
      :or {directed? true
           vertical? true
           node->descriptor (constantly nil)
           edge->descriptor (constantly nil)}}]
  (let [node->id (memoize (fn [_] (gensym "node")))]
    (apply str
      (if directed?
        "digraph"
        "graph")
      " {\n"

      ;; global options
      (when-not (empty? options)
        (str "graph[" (format-options "\n" options) "]\n"))

      (interpose "\n"
        (concat
          
          ;; nodes
          (map
            #(format-node (node->id %)
               (merge
                 default-node-options
                 (node->descriptor %)))
            nodes)
          
          ;; edges
          (->> nodes
            (mapcat #(map vector (repeat %) (adjacent %)))
            (map (fn [[a b]] (format-edge (node->id a) (node->id b)
                               (merge
                                 default-edge-options
                                 (edge->descriptor a b))))))

          ["}\n"])))))

(defn tree->dot
  "Like tree-seq, but returns a string containing a GraphViz dot file.  Additional options
   mimic those in graph->dot."
  [branch? children root
   & {:keys [vertical?
             node->descriptor
             edge->descriptor]
      :or {vertical? true
           node->descriptor (constantly {:label ""})
           edge->descriptor (constantly nil)}}]
  (let [node->children (atom {})
        nodes (tree-seq
                (comp branch? second)
                (fn [x]
                  (swap! node->children assoc x
                    (map vector
                      (repeatedly #(Object.))
                      (children (second x))))
                  (@node->children x))
                [(Object.) root])]
    (graph->dot
      :directed? true
      :nodes nodes
      :adjacent #(@node->children %)
      :vertical? vertical?
      :node->descriptor (comp node->descriptor second)
      :edge->descriptor (fn [a b] (edge->descriptor (second a) (second b))))))
