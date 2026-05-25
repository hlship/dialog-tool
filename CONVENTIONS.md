# Coding Conventions

This document formalizes the architectural rules and patterns established in this codebase.
It is grounded in concrete examples from the source and is intended to guide future development.

---

## 1. Namespace Layering

The UI layer is organized into four tiers. Dependencies flow strictly downward — upper tiers
may require lower tiers, but **never the reverse**.

| Tier | Namespaces | Responsibility |
|---|---|---|
| **Model** | `skein.tree`, `skein.session`, `skein.file`, `skein.process`, `skein.dynamic`, `skein.trace`, `skein.search` | Pure data manipulation, subprocess I/O, persistence. No UI concerns. |
| **Controller** | `skein.ui.actions` | Orchestrates mutations across cursors and model functions. No hiccup, no Ring, no CSS. |
| **Shared** | `skein.ui.common`, `skein.ui.utils`, `skein.ui.js` | Cursor accessors, rendering utilities, JS-effect wrappers. |
| **Renderer / Widget** | `skein.ui.app`, `skein.ui.modals`, `skein.ui.trace_view`, `skein.ui.components/*` | Hiccup renderers and reusable structural components. |

**Invariant:** Renderers may `require` actions; `actions` must never `require` renderers.
The dependency graph must be a DAG pointing model-ward.

---

## 2. Action Handler Rules (`h/action`)

`h/action` is the boundary between client events and server-side Clojure. Keep its body thin.

### Rules

- **One call per action.** The body of `h/action` should be a single call to an `actions/*`
  function (or a local `do-*` helper in `modals.clj`) whenever the logic involves state
  mutation. Logic must not accumulate inside `h/action`.

- **Always name actions.** Include `{:as "verb:noun"}` on every `h/action` call. This name
  is the auditable identity of the operation and aids logging and debugging.
  Use a `"namespace:verb"` convention for actions scoped to a subsystem
  (e.g., `"trace:toggle-expanded"`, `"edit-command:submit"`).

- **Form submissions.** Extracting fields from `$form-data` inline is acceptable, but the
  extracted values must be passed immediately to an `actions/*` function — no further logic
  inline.

### Acceptable exceptions

A single, self-documenting `swap!` or `reset!` may remain inline when:
- It touches only one cursor.
- Its intent is obvious from the action name alone.
- No coordination with other cursors or side effects is required.

```clojure
;; Acceptable inline — single cursor, obvious intent
(h/action {:as "bless"} (actions/bless id))

;; Acceptable inline — one swap, no coordination
(h/action {:as "set-active-knot"}
          (swap! (session-cursor) session/set-active-knot id)
          (js/focus-if-leaf! (session-cursor) id))

;; Move to actions/* — touches multiple cursors, has branching logic
;; Do NOT expand this inline.
```

---

## 3. Cursor Rules

### Definition

All `h/global-cursor` definitions live exclusively in `skein.ui.common`.

```clojure
;; skein/ui/common.clj — the only place cursors are defined
(defn session-cursor [] (h/global-cursor :session))
(defn modal-cursor   [] (h/global-cursor :modal))
(defn search-cursor  [] (h/global-cursor :search))
```

### Cursors are functions, not vars

Cursors are **zero-argument functions**, not top-level vars. This prevents stale captures
when the Hyper app-state atom is reset between requests. Always call `(session-cursor)`,
never bind the result at namespace load time.

```clojure
;; Correct — cursor resolved at call time
(swap! (session-cursor) session/bless id)

;; Wrong — stale reference if app-state is reset
(def *session (session-cursor))
```

### Cursors are required, never passed

Any namespace that needs a cursor declares it via `(:require ... :refer [session-cursor ...])`.
**Cursors are never passed as function arguments.** If a function needs a cursor, it requires
`skein.ui.common` and calls the accessor directly.

```clojure
;; Correct
(ns skein.ui.actions
  (:require [skein.ui.common :refer [session-cursor modal-cursor]]))

(defn bless [id]
  (swap! (session-cursor) session/bless id))

;; Wrong — do not thread cursors through function parameters
(defn bless [*session id]
  (swap! *session session/bless id))
```

### Deref at point of use

Dereference cursors as close to the use as possible, not at the top of a function.

```clojure
;; Correct — deref where the value is consumed
(let [{:keys [error]} @(session-cursor)] ...)

;; Avoid — deref up front obscures when the value was captured
(let [session @(session-cursor)
      ...many lines of setup...] ...)
```

