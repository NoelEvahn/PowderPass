#!/bin/sh
# Rebuild the client and (re)start the game server serving it on :2567 (local testing helper).
SP=/tmp/claude-0/-home-user-PowderPass/ec1c77a4-4f2b-5a3c-970b-44c040e78aa4/scratchpad
[ -f $SP/server.pid ] && kill $(cat $SP/server.pid) 2>/dev/null
cd "$(dirname "$0")/.." && (cd client && npx vite build >/dev/null 2>&1 || echo BUILD FAILED)
PORT=2567 STATIC_DIR=$PWD/client/dist DATA_FILE=$SP/profiles.json nohup node --import tsx server/src/index.ts > $SP/server.log 2>&1 &
echo $! > $SP/server.pid
sleep 3; curl -s localhost:2567/health; echo
