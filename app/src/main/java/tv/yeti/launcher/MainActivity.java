package tv.yeti.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Yeti TV home, Google TV style. One fixed screen, no scrolling: everything is laid out on a 1920x1080
 * design canvas inside 5% TV-safe margins and scaled to the real screen (see Ui.s).
 *
 * Design budget (height 972 inside safe area):
 *   top bar 64 | 28 | hero (rest, 284 min) | 36 | "Your apps" 30+16+171 | "Get more to watch" 8+30+16+171 (hidden when
 *   every streaming app is installed — the hero grows instead) | 8 | "Settings" 30+16+64
 * App rows are 7 equal slots of 16:9 cards (226x127); the slot's extra 44px holds the app name shown under the
 * focused card.
 */
public class MainActivity extends Activity {

    /** label, candidate packages (first installed wins), Play Store id, accent colour. */
    private static final Object[][] WATCH = {
            {"YouTube", "com.google.android.youtube.tv,com.google.android.youtube", "com.google.android.youtube.tv", Color.rgb(255, 48, 48)},
            {"Netflix", "com.netflix.ninja,com.netflix.mediaclient", "com.netflix.ninja", Color.rgb(229, 9, 20)},
            {"Prime Video", "com.amazon.amazonvideo.livingroom,com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom", Color.rgb(0, 168, 225)},
            {"VLC", "org.videolan.vlc", "org.videolan.vlc", Color.rgb(255, 136, 0)},
            {"Browser", "com.phlox.tvwebbrowser,com.android.chrome,com.cloudmosa.puffinTV", "com.phlox.tvwebbrowser", Color.rgb(0, 200, 170)},
            {"Play Store", "com.android.vending", "com.android.vending", Color.rgb(52, 199, 89)},
    };
    private static final String PLAY = "com.android.vending";
    private static final int SLOTS = 7;
    private static final float CARD_H = 127, SLOT_H = 171;

