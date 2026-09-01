#!/usr/bin/env bash
set -euo pipefail
exec "$(dirname "$0")/gradle.sh" :app:assembleDebug --stacktrace
