package com.farmharvest.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything that survives between sessions: levels, coins, the barn, upgrades, boosters and tutorial cards seen. */
final class Progress {
    int unlocked, totalCoins;
    boolean soundOn;
    final int[] bestStars = new int[Level.COUNT];
    final boolean[] tipSeen = new boolean[Art.COUNT];
    final int[] barn = new int[Art.CROPS];
    final int[] building = new int[Farm.UPGRADES];
    final int[] boosters = new int[Farm.BOOSTERS];
    final Orders.Order[] orders = new Orders.Order[Orders.SLOTS];
    long giftDay;
    int orderSerial;
    private final SharedPreferences prefs;

    Progress(Context ctx) {
        prefs = ctx.getSharedPreferences("farm", Context.MODE_PRIVATE);
        unlocked = Math.max(1, prefs.getInt("unlocked", 1));
        totalCoins = prefs.getInt("coins", 0);
        soundOn = prefs.getBoolean("sound", true);
        for (int t = Art.CROPS; t < Art.COUNT; t++) tipSeen[t] = prefs.getBoolean("tip_" + t, false);
        for (int i = 0; i < Level.COUNT; i++) bestStars[i] = prefs.getInt("stars_" + i, 0);
        for (int i = 0; i < barn.length; i++) barn[i] = prefs.getInt("barn_" + i, 0);
        for (int i = 0; i < building.length; i++) building[i] = prefs.getInt("building_" + i, 0);
        for (int i = 0; i < boosters.length; i++) boosters[i] = prefs.getInt("booster_" + i, 0);
        giftDay = prefs.getLong("gift_day", 0);
        orderSerial = prefs.getInt("order_serial", 0);
        for (int s = 0; s < orders.length; s++) orders[s] = loadOrder(s);
    }

    private void saveFarm() {
        SharedPreferences.Editor e = prefs.edit().putInt("coins", totalCoins);
        for (int i = 0; i < barn.length; i++) e.putInt("barn_" + i, barn[i]);
        for (int i = 0; i < building.length; i++) e.putInt("building_" + i, building[i]);
        for (int i = 0; i < boosters.length; i++) e.putInt("booster_" + i, boosters[i]);
        e.apply();
    }

    /** Records a finished level. stars is 0 for a loss. */
    void levelFinished(int levelIndex, int stars, int coinsEarned) {
        if (stars > 0) {
            bestStars[levelIndex] = Math.max(bestStars[levelIndex], stars);
            unlocked = Math.max(unlocked, Math.min(Level.COUNT, levelIndex + 2));
        }
        totalCoins += coinsEarned;
        prefs.edit().putInt("coins", totalCoins).putInt("unlocked", unlocked)
                .putInt("stars_" + levelIndex, bestStars[levelIndex]).apply();
    }

    void setSound(boolean on) {
        soundOn = on;
        prefs.edit().putBoolean("sound", on).apply();
    }

    void tipShown(int type) {
        tipSeen[type] = true;
        prefs.edit().putBoolean("tip_" + type, true).apply();
    }

    // ---- barn and market

    int barnTotal() {
        int n = 0;
        for (int v : barn) n += v;
        return n;
    }

    int capacity() {
        return Farm.capacity(building[Farm.BARN]);
    }

    /**
     * Stores a level's harvest. Crops that do not fit are sold straight away at half the day's price.
     * Returns {crops stored, coins from overflow}.
     */
    int[] addHarvest(int[] counts, long day) {
        int stored = 0, overflowCoins = 0, total = barnTotal(), cap = capacity();
        for (int type = 0; type < Art.CROPS; type++) {
            for (int i = 0; i < counts[type]; i++) {
                if (total < cap) {
                    barn[type]++;
                    total++;
                    stored++;
                } else {
                    overflowCoins += Math.max(1, Farm.price(type, day, building[Farm.WELL]) / 2);
                }
            }
        }
        totalCoins += overflowCoins;
        saveFarm();
        return new int[]{stored, overflowCoins};
    }

    int sellValue(int type, long day) {
        return barn[type] * Farm.price(type, day, building[Farm.WELL]);
    }

    int sellValueAll(long day) {
        int v = 0;
        for (int t = 0; t < barn.length; t++) v += sellValue(t, day);
        return v;
    }

    /** Sells every unit of one crop. Returns the coins earned. */
    int sell(int type, long day) {
        int v = sellValue(type, day);
        barn[type] = 0;
        totalCoins += v;
        saveFarm();
        return v;
    }

    int sellAll(long day) {
        int v = sellValueAll(day);
        java.util.Arrays.fill(barn, 0);
        totalCoins += v;
        saveFarm();
        return v;
    }