    private FrameLayout appsHolder, getHolder;
    private View getSection;
    private TextView heroTitle, heroSub, primaryText, statusText;
    private ImageView primaryIcon, statusIcon;
    private View primaryBtn, ethPill;
    private Runnable primaryAction = () -> { };
    private BroadcastReceiver receiver;
    private FrameLayout introRoot;
    private ViewGroup introPage;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Amlogic.applySaved(this);   // picture brightness/contrast/colour reset at boot

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());
        root.setClipChildren(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(Ui.safeX, Ui.safeY, Ui.safeX, Ui.safeY);
        root.addView(page, Apps.fill());

        page.addView(topBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(64)));
        page.addView(spacer(28));
        page.addView(hero(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(spacer(36));

        appsHolder = holder();
        page.addView(section("Your apps", appsHolder, SLOT_H));

        getHolder = holder();
        LinearLayout gs = section("Get more to watch", getHolder, SLOT_H);
        gs.setPadding(0, Ui.s(8), 0, 0);
        getSection = gs;
        page.addView(gs);

        page.addView(spacer(8));
        page.addView(section("Settings", quickSettings(), 64));

        setContentView(root);
        // Boot hand-off. "am start -n tv.yeti.launcher/.MainActivity --ez intro true" replays it (testing).
        introRoot = root;
        introPage = page;
        if (BootIntro.wanted() || getIntent().getBooleanExtra("intro", false))
            root.post(BootIntro.attach(this, root, page, primaryBtn));

        receiver = new Refresher(this);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(receiver, f);
        IntentFilter n = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        n.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        n.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        n.addAction(WifiManager.RSSI_CHANGED_ACTION);
        n.addAction(Intent.ACTION_TIME_TICK);
        n.addAction(Intent.ACTION_TIME_CHANGED);
        registerReceiver(receiver, n);

        primaryBtn.requestFocus();
    }

    static final class Refresher extends BroadcastReceiver {
        private final MainActivity a;
        Refresher(MainActivity a) { this.a = a; }
        @Override public void onReceive(Context c, Intent i) {
            if (i.getData() != null) a.refresh(); else a.updateStatus();
        }
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    /** Home pressed while already home: back to the top, like Google TV. */
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        primaryBtn.requestFocus();
        if (i.getBooleanExtra("intro", false)) introRoot.post(BootIntro.attach(this, introRoot, introPage, primaryBtn));
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override public void onBackPressed() { /* home */ }

    @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            Object tag = f == null ? null : f.getTag();
            if (tag instanceof String && !((String) tag).startsWith("install:") && !"allapps".equals(tag)) {
                Apps.options(this, (String) tag);
            } else {
                Ui.openSettings(this);
            }
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    // ---------------- building blocks ----------------

    private View spacer(float h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.s(h)));
        return v;
    }

    private FrameLayout holder() {
        FrameLayout f = new FrameLayout(this);
        f.setClipChildren(false);
        f.setClipToPadding(false);
        return f;
    }

    /** Row title + content of a fixed design height. */
    private LinearLayout section(String title, View content, float h) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        TextView t = Ui.text(this, title, 28, Ui.TEXT, true);
        s.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(30)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(h));
        lp.topMargin = Ui.s(16);
        s.addView(content, lp);
        return s;
    }

    /** Pill with optional icon. Height comes from the caller's LayoutParams. */
    private LinearLayout pill(ImageView icon, String label, float textPx, Runnable click) {
        LinearLayout p = new LinearLayout(this);
        p.setGravity(Gravity.CENTER);
        p.setPadding(Ui.s(icon != null ? 26 : 32), 0, Ui.s(32), 0);
        p.setFocusable(true);
        p.setClickable(true);
        if (icon != null) p.addView(icon, new LinearLayout.LayoutParams(Ui.s(30), Ui.s(30)));
        TextView t = Ui.text(this, label, textPx, Ui.TEXT, true);
        t.setSingleLine(true);
        if (icon != null) t.setPadding(Ui.s(14), 0, 0, 0);
        p.addView(t);
        p.setOnClickListener(v -> click.run());
        Ui.pillFocus(p, 1.06f, new TextView[]{t}, new ImageView[]{icon});
        return p;
    }

    private ImageView icon(int res) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        return iv;
    }

    private View topBar() {
        FrameLayout h = new FrameLayout(this);
        h.setClipChildren(false);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_header);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.FIT_START);
        h.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(50), Gravity.START | Gravity.CENTER_VERTICAL));

        LinearLayout right = new LinearLayout(this);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setClipChildren(false);

        statusIcon = new ImageView(this);
        LinearLayout wifi = pill(statusIcon, "Wi-Fi", 22, this::openWifi);
        statusText = (TextView) wifi.getChildAt(1);
        right.addView(wifi, barLp(ViewGroup.LayoutParams.WRAP_CONTENT));

        ethPill = pill(icon(R.drawable.ic_ethernet), "Ethernet", 22, () -> Ui.start(this, new Intent(this, NetworkActivity.class)));
        right.addView(ethPill, barLp(ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout gear = new LinearLayout(this);
        gear.setGravity(Gravity.CENTER);
        gear.setFocusable(true);
        gear.setClickable(true);
        ImageView gi = icon(R.drawable.ic_settings);
        gear.addView(gi, new LinearLayout.LayoutParams(Ui.s(28), Ui.s(28)));
        gear.setOnClickListener(v -> Ui.openSettings(this));
        Ui.pillFocus(gear, 1.08f, null, new ImageView[]{gi});
        right.addView(gear, barLp(Ui.s(56)));

        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        styleClock(time, 34, Ui.TEXT);
        time.setPadding(Ui.s(12), 0, 0, 0);
        right.addView(time);

        h.addView(right, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL));
        return h;
    }

    private LinearLayout.LayoutParams barLp(int w) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, Ui.s(56));
        lp.rightMargin = Ui.s(14);
        return lp;
    }

    private void styleClock(TextClock t, float px, int color) {
        TextView model = Ui.text(this, "", px, color, true);
        Ui.textSize(t, px);
        t.setTextColor(color);
        t.setTypeface(model.getTypeface());
        t.setIncludeFontPadding(false);
    }

    private View hero() {
        FrameLayout hero = new FrameLayout(this);
        hero.setClipChildren(false);

        // Panel art: deep blue-to-neutral sweep with a soft glow behind the mascot. Drawn, so it scales cleanly.
        GradientDrawable sweep = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(17, 44, 82), Color.rgb(15, 25, 43), Color.rgb(20, 22, 30)});
        sweep.setCornerRadius(Ui.s(32));
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{Color.argb(120, 56, 182, 255), Color.argb(0, 56, 182, 255)});
        glow.setGradientCenter(0.84f, 0.5f);
        glow.setGradientRadius(Ui.s(560));
        glow.setCornerRadius(Ui.s(32));
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(Ui.s(32));
        edge.setColor(Color.TRANSPARENT);
        edge.setStroke(Math.max(1, Ui.s(2)), Color.argb(26, 255, 255, 255));
        View panel = new View(this);
        panel.setBackground(new LayerDrawable(new Drawable[]{sweep, glow, edge}));
        hero.addView(panel, Apps.fill());

        ImageView mascot = new ImageView(this);
        mascot.setImageResource(R.drawable.mascot);
        mascot.setAdjustViewBounds(true);
        mascot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(Ui.s(300), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END | Gravity.CENTER_VERTICAL);
        mlp.setMargins(0, Ui.s(36), Ui.s(110), Ui.s(36));
        hero.addView(mascot, mlp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        col.setPadding(Ui.s(64), 0, 0, 0);

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        styleClock(date, 19, Ui.ACCENT);
        date.setAllCaps(true);
        date.setLetterSpacing(0.16f);
        col.addView(date);

        heroTitle = Ui.text(this, "", 64, Ui.TEXT, true);
        heroTitle.setPadding(0, Ui.s(14), 0, 0);
        col.addView(heroTitle);

        heroSub = Ui.text(this, "", 26, Ui.TEXT_DIM, false);
        heroSub.setPadding(0, Ui.s(14), 0, 0);
        heroSub.setMaxWidth(Ui.s(1000));
        col.addView(heroSub);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setClipChildren(false);
        primaryIcon = new ImageView(this);
        LinearLayout primary = pill(primaryIcon, "", 24, () -> primaryAction.run());
        primaryText = (TextView) primary.getChildAt(1);
        primaryBtn = primary;
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(60));
        blp.rightMargin = Ui.s(18);
        buttons.addView(primary, blp);
        buttons.addView(pill(icon(R.drawable.ic_apps), "All apps", 24, () -> Ui.start(this, new Intent(this, AppsActivity.class))),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(60)));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = Ui.s(32);
        col.addView(buttons, bl);

        hero.addView(col, Apps.fill());
        return hero;
    }

    private View quickSettings() {
        // Every button opens Yeti TV's own page; "All settings" is the only one that goes to Android TV Settings.
        Object[][] items = {
                {"Wi-Fi", R.drawable.ic_wifi, WifiActivity.class},
                {"Network", R.drawable.ic_network, NetworkActivity.class},
                {"Display", R.drawable.ic_display, DisplayActivity.class},
                {"Sound", R.drawable.ic_sound, SoundActivity.class},
                {"Date & time", R.drawable.ic_time, DateTimeActivity.class},
                {"Apps", R.drawable.ic_apps, AppSettingsActivity.class},
                {"All settings", R.drawable.ic_settings, null},
                {"About", R.drawable.ic_info, AboutActivity.class},
        };
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        for (Object[] it : items) {
            final String name = (String) it[0];
            final Class<?> page = (Class<?>) it[2];
            Runnable click = () -> {
                if (page == null) Ui.openSettings(this);
                else Ui.start(this, new Intent(this, page));
            };
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(64));
            lp.rightMargin = Ui.s(16);
            row.addView(pill(icon((Integer) it[1]), name, 22, click), lp);
        }
        return row;
    }

    private void openWifi() { Ui.start(this, new Intent(this, WifiActivity.class)); }

    // ---------------- content ----------------

    void refresh() {
        updateStatus();
        PackageManager pm = getPackageManager();
        Object focusTag = getCurrentFocus() == null ? null : getCurrentFocus().getTag();

        // "Your apps": installed streaming apps first, then everything else, "All apps" in the last slot.
        List<View> mine = new ArrayList<>();
        List<View> get = new ArrayList<>();
        Set<String> watchPkgs = new HashSet<>();
        for (Object[] w : WATCH) {
            String installed = null;
            for (String p : ((String) w[1]).split(",")) if (Apps.installed(pm, p)) { installed = p; break; }
            if (installed != null) {
                watchPkgs.add(installed);
                mine.add(appSlot(installed, (String) w[0]));
            } else if (Apps.installed(pm, PLAY)) {
                get.add(getSlot((String) w[0], (String) w[2], (Integer) w[3]));
            }
        }
        List<Apps.App> others = Apps.list(this, watchPkgs);
        for (int i = 0; i < others.size() && mine.size() < SLOTS - 1; i++) mine.add(appSlot(others.get(i).pkg, others.get(i).label));
        mine.add(allAppsSlot(Apps.list(this, new HashSet<>()).size()));
        appsHolder.removeAllViews();
        appsHolder.addView(Apps.row(this, mine, SLOTS, Ui.contentW, Ui.s(SLOT_H), Ui.s(24)));

        getHolder.removeAllViews();
        if (!get.isEmpty()) getHolder.addView(Apps.row(this, get, SLOTS, Ui.contentW, Ui.s(SLOT_H), Ui.s(24)));
        getSection.setVisibility(get.isEmpty() ? View.GONE : View.VISIBLE);

        View restore = focusTag == null ? null : getWindow().getDecorView().findViewWithTag(focusTag);
        if (restore != null) restore.requestFocus();
        else if (getCurrentFocus() == null) primaryBtn.requestFocus();
    }

    /** Slot = 16:9 card + app name that fades in under the card while it is focused. */
    private LinearLayout slot(View card, String name) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.s(CARD_H)));
        TextView label = Ui.text(this, name, 22, Ui.TEXT, true);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.s(16);
        s.addView(label, lp);
        card.setFocusable(true);
        card.setClickable(true);
        Ui.focusEffect(card, 1.1f, (v, has) -> label.animate().alpha(has ? 1f : 0f).setDuration(160).start());
        return s;
    }

    private View appSlot(String pkg, String name) {
        PackageManager pm = getPackageManager();
        FrameLayout card = new FrameLayout(this);
        card.setClipToOutline(true);
        Drawable banner = Apps.banner(pm, pkg);
        if (banner != null) {
            card.setBackground(Ui.solid(Ui.CARD, 16));
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(banner);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(iv, Apps.fill());
        } else {
            Drawable ic = Apps.icon(pm, pkg);
            int tint = ic == null ? Ui.CARD_HI : Ui.dominant(ic);
            card.setBackground(Ui.card(tint, Ui.CARD, 16));
            if (ic != null) {
                ImageView iv = new ImageView(this);
                iv.setImageDrawable(ic);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                card.addView(iv, new FrameLayout.LayoutParams(Ui.s(72), Ui.s(72), Gravity.CENTER));
            }
        }
        card.setTag(pkg);
        card.setOnClickListener(v -> Apps.launch(this, pkg));
        card.setOnLongClickListener(v -> { Apps.options(this, pkg); return true; });
        return slot(card, name);
    }

    private View getSlot(String name, String storeId, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.BOTTOM | Gravity.START);
        card.setPadding(Ui.s(24), 0, Ui.s(20), Ui.s(20));
        card.setBackground(Ui.card(Apps.alpha(accent, 0.42f), Ui.CARD, 16));
        TextView t = Ui.text(this, name, 26, Ui.TEXT, true);
        t.setSingleLine(true);
        card.addView(t);
        TextView sub = Ui.text(this, "Get on Play Store", 18, Ui.TEXT_DIM, false);
        sub.setPadding(0, Ui.s(8), 0, 0);
        card.addView(sub);
        card.setTag("install:" + storeId);
        card.setOnClickListener(v -> Apps.openStore(this, storeId));
        return slot(card, "Install " + name);
    }

    private View allAppsSlot(int count) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.solid(Ui.CARD, 16));
        t.setTag("allapps");
        ImageView iv = icon(R.drawable.ic_apps);
        t.addView(iv, new LinearLayout.LayoutParams(Ui.s(44), Ui.s(44)));
        TextView n = Ui.text(this, "All apps", 22, Ui.TEXT, true);
        n.setPadding(0, Ui.s(12), 0, 0);
        n.setGravity(Gravity.CENTER_HORIZONTAL);
        t.addView(n);
        t.setOnClickListener(v -> Ui.start(this, new Intent(this, AppsActivity.class)));
        return slot(t, count + (count == 1 ? " app installed" : " apps installed"));
    }

    // ---------------- status ----------------

    void updateStatus() {
        boolean wifi = false, eth = false, wifiOk = false, ethOk = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null) continue;
                boolean ok = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) { wifi = true; wifiOk |= ok; }
                if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) { eth = true; ethOk |= ok; }
            }
        } catch (Exception ignored) { }
        boolean wifiOn = false;
        String ssid = null;
        int level = 0;
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiOn = wm.isWifiEnabled();
            WifiInfo wi = wm.getConnectionInfo();
            if (wifi && wi != null) {
                level = WifiManager.calculateSignalLevel(wi.getRssi(), 5);
                ssid = Wifi.cleanSsid(wi.getSSID());
            }
        } catch (Exception ignored) { }
        if (wifi && ssid == null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) ssid = "Wi-Fi";

        statusIcon.setImageDrawable(Ui.wifiGlyph(wifi ? Math.max(1, level) : 0, false, Ui.TEXT));
        statusText.setText(wifi ? (ssid != null ? ssid : "Wi-Fi") : (wifiOn ? "Not connected" : "Wi-Fi off"));
        ethPill.setVisibility(eth ? View.VISIBLE : View.GONE);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        heroTitle.setText(hour >= 5 && hour < 12 ? "Good morning" : hour >= 12 && hour < 17 ? "Good afternoon" : "Good evening");

        boolean online = (wifi && wifiOk) || (eth && ethOk);
        if (eth && ethOk) heroSub.setText("Connected over Ethernet. Everything's ready to watch.");
        else if (wifi && wifiOk) heroSub.setText("Connected to " + (ssid != null ? ssid : "Wi-Fi") + ". Everything's ready to watch.");
        else if (wifi || eth) heroSub.setText("Connected to " + (wifi && ssid != null ? ssid : "the network") + ", but there's no internet yet.");
        else heroSub.setText("You're offline. Connect to Wi-Fi to start streaming.");

        PackageManager pm = getPackageManager();
        if (online && Apps.installed(pm, PLAY)) {
            primaryText.setText("Get apps");
            Drawable store = Apps.icon(pm, PLAY);
            primaryIcon.setTag(Boolean.TRUE);   // keep brand colours, never tint
            primaryIcon.clearColorFilter();
            primaryIcon.setImageDrawable(store);
            primaryAction = () -> Apps.launch(this, PLAY);
        } else {
            primaryText.setText("Connect to Wi-Fi");
            primaryIcon.setTag(null);
            primaryIcon.setImageDrawable(Ui.wifiGlyph(4, false, Ui.TEXT));
            if (primaryBtn.isFocused()) primaryIcon.setColorFilter(Ui.TEXT_ON_FOCUS, android.graphics.PorterDuff.Mode.SRC_IN);
            primaryAction = this::openWifi;
        }
    }
}
