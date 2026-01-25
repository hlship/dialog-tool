# 2.0 -- UNRELEASED 2024

Rewritten in Babashka. Added the Skein for development and testing support.

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
