package com.mc.lyrichud.api;

import com.mc.lyrichud.LyricHUD;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = LyricHUD.MODID)
public class LyricCommand {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("lyricreport").executes(ctx -> {
            String t = LyricHUD.fetcher.getCurrentSongTitle();
            String a = LyricHUD.fetcher.getCurrentSongArtist();
            if (t.isEmpty()) return 0;
            int skip = LyricReportStore.reportWrong(a, t);
            ctx.getSource().sendSuccess(() -> Component.literal(
                "Reported: " + a + " - " + t + " (will skip " + skip + " next time)"), false);
            return 1;
        }));
    }
}
