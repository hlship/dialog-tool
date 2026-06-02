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

(def ^:private jpackage-type
  "Native installer type for the current OS."
  (let [os (System/getProperty "os.name")]
    (cond
      (re-find #"(?i)mac"     os) "dmg"
      (re-find #"(?i)linux"   os) "deb"
      (re-find #"(?i)windows" os) "msi"
      :else (throw (ex-info "Unsupported OS for jpackage" {:os os})))))

(defn sanitize-version
  "Converts a git tag to a jpackage-compatible version string N[.N[.N[.N]]].
   Examples: 2.0-beta-18 → 2.0.18, 2.0-beta-17a → 2.0.17, v2.1.0 → 2.1.0"
  [tag]
  (let [v (-> tag
              (string/replace #"^v" "")
              (string/replace #"-alpha-" ".")
              (string/replace #"-beta-" ".")
              (string/replace #"[^0-9.]" ""))]
    (if (string/blank? v) "0.0.1" v)))

(defn package-zip
  "Builds CSS, uberjar, and zip archive for Homebrew / macOS distribution.
   Returns the zip file."
  [tag]
  (let [out-dir   (fs/file "out")
        class-dir (fs/file out-dir "classes")
        build-dir (fs/file out-dir "build")]
    (fs/delete-tree out-dir)
    (fs/create-dirs class-dir)
    (fs/create-dirs build-dir)
    (let [uber-file (fs/file build-dir (str "dialog-tool-" tag ".jar"))
          zip-file  (fs/file out-dir (str "dialog-tool-" tag ".zip"))]
      (sh "tailwindcss --minimize --map"
          "--input"  "public/style.css"
          "--output" (str (fs/file class-dir "public" "style.css")))
      (spit (fs/file class-dir "dialog-tool-version.txt") tag)
      (sh "clojure -T:build uber " (pr-str {:uber-file (str uber-file)
                                            :class-dir (str class-dir)}))
      (render-template "templates/dgt"
                       (fs/file build-dir "dgt")
                       {:uber-jar (fs/file-name uber-file)})
      (sh "chmod a+x" (fs/file build-dir "dgt"))
      (sh "cp -R" "LICENSE" "README.md" "CHANGES.md" build-dir)
      (perr "Writing: " [:bold zip-file] " ...")
      ;; Use system zip (not fs/zip) to preserve executable permissions
      (apply sh "zip" "-j" (str zip-file)
             (map str (fs/list-dir build-dir)))
      zip-file)))

(defn package
  "Builds CSS, uberjar, and native installer for the current platform
   (Linux → .deb, Windows → .msi, macOS → .dmg for local testing).
   Returns the installer file."
  [tag]
  (let [out-dir      (fs/file "out")
        class-dir    (fs/file out-dir "classes")
        build-dir    (fs/file out-dir "build")
        packages-dir (fs/file out-dir "packages")]
    (fs/delete-tree out-dir)
    (fs/create-dirs class-dir)
    (fs/create-dirs build-dir)
    (fs/create-dirs packages-dir)
    (let [uber-file (fs/file build-dir (str "dialog-tool-" tag ".jar"))
          version   (sanitize-version tag)]
      (sh "tailwindcss --minimize --map"
          "--input"  "public/style.css"
          "--output" (str (fs/file class-dir "public" "style.css")))
      (spit (fs/file class-dir "dialog-tool-version.txt") tag)
      (sh "clojure -T:build uber " (pr-str {:uber-file (str uber-file)
                                            :class-dir (str class-dir)}))
      (apply sh (concat
                  ["jpackage"
                   "--name"         "dgt"
                   "--app-version"  version
                   "--input"        (str build-dir)
                   "--main-jar"     (fs/file-name uber-file)
                   "--main-class"   "dialog_tool.main"
                   "--java-options" "--enable-native-access=ALL-UNNAMED"
                   "--type"         jpackage-type
                   "--dest"         (str packages-dir)]
                  (when (= jpackage-type "dmg")
                    ["--mac-package-identifier" "io.github.hlship.dialog-tool"])))
      (let [installer (first (fs/list-dir packages-dir))]
        (perr "Installer: " [:bold installer])
        installer))))

(defn sha256
  [zip-file]
  (-> (sh {:out :string
           :quiet? true}
          "shasum --algorithm 256 --binary" zip-file)
      (string/split #"\s+")
      first))
