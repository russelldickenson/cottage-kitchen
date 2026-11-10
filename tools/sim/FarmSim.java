package com.farmharvest.game;

import java.util.Arrays;

/**
 * Plays whole campaigns with a simple buying policy and reports how the farm behaves: how much the animals earn,
 * how often they go hungry and how long the fed streak lasts. Run: tools/sim/run.sh [campaigns] farm
 * The bots do not steer toward what the herd needs, so hunger rates here are a floor for a real player.
 */
final class FarmSim {
    static void run(int campaigns) {
        report(campaigns, 1.0, "expert");
        report(campaigns, 0.5, "casual");
    }

    private static void report(int campaigns, double skill, String name) {
        int n = Level.COUNT;
        double[] play = new double[n], animals = new double[n], bonus = new double[n], hungryRate = new double[n], streak = new double[n];
        double[] farmMoves = new double[n], chickens = new double[n], cows = new double[n], banked = new double[n];
        double[][] hauled = new double[n][Farm.STOCKS];
        for (int g = 0; g < campaigns; g++) {
            Farm.Herd h = new Farm.Herd();
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
                coins = spend(h, coins, i);
                chickens[i] += h.chickens;
                cows[i] += h.cows;
                banked[i] += coins;
            }
        }
        System.out.printf("%n%s bot, %d campaigns%n", name, campaigns);
        System.out.println("lvl  play | animals (share, streak bonus) | hungry lvls | streak | hauled egg seed hay water | hens cows | farm moves | banked");
        for (int i = 0; i < n; i++) {
            double pl = play[i] / campaigns, an = animals[i] / campaigns;
            System.out.printf("%3d %5.0f | %5.0f (%4.1f%%, %4.0f) | %9.0f%% | %6.1f | %12.0f %4.0f %4.0f %5.0f | %4.1f %4.1f | %10.1f | %6.0f%n", i + 1, pl, an, 100 * an / (pl + an), bonus[i] / campaigns,
                    100 * hungryRate[i] / campaigns, streak[i] / campaigns, hauled[i][0] / campaigns, hauled[i][1] / campaigns, hauled[i][2] / campaigns, hauled[i][3] / campaigns,
                    chickens[i] / campaigns, cows[i] / campaigns, farmMoves[i] / campaigns, banked[i] / campaigns);
        }
    }

    /** Hatch what can be hatched, then buy the coop, barn, cows and house upgrades as soon as they are affordable. */
    private static int spend(Farm.Herd h, int coins, int levelIndex) {
        boolean again = true;
        while (again) {
            again = false;
            while (h.canHatch()) h.hatch();
            int coopCost = h.house[Farm.COOP] >= Farm.MAX_HOUSE_LEVEL ? -1 : Farm.HOUSE_COST[Farm.COOP][h.house[Farm.COOP]];
            int barnCost = h.house[Farm.BARN] >= Farm.MAX_HOUSE_LEVEL ? -1 : Farm.HOUSE_COST[Farm.BARN][h.house[Farm.BARN]];
            boolean barnOpen = levelIndex + 2 >= Farm.HOUSE_UNLOCK_LEVEL[Farm.BARN];
            if (h.house[Farm.BARN] > 0 && h.cowRoom() > 0 && coins >= Farm.COW_PRICE) {
                coins -= Farm.COW_PRICE;
                h.cows++;
                again = true;
            } else if (h.house[Farm.COOP] == 0 && coins >= coopCost) {
                coins -= coopCost;
                h.house[Farm.COOP]++;
                again = true;
            } else if (h.house[Farm.BARN] == 0 && barnOpen && coins >= barnCost) {
                coins -= barnCost;
                h.house[Farm.BARN]++;
                again = true;
            } else if (h.house[Farm.COOP] > 0 && coopCost > 0 && h.chickenRoom() <= 0 && coins >= coopCost) {
                coins -= coopCost;
                h.house[Farm.COOP]++;
                again = true;
            } else if (h.house[Farm.BARN] > 0 && barnCost > 0 && h.cowRoom() <= 0 && coins >= barnCost) {
                coins -= barnCost;
                h.house[Farm.BARN]++;
                again = true;
            }
        }
        return coins;
    }

    private FarmSim() {
    }
}
