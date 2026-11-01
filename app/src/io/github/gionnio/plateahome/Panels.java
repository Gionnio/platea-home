package io.github.gionnio.plateahome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** I contenuti dei pannelli laterali: rapido, Rete, Ingressi. Leggono lo stato vero della TV, senza rete. */
final class Panels {
    interface Host {
        void openNetwork();
        void openInputs();
        void close();
    }

    private static final int GREEN = 0xFF34D399;

    private Panels() {}

    // ---- pannello rapido ---------------------------------------------------------------------

    static Sheet quick(Context c, Ui ui, Palette pal, Host host) {
        Sheet s = new Sheet(c, ui, pal, "Impostazioni rapide", "Indietro per chiudere");
        Net net = Net.read(c);
        List<Input> inputs = inputs(c);
        // Il primo dispositivo acceso con un nome (CEC), altrimenti la prima porta collegata.
        String inputSub = "Nessun ingresso collegato";
        for (Input i : inputs) if (i.connected && !i.tuner && !i.name.equals(i.port)) { inputSub = i.name + " · " + i.port; break; }
        if (inputSub.startsWith("Nessun")) for (Input i : inputs) if (i.connected && !i.tuner) { inputSub = i.port; break; }
        String[][] tiles = {
                {"Immagine", "Modalità e regolazioni"},
                {"Audio", "Uscita e volume"},
                {"Rete", net.summary()},
                {"Ingressi", inputSub},
                {"Impostazioni TV", "Tutte le opzioni di sistema"},
                {"Launcher", "Dock, palette, salvaschermo"}
        };
        Runnable[] actions = {
                () -> Apps.action(c, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SETTINGS),
                () -> Apps.action(c, Settings.ACTION_SOUND_SETTINGS, Settings.ACTION_SETTINGS),
                host::openNetwork,
                host::openInputs,
                () -> Apps.action(c, Settings.ACTION_SETTINGS),
                () -> c.startActivity(new Intent(c, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        };
        for (int r = 0; r < 3; r++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                int i = r * 2 + k;
                Sheet.Item it = new Sheet.Item(c, ui, pal, tiles[i][0], tiles[i][1], 0, true);
                final Runnable run = actions[i];
                it.setOnClickListener(v -> run.run());
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ui.px(150), 1f);
                if (k == 0) p.rightMargin = ui.px(16);
                row.addView(it, p);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.bottomMargin = ui.px(16);
            s.box.addView(row, rp);
        }
        s.spacer();
        s.section("VOLUME");
        VolumeBar vol = new VolumeBar(c, ui, pal);
        s.box.addView(vol, new LinearLayout.LayoutParams(-1, ui.px(56)));
        return s;
    }

    /** Volume: Sinistra e Destra lo cambiano, come i tasti del telecomando. */
    static final class VolumeBar extends View {
        private final Ui ui;
        private final Palette pal;
        private final AudioManager audio;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        VolumeBar(Context c, Ui ui, Palette pal) {
            super(c);
            this.ui = ui; this.pal = pal;
            audio = c.getSystemService(AudioManager.class);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setContentDescription("Volume");
        }

        @Override public boolean onKeyDown(int code, KeyEvent e) {
            if (audio != null && (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        code == KeyEvent.KEYCODE_DPAD_RIGHT ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, 0);
                invalidate();
                return true;
            }
            return super.onKeyDown(code, e);
        }

        @Override protected void onFocusChanged(boolean g, int d, android.graphics.Rect p) {
            super.onFocusChanged(g, d, p);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            int max = audio == null ? 1 : Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            int now = audio == null ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            float h = getHeight(), w = getWidth(), cy = h / 2f, barH = ui.px(isFocused() ? 12 : 8);
            float right = w - ui.px(64);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(pal.line);
            c.drawRoundRect(0, cy - barH / 2, right, cy + barH / 2, barH / 2, barH / 2, paint);
            paint.setColor(isFocused() ? pal.focus : pal.text);
            c.drawRoundRect(0, cy - barH / 2, right * now / max, cy + barH / 2, barH / 2, barH / 2, paint);
            paint.setTextSize(ui.px(22));
            paint.setTypeface(ui.face(500));
            paint.setTextAlign(Paint.Align.RIGHT);
            paint.setColor(pal.text);
            c.drawText(String.valueOf(now), w, cy + ui.px(8), paint);
        }
    }

    // ---- Rete ----------------------------------------------------------------------------------

    static final class Net {
        String type = "";
        boolean internet;
        String ip = "", wifiName = "";
        int wifiMbps = -1;

        static Net read(Context c) {
            Net n = new Net();
            try {
                ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
                Network active = cm == null ? null : cm.getActiveNetwork();
                NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
                LinkProperties lp = active == null ? null : cm.getLinkProperties(active);
                if (caps != null) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) n.type = "Ethernet";
                    else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) n.type = "Wi-Fi";
                    else n.type = "Rete";
                    n.internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                }
                if (lp != null) for (LinkAddress a : lp.getLinkAddresses())
                    if (a.getAddress() instanceof Inet4Address) { n.ip = a.getAddress().getHostAddress(); break; }
                if ("Wi-Fi".equals(n.type)) {
                    WifiManager wm = c.getApplicationContext().getSystemService(WifiManager.class);
                    WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
                    if (wi != null) {
                        String ssid = wi.getSSID();
                        if (ssid != null && !ssid.contains("unknown ssid")) n.wifiName = ssid.replace("\"", "");
                        n.wifiMbps = wi.getLinkSpeed();
                    }
                }
            } catch (SecurityException | NullPointerException ignored) { }
            return n;
        }

        String summary() {
            if (type.isEmpty()) return "Non connessa";
            return type + (internet ? " · connessa" : " · senza internet");
        }
    }

    static Sheet network(Context c, Ui ui, Palette pal) {
        Net n = Net.read(c);
        Sheet s = new Sheet(c, ui, pal, "Rete", n.type.isEmpty() ? "Non connessa" : n.internet ? "Connessa a internet" : "Connessa, ma senza internet");

        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(ui.px(24), ui.px(22), ui.px(24), ui.px(22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pal.tile);
        bg.setCornerRadius(ui.px(22));
        bg.setStroke(Math.max(1, ui.px(1)), pal.line);
        card.setBackground(bg);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView kind = ui.text(n.type.isEmpty() ? "Nessuna rete" : n.type, 25, pal.text, 600);
        top.addView(kind, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!n.type.isEmpty()) {
            TextView pill = ui.text(n.internet ? "ATTIVA" : "SENZA INTERNET", 15, n.internet ? GREEN : 0xFFF59E0B, 700);
            pill.setLetterSpacing(0.1f);
            top.addView(pill);
        }
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        addFact(c, ui, pal, card, "Indirizzo", n.ip.isEmpty() ? "—" : n.ip);
        if ("Wi-Fi".equals(n.type)) {
            addFact(c, ui, pal, card, "Rete", n.wifiName.isEmpty() ? "nome non disponibile" : n.wifiName);
            if (n.wifiMbps > 0) addFact(c, ui, pal, card, "Velocità", n.wifiMbps + " Mbit/s");
        } else if ("Ethernet".equals(n.type)) {
            addFact(c, ui, pal, card, "Collegamento", "via cavo");
        }
        s.box.addView(card, new LinearLayout.LayoutParams(-1, -2));

        s.section("WI-FI");
        s.row("Reti Wi-Fi", "Scegli o cambia rete nelle impostazioni della TV", 0,
                () -> Apps.action(c, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS));
        s.spacer();
        s.row("Impostazioni di rete della TV", null, 0,
                () -> Apps.action(c, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS));
        return s;
    }

    private static void addFact(Context c, Ui ui, Palette pal, LinearLayout card, String k, String v) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView a = ui.text(k, 19, pal.ter, 400);
        r.addView(a, new LinearLayout.LayoutParams(ui.px(170), -2));
        TextView b = ui.text(v, 19, pal.sec, 500);
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = ui.px(12);
        card.addView(r, p);
    }

    // ---- Ingressi ------------------------------------------------------------------------------

    static final class Input {
        String id, name, port;
        boolean tuner, connected, standby;
    }

    /** Gli ingressi della TV: un'unica voce per i sintonizzatori, gli HDMI col nome del dispositivo CEC. */
    static List<Input> inputs(Context c) {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = c.getSystemService(TvInputManager.class);
        if (tim == null) return out;
        List<TvInputInfo> all;
        try { all = tim.getTvInputList(); } catch (RuntimeException e) { return out; }
        Map<String, TvInputInfo> children = new HashMap<>();
        for (TvInputInfo i : all) if (i.getParentId() != null) children.put(i.getParentId(), i);
        boolean tunerAdded = false;
        int hdmi = 0;
        for (TvInputInfo i : all) {
            if (i.getParentId() != null || i.isHidden(c)) continue;
            int type = i.getType();
            if (type == TvInputInfo.TYPE_TUNER) {
                if (tunerAdded) continue;
                tunerAdded = true;
                Input in = new Input();
                in.id = i.getId(); in.tuner = true; in.name = "TV in diretta"; in.port = "Antenna"; in.connected = true;
                out.add(0, in);
                continue;
            }
            if (type == TvInputInfo.TYPE_OTHER) continue;
            Input in = new Input();
            TvInputInfo child = children.get(i.getId());
            in.id = (child != null ? child : i).getId();
            CharSequence port = i.loadCustomLabel(c);
            if (port == null || port.length() == 0) port = i.loadLabel(c);
            in.port = port == null ? "Ingresso" : port.toString();
            if (type == TvInputInfo.TYPE_HDMI) hdmi++;
            CharSequence dev = child == null ? null : child.loadLabel(c);
            in.name = dev != null && dev.length() > 0 ? dev.toString() : in.port;
            int state;
            try { state = tim.getInputState(in.id); } catch (RuntimeException e) { state = TvInputManager.INPUT_STATE_CONNECTED; }
            in.connected = state == TvInputManager.INPUT_STATE_CONNECTED || child != null;
            in.standby = state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
            out.add(in);
        }
        return out;
    }

    static Sheet inputs(Context c, Ui ui, Palette pal, String liveTv, Host host) {
        Sheet s = new Sheet(c, ui, pal, "Ingressi", "I dispositivi si riconoscono via HDMI-CEC");
        List<Input> list = inputs(c);
        final List<Sheet.Item> hiddenRows = new ArrayList<>();
        for (Input in : list) {
            String sub = in.tuner ? "Antenna" : in.name.equals(in.port) ? (in.connected ? "collegato" : in.standby ? "in standby" : "niente collegato")
                    : in.port + (in.connected ? " · acceso" : in.standby ? " · in standby" : "");
            int dot = in.connected ? GREEN : pal.line;
            final Input target = in;
            Sheet.Item row = s.row(in.name, sub, dot, () -> { host.close(); switchTo(c, target, liveTv); });
            if (!in.connected && !in.standby && !in.tuner) { row.setVisibility(View.GONE); hiddenRows.add(row); }
        }
        if (list.isEmpty()) s.row("Nessun ingresso", "Questa TV non espone ingressi ad Android", 0, () -> { });
        if (!hiddenRows.isEmpty()) {
            s.spacer();
            final Sheet.Item more = s.row("Altri ingressi (" + hiddenRows.size() + ")", "Porte senza niente collegato", 0, () -> { });
            more.setOnClickListener(v -> {
                for (Sheet.Item r : hiddenRows) r.setVisibility(View.VISIBLE);
                more.setVisibility(View.GONE);
                hiddenRows.get(0).requestFocus();
            });
        }
        return s;
    }

    private static void switchTo(Context c, Input in, String liveTv) {
        if (in.tuner) {
            if (!liveTv.isEmpty()) { Apps.open(c, liveTv); return; }
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelsUriForInput(in.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { c.startActivity(i); } catch (ActivityNotFoundException ignored) { }
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(in.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(i); } catch (ActivityNotFoundException e) {
            if (!liveTv.isEmpty()) Apps.open(c, liveTv);
        }
    }
}
