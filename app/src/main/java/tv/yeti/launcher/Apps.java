package tv.yeti.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** App discovery, launching and the shared app tile. */
final class Apps {
    private Apps() { }

    /** Never shown anywhere in Yeti TV: this launcher, both settings apps (reached from the Settings row instead) and
     *  chip-vendor system tools. (The stock firmware's own apps are removed from the box, so they never appear.) */
    static final Set<String> HIDDEN = new HashSet<>();
    static {
        Collections.addAll(HIDDEN, "tv.yeti.launcher", "com.droidlogic.vsota", "com.softwinner.agingdragonbox",
                "com.android.provision", "com.google.android.feedback", "com.google.android.projection.gearhead",
                "com.android.settings", "com.android.settings.intelligence", Ui.TV_SETTINGS);
    }

    static final class App {
        final String pkg, label;
        App(String pkg, String label) { this.pkg = pkg; this.label = label; }
    }

    static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    static boolean installed(PackageManager pm, String pkg) { return launchIntent(pm, pkg) != null; }

    /** All launchable apps (TV + phone style), alphabetical, minus hidden and excluded packages. */
    static List<App> list(Context c, Set<String> exclude) {
        PackageManager pm = c.getPackageManager();
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = ri.activityInfo.packageName;
                if (HIDDEN.contains(p) || exclude.contains(p) || !seen.add(p)) continue;
                out.add(new App(p, ri.loadLabel(pm).toString()));
            }
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    static Drawable icon(PackageManager pm, String pkg) {
        try {
            Intent li = launchIntent(pm, pkg);
            if (li != null && li.getComponent() != null) return pm.getActivityIcon(li.getComponent());
            return pm.getApplicationIcon(pkg);
        } catch (Exception e) { return null; }
    }

    static Drawable banner(PackageManager pm, String pkg) {
        try {
            Intent li = launchIntent(pm, pkg);
            Drawable d = (li != null && li.getComponent() != null) ? pm.getActivityBanner(li.getComponent()) : null;
            return d != null ? d : pm.getApplicationInfo(pkg, 0).loadBanner(pm);
        } catch (Exception e) { return null; }
    }

    static void launch(Context c, String pkg) {
        Intent i = launchIntent(c.getPackageManager(), pkg);
        if (i != null) Ui.start(c, i);
    }

    static void openStore(Context c, String pkg) {
        if (!Ui.start(c, new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)))) launch(c, "com.android.vending");
    }

    static void options(Activity a, String pkg) {
        PackageManager pm = a.getPackageManager();
        String label = pkg;
        boolean system = false;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            label = ai.loadLabel(pm).toString();
            system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception ignored) { }
        String[] opts = system ? new String[]{"Open", "App info"} : new String[]{"Open", "App info", "Uninstall"};
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(label)
                .setItems(opts, (d, w) -> {
                    if (w == 0) launch(a, pkg);
                    else if (w == 1) Ui.openSettings(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                    else Ui.start(a, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
                })
                .show();
    }

    /** Square-ish app tile: icon + name. Size is set by the caller's LayoutParams. */
    static View tile(Activity a, App app, float iconPx, float textPx) {
        PackageManager pm = a.getPackageManager();
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.s(12), Ui.s(14), Ui.s(12), Ui.s(14));
        t.setBackground(Ui.card(Ui.CARD_HI, Ui.CARD, 18));
        t.setFocusable(true);
        t.setClickable(true);
        t.setTag(app.pkg);
        ImageView iv = new ImageView(a);
        Drawable d = icon(pm, app.pkg);
        if (d != null) iv.setImageDrawable(d);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(iv, new LinearLayout.LayoutParams(Ui.s(iconPx), Ui.s(iconPx)));
        TextView name = Ui.text(a, app.label, textPx, Ui.TEXT, true);
        name.setGravity(Gravity.CENTER);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, Ui.s(14), 0, 0);
        t.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        t.setOnClickListener(v -> launch(a, app.pkg));
        t.setOnLongClickListener(v -> { options(a, app.pkg); return true; });
        Ui.focusEffect(t, 1.08f);
        return t;
    }

    /** Places views in a single row of {@code slots} equal columns filling {@code width}. */
    static LinearLayout row(Context c, List<View> views, int slots, int width, int height, int gap) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        int w = (width - gap * (slots - 1)) / slots;
        for (int i = 0; i < views.size() && i < slots; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, height);
            if (i < slots - 1) lp.rightMargin = gap;
            r.addView(views.get(i), lp);
        }
        return r;
    }

    /** Unused colour helper kept for tiles that need a translucent overlay. */
    static int alpha(int color, float a) { return Color.argb(Math.round(255 * a), Color.red(color), Color.green(color), Color.blue(color)); }

    static FrameLayout.LayoutParams fill() { return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT); }
}
