package com.parchmentmap.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.parchmentmap.data.Icon;
import com.parchmentmap.data.MapData;
import com.parchmentmap.data.MapData.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class WaypointEditScreen extends Screen {
    private final Screen parent;
    private final Waypoint wp;
    private final boolean isNew;
    private EditBox name;
    private int colorIdx, iconIdx;
    private int popup; // 0 — немає, 1 — вибір значка, 2 — вибір кольору

    public WaypointEditScreen(Screen parent, Waypoint wp, boolean isNew) {
        super(Component.translatable("gui.parchmentmap.edit.title"));
        this.parent = parent;
        this.wp = wp;
        this.isNew = isNew;
        this.iconIdx = Icon.of(wp.icon).ordinal();
        this.colorIdx = 0;
        for (int i = 0; i < MapData.PALETTE.length; i++) if (MapData.PALETTE[i] == wp.color) colorIdx = i;
    }

    private int top() { return height / 2 - 46; }
    private int popX() { return width / 2 - 100; }
    private int popY() { return top() + 44; }
    private int popH() { return popup == 1 ? 56 : 42; }

    @Override
    protected void init() {
        int cx = width / 2, y = top();
        name = new EditBox(font, cx - 100, y, 200, 18, Component.empty());
        name.setMaxLength(32);
        name.setValue(wp.name);
        addRenderableWidget(name);
        setInitialFocus(name);

        addRenderableWidget(new PButton(cx - 100, y + 26, 200, 16,
                () -> Component.translatable("gui.parchmentmap.edit.icon", Component.translatable(Icon.values()[iconIdx].langKey())),
                () -> popup = 1, null));
        addRenderableWidget(new PButton(cx - 100, y + 46, 200, 16,
                () -> Component.translatable("gui.parchmentmap.edit.color"),
                () -> popup = 2, null).swatch(() -> MapData.PALETTE[colorIdx]));

        int by = y + 74;
        int bw = isNew ? 98 : 64;
        int bx = cx - 100;
        addRenderableWidget(new PButton(bx, by, bw, 16, () -> Component.translatable("gui.parchmentmap.edit.save"), this::save, null));
        addRenderableWidget(new PButton(bx + bw + 2, by, bw, 16, () -> Component.translatable("gui.parchmentmap.edit.cancel"), this::onClose, null));
        if (!isNew) {
            addRenderableWidget(new PButton(bx + 2 * bw + 4, by, bw + 4, 16, () -> Component.translatable("gui.parchmentmap.edit.delete"), () -> {
                if (ClientState.navWp == wp) ClientState.stopNav();
                ClientState.data.waypoints.remove(wp);
                ClientState.data.save();
                onClose();
            }, null));
        }
    }

    private void save() {
        String n = name.getValue().trim();
        wp.name = n.isEmpty() ? Component.translatable("gui.parchmentmap.wp_default", ClientState.data.waypoints.size() + 1).getString() : n;
        wp.color = MapData.PALETTE[colorIdx];
        wp.icon = Icon.values()[iconIdx].name();
        if (isNew) ClientState.data.waypoints.add(wp);
        ClientState.data.save();
        onClose();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (popup != 0) {
            if (button == 0) {
                int px = popX(), py = popY();
                if (popup == 1) {
                    int i = (int) Math.floor((mx - (px + 5)) / 38.0);
                    if (mx >= px + 5 && my >= py + 14 && my < py + 52 && i >= 0 && i < Icon.values().length) iconIdx = i;
                } else {
                    int i = (int) Math.floor((mx - (px + 4)) / 24.0);
                    if (mx >= px + 4 && my >= py + 16 && my < py + 38 && i >= 0 && i < MapData.PALETTE.length) colorIdx = i;
                }
            }
            popup = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (popup != 0) {
            if (key == InputConstants.KEY_ESCAPE) popup = 0;
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { save(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (popup != 0) return false;
        return super.charTyped(c, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2, y = top();
        g.fill(cx - 112, y - 30, cx + 112, y + 100, MapRender.INK);
        g.fill(cx - 110, y - 28, cx + 110, y + 98, MapRender.PARCH);
        g.drawCenteredString(font, title, cx, y - 20, MapRender.INK);
        g.drawCenteredString(font, "X " + wp.x + "   Y " + wp.y + "   Z " + wp.z, cx, y + 65, 0xFF6B5535);
        int rgb = MapData.PALETTE[colorIdx];
        MapRender.icon(g, Icon.values()[iconIdx], cx + 94, y - 20, rgb);
        super.render(g, mx, my, pt);
        MapRender.icon(g, Icon.values()[iconIdx], cx + 90, y + 34, rgb);
        if (popup != 0) drawPopup(g, mx, my);
    }

    private void drawPopup(GuiGraphics g, int mx, int my) {
        int px = popX(), py = popY(), pw = 200, ph = popH();
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, MapRender.INK);
        g.fill(px, py, px + pw, py + ph, MapRender.PARCH);
        g.drawString(font, Component.translatable(popup == 1 ? "gui.parchmentmap.edit.pick_icon" : "gui.parchmentmap.edit.pick_color"),
                px + 5, py + 3, 0xFF6B5535, false);
        int rgb = MapData.PALETTE[colorIdx];
        if (popup == 1) {
            Icon[] ics = Icon.values();
            for (int i = 0; i < ics.length; i++) {
                int x0 = px + 5 + i * 38, y0 = py + 14;
                boolean hov = mx >= x0 && mx < x0 + 36 && my >= y0 && my < y0 + 38;
                if (i == iconIdx) g.fill(x0 - 1, y0 - 1, x0 + 37, y0 + 39, MapRender.GOLD);
                g.fill(x0, y0, x0 + 36, y0 + 38, hov ? 0xFFC9AA72 : 0xFFEFE1BB);
                g.pose().pushPose();
                g.pose().translate(x0 + 18, y0 + 19, 0);
                g.pose().scale(3f, 3f, 1f);
                MapRender.icon(g, ics[i], 0, 0, rgb);
                g.pose().popPose();
            }
        } else {
            for (int i = 0; i < MapData.PALETTE.length; i++) {
                int x0 = px + 4 + i * 24, y0 = py + 16;
                boolean hov = mx >= x0 && mx < x0 + 22 && my >= y0 && my < y0 + 22;
                if (i == colorIdx) g.fill(x0 - 1, y0 - 1, x0 + 23, y0 + 23, MapRender.GOLD);
                g.fill(x0, y0, x0 + 22, y0 + 22, hov ? 0xFFC9AA72 : 0xFFEFE1BB);
                g.fill(x0 + 3, y0 + 3, x0 + 19, y0 + 19, MapRender.INK);
                g.fill(x0 + 4, y0 + 4, x0 + 18, y0 + 18, 0xFF000000 | MapData.PALETTE[i]);
            }
        }
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
