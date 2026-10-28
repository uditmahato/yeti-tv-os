package tv.yeti.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wi-Fi scanning and connecting for the built-in picker.
 *
 * addNetwork/enableNetwork only work for apps targeting API 28 or lower (see build.gradle.kts). They can only
 * enable or forget networks this app saved; networks saved by Android TV Settings are handed back to Settings.
 */
final class Wifi {
    static final int OPEN = 0, WEP = 1, PSK = 2, SAE = 3, EAP = 4;

    static final class Net {
        String ssid;
        int rssi = Integer.MIN_VALUE, level, security, freq, netId = -1;
        boolean saved, connected;
    }

    private Wifi() { }

    static WifiManager manager(Context c) { return (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE); }

    /** "\"name\"" -> "name"; null for hidden/unknown SSIDs. */
    static String cleanSsid(String s) {
        if (s == null || s.isEmpty() || "<unknown ssid>".equals(s) || "0x".equals(s)) return null;
        if (s.length() > 1 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        return s.isEmpty() ? null : s;
    }

    static int security(String caps) {
        if (caps == null) return OPEN;
        if (caps.contains("EAP")) return EAP;
        if (caps.contains("PSK")) return PSK;          // includes WPA2/WPA3 transition mode
        if (caps.contains("SAE")) return SAE;
        if (caps.contains("WEP")) return WEP;
        return OPEN;
    }

    static int security(WifiConfiguration c) {
        if (c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP) || c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) return EAP;
        if (c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) return PSK;
        if (Build.VERSION.SDK_INT >= 29 && c.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.SAE)) return SAE;
        if (c.wepKeys != null && c.wepKeys[0] != null) return WEP;
        return OPEN;
    }

    static String securityName(int s) {
        switch (s) {
            case WEP: return "WEP";
            case PSK: return "WPA/WPA2";
            case SAE: return "WPA3";
            case EAP: return "Enterprise";
            default: return "Open";
        }
    }

    static String signalName(int level) {
        return level >= 4 ? "Excellent" : level == 3 ? "Good" : level == 2 ? "Fair" : "Weak";
    }

    /** Visible networks, one entry per SSID (strongest access point), connected first, then saved, then by signal. */
    static List<Net> networks(WifiManager wm) {
        Map<String, Net> by = new HashMap<>();
        WifiInfo info = wm.getConnectionInfo();
        String current = info == null ? null : cleanSsid(info.getSSID());
        Map<String, WifiConfiguration> saved = new HashMap<>();
        try {
            List<WifiConfiguration> cfg = wm.getConfiguredNetworks();
            if (cfg != null) for (WifiConfiguration c : cfg) { String s = cleanSsid(c.SSID); if (s != null) saved.put(s, c); }
        } catch (SecurityException ignored) { }
        List<ScanResult> results;
        try { results = wm.getScanResults(); } catch (SecurityException e) { results = new ArrayList<>(); }
        if (results != null) for (ScanResult r : results) {
            String s = cleanSsid(r.SSID);
            if (s == null) continue;
            Net n = by.get(s);
            if (n == null) { n = new Net(); n.ssid = s; by.put(s, n); }
            if (r.level > n.rssi) { n.rssi = r.level; n.freq = r.frequency; n.security = security(r.capabilities); }
        }
        for (Net n : by.values()) {
            n.level = WifiManager.calculateSignalLevel(n.rssi, 5);
            WifiConfiguration c = saved.get(n.ssid);
            if (c != null) { n.saved = true; n.netId = c.networkId; }
            n.connected = n.ssid.equals(current) && info.getNetworkId() != -1;
        }
        List<Net> out = new ArrayList<>(by.values());
        Collections.sort(out, (a, b) -> {
            if (a.connected != b.connected) return a.connected ? -1 : 1;
            if (a.saved != b.saved) return a.saved ? -1 : 1;
            return Integer.compare(b.rssi, a.rssi);
        });
        return out;
    }

    private static String quote(String s) { return "\"" + s + "\""; }

    private static boolean hex(String s, int... lengths) {
        for (int l : lengths) if (s.length() == l && s.matches("[0-9A-Fa-f]+")) return true;
        return false;
    }

    /**
     * Connects to {@code n}. {@code password} is null for open networks or to reuse a saved network.
     * Returns the network id, or -1 if Android refused (e.g. the network was saved by another app).
     */
    static int connect(WifiManager wm, Net n, String password) {
        int id = -1;
        if (password == null && n.saved) {
            id = n.netId;
        } else {
            WifiConfiguration c = new WifiConfiguration();
            c.SSID = quote(n.ssid);
            switch (n.security) {
                case WEP:
                    c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                    c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                    c.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
                    c.wepKeys[0] = hex(password, 10, 26, 58) ? password : quote(password);
                    c.wepTxKeyIndex = 0;
                    break;
                case PSK:
                    c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
                    c.preSharedKey = hex(password, 64) ? password : quote(password);
                    break;
                case SAE:
                    if (Build.VERSION.SDK_INT >= 30) c.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                    else c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.SAE);
                    c.preSharedKey = quote(password);
                    break;
                default:
                    c.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }
            id = wm.addNetwork(c);
        }
        if (id == -1) return -1;
        if (!wm.enableNetwork(id, true)) return -1;
        wm.reconnect();
        return id;
    }

    /** First IPv4 address of the first network with this transport (NetworkCapabilities.TRANSPORT_*), or null. */
    static String ipv4(Context c, int transport) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null || !nc.hasTransport(transport)) continue;
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (LinkAddress a : lp.getLinkAddresses())
                    if (a.getAddress() instanceof Inet4Address) return a.getAddress().getHostAddress();
            }
        } catch (Exception ignored) { }
        return null;
    }
}
