# Developer Notes
 
## Tests

Run tests with `clojure -M:clojure:test`.

## UI Notes

Developing the UI requires an additional window.

### Tailwind

`brew install tailwindcss`

`tailwindcss -i public/style.css -o generated-resources/public/style.css --watch`
- Watches source folders, identifies CSS classes, regenerates `style.css`


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
- Monitor the file system for (specific) changes, use a webhook, optional auto-replay-all on change
- Present source file warnings/errors in a modal when detected?
- Consider *always* running from start, even when adding a new command at end?
- new-child is often not executing in the right game context (but maybe solution is to always start a fresh dgdebug session)
- modals don't cancel, add an SSE that fails repeats forever (from client side?)
- add ability to paste in a transcript (or create a skein from a transcript)
- Knot action to show complete @dynamic output in a modal popup
- Don't show FAB if it has no contents (debug not enabled)
- skein w/ frotz: need to export path to the dfrotz-skein-patch.dg

## Releasing

- `git tag` and push *first*
  - the latest tag will be the version
- `bb release`
  - Builds the deployable bundle and uploads to GitHub
  - Need `gh` installed
  - Prints out info for next step
- Copy `out/dialog-tool.rb` to `homebrew-brew` repo
  - Commit and push -- it's live!
