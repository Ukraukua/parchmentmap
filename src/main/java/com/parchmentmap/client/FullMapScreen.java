package com.parchmentmap.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.parchmentmap.data.Icon;
import com.parchmentmap.data.MapData;
import com.parchmentmap.data.MapData.Route;
import com.parchmentmap.data.MapData.Stroke;
import com.parchmentmap.data.MapData.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/** Велика карта у стилі пергаменту: панель мап ліворуч, вкладки «Мітки / Маршрути» праворуч. */
public class FullMapScreen extends Screen {
    private enum Tool { PAN, MARKER, ROUTE, DRAW, ERASE }

    private static final int ROW = 11;
    private static double sScale = 1.0;

    private double cx, cz, scale;
    private Tool tool = Tool.PAN;
    private int tab, scroll, colorIdx;
    private int mapX1, mapY1, mapX2, mapY2, sideX1, sideX2, listY1, listY2, infoY;
    private boolean panning;
    private Stroke curStroke;
    private Waypoint selWp;
    private Route selRoute;
    private final List<PButton> markBtns = new ArrayList<>();
    private final List<PButton> routeBtns = new ArrayList<>();

    public FullMapScreen() {
        super(Component.translatable("gui.parchmentmap.title"));
        LocalPlayer p = Minecraft.getInstance().player;
        cx = p.getX();
        cz = p.getZ();
        scale = sScale;
    }

    private static Component tr(String key, Object... a) { return Component.translatable("gui.parchmentmap." + key, a); }
    private static MapData data() { return ClientState.data; }
    private static String dim() { return Minecraft.getInstance().level.dimension().location().toString(); }
    private MapRender.View view() { return new MapRender.View(cx, cz, scale, (mapX1 + mapX2) / 2, (mapY1 + mapY2) / 2); }

    private List<Waypoint> wps() {
        List<Waypoint> l = new ArrayList<>();
        String d = dim();
        for (Waypoint w : data().waypoints) if (d.equals(w.dim)) l.add(w);
        return l;
    }

    private List<Route> routes() {
        List<Route> l = new ArrayList<>();
        String d = dim();
        for (Route r : data().routes) if (d.equals(r.dim)) l.add(r);
        return l;
    }

    // ---------------------------------------------------------------- розкладка

