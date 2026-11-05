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
import java.util.Map;
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
    private FrameLayout root;
    private View page;
    private FrameLayout folderPanel;
    private String openKey;
    private final List<TileView> folderTiles = new ArrayList<>();
    private View beforeFolder;
    private List<Apps.App> apps = new ArrayList<>();
    private Map<String, List<String>> folders;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        pal = Store.pal(this);

        root = new FrameLayout(this);
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
        page = scroll;
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        if (folderPanel == null) fill();
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
        apps = Apps.load(this);
        folders = Folders.load(this);
        List<String> dock = Store.dock(this);
        Set<String> hidden = Store.hidden(this);
        int inDock = 0;
        for (String p : dock) if (Apps.find(apps, p) != null) inDock++;
        int nFolders = 0;
        int w = ui.px(256), h = ui.px(144);
        // Prima le cartelle, nell'ordine della lista dei nomi; poi le app che non stanno in una cartella.
        for (Map.Entry<String, List<String>> fo : folders.entrySet()) {
            List<Apps.App> in = Folders.apps(apps, fo.getValue());
            if (in.isEmpty()) continue;
            TileView t = new TileView(this, ui, pal);
            t.bindFolder(fo.getKey(), in);
            addCell(t, Folders.label(this, fo.getKey()), false, w, h);
            nFolders++;
        }
        count.setText(nFolders == 0 ? getString(R.string.apps_count, apps.size(), inDock)
                : nFolders == 1 ? getString(R.string.apps_count_folder, apps.size(), inDock)
                : getString(R.string.apps_count_folders, apps.size(), inDock, nFolders));
        for (int i = 0; i < apps.size(); i++) {
            Apps.App a = apps.get(i);
            if (Folders.of(folders, a.pkg) != null) continue;
            TileView t = new TileView(this, ui, pal);
            t.bind(a, false);
            boolean isHidden = hidden.contains(a.pkg) && !dock.contains(a.pkg);
            t.setAlpha(isHidden ? 0.45f : 1f);
            addCell(t, a.label, dock.contains(a.pkg), w, h);
        }
        if (!tiles.isEmpty()) tiles.get(Math.min(focus, tiles.size() - 1)).requestFocus();
    }

    /** Una cella della griglia: la tessera e sotto il nome, con il puntino se l'app è nel dock. */
    private void addCell(TileView t, String name, boolean inDock, int w, int h) {
        int i = tiles.size();
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.addView(t, new LinearLayout.LayoutParams(w, h));
        LinearLayout cap = new LinearLayout(this);
        cap.setOrientation(LinearLayout.HORIZONTAL);
        cap.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(inDock ? pal.text : 0);
        dot.setBackground(d);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ui.px(7), ui.px(7));
        dl.rightMargin = ui.px(8);
        cap.addView(dot, dl);
        final TextView label = ui.text(name, 19, pal.ter, 400);
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

    // ---- cartelle ----------------------------------------------------------------------------

    private void openFolder(String key) {
        List<String> pk = folders.get(key);
        List<Apps.App> in = pk == null ? new ArrayList<>() : Folders.apps(apps, pk);
        if (in.isEmpty()) return;
        if (folderPanel != null) root.removeView(folderPanel);
        else beforeFolder = getCurrentFocus();
        folderTiles.clear();
        openKey = key;
        folderPanel = Folders.panel(this, ui, pal, key, in, folderTiles);
        root.addView(folderPanel, new FrameLayout.LayoutParams(-1, -1));
        page.setAlpha(0.15f);
        ((android.view.ViewGroup) page).setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void closeFolder() {
        if (folderPanel == null) return;
        int at = tiles.indexOf(beforeFolder);
        Folders.close(root, folderPanel, null, page);
        folderPanel = null;
        openKey = null;
        folderTiles.clear();
        fill();
        if (!tiles.isEmpty()) tiles.get(Math.max(0, Math.min(at, tiles.size() - 1))).requestFocus();
        beforeFolder = null;
    }

    private void refreshFolder() {
        String key = openKey;
        folders = Folders.load(this);
        if (key != null && folders.containsKey(key) && !Folders.apps(apps, folders.get(key)).isEmpty()) {
            View back = beforeFolder;
            openFolder(key);
            beforeFolder = back;
        } else closeFolder();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        View f = getCurrentFocus();
        if (folderPanel != null && code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) closeFolder();
            return true;
        }
        // A capo: Destra sull'ultima colonna va alla prima della riga sotto, Sinistra sulla prima all'ultima di sopra.
        if (f instanceof TileView && e.getAction() == KeyEvent.ACTION_DOWN
                && (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_LEFT)) {
            int i = tiles.indexOf(f);
            List<TileView> list = i >= 0 ? tiles : folderTiles;
            if (i < 0) i = folderTiles.indexOf(f);
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT && i % COLS == COLS - 1 && i + 1 < list.size()) { list.get(i + 1).requestFocus(); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_LEFT && i % COLS == 0 && i > 0) { list.get(i - 1).requestFocus(); return true; }
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
                if (!longFired && t.folder != null) openFolder(t.folder);
                else if (!longFired && t.app != null) Apps.open(this, t.app.pkg);
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
        if (t.folder != null) {
            final String key = t.folder;
            menu = TileMenu.show(this, ui, pal, t,
                    new TileMenu.Item(getString(R.string.folder_rename), () ->
                            menu = TileMenu.show(this, ui, pal, t, Folders.renamer(this, key, this::fill))),
                    new TileMenu.Item(getString(R.string.folder_delete), () -> { Folders.delete(this, key); fill(); }));
            return;
        }
        if (t.app == null) return;
        final String pkg = t.app.pkg;
        if (folderTiles.contains(t)) {
            menu = TileMenu.show(this, ui, pal, t,
                    new TileMenu.Item(getString(R.string.folder_take_out), () -> { Folders.remove(this, pkg); refreshFolder(); }),
                    new TileMenu.Item(getString(R.string.move_to_other_folder), () ->
                            menu = TileMenu.show(this, ui, pal, t, Folders.chooser(this, pkg, openKey, this::refreshFolder))),
                    new TileMenu.Item(getString(Store.dock(this).size() < 6 ? R.string.add_to_dock : R.string.replace_in_dock), () -> place(pkg)),
                    new TileMenu.Item(getString(R.string.app_info), () -> Apps.info(this, pkg)));
            return;
        }
        final List<String> dock = Store.dock(this);
        final Set<String> hidden = Store.hidden(this);
        List<TileMenu.Item> items = new ArrayList<>();
        if (dock.contains(pkg)) items.add(new TileMenu.Item(getString(R.string.remove_from_dock), () -> { dock.remove(pkg); Store.setDock(this, dock); fill(); }));
        else items.add(new TileMenu.Item(getString(dock.size() < 6 ? R.string.add_to_dock : R.string.replace_in_dock), () -> place(pkg)));
        if (hidden.contains(pkg)) items.add(new TileMenu.Item(getString(R.string.show_on_home), () -> { hidden.remove(pkg); Store.setHidden(this, hidden); fill(); }));
        else items.add(new TileMenu.Item(getString(R.string.hide_from_home), () -> { hidden.add(pkg); Store.setHidden(this, hidden); fill(); }));
        if (Folders.on(this))
            items.add(new TileMenu.Item(getString(R.string.move_to_folder), () ->
                    menu = TileMenu.show(this, ui, pal, t, Folders.chooser(this, pkg, null, this::fill))));
        items.add(new TileMenu.Item(getString(R.string.app_info), () -> Apps.info(this, pkg)));
        menu = TileMenu.show(this, ui, pal, t, items.toArray(new TileMenu.Item[0]));
    }
}
