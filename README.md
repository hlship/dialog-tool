# dgt - Dialog Tool

`dgt` is a tool to assist in the development of games
written in [Dialog](https://linusakesson.net/dialog/index.php).

`dgt` simplifies development, it allows you to specify the details of your project,
including what individual source files to use, and then provides commands to:

- run your game in the Dialog debugger
- run tests and identify failures
- review failed tests and "bless" updates
- build a compiled game for distribution
- more to come ...

Unlike many of my projects, this is _really_ for personal use:

- No releases, just the repo
- Subject to breaking change at any time
- Written in [Joker](https://github.com/candid82/joker) (the ultimate scripting language) which you almost certainly don't know

## dialog.edn

Your project must have a `dialog.edn` file at the root.

This file identifies the sources in your project as well
as other things.

```
{:output-format :z8
 :name "monty-haul"
 :story-sources ["src/*.dg"]
 :debug-sources ["debug/*.dg"
                 "/usr/local/share/dialog-if/stddebug.dg"]
 :library-sources ["lib/*.dg"
                   "/usr/local/share/dialog-if/stdlib.dg"]}
```                   

`dgt` uses three sets of sources.
For each, you may specify any number of inidividual files, or _glob matches_.
You should be careful with glob matches, as Dialog can be sensitive to the order in which
source files are loaded.

* :story-sources story specific files
* :debug-sources used by the `debug` and `build --test` commands
* :library-sources additional libraries, including the standard library

You may omit :debug-sources or :library-sources; the default `stddebug.dg` and `stdlib.dg` will be
supplied.

You may specify a numeric `:seed` value, used to initialize the random number
generator.

When `:seed` is omitted when running tests, a default seed value, based on a hash
of the path to the test input script, is supplied.

## Write / Run / Test Loop

Dialog makes it easy to code and test at the same time; the
`dgdebug` does a great job of updating in-place
when any of the source files change.

`dgt`s approach to testing is really simple:

- capture an input transcript from the REPL using `@save`
- save the input transcript as a file under `tests` with extension `.txt`
- execute `dgt test` to run all tests
- for each input transcript, the game is executed and output captured
- if the execution generates different text than what been previously _blessed_, it is marked as a failure
- execute `dgt bless` to review failed tests and choose which to bless (mark as correct)

`dgt` searches all directories under `tests` for test files (with
extension `.txt`), which allows you to organize things as you like.
Once blessed, the blessed tests' output is saved with extension `.out`.

The `@save` command does a good job of saving just the input
to the game; although `dgdebug` has additional commands (prefixed
with `@`) and the ability to evaluate predicates, these
inputs are *not* saved to the input transcript file, which is
very convienient.

`dgt` users a colorized side-by-side diff to show what changed
between executions.

## Installing

```
brew install candid82/brew/joker colordiff vickio/dialog/dialog-if
```

Download the `dgt` script and place it on your `$PATH`.

## Future Work

- Version numbers at start of game transcripts are problematic
  and perhaps can be editted out by `dgt` to prevent false
  failures
- Packaging as a zblorb
- Build along with a website, etc.
- Setup a brew formula

## License

Apache Software License 2.0
