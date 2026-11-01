package io.github.gionnio.plateahome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Il pannello laterale a destra (rapido, Rete, Ingressi) e i suoi elementi: righe, riquadri, pallini di stato. */
final class Sheet {
    final LinearLayout box;
    private final Ui ui;
    private final Palette pal;

    Sheet(Context c, Ui ui, Palette pal, String title, String subtitle) {
        this.ui = ui;
        this.pal = pal;
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(ui.px(44), ui.px(48), ui.px(44), ui.px(44));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.surface);
        bg.setCornerRadius(ui.px(32));
        bg.setStroke(Math.max(1, ui.px(1)), pal.line);
        box.setBackground(bg);
        box.setElevation(ui.px(40));
        TextView t = ui.text(title, 40, pal.text, 600);
        t.setLetterSpacing(-0.02f);
        box.addView(t, wrap());
        TextView s = ui.text(subtitle, 20, pal.ter, 400);
        LinearLayout.LayoutParams sp = wrap();
        sp.topMargin = ui.px(8);
        sp.bottomMargin = ui.px(26);
        box.addView(s, sp);
    }

    FrameLayout.LayoutParams params() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ui.px(600), -1, Gravity.END);
        p.topMargin = ui.px(32);
        p.bottomMargin = ui.px(32);
        p.rightMargin = ui.px(32);
        return p;
    }

    static LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-2, -2); }

    TextView section(String label) {
        TextView v = ui.text(label, 16, pal.ter, 600);
        v.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams p = wrap();
        p.topMargin = ui.px(18);
        p.bottomMargin = ui.px(10);
        box.addView(v, p);
        return v;
    }

    /** Una riga selezionabile: titolo, sottotitolo, pallino di stato facoltativo (0 = nessuno). */
    Item row(String title, String sub, int dot, Runnable action) {
        Item it = new Item(box.getContext(), ui, pal, title, sub, dot, false);
        it.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.px(sub == null ? 66 : 88));
        p.bottomMargin = ui.px(8);
        box.addView(it, p);
        return it;
    }

    void spacer() {
        box.addView(new View(box.getContext()), new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    /** Elemento selezionabile del pannello: col focus diventa pieno (bianco), come su tvOS. */
    static final class Item extends LinearLayout {
        final TextView title, sub;
        private final Palette pal;
        private final Ui ui;
        private final GradientDrawable bg = new GradientDrawable();
        private final int dot;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean tileStyle;

        Item(Context c, Ui ui, Palette pal, String t, String s, int dot, boolean tileStyle) {
            super(c);
            this.ui = ui; this.pal = pal; this.dot = dot; this.tileStyle = tileStyle;
            setOrientation(VERTICAL);
            setGravity(tileStyle ? Gravity.BOTTOM : Gravity.CENTER_VERTICAL);
            setPadding(ui.px(22), 0, ui.px(dot != 0 ? 52 : 22), tileStyle ? ui.px(20) : 0);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setWillNotDraw(false);
            bg.setCornerRadius(ui.px(tileStyle ? 22 : 18));
            setBackground(bg);
            title = ui.text(t, 23, pal.text, 600);
            addView(title, wrap());
            if (s != null) {
                sub = ui.text(s, 18, pal.ter, 400);
                LinearLayout.LayoutParams p = wrap();
                p.topMargin = ui.px(4);
                addView(sub, p);
            } else sub = null;
            paint(false);
        }

        private void paint(boolean f) {
            bg.setColor(f ? pal.focus : tileStyle ? pal.tile : pal.tile);
            bg.setStroke(f ? ui.px(3) : 0, pal.focus);
            title.setTextColor(f ? pal.onFocus : pal.text);
            if (sub != null) sub.setTextColor(f ? pal.onFocus : pal.ter);
            if (sub != null) sub.setAlpha(f ? 0.75f : 1f);
        }

        @Override protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
            super.onFocusChanged(gained, direction, previous);
            paint(gained);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            if (dot == 0) return;
            paint.setColor(dot);
            c.drawCircle(getWidth() - ui.px(28), getHeight() / 2f, ui.px(5), paint);
        }
    }
}