    @Override
    protected void init() {
        if (ClientState.data == null) { onClose(); return; }
        markBtns.clear();
        routeBtns.clear();
        int pad = 6;
        int sideW = Math.max(130, Math.min(170, width / 3));
        mapX1 = pad;
        mapY1 = pad + 18;
        mapY2 = height - pad;
        mapX2 = width - pad - sideW - 4;
        sideX1 = mapX2 + 4;
        sideX2 = width - pad;
        int ix = sideX1 + 4, iw = sideX2 - sideX1 - 8;
        int y = mapY1 + 3;

        int tw = iw / 2;
        addRenderableWidget(new PButton(ix, y, tw - 1, 14, () -> tr("tab.marks"), () -> { tab = 0; scroll = 0; refresh(); }, () -> tab == 0));
        addRenderableWidget(new PButton(ix + tw + 1, y, tw - 1, 14, () -> tr("tab.routes"), () -> { tab = 1; scroll = 0; refresh(); }, () -> tab == 1));
        y += 18;

        int cw = (iw - 4) / 3;
        toolBtn(ix, y, cw, "tool.pan", Tool.PAN);
        toolBtn(ix + cw + 2, y, cw, "tool.marker", Tool.MARKER);
        toolBtn(ix + 2 * (cw + 2), y, cw, "tool.route", Tool.ROUTE);
        y += 16;
        toolBtn(ix, y, cw, "tool.draw", Tool.DRAW);
        toolBtn(ix + cw + 2, y, cw, "tool.erase", Tool.ERASE);
        addRenderableWidget(new PButton(ix + 2 * (cw + 2), y, cw, 14, () -> tr("tool.me"), this::centerOnPlayer, null));
        y += 16;
        int hw = (iw - 2) / 2;
        addRenderableWidget(new PButton(ix, y, hw, 14, () -> tr("tool.color"), () -> colorIdx = (colorIdx + 1) % MapData.PALETTE.length, null)
                .swatch(() -> MapData.PALETTE[colorIdx]));
        addRenderableWidget(new PButton(ix + hw + 2, y, hw, 14, () -> tr("tool.clear"), () -> {
            String d = dim();
            data().strokes.removeIf(s -> d.equals(s.dim));
            data().save();
        }, null));
        y += 20;

        listY1 = y + 11;
        int b2 = mapY2 - 18, b1 = mapY2 - 34;
        infoY = mapY2 - 58;
        listY2 = mapY2 - 62;
        int bx2 = ix + hw + 2;

        // вкладка «Мітки»
        markBtns.add(add(new PButton(ix, b1, hw, 14, () -> tr(ClientState.navWp != null && ClientState.navWp == selWp ? "btn.nav_stop" : "btn.nav"), () -> {
            if (selWp == null) return;
            if (ClientState.navWp == selWp) ClientState.stopNav(); else ClientState.navigateTo(selWp);
        }, () -> ClientState.navWp != null && ClientState.navWp == selWp)));
        markBtns.add(add(new PButton(bx2, b1, hw, 14, () -> tr("btn.edit"), () -> {
            if (selWp != null) Minecraft.getInstance().setScreen(new WaypointEditScreen(this, selWp, false));
        }, null)));
        markBtns.add(add(new PButton(ix, b2, hw, 14, () -> tr("btn.show"), () -> {
            if (selWp != null) { selWp.visible = !selWp.visible; data().save(); }
        }, null)));
        markBtns.add(add(new PButton(bx2, b2, hw, 14, () -> tr("btn.delete"), () -> {
            if (selWp == null) return;
            if (ClientState.navWp == selWp) ClientState.stopNav();
            data().waypoints.remove(selWp);
            selWp = null;
            data().save();
        }, null)));

        // вкладка «Маршрути»
        routeBtns.add(add(new PButton(ix, b1, hw, 14, () -> tr("btn.new"), this::newRoute, null)));
        routeBtns.add(add(new PButton(bx2, b1, hw, 14, () -> tr(ClientState.navRoute != null && ClientState.navRoute == selRoute ? "btn.nav_stop" : "btn.nav"), () -> {
            if (selRoute == null || selRoute.points.isEmpty()) return;
            if (ClientState.navRoute == selRoute) ClientState.stopNav();
            else ClientState.navigateRoute(selRoute, Minecraft.getInstance().player.getX(), Minecraft.getInstance().player.getZ());
        }, () -> ClientState.navRoute != null && ClientState.navRoute == selRoute)));
        routeBtns.add(add(new PButton(ix, b2, hw, 14, () -> tr("btn.undo"), this::undo, null)));
        routeBtns.add(add(new PButton(bx2, b2, hw, 14, () -> tr("btn.delete"), () -> {
            if (selRoute == null) return;
            if (ClientState.navRoute == selRoute) ClientState.stopNav();
            data().routes.remove(selRoute);
            selRoute = null;
            data().save();
        }, null)));
        refresh();
    }

    private PButton add(PButton b) { addRenderableWidget(b); return b; }

    private void toolBtn(int x, int y, int w, String key, Tool t) {
        addRenderableWidget(new PButton(x, y, w, 14, () -> tr(key), () -> tool = t, () -> tool == t));
    }

    private void refresh() {
        for (PButton b : markBtns) b.visible = tab == 0;
        for (PButton b : routeBtns) b.visible = tab == 1;
    }

    private void centerOnPlayer() {
        LocalPlayer p = Minecraft.getInstance().player;
        cx = p.getX();
        cz = p.getZ();
    }

    private Route newRoute() {
        Route r = new Route();
        r.dim = dim();
        r.name = tr("route_default", data().routes.size() + 1).getString();
        r.color = MapData.PALETTE[colorIdx];
        data().routes.add(r);
        selRoute = r;
        tool = Tool.ROUTE;
        data().save();
        return r;
    }

    private void undo() {
        if (tool == Tool.ROUTE || tab == 1) {
            if (selRoute != null && !selRoute.points.isEmpty()) { selRoute.points.remove(selRoute.points.size() - 1); data().save(); }
        } else {
            String d = dim();
            for (int i = data().strokes.size() - 1; i >= 0; i--) {
                if (d.equals(data().strokes.get(i).dim)) { data().strokes.remove(i); data().save(); break; }
            }
        }
    }

