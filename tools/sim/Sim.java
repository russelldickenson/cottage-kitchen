package com.zigzag.cottagekitchen;

import java.util.*;

/**
 * Headless level simulator. Uses the same Rules and Board code as the game and plays every
 * level with bots of different skill so level goals and move limits can be tuned from data.
 * Run: tools/sim/run.sh [games-per-level]
 */
public class Sim {
    static final int COLS = Rules.COLS, ROWS = Rules.ROWS;
    static final Random rnd = new Random(42);

    static final class State {
        int[] need = new int[Art.COUNT];
        int moves;
        boolean batchBonus;

        State copy() {
            State s = new State();
            s.need = need.clone();
            s.moves = moves;
            s.batchBonus = batchBonus;
            return s;
        }

        boolean done() {
            for (int n : need) if (n > 0) return false;
            return true;
        }
    }

    static Board clone(Board b, int[] pal) {
        Board c = new Board(COLS, ROWS, pal, rnd);
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                c.g[r][col].type = b.g[r][col].type;
                c.g[r][col].hp = b.g[r][col].hp;
            }
        }
        return c;
    }

    static boolean valid(Board b, int[] m) {
        Tile a = b.g[m[0]][m[1]], t = b.g[m[2]][m[3]];
        if (Art.isCrate(a.type) || Art.isCrate(t.type)) return false;
        b.swap(m[0], m[1], m[2], m[3]);
        boolean ok = !b.findRuns().isEmpty();
        b.swap(m[0], m[1], m[2], m[3]);
        return ok;
    }

    static List<int[]> moves(Board b) {
        List<int[]> out = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int[] right = {r, c, r, c + 1}, down = {r, c, r + 1, c};
                if (c + 1 < COLS && valid(b, right)) out.add(right);
                if (r + 1 < ROWS && valid(b, down)) out.add(down);
            }
        }
        return out;
    }

    /** Applies one player move. Returns goal progress (higher is better), or -1 if the move is illegal. */
    static double play(Board b, State s, int[] m, boolean full) {
        b.swap(m[0], m[1], m[2], m[3]);
        List<Board.Run> runs = b.findRuns();
        if (runs.isEmpty()) {
            b.swap(m[0], m[1], m[2], m[3]);
            return -1;
        }
        s.moves--;
        double gain = 0;
        Set<Tile> counted = new LinkedHashSet<>();
        while (true) {
            if (!s.batchBonus && Rules.bigMatch(runs)) {
                s.batchBonus = true;
                s.moves++;
                gain += 2;
            }
            for (Board.Run run : runs) counted.addAll(run.tiles);
            Set<Tile> clear = new LinkedHashSet<>(counted);
            List<Tile> damaged = new ArrayList<>();
            List<Tile> broken = Rules.hitCrates(b, counted, damaged);
            gain += damaged.size();
            for (Tile t : broken) {
                if (s.need[Art.CRATE] > 0) s.need[Art.CRATE]--;
                gain += 3;
            }
            clear.removeIf(t -> Art.isCrate(t.type));
            clear.addAll(broken);
            for (Tile t : counted) {
                if (t.type < Art.CROPS && s.need[t.type] > 0) {
                    s.need[t.type]--;
                    gain += 1;
                } else gain += 0.1;
            }
            for (Tile t : clear) b.g[t.r][t.c] = null;
            b.collapse();
            if (!full) return gain;
            runs = b.findRuns();
            if (runs.isEmpty()) break;
            counted = new LinkedHashSet<>();
        }
        return gain;
    }

    /** Plays one level. Returns the moves left, or -1 on a loss. */
    static int playLevel(Level lv, double skill) {
        Board b = new Board(COLS, ROWS, lv.kinds, rnd);
        b.addCrates(lv.crates(), rnd);
        State s = new State();
        for (int i = 0; i < lv.goalKinds.length; i++) s.need[lv.goalKinds[i]] = lv.goalCounts[i];
        s.moves = lv.moves;
        runMoves(b, s, lv, skill);
        return s.done() ? s.moves : -1;
    }

    private static void runMoves(Board b, State s, Level lv, double skill) {
        while (s.moves > 0 && !s.done()) {
            if (b.findMove() == null) b.reshuffle();
            List<int[]> ms = moves(b);
            int[] pick;
            if (rnd.nextDouble() >= skill) {
                pick = ms.get(rnd.nextInt(ms.size()));
            } else {
                double best = -1;
                pick = ms.get(0);
                for (int[] m : ms) {
                    double g = play(clone(b, lv.kinds), s.copy(), m, false) + rnd.nextDouble() * 0.01;
                    if (g > best) {
                        best = g;
                        pick = m;
                    }
                }
            }
            play(b, s, pick, true);
        }
    }

    /** Moves an expert needs on average with an unlimited budget. */
    static double expertMoves(int index, int scale, int games) {
        Level lv = Level.get(index, 999, scale);
        double used = 0;
        for (int g = 0; g < games; g++) used += 999 - playLevel(lv, 1.0);
        return used / games;
    }

    public static void main(String[] args) {
        if (args.length > 1 && args[1].equals("tune")) {
            int games = Integer.parseInt(args[0]);
            StringBuilder sm = new StringBuilder();
            for (int i = 0; i < Level.COUNT; i++) {
                // the budget is the expert's average, with slack that shrinks over the game
                double slack = 1.5 - 0.4 * i / (Level.COUNT - 1);
                int scale = Level.scaleFor(i);
                double target = expertMoves(i, scale, games);
                int moves = Math.max(9, (int) Math.ceil(target * slack));
                System.out.printf("level %2d  %-18s %-12s collect %3d  expert needs %.1f  budget %d (x%.2f)%n", i + 1, Level.get(i).recipe.name, Recipes.INGREDIENT[Level.get(i).ingredient()] + (Level.get(i).crates() > 0 ? "+crates" : ""), scale, target, moves, slack);
                sm.append(moves).append(i + 1 < Level.COUNT ? ", " : "");
            }
            System.out.println("MOVES = {" + sm + "}");
            return;
        }
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        double[] skills = {1.0, 0.5, 0.0};
        System.out.printf("%-5s %-34s %5s | %-22s | %-22s | %-22s%n", "level", "goals", "moves", "expert  win%  left", "casual  win%  left", "random  win%  left");
        for (int i = 0; i < Level.COUNT; i++) {
            Level lv = Level.get(i);
            StringBuilder goals = new StringBuilder();
            for (int j = 0; j < lv.goalKinds.length; j++) goals.append(lv.goalCounts[j]).append(j + 1 < lv.goalKinds.length ? "/" : "");
            StringBuilder row = new StringBuilder(String.format("%-5d %-34s %5d |", lv.number, lv.recipe.name + " " + goals, lv.moves));
            for (double sk : skills) {
                int wins = 0, left = 0;
                for (int g = 0; g < n; g++) {
                    int r = playLevel(lv, sk);
                    if (r >= 0) {
                        wins++;
                        left += r;
                    }
                }
                row.append(String.format(" %12.0f%% %6.1f |", 100.0 * wins / n, wins == 0 ? 0.0 : (double) left / wins));
            }
            System.out.println(row);
        }
    }
}
