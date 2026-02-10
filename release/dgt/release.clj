(ns dgt.release
  "Support for release new version of dialog-tool (see release.edn)."
  (:require [clj-commons.ansi :refer [pout perr]]
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
  (let [out-dir   (fs/file "out")
        build-dir (fs/file out-dir "build")
        _         (do
                    (fs/create-dirs out-dir)
                    (fs/delete-tree build-dir)
                    (fs/create-dirs build-dir))
        zip-file  (fs/file out-dir (str "dialog-tool-" tag ".zip"))]
    (sh "cp -R"
        "src"
        "dgt"
        "bb.edn"
        "LICENSE"
        "README.md"
        "CHANGES.md"
        "resources"
        build-dir)
    (sh "cp -R public" (fs/file build-dir "resources"))
    (-> (fs/file build-dir "resources" "version.txt")
        (spit tag))
    (sh "tailwindcss --minimize --map"
        "--input" "public/style.css"
        "--output" "out/build/resources/public/style.css")
    (perr "Writing: " [:bold zip-file] " ...")
    (fs/zip zip-file build-dir {:root "out/build"})
    zip-file))

(defn sha256
  [zip-file]
  (-> (sh {:out :string
           :quiet? true}
          "shasum --algorithm 256 --binary" zip-file)
      (string/split #"\s+")
      first))
