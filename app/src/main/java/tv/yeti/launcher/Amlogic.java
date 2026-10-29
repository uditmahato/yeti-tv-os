package tv.yeti.launcher;

import android.content.Context;
import android.util.Log;

/**
 * Display controls through the Amlogic vendor library (droidlogic.software.core, /vendor/framework/droidlogic.jar).
 * Its SystemControlManager talks to the root "systemcontrol" HAL. Called by reflection so the app still builds
 * and runs without the library.
 *
 * Picture (brightness / contrast / colour) uses the amvecm "post-blend" adjustment (brightness2, contrast2,
 * saturation_hue_post), which applies to the whole HDMI picture, menus and video. (The vendor's SetBrightness only
 * touches the video layer, brightness1, which is why it showed no change on the home screen.) Those registers
 * reset at boot, so values are kept in SharedPreferences (commit(), so a reboot right after a change keeps it) and re-applied by {@link #applySaved} from MainActivity.
 * Slider values are 0..100 with 50 = standard (register 0).
 */
final class Amlogic {
    private static final String TAG = "YetiAmlogic";
    static final int NEUTRAL = 50;
    private static final String VECM = "/sys/class/amvecm/";
    private static final String PREFS = "picture";

    private static Object scm;
    private static boolean tried;

    private Amlogic() { }

    private static synchronized Object scm() {
        if (!tried) {
            tried = true;
            try {
                scm = Class.forName("com.droidlogic.app.SystemControlManager").getMethod("getInstance").invoke(null);
            } catch (Throwable t) {
                Log.w(TAG, "SystemControlManager unavailable", t);
            }
        }
        return scm;
    }

    /** Root write through the systemcontrol HAL (apps can't write /sys directly). */
    private static boolean writeSysFs(String path, String value) {
        try {
            Object o = scm();
            if (o == null) return false;
            Object ok = o.getClass().getMethod("writeSysFs", String.class, String.class).invoke(o, path, value);
            return !Boolean.FALSE.equals(ok);
        } catch (Throwable t) {
            Log.w(TAG, "writeSysFs " + path + " failed", t);
            return false;
        }
    }

    static boolean available() { return scm() != null && new java.io.File(VECM + "brightness2").exists(); }

    static int clamp(int v) { return Math.max(0, Math.min(100, v)); }

    // slider 0..100 -> register; ranges from the amvecm driver help: brightness -255..255, contrast -127..127,
    // saturation -128..128. Scaled so the ends are strong but still watchable.
    private static int reg(int v, int perStep) { return (clamp(v) - NEUTRAL) * perStep; }

    private static boolean applyBrightness(int v) { return writeSysFs(VECM + "brightness2", String.valueOf(reg(v, 3))); }
    private static boolean applyContrast(int v) { return writeSysFs(VECM + "contrast2", String.valueOf(reg(v, 2))); }
    private static boolean applySaturation(int v) { return writeSysFs(VECM + "saturation_hue_post", reg(v, 2) + " 0"); }

    private static android.content.SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static int brightness(Context c) { return prefs(c).getInt("brightness", NEUTRAL); }
    static int contrast(Context c) { return prefs(c).getInt("contrast", NEUTRAL); }
    static int saturation(Context c) { return prefs(c).getInt("saturation", NEUTRAL); }

    static void setBrightness(Context c, int v) { prefs(c).edit().putInt("brightness", clamp(v)).commit(); applyBrightness(v); }
    static void setContrast(Context c, int v) { prefs(c).edit().putInt("contrast", clamp(v)).commit(); applyContrast(v); }
    static void setSaturation(Context c, int v) { prefs(c).edit().putInt("saturation", clamp(v)).commit(); applySaturation(v); }

    /** Re-apply the saved picture after boot (the registers start at 0). Cheap; only writes non-standard values. */
    static void applySaved(Context c) {
        if (scm() == null) return;
        if (brightness(c) != NEUTRAL) applyBrightness(brightness(c));
        if (contrast(c) != NEUTRAL) applyContrast(contrast(c));
        if (saturation(c) != NEUTRAL) applySaturation(saturation(c));
    }

