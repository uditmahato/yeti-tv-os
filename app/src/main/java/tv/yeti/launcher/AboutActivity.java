package tv.yeti.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Color;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** About Yeti TV: brand, founder credit, live device info. Fixed layout on the 1920x1080 canvas. */
public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(Ui.safeX, Ui.safeY, Ui.safeX, Ui.safeY);
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // ---- left column: brand + founder
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.START);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_header);
        logo.setAdjustViewBounds(true);
        left.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.s(130)));

        TextView ver = Ui.text(this, "Version " + version(), 26, Ui.TEXT_DIM, false);
        ver.setPadding(0, Ui.s(22), 0, Ui.s(48));
        left.addView(ver);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(Ui.s(56), Ui.s(36), Ui.s(56), Ui.s(40));
        card.setBackground(Ui.card(Ui.CARD_HI, Ui.CARD, 28));
        TextView by = Ui.text(this, "CREATED BY", 20, Ui.ACCENT, true);
        by.setLetterSpacing(0.2f);
        card.addView(by);
        TextView name = Ui.text(this, "Udit Kumar Mahato", 46, Ui.TEXT, true);
        name.setPadding(0, Ui.s(14), 0, Ui.s(8));
        card.addView(name);
        card.addView(Ui.text(this, "Founder, Yeti TV", 28, Ui.TEXT_DIM, false));
        left.addView(card);

        TextView tag = Ui.text(this, "Simple. Fast. Yours.", 26, Ui.TEXT_DIM, false);
        tag.setPadding(0, Ui.s(40), 0, 0);
        left.addView(tag);

        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        llp.rightMargin = Ui.s(64);
        page.addView(left, llp);

        // ---- right column: device info
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(Ui.s(48), Ui.s(40), Ui.s(48), Ui.s(40));
        right.setBackground(Ui.solid(Ui.CHIP, 28));
        TextView h = Ui.text(this, "This device", 32, Ui.TEXT, true);
        h.setPadding(0, 0, 0, Ui.s(18));
        right.addView(h);
        row(right, "Device name", "Yeti TV");
        row(right, "Software", "Yeti TV OS " + version());
        row(right, "Processor", "Amlogic " + chip());
        row(right, "Memory", memory());
        row(right, "Storage", storage());
        row(right, "IP address", ip());
        row(right, "Up time", uptime());
        right.setFocusable(true);
        page.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));

        setContentView(root);
        right.requestFocus();
    }

    private void row(LinearLayout parent, String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(0, Ui.s(16), 0, Ui.s(16));
        r.addView(Ui.text(this, k, 24, Ui.TEXT_DIM, false), new LinearLayout.LayoutParams(Ui.s(220), ViewGroup.LayoutParams.WRAP_CONTENT));
        r.addView(Ui.text(this, v, 24, Ui.TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(r);
        View line = new View(this);
        line.setBackgroundColor(Color.argb(36, 255, 255, 255));
        parent.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Ui.s(2))));
    }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception e) { return ""; }
    }

    private String chip() {
        try {
            String board = (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, "ro.board.platform");
            if ("s4".equals(board)) return "S905W2 · quad-core Cortex-A35";
        } catch (Exception ignored) { }
        return Build.HARDWARE;
    }

    private String memory() {
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        return String.format(Locale.US, "%.1f GB  (%.1f GB free)", mi.totalMem / 1073741824.0, mi.availMem / 1073741824.0);
    }

    private String storage() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return String.format(Locale.US, "%.1f GB free of %.1f GB", s.getAvailableBytes() / 1e9, s.getTotalBytes() / 1e9);
    }

    /** From ConnectivityManager: NetworkInterface enumeration is blocked for apps on Android 11 and returned nothing. */
    private String ip() {
        String eth = Wifi.ipv4(this, NetworkCapabilities.TRANSPORT_ETHERNET);
        if (eth != null) return eth + "  (Ethernet)";
        String wifi = Wifi.ipv4(this, NetworkCapabilities.TRANSPORT_WIFI);
        if (wifi != null) return wifi + "  (Wi-Fi)";
        return "Not connected";
    }

    private String uptime() {
        long m = SystemClock.elapsedRealtime() / 60000;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : m + " min";
    }
}
