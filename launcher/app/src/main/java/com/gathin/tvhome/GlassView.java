package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * A translucent glass slab: soft sheen fill, bright top specular, crisp rim and a faint outer glow.
 * One instance floats over the grid and is moved with spring animations between tiles.
 */
public class GlassView extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float radius;
    /** Glow colour as 0xRRGGBB: white normally, a festival's accent (diya gold) while a festive skin is on. */
    private int glowRgb = 0xFFFFFF;
    /** Glow strength: the white glow stays faint, a coloured one needs more alpha to read as warm light. */
    private int glowAlpha = 0x30;
    /** focus_outline: a crisp rim and a soft shadow under the tile, no slab or sheen over the artwork. */
    private boolean outline;
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** liquid_glass: lift shadow, a rim lit from the top left and a light sweep across the tile on each move. */
    private boolean liquid;
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Matrix shineMatrix = new android.graphics.Matrix();
    private android.graphics.LinearGradient shineShader;
    /** Sweep position: 0 = before the tile's left edge, 1 = past its right edge; outside 0..1 nothing is drawn. */
    private float shinePos = 2f;
    private android.animation.ValueAnimator shineAnim;

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        rim.setColor(0xCCFFFFFF);
        glow.setStyle(Paint.Style.STROKE);
        // Cache the slab as a texture. The spring only changes translation, so the GPU can
        // move it without replaying clipPath and the glow on every frame. That path is
        // software-rendered on the MT5895 and is what makes the highlight crawl.
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    /** Tints the rim and the outer glow; 0 restores the plain white glass. */
    void setAccent(int argb) {
        if (argb == 0) {
            glowRgb = 0xFFFFFF;
            glowAlpha = 0x30;
            rim.setColor(outline ? 0xF2FFFFFF : 0xCCFFFFFF);
        } else {
            glowRgb = argb & 0xFFFFFF;
            glowAlpha = 0x70;
            // The rim is the accent lifted towards white, so the edge still reads as glass, not paint.
            int r = (((glowRgb >> 16) & 0xFF) + 255 * 2) / 3, g = (((glowRgb >> 8) & 0xFF) + 255 * 2) / 3,
                    b = ((glowRgb & 0xFF) + 255 * 2) / 3;
            rim.setColor(0xE6000000 | (r << 16) | (g << 8) | b);
        }
        invalidate();
    }

    void setOutline(boolean on) {
        outline = on;
        rim.setStrokeWidth((on ? 3f : 1.5f) * density);
        if (on && glowRgb == 0xFFFFFF) rim.setColor(0xF2FFFFFF);
        invalidate();
    }

    void setLiquid(boolean on) {
        liquid = on;
        if (on) outline = false;
        rim.setStrokeWidth(1.5f * density);
        liquidRim(getWidth(), getHeight());
        invalidate();
    }

    /** The lit-from-the-top-left rim in Liquid Glass mode; the plain rim colour otherwise. */
    private void liquidRim(int w, int h) {
        if (!liquid || w == 0) {
            rim.setShader(null);
            return;
        }
        float inset = 6f * density;
        rim.setShader(new LinearGradient(inset, inset, w * 0.55f, h - inset,
                new int[]{0xE6FFFFFF, 0x40FFFFFF, 0x1AFFFFFF, 0x59FFFFFF}, new float[]{0f, 0.35f, 0.7f, 1f},
                Shader.TileMode.CLAMP));
    }

    /**
     * One sweep of light across the focused tile, left to right, as tvOS does when focus lands. Each frame repaints the
     * cached layer; between sweeps the spring moves the texture as before.
     */
    void shine() {
        if (!liquid) return;
        if (shineAnim != null) shineAnim.cancel();
        shineAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        shineAnim.setDuration(650);
        shineAnim.setStartDelay(90);
        shineAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f));
        shineAnim.addUpdateListener(a -> {
            shinePos = (float) a.getAnimatedValue();
            invalidate();
        });
        shineAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                shinePos = 2f;
                invalidate();
            }
        });
        shineAnim.start();
    }

    void setRadius(float px) {
        radius = px;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x3DFFFFFF, 0x12FFFFFF, Shader.TileMode.CLAMP));
        // The sweep: a soft diagonal band of light about a third of the tile wide, moved by its local matrix.
        shineShader = new LinearGradient(0, 0, w * 0.35f, h * 0.25f,
                new int[]{0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        shine.setShader(shineShader);
        liquidRim(w, h);
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.45f, 0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = 6f * density;   // room for the glow
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);

        if (liquid) {
            // Lift: a soft shadow under the zoomed tile, deeper below than above, as if raised off the dock.
            int rings = 12;
            float drop = 3f * density;
            float step = (inset - drop) / rings;
            shadow.setStyle(Paint.Style.STROKE);
            shadow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < rings; i++) {
                float o = step * (i + 0.5f);
                float t = 1f - (float) i / rings;
                shadow.setColor(Math.round(0x48 * t * t * t) << 24);
                canvas.drawRoundRect(rect.left - o, rect.top - o + drop, rect.right + o, rect.bottom + o + drop,
                        radius + o, radius + o, shadow);
            }
            if (shinePos >= 0f && shinePos <= 1f && shineShader != null) {
                float w = rect.width();
                shineMatrix.setTranslate(rect.left - w * 0.45f + shinePos * w * 1.5f, rect.top);
                shineShader.setLocalMatrix(shineMatrix);
                canvas.drawRoundRect(rect, radius, radius, shine);
            }
            float half = rim.getStrokeWidth() / 2f;
            canvas.drawRoundRect(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half,
                    radius - half, radius - half, rim);
            return;
        }
        if (outline) {
            // The lifted tile's shadow: rings below the slab, darkest at its edge, offset down so the tile reads as
            // raised towards you (tvOS). The rim sits just outside the artwork so the logo stays untouched.
            int rings = 12;
            float drop = 2f * density;
            float step = (inset - drop) / rings;   // the lowest ring still fits inside the view
            shadow.setStyle(Paint.Style.STROKE);
            shadow.setStrokeWidth(step + 0.5f);
            for (int i = 0; i < rings; i++) {
                float o = step * (i + 0.5f);
                float t = 1f - (float) i / rings;
                shadow.setColor(Math.round(0x66 * t * t) << 24);
                canvas.drawRoundRect(rect.left - o, rect.top - o + drop, rect.right + o, rect.bottom + o + drop,
                        radius + o, radius + o, shadow);
            }
            float half = rim.getStrokeWidth() / 2f;
            canvas.drawRoundRect(rect.left - half, rect.top - half, rect.right + half, rect.bottom + half,
                    radius + half, radius + half, rim);
            return;
        }

        // Outer glow: thin rings across the inset, each a little fainter, so it fades out smoothly. Three wide
        // strokes left visible bands once the glow took an app's colour at a stronger alpha. The hardware layer
        // caches the result, so the rings cost one draw, not one per frame of the spring.
        int rings = 12;
        float step = inset / rings;
        glow.setStrokeWidth(step + 0.5f);
        for (int i = 0; i < rings; i++) {
            float o = step * (i + 0.5f);
            float t = 1f - (float) i / rings;
            glow.setColor(Math.round(glowAlpha * t) << 24 | glowRgb);
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow);
        }

        canvas.drawRoundRect(rect, radius, radius, fill);

        // Sheen across the top 45%: clipRect stays on the GPU (clipPath did not on the MT5895) and
        // the round rect under it keeps the top corners rounded with a flat bottom edge.
        canvas.save();
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f);
        canvas.drawRoundRect(rect, radius, radius, specular);
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
