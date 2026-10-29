package tv.yeti.launcher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Shared look + screen scaling for every Yeti TV screen.
 *
 * Layout is designed on a 1920x1080 canvas and scaled to the real screen ("media query" for TV):
 * s(px) converts a design pixel to a real pixel, so the UI looks identical on 720p, 1080p and 4K,
 * whatever density the box reports, and never overflows.
 *
 * Look: Google TV style. Near-black neutral surfaces, white focus ring + zoom, pills that turn white
 * when focused, Yeti blue used only as an accent.
 */
final class Ui {
    static final int BG_TOP = Color.rgb(16, 19, 26);
    static final int BG_BOTTOM = Color.rgb(7, 8, 11);
    static final int CARD = Color.rgb(30, 33, 41);
    static final int CARD_HI = Color.rgb(42, 46, 56);
    static final int CHIP = Color.argb(30, 255, 255, 255);
    static final int ACCENT = Color.rgb(56, 182, 255);
    static final int FOCUS = Color.WHITE;
    static final int TEXT = Color.rgb(241, 243, 244);
    static final int TEXT_DIM = Color.rgb(154, 160, 166);
    static final int TEXT_ON_FOCUS = Color.rgb(18, 20, 24);
    static final int GOOD = Color.rgb(52, 199, 89);
    static final int WARN = Color.rgb(255, 176, 32);

    static final String TV_SETTINGS = "com.android.tv.settings";

    private static float scale = 1f;
    static int screenW, screenH, safeX, safeY, contentW, contentH;

    private Ui() { }

    /** Call once per activity before building views. */
    static void init(Activity a) {
        // App window size (UI resolution, e.g. 1920x1080), NOT getRealMetrics(): on this box the HDMI output is
        // 4K (vout=2160p60hz) while Android draws at 1080p, so real metrics would scale everything 2x and overflow.
        DisplayMetrics m = a.getResources().getDisplayMetrics();
        screenW = Math.max(m.widthPixels, m.heightPixels);
        screenH = Math.min(m.widthPixels, m.heightPixels);
        scale = Math.min(screenW / 1920f, screenH / 1080f);
        safeX = Math.round(screenW * 0.05f);   // TV title-safe margins
        safeY = Math.round(screenH * 0.05f);
        contentW = screenW - 2 * safeX;
        contentH = screenH - 2 * safeY;
    }

    /** Design pixel (1920x1080 canvas) -> real pixel. */
    static int s(float designPx) { return Math.round(designPx * scale); }

    /** Design-pixel text size. */
    static void textSize(TextView t, float designPx) { t.setTextSize(TypedValue.COMPLEX_UNIT_PX, designPx * scale); }

