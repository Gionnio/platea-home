package io.github.gionnio.plateahome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Tutte le app, 6 per riga come il dock. Puntino sotto quelle del dock; le nascoste sono attenuate. */
public final class AppsActivity extends Activity {
    private static final int COLS = 6;
    private Ui ui;
    private Palette pal;
    private GridLayout grid;
    private TextView count;
    private final List<TileView> tiles = new ArrayList<>();
    private boolean longFired;
    private PopupWindow menu;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        pal = Store.pal(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.ground);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        // La tessera col focus si ingrandisce e sconfina nel margine: senza questo, a destra e a sinistra viene tagliata.
        col.setClipToPadding(false);
        scroll.setClipToPadding(false);
        col.setPadding(ui.px(112), ui.px(50), ui.px(112), ui.px(96));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        // Stessa intestazione della Home: "App" come l'ora, il conteggio come la data (sulla sua linea di base),
        // e il suggerimento a destra centrato sulla stessa riga, come i pulsanti.
        head.setBaselineAligned(true);
        TextView title = ui.text(getString(R.string.apps_title), 38, pal.text, 300);
        count = ui.text("", 20, pal.ter, 500);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
        cp.leftMargin = ui.px(18);
        head.addView(title);
        head.addView(count, cp);
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBaselineAligned(false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(head, new LinearLayout.LayoutParams(-2, -2));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(ui.text(getString(R.string.back_to_home), 20, pal.ter, 500), new LinearLayout.LayoutParams(-2, -2));
        col.addView(bar, new LinearLayout.LayoutParams(-1, ui.px(60)));

        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setUseDefaultMargins(false);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.topMargin = ui.px(70);
        col.addView(grid, gp);

        scroll.addView(col);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        fill();
    }

    @Override protected void onPause() {
        super.onPause();
        if (menu != null) menu.dismiss();
    }

    private void fill() {
        View f = getCurrentFocus();
        int focus = Math.max(0, tiles.indexOf(f));
        grid.removeAllViews();
        tiles.clear();
        List<Apps.App> apps = Apps.load(this);
        List<String> dock = Store.dock(this);
        Set<String> hidden = Store.hidden(this);
        int inDock = 0;
        for (String p : dock) if (Apps.find(apps, p) != null) inDock++;
        count.setText(getString(R.string.apps_count, apps.size(), inDock));

        int w = ui.px(256), h = ui.px(144);
        for (int i = 0; i < apps.size(); i++) {
            Apps.App a = apps.get(i);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setClipChildren(false);
            TileView t = new TileView(this, ui, pal);
            t.bind(a, false);
            boolean isHidden = hidden.contains(a.pkg) && !dock.contains(a.pkg);
            t.setAlpha(isHidden ? 0.45f : 1f);
            cell.addView(t, new LinearLayout.LayoutParams(w, h));

            LinearLayout cap = new LinearLayout(this);
            cap.setOrientation(LinearLayout.HORIZONTAL);
            cap.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(dock.contains(a.pkg) ? pal.text : 0);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ui.px(7), ui.px(7));
            dl.rightMargin = ui.px(8);
            cap.addView(dot, dl);
            final TextView label = ui.text(a.label, 19, pal.ter, 400);
            cap.addView(label, new LinearLayout.LayoutParams(w - ui.px(16), -2));
            LinearLayout.LayoutParams capp = new LinearLayout.LayoutParams(-2, -2);
            capp.topMargin = ui.px(12);
            cell.addView(cap, capp);

            t.setListener((tile, gained) -> {
                label.setTextColor(gained ? pal.text : pal.ter);
                label.setTypeface(ui.face(gained ? 600 : 400));
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = w;
            lp.rightMargin = (i % COLS == COLS - 1) ? 0 : ui.px(32);
            lp.bottomMargin = ui.px(40);
            grid.addView(cell, lp);
            tiles.add(t);
        }
        if (!tiles.isEmpty()) tiles.get(Math.min(focus, tiles.size() - 1)).requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        View f = getCurrentFocus();
        // A capo: Destra sull'ultima colonna va alla prima della riga sotto, Sinistra sulla prima all'ultima di sopra.
        if (f instanceof TileView && e.getAction() == KeyEvent.ACTION_DOWN
                && (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT)) {
            int i = tiles.indexOf(f);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i % COLS == COLS - 1 && i + 1 < tiles.size()) { tiles.get(i + 1).requestFocus(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && i % COLS == 0 && i > 0) { tiles.get(i - 1).requestFocus(); return true; }
        }
        if (f instanceof TileView && (ok || code == KeyEvent.KEYCODE_MENU)) {
            TileView t = (TileView) f;
            if (code == KeyEvent.KEYCODE_MENU) {
                if (e.getAction() == KeyEvent.ACTION_UP) showMenu(t);
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) longFired = false;
                else if (!longFired && (e.isLongPress() || e.getEventTime() - e.getDownTime() >= 550)) {
                    longFired = true;
                    showMenu(t);
                }
                return true;
            }
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (!longFired && t.app != null) Apps.open(this, t.app.pkg);
                longFired = false;
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** Alla Home, con l'app in modalità Sposta (o Sostituisci, se il dock è pieno) per scegliere dove metterla. */
    private void place(String pkg) {
        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_PLACE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
    }

    private void showMenu(TileView t) {
        if (t.app == null) return;
        final String pkg = t.app.pkg;
        final List<String> dock = Store.dock(this);
        final Set<String> hidden = Store.hidden(this);
        List<TileMenu.Item> items = new ArrayList<>();
        if (dock.contains(pkg)) items.add(new TileMenu.Item(getString(R.string.remove_from_dock), () -> { dock.remove(pkg); Store.setDock(this, dock); fill(); }));
        else items.add(new TileMenu.Item(getString(dock.size() < 6 ? R.string.add_to_dock : R.string.replace_in_dock), () -> place(pkg)));
        if (hidden.contains(pkg)) items.add(new TileMenu.Item(getString(R.string.show_on_home), () -> { hidden.remove(pkg); Store.setHidden(this, hidden); fill(); }));
        else items.add(new TileMenu.Item(getString(R.string.hide_from_home), () -> { hidden.add(pkg); Store.setHidden(this, hidden); fill(); }));
        items.add(new TileMenu.Item(getString(R.string.app_info), () -> Apps.info(this, pkg)));
        menu = TileMenu.show(this, ui, pal, t, items.toArray(new TileMenu.Item[0]));
    }
}
