package com.mc.lyrichud.api;

import com.mc.lyrichud.LyricHUD;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.io.InputStream;
import java.util.Optional;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = LyricHUD.MODID)
public class SoundEventListener {
    private static ResourceLocation lastMusicTrack = null;

    /** Called by SoundManagerMixin when weighted music resolves to actual file */
    public static void onMusicResolved(ResourceLocation eventLoc, ResourceLocation fileLoc) {
        if (LyricHUD.fetcher == null || LyricHUD.renderer == null) return;
        LyricHUD.LOGGER.info("Resolved: {} -> {}", eventLoc, fileLoc);
        Minecraft mc = Minecraft.getInstance();
        try {
            var r = mc.getResourceManager().getResource(fileLoc);
            if (r.isPresent()) {
                java.io.InputStream is = r.get().open();
                byte[] ad = is.readAllBytes(); is.close();
                LyricHUD.renderer.onSongStart();
                LyricHUD.fetcher.fetchLyrics("", "", ad)
                        .thenAccept(lr -> LyricHUD.LOGGER.info("Lines: {} for {}", lr.size(), LyricHUD.fetcher.getCurrentSongTitle()));
            }
        } catch (Exception e) {
            LyricHUD.LOGGER.warn("onMusicResolved error: {}", e.getMessage());
        }
    }

    @SubscribeEvent
    public static void onSoundPlay(PlaySoundEvent event) {
        SoundInstance sound = event.getOriginalSound();
        if (sound == null) return;
        if (sound.getSource() != SoundSource.MUSIC) return;
        ResourceLocation loc = sound.getLocation();
        if (loc.equals(lastMusicTrack)) return;
        lastMusicTrack = loc;
        
        // Log class info to diagnose weighted sound issue
        String oc = sound.getClass().getSimpleName();
        SoundInstance ps = event.getSound();
        String pc = ps != null ? ps.getClass().getSimpleName() : "null";
        String os = "?"; try { os = sound.getSound() != null ? "Sound" : "null"; } catch (Exception ignored) {}
        String ps2 = "?"; if (ps != null) try { ps2 = ps.getSound() != null ? "Sound:"+ps.getSound().getPath() : "null"; } catch (Exception ignored) {}
        LyricHUD.LOGGER.info("Music: {} | orig={} snd={} | proc={} snd={}", loc, oc, os, pc, ps2);

        String title = "", artist = "";
        String path = loc.getPath();
        if (path.contains("/")) {
            String fn = path.substring(path.lastIndexOf('/') + 1)
                    .replaceAll("\\.(ogg|mp3|wav|flac)$", "").replace("_", " ");
            title = fn;
            if (fn.contains(" - ")) { String[] p = fn.split(" - ", 2); artist = p[0].trim(); title = p[1].trim(); }
        }

        // Try both: original (unwrapped) and processed (possibly resolved) sound
        byte[] ad = readAudio(sound, loc);
        if (ad == null) ad = readAudio(event.getSound(), loc);
        
        final byte[] fad = ad; final String ft = title, fa = artist;
        LyricHUD.renderer.onSongStart();
        LyricHUD.fetcher.fetchLyrics(ft, fa, fad)
                .thenAccept(lr -> LyricHUD.LOGGER.info("Lines: {} for {}", lr.size(), LyricHUD.fetcher.getCurrentSongTitle()));
    }

    private static byte[] readAudio(SoundInstance sound, ResourceLocation loc) {
        Minecraft mc = Minecraft.getInstance();

        // 1) Sound.getPath()
        try { Sound s = sound.getSound(); if (s != null) {
            var r = mc.getResourceManager().getResource(s.getPath());
            if (r.isPresent()) { InputStream is = r.get().open(); byte[] d = is.readAllBytes(); is.close(); return d; }
        }} catch (Exception ignored) {}

        // 2) event location directly
        try { var r = mc.getResourceManager().getResource(loc);
            if (r.isPresent()) { InputStream is = r.get().open(); byte[] d = is.readAllBytes(); is.close(); return d; }
        } catch (Exception ignored) {}

        // 3) Directory listing disabled — can't know which file is actually playing
        LyricHUD.LOGGER.debug("Methods 1+2 failed for {}, no audio data", loc);

        LyricHUD.LOGGER.warn("Cannot read audio for {}", loc);
        return null;
    }
}
