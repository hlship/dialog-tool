# Developer Notes

## TODO / Ideas / Plans/ Bugs

- Visual feedback when the skein changes
- The graph view (currently it's just a serial view, one path through the graph)
- Collapsable text
- Buttons to jump to next/prev command
- Animation when adding/removing nodes
- Search the Skein
- Jump to "nearest" unblessed node
- Visual feedback is a problem, because the round-trip is often just milliseconds.
- Add an indicator that there are invisible whitespace changes in a diff
- Recognize when the `dgdebug` command fails to launch entirely (currently, results in :unblessed as null)
- Detect/report network failures
- Shutdown the skein window (and server)

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