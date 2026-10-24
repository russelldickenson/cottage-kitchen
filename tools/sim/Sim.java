package com.farmharvest.game;

import java.util.*;

/**
 * Headless level simulator. Mirrors the clear/spawn/activation rules in GameView and plays every
 * level with bots of different skill so level goals and move limits can be tuned from data.
 * Run: tools/sim/run.sh [games-per-level]
 */
public class Sim {
    static final int COLS = 7, ROWS = 8;
    static final Random rnd = new Random(42);

    static final class State {
        int[] need = new int[Art.COUNT];
        int moves;

        State copy() {
            State s = new State();
            s.need = need.clone();
            s.moves = moves;
            return s;
        }

        boolean done() {
            for (int n : need) if (n > 0) return false;
            return true;
        }
    }

    static Board clone(Board b, int[] pal) {
        Board c = new Board(COLS, ROWS, pal, rnd);
        for (int r = 0; r < ROWS; r++) for (int col = 0; col < COLS; col++) c.g[r][col].type = b.g[r][col].type;
        return c;
    }

    static boolean valid(Board b, int[] m) {
        Tile a = b.g[m[0]][m[1]], t = b.g[m[2]][m[3]];
        if (Art.isSpecial(a.type) || Art.isSpecial(t.type)) return true;
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
        Tile a = b.g[m[0]][m[1]], t2 = b.g[m[2]][m[3]];
        b.swap(m[0], m[1], m[2], m[3]);
        Set<Tile> counted = new LinkedHashSet<>(), clear;
        List<Tile> spT = new ArrayList<>();
        List<Integer> spTy = new ArrayList<>();
        if (Art.isSpecial(a.type) || Art.isSpecial(t2.type)) {
            clear = special(b, a, t2);
            counted.addAll(clear);
        } else {
            List<Board.Run> runs = b.findRuns();
            if (runs.isEmpty()) {
                b.swap(m[0], m[1], m[2], m[3]);
                return -1;
            }
            clear = fromRuns(runs, a, t2, counted, spT, spTy);
        }
        s.moves--;
        double gain = 0;
        while (true) {
            for (Tile t : counted) {
                if (t.type < Art.CROPS && s.need[t.type] > 0) {
                    s.need[t.type]--;
                    gain += 1;
                } else gain += 0.1;
            }
            gain += 2 * spT.size();
            for (Tile t : clear) b.g[t.r][t.c] = null;
            for (int i = 0; i < spT.size(); i++) spT.get(i).type = spTy.get(i);
            b.collapse();
            if (!full) return gain;
            List<Board.Run> runs = b.findRuns();
            if (runs.isEmpty()) break;
            counted = new LinkedHashSet<>();
            spT = new ArrayList<>();
            spTy = new ArrayList<>();
            clear = fromRuns(runs, null, null, counted, spT, spTy);
        }
        return gain;
    }

    static Set<Tile> fromRuns(List<Board.Run> runs, Tile sa, Tile sb, Set<Tile> counted, List<Tile> spT, List<Integer> spTy) {
        for (Board.Run run : runs) {
            if (run.tiles.size() < 5) continue;
            Tile at = run.tiles.contains(sa) ? sa : run.tiles.contains(sb) ? sb : run.tiles.get(run.tiles.size() / 2);
            if (!spT.contains(at)) {
                spT.add(at);
                spTy.add(rnd.nextBoolean() ? Art.GOLDEN_EGG : Art.TRACTOR);
            }
        }
        for (Board.Run h : runs) {
            if (!h.horizontal) continue;
            for (Board.Run v : runs) {
                if (v.horizontal || v.type != h.type) continue;
                for (Tile t : h.tiles) {
                    if (v.tiles.contains(t)) {
                        if (!spT.contains(t)) {
                            spT.add(t);
                            spTy.add(rnd.nextBoolean() ? Art.TNT : Art.SCARECROW);
                        }
                        break;
                    }
                }
            }
        }
        for (Board.Run run : runs) counted.addAll(run.tiles);
        Set<Tile> clear = new LinkedHashSet<>(counted);
        clear.removeAll(spT);
        return clear;
    }

    static Set<Tile> special(Board b, Tile a, Tile t2) {
        boolean ac = Art.isColor(a.type), bc = Art.isColor(t2.type);
        boolean as = Art.isSpecial(a.type), bs = Art.isSpecial(t2.type);
        Set<Tile> set = new LinkedHashSet<>();
        if ((ac || bc) && as && bs) {
            for (Tile[] row : b.g) for (Tile t : row) if (t != null) set.add(t);
        } else if (ac || bc) {
            Tile act = ac ? a : t2, other = ac ? t2 : a;
            for (Tile[] row : b.g) for (Tile t : row) if (t != null && t.type == other.type) set.add(t);
            set.add(act);
        } else {
            ArrayDeque<Tile> q = new ArrayDeque<>();
            for (Tile t : new Tile[]{a, t2}) if (Art.isBomb(t.type) && set.add(t)) q.add(t);
            while (!q.isEmpty()) {
                Tile t = q.poll();
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = t.r + dr, nc = t.c + dc;
                        if (nr < 0 || nc < 0 || nr >= ROWS || nc >= COLS) continue;
                        Tile n = b.g[nr][nc];
                        if (n != null && set.add(n) && Art.isBomb(n.type)) q.add(n);
                    }
                }
            }
        }
        return set;
    }

    /** skill: 1.0 always plays the best goal move, 0.0 plays a random legal move. Returns moves left, or -1 on a loss. */
    static int playLevel(Level lv, double skill) {
        Board b = new Board(COLS, ROWS, lv.kinds, rnd);
        State s = new State();
        for (int i = 0; i < lv.goalKinds.length; i++) s.need[lv.goalKinds[i]] = lv.goalCounts[i];
        s.moves = lv.moves;
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
        return s.done() ? s.moves : -1;
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
            StringBuilder sm = new StringBuilder(), ss = new StringBuilder();
            int prevMoves = 0;
            for (int i = 0; i < Level.COUNT; i++) {
                double slack = 1.5 - 0.35 * i / (Level.COUNT - 1);
                int lo = (int) Math.round(10 + 1.3 * i);
                double target = expertMoves(i, lo, games);
                int moves = Math.max(prevMoves, (int) Math.ceil(target * slack));
                prevMoves = moves;
                System.out.printf("level %2d  scale %3d  expert needs %.1f  budget %d (x%.2f)%n", i + 1, lo, expertMoves(i, lo, games), moves, slack);
                ss.append(lo).append(i + 1 < Level.COUNT ? ", " : "");
                sm.append(moves).append(i + 1 < Level.COUNT ? ", " : "");
            }
            System.out.println("SCALE = {" + ss + "}");
            System.out.println("MOVES = {" + sm + "}");
            return;
        }
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 100;
        double[] skills = {1.0, 0.5, 0.0};
        System.out.printf("%-5s %-26s %5s | %-22s | %-22s | %-22s%n", "level", "goals", "moves", "expert  win%  left", "casual  win%  left", "random  win%  left");
        for (int i = 0; i < Level.COUNT; i++) {
            Level lv = Level.get(i);
            StringBuilder goals = new StringBuilder();
            for (int j = 0; j < lv.goalKinds.length; j++) goals.append(lv.goalCounts[j]).append(j + 1 < lv.goalKinds.length ? "/" : "");
            StringBuilder row = new StringBuilder(String.format("%-5d %-26s %5d |", lv.number, goals + " (" + lv.kinds.length + " crops)", lv.moves));
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
