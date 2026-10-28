package tv.yeti.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.List;

/** Network & internet: what the box is connected through, its addresses, Wi-Fi picker entry, Ethernet details. */
public class NetworkActivity extends SettingsPage {

    private BroadcastReceiver receiver;

    /** One connected transport. */
    private static final class Link {
        boolean up, internet;
        String iface, ip, gateway, dns;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        receiver = new BroadcastReceiver() { @Override public void onReceive(Context c, Intent i) { rebuild(); } };
        IntentFilter f = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        registerReceiver(receiver, f);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        super.onDestroy();
    }

    @Override
    void build() {
        header("Network", "Network & internet", "How Yeti TV connects to the internet.");

        Link wifi = link(NetworkCapabilities.TRANSPORT_WIFI), eth = link(NetworkCapabilities.TRANSPORT_ETHERNET);
        WifiManager wm = Wifi.manager(this);
        WifiInfo wi = wm.getConnectionInfo();
        String ssid = wi == null ? null : Wifi.cleanSsid(wi.getSSID());

        Link main = eth.up ? eth : wifi;
        if (main.up) {
            summary(main.internet ? "Online" : "No internet", main.internet ? Ui.GOOD : Ui.WARN,
                    eth.up ? "Ethernet" : (ssid != null ? ssid : "Wi-Fi"));
            detail("IP address", main.ip);
            detail("Router", main.gateway);
            detail("DNS", main.dns);
        } else {
            summary("Offline", Ui.TEXT_DIM, "Not connected");
            detail("Wi-Fi", wm.isWifiEnabled() ? "On, not connected" : "Off");
            detail("Ethernet", "Cable unplugged");
        }

        section("Connections");
        String wv = !wm.isWifiEnabled() ? "Off" : wifi.up ? "Connected to " + (ssid != null ? ssid : "Wi-Fi") : "On, not connected";
        action("wifi", "Wi-Fi", wv, wifi.up ? Ui.GOOD : Ui.TEXT_DIM, true, () -> Ui.start(this, new Intent(this, WifiActivity.class)));
        String ev = eth.up ? (eth.internet ? "Connected" : "Connected, no internet") + speed() : "Cable unplugged";
        action("eth", "Ethernet", ev, eth.up ? Ui.GOOD : Ui.TEXT_DIM, false, () -> {
            if (!eth.up) dialog("Ethernet", "Plug a network cable into the box's LAN port. It connects by itself.", "OK", null);
        });

        section("This box");
        String name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        action("name", "Device name", name != null ? name : "Yeti TV", true,
                () -> Ui.openTvSettings(this, "com.android.tv.settings.name.DeviceNameSettingsActivity"));
        info("Wi-Fi MAC", mac("wlan0"));
        info("Ethernet MAC", mac("eth0"));

        section("More");
        action("advanced", "Proxy, static IP & more", "Android TV network settings", true,
                () -> Ui.openTvSettings(this, "com.android.tv.settings.connectivity.NetworkActivity"));
    }

    private Link link(int transport) {
        Link l = new Link();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null || !nc.hasTransport(transport)) continue;
                l.up = true;
                l.internet = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) break;
                l.iface = lp.getInterfaceName();
                for (LinkAddress a : lp.getLinkAddresses())
                    if (a.getAddress() instanceof Inet4Address) { l.ip = a.getAddress().getHostAddress(); break; }
                for (RouteInfo r : lp.getRoutes())
                    if (r.isDefaultRoute() && r.getGateway() instanceof Inet4Address) { l.gateway = r.getGateway().getHostAddress(); break; }
                List<String> dns = new ArrayList<>();
                for (InetAddress d : lp.getDnsServers()) if (d instanceof Inet4Address) dns.add(d.getHostAddress());
                if (!dns.isEmpty()) l.dns = android.text.TextUtils.join(", ", dns);
                break;
            }
        } catch (Exception ignored) { }
        return l;
    }

    /** Link speed of the Ethernet port, e.g. " · 100 Mbps" (empty if unknown). */
    private static String speed() {
        try (BufferedReader r = new BufferedReader(new FileReader("/sys/class/net/eth0/speed"))) {
            int s = Integer.parseInt(r.readLine().trim());
            return s > 0 ? "  ·  " + s + " Mbps" : "";
        } catch (Exception e) { return ""; }
    }

    /** From sysfs: Android 11 hides NetworkInterface.getHardwareAddress() from apps. */
    private static String mac(String iface) {
        try (BufferedReader r = new BufferedReader(new FileReader("/sys/class/net/" + iface + "/address"))) {
            String m = r.readLine();
            if (m != null && m.matches("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}")) return m.toUpperCase();
        } catch (Exception ignored) { }
        try {
            NetworkInterface ni = NetworkInterface.getByName(iface);
            byte[] b = ni == null ? null : ni.getHardwareAddress();
            if (b == null) return "Unknown";
            StringBuilder s = new StringBuilder();
            for (byte x : b) s.append(s.length() > 0 ? ":" : "").append(String.format("%02X", x));
            return s.toString();
        } catch (Exception e) { return "Unknown"; }
    }
}
