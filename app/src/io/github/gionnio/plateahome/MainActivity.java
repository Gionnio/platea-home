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
import java.util.Locale;
import java.util.Set;

/**
 * La Home di Platea Home: ora e data, tre pulsanti tondi, il top shelf testuale (categoria, nome, una riga),
 * il dock a 6 app e una riga attenuata con le altre app e "Tutte le app". Nessuna rete, nessuna immagine di sfondo.
 */
public final class MainActivity extends Activity implements TileView.Listener {
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
    private List<String> movingBackup;
    private boolean longFired;
    private PopupWindow menu;
    private boolean receiversOn;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { updateClock(); }
    };
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { reload(); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        pal = Palette.of(Config.get(this).palette);
        build();
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.ground);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.BOTTOM);
        clock = ui.text("", 38, pal.text, 300);
        date = ui.text("", 20, pal.ter, 500);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-2, -2);
        dp.leftMargin = ui.px(18);
        dp.bottomMargin = ui.px(5);
        head.addView(clock);
        head.addView(date, dp);
        root.addView(head, ui.at(LEFT, 56, -2, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setClipChildren(false);
        IconButton inputs = new IconButton(this, ui, pal, IconButton.INPUTS, "Ingressi");
        IconButton net = new IconButton(this, ui, pal, IconButton.NETWORK, "Rete");
        IconButton gear = new IconButton(this, ui, pal, IconButton.SETTINGS, "Impostazioni");
        inputs.setOnClickListener(v -> openInputs());
        net.setOnClickListener(v -> Apps.action(this, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS));
        gear.setOnClickListener(v -> Apps.action(this, Settings.ACTION_SETTINGS));
        for (IconButton btn : new IconButton[]{inputs, net, gear}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ui.px(56), ui.px(56));
            lp.leftMargin = ui.px(16);
            buttons.addView(btn, lp);
        }
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
        bp.gravity = Gravity.TOP | Gravity.END;
        bp.topMargin = ui.px(60);
        bp.rightMargin = ui.px(LEFT);
        root.addView(buttons, bp);

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

        setContentView(root);
    }

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
        updateClock();
        reload();
    }

    @Override protected void onPause() {
        super.onPause();
        if (menu != null) menu.dismiss();
        if (moving >= 0) endMove(false);
    }

    @Override protected void onDestroy() {
        if (receiversOn) { unregisterReceiver(tick); unregisterReceiver(packages); receiversOn = false; }
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (menu != null) menu.dismiss();
        if (moving >= 0) endMove(false);
        if (!dockTiles.isEmpty() && dockTiles.get(0).getVisibility() == View.VISIBLE) dockTiles.get(0).requestFocus();
    }

    private void updateClock() {
        clock.setText(Ui.clock());
        date.setText(Ui.today());
    }

    /** Ricarica app, dock e riga, tenendo il focus dov'era. */
    private void reload() {
        View f = getCurrentFocus();
        int focusDock = dockTiles.indexOf(f), focusRow = rowTiles.indexOf(f);
        apps = Apps.load(this);
        Set<String> hidden = Store.hidden(this);
        dock.clear();
        for (String p : Store.dock(this)) if (Apps.find(apps, p) != null && dock.size() < DOCK) dock.add(p);
        for (int i = 0; i < DOCK; i++) {
            TileView t = dockTiles.get(i);
            if (i < dock.size()) { t.bind(Apps.find(apps, dock.get(i)), false); t.setVisibility(View.VISIBLE); }
            else t.setVisibility(View.GONE);
        }
        List<Apps.App> rest = new ArrayList<>();
        for (Apps.App a : apps) if (!dock.contains(a.pkg) && !hidden.contains(a.pkg)) rest.add(a);
        for (int i = 0; i < ROW; i++) {
            TileView t = rowTiles.get(i);
            if (i < rest.size()) { t.bind(rest.get(i), false); t.setVisibility(View.VISIBLE); }
            else t.setVisibility(View.GONE);
        }
        rowTiles.get(ROW).bind(null, true);

        View target = focusDock >= 0 && focusDock < dock.size() ? dockTiles.get(focusDock)
                : focusRow >= 0 && rowTiles.get(focusRow).getVisibility() == View.VISIBLE ? rowTiles.get(focusRow)
                : f != null && !(f instanceof TileView) ? f
                : dock.isEmpty() ? rowTiles.get(ROW) : dockTiles.get(0);
        target.requestFocus();
        if (target instanceof TileView) onTileFocus((TileView) target, true);
    }

    // ---- top shelf ---------------------------------------------------------------------------

    @Override public void onTileFocus(TileView t, boolean focused) {
        if (!focused || moving >= 0) return;
        boolean inRow = rowTiles.contains(t);
        moreRow.animate().alpha(inRow ? 1f : 0.5f).setDuration(160).start();
        if (t.allApps) {
            shelf("TUTTE LE APP", "App", apps.size() + " app installate");
            return;
        }
        if (t.app == null) return;
        String[] l = Config.get(this).lines.get(t.app.pkg);
        shelf(l == null ? "APP" : l[0], t.app.label, l == null ? "" : l[1]);
    }

    private void shelf(String kind, String title, String text) {
        eyebrow.setText(kind.toUpperCase(Locale.ITALIAN));
        name.setText(title);
        line.setText(text);
    }

    // ---- tasti: OK apre, OK tenuto apre il menu, Sposta ------------------------------------

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean ok = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;

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
        if (t.allApps) startActivity(new Intent(this, AppsActivity.class));
        else if (t.app != null) Apps.open(this, t.app.pkg);
    }

    private void openInputs() {
        if (Apps.action(this, "com.android.tv.action.VIEW_INPUTS")) return;
        String live = Config.get(this).liveTv;
        if (!live.isEmpty()) Apps.open(this, live);
    }

    private void showMenu(TileView t) {
        if (t.allApps || t.app == null) return;
        final String pkg = t.app.pkg;
        int index = dockTiles.indexOf(t);
        List<TileMenu.Item> items = new ArrayList<>();
        if (index >= 0) {
            items.add(new TileMenu.Item("Sposta", () -> startMove(index)));
            items.add(new TileMenu.Item("Togli dal dock", () -> { dock.remove(pkg); Store.setDock(this, dock); reload(); }));
        } else if (dock.size() < DOCK) {
            items.add(new TileMenu.Item("Aggiungi al dock", () -> { dock.add(pkg); Store.setDock(this, dock); reload(); }));
        }
        items.add(new TileMenu.Item("Nascondi", () -> {
            Set<String> h = Store.hidden(this); h.add(pkg); Store.setHidden(this, h);
            if (dock.remove(pkg)) Store.setDock(this, dock);
            reload();
        }));
        items.add(new TileMenu.Item("Info app", () -> Apps.info(this, pkg)));
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
        shelf("SPOSTA", dockTiles.get(index).label(), "Sinistra e Destra per spostarla · OK per posarla · Indietro per annullare");
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
        if (keep) Store.setDock(this, dock);
        else { dock.clear(); dock.addAll(movingBackup); }
        for (TileView t : dockTiles) { t.moving = false; t.setAlpha(1f); t.lift(1f, 0, 0); t.invalidate(); }
        reload();
        if (at >= 0 && at < dock.size()) dockTiles.get(at).requestFocus();
    }
}
