# 2.0-beta-18 -- 2 Jun 2026

Now generates installers for Windows and Linux.

Accessibility improvements - icons to supplement colors, better ARIA control descriptions.

Network error modal no longer is displayed when the Skein is shutdown properly (via the Quit command).

Navigation graph and transcript both scroll to active knot when a count (new or error) is clicked in the status counts.

# 2.0-beta-17 -- 31 May 2026

# 2.0-beta-16 -- 28 May 2026

Upgraded full-text search to EnglishAnalyzer with stemming, Lucene Highlighter for
accurate snippet highlighting, and stemmed prefix queries.

Simplified bless actions: removed standalone "Bless" action; "Bless Changes"
(formerly "Bless to Here") now blesses all changes visible on the page.

Fixed highlighted predicates in trace view being unreadable.

Fixed edge cases related to active knot vs. command input field focus.

Fixed incorrect field focus when adding a new command.

Fixed new skein replay to only replay the root knot.

# 2.0-beta-15 -- 25 May 2026

# 2.0-beta-14 -- 22 May 2026

Full-text knot search (⌘F): searches command text and responses across all
knots with highlighted snippets.

Overhauled keyboard accelerators: updated and expanded shortcuts for toolbar
operations and knot navigation.

Improved ANSI SGR handling for dgdebug output: SGR 50 (monospace), updated
color mappings, and correct conversion to both styled HTML and pseudo-markers.
Stack-based effect tracking with support for 24-bit RGB colors (SGR 38;2;R;G;B)
and SGR 39 (default foreground).

Adds a Reload Skein toolbar action that re-reads the skein file from disk.
Reload is undoable and disabled for new skeins until after the first save.

Shows a loading spinner during the initial replay-all on skein launch.

# 2.0-beta-13 -- 20 May 2026

Redesigned knot interaction: clicking a knot makes it the _active knot_, highlighted
with a blue left border. A persistent secondary operations toolbar replaces the
per-knot dropdown menu.

New keyboard accelerators for all toolbar operations and knot navigation
(⌘↑/↓ parent/child, ⌘⇧↑/↓ first/last knot, ⌘B bless-to-here, ⌘Y replay,
⌘A new child, ⌘D delete, ⌘K lock).

Undo/redo now correctly restores the active knot (active-knot-id is stored
inside the tree and captured by the undo stack).

Dark mode support throughout the UI via DaisyUI semantic color tokens.

# 2.0-beta-12 -- 19 May 2026

Rewritten in Clojure. 

Added the Skein for development and testing support.

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
