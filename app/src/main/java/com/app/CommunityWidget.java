package com.acho.chat.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CommunityWidget extends AppWidgetProvider {

    private static final String CONFIG_URL = "https://raw.githubusercontent.com/jorgecobos14/acho-config/main/url.txt";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        new Thread(() -> {
            try {
                // Obtener URL del servidor
                String serverUrl = fetchLine(CONFIG_URL);
                if (serverUrl == null) return;

                // Obtener posts recientes
                URL url = new URL(serverUrl + "/api/community/posts");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                if (conn.getResponseCode() != 200) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray posts = new JSONArray(sb.toString());

                // Determinar si es widget grande o pequeño
                android.appwidget.AppWidgetProviderInfo info = appWidgetManager.getAppWidgetInfo(widgetId);
                boolean isSmall = info != null && info.minWidth < 250;

                RemoteViews views;
                if (isSmall) {
                    views = buildSmallWidget(context, posts);
                } else {
                    views = buildLargeWidget(context, posts);
                }

                // Intent para abrir la app al tocar el widget
                Intent intent = new Intent(context, MainActivity.class);
                PendingIntent pi = PendingIntent.getActivity(context, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                views.setOnClickPendingIntent(android.R.id.background, pi);

                new Handler(Looper.getMainLooper()).post(() ->
                        appWidgetManager.updateAppWidget(widgetId, views));

            } catch (Exception e) {
                // ignorar errores
            }
        }).start();
    }

    private RemoteViews buildLargeWidget(Context context, JSONArray posts) throws Exception {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_community_large);

        for (int i = 0; i < Math.min(posts.length(), 3); i++) {
            JSONObject post = posts.getJSONObject(i);
            String author = post.optString("author", "");
            String text   = post.optString("text", "");
            String time   = post.optString("created_at", "").replace("T", " ").substring(0, Math.min(16, post.optString("created_at","").length()));
            String initials = getInitials(author);

            if (text.isEmpty()) text = "📎 Archivo adjunto";
            if (text.length() > 40) text = text.substring(0, 40) + "...";

            int avatarId, authorId, textId, timeId;
            switch (i) {
                case 0:
                    avatarId = R.id.avatar1; authorId = R.id.author1;
                    textId = R.id.text1; timeId = R.id.time1;
                    break;
                case 1:
                    avatarId = R.id.avatar2; authorId = R.id.author2;
                    textId = R.id.text2; timeId = R.id.time2;
                    break;
                default:
                    avatarId = R.id.avatar3; authorId = R.id.author3;
                    textId = R.id.text3; timeId = R.id.time3;
                    break;
            }

            views.setTextViewText(avatarId, initials);
            views.setTextViewText(authorId, author);
            views.setTextViewText(textId, text);
            views.setTextViewText(timeId, time);
        }

        views.setTextViewText(R.id.badge, posts.length() + " NUEVO");
        return views;
    }

    private RemoteViews buildSmallWidget(Context context, JSONArray posts) throws Exception {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_community_small);

        for (int i = 0; i < Math.min(posts.length(), 2); i++) {
            JSONObject post = posts.getJSONObject(i);
            String author = post.optString("author", "");
            String text   = post.optString("text", "");
            String initials = getInitials(author);

            if (text.isEmpty()) text = "📎 Archivo";
            if (text.length() > 25) text = text.substring(0, 25) + "...";

            int avatarId, authorId, textId;
            if (i == 0) {
                avatarId = R.id.avatar1; authorId = R.id.author1; textId = R.id.text1;
            } else {
                avatarId = R.id.avatar2; authorId = R.id.author2; textId = R.id.text2;
            }

            views.setTextViewText(avatarId, initials);
            views.setTextViewText(authorId, author);
            views.setTextViewText(textId, text);
        }

        views.setTextViewText(R.id.badge, String.valueOf(posts.length()));
        return views;
    }

    private String getInitials(String name) {
        if (name == null || name.isEmpty()) return "?";
        String[] parts = name.trim().split("[_\\-. ]+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
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
}

