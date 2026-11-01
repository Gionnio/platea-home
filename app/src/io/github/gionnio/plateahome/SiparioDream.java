package io.github.gionnio.plateahome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

/** Sipario: solo l'ora, una linea e la data, su nero. Si sposta un poco ogni minuto per non segnare il pannello. */
public final class SiparioDream extends DreamService {
    private TextView clock, date;
    private LinearLayout box;
    private Ui ui;
    private final Random random = new Random();
    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { update(true); }
    };

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        ui = new Ui(this);
        Palette pal = Palette.of(Config.get(this).siparioPalette);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.ground);
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        clock = ui.text(Ui.clock(), 220, pal.text, 300);
        clock.setLetterSpacing(-0.04f);
        clock.setEllipsize(null);
        View rule = new View(this);
        rule.setBackgroundColor(pal.line);
        date = ui.text(Ui.today(this), 30, pal.sec, 400);
        date.setEllipsize(null);
        // In una LinearLayout verticale i figli senza parametri sono larghi quanto il genitore: qui devono adattarsi al testo.
        box.addView(clock, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ui.px(72), Math.max(1, ui.px(2)));
        rp.topMargin = ui.px(22);
        rp.bottomMargin = ui.px(22);
        box.addView(rule, rp);
        box.addView(date, new LinearLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        root.addView(box, bp);
        setContentView(root);
        registerReceiver(tick, new IntentFilter(Intent.ACTION_TIME_TICK));
        update(false);
    }

    @Override public void onDetachedFromWindow() {
        try { unregisterReceiver(tick); } catch (IllegalArgumentException ignored) { }
        super.onDetachedFromWindow();
    }

    private void update(boolean drift) {
        clock.setText(Ui.clock());
        date.setText(Ui.today(this));
        if (drift) {
            box.animate().translationX((random.nextFloat() - 0.5f) * ui.px(240))
                    .translationY((random.nextFloat() - 0.5f) * ui.px(160)).setDuration(4000).start();
        }
    }
}
