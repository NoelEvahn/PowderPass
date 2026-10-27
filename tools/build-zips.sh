#!/usr/bin/env bash
# Builds the two files you upload in WordPress (Plugins > Add New > Upload, Appearance > Themes > Upload):
#   dist/labeltrunc-core.zip   and   dist/labeltrunc-theme.zip
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf dist && mkdir -p dist
for f in $(find wordpress -name '*.php'); do php -l "$f" >/dev/null; done
(cd wordpress/plugins && zip -qr ../../dist/labeltrunc-core.zip labeltrunc-core -x '*.DS_Store')
(cd wordpress/themes && zip -qr ../../dist/labeltrunc-theme.zip labeltrunc -x '*.DS_Store')
ls -lh dist
