(ns dialog-tool.ui.dropdown
  "Dropdown menu component using data attributes.
   
   Usage in HTML:
   <div data-dropdown-root>
     <button data-dropdown-button>
       <span data-dropdown-label>Select...</span>
     </button>
     <div data-dropdown-menu class=\"hidden\">
       <div data-dropdown-option=\"Option 1\">Option 1</div>
       <div data-dropdown-option=\"Option 2\">Option 2</div>
     </div>
   </div>")

(defn- query-all
  "Query all elements matching selector within root (or document)."
  ([selector]
   (array-seq (.querySelectorAll js/document selector)))
  ([root selector]
   (array-seq (.querySelectorAll root selector))))

(defn- query
  "Query single element matching selector within root."
  [root selector]
  (.querySelector root selector))

(defn- open-menu!
  [button menu]
  (-> menu .-classList (.remove "hidden"))
  (.setAttribute button "aria-expanded" "true"))

(defn- close-menu!
  [button menu]
  (-> menu .-classList (.add "hidden"))
  (.setAttribute button "aria-expanded" "false"))

(defn- toggle-menu!
  [button menu]
  (let [hidden? (-> menu .-classList (.contains "hidden"))]
    (if hidden?
      (open-menu! button menu)
      (close-menu! button menu))))

(defn- setup-dropdown!
  "Initialize a single dropdown root element."
  [root]
  (let [button  (query root "[data-dropdown-button]")
        menu    (query root "[data-dropdown-menu]")
        label   (query root "[data-dropdown-label]")
        options (query-all root "[data-dropdown-option]")]
    
    ;; Toggle on button click
    (.addEventListener button "click"
                       (fn [e]
                         (.stopPropagation e)
                         (toggle-menu! button menu)))
    
    ;; Handle option selection
    (doseq [option options]
      (.addEventListener option "click"
                         (fn [_]
                           (let [value (.getAttribute option "data-dropdown-option")]
                             (when label
                               (set! (.-textContent label) value))
                             (close-menu! button menu)))))
    
    ;; Close when clicking outside
    (.addEventListener js/document "click"
                       (fn [e]
                         (when-not (.contains root (.-target e))
                           (close-menu! button menu))))
    
    ;; Close on Escape key
    (.addEventListener js/document "keydown"
                       (fn [e]
                         (when (= (.-key e) "Escape")
                           (close-menu! button menu))))))

(defn init!
  "Initialize all dropdown components on the page."
  []
  (doseq [root (query-all "[data-dropdown-root]")]
    (setup-dropdown! root)))