    // ---------------------------------------------------------------- малювання

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        Minecraft mc = Minecraft.getInstance();
        String dim = dim();

        // заголовок
        g.fill(6, 6, width - 6, 22, MapRender.INK);
        g.fill(7, 7, width - 7, 21, 0xFF5A4126);
        g.drawString(font, title, 12, 10, 0xFFE8D5A5, false);
        Component hint = tr("hint");
        if (width > 380) g.drawString(font, hint, width - 12 - font.width(hint), 10, 0xFFE8D5A5, false);

        // карта
        MapRender.frame(g, mapX1, mapY1, mapX2, mapY2);
        g.fill(mapX1, mapY1, mapX2, mapY2, MapRender.PARCH);
        MapRender.View v = view();
        g.enableScissor(mapX1, mapY1, mapX2, mapY2);
        ClientState.cache.drawTerrain(g, dim, v, mapX1, mapY1, mapX2, mapY2);
        MapRender.drawOverlays(g, font, v, dim, true, mapX1, mapY1, mapX2, mapY2, pt);
        if (tool == Tool.ROUTE && selRoute != null && !selRoute.points.isEmpty() && inMap(mx, my)) {
            int[] last = selRoute.points.get(selRoute.points.size() - 1);
            MapRender.line(g, v.sx(last[0] + 0.5), v.sy(last[1] + 0.5), mx, my, 0xFF000000 | selRoute.color, 1, 3, mapX1, mapY1, mapX2, mapY2);
        }
        g.disableScissor();

        // червоний банер-підказка (як на скріншоті)
        Component banner = tr("mode." + tool.name().toLowerCase());
        int bw = font.width(banner) + 12;
        g.fill(mapX1 + 8, mapY1 + 8, mapX1 + 8 + bw, mapY1 + 21, 0xFFB03030);
        g.drawString(font, banner, mapX1 + 14, mapY1 + 11, 0xFFFFFFFF, false);

        // координати курсору
        if (inMap(mx, my)) {
            String c = "X " + (int) Math.floor(v.wx(mx)) + "  Z " + (int) Math.floor(v.wz(my));
            g.fill(mapX1 + 8, mapY2 - 20, mapX1 + 14 + font.width(c), mapY2 - 8, MapRender.INK);
            g.fill(mapX1 + 9, mapY2 - 19, mapX1 + 13 + font.width(c), mapY2 - 9, 0xFFF1E3BC);
            g.drawString(font, c, mapX1 + 11, mapY2 - 17, MapRender.INK, false);
        }
        drawScaleBar(g);

        // права панель
        g.fill(sideX1 - 1, mapY1 - 1, sideX2 + 1, mapY2 + 1, MapRender.INK);
        g.fill(sideX1, mapY1, sideX2, mapY2, 0xFFE4D2A4);
        drawList(g, mx, my);
        super.render(g, mx, my, pt);

