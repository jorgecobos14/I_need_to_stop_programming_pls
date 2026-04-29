package com.acho.chat.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

public class MainActivity extends Activity {

    private WebView webView;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    private static final String HOME_URL = "https://refers-advocacy-yards-beginner.trycloudflare.com";
    private static final String ALLOWED_HOST = "refers-advocacy-yards-beginner.trycloudflare.com";

    private static final int REQ_MICROPHONE = 1001;
    private static final int REQ_STORAGE    = 1002;

    private PermissionRequest pendingPermissionRequest;

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

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setEnableSmoothTransition(true);

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

        requestStoragePermissions();

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String host = request.getUrl().getHost();
                return host == null || !host.equals(ALLOWED_HOST);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                if (error.getUrl().contains(ALLOWED_HOST)) {
                    handler.proceed();
                } else {
                    handler.cancel();
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                fullscreenContainer.addView(customView);
                webView.setVisibility(View.GONE);
            }

            @Override
            public void onHideCustomView() {
                if (customView != null) {
                    fullscreenContainer.removeView(customView);
                    customView = null;
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                    webView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    for (String res : request.getResources()) {
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) {
                            if (hasMicPermission()) {
                                request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                            } else {
                                pendingPermissionRequest = request;
                                requestPermissions(
                                        new String[]{Manifest.permission.RECORD_AUDIO},
                                        REQ_MICROPHONE
                                );
                            }
                            return;
                        }
                    }
                    request.deny();
                });
            }
        });

        webView.loadUrl(HOME_URL);
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
                    needsRequest = true;
                    break;
                }
            }
            if (needsRequest) requestPermissions(perms, REQ_STORAGE);
        } else {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_STORAGE
                );
            }
        }
    }

    private boolean hasMicPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_MICROPHONE) {
            boolean granted = grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (pendingPermissionRequest != null) {
                final PermissionRequest req = pendingPermissionRequest;
                pendingPermissionRequest = null;
                runOnUiThread(() -> {
                    if (granted) {
                        req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        req.deny();
                    }
                });
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            webView.setVisibility(View.VISIBLE);
            fullscreenContainer.removeView(customView);
            customView = null;
            if (customViewCallback != null) {
                customViewCallback.onCustomViewHidden();
                customViewCallback = null;
            }
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webView != null) webView.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearCache(false);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
