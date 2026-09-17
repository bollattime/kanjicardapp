package com.ugur.kanjikart;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kanji Kartları
 *
 * - Uygulama arayüzü assets/index.html içinde (tools/build.py üretir).
 * - Japonca sesli okuma için cihazın TextToSpeech motoru kullanılır (JS: window.AndroidTTS).
 * - Veri güncellemeleri assets/config.json içindeki remoteBaseUrl adresinden
 *   (GitHub Pages) indirilir ve cihazda saklanır (JS: window.AndroidData).
 */
public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final String DATA_FILE = "remote-data.json";
    private static final int MAX_DOWNLOAD_BYTES = 30 * 1024 * 1024;

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile String noVoiceMessage = "Japonca ses bulunamadı.";
    private String remoteBaseUrl = "";
    private boolean pageLoaded = false;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnBackInvokedCallback backCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        remoteBaseUrl = readRemoteBaseUrl();

        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int bg = Color.parseColor(night ? "#0E0F26" : "#ECEEFB");

        web = new WebView(this);
        web.setBackgroundColor(bg);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Kaynaklar ekranındaki bağlantıları tarayıcıda aç
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageLoaded = true;
            }
        });

        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");
        web.addJavascriptInterface(new DataBridge(), "AndroidData");

        setContentView(web);
        setupSystemBars(night, bg);
        setupBackHandling();

        tts = new TextToSpeech(this, this);
        web.loadUrl("file:///android_asset/index.html");
    }

    // ---------------------------------------------------------------- Sistem çubukları

    private void setupSystemBars(boolean night, int bg) {
        if (Build.VERSION.SDK_INT >= 35) {
            // Android 15+ uygulamayı kenardan kenara çizer; içeriği sistem çubuklarının dışına it.
            web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @Override
                public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                    Insets bars = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return WindowInsets.CONSUMED;
                }
            });
        } else {
            getWindow().setStatusBarColor(bg);
            getWindow().setNavigationBarColor(bg);
        }

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(night ? 0 : light, light);
            }
        } else if (!night) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            if (Build.VERSION.SDK_INT < 26) getWindow().setNavigationBarColor(Color.BLACK);
        }
    }

    // ---------------------------------------------------------------- Geri tuşu

    private void setupBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = new OnBackInvokedCallback() {
                @Override
                public void onBackInvoked() {
                    handleBack();
                }
            };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
    }

    /** Android 12 ve altı için. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        handleBack();
    }

    /** Önce uygulama içinde geri git (menü, alt sayfa); gidilecek yer yoksa kapat. */
    private void handleBack() {
        if (!pageLoaded) {
            finish();
            return;
        }
        web.evaluateJavascript("window.appBack ? window.appBack() : false", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (!"true".equals(value)) finish();
            }
        });
    }

    // ---------------------------------------------------------------- Sesli okuma

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS && tts != null) {
            int r = tts.setLanguage(Locale.JAPANESE);
            ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
            tts.setSpeechRate(0.85f);
        }
    }

    private class TtsBridge {
        @JavascriptInterface
        public void setMessage(String msg) {
            if (msg != null) noVoiceMessage = msg;
        }

        @JavascriptInterface
        public void speak(final String text) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (!ttsReady || tts == null) {
                        Toast.makeText(MainActivity.this, noVoiceMessage, Toast.LENGTH_LONG).show();
                        return;
                    }
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kanji");
                }
            });
        }
    }

    // ---------------------------------------------------------------- Veri güncelleme

    private class DataBridge {
        /** Cihazda saklanan son indirilen veri ("" = yok). */
        @JavascriptInterface
        public String getData() {
            File f = new File(getFilesDir(), DATA_FILE);
            if (!f.exists()) return "";
            try {
                return new String(readAll(new FileInputStream(f), MAX_DOWNLOAD_BYTES), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        }

        @JavascriptInterface
        public int appVersion() {
            return BuildConfig.VERSION_CODE;
        }

        @JavascriptInterface
        public boolean canUpdate() {
            return isRemoteConfigured();
        }

        /** Arka planda denetler; sonucu window.onDataStatus(durum, sürüm) ile bildirir. */
        @JavascriptInterface
        public void checkForUpdate(final int currentVersion) {
            io.execute(new Runnable() {
                @Override
                public void run() {
                    runUpdate(currentVersion);
                }
            });
        }

        /** İndirilen veriyi siler, uygulama içindeki veriye döner. */
        @JavascriptInterface
        public void clearData() {
            //noinspection ResultOfMethodCallIgnored
            new File(getFilesDir(), DATA_FILE).delete();
        }
    }

    private boolean isRemoteConfigured() {
        return remoteBaseUrl.startsWith("https://") && !remoteBaseUrl.contains("KULLANICI");
    }

    private void runUpdate(int currentVersion) {
        if (!isRemoteConfigured()) {
            notifyJs("disabled", currentVersion);
            return;
        }
        try {
            String verText = new String(
                    httpGet(remoteBaseUrl + "version.json?t=" + System.currentTimeMillis()),
                    StandardCharsets.UTF_8);
            JSONObject ver = new JSONObject(verText);
            int remoteVersion = ver.getInt("dataVersion");
            int minApp = ver.optInt("minAppVersion", 1);
            String expectedSha = ver.getString("sha256");
            String file = ver.optString("file", "data.json");

            if (minApp > BuildConfig.VERSION_CODE) {
                notifyJs("appTooOld", remoteVersion);
                return;
            }
            if (remoteVersion <= currentVersion) {
                notifyJs("uptodate", currentVersion);
                return;
            }

            byte[] body = httpGet(remoteBaseUrl + file + "?v=" + remoteVersion);
            if (!sha256(body).equalsIgnoreCase(expectedSha)) throw new IOException("sha mismatch");

            JSONObject data = new JSONObject(new String(body, StandardCharsets.UTF_8));
            if (data.getJSONObject("meta").getInt("version") != remoteVersion
                    || data.getJSONArray("kanji").length() < 100) {
                throw new IOException("invalid data");
            }

            File tmp = new File(getFilesDir(), DATA_FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(body);
            }
            File target = new File(getFilesDir(), DATA_FILE);
            if (!tmp.renameTo(target)) throw new IOException("rename failed");

            notifyJs("updated", remoteVersion);
        } catch (Exception e) {
            notifyJs("failed", currentVersion);
        }
    }

    private void notifyJs(final String status, final int version) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (web == null) return;
                web.evaluateJavascript(
                        "window.onDataStatus && window.onDataStatus('" + status + "'," + version + ")", null);
            }
        });
    }

    private byte[] httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            return readAll(c.getInputStream(), MAX_DOWNLOAD_BYTES);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int limit) throws IOException {
        try (InputStream is = in; ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            byte[] b = new byte[16384];
            int n;
            int total = 0;
            while ((n = is.read(b)) != -1) {
                total += n;
                if (total > limit) throw new IOException("too large");
                buf.write(b, 0, n);
            }
            return buf.toByteArray();
        }
    }

    private static String sha256(byte[] data) throws Exception {
        byte[] h = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte x : h) sb.append(String.format(Locale.ROOT, "%02x", x));
        return sb.toString();
    }

    private String readRemoteBaseUrl() {
        try {
            byte[] b = readAll(getAssets().open("config.json"), 64 * 1024);
            String url = new JSONObject(new String(b, StandardCharsets.UTF_8)).optString("remoteBaseUrl", "");
            if (!url.isEmpty() && !url.endsWith("/")) url += "/";
            return url;
        } catch (Exception e) {
            return "";
        }
    }

    // ---------------------------------------------------------------- Yaşam döngüsü

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        io.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
