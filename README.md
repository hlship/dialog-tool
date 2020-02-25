# dgt - Dialog Tool

`dgt` is a tool to assist in the development of interactive fiction
written in the [Dialog](https://linusakesson.net/dialog/index.php) language.

`dgt` simplifies development, it allows you to specify the details of your project,
including what individual source files to use, and then provides commands to:

- run your project in the Dialog debugger
- run tests and identify failures
- review failed tests and "bless" updates
- build a compiled project for distribution
- more to come ...

Unlike many of my projects, this is _really_ for personal use:

- OS X only
- Subject to breaking change at any time
- Written in [Joker](https://github.com/candid82/joker) (the ultimate scripting language) which you almost certainly don't know

Run the command `dgt help` for a list of commands; each command has a `--help` option (abbreviated as `-h`) that gives
specific details.

## dialog.edn

Your project must have a `dialog.edn` file at the root.

> [EDN (Extensible Data Notation)](https://github.com/edn-format/edn) is like JSON on steroids. It's the natural
  choice for Clojure or Joker programmers, but it's close enough to JSON that you should be able to figure it out.

This file identifies the sources in your project as well as other details needs to build, debug, and test your project.

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
For each, you may specify any number of individual files, or _glob matches_.
You should be careful with glob matches, as Dialog can be sensitive to the order in which
source files are loaded.

* `:story-sources` - sources specific to your project
* `:debug-sources` - used by the `debug` and `build --test` commands
* `:library-sources` - additional libraries, including the standard library

You may omit `:debug-sources` or `:library-sources`; the default `stddebug.dg` and `stdlib.dg` will be
supplied.

The default for `:output-format` is `:z8`, and the default for `:name` is the name of the current directory.

You may optinally specify a numeric `:seed` value, used to initialize the random number generator.
This can be very useful when writing tests where output text varies randomly; the same test script
with the same seed will result in stable, repeatable output.

When `:seed` is omitted when running tests, a default seed value, based on a hash
of the path to the test input script, is supplied.

# Remote Sources

`dgt` can download sources from GitHub repositories.  Instead of a string path (as shown above), a remote source
is a map with the following structure:

```
   {:github "hlship/threaded-conversation"
    :version "v0.2"
    :path "lib/tc.dg"}
```

This identifies the GitHub repository, the version, and the path within the GitHub repository.

On first execution, this map will be converted to a URL, which is downloaded, and saved to 
the `.cache` directory.

The `:version` key may *not* be `master`.
Ideally it should be a published tag, though it can also
be a Git commit SHA.

## Write / Run / Test Loop

Dialog makes it easy to code and test at the same time; the
`dgdebug` does a great job of updating in-place
when any of the source files change.

Testing is very simple with `dgt`; under the `tests` folder are `.txt` files
and corresponding `.out` files.
The first is a transcript for the project, the series of commands to execute.
The transcript may also include Dialog style comments (`%%`), and
Dialog queries, including `(now)` queries.

The `dgt test` command finds each of these files, runs the debugger to execute
the transcript, and captures the output.
If the output matches the `.out` file, the test is succesful.
Otherwise, the actual output is saved and the test is a failure; the
`dgt bless` command allows you to review failed tests, using
a colorized side-by-side diff, and identify those where
the new output is correct.

`dgt test` searches all directories under `tests` for test files
which allows you to organize things as you like.
Once blessed, the blessed tests' output is saved with extension `.out`.

From the debugger, the `@save` command does a good job of saving just the input
to the game; although `dgdebug` has additional commands (prefixed
with `@`) and the ability to evaluate predicates, these
inputs are *not* saved to the input transcript file, which is
very convienient.

## Installing

```
brew install hlship/brew/dialog-tool
```

## Future Work

- Version numbers at start of game transcripts are problematic
  and perhaps can be editted out by `dgt` to prevent false
  failures
- Packaging as a zblorb
- Build along with a website, etc.

## License

Apache Software License 2.0
