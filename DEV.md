# Developer Notes
 
## Tests

Run tests with `clojure -M:test`.

## UI Notes

Developing the UI requires an additional window.

### Tailwind

`brew install tailwindcss`

`tailwindcss -i public/style.css -o out/public/style.css --watch`
- Watches source folders, identifies CSS classes, regenerates `out/public/style.css`


### Datastar

- https://lllama.github.io/posts/data-bind-attr/


## TODO / Ideas / Plans/ Bugs

- The graph view (currently it's just a serial view, one path through the graph)
- Collapsable text (hide text, allow for quick navigation)
- Buttons to jump to next/prev command
- Search the Skein
- Jump to "nearest" unblessed node
- Add an indicator that there are invisible whitespace changes in a diff
- Recognize when the `dgdebug` command fails to launch entirely (currently, results in :unblessed as null)
- Detect/report network failures
- Color-blind indicators or mode
- Restart should rebuild the source file list, re-read the dialog.edn
- Monitor the file system for (specific) changes, use a webhook, optional auto-replay-all on change
- Present source file warnings/errors in a modal when detected?
- Consider *always* running from start, even when adding a new command at end?
- new-child is often not executing in the right game context (but maybe solution is to always start a fresh dgdebug session)
- modals don't cancel, add an SSE that fails repeats forever (from client side?)
- add ability to paste in a transcript (or create a skein from a transcript)
- Knot action to show complete @dynamic output in a modal popup

## Releasing

- `git tag` and push *first*
  - the latest tag will be the version
- `bb --config release.edn release`
  - Builds the deployable bundle and uploads to GitHub
  - Need `gh` installed
  - Prints out info for next step
- Edit `homebrew-brew/dialog-tool.rb` to update `url`, `sha`, and `version`
  - This is a different repo
  - Commit and push -- it's live!
