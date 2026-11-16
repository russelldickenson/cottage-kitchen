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

    void clipRRect(Canvas c, RectF r, float rad) {
        path.reset();
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(path);
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
