package com.acho.chat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MessageCheckService extends Service {

    private static final String CHANNEL_ID    = "acho_notifications";
    private static final String CHANNEL_BG_ID = "acho_bg";
    private static final String CONFIG_URL    = "https://raw.githubusercontent.com/jorgecobos14/acho-config/main/url.txt";
    private static final String PREFS         = "acho_prefs";
    private static final String KEY_TOKEN     = "auth_token";
    private static final String KEY_LAST      = "last_msg_ts";
    private static final String KEY_LAST_POST = "last_post_ts";
    private boolean running = true;
    private int notifId = 2000;
    private String cachedServerUrl = null;
    private long lastUrlFetch = 0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("token")) {
            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_TOKEN, intent.getStringExtra("token")).apply();
        }
        createChannels();
        startForeground(1999, buildForegroundNotification());
        new Thread(this::loopMessages).start();
        new Thread(this::loopWidget).start();
        return START_STICKY;
    }

    // ── Mensajes privados ────────────────────────────────────────
    private void loopMessages() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        while (running) {
            try {
                Thread.sleep(2000);
                String token = prefs.getString(KEY_TOKEN, null);
                if (token == null) continue;

                String serverUrl = getServerUrl();
                if (serverUrl == null) continue;

                URL url = new URL(serverUrl + "/api/messages/check");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("X-Auth-Token", token);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (conn.getResponseCode() != 200) continue;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("ok", false)) continue;

                JSONArray msgs = json.getJSONArray("messages");
                if (msgs.length() == 0) continue;

                String lastTs     = prefs.getString(KEY_LAST, "");
                JSONObject newest = msgs.getJSONObject(0);
                String newestTs   = newest.optString("created_at", "");

                if (!newestTs.equals(lastTs)) {
                    prefs.edit().putString(KEY_LAST, newestTs).apply();
                    String sender = newest.optString("sender", "Alguien");
                    String text   = newest.optString("text", "");
                    showNotification(sender, text.isEmpty() ? "Te envió un archivo" : text);
                }
            } catch (Exception e) {
                // ignorar
            }
        }
    }

    // ── Widget comunidad ─────────────────────────────────────────
    private void loopWidget() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        while (running) {
            try {
                Thread.sleep(3000);
                String serverUrl = getServerUrl();
                if (serverUrl == null) continue;

                URL url = new URL(serverUrl + "/api/community/posts");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                if (conn.getResponseCode() != 200) continue;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray posts = new JSONArray(sb.toString());
                if (posts.length() == 0) continue;

                String lastPost   = prefs.getString(KEY_LAST_POST, "");
                String newestPost = posts.getJSONObject(0).optString("created_at", "");

                if (!newestPost.equals(lastPost)) {
                    prefs.edit().putString(KEY_LAST_POST, newestPost).apply();
                    triggerWidgetUpdate();
                }
            } catch (Exception e) {
                // ignorar
            }
        }
    }

    private void triggerWidgetUpdate() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName component = new ComponentName(this, CommunityWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids.length == 0) return;
        Intent intent = new Intent(this, CommunityWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private String getServerUrl() {
        long now = System.currentTimeMillis();
        if (cachedServerUrl != null && now - lastUrlFetch < 60000) return cachedServerUrl;
        String url = fetchLine(CONFIG_URL);
        if (url != null) {
            cachedServerUrl = url;
            lastUrlFetch    = now;
        }
        return cachedServerUrl;
    }

    private String fetchLine(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line = reader.readLine();
            reader.close();
            return line != null ? line.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void showNotification(String title, String body) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
               .setContentTitle(title)
               .setContentText(body)
               .setAutoCancel(true)
               .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId++, builder.build());
    }

    private Notification buildForegroundNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_BG_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Acho")
                .setContentText("Escuchando mensajes...")
                .build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Acho", NotificationManager.IMPORTANCE_DEFAULT));
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_BG_ID, "Acho Background", NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public void onDestroy() { running = false; super.onDestroy(); }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

