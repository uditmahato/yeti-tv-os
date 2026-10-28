package tv.yeti.launcher;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.provider.Settings;

/** Sound: TV volume, mute, remote click sounds, where audio goes. */
public class SoundActivity extends SettingsPage {

    private AudioManager am;

    @Override
    void build() {
        if (am == null) am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        header("Sound", "Sound", "Focus Volume and press left or right to change it.");

        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int vol = am.getStreamVolume(AudioManager.STREAM_MUSIC);
        boolean muted = am.isStreamMute(AudioManager.STREAM_MUSIC);
        summary("Audio output", Ui.GOOD, output());
        detail("Volume", muted ? "Muted" : Math.round(100f * vol / max) + "%");
        detail("Remote clicks", clicks() ? "On" : "Off");

        section("Volume");
        slider("volume", "Volume", vol, max, 1, v -> {
            if (am.isStreamMute(AudioManager.STREAM_MUSIC)) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0);
        }, v -> Math.round(100f * v / max) + "%");
        toggle("mute", "Mute", "Silence all sound", muted, on -> {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, on ? AudioManager.ADJUST_MUTE : AudioManager.ADJUST_UNMUTE, 0);
            return true;
        });

        section("Sounds");
        toggle("clicks", "Remote click sounds", "Play a sound when you press the remote", clicks(), on -> {
            if (!Settings.System.canWrite(this)) { needsPermission(); return false; }
            Settings.System.putInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, on ? 1 : 0);
            if (on) am.loadSoundEffects(); else am.unloadSoundEffects();
            return true;
        });

        section("More");
        action("advanced", "Surround sound & audio formats", "Android TV sound settings", true,
                () -> Ui.openTvSettings(this, "com.android.tv.settings.SoundActivity"));
    }

    private boolean clicks() {
        return Settings.System.getInt(getContentResolver(), Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1;
    }

    private String output() {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_HDMI: return "TV speakers (HDMI)";
                case AudioDeviceInfo.TYPE_HDMI_ARC: return "Sound system (HDMI ARC)";
                case AudioDeviceInfo.TYPE_USB_DEVICE: case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB audio";
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth";
                default:
            }
        }
        return "TV";
    }

    private void needsPermission() {
        dialog("Can't change this here", "Yeti TV isn't allowed to change system sounds on this box. You can change it in Android TV settings.",
                "Open settings", (Runnable) () -> Ui.openTvSettings(this, "com.android.tv.settings.SoundActivity"), "Cancel", null);
    }
}
