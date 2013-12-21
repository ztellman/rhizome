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
  {:dpi 100})

(def ^:private default-node-options
  {})

(def ^:private default-edge-options
  {})

;;;

(def ^:private option-translations
  {:vertical? [:rankdir {true :TP, false :LR}]})

(defn translate-options [m]
  (->> m
    (map
      (fn [[k v]]
        (if-let [[k* f] (option-translations k)]
          (when-not (contains? m k*)
            [k* (f v)])
          [k v])))
    (remove nil?)
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

(defn format-label [label]
  (cond
    (sequential? label)
    (->> label
      (map #(str "{ " (-> % format-label unwrap-literal) " }"))
      (interpose " | ")
      (apply str)
      ->literal)

    (string? label)
    label

    (nil? label)
    ""

    :else
    (pr-str label)))

(defn- format-options [m separator]
  (->> 
    (update-in m [:label] #(when % (format-label %)))
    (remove (comp nil? second))
    (map
      (fn [[k v]]
        (str (name k) "=" (format-options-value v))))
    (interpose separator)
    (apply str)))

(defn- format-edge [src dst {:keys [directed?] :as options}]
  (let [options (update-in options [:label] #(or % ""))]
    (str src
      (if directed?
        " -> "
        " -- ")
      dst
      "[" (format-options (dissoc options :directed?) ", ") "]")))

(defn- format-node [id {:keys [label shape] :as options}]
  (let [shape (or shape
                (when (sequential? label)
                  :record))
        options (assoc options
                  :label (or label "")
                  :shape shape)]
    (str id "["
      (format-options options ", ")
      "]")))

;;;

(def ^:private ^:dynamic *node->id* nil)
(def ^:private ^:dynamic *cluster->id* nil)

(defn- node->id [n]
  (*node->id* n))

(defn- cluster->id [s]
  (*cluster->id* s))

(defmacro ^:private with-gensyms
  "Makes sure the mapping of node and clusters onto identifiers is consistent within its scope."
  [& body]
  `(binding [*node->id* (or *node->id* (memoize (fn [_#] (gensym "node"))))
             *cluster->id* (or *cluster->id* (memoize (fn [_#] (gensym "cluster"))))]
     ~@body))

(defn graph->dot
  "Takes a description of a graph, and returns a string describing a GraphViz dot file.

   Requires two fields: `nodes`, which is a list of the nodes in the graph, and `adjacent`, which
   is a function that takes a node and returns a list of adjacent nodes."
  [nodes adjacent
   & {:keys [directed?
             vertical?
             options
             node->descriptor
             edge->descriptor
             cluster->parent
             node->cluster
             cluster->descriptor]
      :or {directed? true
           vertical? true
           node->descriptor (constantly nil)
           edge->descriptor (constantly nil)
           cluster->parent (constantly nil)
           node->cluster (constantly nil)
           cluster->descriptor (constantly nil)}
      :as graph-descriptor}]

  (with-gensyms
    (let [current-cluster (::cluster graph-descriptor)
          subgraph? (boolean current-cluster)
          cluster->nodes (when node->cluster
                           (dissoc (group-by node->cluster nodes) nil))
          cluster? (if cluster->nodes
                     (comp boolean cluster->nodes)
                     (constantly false))
          node? (set nodes)]

     (apply str
       (if current-cluster
         (str "subgraph " (cluster->id current-cluster))
         (if directed?
           "digraph"
           "graph"))
       " {\n"

       ;; global options
       (let [edge-options (:edge options)
             node-options (:node options)]
         (str
           ;; graph[...]
           "graph["
           (-> (merge default-options options)
             (update-in [:fontname] #(or % (when subgraph? "Monospace")))
             (assoc :vertical? vertical?)
             (dissoc :edge :node)
             translate-options
             (format-options ", "))
           "]\n"

           ;; node[...]
           "node["
           (-> node-options
             (update-in [:fontname] #(or % "Monospace"))
             (translate-options)
             (format-options ", "))
           "]\n"

           ;; edge[...]
           "edge["
           (-> edge-options
             (update-in [:fontname] #(or % "Monospace"))
             (translate-options)
             (format-options ", "))
           "]\n\n"))

       (interpose "\n"
         (concat
            
           ;; nodes
           (->> nodes
             (remove #(not= current-cluster (node->cluster %)))
             (map
               #(format-node (node->id %)
                  (merge
                    default-node-options
                    (node->descriptor %)))))

           ;; clusters
           (->> cluster->nodes
             keys
             (remove #(not= current-cluster (cluster->parent %)))
             (map
               #(apply graph->dot
                  nodes
                  adjacent
                  (apply concat
                    (assoc graph-descriptor
                      ::cluster %
                      :options (cluster->descriptor %))))))
            
           ;; edges
           (when-not subgraph?

             (->> nodes
                
               ;; filter out destinations that aren't in `nodes`, and differentiate
               ;; between nodes and clusters
               (mapcat
                 (fn [node]
                   (map vector
                     (repeat node)
                     (->> node
                       adjacent
                       (map
                         #(cond
                            (node? %) [:node %]
                            (cluster? %) [:cluster %]
                            :else nil))
                       (remove nil?)))))
                
               ;; format the edges
               (map (fn [[a [type b]]]
                      (format-edge
                        (node->id a)
                        (if (= :node type)
                          (node->id b)
                          (cluster->id b))
                        (merge
                          default-edge-options
                          {:directed? directed?}
                          (edge->descriptor a b)))))))
            
           ["}\n"]))))))

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
