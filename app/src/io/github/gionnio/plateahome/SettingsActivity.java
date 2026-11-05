package io.github.gionnio.plateahome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Impostazioni del launcher, stile tvOS: a sinistra la voce selezionata in grande, a destra l'elenco. */
public final class SettingsActivity extends Activity {
    private static final String[] PALETTES = {"Grafite", "Notte blu", "Giorno"};
    /** Le scelte che OK fa scorrere: le tre palette, poi le due automatiche. */
    private static final String[] CHOICES = {"Grafite", "Notte blu", "Giorno", "Auto Grafite", "Auto Notte blu"};
    private Ui ui;
    private Palette pal;
    private TextView bigTitle, bigText;
    private LinearLayout swatches, list;
    private String paletteName, choice;
    /** La voce col focus prima di ricreare la pagina (cambio palette): ci si torna, così OK scorre le palette di fila. */
    private int restoreFocus = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (b != null) restoreFocus = b.getInt("focus", -1);
        ui = new Ui(this);
        choice = Store.palette(this);
        paletteName = Store.effective(this);
        pal = Palette.of(paletteName);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.ground);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow = ui.text(getString(R.string.settings_eyebrow), 18, pal.ter, 600);
        eyebrow.setLetterSpacing(0.18f);
        bigTitle = ui.text("", 72, pal.text, 600);
        bigTitle.setLetterSpacing(-0.03f);
        bigText = ui.text("", 26, pal.sec, 400);
        bigText.setSingleLine(false);
        bigText.setMaxLines(4);
        bigText.setLineSpacing(0, 1.3f);
        swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        left.addView(eyebrow, Sheet.wrap());
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = ui.px(28);
        left.addView(bigTitle, tp);
        LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(-1, -2);
        xp.topMargin = ui.px(28);
        left.addView(bigText, xp);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = ui.px(38);
        left.addView(swatches, sp);
        root.addView(left, ui.at(112, 0, ui.px(700), -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, ui.px(96), 0, ui.px(96));
        scroll.addView(list);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ui.px(888), -1);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = ui.px(920);
        root.addView(scroll, lp);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        fill();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("focus", list.indexOfChild(getCurrentFocus()));
    }

    private void fill() {
        View f = getCurrentFocus();
        int focus = Math.max(0, list.indexOfChild(f));
        if (restoreFocus >= 0) { focus = restoreFocus; restoreFocus = -1; }
        list.removeAllViews();
        List<Apps.App> apps = Apps.load(this);
        int dock = 0;
        for (String p : Store.dock(this)) if (Apps.find(apps, p) != null) dock++;
        int hidden = Store.hidden(this).size();
        String dream = Settings.Secure.getString(getContentResolver(), "screensaver_components");
        boolean sipario = dream != null && dream.startsWith(getPackageName());
        ResolveInfo home = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
        boolean isHome = home != null && home.activityInfo != null && getPackageName().equals(home.activityInfo.packageName);
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception e) { version = "?"; }

        header(getString(R.string.sec_home));
        row(getString(R.string.dock_apps), String.valueOf(dock), getString(R.string.dock_apps), getString(R.string.dock_apps_text), false,
                () -> startActivity(new Intent(this, AppsActivity.class)));
        row(getString(R.string.hidden_apps), String.valueOf(hidden), getString(R.string.hidden_apps), getString(R.string.hidden_apps_text), false,
                () -> startActivity(new Intent(this, AppsActivity.class)));
        String palText = getString(R.string.palette_text);
        if (Store.isAuto(choice)) {
            int[] sun = Daylight.today(System.currentTimeMillis());
            palText = getString(R.string.palette_auto_text, paletteLabel(Store.night(choice)), Daylight.hhmm(sun[0]), Daylight.hhmm(sun[1]));
        }
        row(getString(R.string.palette), choiceLabel(choice), getString(R.string.palette), palText, true, () -> {
            int i = 0;
            while (i < CHOICES.length && !CHOICES[i].equals(choice)) i++;
            Store.setPalette(this, CHOICES[(i + 1) % CHOICES.length]);
            recreate();
        });
        boolean foldersOn = Folders.on(this);
        row(getString(R.string.folders), foldersOn ? getString(R.string.state_on) : getString(R.string.state_off), getString(R.string.folders), getString(R.string.folders_text), false, () -> {
            Folders.setOn(this, !Folders.on(this));
            fill();
        });
        header(getString(R.string.sec_motion));
        boolean reduced = Store.reducedMotion(this);
        row(getString(R.string.animations), reduced ? getString(R.string.anim_reduced) : getString(R.string.anim_normal), getString(R.string.animations), getString(R.string.anim_text), false, () -> {
            Store.setReducedMotion(this, !Store.reducedMotion(this));
            fill();
        });
        header(getString(R.string.sec_screensaver));
        row(getString(R.string.sipario), sipario ? getString(R.string.state_on) : getString(R.string.state_off), getString(R.string.sipario), getString(R.string.sipario_text), false,
                () -> Apps.action(this, Settings.ACTION_DREAM_SETTINGS, Settings.ACTION_SETTINGS));
        header(getString(R.string.sec_system));
        row(getString(R.string.default_home), isHome ? "Platea Home" : getString(R.string.another_app), getString(R.string.default_home), getString(R.string.default_home_text), false,
                () -> Apps.action(this, Settings.ACTION_HOME_SETTINGS, Settings.ACTION_SETTINGS));
        row(getString(R.string.about), getString(R.string.version, version), "Platea Home", getString(R.string.about_text, version), false, () -> { });

        int i = Math.min(focus, list.getChildCount() - 1);
        View target = list.getChildAt(i);
        if (!(target != null && target.isFocusable())) target = list.getChildAt(1);
        target.requestFocus();
    }

    /** Nome della palette nella lingua della TV; il valore salvato resta quello del config (Grafite, Notte blu, Giorno). */
    private String paletteLabel(String key) {
        if ("Notte blu".equals(key)) return getString(R.string.palette_notte);
        if ("Giorno".equals(key)) return getString(R.string.palette_giorno);
        return getString(R.string.palette_grafite);
    }

    private String choiceLabel(String key) {
        if (!Store.isAuto(key)) return paletteLabel(key);
        return getString(R.string.palette_auto, paletteLabel(Store.night(key)));
    }

    private void header(String label) {
        TextView v = ui.text(label, 16, pal.ter, 600);
        v.setLetterSpacing(0.14f);
        LinearLayout.LayoutParams p = Sheet.wrap();
        p.leftMargin = ui.px(22);
        p.topMargin = ui.px(list.getChildCount() == 0 ? 0 : 18);
        p.bottomMargin = ui.px(8);
        list.addView(v, p);
    }

    private void row(String label, String value, String title, String text, boolean showSwatches, Runnable action) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(ui.px(22), 0, ui.px(22), 0);
        r.setFocusable(true);
        r.setFocusableInTouchMode(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(ui.px(18));
        bg.setColor(pal.surface);
        r.setBackground(bg);
        TextView a = ui.text(label, 24, pal.text, 500);
        TextView b = ui.text(value, 21, pal.ter, 400);
        b.setGravity(Gravity.END);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(b, Sheet.wrap());
        r.setOnFocusChangeListener((v, on) -> {
            bg.setColor(on ? pal.focus : pal.surface);
            a.setTextColor(on ? pal.onFocus : pal.text);
            b.setTextColor(on ? pal.onFocus : pal.ter);
            if (on) {
                bigTitle.setText(title);
                bigText.setText(text);
                showSwatches(showSwatches);
            }
        });
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, ui.px(62));
        p.bottomMargin = ui.px(10);
        list.addView(r, p);
    }

    private void showSwatches(boolean on) {
        swatches.removeAllViews();
        if (!on) return;
        for (String n : PALETTES) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(new Swatch(Palette.of(n), n.equals(paletteName)), new LinearLayout.LayoutParams(ui.px(180), ui.px(110)));
            TextView t = ui.text(paletteLabel(n), 20, n.equals(paletteName) ? pal.text : pal.ter, n.equals(paletteName) ? 600 : 400);
            LinearLayout.LayoutParams tp = Sheet.wrap();
            tp.topMargin = ui.px(12);
            col.addView(t, tp);
            LinearLayout.LayoutParams cp = Sheet.wrap();
            cp.rightMargin = ui.px(20);
            swatches.addView(col, cp);
        }
    }

    /** Anteprima di una palette: fondo, una riga di testo, una tessera. */
    private final class Swatch extends View {
        private final Palette p;
        private final boolean current;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Swatch(Palette p, boolean current) {
            super(SettingsActivity.this);
            this.p = p; this.current = current;
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), rad = ui.px(16);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(p.ground);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, rad, rad, paint);
            paint.setColor(p.text);
            c.drawRoundRect(ui.px(16), ui.px(16), ui.px(76), ui.px(26), ui.px(5), ui.px(5), paint);
            paint.setColor(p.tile);
            c.drawRoundRect(ui.px(16), h - ui.px(42), w - ui.px(16), h - ui.px(16), ui.px(7), ui.px(7), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(current ? ui.px(3) : Math.max(1, ui.px(1)));
            paint.setColor(current ? pal.focus : pal.line);
            float sw = paint.getStrokeWidth() / 2;
            r.set(sw, sw, w - sw, h - sw);
            c.drawRoundRect(r, rad, rad, paint);
        }
    }
}
