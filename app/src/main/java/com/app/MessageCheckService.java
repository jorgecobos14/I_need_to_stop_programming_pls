package com.acho.chat.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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

    private static final String CHANNEL_ID     = "acho_notifications";
    private static final String CHANNEL_BG_ID  = "acho_bg";
    private static final String CONFIG_URL      = "https://raw.githubusercontent.com/jorgecobos14/acho-config/main/url.txt";
    private static final String PREFS           = "acho_prefs";
    private static final String KEY_TOKEN       = "auth_token";
    private static final String KEY_LAST        = "last_msg_ts";
    private boolean running = true;
    private int notifId = 2000;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("token")) {
            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_TOKEN, intent.getStringExtra("token")).apply();
        }
        createChannels();
        startForeground(1999, buildForegroundNotification());
        new Thread(this::loop).start();
        return START_STICKY;
    }

    private void loop() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        while (running) {
            try {
                Thread.sleep(30000);
                String token = prefs.getString(KEY_TOKEN, null);
                if (token == null) continue;

                String serverUrl = fetchLine(CONFIG_URL);
                if (serverUrl == null) continue;

                URL url = new URL(serverUrl + "/api/messages/check");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("X-Auth-Token", token);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() != 200) continue;

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
                );
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                if (!json.optBoolean("ok", false)) continue;

                JSONArray msgs = json.getJSONArray("messages");
                if (msgs.length() == 0) continue;

                String lastTs  = prefs.getString(KEY_LAST, "");
                JSONObject newest = msgs.getJSONObject(0);
                String newestTs   = newest.optString("created_at", "");

                if (!newestTs.equals(lastTs)) {
                    prefs.edit().putString(KEY_LAST, newestTs).apply();
                    String sender = newest.optString("sender", "Alguien");
                    String text   = newest.optString("text", "");
                    showNotification(sender, text.isEmpty() ? "Te envió un archivo" : text);
                }

            } catch (Exception e) {
            }
        }
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
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
               .setContentTitle(title)
               .setContentText(body)
               .setAutoCancel(true)
               .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(notifId++, builder.build());
    }

    private Notification buildForegroundNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_BG_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Acho")
            .setContentText("Escuchando mensajes...")
            .build();
    }

    private void createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;
            nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Acho", NotificationManager.IMPORTANCE_DEFAULT
            ));
            NotificationChannel bg = new NotificationChannel(
                CHANNEL_BG_ID, "Acho Background",
                NotificationManager.IMPORTANCE_LOW
            );
            nm.createNotificationChannel(bg);
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}

