package io.github.gionnio.plateahome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Misure in pixel del mockup (TV 1920×1080) scalate sullo schermo reale, e Figtree ai vari pesi. */
final class Ui {
    final Context context;
    final float s;
    private static final SparseArray<Typeface> FACES = new SparseArray<>();

    Ui(Context context) {
        this.context = context;
        DisplayMetrics m = context.getResources().getDisplayMetrics();
        s = Math.max(m.widthPixels, m.heightPixels) / 1920f;
    }

    int px(float v) { return Math.round(v * s); }

    Typeface face(int weight) {
        synchronized (FACES) {
            Typeface t = FACES.get(weight);
            if (t == null) {
                try {
                    t = new Typeface.Builder(context.getAssets(), "fonts/figtree.ttf")
                            .setFontVariationSettings("'wght' " + weight).build();
                } catch (RuntimeException e) {
                    t = null;
                }
                if (t == null) t = Typeface.create("sans-serif", Typeface.NORMAL);
                FACES.put(weight, t);
            }
            return t;
        }
    }

    TextView text(String value, float size, int color, int weight) {
        TextView v = new TextView(context);
        v.setText(value);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s);
        v.setTextColor(color);
        v.setTypeface(face(weight));
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return v;
    }

    FrameLayout.LayoutParams at(float left, float top, int width, int height) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(width, height);
        p.gravity = Gravity.TOP | Gravity.START;
        p.leftMargin = px(left);
        p.topMargin = px(top);
        return p;
    }

    /** "Domenica 1 novembre". */
    static String today() {
        String d = new SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(new Date());
        return d.isEmpty() ? d : d.substring(0, 1).toUpperCase(Locale.ITALIAN) + d.substring(1);
    }

    static String clock() {
        return new SimpleDateFormat("HH:mm", Locale.ITALIAN).format(new Date());
    }
}