    /** Page background: neutral dark gradient with a faint Yeti-blue glow in the top-left corner. */
    static Drawable background() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{BG_TOP, BG_BOTTOM});
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setColors(new int[]{Color.argb(46, 56, 182, 255), Color.argb(0, 56, 182, 255)});
        glow.setGradientCenter(0.1f, 0.0f);
        glow.setGradientRadius(s(1100));
        return new LayerDrawable(new Drawable[]{base, glow});
    }

    static GradientDrawable card(int from, int to, float radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        g.setCornerRadius(s(radius));
        return g;
    }

    static GradientDrawable solid(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(s(radius));
        return g;
    }

    static TextView text(Context c, String s, float designPx, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        textSize(t, designPx);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return t;
    }

    static TextView eyebrow(Context c, String s, int color) {
        TextView t = text(c, s.toUpperCase(), 19, color, true);
        t.setLetterSpacing(0.16f);
        return t;
    }

    /**
     * Card focus: white ring + zoom + lift. The ring is a foreground layer (drawn over banner images) with the
     * background's corner radius, so the view's background should be a GradientDrawable.
     */
    static void focusEffect(View v, float zoom) { focusEffect(v, zoom, null); }

    static void focusEffect(View v, float zoom, View.OnFocusChangeListener also) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 24 && v.getBackground() instanceof GradientDrawable) ring.setCornerRadius(((GradientDrawable) v.getBackground()).getCornerRadius());
        v.setOnFocusChangeListener((view, has) -> {
            ring.setStroke(s(4), has ? FOCUS : Color.TRANSPARENT);
            if (android.os.Build.VERSION.SDK_INT >= 23) view.setForeground(ring);
            else if (view.getBackground() instanceof GradientDrawable)
                ((GradientDrawable) view.getBackground()).setStroke(s(4), has ? FOCUS : Color.TRANSPARENT);
            view.animate().scaleX(has ? zoom : 1f).scaleY(has ? zoom : 1f)
                    .translationZ(has ? s(18) : 0).setDuration(160).start();
            if (also != null) also.onFocusChange(view, has);
        });
    }

    /**
     * Pill focus (Google TV buttons/chips): translucent when idle, solid white with dark text and icon when focused.
     * {@code labels} and {@code icons} are recoloured; either may be null. An icon tagged Boolean.TRUE keeps its colours.
     */
    static void pillFocus(View v, float zoom, TextView[] labels, ImageView[] icons) {
        v.setBackground(solid(CHIP, 999));
        v.setOnFocusChangeListener((view, has) -> {
            ((GradientDrawable) view.getBackground()).setColor(has ? FOCUS : CHIP);
            if (labels != null) for (TextView t : labels) if (t != null) t.setTextColor(has ? TEXT_ON_FOCUS : TEXT);
            if (icons != null) for (ImageView i : icons) if (i != null && !Boolean.TRUE.equals(i.getTag())) {
                if (has) i.setColorFilter(TEXT_ON_FOCUS, PorterDuff.Mode.SRC_IN); else i.clearColorFilter();
            }
            view.animate().scaleX(has ? zoom : 1f).scaleY(has ? zoom : 1f).setDuration(140).start();
        });
    }

    /** Darkened average colour of an icon, used to tint cards of apps that ship no TV banner. */
    static int dominant(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 24, 24);
            d.draw(c);
            long r = 0, g = 0, bl = 0, n = 0;
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 128) continue;
                float[] hsv = new float[3];
                Color.colorToHSV(p, hsv);
                int w = hsv[1] > 0.25f && hsv[2] > 0.2f ? 4 : 1;   // favour saturated pixels over white/grey
                r += Color.red(p) * w; g += Color.green(p) * w; bl += Color.blue(p) * w; n += w;
            }
            if (n == 0) return CARD_HI;
            float[] hsv = new float[3];
            Color.colorToHSV(Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n)), hsv);
            hsv[1] = Math.min(hsv[1], 0.75f);
            hsv[2] = Math.min(Math.max(hsv[2], 0.30f), 0.42f);
            return Color.HSVToColor(hsv);
        } catch (Exception e) { return CARD_HI; }
    }

    /** Wi-Fi fan glyph, {@code level} 0..4 bars lit, optional padlock; drawn so it stays crisp at any scale. */
    static Drawable wifiGlyph(int level, boolean lock, int color) { return new WifiGlyph(level, lock, color); }

    private static final class WifiGlyph extends Drawable {
        private final int level, color;
        private final boolean lock;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        WifiGlyph(int level, boolean lock, int color) { this.level = level; this.lock = lock; this.color = color; }

        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height();
            float cx = getBounds().left + w / 2f, by = getBounds().top + h * 0.86f;
            p.setStyle(Paint.Style.FILL);
            for (int i = 3; i >= 0; i--) {
                float r = h * (0.22f + 0.2f * i);
                p.setColor(i < level ? color : Color.argb(70, Color.red(color), Color.green(color), Color.blue(color)));
                if (i == 0) {
                    c.drawCircle(cx, by - r * 0.35f, h * 0.09f, p);
                } else {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(h * 0.1f);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    c.drawArc(new RectF(cx - r, by - r, cx + r, by + r), 225, 90, false, p);
                    p.setStyle(Paint.Style.FILL);
                }
            }
            if (lock) {
                float lw = w * 0.30f, lh = h * 0.24f, lx = getBounds().right - lw, ly = getBounds().bottom - lh;
                p.setColor(color);
                c.drawRoundRect(new RectF(lx, ly, lx + lw, ly + lh), lw * 0.15f, lw * 0.15f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(h * 0.055f);
                c.drawArc(new RectF(lx + lw * 0.2f, ly - lh * 0.55f, lx + lw * 0.8f, ly + lh * 0.45f), 180, 180, false, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // ---------------- launching ----------------

    static boolean start(Context c, Intent i) {
        try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true; }
        catch (ActivityNotFoundException | SecurityException e) { return false; }
    }

    /** Main screen of the full Android TV Settings app (its own launcher entry, as on the stock home). */
    static Intent tvSettingsMain(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(TV_SETTINGS);
        if (i == null) i = pm.getLaunchIntentForPackage(TV_SETTINGS);
        return i;
    }

    /** Opens one Android TV Settings screen by class (e.g. "com.android.tv.settings.SoundActivity"); main screen if that fails. */
    static void openTvSettings(Context c, String cls) {
        if (start(c, new Intent(Intent.ACTION_MAIN).setClassName(TV_SETTINGS, cls))) return;
        openSettings(c);
    }

    /**
     * Opens a settings page inside Android TV Settings only. For each action, looks up which of
     * Android TV Settings' own activities handles it and starts that one explicitly, so the vendor's
     * simplified settings app can never intercept it. Falls back to the Android TV Settings main screen.
     */
    static void openSettings(Context c, Intent... candidates) {
        PackageManager pm = c.getPackageManager();
        for (Intent want : candidates) {
            if (want == null) continue;
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(want), 0)) {
                if (TV_SETTINGS.equals(ri.activityInfo.packageName)) {
                    Intent exact = new Intent(want).setClassName(ri.activityInfo.packageName, ri.activityInfo.name);
                    if (start(c, exact)) return;
                }
            }
        }
        Intent main = tvSettingsMain(c);
        if (main != null && start(c, main)) return;
        start(c, new Intent(android.provider.Settings.ACTION_SETTINGS));
    }
}
