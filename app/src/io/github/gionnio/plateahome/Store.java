package io.github.gionnio.plateahome;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Le scelte fatte sulla TV: ordine del dock e app nascoste. Senza scelte valgono quelle del config. */
final class Store {
    private Store() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("platea_home", Context.MODE_PRIVATE);
    }

    static List<String> dock(Context c) {
        String saved = prefs(c).getString("dock", null);
        if (saved == null) return new ArrayList<>(Config.get(c).dock);
        List<String> out = new ArrayList<>();
        for (String p : saved.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    static void setDock(Context c, List<String> dock) {
        prefs(c).edit().putString("dock", String.join(",", dock)).apply();
    }

    static Set<String> hidden(Context c) {
        String saved = prefs(c).getString("hidden", null);
        if (saved == null) return new HashSet<>(Config.get(c).hidden);
        return new HashSet<>(Arrays.asList(saved.split(",")));
    }

    static void setHidden(Context c, Set<String> hidden) {
        prefs(c).edit().putString("hidden", String.join(",", hidden)).apply();
    }
}
