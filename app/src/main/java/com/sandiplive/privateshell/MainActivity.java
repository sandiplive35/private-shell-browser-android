package com.sandiplive.privateshell;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.ClientCertRequest;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.HttpAuthHandler;
import android.webkit.PermissionRequest;
import android.webkit.SafeBrowsingResponse;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewDatabase;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebStorageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.WebViewMediaIntegrityApiStatusConfig;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Privacy-first in-app browser shell.
 *
 * It intentionally requests INTERNET only and denies Android/WebView integrations that could
 * expose device data. It does not claim network anonymity: destination sites can still observe
 * the public IP address and browser/network fingerprinting signals.
 */
public final class MainActivity extends Activity {
    private static final String SEARCH_URL = "https://duckduckgo.com/?q=";
    private static final String PROFILE_PREFIX = "privateshell-";

    private static final String[] TRACKER_SUFFIXES = {
            "google-analytics.com",
            "googletagmanager.com",
            "doubleclick.net",
            "googlesyndication.com",
            "facebook.net",
            "hotjar.com",
            "segment.io",
            "segment.com",
            "mixpanel.com",
            "amplitude.com",
            "appsflyer.com",
            "clarity.ms",
            "bat.bing.com",
            "ads-twitter.com",
            "analytics.twitter.com",
            "scorecardresearch.com",
            "quantserve.com"
    };

