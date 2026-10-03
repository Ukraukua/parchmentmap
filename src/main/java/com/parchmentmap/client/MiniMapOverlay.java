package com.parchmentmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class MiniMapOverlay {
    private static final int SIZE = 96;

    private MiniMapOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.screen instanceof FullMapScreen) return;
        if (ClientState.data == null || ClientState.cache == null || !ClientState.data.minimap) return;

        Font font = mc.font;
        String dim = mc.level.dimension().location().toString();
        Vec3 pos = mc.player.getPosition(partial);
        int x1 = w - SIZE - 8, y1 = 8, x2 = x1 + SIZE, y2 = y1 + SIZE;
        double scale = ClientState.MINI_ZOOMS[ClientState.data.miniZoom];
        MapRender.View v = new MapRender.View(pos.x, pos.z, scale, (x1 + x2) / 2, (y1 + y2) / 2);

        MapRender.frame(g, x1, y1, x2, y2);
        g.fill(x1, y1, x2, y2, MapRender.PARCH);
        g.enableScissor(x1, y1, x2, y2);
        ClientState.cache.drawTerrain(g, dim, v, x1, y1, x2, y2);
        MapRender.drawOverlays(g, font, v, dim, false, x1, y1, x2, y2, partial);
        g.disableScissor();

        g.drawString(font, "N", (x1 + x2) / 2 - 2, y1 + 2, 0xFFFFFFFF, true);

        String coords = mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ();
        g.drawString(font, coords, x2 - font.width(coords), y2 + 5, 0xFFFFFFFF, true);

        int[] t = ClientState.navTarget(dim);
        if (t != null) {
            int dist = (int) Math.hypot(pos.x - (t[0] + 0.5), pos.z - (t[1] + 0.5));
            Component c = Component.translatable("gui.parchmentmap.nav.hud", ClientState.navName(), dist);
            g.drawString(font, c, x2 - font.width(c), y2 + 16, MapRender.GOLD, true);
        }
    }
}
