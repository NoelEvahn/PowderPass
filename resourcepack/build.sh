#!/usr/bin/env bash
# Builds ../target/PowderPass-pack.zip (pack.mcmeta at the zip root) and prints its SHA-1.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

OUT="../target/PowderPass-pack.zip"
mkdir -p "$(dirname "$OUT")"
rm -f "$OUT"

if command -v zip >/dev/null 2>&1; then
    zip -X -r -q "$OUT" pack.mcmeta pack.png assets
else
    python3 -m zipfile -c "$OUT" pack.mcmeta pack.png assets
fi

echo "Built $(cd "$(dirname "$OUT")" && pwd)/$(basename "$OUT")"
echo "sha1: $(sha1sum "$OUT" | cut -d' ' -f1)"
