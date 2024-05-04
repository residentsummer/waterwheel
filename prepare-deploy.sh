#!/bin/bash

set -x -e -o pipefail

clojure -M:fig:build

rm -rf deploy target/public/prod

mkdir -p deploy/cljs-out
cp resources/public/index.html deploy/
cp target/public/cljs-out/prod-main.js deploy/cljs-out/main.js
