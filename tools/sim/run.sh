#!/bin/sh
# Compiles the game's pure logic with the simulator and prints per-level win rates.
# Usage: run.sh [games] [stats|tune]
set -e
cd "$(dirname "$0")/../.."
OUT=$(mktemp -d)
SRC=app/src/main/java/com/zigzag/cottagekitchen
javac -d "$OUT" $SRC/Art.java $SRC/Recipes.java $SRC/Board.java $SRC/Level.java $SRC/Rules.java $SRC/Tile.java tools/sim/R.java tools/sim/Sim.java
java $JOPTS -cp "$OUT" com.zigzag.cottagekitchen.Sim "${1:-100}" $2
rm -rf "$OUT"
