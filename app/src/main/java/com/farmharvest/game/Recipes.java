package com.farmharvest.game;

/** The dishes: what each one is made of and how it looks. One dish per level. */
final class Recipes {
    // ingredient ids are tile types (see Art)
    static final int CORN = 0, STRAWBERRY = 1, ORANGE = 2, BLUEBERRY = 3, APPLE = 4, PUMPKIN = 5, EGG = 6, POTATO = 7,
            WATERMELON = 8, FLOUR = 9, SEED = 10, HONEY = 11, MILK = 12;

    static final String[] INGREDIENT = {"Corn", "Strawberries", "Oranges", "Blueberries", "Apples", "Pumpkins", "Eggs", "Potatoes",
            "Watermelon", "Flour", "Seeds", "Honey", "Milk"};

    // how a dish is drawn (see CookRenderer.drawDish)
    static final int JAR = 0, CAKE = 1, PIE = 2, BOWL = 3, STACK = 4, MUFFIN = 5, LOAF = 6, FEAST = 7, BARS = 8;

    static final class Recipe {
        final String name;
        /** Artwork is the drawable dish_<key> when there is one; otherwise the dish is drawn in code. */
        final String key;
        final int shape, color;
        /** Ingredient tile types and their share of the dish. */
        final int[] ingredients, parts;

        Recipe(String key, String name, int shape, int color, int[] ingredients, int[] parts) {
            this.key = key;
            this.name = name;
            this.shape = shape;
            this.color = color;
            this.ingredients = ingredients;
            this.parts = parts;
        }
    }

    private static Recipe r(String key, String name, int shape, int color, int... ingredientAndParts) {
        int n = ingredientAndParts.length / 2;
        int[] ing = new int[n], parts = new int[n];
        for (int i = 0; i < n; i++) {
            ing[i] = ingredientAndParts[2 * i];
            parts[i] = ingredientAndParts[2 * i + 1];
        }
        return new Recipe(key, name, shape, color, ing, parts);
    }

    /** In level order: from one ingredient up to four. */
    static final Recipe[] ALL = {
            r("strawberry_jam", "Strawberry Jam", JAR, 0xFFE53B4F, STRAWBERRY, 1),
            r("apple_sauce", "Apple Sauce", JAR, 0xFFE9B04A, APPLE, 2, HONEY, 1),
            r("marmalade", "Orange Marmalade", JAR, 0xFFFF9B26, ORANGE, 2, HONEY, 1),
            r("melon_sorbet", "Melon Sorbet", BOWL, 0xFF55C27A, WATERMELON, 2, HONEY, 1),
            r("pancakes", "Pancakes", STACK, 0xFFE0A24F, EGG, 1, MILK, 1, FLOUR, 1),
            r("vanilla_ice_cream", "Vanilla Ice Cream", BOWL, 0xFFF6E7B8, MILK, 2, EGG, 1, HONEY, 1),
            r("blueberry_muffin", "Blueberry Muffins", MUFFIN, 0xFF6D74D9, BLUEBERRY, 2, FLOUR, 1, EGG, 1),
            r("pumpkin_soup", "Pumpkin Soup", BOWL, 0xFFF08A24, PUMPKIN, 2, POTATO, 1, MILK, 1),
            r("corn_chowder", "Corn Chowder", BOWL, 0xFFF2C94C, CORN, 2, POTATO, 1, MILK, 1),
            r("fruit_salad", "Fruit Salad", BOWL, 0xFFD9456E, STRAWBERRY, 1, APPLE, 1, ORANGE, 1, BLUEBERRY, 1),
            r("apple_pie", "Apple Pie", PIE, 0xFFD8913F, APPLE, 2, FLOUR, 1, EGG, 1, HONEY, 1),
            r("birthday_cake", "Birthday Cake", CAKE, 0xFFF48FB1, EGG, 1, MILK, 1, FLOUR, 1, STRAWBERRY, 1),
            r("honey_cake", "Honey Cake", CAKE, 0xFFE3A72F, HONEY, 2, FLOUR, 1, EGG, 1, MILK, 1),
            r("granola_bars", "Granola Bars", BARS, 0xFFC98B3E, SEED, 2, HONEY, 1, FLOUR, 1, EGG, 1),
    };

    private Recipes() {
    }
}
