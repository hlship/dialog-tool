# CLAUDE.md

## Project Overview

`dgt` (Dialog Tool) is a CLI tool for developing interactive fiction written in the
[Dialog](https://github.com/dialog-if/dialog) language. It is implemented in Clojure and
distributed as an uberjar.

The tool provides commands for:
- Creating new projects from a template (`dgt new`)
- Running projects in the Dialog debugger (`dgt debug`)
- Running a web-based Skein UI for interactive testing (`dgt skein`)
- Building projects to various targets (`dgt build`)
- Bundling projects for web deployment (`dgt bundle`)
- Running projects via frotz (`dgt run`)

## Project Structure

- `src/dialog_tool/` — Main source code
  - `main.clj` — Entry point, CLI dispatch
  - `commands.clj` — Top-level CLI commands (build, debug, run, new, etc.)
  - `build.clj` — Compilation via external `dialogc` compiler
  - `bundle.clj` — Web bundle packaging
  - `project_file.clj` — Reading and processing `dialog.edn` project files
  - `template.clj` — Project template creation
  - `skein/` — Skein subsystem (session management, tree structure, web UI, process control)
- `test/` — Tests (run with `bb test` or `clj -M:test`)
- `resources/` — Templates, bundled assets, skein resources
- `release/` — Babashka release scripts and Homebrew formula template
- `build.clj` — tools.build uberjar configuration

## Key Technologies

- **Clojure** with `deps.edn` for dependency management
- **Babashka** (`bb.edn`) for task running (tests, releases, tailwind)
- **cli-tools** (`io.github.hlship/cli-tools`) for CLI option parsing and command dispatch
- **Datastar** + **Huff** + **DaisyUI** for the Skein web UI
- **http-kit** as the web server
- **pty4j** for pseudo-terminal process interaction with `dgdebug`

## Development

- `bb test` — Run the test suite
- `bb tailwind` — Watch and rebuild CSS (requires `npm install` and `brew install tailwindcss`)
- REPL-driven development via nREPL (`:dev` alias adds dev paths and reload tooling)

## Configuration

Each Dialog project has a `dialog.edn` at its root. Key fields:
- `:name` — Project name
- `:target` — Build target(s): a single keyword (`:zblorb`) or a vector of keywords (`[:zblorb :aa]`).
  Normalized to a vector internally by `project_file.clj`.
- `:build` — Per-target build options (e.g., cover image flags)
- `:sources` — Source directories organized as `:main`, `:debug`, `:library`

## Important Rules

- **Never commit or push without explicit approval from the user.**
- The `--format` flag passed to external tools (`dialogc`, `aambundle`) is the *external tool's* flag
  and is unrelated to the project's `:target` configuration key.
- `CHANGES.md` is a historical record; do not retroactively alter past entries.
