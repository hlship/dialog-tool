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
- Color-blind indicators or mode
- Monitor the file system for (specific) changes, use a webhook, optional auto-replay-all on change
- Present source file warnings/errors in a modal when detected?
- Consider *always* running from start, even when adding a new command at end?
- add ability to paste in a transcript (or create a skein from a transcript)
- disable FAB if it not dgdebug engine 
- use core.cache (or memoize) for caching (of text diffs)
- provide a `favicon.ico`

## Releasing

- `git tag` and push *first*
  - the latest tag will be the version
- `bb release`
  - Builds the deployable bundle and uploads to GitHub
  - Need `gh` installed
  - Prints out info about release
  - Generates `out/dialog-tool.rb` from template
- Copy `out/dialog-tool.rb` to `hlship/homebrew-brew` repo
  - Commit and push -- it's live!
