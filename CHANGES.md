# 2.0 -- UNRELEASED

This is a big release, nearly two years in the making. The project has been rewritten in
Clojure, and can now run on OS X, Linux, or Windows.

The major new component is the Skein; a visual (web-based) tool for
authoring, debugging, and maintaining your project. The Skein is inspired by the tool
of the same name built into Inform 7 and functions similarly -- it maintains a transcript 
of all your different paths through your project, allowing you to test new behavior as you
write it, and verify that old behaviors don't change unexpectedly.

Further, the Skein vastly extends the abilities of Dialog's built-in debugger; it provides a way
to visually see how the dynamic state of the world changes after each command, as well as
a view into how each line of command input is processed, as a tree of Dialog queries.

Other notable changes:

* `dgt test` added: runs unit tests 
* Can now specify sources as individual files, not just directories
* Added `dgt new` command to create a new project
* Added `dgt run` command to build and run project in Frotz or Dumb Frotz
* Added `dgt bundle` to build and package a project as a Zip file
* Can now build for multiple targets (e.g., aamachine and zblorb)

[Changes](https://github.com/hlship/dialog-tool/pulls?q=is%3Apr+is%3Aclosed)

# 1.4 -- 29 Sep 2020

Remove the `bless` command; it is now all done from the `test` command.

Fix building cover art options passed to `dialogc`.

# 1.3 -- 17 Sep 2020

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
