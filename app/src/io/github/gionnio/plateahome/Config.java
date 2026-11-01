package io.github.gionnio.plateahome;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * La configurazione della TV, messa nell'APK alla build (assets/config.json, dal config del progetto).
 * Le scelte fatte sulla TV (ordine del dock, app nascoste) stanno in Store e vincono su questa.
 */
final class Config {
    final List<String> dock = new ArrayList<>();
    final Set<String> hidden = new HashSet<>();
    String liveTv = "";
    String palette = "Grafite";
    String siparioPalette = "Nero";
    /** pacchetto → {categoria, riga} per il top shelf. */
    final Map<String, String[]> lines = new HashMap<>();

    private static Config cached;

    static synchronized Config get(Context context) {
        if (cached == null) cached = load(context);
        return cached;
    }

    private static Config load(Context context) {
        Config c = new Config();
        c.defaults(context);
        try (InputStream in = context.getAssets().open("config.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject o = new JSONObject(out.toString("UTF-8"));
            JSONArray d = o.optJSONArray("dock");
            if (d != null) { c.dock.clear(); for (int i = 0; i < d.length(); i++) c.dock.add(d.getString(i)); }
            JSONArray h = o.optJSONArray("hidden");
            if (h != null) for (int i = 0; i < h.length(); i++) c.hidden.add(h.getString(i));
            c.liveTv = o.optString("live_tv", c.liveTv);
            c.palette = o.optString("palette", c.palette);
            c.siparioPalette = o.optString("sipario_palette", c.siparioPalette);
            JSONObject l = o.optJSONObject("lines");
            if (l != null) {
                JSONArray names = l.names();
                for (int i = 0; names != null && i < names.length(); i++) {
                    JSONArray v = l.getJSONArray(names.getString(i));
                    c.lines.put(names.getString(i), new String[]{v.getString(0), v.optString(1, "")});
                }
            }
        } catch (Exception ignored) {
            // Nessun config o config non valido: restano i valori di serie.
        }
        if ("Amazon".equalsIgnoreCase(android.os.Build.MANUFACTURER)) {
            // Fire OS lists system screens as launchable apps: keep them off Home (they stay on the Apps page).
            for (String p : FIRE_OS_HIDDEN) c.hidden.add(p);
        }
        return c;
    }

    /** Fire OS: Amazon's own Home, profiles, screen savers and settings screens that show up as apps. */
    static final String[] FIRE_OS_HIDDEN = {
            "com.amazon.tv.launcher", "com.amazon.ftv.profilepicker", "com.amazon.tv.earlyaccess",
            "com.amazon.tv.ftvambient", "com.amazon.ftv.screensaver", "com.amazon.ssm",
            "com.amazon.smarthomemapviewapp", "com.amazon.whasettings", "com.amazon.audiohome",
            "com.amazon.firetv.troubleshooting", "tv.dumbtv.home"
    };

    private void defaults(Context context) {
        // More than 6 on purpose: apps that are not installed are skipped, so the same list fits Google TV and Fire TV.
        for (String p : new String[]{"com.google.android.youtube.tv", "com.amazon.firetv.youtube", "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom", "com.amazon.firebat", "com.disney.disneyplus",
                "com.android.vending", "com.amazon.venezia", "com.android.tv.settings"}) dock.add(p);
        // Categoria e riga di serie per le app note, nella lingua della TV (res/values*/strings.xml).
        for (String item : context.getResources().getStringArray(R.array.default_lines)) {
            String[] f = item.split("\\|", 3);
            if (f.length == 3) lines.put(f[0], new String[]{f[1], f[2]});
        }
    }
}
