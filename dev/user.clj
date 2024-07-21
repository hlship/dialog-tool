(ns user
  (:require [net.lewisship.trace :as trace]
            clj-reload.core
            [clj-commons.pretty.repl :as repl]
            [net.lewisship.cli-tools :as cli]))

(repl/install-pretty-exceptions)
(trace/setup-default)
(cli/set-prevent-exit! true)

(trace/trace :startup true)

(comment
  (trace/set-enable-trace! false)
  (trace/set-enable-trace! true)

  )
