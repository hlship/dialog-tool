#!/bin/bash
set -euo pipefail

java --class-path "$(dirname $0)/{{uber-jar}}" \
  clojure.main -m dialog-tool.main $*
