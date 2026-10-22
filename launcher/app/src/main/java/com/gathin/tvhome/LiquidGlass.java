package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * tvOS 26 style Liquid Glass for one rounded surface (liquid_glass): the blurred wallpaper behind it (see {@link Frost}),
 * under one even milky veil (an inner lens band read as a second pill inside the first, so there is none); and a
 * rim lit from the top left. Shaders only, no clipPath, so it stays on the GPU of
 * MediaTek TV chips. One instance per surface: each keeps its own shader matrices.
 */
final class LiquidGlass {

    private final float density;
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint veil = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix bodyMatrix = new Matrix();
    private final RectF band = new RectF();
    private BitmapShader bodyShader;
    private int screenW, screenH;
    private final RectF lit = new RectF(Float.NaN, 0, 0, 0);
    /** Strength of the clear tint over the blur: a bright pill reads better a little lighter than the big dock. */
    private final int veilColor;

    LiquidGlass(float density, int veilColor) {
        this.density = density;
        this.veilColor = veilColor;
        veil.setColor(veilColor);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
    }

    /** The blurred wallpaper copy and the size of the screen it stands for; null draws only the tint and the rim. */
    void setFrost(Bitmap blurred, int screenWidth, int screenHeight) {
        screenW = screenWidth;
        screenH = screenHeight;
        bodyShader = blurred == null ? null : new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        body.setShader(bodyShader);
        veil.setColor(blurred == null ? 0x59141418 : veilColor);
    }

    boolean hasFrost() {
        return bodyShader != null;
    }

    /**
     * Draws the glass in rect, in the canvas's coordinates. originX/Y = where those coordinates start on screen, so the
     * blur lines up with the wallpaper really behind the surface.
     */
    void draw(Canvas c, RectF rect, float radius, float originX, float originY) {
        float sx = (float) screenW / Frost.WIDTH, sy = (float) screenH / Frost.HEIGHT;
        if (bodyShader != null) {
            bodyMatrix.setScale(sx, sy);
            bodyMatrix.postTranslate(-originX, -originY);
            bodyShader.setLocalMatrix(bodyMatrix);
            c.drawRoundRect(rect, radius, radius, body);
        }
        c.drawRoundRect(rect, radius, radius, veil);

        if (rect.left != lit.left || rect.top != lit.top || rect.right != lit.right || rect.bottom != lit.bottom) {
            lit.set(rect);
            // Light from the top left: bright on the top and left edges, faint again at the bottom right, as the
            // specular edge of the tvOS material, kept faint so the surface stays one tone.
            rim.setShader(new LinearGradient(rect.left, rect.top, rect.left + rect.height() * 0.6f + rect.width() * 0.15f,
                    rect.bottom, new int[]{0x8CFFFFFF, 0x2EFFFFFF, 0x1AFFFFFF, 0x33FFFFFF},
                    new float[]{0f, 0.35f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        }
        float half = rim.getStrokeWidth() / 2f;
        band.set(rect);
        band.inset(half, half);
        c.drawRoundRect(band, radius - half, radius - half, rim);
    }
}
