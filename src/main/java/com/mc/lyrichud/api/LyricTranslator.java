package com.mc.lyrichud.api;

import com.google.gson.*;
import com.mc.lyrichud.LyricHUD;
import com.mc.lyrichud.api.LyricFetcher.LyricLine;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Lyrics translation using Google Translate (free web API).
 */
public class LyricTranslator {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static CompletableFuture<Map<Integer, String>> translate(List<LyricLine> lines) {
        return CompletableFuture.supplyAsync(() -> {
            LyricHUD.LOGGER.info("Translator started, {} lines", lines.size());
            try {
                Map<Integer, String> result = new HashMap<>();
                for (int i = 0; i < lines.size(); i++) {
                    String text = lines.get(i).text.trim();
                    if (text.isEmpty()) continue;
                    try {
                        String translated = translateOne(text);
                        if (translated != null && !translated.isEmpty() && !translated.equals(text)) {
                            result.put(i, translated);
                        }
                    } catch (Exception ignored) {}
                }
                LyricHUD.LOGGER.info("Translated {}/{} lines", result.size(), lines.size());
                return result;
            } catch (Exception e) {
                return new HashMap<>();
            }
        });
    }

    private static String translateOne(String text) throws Exception {
        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
        String url = "https://translate.googleapis.com/translate_a/single"
                + "?client=gtx&sl=auto&tl=zh&dt=t&q=" + encoded;
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            LyricHUD.LOGGER.warn("Google translate HTTP {}: {}", resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
            return null;
        }

        String body = resp.body();
        if (body == null || body.isEmpty() || !body.startsWith("[")) {
            LyricHUD.LOGGER.warn("Google translate bad body: {}", body.substring(0, Math.min(100, body.length())));
            return null;
        }

        // Response: [[["translated text","original",...]],...]
        JsonArray arr = GSON.fromJson(resp.body(), JsonArray.class);
        if (arr == null || arr.isEmpty()) return null;
        JsonArray inner = arr.get(0).getAsJsonArray();
        if (inner == null || inner.isEmpty()) return null;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inner.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(inner.get(i).getAsJsonArray().get(0).getAsString());
        }
        return sb.toString();
    }
}
