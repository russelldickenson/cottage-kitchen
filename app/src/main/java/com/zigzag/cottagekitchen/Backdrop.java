package com.zigzag.cottagekitchen;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * The cosy kitchen behind every screen: a peach wall with bunting and a sunny window, and a wooden counter over
 * sage cabinets. It is drawn once per screen size into a bitmap, in the same outlined style as the artwork but with
 * soft colours and a pale outline, so the board and the dishes stay the loudest things on screen.
 */
final class Backdrop {
    /** Pale brown outline, much lighter than the artwork's, so the scene sits back. */
    private static final int OUT = 0xFFD9BC96;
    private static final int[] FLAGS = {0xFFF4B9B2, 0xFFFFF1D8, 0xFFB5D9B0, 0xFFFFDE94};

    private final Gfx gfx;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private float u;

    Backdrop(Gfx gfx) {
        this.gfx = gfx;
    }

    Bitmap build(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        u = w / 100f;
        wall(c, w, h);
        window(c, w, h);
        bunting(c, w, h);
        float counter = h * 0.835f;
        items(c, w, counter);
        counter(c, w, h, counter);
        return bmp;
    }

    // ---------------------------------------------------------------- pieces

    private void wall(Canvas c, int w, int h) {
        paint.setShader(new LinearGradient(0, 0, 0, h, 0xFFFCEBD3, 0xFFF6DEC0, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        // faint wallpaper stripes
        paint.setColor(0x12E8B77A);
        for (float x = 0; x < w; x += 9 * u) c.drawRect(x, 0, x + 4.5f * u, h, paint);
    }

    private void bunting(Canvas c, int w, int h) {
        float y0 = h * 0.012f, sag = h * 0.022f;
        int n = 9;
        path.reset();
        path.moveTo(-2 * u, y0);
        path.quadTo(w / 2f, y0 + 2 * sag, w + 2 * u, y0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.5f * u);
        paint.setColor(OUT);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < n; i++) {
            float t = (i + 0.5f) / n;
            float x = -2 * u + t * (w + 4 * u), y = y0 + 4 * sag * t * (1 - t);
            gfx.polygon(c, FLAGS[i % FLAGS.length], OUT, 0.45f * u, x - 4.6f * u, y, x + 4.6f * u, y, x, y + 8.6f * u);
        }
    }

    private void window(Canvas c, int w, int h) {
        float x0 = 51 * u, x1 = 86 * u, y0 = h * 0.072f, y1 = h * 0.205f, ph = y1 - y0;
        // frame and glass
        box(c, x0, y0, x1, y1, 2 * u, 0xFFFFF3E0);
        float g = 2.4f * u;
        rect.set(x0 + g, y0 + g, x1 - g, y1 - g);
        c.save();
        gfx.clipRRect(c, rect, 0.8f * u);
        paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0xFFC4E5F9, 0xFFEAF7FF, Shader.TileMode.CLAMP));
        c.drawRect(rect, paint);
        paint.setShader(null);
        gfx.circle(c, x1 - 10 * u, y0 + 11 * u, 7 * u, 0x44FFE9A6);
        gfx.circle(c, x1 - 10 * u, y0 + 11 * u, 4.2f * u, 0xFFFFEBA8);
        gfx.ellipse(c, x0 + 14 * u, y0 + 0.22f * ph, 6 * u, 1.8f * u, 0xCCFFFFFF);
        gfx.ellipse(c, x0 + 18 * u, y0 + 0.2f * ph, 4 * u, 1.6f * u, 0xCCFFFFFF);
        gfx.ellipse(c, x0 + 8 * u, y1 - 3 * u, 22 * u, 9 * u, 0xFFCFE8B8);
        gfx.ellipse(c, x1 - 4 * u, y1 - 1 * u, 22 * u, 8 * u, 0xFFB8DD9E);
        // muntins
        gfx.lineSeg(c, (x0 + x1) / 2, y0 + g, (x0 + x1) / 2, y1 - g, 1.5f * u, 0xFFFFF3E0);
        gfx.lineSeg(c, x0 + g, y0 + 0.56f * ph, x1 - g, y0 + 0.56f * ph, 1.5f * u, 0xFFFFF3E0);
        c.restore();
        // sill
        box(c, x0 - 2.5f * u, y1 - 0.4f * u, x1 + 2.5f * u, y1 + 2.4f * u, 1 * u, 0xFFEBCB9C);
        // curtain rod
        gfx.lineSeg(c, x0 - 9 * u, y0 - 3.2f * u, x1 + 9 * u, y0 - 3.2f * u, 1.1f * u, 0xFFD6B07A);
        gfx.circle(c, x0 - 9 * u, y0 - 3.2f * u, 1.5f * u, 0xFFE6C48C);
        gfx.circle(c, x1 + 9 * u, y0 - 3.2f * u, 1.5f * u, 0xFFE6C48C);
        // curtains, tied back
        float top = y0 - 2.4f * u, waist = y0 + 0.55f * ph, bot = y1 + 1.4f * u;
        curtain(c, x0, top, waist, bot, false);
        curtain(c, x1, top, waist, bot, true);
    }

    /** A gingham curtain hanging from the window edge x, tied back at the waist. */
    private void curtain(Canvas c, float x, float top, float waist, float bot, boolean mirror) {
        float s = mirror ? -1 : 1;
        float[] p = {
                x - s * 7.5f * u, top, x + s * 7.5f * u, top, x + s * 5.8f * u, waist - 8 * u, x + s * 3f * u, waist,
                x + s * 6.8f * u, bot, x - s * 7.5f * u, bot};
        path.reset();
        path.moveTo(p[0], p[1]);
        for (int i = 2; i < p.length; i += 2) path.lineTo(p[i], p[i + 1]);
        path.close();
        c.save();
        c.clipPath(path);
        paint.setColor(0xFFF5C2BB);
        c.drawPaint(paint);
        float cell = 2.6f * u;
        paint.setColor(0x66FFFFFF);
        for (float xx = Math.min(p[0], p[2]) - cell; xx < Math.max(p[0], p[2]) + cell; xx += 2 * cell) c.drawRect(xx, top, xx + cell, bot, paint);
        for (float yy = top; yy < bot; yy += 2 * cell) c.drawRect(Math.min(p[0], p[2]) - cell, yy, Math.max(p[0], p[2]) + cell, yy + cell, paint);
        c.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.5f * u);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(OUT);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        gfx.circle(c, x + s * 3.6f * u, waist + 0.3f * u, 1.5f * u, 0xFFE59A8F);
    }

    private void items(Canvas c, int w, float counter) {
        // a mixing bowl with a wooden spoon
        float bx = 17 * u, rim = counter - 8.5f * u;
        gfx.lineSeg(c, bx + 3 * u, rim, bx + 12.5f * u, counter - 21 * u, 1.9f * u, 0xFFD9A86C);
        gfx.ellipse(c, bx + 13.2f * u, counter - 22 * u, 2.6f * u, 1.7f * u, 0xFFD9A86C);
        c.save();
        c.clipRect(0, rim, w, counter);
        blob(c, bx, rim, 11 * u, 8.5f * u, 0xFFBBDAEE);
        c.restore();
        gfx.rrect(c, bx - 8 * u, counter - 5.5f * u, bx + 8 * u, counter - 3.8f * u, 0.8f * u, 0x66FFFFFF);
        blob(c, bx, rim, 11 * u, 2.7f * u, 0xFFD7ECF8);
        gfx.ellipse(c, bx, rim + 0.2f * u, 9.2f * u, 1.9f * u, 0xFFFFF6E4);
        // a jar of flour with a gingham lid
        float jx = 74 * u;
        box(c, jx - 6 * u, counter - 17 * u, jx + 6 * u, counter, 2.4f * u, 0xFFFFF8EA);
        box(c, jx - 3.6f * u, counter - 12 * u, jx + 3.6f * u, counter - 5.5f * u, 1 * u, 0xFFFBE6C2);
        box(c, jx - 6.8f * u, counter - 20.4f * u, jx + 6.8f * u, counter - 16.2f * u, 1.2f * u, 0xFFF4B9B2);
        gfx.rrect(c, jx - 4.5f * u, counter - 19.4f * u, jx - 1.5f * u, counter - 17.2f * u, 0.4f * u, 0x88FFFFFF);
        gfx.rrect(c, jx + 1.5f * u, counter - 19.4f * u, jx + 4.5f * u, counter - 17.2f * u, 0.4f * u, 0x88FFFFFF);
        // a pot of herbs
        float px = 88.5f * u;
        for (int i = -2; i <= 2; i++) {
            float lx = px + i * 2.6f * u, ly = counter - 14.5f * u - (2 - Math.abs(i)) * 1.8f * u;
            blob(c, lx, ly, 2.2f * u, 3.8f * u, 0xFFB9DDA6);
        }
        gfx.polygon(c, 0xFFEDB190, OUT, 0.5f * u, px - 5 * u, counter - 9.5f * u, px + 5 * u, counter - 9.5f * u, px + 3.6f * u, counter, px - 3.6f * u, counter);
        box(c, px - 5.6f * u, counter - 11 * u, px + 5.6f * u, counter - 8.6f * u, 0.8f * u, 0xFFF3C0A2);
    }

    private void counter(Canvas c, int w, int h, float top) {
        float slab = h * 0.047f, cab = top + slab;
        // cabinets
        gfx.rrect(c, -u, cab - u, w + u, h + u, 0, 0xFFC4DCC6);
        float doorW = (w - 2 * u) / 3f;
        for (int i = 0; i < 3; i++) {
            float l = u + i * doorW + 0.4f * u, r = u + (i + 1) * doorW - 0.4f * u;
            box(c, l, cab + 1.2f * u, r, h - 5 * u, 1.6f * u, 0xFFCFE5D1);
            box(c, l + 3 * u, cab + 4.2f * u, r - 3 * u, h - 8 * u, 1.2f * u, 0xFFD9ECDA);
            float kx = i == 1 ? l + 5.5f * u : (i == 0 ? r - 5.5f * u : l + 5.5f * u);
            blob(c, kx, cab + 8 * u, 1.5f * u, 1.5f * u, 0xFFEFD08A);
        }
        gfx.rrect(c, -u, h - 5 * u, w + u, h + u, 0, 0xFFB5CEB8);
        // shadow under the worktop
        paint.setShader(new LinearGradient(0, cab, 0, cab + 4 * u, 0x2A8A6A4A, 0x008A6A4A, Shader.TileMode.CLAMP));
        c.drawRect(0, cab, w, cab + 4 * u, paint);
        paint.setShader(null);
        // worktop: a lit top edge over a wooden front
        box(c, -2 * u, top, w + 2 * u, cab, 1.4f * u, 0xFFEBC692);
        gfx.rrect(c, 0, top + 0.5f * u, w, top + slab * 0.38f, 0, 0xFFF5DDB4);
        paint.setColor(0x22B58A56);
        for (float x = 6 * u; x < w; x += 21 * u) c.drawRect(x, top + slab * 0.5f, x + 11 * u, top + slab * 0.5f + 0.35f * u, paint);
        for (float x = 14 * u; x < w; x += 26 * u) c.drawRect(x, top + slab * 0.78f, x + 7 * u, top + slab * 0.78f + 0.3f * u, paint);
    }

    // ---------------------------------------------------------------- helpers

    private void box(Canvas c, float l, float t, float r, float b, float rad, int color) {
        gfx.rrect(c, l, t, r, b, rad, color);
        gfx.rrectLine(c, l, t, r, b, rad, 0.5f * u, OUT);
    }

    private void blob(Canvas c, float cx, float cy, float rx, float ry, int color) {
        gfx.ellipse(c, cx, cy, rx, ry, color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.5f * u);
        paint.setColor(OUT);
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry);
        c.drawOval(rect, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
