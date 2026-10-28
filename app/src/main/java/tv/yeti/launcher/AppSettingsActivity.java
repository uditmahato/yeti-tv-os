package tv.yeti.launcher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** Apps: storage summary + every app with Open / App info / Uninstall. */
public class AppSettingsActivity extends SettingsPage {

    @Override
    void build() {
        header("Apps", "Apps", "Pick an app to open it, see its details or uninstall it.");

        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        double free = s.getAvailableBytes() / 1e9, total = s.getTotalBytes() / 1e9;
        List<Apps.App> apps = Apps.list(this, new HashSet<>());
        summary("Storage", free < 0.5 ? Ui.WARN : Ui.GOOD, String.format(Locale.US, "%.1f GB free", free));
        detail("Total", String.format(Locale.US, "%.1f GB", total));
        detail("Apps", String.valueOf(apps.size()));

        section("Get apps");
        action("store", "Google Play Store", "Find and install apps", true, () -> Apps.launch(this, "com.android.vending"));

        section("Installed");
        PackageManager pm = getPackageManager();
        for (Apps.App a : apps) {
            String sub;
            boolean system = false;
            try {
                PackageInfo pi = pm.getPackageInfo(a.pkg, 0);
                system = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (pi.applicationInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                sub = "Version " + pi.versionName + (system ? "  ·  Built in" : "");
            } catch (Exception e) { sub = a.pkg; }
            final boolean builtIn = system;
            action("app:" + a.pkg, a.label, sub, true, () -> options(a, builtIn));
        }

        section("More");
        action("advanced", "Permissions & special access", "Android TV app settings", true,
                () -> Ui.openTvSettings(this, "com.android.tv.settings.device.apps.AppsActivity"));
    }

    private void options(Apps.App a, boolean builtIn) {
        Runnable info = () -> {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))
                    .setClassName(Ui.TV_SETTINGS, "com.android.tv.settings.device.apps.AppManagementActivity");
            if (!Ui.start(this, i)) Ui.openSettings(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
        };
        Runnable open = () -> Apps.launch(this, a.pkg);
        if (builtIn) dialog(a.label, "Built-in app", "Open", open, "App info", info, "Cancel", null);
        else dialog(a.label, null, "Open", open, "App info", info,
                "Uninstall", (Runnable) () -> Ui.start(this, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))), "Cancel", null);
    }
}
