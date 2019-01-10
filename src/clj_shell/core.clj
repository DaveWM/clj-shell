(ns clj-shell.core
  (:require [clojure.java.io :as io]
            [clojure.string])
  (:import (java.awt.datatransfer DataFlavor StringSelection)
           (java.nio.file Files))
  (:refer-clojure :exclude [cat]))


(defonce *cwd (atom (io/file (.getAbsolutePath (io/file "")) "")))
(defonce *cwd-history (atom []))
(def home-dir (System/getProperty "user.home"))


(add-watch *cwd :cwd-history
           (fn [_ _ old-state _]
             (swap! *cwd-history conj old-state)))


(defmethod print-method java.io.File
  [^java.io.File f w]
  (print-simple (.getAbsolutePath f) w))


(def ^java.awt.datatransfer.Clipboard clipboard
  (.getSystemClipboard (java.awt.Toolkit/getDefaultToolkit)))


(defn paste []
  (.getData clipboard (DataFlavor/stringFlavor)))


(defn copy [s]
  (.setContents clipboard (StringSelection. (str s)) nil))


(defn ->file [path]
  (let [path-with-home-dir (clojure.string/replace path #"^~" home-dir)
        absolute?          (clojure.string/starts-with? path-with-home-dir "/")]
    (if absolute?
      (io/file path-with-home-dir)
      (io/file (.getAbsolutePath @*cwd) path-with-home-dir))))


(defn file-name [path]
  (-> path
      ->file
      .getName))


(defn file-type [path]
  (let [^java.io.File file (->file path)]
    (cond
      (.isDirectory file) :directory
      (Files/isSymbolicLink (.toPath file)) :symlink
      (.isFile file) :file)))


(defn exists? [path]
  (-> path
      ->file
      .exists))


(defn hidden? [path]
  (clojure.string/starts-with? (file-name path) "."))


(defn file-size [path]
  (.length (->file path)))


(defn file? [f]
  (instance? java.io.File f))


(defn ls
  ([] (ls (->file "")))
  ([path]
   (->> path
        ->file
        .listFiles
        (into []))))


(defn tree
  ([] (tree ""))
  ([path]
   (let [f (->file path)]
     (if (= :file (file-type f))
       f
       [f (map tree (ls f))]))))


(defn print-tree
  ([t] (print-tree t {}))
  ([t opts] (print-tree t opts 0))
  ([t {:keys [display-fn] :or {display-fn file-name} :as opts} indent]
   (when t
     (let [print-with-indent (fn [file i]
                               (apply println (conj (vec (repeat i " ")) (display-fn file))))]
       (if (sequential? t)
         (do (print-with-indent (first t) indent)
             (doseq [v (second t)]
               (print-tree v opts (inc indent))))
         (print-with-indent t indent))))
   nil))


(defn walk-tree [f t]
  (clojure.walk/postwalk
    (fn [x]
      (if (file? x)
        (f x)
        x))
    t))


(defn filter-tree [predicate t]
  (if (sequential? t)
    (let [[dir children] t
          filtered-children (->> children
                                 (keep (partial filter-tree predicate)))]
      (when-not (empty? filtered-children)
        [dir filtered-children]))
    (when (predicate t)
      t)))


(defn flatten-tree [t]
  (->> t
       (tree-seq sequential? second)
       (map #(if (sequential? %)
               (first %)
               %))))


(defn pwd []
  @*cwd)


(defn cd! [path]
  (let [^java.io.File dir (->file path)]
    (if (exists? dir)
      (reset! *cwd dir)
      (println "Path does not exist: " path))))


(defn up! []
  (swap! *cwd #(if-let [parent (.getParentFile %)]
                 parent
                 %)))


(defn back! []
  (when-let [prev-cwd (last @*cwd-history)]
    (reset! *cwd prev-cwd)
    (swap! *cwd-history (comp pop pop)))
  @*cwd)


(defn- unlines [xs]
  (clojure.string/join "\n" xs))


(defn head
  ([path] (head path 10))
  ([path n]
   (->> path
        ->file
        io/reader
        line-seq
        (take n)
        unlines)))


(defn tail
  ([path] (tail path 10))
  ([path n]
   (->> path
        ->file
        io/reader
        line-seq
        (take-last n)
        unlines)))


(def cat (comp slurp ->file))


(defn cp [source dest]
  (io/copy (->file source) (->file dest)))


(defn rm [path]
  (io/delete-file (->file path)))


(defn mv [source dest]
  (do (cp source dest)
      (rm source)))
