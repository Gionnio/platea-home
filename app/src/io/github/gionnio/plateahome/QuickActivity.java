package io.github.gionnio.plateahome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The quick settings panel on its own, over whatever is on screen: opened by the remote's gear through KeyService.
 * The window is translucent and lives in its own task, so the app behind stays where it was; Back (or the gear
 * again) closes the panel and goes back to it.
 */
public final class QuickActivity extends Activity implements Panels.Host {
    private Ui ui;
    private Palette pal;
    private FrameLayout root;
    private View sheet;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ui = new Ui(this);
        pal = Store.pal(this);
        root = new FrameLayout(this);
        // A light veil, so the panel reads over a bright video too; the app stays visible behind it.
        root.setBackgroundColor((pal.ground & 0x00FFFFFF) | 0x8C000000);
        // Never focusable or clickable: a clickable view is focusable on Android 8+, and the whole screen would take
        // the focus away from the panel's tiles.
        root.setFocusable(false);
        setContentView(root);
        show(Panels.quick(this, ui, pal, this));
    }

    private void show(Sheet s) {
        if (sheet != null) root.removeView(sheet);
        sheet = s.box;
        root.addView(sheet, s.params());
        sheet.post(() -> { if (sheet != null) sheet.requestFocus(View.FOCUS_DOWN); });
    }

    /** The gear pressed again while the panel is open closes it. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Opening a setting or another app leaves the panel: coming back lands on that app, not on the panel. */
    @Override protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations()) finish();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void openNetwork() { show(Panels.network(this, ui, pal)); }

    @Override public void openInputs() { show(Panels.inputs(this, ui, pal, Config.get(this).liveTv, this)); }

    @Override public void close() { finish(); }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(0, 0);
    }
}
