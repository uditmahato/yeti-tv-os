package tv.yeti.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.NetworkCapabilities;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Built-in Wi-Fi picker: on/off, current connection details, live network list, password entry.
 * Left column 600 wide, list fills the rest; all sizes on the 1920x1080 design canvas.
 */
public class WifiActivity extends Activity {

    private static final long RESCAN_MS = 20000;

    private WifiManager wm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver receiver;

    private TextView toggleState, connTitle, connEyebrow, listStatus;
    private View toggleKnob, toggleTrack;
    private LinearLayout connDetails, list;
    private FrameLayout overlay;
    private EditText passwordField;
    private TextView passwordError;

    /** Network we are connecting to (null when idle), and the id we added for it (-1 if reusing a saved one). */
    private Wifi.Net pending;
    private int pendingAddedId = -1;
    private LinearLayout page;

    private final Runnable rescan = new Runnable() {
        @Override public void run() {
            try { if (wm.isWifiEnabled()) wm.startScan(); } catch (Exception ignored) { }
            handler.postDelayed(this, RESCAN_MS);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        wm = Wifi.manager(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());

        page = new LinearLayout(this);
        page.setPadding(Ui.safeX, Ui.safeY, Ui.safeX, Ui.safeY);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, Apps.fill());

        page.addView(leftColumn(), new LinearLayout.LayoutParams(Ui.s(600), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rlp.leftMargin = Ui.s(72);
        page.addView(rightColumn(), rlp);

        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(200, 0, 0, 0));
        overlay.setVisibility(View.GONE);
        overlay.setClickable(true);
        root.addView(overlay, Apps.fill());

        setContentView(root);
        toggleTrack.requestFocus();

        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { onWifiEvent(i); }
        };
        IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.RSSI_CHANGED_ACTION);
        registerReceiver(receiver, f);

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) { refresh(); rescan.run(); }

    @Override protected void onResume() {
        super.onResume();
        refresh();
        handler.removeCallbacks(rescan);
        rescan.run();
    }

    @Override protected void onPause() { handler.removeCallbacks(rescan); super.onPause(); }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) closeOverlay();
        else super.onBackPressed();
    }

    // ---------------- layout ----------------

    private View leftColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);

        col.addView(Ui.eyebrow(this, "Network", Ui.ACCENT));
        TextView title = Ui.text(this, "Wi-Fi", 60, Ui.TEXT, true);
        title.setPadding(0, Ui.s(12), 0, 0);
        col.addView(title);
        TextView sub = Ui.text(this, "Choose a network to get Yeti TV online.", 24, Ui.TEXT_DIM, false);
        sub.setPadding(0, Ui.s(12), 0, Ui.s(40));
        col.addView(sub);

        // On/off row with a drawn switch.
        LinearLayout toggle = new LinearLayout(this);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(Ui.s(32), 0, Ui.s(24), 0);
        toggle.setFocusable(true);
        toggle.setClickable(true);
        TextView tl = Ui.text(this, "Wi-Fi", 26, Ui.TEXT, true);
        toggle.addView(tl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        toggleState = Ui.text(this, "", 22, Ui.TEXT_DIM, false);
        toggleState.setPadding(0, 0, Ui.s(18), 0);
        toggle.addView(toggleState);
        FrameLayout track = new FrameLayout(this);
        toggleKnob = new View(this);
        toggleKnob.setBackground(Ui.solid(Color.WHITE, 999));
        track.addView(toggleKnob, new FrameLayout.LayoutParams(Ui.s(30), Ui.s(30), Gravity.CENTER_VERTICAL | Gravity.START));
        track.setPadding(Ui.s(5), 0, Ui.s(5), 0);
        toggle.addView(track, new LinearLayout.LayoutParams(Ui.s(72), Ui.s(40)));
        toggle.setOnClickListener(v -> {
            boolean on = !wm.isWifiEnabled();
            try { wm.setWifiEnabled(on); } catch (Exception ignored) { }
            showToggle(on);
        });
        toggleTrack = toggle;
        track.setTag("track");
        Ui.pillFocus(toggle, 1.03f, new TextView[]{tl}, null);
        col.addView(toggle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(76)));

        // Current connection card.
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.s(36), Ui.s(30), Ui.s(36), Ui.s(26));
        card.setBackground(Ui.solid(Ui.CARD, 28));
        connEyebrow = Ui.eyebrow(this, "", Ui.GOOD);
        card.addView(connEyebrow);
        connTitle = Ui.text(this, "", 36, Ui.TEXT, true);
        connTitle.setSingleLine(true);
        connTitle.setEllipsize(TextUtils.TruncateAt.END);
        connTitle.setPadding(0, Ui.s(10), 0, Ui.s(12));
        card.addView(connTitle);
        connDetails = new LinearLayout(this);
        connDetails.setOrientation(LinearLayout.VERTICAL);
        card.addView(connDetails);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.s(24);
        col.addView(card, clp);

        LinearLayout actions = new LinearLayout(this);
        actions.setClipChildren(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(60));
        alp.rightMargin = Ui.s(16);
        actions.addView(pill("Scan again", () -> { try { wm.startScan(); } catch (Exception ignored) { } listStatus.setText("Scanning…"); }), alp);
        actions.addView(pill("Advanced settings", () -> Ui.openSettings(this, new Intent(Settings.ACTION_WIFI_SETTINGS))),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(60)));
        LinearLayout.LayoutParams aclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aclp.topMargin = Ui.s(28);
        col.addView(actions, aclp);
        return col;
    }

    private View rightColumn() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.BOTTOM);
        head.addView(Ui.text(this, "Available networks", 30, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        listStatus = Ui.text(this, "", 22, Ui.TEXT_DIM, false);
        head.addView(listStatus);
        head.setPadding(Ui.s(8), Ui.s(8), Ui.s(8), Ui.s(20));
        col.addView(head);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(true);
        scroll.setFadingEdgeLength(Ui.s(40));
        scroll.setVerticalFadingEdgeEnabled(true);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return col;
    }

    private LinearLayout pill(String label, Runnable click) {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER);
        p.setPadding(Ui.s(30), 0, Ui.s(30), 0);
        p.setFocusable(true);
        p.setClickable(true);
        TextView t = Ui.text(this, label, 22, Ui.TEXT, true);
        p.addView(t);
        p.setOnClickListener(v -> click.run());
        Ui.pillFocus(p, 1.05f, new TextView[]{t}, null);
        return p;
    }

    /** List row: Google TV settings style — transparent, turns white with dark text when focused. */
    private View netRow(Wifi.Net n) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.s(28), 0, Ui.s(28), 0);
        r.setFocusable(true);
        r.setClickable(true);
        r.setTag("net:" + n.ssid);
        r.setBackground(Ui.solid(Color.TRANSPARENT, 20));

        ImageView glyph = new ImageView(this);
        glyph.setImageDrawable(Ui.wifiGlyph(Math.max(1, n.level), n.security != Wifi.OPEN, Ui.TEXT));
        r.addView(glyph, new LinearLayout.LayoutParams(Ui.s(38), Ui.s(38)));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(Ui.s(26), 0, 0, 0);
        TextView name = Ui.text(this, n.ssid, 26, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        txt.addView(name);
        String state = n.connected ? "Connected" : (pending != null && n.ssid.equals(pending.ssid)) ? "Connecting…"
                : n.saved ? "Saved" : n.security == Wifi.OPEN ? "Open network" : "Secured · " + Wifi.securityName(n.security);
        int stateColor = n.connected ? Ui.GOOD : Ui.TEXT_DIM;
        TextView sub = Ui.text(this, state, 20, stateColor, false);
        sub.setPadding(0, Ui.s(6), 0, 0);
        txt.addView(sub);
        r.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView band = Ui.text(this, n.freq >= 5900 ? "6 GHz" : n.freq >= 4900 ? "5 GHz" : "2.4 GHz", 20, Ui.TEXT_DIM, false);
        r.addView(band);

        r.setOnFocusChangeListener((v, has) -> {
            ((GradientDrawable) v.getBackground()).setColor(has ? Ui.FOCUS : Color.TRANSPARENT);
            name.setTextColor(has ? Ui.TEXT_ON_FOCUS : Ui.TEXT);
            sub.setTextColor(has ? (n.connected ? Color.rgb(20, 130, 50) : Color.rgb(80, 86, 94)) : stateColor);
            band.setTextColor(has ? Color.rgb(80, 86, 94) : Ui.TEXT_DIM);
            glyph.setImageDrawable(Ui.wifiGlyph(Math.max(1, n.level), n.security != Wifi.OPEN, has ? Ui.TEXT_ON_FOCUS : Ui.TEXT));
        });
        r.setOnClickListener(v -> onNetwork(n));
        r.setOnLongClickListener(v -> { if (n.saved) forget(n); return true; });
        return r;
    }

    // ---------------- state ----------------

    private void showToggle(boolean on) {
        toggleState.setText(on ? "On" : "Off");
        View track = toggleTrack.findViewWithTag("track");
        track.setBackground(Ui.solid(on ? Ui.ACCENT : Color.argb(80, 255, 255, 255), 999));
        ((FrameLayout.LayoutParams) toggleKnob.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
        toggleKnob.requestLayout();
    }

    private void refresh() {
        boolean on = wm.isWifiEnabled();
        showToggle(on);
        updateConnection();
        if (!on) {
            list.removeAllViews();
            listStatus.setText("");
            list.addView(message("Wi-Fi is off", "Turn it on to see networks nearby."));
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            list.removeAllViews();
            list.addView(message("Permission needed", "Allow location access so Yeti TV can list nearby Wi-Fi networks."));
            return;
        }
        List<Wifi.Net> nets = Wifi.networks(wm);
        View f = getCurrentFocus();
        Object focusTag = f == null ? null : f.getTag();
        list.removeAllViews();
        for (Wifi.Net n : nets) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(92));
            lp.bottomMargin = Ui.s(6);
            list.addView(netRow(n), lp);
        }
        if (nets.isEmpty()) list.addView(message("Looking for networks…", "This can take a few seconds."));
        listStatus.setText(pending != null ? "Connecting to " + pending.ssid + "…" : nets.size() + (nets.size() == 1 ? " network" : " networks"));
        if (focusTag instanceof String && ((String) focusTag).startsWith("net:")) {
            View again = list.findViewWithTag(focusTag);
            if (again != null) again.requestFocus();
            else if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
        }
    }

    private View message(String title, String body) {
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setPadding(Ui.s(28), Ui.s(30), Ui.s(28), Ui.s(30));
        m.addView(Ui.text(this, title, 28, Ui.TEXT, true));
        TextView b = Ui.text(this, body, 22, Ui.TEXT_DIM, false);
        b.setPadding(0, Ui.s(10), 0, 0);
        m.addView(b);
        return m;
    }

    private void updateConnection() {
        connDetails.removeAllViews();
        WifiInfo wi = wm.getConnectionInfo();
        boolean connected = wm.isWifiEnabled() && wi != null && wi.getNetworkId() != -1 && wi.getSupplicantState() == SupplicantState.COMPLETED;
        if (!connected) {
            connEyebrow.setText("NOT CONNECTED");
            connEyebrow.setTextColor(Ui.TEXT_DIM);
            connTitle.setText(wm.isWifiEnabled() ? "Pick a network" : "Wi-Fi is off");
            detail("Ethernet", Wifi.ipv4(this, NetworkCapabilities.TRANSPORT_ETHERNET) != null ? "Connected" : "Unplugged");
            return;
        }
        int level = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
        String ssid = Wifi.cleanSsid(wi.getSSID());
        connEyebrow.setText("CONNECTED");
        connEyebrow.setTextColor(Ui.GOOD);
        connTitle.setText(ssid != null ? ssid : "Wi-Fi");
        detail("Signal", Wifi.signalName(level) + "  (" + wi.getRssi() + " dBm)");
        String ip = Wifi.ipv4(this, NetworkCapabilities.TRANSPORT_WIFI);
        detail("IP address", ip != null ? ip : "Getting address…");
        detail("Speed", wi.getLinkSpeed() + " Mbps");
        detail("Band", String.format(Locale.US, "%s  ·  channel %d", wi.getFrequency() >= 4900 ? "5 GHz" : "2.4 GHz", channel(wi.getFrequency())));
    }

    private static int channel(int f) {
        if (f == 2484) return 14;
        if (f < 2484) return (f - 2407) / 5;
        return (f - 5000) / 5;
    }

    private void detail(String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, Ui.s(12), 0, Ui.s(12));
        r.addView(Ui.text(this, k, 22, Ui.TEXT_DIM, false), new LinearLayout.LayoutParams(Ui.s(170), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView val = Ui.text(this, v, 22, Ui.TEXT, true);
        val.setSingleLine(true);
        r.addView(val, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        connDetails.addView(r);
    }

    // ---------------- connecting ----------------

    private void onNetwork(Wifi.Net n) {
        if (n.connected) return;
        if (n.security == Wifi.EAP) { handOff(n, "Enterprise networks are set up in Settings."); return; }
        if (n.saved || n.security == Wifi.OPEN) {
            startConnect(n, null);
            return;
        }
        askPassword(n, null);
    }

    private void startConnect(Wifi.Net n, String password) {
        int id = Wifi.connect(wm, n, password);
        if (id == -1) {
            handOff(n, n.saved ? "“" + n.ssid + "” was saved by Settings, so Settings will connect it." : "Android refused this network.");
            return;
        }
        pending = n;
        pendingAddedId = password != null || !n.saved ? id : -1;
        refresh();
    }

    /** Fallback when Android won't let this app manage the network: open Android TV Settings' Wi-Fi page. */
    private void handOff(Wifi.Net n, String why) {
        showDialog("Opening Settings", why, "Open Settings", () -> Ui.openSettings(this, new Intent(Settings.ACTION_WIFI_SETTINGS)));
    }

    private void forget(Wifi.Net n) {
        showDialog("Forget “" + n.ssid + "”?", "Yeti TV will stop connecting to this network automatically.", "Forget", () -> {
            if (wm.removeNetwork(n.netId)) refresh();
            else handOff(n, "This network was saved by Settings, so it can only be forgotten there.");
        });
    }

    private void onWifiEvent(Intent i) {
        String a = i.getAction();
        if (WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(a) && pending != null
                && i.getIntExtra(WifiManager.EXTRA_SUPPLICANT_ERROR, 0) == WifiManager.ERROR_AUTHENTICATING) {
            Wifi.Net failed = pending;
            if (pendingAddedId != -1) wm.removeNetwork(pendingAddedId);
            pending = null;
            pendingAddedId = -1;
            refresh();
            if (overlay.getVisibility() != View.VISIBLE) askPassword(failed, "Wrong password. Try again.");
            return;
        }
        if (pending != null) {
            WifiInfo wi = wm.getConnectionInfo();
            if (wi != null && wi.getSupplicantState() == SupplicantState.COMPLETED && pending.ssid.equals(Wifi.cleanSsid(wi.getSSID()))) {
                pending = null;
                pendingAddedId = -1;
            }
        }
        refresh();
    }

    // ---------------- overlays ----------------

    private LinearLayout panel(String title, String body) { return panel(title, body, false); }

    /** {@code top}: sit above the on-screen keyboard (which covers the lower ~40% of the screen) instead of centring. */
    private LinearLayout panel(String title, String body, boolean top) {
        overlay.removeAllViews();
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(Ui.s(56), Ui.s(48), Ui.s(56), Ui.s(48));
        p.setBackground(Ui.solid(Ui.CARD, 32));
        p.setClipChildren(false);
        p.setClipToPadding(false);
        TextView t = Ui.text(this, title, 38, Ui.TEXT, true);
        p.addView(t);
        if (body != null) {
            TextView bd = Ui.text(this, body, 24, Ui.TEXT_DIM, false);
            bd.setPadding(0, Ui.s(14), 0, 0);
            bd.setLineSpacing(0, 1.15f);
            p.addView(bd);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(Ui.s(860), ViewGroup.LayoutParams.WRAP_CONTENT,
                top ? Gravity.TOP | Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        lp.topMargin = top ? Ui.s(70) : 0;
        overlay.addView(p, lp);
        overlay.setVisibility(View.VISIBLE);
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        return p;
    }

    private LinearLayout buttons(LinearLayout p) {
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.s(36);
        p.addView(row, lp);
        return row;
    }

    private View addButton(LinearLayout row, String label, Runnable click) {
        View b = pill(label, click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(60));
        lp.rightMargin = Ui.s(16);
        row.addView(b, lp);
        return b;
    }

    private void showDialog(String title, String body, String ok, Runnable action) {
        LinearLayout p = panel(title, body);
        LinearLayout row = buttons(p);
        View first = addButton(row, ok, () -> { closeOverlay(); action.run(); });
        addButton(row, "Cancel", this::closeOverlay);
        first.requestFocus();
    }

    private void askPassword(Wifi.Net n, String error) {
        LinearLayout p = panel(n.ssid, "Enter the password  ·  " + Wifi.securityName(n.security), true);

        passwordField = new EditText(this);
        passwordField.setSingleLine(true);
        passwordField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordField.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        Ui.textSize(passwordField, 28);
        final android.graphics.Typeface font = Ui.text(this, "", 28, Ui.TEXT, false).getTypeface();
        passwordField.setTypeface(font);
        passwordField.setTextColor(Ui.TEXT);
        passwordField.setHintTextColor(Ui.TEXT_DIM);
        passwordField.setHint("Password");
        passwordField.setPadding(Ui.s(28), 0, Ui.s(28), 0);
        GradientDrawable fieldBg = Ui.solid(Ui.CARD_HI, 18);
        passwordField.setBackground(fieldBg);
        passwordField.setOnFocusChangeListener((v, has) -> fieldBg.setStroke(Ui.s(4), has ? Ui.FOCUS : Color.TRANSPARENT));
        passwordField.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { submitPassword(n); return true; }
            return false;
        });
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(76));
        flp.topMargin = Ui.s(32);
        p.addView(passwordField, flp);

        passwordError = Ui.text(this, error == null ? "" : error, 22, Color.rgb(255, 120, 110), true);
        passwordError.setPadding(Ui.s(4), Ui.s(14), 0, 0);
        passwordError.setVisibility(error == null ? View.GONE : View.VISIBLE);
        p.addView(passwordError);

        LinearLayout row = buttons(p);
        addButton(row, "Connect", () -> submitPassword(n));
        final View[] show = new View[1];
        show[0] = addButton(row, "Show password", () -> {
            boolean hidden = (passwordField.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == InputType.TYPE_TEXT_VARIATION_PASSWORD;
            passwordField.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            passwordField.setTypeface(font);
            passwordField.setSelection(passwordField.getText().length());
            ((TextView) ((LinearLayout) show[0]).getChildAt(0)).setText(hidden ? "Hide password" : "Show password");
        });
        addButton(row, "Cancel", this::closeOverlay);

        passwordField.requestFocus();
        passwordField.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(passwordField, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private void submitPassword(Wifi.Net n) {
        String pw = passwordField.getText().toString();
        boolean wep = n.security == Wifi.WEP;
        if ((!wep && pw.length() < 8) || pw.isEmpty()) {
            passwordError.setText(wep ? "Enter the WEP key." : "Wi-Fi passwords are at least 8 characters.");
            passwordError.setVisibility(View.VISIBLE);
            return;
        }
        closeOverlay();
        startConnect(n, pw);
    }

    private void closeOverlay() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && passwordField != null) imm.hideSoftInputFromWindow(passwordField.getWindowToken(), 0);
        overlay.removeAllViews();
        overlay.setVisibility(View.GONE);
        page.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        passwordField = null;
        if (list.getChildCount() > 0 && list.getChildAt(0).isFocusable()) list.getChildAt(0).requestFocus();
        else toggleTrack.requestFocus();
    }
}
