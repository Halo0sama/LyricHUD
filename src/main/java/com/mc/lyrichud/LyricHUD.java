package com.mc.lyrichud;

import com.mc.lyrichud.api.AcrcloudClient;
import com.mc.lyrichud.api.LyricFetcher;
import com.mc.lyrichud.api.LyricRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(LyricHUD.MODID)
public class LyricHUD {
    public static final String MODID = "lyrichud";
    public static final Logger LOGGER = LoggerFactory.getLogger("LyricHUD");
    public static LyricRenderer renderer;
    public static LyricFetcher fetcher;

    public LyricHUD() {
        var ctx = ModLoadingContext.get();
        ctx.registerConfig(ModConfig.Type.CLIENT, LyricConfig.CLIENT_SPEC);
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onClientSetup(FMLClientSetupEvent e) {
        LOGGER.info("LyricHUD init...");
        String host = LyricConfig.ACR_HOST.get();
        String key = LyricConfig.ACR_KEY.get();
        String secret = LyricConfig.ACR_SECRET.get();
        AcrcloudClient acr = new AcrcloudClient(host, key, secret);
        fetcher = new LyricFetcher(acr);
        renderer = new LyricRenderer();
        LOGGER.info("LyricHUD ready! ACRCloud: {}", acr.isEnabled());
    }
}
