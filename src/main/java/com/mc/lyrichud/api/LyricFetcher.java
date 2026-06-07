package com.mc.lyrichud.api;

import com.google.gson.*;
import com.mc.lyrichud.LyricHUD;
import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.*;

public class LyricFetcher {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();
    private static final String ACOUSTID_KEY = "cSpUJKpD";
    private final AcrcloudClient acrcloud;

    private List<LyricLine> currentLyrics = new ArrayList<>();
    private Map<Integer, String> translations = new HashMap<>();  // line index -> translated text
    private String currentSongTitle = "", currentSongArtist = "", composer = "";
    private boolean isInstrumental = false;

    public LyricFetcher(AcrcloudClient acrcloud) { this.acrcloud = acrcloud; }
    public boolean isInstrumental() { return isInstrumental; }
    public String getComposer() { return composer; }
    public List<LyricLine> getCurrentLyrics() { return currentLyrics; }
    public String getCurrentSongTitle() { return currentSongTitle; }
    public String getCurrentSongArtist() { return currentSongArtist; }
    public Map<Integer, String> getTranslations() { return translations; }

    /** Full pipeline */
    public CompletableFuture<List<LyricLine>> fetchLyrics(
            String title, String artist, byte[] audioData) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                SearchResult sr = searchAndFetch(title, artist);
                if (sr != null) { apply(sr); return sr.lines; }

                // Step 2: AcoustID (free, fast, no rate limit)
                if (audioData != null && audioData.length > 0) {
                    LyricHUD.LOGGER.info("Trying AcoustID...");
                    var acoust = identifyByFingerprint(audioData);
                    if (acoust != null && acoust.title != null) {
                        // If AcoustID has no artist, try ACRCloud for artist first
                        String art = acoust.artist;
                        LyricHUD.LOGGER.info("AcoustID artist: '{}' empty={}", art, art.isEmpty());
                        if ((art == null || art.isEmpty()) && acrcloud.isEnabled()) {
                            LyricHUD.LOGGER.info("AcoustID no artist, trying ACRCloud for artist...");
                            var ar2 = acrcloud.identify(audioData);
                            if (ar2 != null && ar2.artist != null && !ar2.artist.isEmpty()) {
                                art = ar2.artist;
                                LyricHUD.LOGGER.info("ACRCloud artist fallback: '{}'", art);
                            }
                        }
                        sr = searchAndFetch(acoust.title, art);
                        if (sr != null) { apply(sr, acoust.title, art); return sr.lines; }
                        return showInstrumental(acoust.title, art);
                    }
                }

                // Step 3: ACRCloud fallback
                if (audioData != null && audioData.length > 0 && acrcloud.isEnabled()) {
                    LyricHUD.LOGGER.info("Trying ACRCloud...");
                    var ar = acrcloud.identify(audioData);
                    if (ar != null && ar.title != null) {
                        sr = searchAndFetch(ar.title, ar.artist);
                        if (sr != null) { apply(sr, ar.title, ar.artist); return sr.lines; }
                        return showInstrumental(ar.title, ar.artist);
                    }
                }

