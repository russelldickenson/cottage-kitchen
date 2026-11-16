package com.farmharvest.game;

import java.util.Arrays;

/**
 * Plays whole campaigns with a simple buying policy and reports how the farm behaves: how much the animals earn,
 * how often they go hungry, how long the fed streak lasts and how the herd grows. Run: tools/sim/run.sh [campaigns] farm
 * The bots steer only through the level goals (see Level.get), so hunger rates here are for a player who does the goals.
 */
final class FarmSim {
    static void run(int campaigns) {
        report(campaigns, 1.0, "expert");
        report(campaigns, 0.5, "casual");
    }

    private static void report(int campaigns, double skill, String name) {
        int n = Level.COUNT, sp = Farm.SPECIES;
        double[] play = new double[n], animals = new double[n], bonus = new double[n], hungryRate = new double[n], streak = new double[n];
        double[] farmMoves = new double[n], banked = new double[n], spent = new double[n];
        double[][] herd = new double[n][sp], hauled = new double[n][Farm.STOCKS];
        for (int g = 0; g < campaigns; g++) {
            Farm.Herd h = new Farm.Herd();
            h.house[Farm.CHICKEN] = 1; // a new farm starts with a coop and two hens
            h.count[Farm.CHICKEN] = 2;
            int coins = 0;
            for (int i = 0; i < n; i++) {
                Level lv = Level.get(i, h);
                Arrays.fill(Sim.byType, 0);
                Sim.levelCoins = 0;
                int left, tries = 0;
                do {
                    left = Sim.playLevel(lv, skill, h);
                    tries++;
                } while (left < 0 && tries < 5);
                int[] harvest = new int[Art.CROPS];
                for (int t = 0; t < Art.CROPS; t++) harvest[t] = (int) Sim.byType[t];
                int p = (int) Sim.levelCoins + Sim.lastMovesLeft * Rules.MOVE_BONUS + Farm.saleValue(harvest);
                farmMoves[i] += Sim.lastFarmMoves;
                Farm.Report rp = h.endLevel(harvest);
                play[i] += p;
                animals[i] += rp.coins - rp.soldOverflow;
                bonus[i] += rp.bonusCoins;
                hungryRate[i] += rp.hungry > 0 ? 1 : 0;
                streak[i] += rp.streak;
                for (int s = 0; s < Farm.STOCKS; s++) hauled[i][s] += rp.hauled[s];
                coins += p + rp.coins;
                int before = coins;
                coins = spend(h, coins, i);
                spent[i] += before - coins;
                for (int s = 0; s < sp; s++) herd[i][s] += h.count[s];
                banked[i] += coins;
            }
        }
        System.out.printf("%n%s bot, %d campaigns%n", name, campaigns);
        System.out.println("lvl  play | animals (share, streak bonus) | hungry | streak | farm mv | hens cows pigs sheep bees | spent | banked");
        for (int i = 0; i < n; i++) {
            double pl = play[i] / campaigns, an = animals[i] / campaigns;
            System.out.printf("%3d %5.0f | %5.0f (%4.1f%%, %4.0f) | %5.0f%% | %6.1f | %7.1f | %4.1f %4.1f %4.1f %5.1f %4.1f | %5.0f | %6.0f%n", i + 1, pl, an, 100 * an / (pl + an), bonus[i] / campaigns,
                    100 * hungryRate[i] / campaigns, streak[i] / campaigns, farmMoves[i] / campaigns,
                    herd[i][0] / campaigns, herd[i][1] / campaigns, herd[i][2] / campaigns, herd[i][3] / campaigns, herd[i][4] / campaigns, spent[i] / campaigns, banked[i] / campaigns);
        }
    }

    /**
     * Hatch what can be hatched, then keep doing each species' next step (build, buy, upgrade) when it is affordable.
     * Species are tried best payer first, so a limited purse goes to the animals that earn most.
     */
    private static int spend(Farm.Herd h, int coins, int levelIndex) {
        int[] order = {Farm.COW, Farm.PIG, Farm.SHEEP, Farm.BEE, Farm.CHICKEN};
        boolean again = true;
        while (again) {
            again = false;
            h.autoHatch();
            for (int sp : order) {
                if (levelIndex + 2 < Farm.HOUSE_UNLOCK_LEVEL[sp]) continue;
                int price;
                boolean buy = false;
                if (h.house[sp] == 0) price = Farm.houseCost(sp, 0);
                else if (h.room(sp) > 0) {
                    if (sp == Farm.CHICKEN) continue;
                    price = Farm.animalPrice(sp);
                    buy = true;
                } else price = Farm.houseCost(sp, h.house[sp]);
                if (price < 0 || coins < price) continue;
                coins -= price;
                if (buy) h.count[sp]++;
                else h.house[sp]++;
                again = true;
                break;
            }
        }
        return coins;
    }

    private FarmSim() {
    }
}
