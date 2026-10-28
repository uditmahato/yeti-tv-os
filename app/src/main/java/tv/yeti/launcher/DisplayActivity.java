package tv.yeti.launcher;

import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.widget.TextView;

import java.util.List;

/**
 * Display: HDMI resolution (with a 15 s "keep it?" auto-revert, like Android TV), screen size for TVs that cut
 * off the edges, and picture brightness / contrast / colour — all through the Amlogic vendor service (Amlogic.java).
 */
public class DisplayActivity extends SettingsPage {

    private static final int REVERT_SECONDS = 15;
    /** Static on purpose: the revert must still happen if this screen is closed or recreated mid-countdown. */
    private static final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    void build() {
        header("Display", "Display", "Adjust the picture with the remote: focus a slider and press left or right.");

        String mode = Amlogic.outputMode(this);
        boolean auto = Amlogic.autoMode(this);
        DisplayMetrics m = getResources().getDisplayMetrics();
        summary("HDMI output", Ui.GOOD, Amlogic.prettyMode(mode));
        detail("Resolution", auto ? "Automatic" : "Fixed");
        detail("Signal", mode != null ? mode : "Unknown");
        detail("Interface", Math.max(m.widthPixels, m.heightPixels) + " × " + Math.min(m.widthPixels, m.heightPixels));

        section("Screen");
        List<String> modes = Amlogic.tvModes(this);
        if (!modes.isEmpty()) {
            action("resolution", "Resolution", (auto ? "Automatic  ·  " : "") + Amlogic.prettyMode(mode), true,
                    () -> pickResolution(modes, mode, auto));
        }
        int size = Amlogic.screenSize(this);
        if (size >= Amlogic.ZOOM_MIN) {
            slider("size", "Screen size", size, Amlogic.ZOOM_MIN, Amlogic.ZOOM_MAX, 1, v -> Amlogic.setScreenSize(this, v),
                    v -> v == Amlogic.ZOOM_MAX ? "100%  ·  Full" : v + "%");
        }

        section("Picture");
        if (Amlogic.available()) {
            slider("bright", "Brightness", Amlogic.brightness(this), 100, 1, v -> Amlogic.setBrightness(this, v), DisplayActivity::level);
            slider("contrast", "Contrast", Amlogic.contrast(this), 100, 1, v -> Amlogic.setContrast(this, v), DisplayActivity::level);
            slider("colour", "Colour", Amlogic.saturation(this), 100, 1, v -> Amlogic.setSaturation(this, v), DisplayActivity::level);
            action("reset", "Reset picture", "Back to brightness, contrast and colour 50", false, () ->
                    dialog("Reset picture?", "Brightness, contrast and colour go back to their standard values.",
                            "Reset", (Runnable) () -> {
                                Amlogic.setBrightness(this, Amlogic.NEUTRAL);
                                Amlogic.setContrast(this, Amlogic.NEUTRAL);
                                Amlogic.setSaturation(this, Amlogic.NEUTRAL);
                            },
                            "Cancel", null));
        } else {
            info("Picture controls", "Not supported on this box");
        }

        section("More");
        action("advanced", "HDR, colour format & more", "Android TV display settings", true, () -> Ui.openTvSettings(this,
                "com.android.tv.settings.device.displaysound.DisplaySoundActivity"));
    }

    private void pickResolution(List<String> modes, String current, boolean auto) {
        String[] labels = new String[modes.size() + 1];
        labels[0] = "Automatic (best for this TV)";
        int sel = auto ? 0 : -1;
        for (int i = 0; i < modes.size(); i++) {
            labels[i + 1] = Amlogic.prettyMode(modes.get(i)) + (modes.get(i).contains("i") ? "  (interlaced)" : "");
            if (!auto && modes.get(i).equals(current)) sel = i + 1;
        }
        choose("Resolution", labels, Math.max(sel, 0), i -> {
            String target = i == 0 ? null : modes.get(i - 1);
            if (i == 0 ? auto : (!auto && target.equals(current))) return;
            Amlogic.setMode(this, target);
            rebuild();
            confirm(auto ? null : current);
        });
    }

    /** Ask to keep the new mode; go back to {@code previous} (null = automatic) on "Revert", Back, or after 15 s. */
    private void confirm(String previous) {
        final int[] left = {REVERT_SECONDS};
        final boolean[] done = {false};
        Runnable revert = () -> {
            if (done[0]) return;
            done[0] = true;
            handler.removeCallbacksAndMessages(null);
            Amlogic.setMode(getApplicationContext(), previous);
            if (!isDestroyed()) rebuild();
        };
        Runnable keep = () -> { done[0] = true; handler.removeCallbacksAndMessages(null); rebuild(); };
        TextView body = dialog("Keep this resolution?", text(left[0]), "Keep", keep, "Revert", revert);
        dialogBack = revert;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (done[0]) return;
                if (--left[0] <= 0) { if (!isDestroyed()) closeOverlay(); revert.run(); return; }
                if (body != null) body.setText(text(left[0]));
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    private static String text(int s) {
        return "If the picture looks wrong or your TV shows no signal, just wait: Yeti TV switches back in " + s + " s.";
    }

    /** 50 is the standard picture, shown as "Standard"; others as a number. */
    private static String level(int v) { return v == Amlogic.NEUTRAL ? "50  ·  Standard" : String.valueOf(v); }
}
