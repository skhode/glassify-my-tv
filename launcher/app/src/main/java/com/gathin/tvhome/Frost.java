package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/**
 * A small, blurred and slightly saturated copy of the wallpaper for the frosted dock (dock_frost). Android 9 has
 * no backdrop blur, so the band paints this copy, stretched back to the screen, instead of blurring live: 160×90
 * pixels is about 56 KB and a few milliseconds of work per wallpaper change.
 */
final class Frost {
    static final int WIDTH = 160;
    static final int HEIGHT = 90;

    private Frost() {
    }

    /** The frosted copy of the source, or null if it could not be made. */
    static Bitmap of(Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        try {
            Bitmap small = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(small);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            ColorMatrix saturate = new ColorMatrix();
            saturate.setSaturation(1.4f);   // blur greys colours out; tvOS's material keeps them vivid
            paint.setColorFilter(new ColorMatrixColorFilter(saturate));
            // Centre-crop like the wallpaper's ImageView, so the band shows what is really behind it.
            float scale = Math.max((float) WIDTH / source.getWidth(), (float) HEIGHT / source.getHeight());
            canvas.translate((WIDTH - source.getWidth() * scale) / 2f, (HEIGHT - source.getHeight() * scale) / 2f);
            canvas.scale(scale, scale);
            canvas.drawBitmap(source, 0, 0, paint);
            int[] px = new int[WIDTH * HEIGHT];
            small.getPixels(px, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
            // Three box passes approximate a Gaussian; radius 3 at 1/12 scale is about a 36 px blur at 1080p.
            for (int pass = 0; pass < 3; pass++) {
                boxBlur(px, WIDTH, HEIGHT, 3, true);
                boxBlur(px, WIDTH, HEIGHT, 3, false);
            }
            small.setPixels(px, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
            return small;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** One box pass along rows (horizontal) or columns, clamping at the edges. */
    private static void boxBlur(int[] px, int w, int h, int radius, boolean horizontal) {
        int lines = horizontal ? h : w, length = horizontal ? w : h;
        int[] line = new int[length];
        int window = radius * 2 + 1;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < length; i++) line[i] = px[horizontal ? l * w + i : i * w + l];
            int r = 0, g = 0, b = 0;
            for (int i = -radius; i <= radius; i++) {
                int c = line[Math.max(0, Math.min(length - 1, i))];
                r += (c >> 16) & 0xFF;
                g += (c >> 8) & 0xFF;
                b += c & 0xFF;
            }
            for (int i = 0; i < length; i++) {
                px[horizontal ? l * w + i : i * w + l] = 0xFF000000 | (r / window) << 16 | (g / window) << 8 | (b / window);
                int out = line[Math.max(0, i - radius)], in = line[Math.min(length - 1, i + radius + 1)];
                r += ((in >> 16) & 0xFF) - ((out >> 16) & 0xFF);
                g += ((in >> 8) & 0xFF) - ((out >> 8) & 0xFF);
                b += (in & 0xFF) - (out & 0xFF);
            }
        }
    }
}
