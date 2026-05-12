package com.acho.chat.app;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {
    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String CONFIG_URL  = "https://raw.githubusercontent.com/jorgecobos14/acho-config/main/url.txt";
    private String HOME_URL     = null;
    private String ALLOWED_HOST = null;
    private static final int REQ_STORAGE      = 1002;
    private static final int REQ_FILE_CHOOSER = 1003;
    private static final int REQ_NOTIFICATION = 1004;
    private static final String CHANNEL_ID    = "acho_notifications";
    private int notificationId = 1;
    private boolean running = true;

    // Contenido pendiente de compartir
    private String pendingShareText = null;
    private Uri pendingShareUri     = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        webView = new WebView(this);
        fullscreenContainer.addView(webView);
        setContentView(fullscreenContainer);
        createNotificationChannel();
        requestStoragePermissions();
        requestNotificationPermission();
        setupServiceWorker();
        setupWebView();

        // Manejar intent de compartir al abrir
        handleShareIntent(getIntent());

        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(CONFIG_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream())
                );
                String serverUrl = reader.readLine().trim();
                reader.close();
                HOME_URL     = serverUrl;
                ALLOWED_HOST = serverUrl.replace("https://", "").replace("http://", "");
                runOnUiThread(() -> webView.loadUrl(HOME_URL));
            } catch (Exception e) {
                runOnUiThread(() -> webView.loadData(
                    "<h2>No se pudo conectar al servidor.<br>Intenta de nuevo.</h2>",
                    "text/html", "utf-8"
                ));
            }
        }).start();

        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(5000);
                    java.net.URL url = new java.net.URL(CONFIG_URL);
                    java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream())
                    );
                    String newUrl = reader.readLine().trim();
                    reader.close();
                    if (HOME_URL != null && !newUrl.equals(HOME_URL)) {
                        HOME_URL     = newUrl;
                        ALLOWED_HOST = newUrl.replace("https://", "").replace("http://", "");
                        runOnUiThread(() -> webView.loadUrl(HOME_URL));
                    }
                } catch (Exception e) { }
            }
        }).start();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShareIntent(intent);
    }

    private void handleShareIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String type   = intent.getType();
        if (!Intent.ACTION_SEND.equals(action) || type == null) return;

        if (type.startsWith("text/")) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) pendingShareText = text;
        } else if (type.startsWith("image/") || type.startsWith("video/") ||
                   type.startsWith("audio/") || type.equals("application/pdf") ||
                   type.equals("text/plain")) {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) pendingShareUri = uri;
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (extraText != null) pendingShareText = extraText;
        }
    }

    private void deliverPendingShare() {
        if (pendingShareText != null) {
            String escaped = pendingShareText
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "");
            webView.evaluateJavascript(
                "if(window.receiveSharedContent) window.receiveSharedContent('" + escaped + "', null);",
                null
            );
            pendingShareText = null;
        }
        if (pendingShareUri != null) {
            String uriStr = pendingShareUri.toString();
            webView.evaluateJavascript(
                "if(window.receiveSharedContent) window.receiveSharedContent(null, '" + uriStr + "');",
                null
            );
            pendingShareUri = null;
        }
    }

    @Override
    protected void onDestroy() {
        running = false;
        if (webView != null) {
            webView.stopLoading();
            webView.clearCache(false);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void setupServiceWorker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    return null;
                }
            });
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Acho", NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notificaciones de Acho Chat");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATION);
            }
        }
    }

    public class AchoBridge {

        @JavascriptInterface
        public void startBackgroundService(String token) {
            SharedPreferences prefs = getSharedPreferences("acho_prefs", Context.MODE_PRIVATE);
            prefs.edit().putString("auth_token", token).apply();
            Intent intent = new Intent(MainActivity.this, MessageCheckService.class);
            intent.putExtra("token", token);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        @JavascriptInterface
        public void showNotification(String title, String body) {
            Intent intent = new Intent(MainActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                MainActivity.this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(MainActivity.this, CHANNEL_ID);
            } else {
                builder = new Notification.Builder(MainActivity.this);
            }
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                   .setContentTitle(title)
                   .setContentText(body)
                   .setAutoCancel(true)
                   .setContentIntent(pendingIntent);
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(notificationId++, builder.build());
        }

        @JavascriptInterface
        public void shareContent(String text) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(shareIntent, "Compartir via"));
        }

        @JavascriptInterface
        public void shareUrl(String url, String title) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, url);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            startActivity(Intent.createChooser(shareIntent, "Compartir via"));
        }

        @JavascriptInterface
        public void pageReady() {
            runOnUiThread(() -> deliverPendingShare());
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.addJavascriptInterface(new AchoBridge(), "AchoApp");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                return host == null || !host.equals(ALLOWED_HOST);
            }
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (ALLOWED_HOST != null && error.getUrl().contains(ALLOWED_HOST)) handler.proceed();
                else handler.cancel();
            }
            @Override
            public void onPageFinished(WebView view, String url) {
                // Avisar a la app que la página cargó
                view.evaluateJavascript("if(window.AchoApp) AchoApp.pageReady();", null);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(customView);
                webView.setVisibility(View.GONE);
            }
            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                fullscreenContainer.removeView(customView);
                customView = null;
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
                webView.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                request.deny();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER && filePathCallback != null) {
            Uri[] results = (resultCode == Activity.RESULT_OK && data != null)
                    ? new Uri[]{data.getData()} : null;
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] perms = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            };
            boolean needsRequest = false;
            for (String p : perms) {
                if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                    needsRequest = true; break;
                }
            }
            if (needsRequest) requestPermissions(perms, REQ_STORAGE);
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            fullscreenContainer.removeView(customView);
            customView = null;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
            webView.setVisibility(View.VISIBLE);
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() { super.onPause(); if (webView != null) webView.onPause(); }

    @Override
    protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }
}

