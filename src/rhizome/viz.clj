(ns rhizome.viz
  (:use
    [rhizome.dot])
  (:require
    [clojure.java.shell :as sh]
    [clojure.java.io :as io])
  (:import
    [javax.imageio
     ImageIO]
    [javax.swing
     JFrame JLabel JScrollPane ImageIcon]
    [javax.script
     ScriptEngineManager]))

(defn create-frame
  "Creates a frame for viewing graphviz images.  Only useful if you don't want to use the default frame."
  [name]
  (delay
    (let [frame (JFrame. ^String name)
          image-icon (ImageIcon.)]
      (doto frame
        (.add (-> image-icon JLabel. JScrollPane.))
        (.setSize 1024 768)
        (.setDefaultCloseOperation javax.swing.WindowConstants/HIDE_ON_CLOSE))
      [frame image-icon])))

(defonce default-frame (create-frame "rhizome"))

(defn- send-to-front
  "Makes absolutely, completely sure that the frame is moved to the front."
  [^JFrame frame]
  (doto frame
    (.setExtendedState JFrame/NORMAL)
    (.setAlwaysOnTop true)
    .repaint
    .toFront
    .requestFocus
    (.setAlwaysOnTop false))

  ;; may I one day be forgiven
  (when-let [applescript (.getEngineByName (ScriptEngineManager.) "AppleScript")]
    (try
      (.eval applescript "tell me to activate")
      (catch Throwable e
        ))))

(defn view-image
  "Takes an `image`, and displays it in a window.  If `frame` is not specified, then the default frame will be used."
  ([image]
     (view-image default-frame image))
  ([frame image]
     (let [[^JFrame frame ^ImageIcon image-icon] @frame]
       (.setImage image-icon image)
       (.setVisible frame true)
       (java.awt.EventQueue/invokeLater
         #(send-to-front frame)))))

(defn dot->image
  "Takes a string containing a GraphViz dot file, and renders it to an image.  This requires that GraphViz
   is installed on the local machine."
  [s]
  (let [bytes (:out (sh/sh "dot" "-Tpng" :in s :out-enc :bytes))]
    (ImageIO/read (io/input-stream bytes))))

(defn save-image
  "Saves the given image buffer to the given filename. The default
file type for the image is png, but an optional type may be supplied
as a third argument."
  ([filename image] 
     (save-image filename "png" image))
  ([filename filetype image] 
     (ImageIO/write image filetype (io/file filename))))

(def
  ^{:doc "Takes a graph descriptor in the style of `graph->dot`, and returns a rendered image."}
  graph->image
  (comp dot->image graph->dot))

(def
  ^{:doc "Takes a graph descriptor in the style of `graph->dot`, and displays a rendered image."}
  view-graph
  (comp view-image dot->image graph->dot))

(def
  ^{:doc "Takes a tree descriptor in the style of `tree->dot`, and returns a rendered image."}
  tree->image
  (comp dot->image tree->dot))

(def
  ^{:doc "Takes a tree descriptor in the style of `tree->dot`, and displays a rendered image."}
  view-tree
  (comp view-image dot->image tree->dot))

