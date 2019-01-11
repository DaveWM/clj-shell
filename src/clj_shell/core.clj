(ns clj-shell.core
  "This ns contains the core api for clj-shell. It is designed to be used from the repl.
   Most of the function names mirror the names of unix shell commands (ls, mv!, etc.), but there are also some functions that have no analogous unix command (e.g. paste, up!, etc.).

   Note: all 'path' arguments can be either string file paths (absolute, or relative to the current working directory) or java.io.File objects. Using '~' for the home directory is allowed."
  (:require [clojure.java.io :as io]
            [clojure.string])
  (:import (java.awt.datatransfer DataFlavor StringSelection)
           (java.nio.file Files))
  (:refer-clojure :exclude [cat find]))


(defonce *cwd (atom (io/file (.getAbsolutePath (io/file "")) "")))
(alter-meta! #'*cwd assoc :doc "Atom containing the current working directory, used only by functions in this ns.
Note that this is completely separate from the current working directory of the application.
Avoid swap!-ing or reset!-ing this atom, use cd!, up!, or back! instead.")
(defonce *cwd-history (atom []))
(alter-meta! #'*cwd-history assoc :doc "Atom containing the history of the *cwd atom, not including the current value.
It is recommended not to update this atom, and treat it as read-only.")
(def home-dir (System/getProperty "user.home"))


(add-watch *cwd :cwd-history
           (fn [_ _ old-state _]
             (swap! *cwd-history conj old-state)))


(defmethod print-method java.io.File
  [^java.io.File f w]
  (print-simple (.getAbsolutePath f) w))


(def ^java.awt.datatransfer.Clipboard clipboard
  (.getSystemClipboard (java.awt.Toolkit/getDefaultToolkit)))


(defn paste
  "Returns the data in the clipboard as a string."
  []
  (.getData clipboard (DataFlavor/stringFlavor)))


(defn copy!
  "Replaces the clipboard with the given string s."
  [s]
  (.setContents clipboard (StringSelection. (str s)) nil))


(defn ->file
  "Coerces the given path to a java.io.File object. If given a file object, will return it unchanged."
  [path]
  (let [path-with-home-dir (clojure.string/replace path #"^~" home-dir)
        absolute?          (clojure.string/starts-with? path-with-home-dir "/")]
    (if absolute?
      (io/file path-with-home-dir)
      (io/file (.getAbsolutePath @*cwd) path-with-home-dir))))


(defn file-name
  "Returns the file name for the file at the given path."
  [path]
  (-> path
      ->file
      .getName))


(defn file-path
  "Returns the absolute file path for the file at the given path."
  [path]
  (-> path
      ->file
      .getAbsolutePath))


(defn file-type
  "Returns the file type of the given file/path."
  [path]
  (let [^java.io.File file (->file path)]
    (cond
      (.isDirectory file) :directory
      (Files/isSymbolicLink (.toPath file)) :symlink
      (.isFile file) :file)))


(defn exists?
  "Returns true if there is a file at the given path, false otherwise."
  [path]
  (-> path
      ->file
      .exists))


(defn hidden?
  "Returns true if the file at the given path is hidden (i.e. it is a dotfile), false otherwise."
  [path]
  (clojure.string/starts-with? (file-name path) "."))


(defn file-size
  "Returns the size, in bytes, of the file at the given path."
  [path]
  (.length (->file path)))


(defn matches
  "Returns a predicate which applies `match-value-fn` to its input and compares it to `value`, using the `compare` fn."
  [value match-value-fn compare]
  (fn [x]
    (compare value (match-value-fn x))))


(defn matches-exactly
  "Returns a predicate that transforms its input using `match-value-fn`, and returns true iff the result is equal to `value`."
  [value match-value-fn]
  (matches value match-value-fn =))


(defn matches-regex
  "Returns a predicate that transforms its input using `match-value-fn`, and returns true iff `regex` matches the result."
  [regex match-value-fn]
  (matches regex match-value-fn re-find))


(defn file?
  "Returns true if f is a java.io.File object, false otherwise."
  [f]
  (instance? java.io.File f))


(defn ls
  "Lists the files in the directory at the given path. Will use the current working directory (*cwd) if no argument is provided.
   Returns a seq of java.io.File objects."
  ([] (ls (->file "")))
  ([path]
   (->> path
        ->file
        .listFiles
        (into []))))


(defn tree
  "Returns a tree of all the files and directories under the given path.
   The returned tree is in the format [directory '(file1 file2 [subdirectory '(file3)])].
   Will no follow symlinks."
  ([] (tree ""))
  ([path]
   (let [f (->file path)]
     (if (= :file (file-type f))
       f
       [f (map tree (ls f))]))))


(defn print-tree
  "Prints a tree, presumably returned from the tree fn, in a more readable format.

  Opts:
  display-fn - single argument function, taking a java.io.File object, called for each file in the tree. Returns what should be printed for the given file."
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


(defn walk-tree
  "Walks the given tree t, presumably returned from the tree fn, and transforms each file by running it through f."
  [f t]
  (clojure.walk/postwalk
    (fn [x]
      (if (file? x)
        (f x)
        x))
    t))


(defn filter-tree
  "Walks the given tree t, presumably returned from the tree fn, and filters out any files not matching the given predicate.
   Will remove any directories with no children from the returned tree."
  [predicate t]
  (if (sequential? t)
    (let [[dir children] t
          filtered-children (->> children
                                 (keep (partial filter-tree predicate)))]
      (when-not (empty? filtered-children)
        [dir filtered-children]))
    (when (predicate t)
      t)))


(defn flatten-tree
  "Returns a seq of all files (including directories) in the given tree"
  [t]
  (->> t
       (tree-seq sequential? second)
       (map #(if (sequential? %)
               (first %)
               %))))


(defn find
  "Returns a seq of files under the directory at `path` (including those in subdirectories) that pass the given predicate.
  Will use the current working directory (*cwd) as `path` if no argument is provided.

  The 'matches*' fns work well for composing the `predicate`, for example:
  ```
  (find (matches-exactly \"project.clj\" file-name))
  ```"
  ([predicate] (find predicate ""))
  ([predicate path]
   (->> (tree path)
        flatten-tree
        (filter predicate))))


(defn pwd
  "Returns the current working directory, i.e. the value of the *cwd atom."
  []
  @*cwd)


(defn cd!
  "Changes the current working directory to the given path. Note: call back! or up! instead of passing '..' or '-' to this fn."
  [path]
  (let [^java.io.File dir (->file path)]
    (if (exists? dir)
      (reset! *cwd dir)
      (println "Path does not exist: " path))))


(defn up!
  "Moves the current working directory up one level."
  []
  (swap! *cwd #(if-let [parent (.getParentFile %)]
                 parent
                 %)))


(defn back!
  "Goes back to the previous current working directory (if there is one)."
  []
  (when-let [prev-cwd (last @*cwd-history)]
    (reset! *cwd prev-cwd)
    (swap! *cwd-history (comp pop pop)))
  @*cwd)


(defn- unlines [xs]
  (clojure.string/join "\n" xs))


(defn head
  "Returns a string of the first n lines of the file at the given path. Returns 10 lines if n is not provided."
  ([path] (head path 10))
  ([path n]
   (->> path
        ->file
        io/reader
        line-seq
        (take n)
        unlines)))


(defn tail
  "Returns a string of the last n lines of the file at the given path. Returns 10 lines if n is not provided."
  ([path] (tail path 10))
  ([path n]
   (->> path
        ->file
        io/reader
        line-seq
        (take-last n)
        unlines)))


(def cat
  "Returns a string of the whole contents of the file at the given path."
  (comp slurp ->file))


(defn cp!
  "Copies a file from source to dest."
  [source dest]
  (io/copy (->file source) (->file dest)))


(defn rm!
  "Removes the file at the given path."
  [path]
  (io/delete-file (->file path)))


(defn mv!
  "Moves a file from source to dest."
  [source dest]
  (do (cp! source dest)
      (rm! source)))
