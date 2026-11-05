package io.github.gionnio.plateahome;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * La Home di Platea Home: ora e data, tre pulsanti tondi, il top shelf testuale (categoria, nome, una riga),
 * il dock a 6 app e una riga attenuata con le altre app e "Tutte le app". Nessuna rete, nessuna immagine di sfondo.
 */
public final class MainActivity extends Activity implements TileView.Listener, Panels.Host {
    static final String EXTRA_PLACE = "io.github.gionnio.plateahome.PLACE";
    private static final int DOCK = 6, ROW = 5;
    private static final float LEFT = 112, TILE_W = 256, TILE_H = 144, GAP = 32;

    private Ui ui;
    private Palette pal;
    private List<Apps.App> apps = new ArrayList<>();
    private final List<String> dock = new ArrayList<>();

    private TextView clock, date, eyebrow, name, line;
    private LinearLayout dockRow, moreRow;
    private final List<TileView> dockTiles = new ArrayList<>(), rowTiles = new ArrayList<>();

    private int moving = -1;
    /** App appena aggiunta al dock e in modalità Sposta: confermata, esce dalle nascoste. */
    private String adding;
    /** App da mettere nel dock pieno (modalità Sostituisci) e la posizione scelta. */
    private String placing;
    private int placeSlot = -1;
    /** App arrivata dalla pagina App con "Aggiungi al dock" / "Sostituisci nel dock", da piazzare dopo il reload. */
    private String pendingPlace;
    private List<String> movingBackup;
    private boolean longFired;
    private PopupWindow menu;
    private boolean receiversOn;
    private FrameLayout screen, home;
    private View sheet;
    private View beforeSheet;
    private String paletteName;
    private Map<String, List<String>> folders = new LinkedHashMap<>();
    /** La cartella aperta sopra la Home, le sue tessere e la tessera da cui la si è aperta. */
    private FrameLayout folderPanel;
    private String openKey;
    private final List<TileView> folderTiles = new ArrayList<>();
    private View beforeFolder;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            updateClock();
            // Palette automatica: cambia all'alba e al tramonto, ma solo se nessuno sta usando la Home da un minuto.
            if (!Store.effective(MainActivity.this).equals(paletteName) && idle()) recreate();
        }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        paletteName = Store.effective(this);
        pal = Palette.of(paletteName);
        build();
    }

    private void build() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(pal.ground);
        FrameLayout root = new FrameLayout(this);
        home = root;
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        // La data poggia sulla stessa linea di base delle cifre dell'ora (non centrata, non in fondo al riquadro).
        head.setBaselineAligned(true);
        clock = ui.text("", 38, pal.text, 300);
        date = ui.text("", 20, pal.ter, 500);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.leftMargin = ui.px(18);
        head.addView(clock);
        head.addView(date, dp);

        // Ora e pulsanti nella stessa riga, centrati sulla stessa linea orizzontale.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBaselineAligned(false);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.addView(head, new LinearLayout.LayoutParams(-2, -2));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        FrameLayout.LayoutParams barp = new FrameLayout.LayoutParams(-1, ui.px(60));
        barp.gravity = Gravity.TOP | Gravity.START;
        barp.leftMargin = ui.px(LEFT);
        barp.rightMargin = ui.px(LEFT);
        barp.topMargin = ui.px(50);
        root.addView(bar, barp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        IconButton inputs = new IconButton(this, ui, pal, IconButton.INPUTS, getString(R.string.inputs));
        IconButton net = new IconButton(this, ui, pal, IconButton.NETWORK, getString(R.string.network));
        IconButton gear = new IconButton(this, ui, pal, IconButton.SETTINGS, getString(R.string.settings));
        inputs.setOnClickListener(v -> openInputs());
        net.setOnClickListener(v -> openNetwork());
        gear.setOnClickListener(v -> openSheet(Panels.quick(this, ui, pal, this)));
        for (IconButton btn : new IconButton[]{inputs, net, gear}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.px(56), ui.px(56));
            lp.leftMargin = ui.px(16);
            buttons.addView(btn, lp);
        }
        bar.addView(buttons, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout shelf = new LinearLayout(this);
        shelf.setOrientation(LinearLayout.VERTICAL);
        eyebrow = ui.text("", 18, pal.ter, 600);
        eyebrow.setLetterSpacing(0.18f);
        name = ui.text("", 104, pal.text, 600);
        name.setLetterSpacing(-0.03f);
        line = ui.text("", 28, pal.sec, 400);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = ui.px(18);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(-1, -2);
        lp2.topMargin = ui.px(18);
        shelf.addView(eyebrow);
        shelf.addView(name, np);
        shelf.addView(line, lp2);
        root.addView(shelf, ui.at(LEFT, 296, ui.px(1500), -2));

        dockRow = row(root, 640);
        moreRow = row(root, 900);
        moreRow.setAlpha(0.5f);
        for (int i = 0; i < DOCK; i++) dockTiles.add(tile(dockRow));
        for (int i = 0; i <= ROW; i++) rowTiles.add(tile(moreRow));

        screen.setClipChildren(false);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
    }

    // ---- pannelli laterali -------------------------------------------------------------------

    private void openSheet(Sheet s) {
        if (sheet != null) screen.removeView(sheet);
        else beforeSheet = getCurrentFocus();
        sheet = s.box;
        screen.addView(sheet, s.params());
        home.setAlpha(0.28f);
        home.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        sheet.post(() -> { if (sheet != null) sheet.requestFocus(View.FOCUS_DOWN); });
    }

    @Override public void close() {
        if (sheet == null) return;
        screen.removeView(sheet);
        sheet = null;
        home.setAlpha(1f);
        home.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if (beforeSheet != null) beforeSheet.requestFocus();
    }

    @Override public void openNetwork() { openSheet(Panels.network(this, ui, pal)); }

    @Override public void openInputs() { openSheet(Panels.inputs(this, ui, pal, Config.get(this).liveTv, this)); }

    private LinearLayout row(FrameLayout root, float top) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        root.addView(r, ui.at(LEFT, top, -2, ui.px(TILE_H)));
        return r;
    }

    private TileView tile(LinearLayout parent) {
        TileView t = new TileView(this, ui, pal);
        t.setListener(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.px(TILE_W), ui.px(TILE_H));
        if (parent.getChildCount() > 0) lp.leftMargin = ui.px(GAP);
        parent.addView(t, lp);
        return t;
    }

    @Override protected void onResume() {
        super.onResume();
        if (!receiversOn) {
            registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_PACKAGE_ADDED);
            f.addAction(Intent.ACTION_PACKAGE_REMOVED);
            f.addAction(Intent.ACTION_PACKAGE_CHANGED);
            f.addDataScheme("package");
            registerReceiver(packages, f);
            receiversOn = true;
        }
        if (!Store.effective(this).equals(paletteName)) { recreate(); return; }
        updateClock();
        if (sheet == null) reload();
        if (pendingPlace != null) { String p = pendingPlace; pendingPlace = null; startPlace(p); }
    }

    @Override protected void onPause() {
        super.onPause();
        if (menu != null) menu.dismiss();
        if (moving >= 0) endMove(false);
        if (placing != null) endPlace(false);
    }

    @Override protected void onDestroy() {
        if (receiversOn) { unregisterReceiver(tick); unregisterReceiver(packages); receiversOn = false; }
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (menu != null) menu.dismiss();
        close();
        closeFolder();
        if (moving >= 0) endMove(false);
        if (placing != null) endPlace(false);
        pendingPlace = intent.getStringExtra(EXTRA_PLACE);
        if (pendingPlace == null && !dockTiles.isEmpty() && dockTiles.get(0).getVisibility() == View.VISIBLE) dockTiles.get(0).requestFocus();
    }

    private void updateClock() {
        clock.setText(Ui.clock());
        date.setText(Ui.today(this));
    }

    /** Ricarica app, dock e riga, tenendo il focus dov'era. */
    private void reload() {
        View f = getCurrentFocus();
        int focusDock = dockTiles.indexOf(f), focusRow = rowTiles.indexOf(f);
        apps = Apps.load(this);
        folders = Folders.load(this);
        Set<String> hidden = Store.hidden(this);
        dock.clear();
        for (String p : Store.dock(this)) if (Apps.find(apps, p) != null && dock.size() < DOCK) dock.add(p);
        bindDock();
        bindRow(hidden);

        View target = focusDock >= 0 && focusDock < dock.size() ? dockTiles.get(focusDock)
                : focusRow >= 0 && rowTiles.get(focusRow).getVisibility() == View.VISIBLE ? rowTiles.get(focusRow)
                : f != null && !(f instanceof TileView) ? f
                : dock.isEmpty() ? rowTiles.get(ROW) : dockTiles.get(0);
        target.requestFocus();
        if (target instanceof TileView) onTileFocus((TileView) target, true);
    }

    /** La riga sotto il dock: prima le cartelle, poi le app né nel dock, né nascoste, né in una cartella; poi "Tutte le app". */
    private void bindRow(Set<String> hidden) {
        List<Object> rest = new ArrayList<>();
        for (Map.Entry<String, List<String>> f : folders.entrySet())
            if (!Folders.apps(apps, f.getValue()).isEmpty()) rest.add(f.getKey());
        for (Apps.App a : apps)
            if (!dock.contains(a.pkg) && !hidden.contains(a.pkg) && Folders.of(folders, a.pkg) == null) rest.add(a);
        for (int i = 0; i < ROW; i++) {
            TileView t = rowTiles.get(i);
            if (i >= rest.size()) { t.setVisibility(View.GONE); continue; }
            Object o = rest.get(i);
            if (o instanceof String) t.bindFolder((String) o, Folders.apps(apps, folders.get(o)));
            else t.bind((Apps.App) o, false);
            t.setVisibility(View.VISIBLE);
        }
        rowTiles.get(ROW).bind(null, true);
    }

    private void bindDock() {
        for (int i = 0; i < DOCK; i++) {
            TileView t = dockTiles.get(i);
            if (i < dock.size()) { t.bind(Apps.find(apps, dock.get(i)), false); t.setVisibility(View.VISIBLE); }
            else t.setVisibility(View.GONE);
        }
    }

    // ---- top shelf ---------------------------------------------------------------------------

    @Override public void onTileFocus(TileView t, boolean focused) {
        if (!focused || moving >= 0 || placing != null) return;
        boolean inRow = rowTiles.contains(t);
        moreRow.animate().alpha(inRow ? 1f : 0.5f).setDuration(160).start();
        if (t.allApps) {
            shelf(getString(R.string.all_apps_kind), getString(R.string.apps_title), getString(R.string.apps_installed, apps.size()));
            return;
        }
        if (t.folder != null) {
            shelf(getString(R.string.folder_kind), t.label(), summary(t.folderApps));
            return;
        }
        if (t.app == null) return;
        String[] l = Config.get(this).lines.get(t.app.pkg);
        shelf(l == null ? getString(R.string.kind_app) : l[0], t.app.label, l == null ? "" : l[1]);
    }

    /** "5 app: DAZN, Infinity, Sky Go e altre 2" */
    private String summary(List<Apps.App> in) {
        if (in == null || in.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Math.min(3, in.size()); i++) { if (i > 0) b.append(", "); b.append(in.get(i).label); }
        return in.size() > 3 ? getString(R.string.folder_summary_more, in.size(), b.toString(), in.size() - 3)
                : getString(R.string.folder_summary, in.size(), b.toString());
    }

    private void shelf(String kind, String title, String text) {
        eyebrow.setText(kind.toUpperCase(Locale.getDefault()));
        name.setText(title);
        line.setText(text);
    }

    // ---- tasti: OK apre, OK tenuto apre il menu, Sposta ------------------------------------

    /** Ultimo tasto premuto: la palette automatica non cambia mentre qualcuno usa la Home. */
    private long lastKey;

    private boolean idle() {
        return sheet == null && folderPanel == null && (menu == null || !menu.isShowing()) && moving < 0 && placing == null
                && android.os.SystemClock.uptimeMillis() - lastKey > 60_000;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastKey = android.os.SystemClock.uptimeMillis();
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;

        if (sheet != null) {
            if (code == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) close();
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (folderPanel != null && code == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) closeFolder();
            return true;
        }

        if (placing != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT && placeSlot > 0) { placeSlot--; showPlace(); }
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT && placeSlot < dock.size() - 1) { placeSlot++; showPlace(); }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (ok) endPlace(true);
                else if (code == KeyEvent.KEYCODE_BACK) endPlace(false);
            }
            return true;
        }

        if (moving >= 0) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) moveBy(-1);
                else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) moveBy(1);
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                if (ok) endMove(true);
                else if (code == KeyEvent.KEYCODE_BACK) endMove(false);
            }
            return true;
        }

        View f = getCurrentFocus();
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
                if (!longFired) openTile(t);
                longFired = false;
                return true;
            }
        }
        if (code == KeyEvent.KEYCODE_BACK) return true;   // la Home non va "indietro"
        return super.dispatchKeyEvent(e);
    }

    private void openTile(TileView t) {
        if (t.folder != null) openFolder(t.folder);
        else if (t.allApps) startActivity(new Intent(this, AppsActivity.class));
        else if (t.app != null) Apps.open(this, t.app.pkg);
    }

    private void showMenu(TileView t) {
        if (t.folder != null) { folderMenu(t); return; }
        if (t.allApps || t.app == null) return;
        final String pkg = t.app.pkg;
        if (folderTiles.contains(t)) { inFolderMenu(t); return; }
        int index = dockTiles.indexOf(t);
        List<TileMenu.Item> items = new ArrayList<>();
        if (index >= 0) {
            items.add(new TileMenu.Item(getString(R.string.move), () -> startMove(index)));
            items.add(new TileMenu.Item(getString(R.string.remove_from_dock), () -> { dock.remove(pkg); Store.setDock(this, dock); reload(); }));
        } else {
            items.add(new TileMenu.Item(getString(dock.size() < DOCK ? R.string.add_to_dock : R.string.replace_in_dock), () -> startPlace(pkg)));
        }
        items.add(new TileMenu.Item(getString(R.string.hide), () -> {
            Set<String> h = Store.hidden(this); h.add(pkg); Store.setHidden(this, h);
            if (dock.remove(pkg)) Store.setDock(this, dock);
            reload();
        }));
        if (Folders.on(this))
            items.add(new TileMenu.Item(getString(R.string.move_to_folder), () ->
                    menu = TileMenu.show(this, ui, pal, t, Folders.chooser(this, pkg, null, this::reload))));
        items.add(new TileMenu.Item(getString(R.string.app_info), () -> Apps.info(this, pkg)));
        menu = TileMenu.show(this, ui, pal, t, items.toArray(new TileMenu.Item[0]));
    }

    // ---- cartelle ----------------------------------------------------------------------------

    private void openFolder(String key) {
        List<String> pk = folders.get(key);
        List<Apps.App> in = pk == null ? new ArrayList<>() : Folders.apps(apps, pk);
        if (in.isEmpty()) return;
        if (folderPanel != null) screen.removeView(folderPanel);
        else beforeFolder = getCurrentFocus();
        folderTiles.clear();
        openKey = key;
        folderPanel = Folders.panel(this, ui, pal, key, in, folderTiles);
        screen.addView(folderPanel, new FrameLayout.LayoutParams(-1, -1));
        home.setAlpha(0.15f);
        home.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void closeFolder() {
        if (folderPanel == null) return;
        Folders.close(screen, folderPanel, null, home);
        folderPanel = null;
        openKey = null;
        folderTiles.clear();
        reload();
        // Torna sulla tessera della cartella, se c'è ancora; altrimenti reload() ha già messo il focus.
        if (beforeFolder instanceof TileView && beforeFolder.getVisibility() == View.VISIBLE) beforeFolder.requestFocus();
        beforeFolder = null;
    }

    /** Dopo una modifica dentro la cartella: la riapre aggiornata, o la chiude se è rimasta vuota. */
    private void refreshFolder() {
        String key = openKey;
        reload();
        if (key != null && folders.containsKey(key) && !Folders.apps(apps, folders.get(key)).isEmpty()) {
            View back = beforeFolder;
            openFolder(key);
            beforeFolder = back;
        } else closeFolder();
    }

    private void folderMenu(TileView t) {
        final String key = t.folder;
        List<TileMenu.Item> items = new ArrayList<>();
        items.add(new TileMenu.Item(getString(R.string.folder_rename), () ->
                menu = TileMenu.show(this, ui, pal, t, Folders.renamer(this, key, this::reload))));
        items.add(new TileMenu.Item(getString(R.string.folder_delete), () -> { Folders.delete(this, key); reload(); }));
        menu = TileMenu.show(this, ui, pal, t, items.toArray(new TileMenu.Item[0]));
    }

    private void inFolderMenu(TileView t) {
        final String pkg = t.app.pkg;
        List<TileMenu.Item> items = new ArrayList<>();
        items.add(new TileMenu.Item(getString(R.string.folder_take_out), () -> { Folders.remove(this, pkg); refreshFolder(); }));
        items.add(new TileMenu.Item(getString(R.string.move_to_other_folder), () ->
                menu = TileMenu.show(this, ui, pal, t, Folders.chooser(this, pkg, openKey, this::refreshFolder))));
        items.add(new TileMenu.Item(getString(dock.size() < DOCK ? R.string.add_to_dock : R.string.replace_in_dock), () -> {
            beforeFolder = null;
            closeFolder();
            startPlace(pkg);
        }));
        items.add(new TileMenu.Item(getString(R.string.app_info), () -> Apps.info(this, pkg)));
        menu = TileMenu.show(this, ui, pal, t, items.toArray(new TileMenu.Item[0]));
    }

    private void startMove(int index) {
        moving = index;
        movingBackup = new ArrayList<>(dock);
        for (int i = 0; i < dock.size(); i++) {
            TileView t = dockTiles.get(i);
            t.moving = i == index;
            t.setAlpha(i == index ? 1f : 0.55f);
            if (i == index) t.lift(1.1f, -ui.px(34), -1.5f); else t.lift(1f, 0, 0);
            t.invalidate();
        }
        dockTiles.get(index).requestFocus();
        shelf(getString(R.string.move_kind), dockTiles.get(index).label(), getString(R.string.move_hint));
    }

    private void moveBy(int delta) {
        int to = moving + delta;
        if (to < 0 || to >= dock.size()) return;
        String p = dock.remove(moving);
        dock.add(to, p);
        TileView from = dockTiles.get(moving), dest = dockTiles.get(to);
        from.moving = false; from.setAlpha(0.55f); from.lift(1f, 0, 0);
        moving = to;
        for (int i = 0; i < dock.size(); i++) dockTiles.get(i).bind(Apps.find(apps, dock.get(i)), false);
        dest.moving = true; dest.setAlpha(1f); dest.lift(1.1f, -ui.px(34), -1.5f);
        dest.requestFocus();
        from.invalidate(); dest.invalidate();
    }

    private void endMove(boolean keep) {
        int at = moving;
        moving = -1;
        if (keep) { Store.setDock(this, dock); unhide(adding); if (adding != null) Folders.remove(this, adding); }
        else { dock.clear(); dock.addAll(movingBackup); }
        adding = null;
        for (TileView t : dockTiles) { t.moving = false; t.setAlpha(1f); t.lift(1f, 0, 0); t.invalidate(); }
        reload();
        if (at >= 0 && at < dock.size()) dockTiles.get(at).requestFocus();
    }

    // ---- Aggiungi al dock / Sostituisci nel dock ------------------------------------------

    /** Mette un'app nel dock scegliendo dove: in fondo e in modalità Sposta, o se il dock è pieno in modalità Sostituisci. */
    private void startPlace(String pkg) {
        if (pkg == null || Apps.find(apps, pkg) == null) return;
        int at = dock.indexOf(pkg);
        if (at >= 0) { dockTiles.get(at).requestFocus(); return; }
        if (dock.size() < DOCK) {
            adding = pkg;
            dock.add(pkg);
            bindDock();
            bindRow(Store.hidden(this));
            startMove(dock.size() - 1);
        } else {
            placing = pkg;
            placeSlot = dock.size() - 1;
            showPlace();
        }
    }

    /** Sostituisci: la tessera scelta mostra l'app nuova, sollevata; le altre sono attenuate. */
    private void showPlace() {
        Apps.App app = Apps.find(apps, placing);
        for (int i = 0; i < dock.size(); i++) {
            TileView t = dockTiles.get(i);
            boolean on = i == placeSlot;
            t.bind(on ? app : Apps.find(apps, dock.get(i)), false);
            t.moving = on;
            t.setAlpha(on ? 1f : 0.55f);
            if (on) t.lift(1.1f, -ui.px(34), -1.5f); else t.lift(1f, 0, 0);
            t.invalidate();
        }
        dockTiles.get(placeSlot).requestFocus();
        Apps.App old = Apps.find(apps, dock.get(placeSlot));
        shelf(getString(R.string.replace_kind), app == null ? "" : app.label, getString(R.string.replace_hint, old == null ? "" : old.label));
    }

    private void endPlace(boolean keep) {
        int at = placeSlot;
        if (keep) { dock.set(placeSlot, placing); Store.setDock(this, dock); unhide(placing); Folders.remove(this, placing); }
        placing = null;
        placeSlot = -1;
        for (TileView t : dockTiles) { t.moving = false; t.setAlpha(1f); t.lift(1f, 0, 0); t.invalidate(); }
        reload();
        if (at >= 0 && at < dock.size()) dockTiles.get(at).requestFocus();
    }

    private void unhide(String pkg) {
        if (pkg == null) return;
        Set<String> h = Store.hidden(this);
        if (h.remove(pkg)) Store.setHidden(this, h);
    }
}
