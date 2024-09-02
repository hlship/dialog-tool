# dgt - Dialog Tool

> dgt 2.0 is a total rewrite and is currently in progress

`dgt` is a tool to assist in the development of interactive fiction
written in the [Dialog](https://linusakesson.net/dialog/index.php) language. Not every work of IF is a "game" so we use the term "project".

`dgt` simplifies Dialog development, it allows you to specify the details of your project,
including what individual source files to use, and then provides commands to:

- run your project in the Dialog debugger
- run the web-based Skein UI
- run tests derived from your skein files
- package your project for release

Unlike many of my projects, this is _really_ for personal use:

- OS X only
- Subject to breaking change at any time
- Written in [Babashka](https://github.com/babashka/babashka) (the ultimate scripting language) which you almost certainly don't know

Run the command `dgt help` for a list of commands; each command has a `--help` option (abbreviated as `-h`) that gives
specific details.

> The first time you run `dgt` it will be somewhat slow, as it has to download additional libraries. After that, it will be lightning fast.

## dialog.edn

Your project must have a `dialog.edn` file at the root.

> [EDN (Extensible Data Notation)](https://github.com/edn-format/edn) is like JSON on steroids. It's the natural
  choice for Clojure programmers, but it's close enough to JSON that you should be able to figure it out.

This file identifies the sources in your project as well as other details needed to build, debug, and test your project.

A minimal example `dialog.edn`:
 
```
{:sources {
  :story ["src/*.dg"]
  :debug ["debug/*.dg"
          "/usr/local/share/dialog-if/stddebug.dg"]
  :library ["lib/*.dg"
            "/usr/local/share/dialog-if/stdlib.dg"]}}
```                   

The primary (but not only) use of `dialog.edn` is to tell `dgt` where the sources for your project are,
and _in what order_ they should apply (which is very critical to how Dialog operates).
Further, during development and testing you will often include extra "debug" sources that should not 
be included when building and packaging your project for release.

`dgt` uses three sets of sources.
For each, you may specify any number of individual files, or _glob matches_.
You should be careful with glob matches, as Dialog can be sensitive to the order in which
source files are loaded.

* `:story` - sources specific to your project
* `:debug` - used by the `test`, `debug` and `build --test` commands
* `:library` - additional libraries, including the standard library

Counter to the above example, you should generally copy the standard library into your own project.
Over time you may need to change the standard library, or you may upgrade your Dialog dependency in a way that breaks your project.

## Running your project

`dgt debug` will start your project in the debugger so you can play.

## Running the Skein

`dgt skein` will  open up the skein UI;
the skein represents your project as a tree of "knots"; each knot is a command. You can
add new commands beneath any knot, and you can also rerun the project to any knot
and the skein will identify anything that has changed.  You can even run *all* possible branches
to completion.

> More on this later

## Building and Packaging

This is coming; it was part of dgt 1.x and will be reimplented soon.

## Creating new projects

This is coming as well, a command to create a new empty project.

## Installing

```
brew install hlship/brew/dialog-tool
```

## dgt 2.0 TODO / IDEAS

- Be able to run the Skein against compiled zcode via dumbfrotz
- Optional override in dialog.edn for where the tools (dgdebug, etc.) are stored 
- Run dgdebugger to collect data (title, version, etc.) needed to publish
- Could we generate (live?) documentation by parsing the source?

## License

Apache Software License 2.0
