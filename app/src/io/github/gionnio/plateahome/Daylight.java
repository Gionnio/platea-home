package io.github.gionnio.plateahome;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Alba e tramonto calcolati sulla TV, senza rete e senza posizione: le coordinate vengono dal fuso orario della TV
 * (la sua città di riferimento, o una stima dal fuso). Formula NOAA semplificata, errore di qualche minuto più quello
 * della città: in Italia circa 10-30 minuti.
 */
final class Daylight {
    private Daylight() {}

    /** Fuso → latitudine, longitudine della città di riferimento. Gli altri fusi usano la stima in coords(). */
    private static final String ZONES =
            "Europe/Rome:41.9,12.5;Europe/London:51.5,-0.1;Europe/Dublin:53.3,-6.3;Europe/Lisbon:38.7,-9.1;"
            + "Europe/Madrid:40.4,-3.7;Europe/Paris:48.9,2.4;Europe/Brussels:50.8,4.4;Europe/Amsterdam:52.4,4.9;"
            + "Europe/Berlin:52.5,13.4;Europe/Zurich:47.4,8.5;Europe/Vienna:48.2,16.4;Europe/Prague:50.1,14.4;"
            + "Europe/Warsaw:52.2,21.0;Europe/Budapest:47.5,19.0;Europe/Athens:38.0,23.7;Europe/Bucharest:44.4,26.1;"
            + "Europe/Stockholm:59.3,18.1;Europe/Oslo:59.9,10.8;Europe/Copenhagen:55.7,12.6;Europe/Helsinki:60.2,24.9;"
            + "Europe/Istanbul:41.0,29.0;Europe/Moscow:55.8,37.6;Europe/Kiev:50.5,30.5;Europe/Kyiv:50.5,30.5;"
            + "Europe/Malta:35.9,14.5;Europe/San_Marino:43.9,12.4;Europe/Vatican:41.9,12.5;"
            + "America/New_York:40.7,-74.0;America/Chicago:41.9,-87.6;America/Denver:39.7,-105.0;"
            + "America/Los_Angeles:34.1,-118.2;America/Phoenix:33.4,-112.1;America/Toronto:43.7,-79.4;"
            + "America/Vancouver:49.3,-123.1;America/Mexico_City:19.4,-99.1;America/Sao_Paulo:-23.5,-46.6;"
            + "America/Argentina/Buenos_Aires:-34.6,-58.4;America/Bogota:4.7,-74.1;America/Lima:-12.0,-77.0;"
            + "America/Santiago:-33.4,-70.7;Asia/Dubai:25.2,55.3;Asia/Kolkata:22.6,77.2;Asia/Calcutta:22.6,77.2;"
            + "Asia/Shanghai:31.2,121.5;Asia/Hong_Kong:22.3,114.2;Asia/Tokyo:35.7,139.7;Asia/Seoul:37.6,127.0;"
            + "Asia/Singapore:1.3,103.8;Asia/Bangkok:13.8,100.5;Asia/Jakarta:-6.2,106.8;Asia/Manila:14.6,121.0;"
            + "Asia/Karachi:24.9,67.0;Asia/Dhaka:23.8,90.4;Asia/Riyadh:24.7,46.7;Asia/Jerusalem:31.8,35.2;"
            + "Africa/Cairo:30.0,31.2;Africa/Johannesburg:-26.2,28.0;Africa/Lagos:6.5,3.4;Africa/Casablanca:33.6,-7.6;"
            + "Australia/Sydney:-33.9,151.2;Australia/Melbourne:-37.8,145.0;Australia/Perth:-32.0,115.9;"
            + "Pacific/Auckland:-36.8,174.8";

    static double[] coords(TimeZone tz) {
        String id = tz.getID();
        int i = ZONES.indexOf(id + ":");
        if (i >= 0 && (i == 0 || ZONES.charAt(i - 1) == ';')) {
            int a = i + id.length() + 1, b = ZONES.indexOf(';', a);
            String[] ll = ZONES.substring(a, b < 0 ? ZONES.length() : b).split(",");
            return new double[]{Double.parseDouble(ll[0]), Double.parseDouble(ll[1])};
        }
        double lon = tz.getRawOffset() / 3600000.0 * 15;
        double lat = 45;
        if (id.startsWith("Australia/") || id.startsWith("Pacific/Auckland") || id.startsWith("America/Argentina")
                || id.equals("America/Sao_Paulo") || id.equals("America/Santiago")) lat = -33;
        else if (id.startsWith("Africa/") || id.startsWith("Asia/") || id.startsWith("America/")) lat = id.startsWith("America/") ? 40 : 20;
        return new double[]{lat, lon};
    }

    /** Alba e tramonto di oggi, in minuti dalla mezzanotte locale. -1/-1 notte polare, 0/1440 giorno polare. */
    static int[] today(long now) {
        TimeZone tz = TimeZone.getDefault();
        Calendar cal = Calendar.getInstance(tz);
        cal.setTimeInMillis(now);
        double[] ll = coords(tz);
        double rad = Math.PI / 180, g = 2 * Math.PI / 365 * (cal.get(Calendar.DAY_OF_YEAR) - 1);
        double eq = 229.18 * (0.000075 + 0.001868 * Math.cos(g) - 0.032077 * Math.sin(g)
                - 0.014615 * Math.cos(2 * g) - 0.040849 * Math.sin(2 * g));
        double decl = 0.006918 - 0.399912 * Math.cos(g) + 0.070257 * Math.sin(g) - 0.006758 * Math.cos(2 * g)
                + 0.000907 * Math.sin(2 * g) - 0.002697 * Math.cos(3 * g) + 0.00148 * Math.sin(3 * g);
        double x = Math.cos(90.833 * rad) / (Math.cos(ll[0] * rad) * Math.cos(decl)) - Math.tan(ll[0] * rad) * Math.tan(decl);
        if (x > 1) return new int[]{-1, -1};
        if (x < -1) return new int[]{0, 1440};
        double ha = Math.acos(x) / rad;
        double off = tz.getOffset(now) / 60000.0;
        int rise = (int) Math.round(720 - 4 * (ll[1] + ha) - eq + off);
        int set = (int) Math.round(720 - 4 * (ll[1] - ha) - eq + off);
        return new int[]{Math.max(0, rise), Math.min(1440, set)};
    }

    static boolean isDay(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int[] s = today(now);
        return m >= s[0] && m < s[1];
    }

    static String hhmm(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
