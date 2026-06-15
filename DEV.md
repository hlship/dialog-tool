¡¡# Developer Notes
 
## Tests

Run tests with `bb test`.

## UI Notes

Developing the UI requires an additional window.

### DaisyUI

`npm install`

### Tailwind

`bb tailwind`
- Watches source folders, identifies CSS classes, regenerates `style.css`

### Datastar

- https://lllama.github.io/posts/data-bind-attr/

## TODO / Ideas / Plans/ Bugs

- Color-blind indicators or mode
- add ability to paste in a transcript (or create a skein from a transcript)
- use core.cache (or memoize) for caching (of text diffs)
- save preference for proportional vs. fixed width in the skein file
- Currently, doesn't show dynamic state delta for root, because too much
  - Could filter out per-object flags and variables?
- Revisit storing active-knot-id inside tree, inside undo/redo stack
- Should Reload trigger a Replay All?
- Sometimes Replay All doesn't update the skein but Replay does

## Hyper

- doesn't do well when h/reactive body returns nil

## Releasing

- `git tag` and push the tag — the GitHub Actions release workflow triggers automatically
  - Builds native installers (.deb, .msi) and a zip archive (for Homebrew), then publishes a GitHub Release
- Update the Homebrew formula in `hlship/homebrew-brew` (`Formula/dialog-tool.rb`):
  - Set `version` to the new tag
  - Set `url` to point to the new zip asset
  - Set `sha256` to the SHA256 of the zip (shown on the GitHub Release page or via `shasum -a 256`)
  - Commit and push — it's live!
