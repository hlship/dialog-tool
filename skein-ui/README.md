# Dialog Skein UI

## Development

This is the client-side portion of the Skein UI; the rest runs as the Clojure or Babashka API server, which must be already running when this is started.

Here:

- `npm run dev`

This starts a local server (on an arbitrary port) to server the development version of the UI; this UI will update as source code changes are made.

It expects the API server (the Clojure or Babshka API server) on port 10140.

## Build Deploy

Here:

- `npm run build`

This generates all files to the `dist` directory.  These files will be packaged along with the Babashka scripts.  Ultimately, `dgt skein` will run
a server on port 10140 to server these files as well as the /api endpoint.
