package com.acho.chat.app;

import android.app.Activity;
import android.net.http.SslError;
import android.os.Bundle;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private WebView webView;
    private static final String HOME_URL = "https://dispatch-commitment-bedrooms-chem.trycloudflare.com";
    private static final String ALLOWED_HOST = "buys-suggested-motion-healthy.trycloudflare.com";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        // ✅ JavaScript necesario para tu app
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // 🔒 Deshabilitar funciones innecesarias y riesgosas
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setGeolocationEnabled(false);
        settings.setSaveFormData(false);
        settings.setSavePassword(false);

        // 🔒 Forzar HTTPS
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {

            // 🔒 Solo permite cargar tu dominio
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                if (host != null && host.equals(ALLOWED_HOST)) {
                    return false; // Permitir
                }
                return true; // Bloquear cualquier otro dominio
            }

            // 🔒 Manejo estricto de errores SSL
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // NUNCA usar handler.proceed() — cancela en caso de error SSL
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl(HOME_URL);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
