package com.mc.lyrichud.mixin;

import com.mc.lyrichud.api.SoundEventListener;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    @Inject(method = "m_120367_", at = @At("RETURN"), remap = false)
    private void afterPlay(SoundInstance s, CallbackInfo ci) {
        if (s == null) return;
        try {
            Sound snd = s.getSound();
            if (snd != null && s.getSource() == SoundSource.MUSIC) {
                SoundEventListener.onMusicResolved(s.getLocation(), snd.getPath());
            }
        } catch (Exception ignored) {}
    }
}
