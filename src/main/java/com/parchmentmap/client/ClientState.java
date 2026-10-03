package com.parchmentmap.client;

import com.parchmentmap.data.MapData;
import com.parchmentmap.data.MapData.Route;
import com.parchmentmap.data.MapData.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

public final class ClientState {
    public static final double[] MINI_ZOOMS = {0.5, 1.0, 2.0, 4.0};
    public static MapCache cache;
    public static MapData data;
    public static Waypoint navWp;
    public static Route navRoute;
    public static int navIdx;

    private ClientState() {}

    public static void ensure(Minecraft mc) {
        if (cache != null && data != null) return;
        String id;
        if (mc.getSingleplayerServer() != null) {
            id = "sp_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else {
            ServerData sd = mc.getCurrentServer();
            id = "mp_" + (sd != null ? sd.ip : "unknown");
        }
        id = id.replaceAll("[^a-zA-Z0-9._-]", "_");
        Path dir = mc.gameDirectory.toPath().resolve("parchmentmap").resolve(id);
        cache = new MapCache(dir);
        data = MapData.load(dir.resolve("data.json"));
    }

    public static void close() {
        if (cache != null) cache.close();
        if (data != null) data.save();
        cache = null;
        data = null;
        stopNav();
    }

    public static void tick(Minecraft mc) {
        cache.tick(mc);
        String dim = mc.level.dimension().location().toString();
        int[] t = navTarget(dim);
        if (t == null) return;
        double d = Math.hypot(mc.player.getX() - (t[0] + 0.5), mc.player.getZ() - (t[1] + 0.5));
        if (navWp != null) {
            if (d < 4) {
                mc.player.displayClientMessage(Component.translatable("gui.parchmentmap.nav.arrived", navWp.name), true);
                navWp = null;
            }
        } else if (navRoute != null && d < 6) {
            navIdx++;
            if (navIdx >= navRoute.points.size()) {
                mc.player.displayClientMessage(Component.translatable("gui.parchmentmap.nav.route_done"), true);
                navRoute = null;
            }
        }
    }

    public static int[] navTarget(String dim) {
        if (navWp != null) return dim.equals(navWp.dim) ? new int[]{navWp.x, navWp.z} : null;
        if (navRoute != null && dim.equals(navRoute.dim) && navIdx >= 0 && navIdx < navRoute.points.size())
            return navRoute.points.get(navIdx);
        return null;
    }

    public static String navName() {
        return navWp != null ? navWp.name : navRoute != null ? navRoute.name : "";
    }

    public static boolean navActive() { return navWp != null || navRoute != null; }

    public static void navigateTo(Waypoint w) { navWp = w; navRoute = null; }

    public static void navigateRoute(Route r, double px, double pz) {
        navWp = null;
        navRoute = r;
        navIdx = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < r.points.size(); i++) {
            int[] p = r.points.get(i);
            double d = Math.hypot(p[0] - px, p[1] - pz);
            if (d < best) { best = d; navIdx = i; }
        }
    }

    public static void stopNav() { navWp = null; navRoute = null; navIdx = 0; }
}
