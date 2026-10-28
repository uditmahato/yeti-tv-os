package tv.yeti.launcher;

import android.provider.Settings;
import android.text.format.DateFormat;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Locale;
import java.util.TimeZone;

/** Date & time: live clock, automatic time / time zone, 24-hour clock. */
public class DateTimeActivity extends SettingsPage {

    private static final String TV_DATE = "com.android.tv.settings.system.DateTimeActivity";

    @Override
    void build() {
        header("System", "Date & time", "Yeti TV sets the clock from the internet when it's connected.");

        boolean h24 = DateFormat.is24HourFormat(this);
        TimeZone tz = TimeZone.getDefault();
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setPadding(Ui.s(4), 0, 0, Ui.s(28));
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        style(time, 96, Ui.TEXT);
        clock.addView(time);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM yyyy");
        date.setFormat24Hour("EEEE, d MMMM yyyy");
        style(date, 28, Ui.TEXT_DIM);
        date.setPadding(0, Ui.s(10), 0, 0);
        clock.addView(date);
        summaryView(clock);

        boolean auto = global(Settings.Global.AUTO_TIME);
        boolean autoZone = global(Settings.Global.AUTO_TIME_ZONE);
        summary("Time zone", Ui.ACCENT, zoneName(tz));
        detail("Offset", offset(tz));
        detail("Clock", auto ? "Set automatically" : "Set manually");

        section("Automatic");
        toggle("auto", "Automatic date & time", "Use the time from the internet", auto, on -> putGlobal(Settings.Global.AUTO_TIME, on));
        toggle("autozone", "Automatic time zone", "Use the time zone from the network", autoZone, on -> putGlobal(Settings.Global.AUTO_TIME_ZONE, on));

        section("Format");
        toggle("24h", "Use 24-hour clock", h24 ? "13:00" : "1:00 PM", h24, on -> {
            if (!Settings.System.canWrite(this)) { handOff(); return false; }
            Settings.System.putString(getContentResolver(), Settings.System.TIME_12_24, on ? "24" : "12");
            return true;
        });

        section("More");
        action("zone", "Time zone", zoneName(tz), true, () -> Ui.openTvSettings(this, TV_DATE));
        action("manual", "Set date & time by hand", "Android TV date & time settings", true, () -> Ui.openTvSettings(this, TV_DATE));
    }

    private boolean global(String k) { return Settings.Global.getInt(getContentResolver(), k, 1) == 1; }

    /** Needs WRITE_SECURE_SETTINGS (granted over adb by tools/dev.ps1); otherwise Android TV settings does it. */
    private boolean putGlobal(String k, boolean on) {
        try {
            return Settings.Global.putInt(getContentResolver(), k, on ? 1 : 0);
        } catch (SecurityException e) {
            handOff();
            return false;
        }
    }

    private void handOff() {
        dialog("Change this in Settings", "Yeti TV isn't allowed to change this on this box. Android TV's date & time settings can.",
                "Open settings", (Runnable) () -> Ui.openTvSettings(this, TV_DATE), "Cancel", null);
    }

    private static String zoneName(TimeZone tz) {
        String id = tz.getID();
        String city = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1).replace('_', ' ') : id;
        return city + "  ·  " + tz.getDisplayName(tz.inDaylightTime(new java.util.Date()), TimeZone.LONG, Locale.getDefault());
    }

    private static String offset(TimeZone tz) {
        int m = tz.getOffset(System.currentTimeMillis()) / 60000;
        return String.format(Locale.US, "GMT%s%02d:%02d", m < 0 ? "-" : "+", Math.abs(m) / 60, Math.abs(m) % 60);
    }

    private void style(TextClock t, float px, int color) {
        TextView model = Ui.text(this, "", px, color, true);
        Ui.textSize(t, px);
        t.setTextColor(color);
        t.setTypeface(model.getTypeface());
        t.setIncludeFontPadding(false);
    }
}
