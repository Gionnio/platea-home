package com.gathin.tvhome;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Platea Home: the line under the app's name on the top shelf ("La tua libreria, in direct play.").
 * Read once from res/values/platea_home.xml, entries "package|line". An app with no entry keeps the
 * launcher's own line ("Last opened").
 */
final class PlateaHomeText {
    private static Map<String, String> lines;

    private PlateaHomeText() {}

    static String line(Context context, String pkg) {
        if (pkg == null) return null;
        if (lines == null) {
            Map<String, String> map = new HashMap<>();
            for (String item : context.getResources().getStringArray(R.array.platea_home_lines)) {
                int bar = item.indexOf('|');
                if (bar > 0) map.put(item.substring(0, bar), item.substring(bar + 1));
            }
            lines = map;
        }
        return lines.get(pkg);
    }
}
