package com.mc.lyrichud;

import net.minecraftforge.common.ForgeConfigSpec;

public class LyricConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> ACR_HOST;
    public static final ForgeConfigSpec.ConfigValue<String> ACR_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> ACR_SECRET;
    public static final ForgeConfigSpec.BooleanValue SHOW_HUD;
    public static final ForgeConfigSpec.BooleanValue REPORT_WRONG_LYRIC;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ACR_HOST = b.comment("ACRCloud API host").define("acr_host", "identify-cn-north-1.acrcloud.cn");
        ACR_KEY = b.comment("ACRCloud Access Key (empty=disabled)").define("acr_key", "");
        ACR_SECRET = b.comment("ACRCloud Access Secret (empty=disabled)").define("acr_secret", "");
        SHOW_HUD = b.comment("Show lyric HUD (master switch)").define("show_hud", true);
        REPORT_WRONG_LYRIC = b.comment("Toggle ON to report current song's lyrics as wrong (auto-resets)").define("report_wrong", false);
        CLIENT_SPEC = b.build();
    }
}
