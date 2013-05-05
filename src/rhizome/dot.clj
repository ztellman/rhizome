(ns rhizome.dot
  (:require [clojure.string :as str]))

;;;

(def ^:private escapable-characters "|{}\"")

(defn- escape-string
  "Escape characters that are significant for the dot format."
  [s]
  (reduce
    #(str/replace %1 (str %2) (str "\\" %2))
    s
    escapable-characters))

;;;

(def ^:private default-options
  {:dpi 150})

(def ^:private default-node-options
  {:fontname "Monospace"})

(def ^:private default-edge-options
  {:fontname "Monospace"})

;;;

(def ^:private option-translations
  {:vertical? [:rankdir {true :TP, false :LR}]})

(defn translate-options [m]
  (->> m
    (map
      (fn [[k v]]
        (if-let [[k* f] (option-translations k)]
          [k* (f v)]
          [k v])))
    (into {})))

;;;

(defn ->literal [s]
  ^::literal [s])

(defn literal? [x]
  (-> x meta ::literal))

(defn unwrap-literal [x]
  (if (literal? x)
    (first x)
    x))

;;;

(defn- format-options-value [v]
  (cond
    (string? v) (str \" (escape-string v) \")
    (keyword? v) (name v)
    (coll? v) (if (literal? v)
                (str "\"" (unwrap-literal v) "\"")
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
    (format-options ", " options)
    "]"))

(defn format-label [label]
  (if-not (sequential? label)
    (if label
      (pr-str label)
      "")
    (->> label
      (map #(str "{ " (-> % format-label unwrap-literal) " }"))
      (interpose " | ")
      (apply str)
      ->literal)))

(defn- format-node [id {:keys [label shape] :as options}]
  (let [shape (or shape
                (when (sequential? label)
                  :record))
        label (format-label label)
        options (assoc options
                  :label label
                  :shape shape)]
    (str id "["
      (format-options ", " options)
      "]")))

;;;

(defn graph->dot
  "Takes a description of a graph, and returns a string describing a GraphViz dot file.

   Requires two fields: `nodes`, which is a list of the nodes in the graph, and `adjacent`, which
   is a function tha takes a node and returns a list of adjacent nodes."
  [nodes adjacent
   & {:keys [directed?
             vertical?
             options
             node->descriptor
             edge->descriptor]
      :or {directed? true
           vertical? true
           node->descriptor (constantly nil)
           edge->descriptor (constantly nil)}}]
  (let [node->id (memoize (fn [_] (gensym "node")))
        ]
    (apply str
      (if directed?
        "digraph"
        "graph")
      " {\n"

      ;; global options
      (str "graph["
        (->> (assoc options :vertical? vertical?)
          translate-options
          (format-options "\n"))
        "]\n")

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
             edge->descriptor
             options]
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
    (graph->dot nodes #(@node->children %)
      :directed? true
      :vertical? vertical?
      :options options
      :node->descriptor (comp node->descriptor second)
      :edge->descriptor (fn [a b] (edge->descriptor (second a) (second b))))))
