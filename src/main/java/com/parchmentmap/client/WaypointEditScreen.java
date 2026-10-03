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

    public WaypointEditScreen(Screen parent, Waypoint wp, boolean isNew) {
        super(Component.translatable("gui.parchmentmap.edit.title"));
        this.parent = parent;
        this.wp = wp;
        this.isNew = isNew;
        this.iconIdx = Icon.of(wp.icon).ordinal();
        this.colorIdx = 0;
        for (int i = 0; i < MapData.PALETTE.length; i++) if (MapData.PALETTE[i] == wp.color) colorIdx = i;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 - 46;
        name = new EditBox(font, cx - 100, y, 200, 18, Component.empty());
        name.setMaxLength(32);
        name.setValue(wp.name);
        addRenderableWidget(name);
        setInitialFocus(name);

        addRenderableWidget(new PButton(cx - 100, y + 26, 200, 16,
                () -> Component.translatable("gui.parchmentmap.edit.icon", Component.translatable(Icon.values()[iconIdx].langKey())),
                () -> iconIdx = (iconIdx + 1) % Icon.values().length, null));
        addRenderableWidget(new PButton(cx - 100, y + 46, 200, 16,
                () -> Component.translatable("gui.parchmentmap.edit.color"),
                () -> colorIdx = (colorIdx + 1) % MapData.PALETTE.length, null).swatch(() -> MapData.PALETTE[colorIdx]));

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
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) { save(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int cx = width / 2, y = height / 2 - 46;
        g.fill(cx - 112, y - 30, cx + 112, y + 100, MapRender.INK);
        g.fill(cx - 110, y - 28, cx + 110, y + 98, MapRender.PARCH);
        g.drawCenteredString(font, title, cx, y - 20, MapRender.INK);
        String c = "X " + wp.x + "   Y " + wp.y + "   Z " + wp.z;
        g.drawCenteredString(font, c, cx, y + 66 - 1 + 0, 0xFF6B5535);
        MapRender.icon(g, Icon.values()[iconIdx], cx + 94, y - 20, MapData.PALETTE[colorIdx]);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() { Minecraft.getInstance().setScreen(parent); }

    @Override
    public boolean isPauseScreen() { return false; }
}
