package io.github.gionnio.plateahome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Una tessera 16:9: il banner dell'app (o l'icona su fondo neutro), angoli arrotondati, focus bianco che la solleva. */
final class TileView extends View {
    interface Listener { void onTileFocus(TileView tile, boolean focused); }

    Apps.App app;
    /** Tessera speciale "Tutte le app" (app == null). */
    boolean allApps;
    /** In modalità Sposta: sollevata, inclinata, con le frecce ai lati. */
    boolean moving;

    private final Ui ui;
    private final Palette pal;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private final Rect bounds = new Rect();
    private final float radius;
    private Listener listener;

    TileView(Context c, Ui ui, Palette pal) {
        super(c);
        this.ui = ui;
        this.pal = pal;
        radius = ui.px(18);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
    }

    void setListener(Listener l) { listener = l; }

    void bind(Apps.App app, boolean allApps) {
        this.app = app;
        this.allApps = allApps;
        setContentDescription(allApps ? "Tutte le app" : app == null ? "" : app.label);
        invalidate();
    }

    String label() { return allApps ? "Tutte le app" : app == null ? "" : app.label; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        clip.reset();
        box.set(0, 0, w, h);
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.save();
        c.clipPath(clip);
        Drawable banner = app == null ? null : app.banner();
        if (banner != null) {
            bounds.set(0, 0, w, h);
            banner.setBounds(bounds);
            banner.draw(c);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pal.tile);
            c.drawRect(0, 0, w, h, paint);
            if (allApps) drawGrid(c, w, h);
            else if (app != null && app.icon() != null) {
                int size = Math.round(h * 0.42f);
                int top = Math.round(h * 0.16f);
                app.icon().setBounds((w - size) / 2, top, (w + size) / 2, top + size);
                app.icon().draw(c);
                drawLabel(c, app.label, w, Math.round(h * 0.84f));
            }
            if (allApps) drawLabel(c, "Tutte le app", w, Math.round(h * 0.80f));
        }
        c.restore();

        paint.setStyle(Paint.Style.STROKE);
        boolean focus = isFocused() || moving;
        float sw = focus ? ui.px(3) : Math.max(1, ui.px(1));
        paint.setStrokeWidth(sw);
        paint.setColor(focus ? pal.focus : pal.line);
        box.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2);
        c.drawRoundRect(box, radius, radius, paint);

        if (moving) drawArrows(c, w, h);
    }

    private void drawLabel(Canvas c, String text, int w, int baseline) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pal.sec);
        paint.setTypeface(ui.face(500));
        paint.setTextSize(ui.px(22));
        paint.setTextAlign(Paint.Align.CENTER);
        String t = text;
        while (t.length() > 3 && paint.measureText(t) > w - ui.px(24)) t = t.substring(0, t.length() - 2);
        if (!t.equals(text)) t = t + "…";
        c.drawText(t, w / 2f, baseline, paint);
    }

    private void drawGrid(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ui.px(2.4f));
        paint.setColor(pal.sec);
        float cell = ui.px(16), gap = ui.px(8), x0 = (w - cell * 2 - gap) / 2f, y0 = h * 0.22f;
        for (int r = 0; r < 2; r++)
            for (int k = 0; k < 2; k++) {
                float x = x0 + k * (cell + gap), y = y0 + r * (cell + gap);
                c.drawRoundRect(x, y, x + cell, y + cell, ui.px(3), ui.px(3), paint);
            }
    }

    private void drawArrows(Canvas c, int w, int h) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ui.px(4));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(pal.text);
        float cy = h / 2f, a = ui.px(10), off = ui.px(26);
        Path p = new Path();
        p.moveTo(-off + a / 2, cy - a); p.lineTo(-off - a / 2, cy); p.lineTo(-off + a / 2, cy + a);
        p.moveTo(w + off - a / 2, cy - a); p.lineTo(w + off + a / 2, cy); p.lineTo(w + off - a / 2, cy + a);
        c.drawPath(p, paint);
    }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        if (!moving) lift(gained ? 1.08f : 1f, gained ? -ui.px(10) : 0, 0);
        invalidate();
        if (listener != null) listener.onTileFocus(this, gained);
    }

    void lift(float scale, float dy, float rotation) {
        animate().scaleX(scale).scaleY(scale).translationY(dy).rotation(rotation).setDuration(140).start();
        setElevation(scale > 1f ? ui.px(24) : 0);
    }
}
