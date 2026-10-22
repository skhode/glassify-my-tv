package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** dock_frost: the blurred wallpaper (see {@link Frost}) painted in the band, then a light tint and sheen. */
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix frostMatrix = new Matrix();
    private final int[] gridOnScreen = new int[2];
    private BitmapShader frostShader;
    private int screenW, screenH;
    private float sheenTop = Float.NaN, sheenBottom;
    /** liquid_glass: the band drawn as Liquid Glass instead of the plain frost. */
    private LiquidGlass liquid;

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;
    private final int plainEdge;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names) {
        this.dockSize = dockSize;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        band.setColor(translucent ? 0x8C121014 : 0xFF141416);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        plainEdge = translucent ? 0x26FFFFFF : 0x1FFFFFFF;
        edge.setColor(plainEdge);
    }

    /**
     * Frosted band from now on, over this blurred wallpaper copy; null goes back to the plain band. The screen size
     * is what the copy is stretched to, since the wallpaper fills the screen.
     */
    void setFrost(Bitmap blurred, int screenWidth, int screenHeight) {
        screenW = screenWidth;
        screenH = screenHeight;
        frostShader = blurred == null ? null : new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        frost.setShader(frostShader);
        tint.setColor(0x38101014);   // a smoky tint: white text and logos stay crisp over a bright sky
        edge.setColor(frostShader != null ? 0x47FFFFFF : plainEdge);
    }

    /** Liquid Glass band from now on, over this blurred wallpaper copy (null keeps only its tint and rim). */
    void setLiquid(Bitmap blurred, int screenWidth, int screenHeight) {
        if (liquid == null) liquid = new LiquidGlass(density, 0x0FFFFFFF);
        liquid.setFrost(blurred, screenWidth, screenHeight);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        // Liquid Glass grows the focused tile 16%: the band reaches further out at the sides so the first and last
        // tiles stay inside it when they grow (the grid gives it the room, see MainActivity).
        float sidePad = liquid != null ? 26 * density : pad;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        float side = Math.max(0f, Math.min(sidePad, Math.min(rect.left, parent.getWidth() - rect.right) - density));
        rect.inset(-side, -pad);
        rect.bottom += namesRoom;
        float radius = 26 * density;
        if (liquid != null) {
            parent.getLocationOnScreen(gridOnScreen);
            liquid.draw(c, rect, radius, gridOnScreen[0], gridOnScreen[1]);
            return;
        }
        if (frostShader != null) {
            // The band paints the blurred wallpaper exactly where the real one is behind it: stretch the copy to
            // the screen, then shift by the grid's place on screen, since the decoration draws in grid coordinates.
            parent.getLocationOnScreen(gridOnScreen);
            frostMatrix.setScale((float) screenW / Frost.WIDTH, (float) screenH / Frost.HEIGHT);
            frostMatrix.postTranslate(-gridOnScreen[0], -gridOnScreen[1]);
            frostShader.setLocalMatrix(frostMatrix);
            c.drawRoundRect(rect, radius, radius, frost);
            c.drawRoundRect(rect, radius, radius, tint);
            if (rect.top != sheenTop || rect.bottom != sheenBottom) {
                sheenTop = rect.top;
                sheenBottom = rect.bottom;
                // Light catching the top of the material, fading out by the middle of the band.
                sheen.setShader(new LinearGradient(0, rect.top, 0, rect.centerY(), 0x1FFFFFFF, 0x00FFFFFF,
                        Shader.TileMode.CLAMP));
            }
            c.drawRoundRect(rect, radius, radius, sheen);
        } else {
            c.drawRoundRect(rect, radius, radius, band);
        }
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
