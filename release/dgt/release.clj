(ns dgt.release
  "Support for release new version of dialog-tool (see release.edn)."
  (:require [clj-commons.ansi :refer [pout perr]]
            [selmer.parser :as selmer]
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

(defn render-template
  [source-file dest-file context]
  (let [content (selmer/render-file source-file context)]
    (spit dest-file content)))

(defn package
  "Packages distribution files into a zip; the file is returned."
  [tag]
  (let [out-dir (fs/file "out")
        class-dir (fs/file out-dir "classes")
        build-dir (fs/file out-dir "build")
        _ (do
            (fs/delete-tree out-dir)
            (fs/create-dirs out-dir)
            (fs/create-dirs class-dir)
            (fs/create-dirs build-dir))
        uber-file (fs/file build-dir (str "dialog-tool-" tag ".jar"))
        zip-file (fs/file out-dir (str "dialog-tool-" tag ".zip"))]
    (sh "tailwindcss --minimize --map"
        "--input" "public/style.css"
        "--output" (-> (fs/file class-dir "public" "style.css") str))
    (-> (fs/file class-dir "version.txt")
        (spit tag))
    (sh "clojure -T:build uber " (pr-str {:uber-file (str uber-file)
                                          :class-dir (str class-dir)}))
    (render-template "templates/dgt"
                     (fs/file build-dir "dgt")
                     {:uber-jar (fs/file-name uber-file)})

    (sh "chmod a+x" (fs/file build-dir "dgt"))

    (sh "cp -R"
        "LICENSE"
        "README.md"
        "CHANGES.md"
        build-dir)
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
