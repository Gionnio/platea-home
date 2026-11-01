package io.github.gionnio.plateahome;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Il menu di OK tenuto premuto su un'app: un pannello accanto alla tessera, voce selezionata in bianco. */
final class TileMenu {
    static final class Item {
        final String label;
        final Runnable action;
        Item(String label, Runnable action) { this.label = label; this.action = action; }
    }

    private TileMenu() {}

    static PopupWindow show(Activity a, Ui ui, Palette pal, View anchor, Item... items) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = ui.px(10);
        box.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.surface);
        bg.setCornerRadius(ui.px(20));
        bg.setStroke(Math.max(1, ui.px(1)), pal.line);
        box.setBackground(bg);
        box.setElevation(ui.px(30));

        final PopupWindow pw = new PopupWindow(box, ui.px(340), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(ui.px(30));
        final boolean[] armed = {false};

        for (Item it : items) {
            final TextView t = ui.text(it.label, 21, pal.text, 500);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(ui.px(20), 0, ui.px(20), 0);
            t.setFocusable(true);
            t.setFocusableInTouchMode(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(58));
            lp.bottomMargin = ui.px(4);
            final GradientDrawable on = new GradientDrawable();
            on.setCornerRadius(ui.px(14));
            on.setColor(0);
            t.setBackground(on);
            t.setOnFocusChangeListener((v, f) -> {
                on.setColor(f ? pal.focus : 0);
                t.setTextColor(f ? pal.onFocus : pal.text);
                t.setTypeface(ui.face(f ? 600 : 500));
            });
            final Runnable run = it.action;
            t.setOnClickListener(v -> { pw.dismiss(); run.run(); });
            // Il rilascio di OK che ha aperto il menu arriva qui: conta solo una pressione fatta nel menu.
            t.setOnKeyListener((v, code, e) -> {
                boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
                if (!ok) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) armed[0] = true;
                else if (e.getAction() == KeyEvent.ACTION_UP && armed[0]) { pw.dismiss(); run.run(); }
                return true;
            });
            box.addView(t, lp);
        }

        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int screenW = a.getResources().getDisplayMetrics().widthPixels;
        int x = loc[0] + anchor.getWidth() + ui.px(28);
        if (x + ui.px(340) > screenW - ui.px(40)) x = loc[0] - ui.px(340) - ui.px(28);
        int y = Math.max(ui.px(40), loc[1] - ui.px(12));
        pw.showAtLocation(anchor, Gravity.TOP | Gravity.START, x, y);
        if (box.getChildCount() > 0) box.getChildAt(0).requestFocus();
        return pw;
    }
}
