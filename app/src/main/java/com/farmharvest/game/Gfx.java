package com.farmharvest.game;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/** Cartoon drawing helpers: outlined text, chunky panels, coins, stars. */
final class Gfx {
    static final int GREEN = 0xFF5BC236, ORANGE = 0xFFFF9B26, BLUE = 0xFF3FA9F5, PANEL = 0xFF6B3F17;
    static final int CREAM = 0xFFFFF4D2, WOOD = 0xFF8B5A2B;
    static final int GOLD = 0xFFFFC928;
    static final int GOLD_DARK = 0xFFC97700;
    static final int INK = 0xFF5A3410;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    Gfx() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        tp.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        tp.setStrokeJoin(Paint.Join.ROUND);
    }

    static int darken(int c, float f) {
        return Color.argb(Color.alpha(c), (int) (Color.red(c) * f), (int) (Color.green(c) * f), (int) (Color.blue(c) * f));
    }

    static int lighten(int c, float f) {
        return Color.argb(Color.alpha(c),
                (int) (Color.red(c) + (255 - Color.red(c)) * f),
                (int) (Color.green(c) + (255 - Color.green(c)) * f),
                (int) (Color.blue(c) + (255 - Color.blue(c)) * f));
    }

    static int alpha(int c, float a) {
        return Color.argb((int) (255 * Math.max(0f, Math.min(1f, a))), Color.red(c), Color.green(c), Color.blue(c));
    }

    /** Baseline text with a thick outline. */
    void text(Canvas c, String s, float x, float baseline, float size, int color, int outline, Paint.Align align) {
        tp.setTextSize(size);
        tp.setTextAlign(align);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(size * 0.18f);
        tp.setColor(outline);
        c.drawText(s, x, baseline, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setColor(color);
        c.drawText(s, x, baseline, tp);
    }

    /** Vertically centred variant of text(). */
    void textC(Canvas c, String s, float x, float cy, float size, int color, int outline, Paint.Align align) {
        text(c, s, x, cy + size * 0.36f, size, color, outline, align);
    }

    float width(String s, float size) {
        tp.setTextSize(size);
        tp.setStyle(Paint.Style.FILL);
        return tp.measureText(s);
    }

    void rrect(Canvas c, float l, float t, float r, float b, float rad, int color) {
        fill.setColor(color);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, fill);
    }

    void rrectLine(Canvas c, float l, float t, float r, float b, float rad, float w, int color) {
        line.setColor(color);
        line.setStrokeWidth(w);
        rf.set(l, t, r, b);
        c.drawRoundRect(rf, rad, rad, line);
    }

    void circle(Canvas c, float cx, float cy, float r, int color) {
        fill.setColor(color);
        c.drawCircle(cx, cy, r, fill);
    }

    void circleLine(Canvas c, float cx, float cy, float r, float w, int color) {
        line.setColor(color);
        line.setStrokeWidth(w);
        c.drawCircle(cx, cy, r, line);
    }

    void ellipse(Canvas c, float cx, float cy, float rx, float ry, int color) {
        fill.setColor(color);
        rf.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(rf, fill);
    }

    void star(Canvas c, float cx, float cy, float R, int color, int outline) {
        path.reset();
        for (int i = 0; i < 10; i++) {
            double a = Math.toRadians(-90 + i * 36);
            float rr = (i % 2 == 0) ? R : R * 0.46f;
            float px = cx + (float) Math.cos(a) * rr;
            float py = cy + (float) Math.sin(a) * rr;
            if (i == 0) path.moveTo(px, py);
            else path.lineTo(px, py);
        }
        path.close();
        if (outline != 0) {
            line.setColor(outline);
            line.setStrokeWidth(R * 0.28f);
            c.drawPath(path, line);
        }
        fill.setColor(color);
        c.drawPath(path, fill);
    }

    /** Cartoon gold coin, centred at (cx, cy) with radius r. */
    void coin(Canvas c, float cx, float cy, float r) {
        circle(c, cx, cy + r * 0.08f, r, 0xFFB86A00);
        circle(c, cx, cy, r, 0xFFE59A00);
        circle(c, cx, cy, r * 0.88f, GOLD);
        circleLine(c, cx, cy, r * 0.64f, r * 0.11f, 0xFFE59A00);
        circle(c, cx, cy, r * 0.58f, 0xFFFFDD55);
        star(c, cx, cy + r * 0.02f, r * 0.38f, 0xFFFFF3B0, 0xFFE59A00);
        line.setColor(0xB3FFFFFF);
        line.setStrokeWidth(r * 0.12f);
        rf.set(cx - r * 0.76f, cy - r * 0.76f, cx + r * 0.76f, cy + r * 0.76f);
        c.drawArc(rf, 200, 60, false, line);
    }

    /** Three overlapping coins, used as the counter icon. */
    void coinPile(Canvas c, float cx, float cy, float r) {
        coin(c, cx - r * 0.55f, cy + r * 0.35f, r * 0.78f);
        coin(c, cx + r * 0.55f, cy + r * 0.4f, r * 0.78f);
        coin(c, cx, cy - r * 0.05f, r * 0.9f);
    }

    void check(Canvas c, float cx, float cy, float r) {
        circle(c, cx, cy, r, 0xFF4CBB2C);
        circleLine(c, cx, cy, r, r * 0.16f, 0xFF2C7F14);
        line.setColor(Color.WHITE);
        line.setStrokeWidth(r * 0.24f);
        path.reset();
        path.moveTo(cx - r * 0.45f, cy + r * 0.02f);
        path.lineTo(cx - r * 0.12f, cy + r * 0.36f);
        path.lineTo(cx + r * 0.5f, cy - r * 0.36f);
        c.drawPath(path, line);
    }

    void lock(Canvas c, float cx, float cy, float r) {
        line.setColor(0xFF6B6B6B);
        line.setStrokeWidth(r * 0.22f);
        rf.set(cx - r * 0.42f, cy - r * 0.85f, cx + r * 0.42f, cy + r * 0.1f);
        c.drawArc(rf, 180, 180, false, line);
        c.drawLine(cx - r * 0.42f, cy - r * 0.38f, cx - r * 0.42f, cy, line);
        c.drawLine(cx + r * 0.42f, cy - r * 0.38f, cx + r * 0.42f, cy, line);
        rrect(c, cx - r * 0.65f, cy - r * 0.1f, cx + r * 0.65f, cy + r * 0.85f, r * 0.2f, 0xFF4A4A4A);
        circle(c, cx, cy + r * 0.38f, r * 0.14f, 0xFFD0D0D0);
    }

    /** Old cartoon leather money bag. fill (0..1) controls how many coins poke out of the mouth. */
    void moneyBag(Canvas c, float cx, float cy, float s, float fillFrac) {
        final int L = 0xFFA9692F, LD = 0xFF7A4520, LL = 0xFFC98B4E, OUT = 0xFF3E2008, ROPE = 0xFFE3C084;
        ellipse(c, cx, cy + 0.56f * s, 0.42f * s, 0.065f * s, 0x40000000);
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        line.setStrokeWidth(0.03f);
        line.setColor(OUT);
        path.reset();
        path.moveTo(-0.15f, -0.17f);
        path.cubicTo(-0.58f, 0.02f, -0.52f, 0.55f, 0, 0.55f);
        path.cubicTo(0.52f, 0.55f, 0.58f, 0.02f, 0.15f, -0.17f);
        path.close();
        fill.setColor(L);
        c.drawPath(path, fill);
        ellipse(c, -0.06f, 0.24f, 0.27f, 0.22f, LL);
        c.drawPath(path, line);
        line.setColor(LD);
        line.setStrokeWidth(0.02f);
        path.reset();
        path.moveTo(-0.33f, 0.04f);
        path.quadTo(-0.27f, 0.18f, -0.34f, 0.32f);
        path.moveTo(0.3f, 0.12f);
        path.quadTo(0.2f, 0.28f, 0.27f, 0.42f);
        path.moveTo(-0.12f, 0.46f);
        path.quadTo(0.0f, 0.5f, 0.14f, 0.45f);
        c.drawPath(path, line);
        line.setPathEffect(new android.graphics.DashPathEffect(new float[]{0.035f, 0.03f}, 0));
        path.reset();
        path.moveTo(0.4f, 0.14f);
        path.quadTo(0.47f, 0.38f, 0.28f, 0.5f);
        c.drawPath(path, line);
        line.setPathEffect(null);
        ellipse(c, -0.31f, 0.14f, 0.045f, 0.12f, 0x55FFFFFF);
        // frilled top
        path.reset();
        path.moveTo(-0.16f, -0.18f);
        path.cubicTo(-0.33f, -0.28f, -0.34f, -0.47f, -0.25f, -0.53f);
        path.quadTo(-0.12f, -0.46f, 0f, -0.53f);
        path.quadTo(0.12f, -0.46f, 0.25f, -0.53f);
        path.cubicTo(0.34f, -0.47f, 0.33f, -0.28f, 0.16f, -0.18f);
        path.close();
        fill.setColor(L);
        c.drawPath(path, fill);
        line.setColor(OUT);
        line.setStrokeWidth(0.03f);
        c.drawPath(path, line);
        ellipse(c, 0f, -0.5f, 0.2f, 0.055f, 0xFF2B1606);
        // rope tie
        line.setStrokeWidth(0.065f);
        c.drawLine(0.02f, -0.15f, 0.11f, -0.05f, line);
        c.drawLine(-0.02f, -0.15f, -0.11f, -0.06f, line);
        line.setColor(ROPE);
        line.setStrokeWidth(0.035f);
        c.drawLine(0.02f, -0.15f, 0.11f, -0.05f, line);
        c.drawLine(-0.02f, -0.15f, -0.11f, -0.06f, line);
        rrect(c, -0.2f, -0.225f, 0.2f, -0.13f, 0.045f, ROPE);
        rrectLine(c, -0.2f, -0.225f, 0.2f, -0.13f, 0.045f, 0.022f, OUT);
        circle(c, 0f, -0.178f, 0.06f, 0xFFD2A965);
        circleLine(c, 0f, -0.178f, 0.06f, 0.02f, OUT);
        // coins poking out of the mouth
        if (fillFrac > 0f) {
            if (fillFrac > 0.33f) coin(c, -0.1f, -0.55f, 0.1f);
            if (fillFrac > 0.66f) coin(c, 0.1f, -0.56f, 0.1f);
            coin(c, 0f, -0.56f - 0.1f * fillFrac, 0.11f);
        }
        c.restore();
        text(c, "$", cx, cy + 0.36f * s, 0.34f * s, 0xFFF2D58A, OUT, Paint.Align.CENTER);
    }

    /** Glowing beam with a bright core. */
    void beam(Canvas c, float x1, float y1, float x2, float y2, float w, int color) {
        line.setColor(color);
        line.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, line);
        line.setColor(0xCCFFFFFF);
        line.setStrokeWidth(w * 0.35f);
        c.drawLine(x1, y1, x2, y2, line);
    }

    /** Speaker icon with sound waves, or a cross when muted. */
    void speaker(Canvas c, float cx, float cy, float r, boolean muted) {
        fill.setColor(Color.WHITE);
        path.reset();
        path.moveTo(cx - r * 0.55f, cy - r * 0.22f);
        path.lineTo(cx - r * 0.25f, cy - r * 0.22f);
        path.lineTo(cx + r * 0.1f, cy - r * 0.55f);
        path.lineTo(cx + r * 0.1f, cy + r * 0.55f);
        path.lineTo(cx - r * 0.25f, cy + r * 0.22f);
        path.lineTo(cx - r * 0.55f, cy + r * 0.22f);
        path.close();
        c.drawPath(path, fill);
        line.setColor(Color.WHITE);
        line.setStrokeWidth(r * 0.14f);
        if (muted) {
            line.setColor(0xFFFF6A5A);
            line.setStrokeWidth(r * 0.2f);
            c.drawLine(cx + r * 0.3f, cy - r * 0.3f, cx + r * 0.75f, cy + r * 0.3f, line);
            c.drawLine(cx + r * 0.75f, cy - r * 0.3f, cx + r * 0.3f, cy + r * 0.3f, line);
        } else {
            rf.set(cx - r * 0.1f, cy - r * 0.32f, cx + r * 0.5f, cy + r * 0.32f);
            c.drawArc(rf, -50, 100, false, line);
            rf.set(cx - r * 0.25f, cy - r * 0.6f, cx + r * 0.8f, cy + r * 0.6f);
            c.drawArc(rf, -50, 100, false, line);
        }
    }

    /** Filled polygon with an outline. pts is x0,y0,x1,y1,... */
    void polygon(Canvas c, int fillColor, int lineColor, float lineW, float... pts) {
        path.reset();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) path.lineTo(pts[i], pts[i + 1]);
        path.close();
        fill.setColor(fillColor);
        c.drawPath(path, fill);
        if (lineW > 0) {
            line.setColor(lineColor);
            line.setStrokeWidth(lineW);
            c.drawPath(path, line);
        }
    }

    void lineSeg(Canvas c, float x1, float y1, float x2, float y2, float w, int color) {
        line.setColor(color);
        line.setStrokeWidth(w);
        c.drawLine(x1, y1, x2, y2, line);
    }

    /** White booster glyphs: extra moves, shuffle, hammer. r is roughly the icon radius. */
    void boosterIcon(Canvas c, int kind, float cx, float cy, float r) {
        switch (kind) {
            case Farm.MOVES:
                text(c, "+5", cx, cy + r * 0.38f, r * 1.25f, Color.WHITE, 0xFF3A1F08, Paint.Align.CENTER);
                break;
            case Farm.SHUFFLE: {
                float w = r * 0.2f;
                lineSeg(c, cx - r * 0.65f, cy - r * 0.45f, cx + r * 0.6f, cy + r * 0.45f, w, Color.WHITE);
                lineSeg(c, cx - r * 0.65f, cy + r * 0.45f, cx + r * 0.6f, cy - r * 0.45f, w, Color.WHITE);
                polygon(c, Color.WHITE, Color.WHITE, w * 0.3f, cx + r * 0.85f, cy + r * 0.6f, cx + r * 0.35f, cy + r * 0.6f, cx + r * 0.6f, cy + r * 0.15f);
                polygon(c, Color.WHITE, Color.WHITE, w * 0.3f, cx + r * 0.85f, cy - r * 0.6f, cx + r * 0.35f, cy - r * 0.6f, cx + r * 0.6f, cy - r * 0.15f);
                break;
            }
            default: {
                c.save();
                c.rotate(-40, cx, cy);
                rrect(c, cx - r * 0.12f, cy - r * 0.1f, cx + r * 0.12f, cy + r * 0.95f, r * 0.08f, 0xFF8B5A2B);
                rrect(c, cx - r * 0.6f, cy - r * 0.7f, cx + r * 0.6f, cy - r * 0.02f, r * 0.14f, 0xFFD9D9D9);
                rrectLine(c, cx - r * 0.6f, cy - r * 0.7f, cx + r * 0.6f, cy - r * 0.02f, r * 0.14f, r * 0.08f, 0xFF4A4A4A);
                c.restore();
            }
        }
    }

    void clipCircle(Canvas c, float cx, float cy, float r) {
        path.reset();
        path.addCircle(cx, cy, r, Path.Direction.CW);
        c.clipPath(path);
    }

    void backArrow(Canvas c, float cx, float cy, float r) {
        line.setColor(Color.WHITE);
        line.setStrokeWidth(r * 0.3f);
        path.reset();
        path.moveTo(cx + r * 0.2f, cy - r * 0.5f);
        path.lineTo(cx - r * 0.3f, cy);
        path.lineTo(cx + r * 0.2f, cy + r * 0.5f);
        c.drawPath(path, line);
    }
}
