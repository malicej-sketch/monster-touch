package com.example.touchmapper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 홈 화면 배너를 받아온다.
 *
 * 이 앱을 쓰는 사람은 주행 중인 배달 기사다. 배너 하나 때문에 화면이 늦게 뜨거나
 * 데이터가 새면 안 된다. 그래서 규칙이 셋이다.
 *
 *   - 화면은 배너를 기다리지 않는다. 받아오면 그때 끼워 넣는다
 *   - 이미지는 한 번 받으면 파일로 두고 다시 받지 않는다. 주소가 바뀔 때만 새로 받는다
 *   - 실패하면 조용히 없던 일로 한다. 배너가 없다고 앱이 못 쓰게 되지 않는다
 */
final class BannerLoader {

    private static final String ENDPOINT = "https://mon-banner.malicej.workers.dev/banner.json";
    private static final String CACHE_FILE = "banner_image";
    private static final String PREF_IMAGE_URL = "banner_image_url";
    private static final String PREF_TARGET_URL = "banner_target_url";
    private static final String PREF_BUTTON_TEXT = "banner_button_text";
    private static final String PREF_ENABLED = "banner_enabled";

    /** 주행 중 데이터망이 느릴 수 있다. 오래 붙잡지 않는다. */
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 6000;
    /** 배너 하나가 이보다 클 이유가 없다. 잘못된 응답으로 메모리를 쓰지 않는다. */
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    interface Callback {
        void onBanner(Bitmap image, String targetUrl, String buttonText);
    }

    private BannerLoader() {
    }

    /**
     * 저장해 둔 배너가 있으면 즉시 돌려주고, 뒤에서 새 내용을 확인한다.
     *
     * 껐다 켤 때마다 빈 자리가 보였다가 채워지면 화면이 흔들린다. 지난번 것을 먼저
     * 보여주는 편이 낫다.
     */
    static void load(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());

        deliverCached(app, callback);

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(readText(ENDPOINT));
                boolean enabled = json.optBoolean("enabled", false);
                String imageUrl = json.optString("imageUrl", "");
                String targetUrl = json.optString("targetUrl", "");
                String buttonText = json.optString("buttonText", "자세히 보기");

                SharedPreferences prefs = prefs(app);
                prefs.edit()
                        .putBoolean(PREF_ENABLED, enabled)
                        .putString(PREF_TARGET_URL, targetUrl)
                        .putString(PREF_BUTTON_TEXT, buttonText)
                        .apply();

                if (!enabled || imageUrl.isEmpty()) {
                    return;
                }

                File cache = new File(app.getFilesDir(), CACHE_FILE);
                boolean sameImage = imageUrl.equals(prefs.getString(PREF_IMAGE_URL, ""));
                if (sameImage && cache.exists()) {
                    return;  // 이미 가진 그림이다
                }

                byte[] bytes = readBytes(imageUrl);
                if (bytes == null) {
                    return;
                }
                try (FileOutputStream out = new FileOutputStream(cache)) {
                    out.write(bytes);
                }
                prefs.edit().putString(PREF_IMAGE_URL, imageUrl).apply();

                Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (image != null) {
                    main.post(() -> callback.onBanner(image, targetUrl, buttonText));
                }
            } catch (Exception ignored) {
                // 망이 없거나 응답이 이상하다. 배너 없이 그대로 쓴다.
            }
        }, "banner-loader").start();
    }

    private static void deliverCached(Context app, Callback callback) {
        SharedPreferences prefs = prefs(app);
        if (!prefs.getBoolean(PREF_ENABLED, false)) {
            return;
        }
        File cache = new File(app.getFilesDir(), CACHE_FILE);
        if (!cache.exists()) {
            return;
        }
        Bitmap image = BitmapFactory.decodeFile(cache.getAbsolutePath());
        if (image != null) {
            callback.onBanner(image,
                    prefs.getString(PREF_TARGET_URL, ""),
                    prefs.getString(PREF_BUTTON_TEXT, "자세히 보기"));
        }
    }

    private static SharedPreferences prefs(Context app) {
        return app.getSharedPreferences("banner", Context.MODE_PRIVATE);
    }

    private static String readText(String url) throws Exception {
        byte[] bytes = readBytes(url);
        return bytes == null ? "" : new String(bytes, "UTF-8");
    }

    private static byte[] readBytes(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    if (out.size() > MAX_IMAGE_BYTES) {
                        return null;
                    }
                }
                return out.toByteArray();
            }
        } finally {
            connection.disconnect();
        }
    }
}
