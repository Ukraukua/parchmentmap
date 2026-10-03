package com.parchmentmap.client;

import com.mojang.math.Axis;
import com.parchmentmap.data.Icon;
import com.parchmentmap.data.MapData;
import com.parchmentmap.data.MapData.Route;
import com.parchmentmap.data.MapData.Stroke;
import com.parchmentmap.data.MapData.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

/** Спільні функції малювання для міні-карти та великої карти. */
public final class MapRender {
    public static final int INK = 0xFF3B2A1A;
    public static final int DARK = 0xFF1C1209;
    public static final int PARCH = 0xFFE8D9B0;
    public static final int EDGE = 0xFFB89860;
    public static final int GOLD = 0xFFFFD34D;

    private MapRender() {}

    /** Перетворення світ ⇄ екран. */
    public static final class View {
        public final double cx, cz, scale;
        public final int midX, midY;

        public View(double cx, double cz, double scale, int midX, int midY) {
            this.cx = cx; this.cz = cz; this.scale = scale; this.midX = midX; this.midY = midY;
        }

        public int sx(double wx) { return (int) Math.round(midX + (wx - cx) * scale); }
        public int sy(double wz) { return (int) Math.round(midY + (wz - cz) * scale); }
        public double wx(double sx) { return cx + (sx - midX) / scale; }
        public double wz(double sy) { return cz + (sy - midY) / scale; }
    }

