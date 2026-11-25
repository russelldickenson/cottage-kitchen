package com.zigzag.cottagekitchen;

/** Artwork indexed by tile type id. Ids below CROPS are matchable crops; the rest are obstacles. */
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
            R.drawable.crop_flour,
            R.drawable.crop_sunflower,
            R.drawable.crop_honey,
            R.drawable.crop_milk,
            R.drawable.crop_sugar,
            R.drawable.crop_water,
            R.drawable.crop_crate,
            R.drawable.crop_crate_cracked,
    };
    static final int CROPS = 15;
    /** A crate is an obstacle: it is not swapped or matched, and breaks after CRATE_HP hits from clears beside it. CRATE_CRACKED is only its second picture. */
    static final int CRATE = 15, CRATE_CRACKED = 16, CRATE_HP = 2;
    static final int COUNT = RES.length;

    static boolean isCrate(int type) {
        return type == CRATE;
    }

    private Art() {
    }
}