        // підказка над міткою
        if (inMap(mx, my)) {
            Waypoint h = waypointAt(mx, my, v);
            if (h != null) {
                LocalPlayer p = mc.player;
                int dist = (int) Math.hypot(p.getX() - h.x, p.getZ() - h.z);
                g.renderComponentTooltip(font, List.of(
                        Component.literal(h.name),
                        Component.literal(h.x + " " + h.y + " " + h.z),
                        Component.literal(dist + " " + tr("blocks").getString())), mx, my);
            }
        }
    }

    private void drawScaleBar(GuiGraphics g) {
        int[] nice = {1, 2, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
        int len = nice[0];
        for (int n : nice) if (n * scale <= 110) len = n;
        int px = (int) Math.round(len * scale);
        Component t = Component.literal(len + " ").append(tr("blocks"));
        int w = Math.max(px, font.width(t)) + 12;
        int x2 = mapX2 - 8, y2 = mapY2 - 8, x1 = x2 - w, y1 = y2 - 24;
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, MapRender.INK);
        g.fill(x1, y1, x2, y2, 0xFFF1E3BC);
        g.drawString(font, t, x1 + 6, y1 + 3, MapRender.INK, false);
        g.fill(x1 + 6, y1 + 16, x1 + 6 + px, y1 + 18, MapRender.INK);
        g.fill(x1 + 6, y1 + 13, x1 + 7, y1 + 18, MapRender.INK);
        g.fill(x1 + 5 + px, y1 + 13, x1 + 6 + px, y1 + 18, MapRender.INK);
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        int ix = sideX1 + 4;
        int headerY = listY1 - 11;
        LocalPlayer p = Minecraft.getInstance().player;
        int rows = Math.max(1, (listY2 - listY1) / ROW);

        if (tab == 0) {
            List<Waypoint> l = wps();
            g.drawString(font, tr("list.marks", l.size()), ix, headerY + 1, MapRender.INK, false);
            g.fill(sideX1 + 2, listY1 - 1, sideX2 - 2, listY2, 0xFFEFE1BB);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - rows)));
            for (int i = 0; i < rows && i + scroll < l.size(); i++) {
                Waypoint w = l.get(i + scroll);
                int y = listY1 + i * ROW;
                if (w == selWp) g.fill(sideX1 + 2, y, sideX2 - 2, y + ROW, 0xFFC9AA72);
                int col = w.visible ? w.color : 0x8A7A5A;
                MapRender.icon(g, Icon.of(w.icon), ix + 5, y + 5, col);
                String dist = (int) Math.hypot(p.getX() - w.x, p.getZ() - w.z) + "";
                int dw = font.width(dist);
                String nm = font.plainSubstrByWidth(w.name, sideX2 - ix - 18 - dw - 8);
                g.drawString(font, nm, ix + 14, y + 2, w.visible ? MapRender.INK : 0xFF8A7A5A, false);
                g.drawString(font, dist, sideX2 - 6 - dw, y + 2, 0xFF6B5535, false);
            }
            if (selWp != null) {
                g.drawString(font, font.plainSubstrByWidth(selWp.name, sideX2 - ix - 4), ix, infoY, MapRender.INK, false);
                g.drawString(font, selWp.x + " " + selWp.y + " " + selWp.z, ix, infoY + 11, 0xFF6B5535, false);
            }
        } else {
            List<Route> l = routes();
            g.drawString(font, tr("list.routes", l.size()), ix, headerY + 1, MapRender.INK, false);
            g.fill(sideX1 + 2, listY1 - 1, sideX2 - 2, listY2, 0xFFEFE1BB);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, l.size() - rows)));
            for (int i = 0; i < rows && i + scroll < l.size(); i++) {
                Route r = l.get(i + scroll);
                int y = listY1 + i * ROW;
                if (r == selRoute) g.fill(sideX1 + 2, y, sideX2 - 2, y + ROW, 0xFFC9AA72);
                g.fill(ix, y + 2, ix + 7, y + 9, MapRender.INK);
                g.fill(ix + 1, y + 3, ix + 6, y + 8, 0xFF000000 | r.color);
                g.drawString(font, font.plainSubstrByWidth(r.name, sideX2 - ix - 20), ix + 11, y + 2, MapRender.INK, false);
            }
            if (selRoute != null) {
                g.drawString(font, font.plainSubstrByWidth(selRoute.name, sideX2 - ix - 4), ix, infoY, MapRender.INK, false);
                g.drawString(font, font.plainSubstrByWidth(tr("route_info", selRoute.points.size(), (int) length(selRoute)).getString(), sideX2 - ix - 4),
                        ix, infoY + 11, 0xFF6B5535, false);
            }
        }
    }

    private static double length(Route r) {
        double s = 0;
        for (int i = 0; i + 1 < r.points.size(); i++) {
            int[] a = r.points.get(i), b = r.points.get(i + 1);
            s += Math.hypot(a[0] - b[0], a[1] - b[1]);
        }
        return s;
    }

    // ---------------------------------------------------------------- ввід

    private boolean inMap(double mx, double my) { return mx >= mapX1 && mx < mapX2 && my >= mapY1 && my < mapY2; }
    private boolean inList(double mx, double my) { return mx >= sideX1 && mx < sideX2 && my >= listY1 && my < listY2; }

    private Waypoint waypointAt(double mx, double my, MapRender.View v) {
        for (Waypoint w : wps()) {
            if (!w.visible) continue;
            if (Math.abs(v.sx(w.x + 0.5) - mx) <= 5 && Math.abs(v.sy(w.z + 0.5) - my) <= 5) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (inList(mx, my) && button == 0) {
            int idx = (int) ((my - listY1) / ROW) + scroll;
            if (tab == 0) {
                List<Waypoint> l = wps();
                if (idx >= 0 && idx < l.size()) { selWp = l.get(idx); cx = selWp.x + 0.5; cz = selWp.z + 0.5; }
            } else {
                List<Route> l = routes();
                if (idx >= 0 && idx < l.size()) {
                    selRoute = l.get(idx);
                    if (!selRoute.points.isEmpty()) { cx = selRoute.points.get(0)[0]; cz = selRoute.points.get(0)[1]; }
                }
            }
            return true;
        }

        if (!inMap(mx, my)) return false;
        MapRender.View v = view();

        if (button == 1 || button == 2 || (button == 0 && tool == Tool.PAN)) {
            panning = true;
            if (button == 0) {
                Waypoint w = waypointAt(mx, my, v);
                if (w != null) { selWp = w; tab = 0; scroll = 0; refresh(); }
            }
            return true;
        }
        if (button != 0) return false;

        int bx = (int) Math.floor(v.wx(mx)), bz = (int) Math.floor(v.wz(my));
        switch (tool) {
            case MARKER -> {
                Minecraft mc = Minecraft.getInstance();
                Waypoint w = new Waypoint();
                w.dim = dim();
                w.x = bx;
                w.z = bz;
                w.y = mc.level.hasChunk(bx >> 4, bz >> 4) ? mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) : mc.player.getBlockY();
                w.color = MapData.PALETTE[colorIdx];
                w.name = tr("wp_default", data().waypoints.size() + 1).getString();
                mc.setScreen(new WaypointEditScreen(this, w, true));
            }
            case ROUTE -> {
                if (selRoute == null || !dim().equals(selRoute.dim)) newRoute();
                selRoute.points.add(new int[]{bx, bz});
                data().save();
            }
            case DRAW -> {
                curStroke = new Stroke();
                curStroke.dim = dim();
                curStroke.color = MapData.PALETTE[colorIdx];
                curStroke.points.add(new int[]{bx, bz});
                data().strokes.add(curStroke);
            }
            case ERASE -> erase(mx, my);
            default -> { }
        }
        return true;
    }

    private void erase(double mx, double my) {
        MapRender.View v = view();
        String d = dim();
        boolean removed = data().strokes.removeIf(s -> {
            if (!d.equals(s.dim)) return false;
            for (int[] p : s.points)
                if (Math.abs(v.sx(p[0] + 0.5) - mx) <= 6 && Math.abs(v.sy(p[1] + 0.5) - my) <= 6) return true;
            return false;
        });
        if (removed) data().save();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            cx -= dx / scale;
            cz -= dy / scale;
            return true;
        }
        if (button == 0 && inMap(mx, my)) {
            if (curStroke != null) {
                MapRender.View v = view();
                int bx = (int) Math.floor(v.wx(mx)), bz = (int) Math.floor(v.wz(my));
                int[] last = curStroke.points.get(curStroke.points.size() - 1);
                if (Math.hypot(bx - last[0], bz - last[1]) * scale >= 3) curStroke.points.add(new int[]{bx, bz});
                return true;
            }
            if (tool == Tool.ERASE) { erase(mx, my); return true; }
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        panning = false;
        if (curStroke != null) { data().save(); curStroke = null; }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inMap(mx, my)) {
            MapRender.View v = view();
            double wx = v.wx(mx), wz = v.wz(my);
            scale = Math.max(0.125, Math.min(8.0, delta > 0 ? scale * 2 : scale / 2));
            sScale = scale;
            cx = wx - (mx - v.midX) / scale;
            cz = wz - (my - v.midY) / scale;
            return true;
        }
        if (mx >= sideX1) { scroll = Math.max(0, scroll - (int) Math.signum(delta)); return true; }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (Keys.OPEN.matches(key, scan)) { onClose(); return true; }
        if (key == InputConstants.KEY_C) { centerOnPlayer(); return true; }
        if (key == InputConstants.KEY_BACKSPACE) { undo(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (ClientState.cache != null) ClientState.cache.saveAll();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
