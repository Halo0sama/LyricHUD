package com.mc.lyrichud.api;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;

/**
 * Built-in mapping for vanilla Minecraft music tracks.
 * These are well-known C418 / Lena Raine compositions that no API can identify
 * because they exist only in Minecraft's resource format.
 */
public class VanillaTrackMap {
    
    /** Maps resource path endings to (title, artist, instrumental) */
    private static final Map<String, String[]> TRACKS = Map.ofEntries(
        // C418 - Volume Alpha
        Map.entry("calm1.ogg",    new String[]{"Minecraft", "C418", "true"}),
        Map.entry("calm2.ogg",    new String[]{"Clark", "C418", "true"}),
        Map.entry("calm3.ogg",    new String[]{"Sweden", "C418", "true"}),
        Map.entry("hal1.ogg",     new String[]{"Subwoofer Lullaby", "C418", "true"}),
        Map.entry("hal2.ogg",     new String[]{"Living Mice", "C418", "true"}),
        Map.entry("hal3.ogg",     new String[]{"Haggstrom", "C418", "true"}),
        Map.entry("hal4.ogg",     new String[]{"Danny", "C418", "true"}),
        Map.entry("nuance1.ogg",  new String[]{"Key", "C418", "true"}),
        Map.entry("nuance2.ogg",  new String[]{"Oxygene", "C418", "true"}),
        Map.entry("piano1.ogg",   new String[]{"Dry Hands", "C418", "true"}),
        Map.entry("piano2.ogg",   new String[]{"Wet Hands", "C418", "true"}),
        Map.entry("piano3.ogg",   new String[]{"Mice on Venus", "C418", "true"}),
        Map.entry("creative1.ogg",new String[]{"Creative1", "C418", "true"}),
        Map.entry("creative2.ogg",new String[]{"Creative2", "C418", "true"}),
        Map.entry("creative3.ogg",new String[]{"Creative3", "C418", "true"}),
        Map.entry("creative4.ogg",new String[]{"Creative4", "C418", "true"}),
        Map.entry("creative5.ogg",new String[]{"Creative5", "C418", "true"}),
        Map.entry("creative6.ogg",new String[]{"Creative6", "C418", "true"}),
        
        // C418 - Volume Beta
        Map.entry("menu1.ogg",    new String[]{"Mutation", "C418", "true"}),
        Map.entry("menu2.ogg",    new String[]{"Moog City 2", "C418", "true"}),
        Map.entry("menu3.ogg",    new String[]{"Beginning 2", "C418", "true"}),
        Map.entry("menu4.ogg",    new String[]{"Floating Trees", "C418", "true"}),
        
        // Nether Update - Lena Raine
        Map.entry("nether1.ogg",  new String[]{"Rubedo", "Lena Raine", "true"}),
        Map.entry("nether2.ogg",  new String[]{"Chrysopoeia", "Lena Raine", "true"}),
        Map.entry("nether3.ogg",  new String[]{"So Below", "Lena Raine", "true"}),
        Map.entry("nether4.ogg",  new String[]{"Pigstep", "Lena Raine", "true"}),
        
        // Wild Update
        Map.entry("aerie.ogg",    new String[]{"Aerie", "Lena Raine", "true"}),
        Map.entry("firebugs.ogg", new String[]{"Firebugs", "Lena Raine", "true"}),
        Map.entry("labyrinthine.ogg", new String[]{"Labyrinthine", "Lena Raine", "true"}),
        
        // Trails & Tales
        Map.entry("bromeliad.ogg",new String[]{"Bromeliad", "Aaron Cherof", "true"}),
        Map.entry("crescent_dunes.ogg", new String[]{"Crescent Dunes", "Aaron Cherof", "true"}),
        Map.entry("echo_in_the_wind.ogg", new String[]{"Echo in the Wind", "Aaron Cherof", "true"}),
        
        // Tricky Trials
        Map.entry("comforting_memories.ogg", new String[]{"Comforting Memories", "Kumi Tanioka", "true"}),
        Map.entry("an_ordinary_day.ogg", new String[]{"An Ordinary Day", "Kumi Tanioka", "true"}),
        
        // 1.21 Garden Awakens
        Map.entry("a_familiar_room.ogg", new String[]{"A Familiar Room", "Aaron Cherof", "true"})
    );
    
    // Sound event name → (title, artist, instrumental)
    private static final Map<String, String[]> EVENTS = Map.of(
        "music.menu",     new String[]{"Minecraft Menu", "C418 / Lena Raine", "true"},
        "music.game",     new String[]{"Overworld Music", "C418", "true"},
        "music.creative", new String[]{"Creative Music", "C418", "true"},
        "music.credits",  new String[]{"Alpha", "C418", "false"},
        "music.nether",   new String[]{"Nether Music", "Lena Raine", "true"},
        "music.end",      new String[]{"The End", "C418", "true"}
    );
    
    /**
     * Look up a track by its resource location.
     * Returns null if not a known track.
     */
    public static TrackInfo lookup(ResourceLocation location) {
        String path = location.getPath();
        
        // Check sound event names first (e.g., "music.menu")
        String[] evt = EVENTS.get(path);
        if (evt != null) return new TrackInfo(evt[0], evt[1], "true".equals(evt[2]));
        
        // Check file names (e.g., "music/game/calm2.ogg")
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        String[] info = TRACKS.get(fileName);
        if (info != null) return new TrackInfo(info[0], info[1], "true".equals(info[2]));
        
        return null;
    }
    
    public static class TrackInfo {
        public final String title;
        public final String artist;
        public final boolean instrumental;
        
        TrackInfo(String t, String a, boolean i) { title = t; artist = a; instrumental = i; }
    }
}
