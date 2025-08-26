package chat.rox.android.sdk.impl;

import androidx.annotation.NonNull;

import chat.rox.android.sdk.Supplier;
import chat.rox.android.sdk.impl.backend.RoxClient;

import java.net.URLEncoder;
import java.nio.charset.Charset;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;

class FileUrlCreator {
    private final RoxClient client;
    private final String serverUrl;
    private Supplier<Boolean> safeUrlProvider;
    private static final Long ATTACHMENT_URL_EXPIRES_PERIOD = 5L * 60L; // 5 minutes

    public FileUrlCreator(RoxClient roxClient, String serverUrl) {
        this.client = roxClient;
        this.serverUrl = serverUrl;
    }

    public void setSafeUrlProvider(Supplier<Boolean> safeUrlProvider) {
        this.safeUrlProvider = safeUrlProvider;
    }

    public String createFileUrl(@NonNull String fileName, @NonNull String guid, boolean thumbnail) {
        try {
            String fileUrl = HttpUrl.parse(serverUrl).toString().replaceFirst("/*$", "/")
                + "l/v/m/download/"
                + guid + "/"
                + URLEncoder.encode(fileName, "utf-8");
            Boolean safeUrlEnabled = safeUrlProvider.get();
            if (safeUrlEnabled != null && safeUrlEnabled && client.getAuthData() != null) {
                String pageId = client.getAuthData().getPageId();
                long expires = currentTimeSeconds() + ATTACHMENT_URL_EXPIRES_PERIOD;
                String data = guid + expires;
                String key = client.getAuthData().getAuthToken();
                String hash = sha256(data, key);
                fileUrl += "?page-id=" + pageId + "&expires=" + expires + "&hash=" + hash;
                if (thumbnail) {
                    fileUrl += "&thumb=android";
                }
            }
            return fileUrl;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private String sha256(String data, String key) {
        StringBuilder hash = new StringBuilder();
        try {
            final Charset asciiCs = Charset.forName("US-ASCII");
            final Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            final SecretKeySpec secret_key
                = new javax.crypto.spec.SecretKeySpec(asciiCs.encode(key).array(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            final byte[] mac_data = sha256_HMAC.doFinal(asciiCs.encode(data).array());
            for (final byte element : mac_data) {
                hash.append(Integer.toString((element & 0xff) + 0x100, 16).substring(1));
            }
        } catch (Exception ignored) { }
        return hash.toString();
    }

    private Long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