                // Step 4: Nothing worked — don't show wrong info
                return List.of(new LyricLine(0, ""));
            } catch (Exception e) {
                LyricHUD.LOGGER.error("Fetch error: {}", e.getMessage());
                return List.of(new LyricLine(0, "Error: " + e.getMessage()));
            }
        });
    }

    public CompletableFuture<List<LyricLine>> fetchLyrics(String title, String artist) {
        return fetchLyrics(title, artist, null);
    }

    private void apply(SearchResult sr) { apply(sr, sr.matchedTitle, sr.matchedArtist); }
    private void apply(SearchResult sr, String t, String a) {
        currentLyrics = sr.lines; currentSongTitle = t; currentSongArtist = a;
        isInstrumental = sr.instrumental;
        composer = a.isEmpty() ? sr.composer : a;
        // NetEase API provides tlyric (translated lyrics) natively
        if (!isInstrumental && sr.lines.size() > 0) {
            LyricHUD.LOGGER.info("Lyrics: {} lines, {} translated", sr.lines.size(), translations.size());
        } else {
            translations = new HashMap<>();
        }
    }

    private List<LyricLine> showInstrumental(String title, String artist) {
        currentLyrics = List.of(new LyricLine(0, ""));
        currentSongTitle = title;
        currentSongArtist = artist;
        isInstrumental = true;
        composer = artist;
        translations = new HashMap<>();
        return currentLyrics;
    }

    // --- Search & Fetch ---
    private SearchResult searchAndFetch(String title, String artist) {
        try {
        // Try NetEase first (more stable API)
        SongInfo info = searchNetEase(title, artist);
        if (info == null) info = searchKuwo(title, artist);
        if (info == null) return null;
        // Always use NetEase for lyrics (Kuwo's lyrics API requires auth tokens)
        String rawLyric = fetchNetEaseLyric(info.songId);
        if (rawLyric == null || rawLyric.isEmpty())
            rawLyric = fetchKuwoLyric(info.songId);
        if (rawLyric == null || rawLyric.isEmpty())
            return new SearchResult(List.of(new LyricLine(0, "")), info.title, info.artist, true, info.artist);
        List<LyricLine> lines = parseLRC(rawLyric);
        boolean instr = rawLyric.contains("纯音乐") || rawLyric.contains("instrumental") || lines.isEmpty();
        if (instr && lines.isEmpty()) lines = List.of(new LyricLine(0, ""));
        return new SearchResult(lines, info.title, info.artist, instr, info.artist);
        } catch (Exception e) { LyricHUD.LOGGER.warn("SearchAndFetch error: {}", e.getMessage()); return null; }
    }

    // --- AcoustID ---
    private AcoustIDResult identifyByFingerprint(byte[] audioData) {
        LyricHUD.LOGGER.info("AcoustID: starting...");
        try {
            Path tmp = Files.createTempFile("lyrichud_", ".ogg");
            Files.write(tmp, audioData);
            String fpcalcPath = extractFpcalc();
            if (fpcalcPath == null) return null;
            ProcessBuilder pb = new ProcessBuilder(fpcalcPath, tmp.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(); Files.deleteIfExists(tmp);
            int dur = 0; String fp = null;
            for (String l : out.split("\n")) {
                if (l.startsWith("DURATION=")) dur = (int)Double.parseDouble(l.substring(9));
                if (l.startsWith("FINGERPRINT=")) fp = l.substring(12);
            }
            if (fp == null || dur == 0) return null;
            String apiUrl = String.format("https://api.acoustid.org/v2/lookup?client=%s&duration=%d&fingerprint=%s&meta=recordings",
                    ACOUSTID_KEY, dur, URLEncoder.encode(fp, StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(apiUrl)).header("User-Agent","LyricHUD/1.1").GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
            if (json.get("status") == null || json.get("status").isJsonNull()) return null;
            if (!"ok".equals(json.get("status").getAsString())) return null;
            
            JsonElement resultsEl = json.get("results");
            if (resultsEl == null || resultsEl.isJsonNull() || !resultsEl.isJsonArray()) return null;
            JsonArray results = resultsEl.getAsJsonArray();
            if (results.isEmpty()) return null;
            
            JsonElement firstEl = results.get(0);
            if (firstEl == null || firstEl.isJsonNull() || !firstEl.isJsonObject()) return null;
            JsonObject first = firstEl.getAsJsonObject();
            
            JsonElement recsEl = first.get("recordings");
            if (recsEl == null || recsEl.isJsonNull() || !recsEl.isJsonArray()) return null;
            JsonArray recs = recsEl.getAsJsonArray();
            if (recs.isEmpty()) return null;
            
            JsonElement recEl = recs.get(0);
            if (recEl == null || recEl.isJsonNull() || !recEl.isJsonObject()) return null;
            JsonObject rec = recEl.getAsJsonObject();
            String t = rec.has("title") ? rec.get("title").getAsString() : null;
            String a = ""; if (rec.has("artists")) { JsonArray ar = rec.getAsJsonArray("artists"); if (!ar.isEmpty()) a = ar.get(0).getAsJsonObject().get("name").getAsString(); }
            return new AcoustIDResult(t, a);
        } catch (Exception e) { LyricHUD.LOGGER.warn("AcoustID error: {} ({})", e.getClass().getSimpleName(), e.getMessage()); return null; }
    }

    private String extractFpcalc() {
        try {
            InputStream is = getClass().getResourceAsStream("/fpcalc.exe");
            if (is == null) return null;
            Path fp = Paths.get(System.getProperty("java.io.tmpdir"), "lyrichud_fpcalc.exe");
            if (!Files.exists(fp)) { Files.copy(is, fp); fp.toFile().setExecutable(true); }
            is.close(); return fp.toString();
        } catch (Exception e) { return null; }
    }

    // --- Kuwo ---
    private SongInfo searchKuwo(String title, String artist) {
        try {
        String kw = URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);
        String url = "https://kuwo.cn/api/www/search/searchMusicBykeyWord?key=" + kw + "&pn=1&rn=5&httpsStatus=1";
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("Referer","https://kuwo.cn/").header("Cookie","kw_token=MC_LYRIC_MOD")
                .header("csrf","MC_LYRIC_MOD").header("User-Agent","Mozilla/5.0").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        String body = resp.body();
        if (body == null || body.isEmpty() || !body.trim().startsWith("{")) return null;
        JsonObject json = GSON.fromJson(body, JsonObject.class);
        if (json.get("code") == null || json.get("code").isJsonNull() || json.get("code").getAsInt() != 200) return null;
        if (json.get("data") == null || json.get("data").isJsonNull()) return null;
        JsonArray list = json.getAsJsonObject("data").getAsJsonArray("list");
        if (list == null || list.isEmpty()) return null;
        for (int i = 0; i < list.size(); i++) {
            JsonObject s = list.get(i).getAsJsonObject();
            String sa = s.get("artist").getAsString();
            if (artist.isEmpty() || sa.contains(artist) || artist.contains(sa))
                return new SongInfo(s.get("rid").getAsString(), s.get("name").getAsString(), sa);
        }
        JsonObject f = list.get(0).getAsJsonObject();
        return new SongInfo(f.get("rid").getAsString(), f.get("name").getAsString(), f.get("artist").getAsString());
        } catch (Exception e) { LyricHUD.LOGGER.warn("Kuwo search error: {}", e.getMessage()); return null; }
    }

    private String fetchKuwoLyric(String songId) {
        try {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://kuwo.cn/newh5/singles/songinfoandlrc?musicId=" + songId))
                .header("User-Agent","Mozilla/5.0").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        if (json.get("data") == null || json.get("data").isJsonNull()) return null;
        JsonObject data = json.getAsJsonObject("data");
        if (data.get("lrclist") == null || data.get("lrclist").isJsonNull()) return null;
        JsonArray lrclist = data.getAsJsonArray("lrclist");
        if (lrclist == null || lrclist.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lrclist.size(); i++) {
            JsonObject l = lrclist.get(i).getAsJsonObject();
            double t = l.get("time").getAsDouble(); String c = l.get("lineLyric").getAsString();
            sb.append(String.format("[%02d:%05.2f]%s\n", (int)(t/60), t%60, c));
        }
        return sb.toString();
        } catch (Exception e) { LyricHUD.LOGGER.warn("Kuwo lyric error: {}", e.getMessage()); return null; }
    }

    private String fetchNetEaseLyric(String songId) {
        try {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://music.163.com/api/song/lyric?id=" + songId + "&lv=-1&kv=-1&tv=-1"))
                .header("User-Agent","Mozilla/5.0").header("Referer","https://music.163.com/").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        
        // Parse LRC first
        if (json.get("lrc") == null || json.get("lrc").isJsonNull()) return null;
        JsonObject lrc = json.getAsJsonObject("lrc");
        if (lrc.get("lyric") == null || lrc.get("lyric").isJsonNull()) return null;
        String lrcText = lrc.get("lyric").getAsString();
        
        // Parse LRC into currentLyrics so translation can match timestamps
        List<LyricLine> tempLyrics = parseLRC(lrcText);
        // Store translations after LRC is parsed (need currentLyrics to match)
        List<LyricLine> old = currentLyrics;
        currentLyrics = tempLyrics;
        
        // Now parse tlyric
        if (json.get("tlyric") != null && !json.get("tlyric").isJsonNull()) {
            JsonObject tlyric = json.getAsJsonObject("tlyric");
            if (tlyric.get("lyric") != null && !tlyric.get("lyric").isJsonNull()) {
                String tText = tlyric.get("lyric").getAsString();
                LyricHUD.LOGGER.info("tlyric found: {} chars, starts with: {}", tText.length(), tText.substring(0, Math.min(50, tText.length())));
                if (!tText.isEmpty()) parseAndStoreTranslation(tText);
            }
        }
        
        return lrcText;
        } catch (Exception e) { LyricHUD.LOGGER.warn("NetEase lyric error: {}", e.getMessage()); return null; }
    }
    
    private void parseAndStoreTranslation(String tlyric) {
        Pattern p = Pattern.compile("\\[(\\d+):(\\d+\\.?\\d*)\\](.*)");
        for (String line : tlyric.split("\\n")) {
            Matcher m = p.matcher(line.trim());
            if (m.find()) {
                String text = m.group(3).trim();
                if (!text.isEmpty()) {
                    int ms = (int)((Integer.parseInt(m.group(1))*60 + Double.parseDouble(m.group(2)))*1000);
                    // Find matching line in current lyrics by time
                    for (int i = 0; i < currentLyrics.size(); i++) {
                        if (Math.abs(currentLyrics.get(i).timeMs - ms) < 500) {
                            translations.put(i, text);
                            break;
                        }
                    }
                }
            }
        }
    }

    private SongInfo searchNetEase(String title, String artist) {
        try {
        String kw = URLEncoder.encode(title + " " + artist, StandardCharsets.UTF_8);
        int skip = LyricReportStore.getSkipCount(artist, title);
        int limit = 5 + skip;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create("https://music.163.com/api/search/get?s=" + kw + "&type=1&limit=" + limit))
                .header("User-Agent","Mozilla/5.0").header("Referer","https://music.163.com/").GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
        if (json.get("code") == null || json.get("code").isJsonNull() || json.get("code").getAsInt() != 200) return null;
        if (json.get("result") == null || json.get("result").isJsonNull()) return null;
        JsonArray songs = json.getAsJsonObject("result").getAsJsonArray("songs");
        if (songs == null || songs.isEmpty() || songs.size() <= skip) return null;
        // Find best match by title similarity
        JsonObject best = null;
        for (int i = 0; i < songs.size(); i++) {
            JsonObject s = songs.get(i).getAsJsonObject();
            String sName = s.get("name").getAsString();
            if (sName.equalsIgnoreCase(title) || sName.toLowerCase().contains(title.toLowerCase()) || title.toLowerCase().contains(sName.toLowerCase())) {
                best = s;
                break;
            }
        }
        if (best == null) return null; // No match found
        String ar = ""; JsonArray arj = best.getAsJsonArray("ar"); if (arj != null && !arj.isEmpty()) ar = arj.get(0).getAsJsonObject().get("name").getAsString();
        return new SongInfo(best.get("id").getAsString(), best.get("name").getAsString(), ar);
        } catch (Exception e) { LyricHUD.LOGGER.warn("NetEase search error: {}", e.getMessage()); return null; }
    }

    private List<LyricLine> parseLRC(String lrc) {
        List<LyricLine> lines = new ArrayList<>();
        Pattern p = Pattern.compile("\\[(\\d+):(\\d+\\.?\\d*)\\](.*)");
        for (String line : lrc.split("\n")) {
            Matcher m = p.matcher(line.trim());
            if (m.find()) { String t = m.group(3).trim(); if (!t.isEmpty()) lines.add(new LyricLine((int)((Integer.parseInt(m.group(1))*60+Double.parseDouble(m.group(2)))*1000), t)); }
        }
        return lines;
    }

    // --- Inner classes ---
    public static class LyricLine { public final int timeMs; public final String text; public LyricLine(int t, String x) { timeMs = t; text = x; } }
    private static class AcoustIDResult { final String title, artist; AcoustIDResult(String t, String a) { title = t; artist = a; } }
    private static class SongInfo { final String songId, title, artist; SongInfo(String i, String t, String a) { songId = i; title = t; artist = a; } }
    public static class SearchResult { final List<LyricLine> lines; final String matchedTitle, matchedArtist, composer; final boolean instrumental; SearchResult(List<LyricLine> l, String t, String a, boolean i, String c) { lines = l; matchedTitle = t; matchedArtist = a; instrumental = i; composer = c; } }
}
