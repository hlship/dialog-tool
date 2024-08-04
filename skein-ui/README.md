# Dialog Skein UI

## Development

This is the client-side portion of the Skein UI; the rest runs as the Clojure or Babashka API server, which must be already running when this is started.

Here:

- `npm run dev`

This starts a local server (on an arbitrary port) to serve the development version of the UI; this UI will update as source code changes are made.

It expects the API server (the Clojure or Babshka API server) to be running on port 10140.

## Build Deploy

Here:

- `npm run build`

This generates all files to the `dist` directory.  These files will be packaged along with the Babashka scripts.  Ultimately, `dgt skein` will run
a server on port 10140 to serve these files as well as the `/api` endpoint.

## TODO/IDEAS

- Visual feedback when the skein changes
- The graph view (currently it's just a serial view, one path through the graph)
- Common SkButton component
- Visually distinguish disabled buttons
- Visually distinguish the selected button (the next node after each command)
- "Bless All" (w/ a confirm)
- After bless (or other update), loses track of selected child node(s)
- Delete node
- Implement replay
- Collapsable text
- Highlight nodes that need to be "blessed"
- Visual diff of text (!!!)
- Buttons to jump to next/prev command
- Keep the main menu bar pinned to the top of the window
- Label a node
- Mark a node w/ a tag: solution (to be included in the packaged game)
- Animation when adding/removing nodes
- Handle *many* children for a node
- Search the Skein
- Jump to labeled node
- Jump to "nearest" unblessed node
- Show warning in menu bar about number of unblessed nodes
- Show the path to the Skein file in the menu bar
- Undo/Redo

Visual feedback is a problem, because the round-trip is often just milliseconds.
