# Developer Notes
 
## Tests

Run tests with `bb test`.

## UI Notes

Developing the UI requires an additional window.

### Tailwind

`brew install tailwindcss`

`bb tailwind`
- Watches source folders, identifies CSS classes, regenerates `style.css`

### Datastar

- https://lllama.github.io/posts/data-bind-attr/


## TODO / Ideas / Plans/ Bugs

- The graph view (currently it's just a serial view, one path through the graph)
- Buttons to jump to next/prev command
- Search the Skein
- Add an indicator that there are invisible whitespace changes in a diff
- Recognize when the `dgdebug` command fails to launch entirely (currently, results in :unblessed as null)
- Color-blind indicators or mode
- Present source file warnings/errors in a modal when detected?
- add ability to paste in a transcript (or create a skein from a transcript)
- use core.cache (or memoize) for caching (of text diffs)

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
