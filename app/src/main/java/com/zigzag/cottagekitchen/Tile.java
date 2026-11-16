package com.zigzag.cottagekitchen;

/** One crop on the board. x/y are render positions in cell units; r/c are the logical grid cell. */
final class Tile {
    int type;
    int r, c;
    float x, y, vy;
    float scale = 1f;
    float squash;
    boolean dying, popped;
    float dyDelay;

    Tile(int type, int r, int c) {
        this.type = type;
        this.r = r;
        this.c = c;
        this.x = c;
        this.y = r;
    }
}
