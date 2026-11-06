#!/bin/bash
# Download every third-party input pinned in deps.lock into deps/dl/ (or $LETSGO_DEPS_DL) and check its sha256.
#   tools/ci/fetch-deps.sh            fetch what is missing, check everything
#   tools/ci/fetch-deps.sh --offline  check only (no network), e.g. from an unpacked letsgo-<ver>-source.tar.gz
#   tools/ci/fetch-deps.sh sdl3 ffmpeg   only these entries
# Exit status 1 on any missing file or hash mismatch. Nothing is extracted or executed here.
set -euo pipefail
cd "$(dirname "$0")/../.."
exec python3 -I tools/ci/deps.py fetch "$@"
