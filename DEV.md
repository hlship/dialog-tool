# Developer Notes

## Datastar notes

- Datastar does not play well with Google Closure and there's no real API outside of `data-...` attributes
- Datastar (for now) loads from a CDN and is not visible to ClojureScript

## Cursor

- Been using Cursor to help set up ClojureScript support and ease me into VSCode / Calva
- Calva REPL: When running a service, the HttpKit service writes to the REPL's Terminal output, not to the Calva REPL output file.
- Agent note: the skein-ui folder contains the Svelte version of the app that we're replacing with Datastar.  It is read-only and still exists only for reference.


## TODO / Ideas / Plans/ Bugs

- Visual feedback when the skein changes
- The graph view (currently it's just a serial view, one path through the graph)
- Collapsable text (hide text, allow for quick navigation)
- Buttons to jump to next/prev command
- Animation when adding/removing nodes
- Search the Skein
- Jump to "nearest" unblessed node
- Visual feedback is a problem, because the round-trip is often just milliseconds.
- Add an indicator that there are invisible whitespace changes in a diff
- Recognize when the `dgdebug` command fails to launch entirely (currently, results in :unblessed as null)
- Detect/report network failures
- Shutdown the skein window (and server)
- Color-blind indicators or mode
- Restart should rebuild the source file list, re-read the dialog.edn
- Monitor the file system for (specific) changes, use a webhook, optional auto-replay-all on change

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