---

## 4. The Actions Namespace (`skein.ui.actions`)

`skein.ui.actions` is the **sole controller namespace**. It is the only place that
orchestrates mutations across multiple cursors simultaneously.

### Responsibilities

- Translate user intent into model operations (`session/*`, `tree/*`, etc.).
- Manage modal lifecycle (`init-modal`, `dismiss-modal`).
- Emit JS side effects via `skein.ui.js`.
- Log every public action via `(env/log-action ...)` as the first statement.

### Reusable coordination primitives defined here

| Function | Purpose |
|---|---|
| `init-modal` | Open a modal by type with initial key/value pairs |
| `flash!` | Schedule a transient status message |
| `complete-session-operation` | Terminal step for async ops: apply flash or source error |

### What does NOT belong here

- Hiccup / HTML rendering of any kind.
- CSS class logic.
- Ring request/response handling.
- `h/action` calls (those live in renderers).

---

## 5. DRY Helpers — Where Things Live

Before writing new logic, check whether a helper already exists in one of these locations.

### `skein/ui/common.clj` — shared cursor + error helpers

Pure functions and cursor accessors shared across the controller and renderer tiers.

| Function | Purpose |
|---|---|
| `session-cursor` / `modal-cursor` / `search-cursor` | Cursor accessors |
| `normalize-input` | Trim and nil-blank strings from form input |
| `setup-source-error` | Open source-error modal and strip `:error` from session |
| `maybe-apply-source-error` | Terminal pipeline step: apply source error if present |

`maybe-apply-source-error` is designed for use at the end of `->` pipelines:

```clojure
(-> session
    session/check-for-changed-sources
    (session/command! parent-knot-id normalized)
    common/maybe-apply-source-error)
```

### `skein/ui/utils.clj` — pure rendering utilities

`classes` — combine Tailwind class strings, collapsing extra whitespace:

```clojure
(classes "btn btn-xs btn-primary tooltip" (str "tooltip-" tooltip-dir))
```

### `skein/ui/js.clj` — JS side effects

All `effects/execute-script!` calls are wrapped here. Never call `execute-script!` directly
from action handlers or renderers.

### `skein/ui/components/` — reusable structural components

If a UI pattern appears in two or more renderers, it graduates to a component here.
Current components: `modal.clj` (shell + cancel/ok buttons), `dropdown.clj`, `new_command.clj`.

---

## 6. Error Handling Convention

### Session-level errors (source file errors)

Operations that can produce a source-file error embed `:error` in the returned session map.
The terminal step of any such pipeline calls `common/maybe-apply-source-error`, which opens
the source-error modal and strips `:error` from the session before it is stored.

### Modal-level errors (validation errors)

Validation failures (duplicate label, bad input) are reported by swapping `:error` into the
modal cursor — never by throwing an exception.

```clojure
(swap! (modal-cursor) assoc :error "Label already exists")
```

### Async operation completion

`complete-session-operation` handles the success/error fork for async ops (replay, etc.):

```clojure
(defn complete-session-operation [session flash-message]
  (let [{:keys [error]} session]
    (when-not error (reset! *pending-flash flash-message))
    (cond-> session
      error (common/setup-source-error error))))
```

---

## 7. Open Questions / Known TODOs

These are known rough edges to resolve in future refactoring:

1. **`{:as}` consistency.** A small number of `h/action` calls omit the `{:as "name"}`
   option (e.g., FAB toggles, `quit-modal`). Audit and add names uniformly.

2. **`session/get-knot` vs `tree/get-knot`.** When a `session` value is in scope, always
   use `session/get-knot session id`. Use `tree/get-knot` only in model-layer code
   (`session.clj`, `tree.clj`) that operates directly on a `tree` map with no session.
   Similarly, prefer `session/get-active-knot-id` and `session/set-active-knot-id` over
   raw `get-in`/`assoc-in` on `[:tree :active-knot-id]`.

3. **`*app-state` threading.** `quit` and `quit-modal` require `*app-state` for the
   shutdown function, which is threaded manually through `skein-page → navbar → quit`.
   This is a layering violation (a renderer is threading infrastructure state). Needs
   a cleaner injection mechanism.

4. **`replay-all` circular dependency.** The `replay-all` function is injected into the
   session map at startup in `service.clj` to break a circular dependency between
   `actions` and `session`. This is marked with a TODO comment and should be resolved
   by introducing a protocol or a separate coordination namespace.
