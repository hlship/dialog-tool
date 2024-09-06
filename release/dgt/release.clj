(ns dgt.release
  "Support for release new version of dialog-tool (see release.edn)."
  (:require [clj-commons.ansi :refer [pcompose perr] :rename {pcompose pout}]
            [babashka.process :as p]
            [babashka.fs :as fs]
            [clojure.string :as string]))

(defn sh
  [& commands]
  (let [base-opts {:err :string}
        [opts commands'] (if (-> commands first map?)
                           [(first commands) (rest commands)]
                           [nil commands])
        final-opts (merge base-opts opts)
        _ (when-not (:quiet? final-opts)
            (pout [:blue (string/join " " commands')]))
        {:keys [exit out err]} (apply p/shell final-opts commands')]
    (when-not (zero? exit)
      (perr [:red "Command failed (" exit "): "]
            [:bold err]))
    (if (string? out)
      (string/trim out)
      out)))

(defn package
  "Packages distribution files into a zip; the file is returned."
  [tag]
  (let [target-dir (fs/file "target")
        _ (fs/create-dirs target-dir)
        zip-file (fs/file target-dir (str "dialog-tool-" tag ".zip"))]
    (pout "Writing: " [:bold zip-file] " ...")
    (fs/zip zip-file
            ["src"
             "dgt"
             "bb.edn"
             "LICENSE"
             "README.md"
             "skein-ui/dist"])
    zip-file))

(defn sha256
  [zip-file]
  (-> (sh {:out :string
           :quiet? true}
          "shasum --algorithm 256 --binary" zip-file)
      (string/split #"\s+")
      first))
