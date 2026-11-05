package io.github.gionnio.plateahome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cartelle di app, come su Apple TV, fuori dal dock. I nomi si scelgono da una lista fissa (niente tastiera col
 * telecomando); un'app sta in una cartella sola. Salvate sulla TV come "chiave=pkg,pkg;chiave=pkg".
 */
final class Folders {
    private Folders() {}

    static final String[] KEYS = {"streaming", "sport", "tv", "music", "games", "kids", "tools", "other"};

    static String label(Context c, String key) {
        switch (key) {
            case "streaming": return c.getString(R.string.folder_streaming);
            case "sport": return c.getString(R.string.folder_sport);
            case "tv": return c.getString(R.string.folder_tv);
            case "music": return c.getString(R.string.folder_music);
            case "games": return c.getString(R.string.folder_games);
            case "kids": return c.getString(R.string.folder_kids);
            case "tools": return c.getString(R.string.folder_tools);
            default: return c.getString(R.string.folder_other);
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("platea_home", Context.MODE_PRIVATE);
    }

    static boolean on(Context c) { return prefs(c).getBoolean("folders_on", true); }

    static void setOn(Context c, boolean on) { prefs(c).edit().putBoolean("folders_on", on).apply(); }

    /** Le cartelle salvate, nell'ordine della lista dei nomi. Vuota se le cartelle sono spente. */
    static Map<String, List<String>> load(Context c) {
        Map<String, List<String>> raw = new LinkedHashMap<>();
        if (!on(c)) return raw;
        String s = prefs(c).getString("folders", "");
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            List<String> pk = new ArrayList<>();
            for (String p : part.substring(eq + 1).split(",")) if (!p.isEmpty()) pk.add(p);
            if (!pk.isEmpty()) raw.put(part.substring(0, eq), pk);
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (String k : KEYS) if (raw.containsKey(k)) out.put(k, raw.get(k));
        return out;
    }

    static void save(Context c, Map<String, List<String>> f) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, List<String>> e : f.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            if (b.length() > 0) b.append(';');
            b.append(e.getKey()).append('=').append(String.join(",", e.getValue()));
        }
        prefs(c).edit().putString("folders", b.toString()).apply();
    }

    /** La cartella che contiene l'app, o null. */
    static String of(Map<String, List<String>> f, String pkg) {
        for (Map.Entry<String, List<String>> e : f.entrySet()) if (e.getValue().contains(pkg)) return e.getKey();
        return null;
    }

    /** Mette l'app nella cartella (togliendola da un'altra e dal dock). */
    static void put(Context c, String key, String pkg) {
        Map<String, List<String>> f = load(c);
        for (List<String> l : f.values()) l.remove(pkg);
        List<String> l = f.get(key);
        if (l == null) { l = new ArrayList<>(); f.put(key, l); }
        l.add(pkg);
        save(c, f);
        List<String> dock = Store.dock(c);
        if (dock.remove(pkg)) Store.setDock(c, dock);
    }

    static void remove(Context c, String pkg) {
        Map<String, List<String>> f = load(c);
        boolean changed = false;
        for (List<String> l : f.values()) changed |= l.remove(pkg);
        if (changed) save(c, f);
    }

    static void delete(Context c, String key) {
        Map<String, List<String>> f = load(c);
        if (f.remove(key) != null) save(c, f);
    }

    static void rename(Context c, String from, String to) {
        Map<String, List<String>> f = load(c);
        List<String> l = f.remove(from);
        if (l == null) return;
        List<String> into = f.get(to);
        if (into == null) f.put(to, l); else into.addAll(l);
        save(c, f);
    }

    /** Le app installate di una cartella, in ordine alfabetico. */
    static List<Apps.App> apps(List<Apps.App> all, List<String> pkgs) {
        List<Apps.App> out = new ArrayList<>();
        for (Apps.App a : all) if (pkgs.contains(a.pkg)) out.add(a);
        return out;
    }

    /** "Sposta in una cartella": le cartelle esistenti con il numero di app, poi i nomi liberi. */
    static TileMenu.Item[] chooser(Context c, String pkg, String current, Runnable done) {
        Map<String, List<String>> f = load(c);
        List<TileMenu.Item> items = new ArrayList<>();
        for (String k : KEYS) {
            if (k.equals(current)) continue;
            List<String> l = f.get(k);
            String text = l == null ? c.getString(R.string.folder_new, label(c, k)) : c.getString(R.string.folder_with, label(c, k), l.size());
            items.add(new TileMenu.Item(text, () -> { put(c, k, pkg); done.run(); }));
        }
        return items.toArray(new TileMenu.Item[0]);
    }

    /** "Rinomina": i nomi delle altre cartelle; uno già usato unisce le due cartelle. */
    static TileMenu.Item[] renamer(Context c, String key, Runnable done) {
        List<TileMenu.Item> items = new ArrayList<>();
        for (String k : KEYS) {
            if (k.equals(key)) continue;
            items.add(new TileMenu.Item(label(c, k), () -> { rename(c, key, k); done.run(); }));
        }
        return items.toArray(new TileMenu.Item[0]);
    }

    /**
     * La cartella aperta sopra la pagina: nome grande, le sue app 6 per riga con il nome sotto. Le tessere finiscono in
     * {@code tiles}; i tasti li gestisce l'attività (OK apre, OK tenuto il menu, Indietro chiude).
     */
    static FrameLayout panel(Activity a, Ui ui, Palette pal, String key, List<Apps.App> apps, List<TileView> tiles) {
        FrameLayout box = new FrameLayout(a);
        box.setBackgroundColor(pal.ground);
        box.setClickable(true);
        ScrollView scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(ui.px(112), ui.px(140), ui.px(112), ui.px(96));
        TextView eyebrow = ui.text(a.getString(R.string.folder_kind).toUpperCase(java.util.Locale.getDefault()), 18, pal.ter, 600);
        eyebrow.setLetterSpacing(0.18f);
        col.addView(eyebrow, new LinearLayout.LayoutParams(-2, -2));
        TextView title = ui.text(label(a, key), 72, pal.text, 600);
        title.setLetterSpacing(-0.03f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = ui.px(12);
        col.addView(title, tp);

        GridLayout grid = new GridLayout(a);
        grid.setColumnCount(6);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        int w = ui.px(256), h = ui.px(144);
        for (int i = 0; i < apps.size(); i++) {
            Apps.App app = apps.get(i);
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setClipChildren(false);
            TileView t = new TileView(a, ui, pal);
            t.bind(app, false);
            cell.addView(t, new LinearLayout.LayoutParams(w, h));
            TextView lbl = ui.text(app.label, 19, pal.ter, 400);
            lbl.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, -2);
            lp.topMargin = ui.px(12);
            cell.addView(lbl, lp);
            t.setListener((tile, gained) -> {
                lbl.setTextColor(gained ? pal.text : pal.ter);
                lbl.setTypeface(ui.face(gained ? 600 : 400));
            });
            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width = w;
            gp.rightMargin = (i % 6 == 5) ? 0 : ui.px(32);
            gp.bottomMargin = ui.px(40);
            grid.addView(cell, gp);
            tiles.add(t);
        }
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, -2);
        gl.topMargin = ui.px(56);
        col.addView(grid, gl);
        TextView hint = ui.text(a.getString(R.string.folder_hint), 20, pal.ter, 500);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = ui.px(12);
        col.addView(hint, hp);
        scroll.addView(col);
        box.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (!tiles.isEmpty()) box.post(() -> tiles.get(0).requestFocus());
        return box;
    }

    /** Toglie la cartella aperta dalla pagina e ridà il focus alla sua tessera. */
    static void close(FrameLayout parent, View panel, View back, View... blocked) {
        parent.removeView(panel);
        for (View v : blocked) {
            v.setAlpha(1f);
            if (v instanceof android.view.ViewGroup)
                ((android.view.ViewGroup) v).setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
        if (back != null) back.requestFocus();
    }
}
