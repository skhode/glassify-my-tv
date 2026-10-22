package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/** A top bar pill's resting background as Liquid Glass (liquid_glass); the focused and activated states stay as they were. */
final class GlassPillDrawable extends Drawable {

    private final View host;
    private final LiquidGlass glass;
    private final float radius;
    private final RectF rect = new RectF();
    private final int[] onScreen = new int[2];

    GlassPillDrawable(View host, float density) {
        this.host = host;
        glass = new LiquidGlass(density, 0x33FFFFFF);
        radius = 21f * density;
    }

    void setFrost(Bitmap blurred, int screenWidth, int screenHeight) {
        glass.setFrost(blurred, screenWidth, screenHeight);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        host.getLocationOnScreen(onScreen);
        rect.set(getBounds());
        float r = Math.min(radius, rect.height() / 2f);
        glass.draw(canvas, rect, r, onScreen[0], onScreen[1]);
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