    public static void frame(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1 - 3, y1 - 3, x2 + 3, y2 + 3, INK);
        g.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, EDGE);
        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, INK);
    }

    // ---------------------------------------------------------------- примітиви

    private static void bitmap(GuiGraphics g, String[] rows, int x, int y, int color) {
        for (int j = 0; j < rows.length; j++) {
            String r = rows[j];
            int i = 0;
            while (i < r.length()) {
                if (r.charAt(i) == '#') {
                    int s = i;
                    while (i < r.length() && r.charAt(i) == '#') i++;
                    g.fill(x + s, y + j, x + i, y + j + 1, color);
                } else i++;
            }
        }
    }

    public static void icon(GuiGraphics g, Icon ic, int cx, int cy, int rgb) {
        int x = cx - 3, y = cy - 3;
        bitmap(g, ic.rows, x - 1, y, DARK);
        bitmap(g, ic.rows, x + 1, y, DARK);
        bitmap(g, ic.rows, x, y - 1, DARK);
        bitmap(g, ic.rows, x, y + 1, DARK);
        bitmap(g, ic.rows, x, y, 0xFF000000 | rgb);
    }

    private static boolean clip(double p, double q, double[] t) {
        if (p == 0) return q >= 0;
        double r = q / p;
        if (p < 0) { if (r > t[1]) return false; if (r > t[0]) t[0] = r; }
        else { if (r < t[0]) return false; if (r < t[1]) t[1] = r; }
        return true;
    }

    /** Лінія з обрізкою по прямокутнику; dash &gt; 0 — пунктир. */
    public static void line(GuiGraphics g, double ax, double ay, double bx, double by, int color, int th, int dash,
                            int minX, int minY, int maxX, int maxY) {
        double dx = bx - ax, dy = by - ay;
        double[] t = {0, 1};
        if (!clip(-dx, ax - minX, t) || !clip(dx, maxX - ax, t) || !clip(-dy, ay - minY, t) || !clip(dy, maxY - ay, t)) return;
        double len = Math.hypot(dx, dy);
        int n = (int) Math.ceil((t[1] - t[0]) * len);
        int off = (int) (t[0] * len);
        int h = th / 2;
        for (int i = 0; i <= n; i++) {
            if (dash > 0 && ((i + off) / dash) % 2 == 1) continue;
            double f = n == 0 ? t[0] : t[0] + (t[1] - t[0]) * i / n;
            int px = (int) Math.round(ax + dx * f), py = (int) Math.round(ay + dy * f);
            g.fill(px - h, py - h, px - h + th, py - h + th, color);
        }
    }

    private static void polyline(GuiGraphics g, View v, List<int[]> pts, int color, int th, int dash, int x1, int y1, int x2, int y2) {
        if (pts.size() == 1) {
            int sx = v.sx(pts.get(0)[0] + 0.5), sy = v.sy(pts.get(0)[1] + 0.5);
            g.fill(sx - th / 2, sy - th / 2, sx - th / 2 + th, sy - th / 2 + th, color);
            return;
        }
        for (int i = 0; i + 1 < pts.size(); i++) {
            int[] a = pts.get(i), b = pts.get(i + 1);
            line(g, v.sx(a[0] + 0.5), v.sy(a[1] + 0.5), v.sx(b[0] + 0.5), v.sy(b[1] + 0.5), color, th, dash, x1, y1, x2, y2);
        }
    }

    // ---------------------------------------------------------------- шари поверх рельєфу

    public static void drawOverlays(GuiGraphics g, Font font, View v, String dim, boolean full,
                                    int x1, int y1, int x2, int y2, float partial) {
        MapData d = ClientState.data;
        LocalPlayer p = Minecraft.getInstance().player;
        if (d == null || p == null) return;

        // 1. малюнки від руки
        for (Stroke s : d.strokes) {
            if (!dim.equals(s.dim) || s.points.isEmpty()) continue;
            polyline(g, v, s.points, 0xFF000000 | s.color, 2, 0, x1, y1, x2, y2);
        }

        // 2. маршрути + мітки точок маршруту
        for (Route r : d.routes) {
            if (!dim.equals(r.dim) || r.points.isEmpty()) continue;
            int col = 0xFF000000 | r.color;
            polyline(g, v, r.points, INK, 4, 5, x1, y1, x2, y2);
            polyline(g, v, r.points, col, 2, 5, x1, y1, x2, y2);
            int i = 0;
            for (int[] pt : r.points) {
                i++;
                int sx = v.sx(pt[0] + 0.5), sy = v.sy(pt[1] + 0.5);
                if (sx < x1 - 6 || sx > x2 + 6 || sy < y1 - 6 || sy > y2 + 6) continue;
                g.fill(sx - 3, sy - 3, sx + 4, sy + 4, INK);
                g.fill(sx - 2, sy - 2, sx + 3, sy + 3, col);
                if (full) g.drawString(font, Integer.toString(i), sx + 5, sy - 4, 0xFFFFFFFF, true);
            }
        }

        // 3. лінія навігації
        double px = p.getPosition(partial).x, pz = p.getPosition(partial).z;
        int[] nt = ClientState.navTarget(dim);
        if (nt != null) {
            line(g, v.sx(px), v.sy(pz), v.sx(nt[0] + 0.5), v.sy(nt[1] + 0.5), INK, 4, 4, x1, y1, x2, y2);
            line(g, v.sx(px), v.sy(pz), v.sx(nt[0] + 0.5), v.sy(nt[1] + 0.5), GOLD, 2, 4, x1, y1, x2, y2);
        }

        // 4. мітки
        for (Waypoint w : d.waypoints) {
            if (!w.visible || !dim.equals(w.dim)) continue;
            int sx = v.sx(w.x + 0.5), sy = v.sy(w.z + 0.5);
            boolean inside = sx >= x1 - 4 && sx <= x2 + 4 && sy >= y1 - 4 && sy <= y2 + 4;
            if (inside) {
                icon(g, Icon.of(w.icon), sx, sy, w.color);
                if (full) label(g, font, w.name, sx, sy + 7);
            } else if (w == ClientState.navWp) {
                int cx = Math.max(x1 + 5, Math.min(x2 - 5, sx)), cy = Math.max(y1 + 5, Math.min(y2 - 5, sy));
                icon(g, Icon.of(w.icon), cx, cy, w.color);
            }
        }

        // 5. гравець
        int sx = Math.max(x1 + 5, Math.min(x2 - 5, v.sx(px)));
        int sy = Math.max(y1 + 5, Math.min(y2 - 5, v.sy(pz)));
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(p.getYRot() + 180f));
        int[][] arrow = {{-1, -5, 1, -3}, {-2, -3, 2, -1}, {-3, -1, 3, 1}, {-1, 1, 1, 3}};
        for (int[] a : arrow) g.fill(a[0] - 1, a[1] - 1, a[2] + 1, a[3] + 1, DARK);
        for (int[] a : arrow) g.fill(a[0], a[1], a[2], a[3], 0xFFD9332B);
        g.pose().popPose();
    }

    /** Підпис на кшталт бежевої таблички зі скріншота. */
    public static void label(GuiGraphics g, Font font, String text, int cx, int top) {
        int w = font.width(text);
        int x = cx - w / 2 - 3;
        g.fill(x - 1, top - 1, x + w + 7, top + 11, INK);
        g.fill(x, top, x + w + 6, top + 10, 0xFFF1E3BC);
        g.drawString(font, text, x + 3, top + 1, INK, false);
    }
}
