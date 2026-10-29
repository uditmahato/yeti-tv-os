package tv.yeti.launcher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Hand-off from the boot animation to the home screen.
 *
 * The boot animation (branding/make_bootanimation.py) is the power-on logo plus a "Starting Yeti TV OS" line. The
 * launcher's window background (drawable/splash.xml) is the same logo on the same navy, and this overlay repeats it
 * with the same pulsing dots, so nothing changes on screen when Android swaps the animation for the launcher:
 * no black frame. Once home has drawn, the splash slides off to the left while fading and the home rows glide in
 * from the right, one after another.
 *
 * Only after a fresh boot (device up for less than 3 minutes); a launcher restart later goes straight to home.
 * Logo and loader are placed in raw pixels, like the boot animation (which Android never scales).
 */
final class BootIntro {
    static final int BG = Color.rgb(0x00, 0x10, 0x31);       // = boot animation background
    private static final int ACCENT = Color.rgb(32, 160, 255);
    private static final long BOOT_WINDOW_MS = 3 * 60 * 1000;
    private static final long MIN_SHOW_MS = 700;               // let the dots pulse at least once in the launcher
    private static boolean shown;                              // once per process

    private BootIntro() { }

    static boolean wanted() { return !shown && SystemClock.elapsedRealtime() < BOOT_WINDOW_MS; }

    /** Covers {@code root} with the splash now; call {@link #play} when home is ready. */
    static Runnable attach(Activity a, FrameLayout root, ViewGroup page, View focusAfter) {
        shown = true;
        long start = SystemClock.uptimeMillis();
        int w = a.getResources().getDisplayMetrics().widthPixels, h = a.getResources().getDisplayMetrics().heightPixels;
        float k = Math.min(w / 1920f, h / 1080f);              // boot frame is 1920x1080; 1.0 on this box

        FrameLayout splash = new FrameLayout(a);
        splash.setBackgroundColor(BG);
        splash.setClickable(true);
        ImageView logo = new ImageView(a);
        logo.setImageResource(R.drawable.splash_logo);
        splash.addView(logo, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // "• • •  Starting Yeti TV OS", centred on y = 811 like in the boot animation
        LinearLayout loader = new LinearLayout(a);
        loader.setGravity(Gravity.CENTER_VERTICAL);
        View[] dots = new View[3];
        for (int i = 0; i < 3; i++) {
            View d = new View(a);
            d.setBackground(Ui.solid(ACCENT, 999));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(14 * k), Math.round(14 * k));
            lp.rightMargin = Math.round(12 * k);
            loader.addView(d, lp);
            dots[i] = d;
        }
        TextView label = new TextView(a);
        label.setText("Starting Yeti TV OS");
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30 * k);
        label.setTextColor(Color.rgb(150, 156, 172));
        label.setIncludeFontPadding(false);
        label.setPadding(Math.round(16 * k), 0, 0, 0);
        loader.addView(label);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(40 * k), Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        llp.topMargin = Math.round(h / 2f + (811 - 540) * k - 20 * k);
        splash.addView(loader, llp);

        ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1000);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(va -> {
            float t = (float) va.getAnimatedValue();
            for (int i = 0; i < 3; i++) {
                float p = (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * (t - i / 3.0)));
                dots[i].setAlpha(0.25f + 0.75f * p);
                float s = 0.75f + 0.35f * p;
                dots[i].setScaleX(s);
                dots[i].setScaleY(s);
            }
        });
        pulse.start();

        root.addView(splash, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.setAlpha(0f);

        return () -> {
            long wait = Math.max(0, MIN_SHOW_MS - (SystemClock.uptimeMillis() - start));
            root.postDelayed(() -> play(root, page, splash, pulse, w, focusAfter), wait);
        };
    }

    private static void play(FrameLayout root, ViewGroup page, FrameLayout splash, ValueAnimator pulse, int w, View focusAfter) {
        DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        // splash: slide left + fade
        splash.animate().translationX(-w * 0.18f).alpha(0f).setDuration(650).setInterpolator(ease)
                .withEndAction(() -> { pulse.cancel(); root.removeView(splash); if (focusAfter != null) focusAfter.requestFocus(); })
                .start();
        // home rows: in from the right, staggered
        page.setAlpha(1f);
        float dx = w * 0.12f;
        for (int i = 0; i < page.getChildCount(); i++) {
            View row = page.getChildAt(i);
            row.setAlpha(0f);
            row.setTranslationX(dx);
            row.animate().translationX(0).alpha(1f).setStartDelay(120 + i * 55L).setDuration(600).setInterpolator(ease).start();
        }
    }
}
