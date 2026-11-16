package com.farmharvest.game;

/** The dishes: the ingredients each one is made of (one level per ingredient) and how it looks. */
final class Recipes {
    // ingredient ids are tile types (see Art)
    static final int CORN = 0, STRAWBERRY = 1, ORANGE = 2, BLUEBERRY = 3, APPLE = 4, PUMPKIN = 5, EGG = 6, POTATO = 7,
            WATERMELON = 8, FLOUR = 9, SEED = 10, HONEY = 11, MILK = 12, SUGAR = 13, WATER = 14;

    static final String[] INGREDIENT = {"Corn", "Strawberries", "Oranges", "Blueberries", "Apples", "Pumpkins", "Eggs", "Potatoes",
            "Watermelon", "Flour", "Seeds", "Honey", "Milk", "Sugar", "Water"};

    // how a dish is drawn (see CookRenderer.drawDish)
    static final int JAR = 0, CAKE = 1, PIE = 2, BOWL = 3, STACK = 4, MUFFIN = 5, LOAF = 6, FEAST = 7, BARS = 8;

    static final class Recipe {
        final String name;
        /** Artwork is the drawable dish_<key> when there is one; otherwise the dish is drawn in code. */
        final String key;
        final int shape, color;
        /** Ingredient tile types, in the order their levels are played. */
        final int[] ingredients;

        Recipe(String key, String name, int shape, int color, int[] ingredients) {
            this.key = key;
            this.name = name;
            this.shape = shape;
            this.color = color;
            this.ingredients = ingredients;
        }
    }

    private static Recipe r(String key, String name, int shape, int color, int... ingredients) {
        return new Recipe(key, name, shape, color, ingredients);
    }

    /** In book order, the simpler dishes first. */
    static final Recipe[] ALL = {
            r("strawberry_jam", "Strawberry Jam", JAR, 0xFFE53B4F, STRAWBERRY, SUGAR, WATER),
            r("apple_sauce", "Apple Sauce", JAR, 0xFFE9B04A, APPLE, SUGAR, WATER),
            r("marmalade", "Orange Marmalade", JAR, 0xFFFF9B26, ORANGE, SUGAR, WATER),
            r("melon_sorbet", "Melon Sorbet", BOWL, 0xFF55C27A, WATERMELON, SUGAR, WATER),
            r("pancakes", "Pancakes", STACK, 0xFFE0A24F, EGG, MILK, FLOUR),
            r("vanilla_ice_cream", "Vanilla Ice Cream", BOWL, 0xFFF6E7B8, MILK, EGG, SUGAR),
            r("blueberry_muffin", "Blueberry Muffins", MUFFIN, 0xFF6D74D9, BLUEBERRY, FLOUR, EGG, SUGAR),
            r("pumpkin_soup", "Pumpkin Soup", BOWL, 0xFFF08A24, PUMPKIN, POTATO, MILK, WATER),
            r("corn_chowder", "Corn Chowder", BOWL, 0xFFF2C94C, CORN, POTATO, MILK),
            r("fruit_salad", "Fruit Salad", BOWL, 0xFFD9456E, STRAWBERRY, APPLE, ORANGE, BLUEBERRY),
            r("apple_pie", "Apple Pie", PIE, 0xFFD8913F, APPLE, FLOUR, EGG, SUGAR),
            r("birthday_cake", "Birthday Cake", CAKE, 0xFFF48FB1, EGG, MILK, FLOUR, STRAWBERRY),
            r("honey_cake", "Honey Cake", CAKE, 0xFFE3A72F, HONEY, FLOUR, EGG, MILK),
            r("granola_bars", "Granola Bars", BARS, 0xFFC98B3E, SEED, HONEY, FLOUR, EGG),
    };

    // ---- levels: one per ingredient, dish after dish
    private static final int[] FIRST = new int[ALL.length + 1];

    static {
        for (int i = 0; i < ALL.length; i++) FIRST[i + 1] = FIRST[i] + ALL[i].ingredients.length;
    }

    /** Total number of levels. */
    static final int LEVELS = FIRST[ALL.length];

    /** The level index of a dish's first ingredient. */
    static int firstLevel(int dish) {
        return FIRST[dish];
    }

    static int lastLevel(int dish) {
        return FIRST[dish + 1] - 1;
    }

    static int dishOf(int level) {
        int d = 0;
        while (FIRST[d + 1] <= level) d++;
        return d;
    }

    private Recipes() {
    }
}