    private WebView webView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private Button strictButton;
    private boolean strictMode = true;
    private Profile activeProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );
        WebView.setWebContentsDebuggingEnabled(false);

        buildUi();
        configureWebView();
        clearCurrentProfileData();
        showHome();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(6);
        toolbar.setPadding(pad, pad, pad, pad);

        Button back = makeButton("‹");
        Button forward = makeButton("›");
        Button reload = makeButton("↻");
        strictButton = makeButton("STRICT ON");
        Button wipe = makeButton("WIPE");

        addressBar = new EditText(this);
        addressBar.setSingleLine(true);
        addressBar.setHint("URL or search");
        addressBar.setTextSize(14f);
        addressBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressBar.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.setSelectAllOnFocus(true);

        toolbar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        toolbar.addView(forward, new LinearLayout.LayoutParams(dp(44), dp(44)));
        toolbar.addView(reload, new LinearLayout.LayoutParams(dp(44), dp(44)));
        toolbar.addView(addressBar, new LinearLayout.LayoutParams(0, dp(44), 1f));
        toolbar.addView(strictButton, new LinearLayout.LayoutParams(dp(94), dp(44)));
        toolbar.addView(wipe, new LinearLayout.LayoutParams(dp(64), dp(44)));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);

        webView = new WebView(this);
        attachEphemeralProfile();
        webView.setBackgroundColor(Color.WHITE);
        webView.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        webView.setLongClickable(false);
        webView.setOnLongClickListener(v -> true);

        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(2)
        ));
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        setContentView(root);

        back.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });
        forward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });
        reload.setOnClickListener(v -> webView.reload());
        wipe.setOnClickListener(v -> wipeSession());
        strictButton.setOnClickListener(v -> togglePrivacyMode());

        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                navigate(addressBar.getText().toString());
                return true;
            }
            return false;
        });
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11f);
        button.setAllCaps(false);
        return button;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setGeolocationEnabled(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setSaveFormData(false);
        settings.setSupportMultipleWindows(false);
        settings.setSupportZoom(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ATTRIBUTION_REGISTRATION_BEHAVIOR)) {
            WebSettingsCompat.setAttributionRegistrationBehavior(
                    settings,
                    WebSettingsCompat.ATTRIBUTION_BEHAVIOR_DISABLED
            );
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(
                    settings,
                    WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_NONE
            );
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
            WebSettingsCompat.setBackForwardCacheEnabled(settings, false);
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEBVIEW_MEDIA_INTEGRITY_API_STATUS)) {
            WebViewMediaIntegrityApiStatusConfig config =
                    new WebViewMediaIntegrityApiStatusConfig.Builder(
                            WebViewMediaIntegrityApiStatusConfig.WEBVIEW_MEDIA_INTEGRITY_API_DISABLED
                    ).build();
            WebSettingsCompat.setWebViewMediaIntegrityApiStatus(settings, config);
        }

        applyPrivacyMode();

        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                Toast.makeText(this, "Downloads are disabled", Toast.LENGTH_SHORT).show()
        );

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!request.isForMainFrame()) {
                    return !isAllowedWebScheme(uri);
                }
                return handleTopLevelNavigation(view, uri);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleTopLevelNavigation(view, Uri.parse(url));
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                String host = lower(request.getUrl().getHost());
                if (isTrackerHost(host)) return emptyResponse();
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                if (url != null && !url.startsWith("data:")) {
                    addressBar.setText("about:blank".equals(url) ? "" : url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                Toast.makeText(
                        MainActivity.this,
                        "Blocked invalid TLS certificate",
                        Toast.LENGTH_LONG
                ).show();
            }

            @Override
            public void onReceivedHttpAuthRequest(
                    WebView view,
                    HttpAuthHandler handler,
                    String host,
                    String realm
            ) {
                handler.cancel();
            }

            @Override
            public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
                request.cancel();
            }

            @Override
            public void onSafeBrowsingHit(
                    WebView view,
                    WebResourceRequest request,
                    int threatType,
                    SafeBrowsingResponse callback
            ) {
                callback.backToSafety(false);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback
            ) {
                callback.invoke(origin, false, false);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                filePathCallback.onReceiveValue(null);
                Toast.makeText(
                        MainActivity.this,
                        "File access is disabled",
                        Toast.LENGTH_SHORT
                ).show();
                return true;
            }
        });
    }

    private void togglePrivacyMode() {
        strictMode = !strictMode;
        applyPrivacyMode();
        strictButton.setText(strictMode ? "STRICT ON" : "COMPAT");
        Toast.makeText(
                this,
                strictMode
                        ? "Strict: JavaScript, cookies and DOM storage disabled"
                        : "Compat: JavaScript + first-party cookies enabled",
                Toast.LENGTH_LONG
        ).show();
        webView.reload();
    }

    private void applyPrivacyMode() {
        if (webView == null) return;

        WebSettings settings = webView.getSettings();
        CookieManager cookies = cookieManager();
        cookies.setAcceptThirdPartyCookies(webView, false);

        if (strictMode) {
            settings.setJavaScriptEnabled(false);
            settings.setDomStorageEnabled(false);
            settings.setDatabaseEnabled(false);
            cookies.setAcceptCookie(false);
            cookies.removeAllCookies(null);
            webStorage().deleteAllData();
        } else {
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(false);
            cookies.setAcceptCookie(true);
        }
    }

    private boolean handleTopLevelNavigation(WebView view, Uri uri) {
        String scheme = lower(uri.getScheme());

        if ("https".equals(scheme)) return false;

        if ("http".equals(scheme)) {
            Uri httpsUri = uri.buildUpon().scheme("https").build();
            view.loadUrl(httpsUri.toString());
            return true;
        }

        if ("about".equals(scheme) && "about:blank".equals(uri.toString())) {
            return false;
        }

        Toast.makeText(
                this,
                "Blocked external link: " + (scheme.isEmpty() ? "unknown" : scheme),
                Toast.LENGTH_SHORT
        ).show();
        return true;
    }

    private boolean isAllowedWebScheme(Uri uri) {
        String scheme = lower(uri.getScheme());
        return "https".equals(scheme) || "http".equals(scheme) || "about".equals(scheme);
    }

    private void navigate(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            showHome();
            return;
        }

        String lower = input.toLowerCase(Locale.ROOT);
        String url;
        if (lower.startsWith("https://")) {
            url = input;
        } else if (lower.startsWith("http://")) {
            url = "https://" + input.substring("http://".length());
        } else if (!input.contains(" ") && input.contains(".")) {
            url = "https://" + input;
        } else {
            url = SEARCH_URL + Uri.encode(input);
        }
        webView.loadUrl(url);
    }

    private void showHome() {
        addressBar.setText("");
        String html = "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;padding:32px;max-width:720px;margin:auto;color:#111}"
                + "h1{font-size:32px;margin-bottom:8px}.pill{display:inline-block;border:1px solid #111;"
                + "border-radius:999px;padding:6px 10px;font-size:12px}li{margin:10px 0;line-height:1.45}"
                + "</style></head><body><span class='pill'>EPHEMERAL / STRICT</span>"
                + "<h1>PrivateShell</h1><p>Enter a URL or search above.</p><ul>"
                + "<li>No device permissions beyond internet access.</li>"
                + "<li>External app links, file access, downloads and permission prompts are blocked.</li>"
                + "<li>Strict mode disables JavaScript, cookies and DOM storage.</li>"
                + "<li>WIPE deletes browser state controlled by this app.</li>"
                + "</ul><p><b>Network anonymity is not guaranteed:</b> websites can still observe "
                + "your public IP and browser/network signals.</p></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void wipeSession() {
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearCache(true);
        webView.clearHistory();
        webView.clearFormData();
        webView.clearSslPreferences();
        WebView.clearClientCertPreferences(null);
        clearCurrentProfileData();
        showHome();
        Toast.makeText(this, "Session wiped", Toast.LENGTH_SHORT).show();
    }

    private void clearCurrentProfileData() {
        CookieManager cookies = cookieManager();
        cookies.removeAllCookies(null);
        cookies.removeSessionCookies(null);

        WebStorage storage = webStorage();
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)) {
            WebStorageCompat.deleteBrowsingData(storage, () -> { });
        } else {
            storage.deleteAllData();
        }

        WebViewDatabase database = WebViewDatabase.getInstance(getApplicationContext());
        database.clearFormData();
        database.clearHttpAuthUsernamePassword();
    }

    private void attachEphemeralProfile() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) return;

        ProfileStore store = ProfileStore.getInstance();
        for (String name : store.getAllProfileNames()) {
            if (!name.startsWith(PROFILE_PREFIX)) continue;
            try {
                store.deleteProfile(name);
            } catch (IllegalStateException ignored) {
                // A prior Activity instance can still own this profile during configuration change.
                // It will be removable on a later cold start.
            }
        }

        String profileName = PROFILE_PREFIX + UUID.randomUUID();
        WebViewCompat.setProfile(webView, profileName);
        activeProfile = WebViewCompat.getProfile(webView);
    }

    private CookieManager cookieManager() {
        return activeProfile != null
                ? activeProfile.getCookieManager()
                : CookieManager.getInstance();
    }

    private WebStorage webStorage() {
        return activeProfile != null
                ? activeProfile.getWebStorage()
                : WebStorage.getInstance();
    }

    private boolean isTrackerHost(String host) {
        if (host.isEmpty()) return false;
        for (String suffix : TRACKER_SUFFIXES) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        }
        return false;
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                new ByteArrayInputStream(new byte[0])
        );
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            clearCurrentProfileData();
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }

        // Do not delete the active profile here: ProfileStore forbids deleting a profile that
        // has been loaded into memory. Stale PrivateShell profiles are deleted on the next launch.
        activeProfile = null;
        super.onDestroy();
    }
}
