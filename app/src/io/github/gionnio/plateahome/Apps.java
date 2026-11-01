package io.github.gionnio.plateahome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Le app avviabili della TV, con banner (16:9) o icona. */
final class Apps {
    /** Banner e icona si caricano solo quando una tessera li disegna: la Home ne mostra 12, non tutte. */
    static final class App {
        final String pkg, label;
        private final ActivityInfo info;
        private final PackageManager pm;
        private Drawable banner, icon;
        private boolean loaded;

        App(String pkg, String label, ActivityInfo info, PackageManager pm) {
            this.pkg = pkg; this.label = label; this.info = info; this.pm = pm;
        }

        private void load() {
            if (loaded) return;
            loaded = true;
            banner = info.loadBanner(pm);
            if (banner == null) banner = info.applicationInfo.loadBanner(pm);
            if (banner == null) icon = info.loadIcon(pm);
        }

        Drawable banner() { load(); return banner; }
        Drawable icon() { load(); return icon; }
    }

    private Apps() {}

    /** Tutte le app avviabili, in ordine alfabetico, senza questo launcher. */
    static List<App> load(Context c) {
        PackageManager pm = c.getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                ActivityInfo a = r.activityInfo;
                if (a == null || byPkg.containsKey(a.packageName) || a.packageName.equals(c.getPackageName())) continue;
                CharSequence label = r.loadLabel(pm);
                byPkg.put(a.packageName, new App(a.packageName, label == null ? a.packageName : label.toString(), a, pm));
            }
        }
        List<App> out = new ArrayList<>(byPkg.values());
        Collator col = Collator.getInstance(Locale.getDefault());
        out.sort((x, y) -> col.compare(x.label, y.label));
        return out;
    }

    static App find(List<App> apps, String pkg) {
        for (App a : apps) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    static void open(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) { Toast.makeText(c, c.getString(R.string.cannot_open), Toast.LENGTH_SHORT).show(); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(c, c.getString(R.string.open_failed), Toast.LENGTH_SHORT).show();
        }
    }

    static void info(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (ActivityNotFoundException e) { /* nessuna pagina info */ }
    }

    /** Apre un'azione di sistema, o la prima alternativa che la TV conosce. */
    static boolean action(Context c, String... actions) {
        for (String a : actions) {
            Intent i = new Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(c.getPackageManager()) == null) continue;
            try { c.startActivity(i); return true; } catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        return false;
    }
}