    // ---------------- HDMI output mode + screen size (these two persist in the u-boot env, by design) ----------------

    private static Object om(Context c) throws Exception {
        return Class.forName("com.droidlogic.app.OutputModeManager").getMethod("getInstance", Context.class).invoke(null, c);
    }

    /** Current HDMI output mode, e.g. "2160p60hz". */
    static String outputMode(Context c) {
        try { Object om = om(c); return (String) om.getClass().getMethod("getCurrentOutputMode").invoke(om); }
        catch (Throwable t) { return null; }
    }

    /** True when the box picks the best mode for the TV by itself. */
    static boolean autoMode(Context c) {
        try { Object om = om(c); return (Boolean) om.getClass().getMethod("isBestOutputmode").invoke(om); }
        catch (Throwable t) { return false; }
    }

    /** TV modes the connected TV reports (HDMI EDID), highest first; PC modes like 1024x768 are left out. */
    static java.util.List<String> tvModes(Context c) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            Object om = om(c);
            String raw = (String) om.getClass().getMethod("getHdmiSupportList").invoke(om);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?<![x\\d])(\\d{3,4})([pi])(\\d{2,3})hz").matcher(raw == null ? "" : raw);
            while (m.find()) if (!out.contains(m.group())) out.add(m.group());
        } catch (Throwable t) {
            Log.w(TAG, "getHdmiSupportList failed", t);
        }
        java.util.Collections.sort(out, (a, b) -> {
            int[] x = modeKey(a), y = modeKey(b);
            for (int i = 0; i < 3; i++) if (x[i] != y[i]) return Integer.compare(y[i], x[i]);
            return 0;
        });
        return out;
    }

    /** lines, progressive (1) / interlaced (0), refresh. */
    private static int[] modeKey(String mode) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([pi])(\\d+)hz").matcher(mode);
        return m.matches() ? new int[]{Integer.parseInt(m.group(1)), "p".equals(m.group(2)) ? 1 : 0, Integer.parseInt(m.group(3))} : new int[]{0, 0, 0};
    }

    /** {@code mode} null = automatic (best for the TV), otherwise a fixed mode such as "1080p60hz". */
    static boolean setMode(Context c, String mode) {
        try {
            Object om = om(c);
            om.getClass().getMethod("setBestMode", String.class).invoke(om, mode);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "setBestMode failed", t);
            return false;
        }
    }

    private static Object position;

    private static Object position(Context c) throws Exception {
        if (position == null)
            position = Class.forName("com.droidlogic.app.DisplayPositionManager").getConstructor(Context.class).newInstance(c.getApplicationContext());
        return position;
    }

    static final int ZOOM_MIN = 80, ZOOM_MAX = 100;

    /** Screen size in percent (80..100, 100 = full picture), or -1 if unavailable. */
    static int screenSize(Context c) {
        if (lastSize > 0) return lastSize;   // the vendor getter is not updated by zoomByPercent
        try { Object p = position(c); return (Integer) p.getClass().getMethod("getCurrentRateValue").invoke(p); }
        catch (Throwable t) { return -1; }
    }

    private static int lastSize;

    static void setScreenSize(Context c, int percent) {
        lastSize = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, percent));
        try {
            Object p = position(c);
            p.getClass().getMethod("zoomByPercent", int.class).invoke(p, Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, percent)));
        } catch (Throwable t) {
            Log.w(TAG, "zoomByPercent failed", t);
        }
    }

    /** "2160p60hz" -> "4K · 60 Hz", "1080i50hz" -> "1080i · 50 Hz". */
    static String prettyMode(String m) {
        if (m == null) return "Unknown";
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("(\\d+x)?(\\d+)([pi])(\\d+)hz").matcher(m);
        if (!x.matches()) return m;
        String lines = x.group(2);
        String name = x.group(1) != null ? x.group(1) + lines : "2160".equals(lines) ? "4K" : lines + x.group(3);
        return name + "  ·  " + x.group(4) + " Hz";
    }
}
