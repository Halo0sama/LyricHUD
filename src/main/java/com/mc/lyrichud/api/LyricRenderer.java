package com.mc.lyrichud.api;

import com.mc.lyrichud.LyricHUD;
import com.mc.lyrichud.LyricConfig;
import com.mc.lyrichud.api.LyricFetcher.LyricLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.List;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = LyricHUD.MODID)
public class LyricRenderer {
    private static final int LH = 12, MG = 8, ML = 1;
    private static final long ENTER = 1000, SHOW = 5000, EXIT = 1000;
    private static boolean wasInGame = false;

    private enum S { HIDDEN, IN, SHOWING, OUT }
    private static S st = S.HIDDEN;
    private static long t0; private static boolean wasP; private static String lastId = "";
    private static boolean pendingAnim = false;
    private long pt; private boolean pl;

    public void onSongStart() { pt = now(); pl = true; }

    private static boolean lastReportState = false;

    @SubscribeEvent public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        
        // Report wrong lyric: only fire on false→true edge
        boolean reportNow = LyricConfig.REPORT_WRONG_LYRIC.get();
        if (reportNow && !lastReportState) {
            String t = LyricHUD.fetcher.getCurrentSongTitle();
            String a = LyricHUD.fetcher.getCurrentSongArtist();
            if (!t.isEmpty()) LyricReportStore.reportWrong(a, t);
        }
        lastReportState = reportNow;
        
        // Clear HUD on scene transitions
        boolean inGame = mc.level != null;
        if (inGame != wasInGame) {
            st = S.HIDDEN; lastId = "";
            if (LyricHUD.fetcher != null) {
                LyricHUD.fetcher.getCurrentLyrics().clear();
            }
        }
        wasInGame = inGame;
        
        boolean p = paused(); if (!p && wasP) { st = S.HIDDEN; } wasP = p; if (p) return;
        switch (st) { case IN: if (el()>=ENTER){st=S.SHOWING;t0=now();} break; case SHOWING: if (el()>=SHOW){st=S.OUT;t0=now();} break; case OUT: if (el()>=EXIT) st=S.HIDDEN; break; }
    }
    @SubscribeEvent public static void onHUD(RenderGuiEvent.Post e) { startIfPending(); draw(e.getGuiGraphics()); }
    @SubscribeEvent public static void onScreen(ScreenEvent.Render.Post e) { if (Minecraft.getInstance().player!=null) return; startIfPending(); draw(e.getGuiGraphics()); }

    private static void startIfPending() {
        if (pendingAnim && st == S.HIDDEN) {
            st = S.IN; t0 = now();
        }
        pendingAnim = false; // One bonus max, never repeat
    }

    private static void draw(net.minecraft.client.gui.GuiGraphics g) {
        if (LyricHUD.renderer==null||LyricHUD.fetcher==null) return;
        if (!LyricConfig.SHOW_HUD.get()) return;
        Minecraft mc = Minecraft.getInstance(); if (mc.player!=null&&mc.options.hideGui) return;
        List<LyricLine> lr = LyricHUD.fetcher.getCurrentLyrics(); if (lr==null||lr.isEmpty()) return;
        Font f = mc.font;
        if (LyricHUD.fetcher.isInstrumental()) { inst(g,f); return; }
        int x=MG, y=MG+20; long el=LyricHUD.renderer.pl?now()-LyricHUD.renderer.pt:0;
        int cur=0; for (int i=0;i<lr.size();i++){if(lr.get(i).timeMs<=el)cur=i;else break;}
        int bw=0,sl=cur,el2=Math.min(lr.size(),sl+ML);
        for(int i=sl;i<el2;i++){int w=f.width(lr.get(i).text);if(w>bw)bw=w;}
        bw=Math.max(bw,100)+MG*2; int bh=(el2-sl)*LH+MG;
        String ti=LyricHUD.fetcher.getCurrentSongTitle(),ar=LyricHUD.fetcher.getCurrentSongArtist();
        if(!ti.isEmpty())g.drawString(f,ar.isEmpty()?ti:"\u266a "+ar+" - "+ti,x,y-LH-2,0xFFAAAAAA);
        for(int i=sl;i<el2;i++)
            g.drawString(f,lr.get(i).text,x,y+(i-sl)*LH,i==cur?0xFFFFFF00:0xFFCCCCCC);
        String tr = LyricHUD.fetcher.getTranslations().get(cur);
        if (tr != null && !tr.isEmpty()) {
            g.drawString(f, tr, x+8, y+LH, 0xFF88CCFF);
        } else if (cur+1 < lr.size()) {
            g.drawString(f, lr.get(cur+1).text, x, y+LH, 0xFFCCCCCC);
        }
    }

    private static void inst(net.minecraft.client.gui.GuiGraphics g, Font f) {
        String ti=LyricHUD.fetcher.getCurrentSongTitle(),co=LyricHUD.fetcher.getComposer();
        if(ti.isEmpty())return; String id=ti+"|"+co;
        if(!id.equals(lastId)){lastId=id;st=S.IN;t0=now();pendingAnim=true;} // Start first round immediately, flag for bonus round
        int mw=f.width(ti); if(!co.isEmpty())mw=Math.max(mw,f.width("\u2014\u2014 "+co));
        mw=Math.max(mw,60); int pw=mw+MG*2+8,tx=MG,dx;
        if(paused()){dx=tx;}else{long t=el();float p;switch(st){
            case IN:p=clamp(t,ENTER);p=1f-(1f-p)*(1f-p)*(1f-p);dx=(int)(-pw+(pw+tx)*p);break;
            case SHOWING:dx=tx;break;case OUT:p=clamp(t,EXIT);p=p*p*p;dx=(int)(tx-(pw+tx)*p);break;
            default:dx=-pw;}}
        int dy=MG+20;
        if(dx+pw>0){ol(g,f,ti,dx+4,dy+2,0xFFFFFF00);
        if(!co.isEmpty())ol(g,f,"\u2014\u2014 "+co,dx+4,dy+LH+4,0xFFCCCCCC);}
    }

    private static void ol(net.minecraft.client.gui.GuiGraphics g,Font f,String t,int x,int y,int c){
        int s=0xDD000000; g.drawString(f,t,x+1,y,s);g.drawString(f,t,x-1,y,s);
        g.drawString(f,t,x,y+1,s);g.drawString(f,t,x,y-1,s);g.drawString(f,t,x,y,c);
    }

    private static boolean paused(){Minecraft mc=Minecraft.getInstance();if(mc.screen instanceof PauseScreen)return true;if(mc.level==null&&mc.screen instanceof OptionsScreen)return true;return false;}
    private static long now(){return System.currentTimeMillis();} private static long el(){return now()-t0;}
    private static float clamp(long t,long max){return Math.min(1f,(float)t/max);}
}
