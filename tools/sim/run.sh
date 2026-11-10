#!/bin/sh
# Compiles the game's pure logic with the simulator and prints per-level win rates.
# Usage: run.sh [games] [economy|stats|tune|farm]
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
SRC=app/src/main/java/com/farmharvest/game
javac -d "$OUT" $SRC/Art.java $SRC/Farm.java $SRC/Board.java $SRC/Level.java $SRC/Rules.java $SRC/Tile.java tools/sim/R.java tools/sim/Sim.java tools/sim/FarmSim.java
java $JOPTS -cp "$OUT" com.farmharvest.game.Sim "${1:-100}" $2
rm -rf "$OUT"
