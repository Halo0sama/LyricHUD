package com.mc.lyrichud.api;

import com.google.gson.*;
import com.mc.lyrichud.LyricHUD;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AcrcloudClient {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static final int MAX_BYTES = 1000000;
    private final String host, accessKey, accessSecret;
    private final boolean enabled;

    public AcrcloudClient(String host, String accessKey, String accessSecret) {
        this.host = host; this.accessKey = accessKey; this.accessSecret = accessSecret;
        this.enabled = host != null && !host.isEmpty() && accessKey != null && !accessKey.isEmpty() && accessSecret != null && !accessSecret.isEmpty();
    }

    public boolean isEnabled() { return enabled; }

    public SongInfo identify(byte[] audioData) {
        if (!enabled || audioData == null || audioData.length == 0) return null;
        try {
            String ts = String.valueOf(System.currentTimeMillis() / 1000);
            int bytesToRead = Math.min(audioData.length, MAX_BYTES);
            byte[] sample = Arrays.copyOf(audioData, bytesToRead);
            String sampleB64 = Base64.getEncoder().encodeToString(sample);

            // Sign: POST\n/v1/identify\n{key}\n{data_type}\n{version}\n{timestamp}
            String toSign = "POST\n/v1/identify\n" + accessKey + "\naudio\n1\n" + ts;
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(accessSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String sig = Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));

            // Build form-urlencoded body (same as Python library)
            String body = "access_key=" + urlEncode(accessKey)
                    + "&sample_bytes=" + bytesToRead
                    + "&sample=" + urlEncode(sampleB64)
                    + "&timestamp=" + ts
                    + "&signature=" + urlEncode(sig)
                    + "&data_type=audio"
                    + "&signature_version=1";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + host + "/v1/identify"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(15)).build();

            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            String respBody = new String(resp.body(), StandardCharsets.UTF_8);
            JsonObject json = GSON.fromJson(respBody, JsonObject.class);
            if (json.get("status") == null || json.get("status").isJsonNull()) return null;
            int code = json.getAsJsonObject("status").get("code").getAsInt();
            if (code != 0) { LyricHUD.LOGGER.debug("ACRCloud code={}", code); return null; }

            JsonArray music = json.getAsJsonObject("metadata").getAsJsonArray("music");
            if (music == null || music.isEmpty()) return null;
            JsonObject f = music.get(0).getAsJsonObject();
            String title = f.has("title") ? f.get("title").getAsString() : null;
            String artist = ""; if (f.has("artists")) { JsonArray ar = f.getAsJsonArray("artists"); if (!ar.isEmpty()) artist = ar.get(0).getAsJsonObject().get("name").getAsString(); }
            int score = f.has("score") ? f.get("score").getAsInt() : 0;
            LyricHUD.LOGGER.info("ACRCloud: artist='{}' title='{}' score={}", artist, title, score);
            return new SongInfo("", title, artist);
        } catch (Exception e) { LyricHUD.LOGGER.warn("ACRCloud error: {}", e.getMessage()); return null; }
    }

    private String urlEncode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    public static class SongInfo { public final String songId, title, artist; public SongInfo(String i, String t, String a) { songId=i; title=t; artist=a; } }
}
