package com.sxsbsxs.dog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * In-app website login. The WebView profile is isolated to Dog, and its cookies
 * are exported locally into an encrypted yt-dlp cookie jar after user approval.
 */
public class LoginActivity extends Activity {
    public static final String EXTRA_TARGET_URL = "target_url";

    private final ExecutorService exporter = Executors.newSingleThreadExecutor();
    private final Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
    private WebView webView;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button finishButton;
    private String loginUrl;
    private boolean saving;
    private boolean pageError;
    private Dialog popupDialog;
    private WebView popupWebView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String target = getIntent().getStringExtra(EXTRA_TARGET_URL);
        loginUrl = resolveLoginUrl(target);
        if (loginUrl == null) {
            Toast.makeText(this, "无法识别网站地址，请先填写有效的视频链接", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        visitedUrls.add(loginUrl);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(6), dp(12), dp(6));
        toolbar.setBackgroundColor(Color.WHITE);

        Button backButton = new Button(this);
        backButton.setText("返回");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> finish());
        toolbar.addView(backButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        TextView title = new TextView(this);
        title.setText("网站账号登录");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1f));

        finishButton = new Button(this);
        finishButton.setText("完成授权");
        finishButton.setAllCaps(false);
        finishButton.setOnClickListener(v -> saveLoginCookies());
        toolbar.addView(finishButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout webContainer = new FrameLayout(this);
        webView = createWebView(false);
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        statusText = new TextView(this);
        statusText.setText("请在网页中登录账号，确认页面显示已登录后，点击右上角“完成授权”。");
        statusText.setTextColor(Color.DKGRAY);
        statusText.setTextSize(12);
        statusText.setPadding(dp(16), dp(10), dp(16), dp(10));
        statusText.setBackgroundColor(0xFFF5F5F5);
        root.addView(statusText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        webView.loadUrl(loginUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView(boolean popup) {
        WebView view = new WebView(this);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setDatabaseEnabled(true);
        view.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        view.getSettings().setSupportMultipleWindows(true);
        view.getSettings().setLoadsImagesAutomatically(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);

        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                return scheme != null &&
                        !scheme.equalsIgnoreCase("http") &&
                        !scheme.equalsIgnoreCase("https");
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                super.onReceivedError(view, request, error);
                if (!popup && request.isForMainFrame() && statusText != null) {
                    pageError = true;
                    statusText.setText("网页加载失败：" + error.getDescription() +
                            "。请检查网络，或该网站是否限制应用内登录。");
                }
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceResponse response
            ) {
                super.onReceivedHttpError(view, request, response);
                if (!popup && request.isForMainFrame() && response.getStatusCode() >= 400 &&
                        statusText != null) {
                    pageError = true;
                    statusText.setText("网站返回 HTTP " + response.getStatusCode() +
                            "。可能需要更新网页、重新登录，或该站点不允许内嵌登录。");
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (requestIsMainFrame(view)) pageError = false;
                rememberVisitedUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                rememberVisitedUrl(url);
                CookieManager.getInstance().flush();
                if (!popup && statusText != null && !saving && !pageError) {
                    statusText.setText("网页已加载。登录成功后点击“完成授权”；不支持内嵌登录的网站可能会提示使用其他浏览器。");
                }
            }
        });

        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (!popup && progressBar != null) {
                    progressBar.setProgress(newProgress);
                    progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public boolean onCreateWindow(
                    WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                if (popup || popupWebView != null) return false;

                popupWebView = createWebView(true);
                popupDialog = new Dialog(LoginActivity.this);
                popupDialog.setContentView(popupWebView);
                Window window = popupDialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
                    window.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                    );
                }
                popupDialog.setOnDismissListener(dialog -> {
                    if (popupWebView != null) {
                        popupWebView.stopLoading();
                        popupWebView.destroy();
                        popupWebView = null;
                    }
                    popupDialog = null;
                });

                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popupWebView);
                resultMsg.sendToTarget();
                popupDialog.show();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                if (popupWebView == window && popupDialog != null) {
                    popupDialog.dismiss();
                }
            }
        });
        return view;
    }

    private void saveLoginCookies() {
        if (saving) return;
        saving = true;
        finishButton.setEnabled(false);
        finishButton.setText("正在保存…");
        statusText.setText("正在读取本机网页登录状态并加密保存，请稍候…");

        CookieManager.getInstance().flush();
        exporter.execute(() -> {
            try {
                String cookieFile = CookieJarExporter.export(
                        getApplicationContext(), loginUrl, visitedUrls);
                SecureCookieStore.saveCookieJar(getApplicationContext(), cookieFile);
                runOnUiThread(() -> {
                    setResult(Activity.RESULT_OK);
                    Toast.makeText(
                            this,
                            "网页登录授权已保存，下载时会自动使用",
                            Toast.LENGTH_LONG
                    ).show();
                    finish();
                });
            } catch (IOException failure) {
                showSaveError(failure.getMessage());
            } catch (Exception failure) {
                showSaveError("授权信息保存失败：" + failure.getMessage());
            }
        });
    }

    private boolean requestIsMainFrame(WebView view) {
        return view == webView;
    }

    private void rememberVisitedUrl(String value) {
        if (value == null || value.isEmpty()) return;
        try {
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isEmpty() ||
                    (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                return;
            }
            String path = uri.getPath();
            visitedUrls.add(scheme + "://" + host + (path == null || path.isEmpty() ? "/" : path));
        } catch (Exception ignored) {
            // Ignore non-web navigation and malformed redirect URLs.
        }
    }

    private void showSaveError(String message) {
        runOnUiThread(() -> {
            saving = false;
            finishButton.setEnabled(true);
            finishButton.setText("完成授权");
            statusText.setText(message == null ? "没有找到有效的登录信息，请先登录网站。" : message);
            Toast.makeText(this, statusText.getText(), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackPressed() {
        if (popupDialog != null && popupDialog.isShowing()) {
            popupDialog.dismiss();
            return;
        }
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
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
            webView = null;
        }
        if (popupDialog != null) popupDialog.dismiss();
        exporter.shutdownNow();
        super.onDestroy();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String resolveLoginUrl(String target) {
        if (target == null || target.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(target.trim());
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return null;
            host = host.toLowerCase(Locale.ROOT);
            if (!"http".equalsIgnoreCase(uri.getScheme()) &&
                    !"https".equalsIgnoreCase(uri.getScheme())) return null;

            if (host.equals("youtu.be") || host.endsWith(".youtube.com") || host.equals("youtube.com")) {
                return "https://www.youtube.com/signin";
            }
            if (host.equals("b23.tv") || host.endsWith(".bilibili.com") || host.equals("bilibili.com")) {
                return "https://passport.bilibili.com/login";
            }
            if (host.endsWith(".douyin.com") || host.equals("douyin.com") ||
                    host.endsWith(".iesdouyin.com") || host.equals("iesdouyin.com")) {
                return "https://www.douyin.com/passport/web/login/";
            }
            if (host.endsWith(".tiktok.com") || host.equals("tiktok.com")) {
                return "https://www.tiktok.com/login";
            }
            if (host.endsWith(".kuaishou.com") || host.equals("kuaishou.com")) {
                return "https://www.kuaishou.com/";
            }
            if (host.endsWith(".instagram.com") || host.equals("instagram.com")) {
                return "https://www.instagram.com/accounts/login/";
            }
            if (host.equals("twitter.com") || host.endsWith(".twitter.com") ||
                    host.equals("x.com") || host.endsWith(".x.com")) {
                return "https://x.com/i/flow/login";
            }
            if (host.endsWith(".facebook.com") || host.equals("facebook.com") ||
                    host.equals("fb.watch")) {
                return "https://www.facebook.com/login/";
            }
            if (host.endsWith(".vimeo.com") || host.equals("vimeo.com")) {
                return "https://vimeo.com/";
            }
            if (host.endsWith(".nicovideo.jp") || host.equals("nicovideo.jp")) {
                return "https://www.nicovideo.jp/";
            }
            if (host.endsWith(".weibo.com") || host.equals("weibo.com") ||
                    host.endsWith(".weibo.cn") || host.equals("weibo.cn")) {
                return "https://passport.weibo.com/sso/signin";
            }
            if (host.endsWith(".ixigua.com") || host.equals("ixigua.com") ||
                    host.endsWith(".xigua.com") || host.equals("xigua.com")) {
                return "https://www.ixigua.com/";
            }
            if (host.endsWith(".qq.com") || host.equals("qq.com")) {
                return "https://v.qq.com/";
            }
            if (host.endsWith(".mgtv.com") || host.equals("mgtv.com")) {
                return "https://www.mgtv.com/";
            }
            if (host.endsWith(".dailymotion.com") || host.equals("dailymotion.com")) {
                return "https://www.dailymotion.com/";
            }
            if (host.endsWith(".youku.com") || host.equals("youku.com")) {
                return "https://www.youku.com/";
            }
            if (host.endsWith(".iqiyi.com") || host.equals("iqiyi.com")) {
                return "https://www.iqiyi.com/";
            }

            // Never downgrade a login page to cleartext HTTP.
            return "https://" + host + "/";
        } catch (Exception ignored) {
            return null;
        }
    }
}
