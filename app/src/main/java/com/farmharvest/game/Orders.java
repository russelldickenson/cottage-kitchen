package com.farmharvest.game;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Customer orders: ask for crops from your barn and pay a premium over the market. No Android types. */
final class Orders {
    static final int SLOTS = 3, MAX_ITEMS = 3;
    /** Reward is this multiple of the crops' base price, so an order beats selling but not by a lot. */
    static final float PREMIUM = 1.4f;
    static final String[] CUSTOMER = {"Farmer Joe", "Granny May", "Chef Luigi", "Baker Beth", "Miss Daisy", "Old Tom", "Little Sam", "Mayor Pat"};

    static final class Order {
        boolean has;
        int n;
        final int[] type = new int[MAX_ITEMS], qty = new int[MAX_ITEMS];
        int reward, gift = -1, customer;
        /** Last day the order can be filled. */
        long expires;
        /** Day the slot was last emptied, so a new customer only arrives on a later day. */
        long filledDay;
    }

    /** A new order for a slot. Bigger slots ask for more kinds of crop; sizes grow with the levels unlocked. */
    static Order generate(int slot, long day, int levelsUnlocked, int serial) {
        Random r = new Random(day * 1000003L + slot * 7919L + serial * 104729L);
        int unlocked = Math.max(1, Math.min(levelsUnlocked, Level.COUNT));
        List<Integer> pool = new ArrayList<>();
        Set<Integer> seen = new LinkedHashSet<>();
        for (int i = 0; i < unlocked; i++) for (int k : Level.get(i).kinds) if (seen.add(k)) pool.add(k);

        Order o = new Order();
        o.has = true;
        o.n = slot == 0 ? 1 : slot == 1 ? 2 : (unlocked >= 4 ? 3 : 2);
        int base = Math.min(40, 8 + 2 * unlocked);
        int sum = 0;
        for (int k = 0; k < o.n; k++) {
            int t = pool.remove(r.nextInt(pool.size()));
            int q = Math.max(5, Math.round(base * (0.7f + 0.5f * r.nextFloat())));
            o.type[k] = t;
            o.qty[k] = q;
            sum += q * Farm.BASE_PRICE[t];
        }
        o.reward = Math.round(sum * PREMIUM / 10f) * 10;
        o.customer = (r.nextInt(CUSTOMER.length) + slot * 3) % CUSTOMER.length;
        o.expires = day + 2 + slot;
        if (o.n == 3) o.gift = (int) ((day + slot) % Farm.BOOSTERS);
        return o;
    }

    static int daysLeft(Order o, long day) {
        return (int) (o.expires - day) + 1;
    }

    private Orders() {
    }
}
