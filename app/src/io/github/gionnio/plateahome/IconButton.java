package io.github.gionnio.plateahome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

/** Pulsante tondo solo icona dell'intestazione (Ingressi, Rete, Impostazioni). Col focus diventa pieno. */
final class IconButton extends View {
    static final int INPUTS = 0, NETWORK = 1, SETTINGS = 2;

    private final int kind;
    private final Ui ui;
    private final Palette pal;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    IconButton(Context c, Ui ui, Palette pal, int kind, String description) {
        super(c);
        this.ui = ui; this.pal = pal; this.kind = kind;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setContentDescription(description);
    }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        animate().scaleX(gained ? 1.1f : 1f).scaleY(gained ? 1.1f : 1f).setDuration(120).start();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, rad = Math.min(w, h) / 2f;
        boolean f = isFocused();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(f ? pal.focus : pal.surface);
        c.drawCircle(cx, cy, rad, paint);
        if (!f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, ui.px(1)));
            paint.setColor(pal.line);
            c.drawCircle(cx, cy, rad - paint.getStrokeWidth() / 2, paint);
        }
        // Icona su griglia 24×24, centrata.
        float u = ui.px(24) / 24f, ox = cx - 12 * u, oy = cy - 12 * u;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(f ? pal.onFocus : pal.sec);
        if (kind == INPUTS) {
            r.set(ox + 3 * u, oy + 5 * u, ox + 21 * u, oy + 17 * u);
            c.drawRoundRect(r, 2 * u, 2 * u, paint);
            c.drawLine(ox + 8 * u, oy + 21 * u, ox + 16 * u, oy + 21 * u, paint);
            c.drawLine(ox + 12 * u, oy + 17 * u, ox + 12 * u, oy + 21 * u, paint);
        } else if (kind == NETWORK) {
            float bx = ox + 12 * u, by = oy + 20 * u;
            for (int i = 1; i <= 3; i++) {
                float rr = (i * 5.2f) * u;
                r.set(bx - rr, by - rr, bx + rr, by + rr);
                c.drawArc(r, 225, 90, false, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            c.drawCircle(bx, by - 0.5f * u, 1.2f * u, paint);
        } else {
            // Ingranaggio: anello, foro centrale e 8 denti larghi.
            c.drawCircle(ox + 12 * u, oy + 12 * u, 2.6f * u, paint);
            c.drawCircle(ox + 12 * u, oy + 12 * u, 6.6f * u, paint);
            paint.setStrokeWidth(3.2f * u);
            paint.setStrokeCap(Paint.Cap.BUTT);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float x1 = (float) (12 + 7.2 * Math.cos(a)), y1 = (float) (12 + 7.2 * Math.sin(a));
                float x2 = (float) (12 + 9.6 * Math.cos(a)), y2 = (float) (12 + 9.6 * Math.sin(a));
                c.drawLine(ox + x1 * u, oy + y1 * u, ox + x2 * u, oy + y2 * u, paint);
            }
        }
    }
}
