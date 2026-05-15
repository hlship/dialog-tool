# 2.0 -- UNRELEASED 2026

Rewritten in Babashka. Added the Skein for development and testing support.

Added trace mode to the Skein: trace command execution through the Dialog debugger,
with an interactive tree view, expand/collapse, and search (dgdebug engine only).

The Skein now automatically performs a Replay All when first launched.

Updated to use new dgdebug flags: `--no-header`, `--height -1`, `--width -1`, and `--numbered`.
Game output now wraps naturally to the browser window width rather than at a fixed 80 columns.

The Skein navbar is now responsive; button labels hide at narrow window widths, showing only icons.

Migrated the Skein web UI from the Datastar Clojure SDK to
[Hyper](https://github.com/dynamic-alpha/hyper), a reactive server-rendered
framework built on Datastar. The UI is now a single render function with
server-side actions instead of individual HTTP endpoints.

`dgt new` now includes `lib/dialog/test/unit.dg` for unit testing support.
Added `:test` source category to `dialog.edn`, loaded alongside `:debug` sources.

Added `dgt test` command: runs unit tests defined by the project.

# 1.4 -- 29 Sep 2020

Remove the `bless` command; it is now all done from the `test` command.

Fix building cover art options passed to `dialogc`.

# 1.3 -- 17 Sep 2020

`dgt test` now blesses failed tests by default

`dgt test --force` will automatically bless failed tests

`dgt build` now takes an optional format to output and can now build
as aa, c64, web, z5, z8, or zblorb.

All commands now include a `--debug` argument that outputs shell commands
as they are executed.

# 1.2 -- 9 Aug 2020

Can now limit tests to run with `dgt test <name>`.

Fixed a bug where the `:seed` from an overrides file was not applied by `dgt debug`.

Can't use `master` or `main` as the tag with a :github source.

# 1.1 -- 30 Jan 2020

Fixed a bug where the wrong input transcript would be fed into each test.

# 1.0 -- 28 Jan 2020

Initial public release.
