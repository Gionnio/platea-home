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
        c.defaults();
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
        return c;
    }

    private void line(String pkg, String kind, String text) { lines.put(pkg, new String[]{kind, text}); }

    private void defaults() {
        dock.add("com.github.gionnio.platea");
        dock.add("io.gh.reisxd.tizentube.cobalt");
        dock.add("com.amazon.amazonvideo.livingroom");
        dock.add("com.apple.atve.androidtv.appletv");
        dock.add("com.netflix.ninja");
        dock.add("com.android.vending");
        line("com.github.gionnio.platea", "MEDIA CENTER", "La tua libreria, in direct play.");
        line("io.gh.reisxd.tizentube.cobalt", "VIDEO", "YouTube, senza pubblicità.");
        line("com.google.android.youtube.tv", "VIDEO", "Video, musica e creator.");
        line("com.amazon.amazonvideo.livingroom", "STREAMING", "Film e serie del tuo abbonamento.");
        line("com.amazon.firebat", "STREAMING", "Film e serie del tuo abbonamento.");
        line("com.netflix.ninja", "STREAMING", "Film e serie del tuo abbonamento.");
        line("com.apple.atve.sony.appletv", "STREAMING", "Film e serie Apple.");
        line("com.apple.atve.androidtv.appletv", "STREAMING", "Film e serie Apple.");
        line("com.disney.disneyplus", "STREAMING", "Film e serie Disney, Pixar, Marvel.");
        line("com.sony.dtv.tvx", "TV IN DIRETTA", "Digitale terrestre e ingressi HDMI.");
        line("com.mediatek.wwtv.tvcenter", "TV IN DIRETTA", "Digitale terrestre e ingressi HDMI.");
        line("it.rainet.androidtv", "TV E STREAMING", "Rai in diretta e on demand.");
        line("it.mediaset.infinitytv", "TV E STREAMING", "Mediaset in diretta e on demand.");
        line("com.dazn", "SPORT", "Sport in diretta.");
        line("com.nowtv.it", "STREAMING", "Cinema, serie e sport.");
        line("com.spotify.tv.android", "MUSICA", "Musica e podcast.");
        line("org.videolan.vlc", "LETTORE", "File e flussi video.");
        line("com.esaba.downloader", "STRUMENTI", "Scarica file e app.");
        line("com.mobisystems.fileman", "STRUMENTI", "I file della TV.");
        line("com.android.vending", "APP", "App e giochi.");
        line("com.android.tv.settings", "SISTEMA", "Tutte le impostazioni della TV.");
    }
}
