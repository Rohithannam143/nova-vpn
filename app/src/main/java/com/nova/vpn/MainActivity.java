package com.nova.vpn;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;

import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_VPN_PERMISSION = 4101;
    private static final int REQUEST_CONFIG_FILE = 4102;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private GoBackend backend;
    private Config pendingConfig;
    private Config activeConfig;
    private boolean connected = false;

    private TextView status;
    private TextView configLabel;
    private TextView detail;
    private Button connectButton;

    private final Tunnel tunnel = new Tunnel() {
        @Override public String getName() { return "nova"; }
        @Override public void onStateChange(State newState) {
            runOnUiThread(() -> renderState(newState == State.UP));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        backend = new GoBackend(getApplicationContext());
        buildInterface();
        renderState(false);
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(34), dp(24), dp(24));
        root.setBackgroundColor(Color.rgb(8, 13, 24));
        scroll.addView(root);

        TextView brand = text("NOVA", 30, Color.WHITE, true);
        brand.setLetterSpacing(0.18f);
        root.addView(brand);

        TextView subtitle = text("PRIVATE CONNECTION", 11, Color.rgb(102, 217, 239), true);
        subtitle.setLetterSpacing(0.16f);
        addGap(root, 28);
        root.addView(subtitle);

        TextView title = text("Your network.\nYour control.", 30, Color.WHITE, true);
        addGap(root, 12);
        root.addView(title);

        status = text("DISCONNECTED", 13, Color.rgb(255, 190, 100), true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(18), 0, dp(18));
        status.setBackground(roundRect(Color.rgb(20, 29, 45), dp(16)));
        addGap(root, 28);
        root.addView(status, matchWrap());

        connectButton = button("CONNECT");
        addGap(root, 16);
        root.addView(connectButton, matchWrap());
        connectButton.setOnClickListener(v -> toggleTunnel());

        TextView serverTitle = text("CONFIGURATION", 12, Color.rgb(150, 166, 190), true);
        addGap(root, 34);
        root.addView(serverTitle);

        configLabel = text("No WireGuard profile imported", 15, Color.WHITE, false);
        addGap(root, 10);
        root.addView(configLabel);

        Button importButton = button("IMPORT WIREGUARD FILE");
        addGap(root, 14);
        root.addView(importButton, matchWrap());
        importButton.setOnClickListener(v -> openConfigPicker());

        detail = text("Import a .conf file from a WireGuard VPN server to connect. NOVA does not include a VPN server subscription or gateway yet.", 13, Color.rgb(150, 166, 190), false);
        detail.setLineSpacing(dp(4), 1f);
        addGap(root, 24);
        root.addView(detail);

        TextView footer = text("WIREGUARD • ENCRYPTED TUNNEL", 10, Color.rgb(95, 111, 137), true);
        footer.setGravity(Gravity.CENTER);
        addGap(root, 38);
        root.addView(footer);

        setContentView(scroll);
    }

    private void openConfigPicker() {
        if (connected) {
            Toast.makeText(this, "Disconnect before changing profiles", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CONFIG_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONFIG_FILE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                if (input == null) throw new IllegalStateException("Could not open selected file");
                Config parsed = Config.parse(input);
                if (parsed.getPeers().isEmpty()) throw new IllegalArgumentException("Profile has no [Peer] section");
                pendingConfig = parsed;
                activeConfig = parsed;
                configLabel.setText("WireGuard profile imported • " + parsed.getPeers().size() + " peer(s)");
                detail.setText("Profile is held in memory for this app session. Private keys are not written to app storage.");
                Toast.makeText(this, "WireGuard profile imported", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                pendingConfig = null;
                activeConfig = null;
                configLabel.setText("No valid profile loaded");
                Toast.makeText(this, "Invalid WireGuard profile: " + safeMessage(e), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_VPN_PERMISSION && resultCode == RESULT_OK) {
            startTunnel();
        } else if (requestCode == REQUEST_VPN_PERMISSION) {
            Toast.makeText(this, "VPN permission was not granted", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTunnel() {
        if (connected) {
            stopTunnel();
            return;
        }
        if (pendingConfig == null) {
            Toast.makeText(this, "Import a WireGuard .conf profile first", Toast.LENGTH_LONG).show();
            return;
        }
        Intent permissionIntent = VpnService.prepare(this);
        if (permissionIntent != null) {
            startActivityForResult(permissionIntent, REQUEST_VPN_PERMISSION);
        } else {
            startTunnel();
        }
    }

    private void startTunnel() {
        if (pendingConfig == null) return;
        final Config config = pendingConfig;
        setBusy("CONNECTING", "Starting WireGuard userspace tunnel…");
        worker.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config);
                runOnUiThread(() -> {
                    activeConfig = config;
                    renderState(true);
                    detail.setText("WireGuard tunnel started. Confirm your gateway handshake and public IP using a trusted independent test before relying on this connection.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    renderState(false);
                    detail.setText("Tunnel could not start. Check the profile, endpoint reachability, and Android VPN permission.");
                    Toast.makeText(this, "Connection failed: " + safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void stopTunnel() {
        setBusy("DISCONNECTING", "Stopping VPN tunnel…");
        worker.execute(() -> {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null);
                runOnUiThread(() -> renderState(false));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    renderState(false);
                    Toast.makeText(this, "Disconnect error: " + safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setBusy(String label, String message) {
        status.setText(label);
        connectButton.setEnabled(false);
        connectButton.setText(label);
        detail.setText(message);
    }

    private void renderState(boolean isConnected) {
        connected = isConnected;
        status.setText(isConnected ? "WIREGUARD ACTIVE" : "DISCONNECTED");
        status.setTextColor(isConnected ? Color.rgb(112, 232, 174) : Color.rgb(255, 190, 100));
        connectButton.setText(isConnected ? "DISCONNECT" : "CONNECT");
        connectButton.setEnabled(true);
        connectButton.setAlpha(1f);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(8, 13, 24));
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setBackground(roundRect(Color.rgb(102, 217, 239), dp(14)));
        b.setPadding(dp(12), dp(12), dp(12), dp(12));
        return b;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private void addGap(LinearLayout root, int height) {
        View gap = new View(this);
        root.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) return e.getClass().getSimpleName();
        // Avoid showing config content or key material in UI errors.
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }
}
