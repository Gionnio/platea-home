package io.github.gionnio.plateahome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Optional remote-button service. Some remotes have buttons that only the stock launcher handles (the gear on Google
 * TV sticks) or that open an app that was disabled (YouTube): with the stock launcher off they do nothing. This
 * service sees the key before the system does, and handles only the keys listed under "keys" in the TV's config:
 * every other key (D-pad, volume, Home, Back, the other app buttons) goes on untouched. It reads no screen content
 * and listens to no accessibility events. platea-home.sh turns it on only when the config has "keys".
 */
public final class KeyService extends AccessibilityService {

    @Override protected boolean onKeyEvent(KeyEvent e) {
        String target = Config.get(this).keys.get(e.getKeyCode());
        if (target == null) return false;
        // Consume both halves of the press; act once, on the first down.
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) run(target);
        return true;
    }

    private void run(String target) {
        if ("settings".equals(target)) {
            Apps.action(this, Settings.ACTION_SETTINGS);
        } else if ("quick".equals(target)) {
            // Over the app in front, which stays where it was.
            Intent i = new Intent(this, QuickActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (ActivityNotFoundException ignored) { }
        } else if ("home".equals(target)) {
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (ActivityNotFoundException ignored) { }
        } else if ("apps".equals(target)) {
            Intent i = new Intent(this, AppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (ActivityNotFoundException ignored) { }
        } else {
            Apps.open(this, target);
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { }
}
