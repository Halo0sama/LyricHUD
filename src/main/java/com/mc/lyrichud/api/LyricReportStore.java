package com.mc.lyrichud.api;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.mc.lyrichud.LyricHUD;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores user reports of wrong lyrics and provides skip counts.
 */
public class LyricReportStore {
    private static final Path FILE = Path.of("config", "lyrichud_reports.json");
    private static final Gson GSON = new Gson();
    private static Map<String, Integer> skipMap = new ConcurrentHashMap<>();
    private static boolean loaded = false;

    private static void load() {
        if (loaded) return;
        try {
            if (Files.exists(FILE)) {
                String json = Files.readString(FILE, java.nio.charset.StandardCharsets.UTF_8);
                Map<String, Integer> map = GSON.fromJson(json, new TypeToken<Map<String, Integer>>(){}.getType());
                if (map != null) skipMap = new ConcurrentHashMap<>(map);
            }
        } catch (Exception e) {
            LyricHUD.LOGGER.warn("Failed to load reports: {}", e.getMessage());
        }
        loaded = true;
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(skipMap), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            LyricHUD.LOGGER.warn("Failed to save reports: {}", e.getMessage());
        }
    }

    public static int getSkipCount(String artist, String title) {
        load();
        String key = (artist + "||" + title).toLowerCase();
        return skipMap.getOrDefault(key, 0);
    }

    public static int reportWrong(String artist, String title) {
        load();
        String key = (artist + "||" + title).toLowerCase();
        skipMap.put(key, skipMap.getOrDefault(key, 0) + 1);
        int count = skipMap.get(key);
        save();
        LyricHUD.LOGGER.info("Reported wrong lyric: {} - {} (skip={})", artist, title, count);
        return count;
    }
}
