¡¡# Developer Notes
 
## Tests

Run tests with `bb test`.

## UI Notes

Developing the UI requires an additional window.

### DaisyUI

`npm install`

### Tailwind

`brew install tailwindcss`

`bb tailwind`
- Watches source folders, identifies CSS classes, regenerates `style.css`

### Datastar

- https://lllama.github.io/posts/data-bind-attr/

## TODO / Ideas / Plans/ Bugs

- The graph view (currently it's just a serial view, one path through the graph)
- Color-blind indicators or mode
- add ability to paste in a transcript (or create a skein from a transcript)
- use core.cache (or memoize) for caching (of text diffs)
- when adding a new command w/ source errors:
  - source error dialog is raised
  - source error dialog does dismiss when replay all is successful
  - should re-focus on the text input field
  - hitting enter in the field does not submit (because field is unchanged?)
- save preference for proportional vs. fixed width in the skein file
- page looks odd when running a new skein against a sizable project
  - blank space for the START knot
  - no modal progress dialog
  - then it blips into place
  - might only be an issue for initial launch (in REPL), but won't that affect deployed all?
  - possibly its cost of starting a Thread and/or initializing core.async
- Currently doesn't show dynamic state delta for root, because too much
  - Could filter out per-object flags and variables?
- Be more consistent in how buttons are applied to modal dialogs
- Something is wonky with text search
- Do we really need lucene?
- Revisit storing active-knot-id inside tree, inside undo/redo stack
- not displaying accels on operations toolbar items

## Hyper

- doesn't do well when h/reactive body returns nil

## Releasing

- `git tag` and push *first*
  - the latest tag will be the version
- `bb release`
  - Builds the deployable bundle and uploads to GitHub
  - Need `gh` installed
  - Prints out info about release
  - Generates `out/dialog-tool.rb` from template
- Copy `out/dialog-tool.rb` to `hlship/homebrew-brew` repo (`/Formulas` directory) 
  - Commit and push -- it's live!
