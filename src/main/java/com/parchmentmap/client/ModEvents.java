package com.parchmentmap.client;

import com.parchmentmap.ParchmentMap;
import com.parchmentmap.data.MapData;
import com.parchmentmap.data.MapData.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ModEvents {
    private ModEvents() {}

    @Mod.EventBusSubscriber(modid = ParchmentMap.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(Keys.OPEN);
            e.register(Keys.TOGGLE);
            e.register(Keys.WAYPOINT);
            e.register(Keys.ZOOM);
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("minimap", MiniMapOverlay::render);
        }
    }

    @Mod.EventBusSubscriber(modid = ParchmentMap.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            ClientState.ensure(mc);
            ClientState.tick(mc);
            MapData d = ClientState.data;

            while (Keys.OPEN.consumeClick()) if (mc.screen == null) mc.setScreen(new FullMapScreen());
            while (Keys.TOGGLE.consumeClick()) { d.minimap = !d.minimap; d.save(); }
            while (Keys.ZOOM.consumeClick()) { d.miniZoom = (d.miniZoom + 1) % ClientState.MINI_ZOOMS.length; d.save(); }
            while (Keys.WAYPOINT.consumeClick()) {
                if (mc.screen != null) continue;
                Waypoint w = new Waypoint();
                w.dim = mc.level.dimension().location().toString();
                w.x = mc.player.getBlockX();
                w.y = mc.player.getBlockY();
                w.z = mc.player.getBlockZ();
                w.name = Component.translatable("gui.parchmentmap.wp_default", d.waypoints.size() + 1).getString();
                mc.setScreen(new WaypointEditScreen(null, w, true));
            }
        }

        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
            ClientState.close();
        }
    }
}
