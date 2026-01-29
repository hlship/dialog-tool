# Developer Notes
 
## Tests

Run tests with `clojure -M:test`.

## UI Notes

Developing the UI requires two additional windows.

### Shadow-CLJS

`npx shadow-cljs watch app`
- Compiles all JavaScript in (dev mode) to `out/public/js`
- Watches for changes and recompiles

`npx shadow-cljs release build`
- Used by release process to create single JavaScript output for distribution


### Tailwind

`tailwindcss -i public/style.css -o out/public/style.css --watch`
- Watches source folders, identifies CSS classes, regenerates `out/public/style.css`


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
- Don't try to close the window on quit; just refresh with a "You may close the window now" and shutdown.
- Clear out signals when no longer needed
- Present source file warnings/errors in a modal when detected?
- Only display the signals JSON in the UI when in development mode
- "Debug to here", runs, collects game state after each command (via @dynamic)
  - Show a delta from one command to the other (what globals, flags, per-objects changed)
- Consider *always* running from start, even when adding a new command at end?


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