    // ---- shop

    boolean buyUpgrade(int b) {
        int cost = Farm.nextCost(b, building[b]);
        if (cost < 0 || totalCoins < cost) return false;
        totalCoins -= cost;
        building[b]++;
        saveFarm();
        return true;
    }

    boolean buyBooster(int k) {
        if (totalCoins < Farm.BOOSTER_PRICE[k]) return false;
        totalCoins -= Farm.BOOSTER_PRICE[k];
        boosters[k]++;
        saveFarm();
        return true;
    }

    /** Uses one booster, buying it on the spot if none are owned. Returns false when it cannot be afforded. */
    boolean takeBooster(int k) {
        if (boosters[k] > 0) {
            boosters[k]--;
        } else if (totalCoins >= Farm.BOOSTER_PRICE[k]) {
            totalCoins -= Farm.BOOSTER_PRICE[k];
        } else {
            return false;
        }
        saveFarm();
        return true;
    }

    // ---- orders and the daily gift

    private Orders.Order loadOrder(int s) {
        Orders.Order o = new Orders.Order();
        String p = "o" + s + "_";
        o.has = prefs.getBoolean(p + "has", false);
        o.n = prefs.getInt(p + "n", 0);
        for (int k = 0; k < Orders.MAX_ITEMS; k++) {
            o.type[k] = prefs.getInt(p + "t" + k, 0);
            o.qty[k] = prefs.getInt(p + "q" + k, 0);
        }
        o.reward = prefs.getInt(p + "reward", 0);
        o.gift = prefs.getInt(p + "gift", -1);
        o.customer = prefs.getInt(p + "cust", 0);
        o.expires = prefs.getLong(p + "exp", 0);
        o.filledDay = prefs.getLong(p + "filled", 0);
        return o;
    }

    private void saveOrders() {
        SharedPreferences.Editor e = prefs.edit().putInt("order_serial", orderSerial);
        for (int s = 0; s < orders.length; s++) {
            Orders.Order o = orders[s];
            String p = "o" + s + "_";
            e.putBoolean(p + "has", o.has).putInt(p + "n", o.n).putInt(p + "reward", o.reward).putInt(p + "gift", o.gift)
                    .putInt(p + "cust", o.customer).putLong(p + "exp", o.expires).putLong(p + "filled", o.filledDay);
            for (int k = 0; k < Orders.MAX_ITEMS; k++) e.putInt(p + "t" + k, o.type[k]).putInt(p + "q" + k, o.qty[k]);
        }
        e.apply();
    }

    /** Drops expired orders and brings in new customers for slots that were emptied on an earlier day. */
    void refreshOrders(long day) {
        boolean changed = false;
        for (int s = 0; s < orders.length; s++) {
            Orders.Order o = orders[s];
            if (o.has && day > o.expires) {
                o.has = false;
                o.filledDay = o.expires;
                changed = true;
            }
            if (!o.has && o.filledDay < day) {
                orders[s] = Orders.generate(s, day, unlocked, orderSerial++);
                changed = true;
            }
        }
        if (changed) saveOrders();
    }

    boolean canFill(int slot) {
        Orders.Order o = orders[slot];
        if (!o.has) return false;
        for (int k = 0; k < o.n; k++) if (barn[o.type[k]] < o.qty[k]) return false;
        return true;
    }

    int fillableOrders() {
        int n = 0;
        for (int s = 0; s < orders.length; s++) if (canFill(s)) n++;
        return n;
    }

    int openOrders() {
        int n = 0;
        for (Orders.Order o : orders) if (o.has) n++;
        return n;
    }

    /** Fills an order from the barn. Returns the coins paid, or 0 when the barn is short. */
    int fillOrder(int slot, long day) {
        if (!canFill(slot)) return 0;
        Orders.Order o = orders[slot];
        for (int k = 0; k < o.n; k++) barn[o.type[k]] -= o.qty[k];
        totalCoins += o.reward;
        if (o.gift >= 0) boosters[o.gift]++;
        int reward = o.reward;
        o.has = false;
        o.filledDay = day;
        saveFarm();
        saveOrders();
        return reward;
    }

    boolean giftReady(long day) {
        return giftDay != day;
    }

    /** Today's free gift: coins, plus a booster every third day. Returns the booster given, or -1. */
    int claimGift(long day) {
        giftDay = day;
        totalCoins += Farm.GIFT_COINS;
        int booster = day % 3 == 0 ? (int) ((day / 3) % Farm.BOOSTERS) : -1;
        if (booster >= 0) boosters[booster]++;
        saveFarm();
        prefs.edit().putLong("gift_day", day).apply();
        return booster;
    }
}
