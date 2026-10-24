package com.farmharvest.game;

/** Artwork indexed by tile type id. Ids below CROPS are matchable crops; the rest are power-up tiles. */
final class Art {
    static final int[] RES = {
            R.drawable.crop_corn,
            R.drawable.crop_strawberry,
            R.drawable.crop_orange,
            R.drawable.crop_blueberry,
            R.drawable.crop_apple,
            R.drawable.crop_pumpkin,
            R.drawable.crop_egg,
            R.drawable.crop_potato,
            R.drawable.crop_watermelon,
            R.drawable.crop_milk,
            R.drawable.crop_sunflower,
            R.drawable.crop_bee,
            R.drawable.crop_water,
            R.drawable.crop_scarecrow,
            R.drawable.crop_tnt,
            R.drawable.crop_egg_gold,
            R.drawable.crop_tractor,
    };
    static final int CROPS = 13;
    static final int SCARECROW = 13, TNT = 14, GOLDEN_EGG = 15, TRACTOR = 16;
    static final int COUNT = RES.length;

    static boolean isSpecial(int type) {
        return type >= CROPS;
    }

    /** Bombs clear the 3x3 area around them. */
    static boolean isBomb(int type) {
        return type == SCARECROW || type == TNT;
    }

    /** Colour-clearers remove every tile of the crop they are swapped with. */
    static boolean isColor(int type) {
        return type == GOLDEN_EGG || type == TRACTOR;
    }

    private Art() {
    }
}
