package com.parchmentmap.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Плоска кнопка в стилі пергаменту. */
public class PButton extends AbstractButton {
    private final Supplier<Component> label;
    private final Runnable action;
    private final BooleanSupplier active;
    private IntSupplier swatch;

    public PButton(int x, int y, int w, int h, Supplier<Component> label, Runnable action, BooleanSupplier active) {
        super(x, y, w, h, Component.empty());
        this.label = label;
        this.action = action;
        this.active = active;
    }

    public PButton swatch(IntSupplier s) { this.swatch = s; return this; }

    @Override
    public void onPress() { action.run(); }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean act = active != null && active.getAsBoolean();
        int bg = act ? 0xFF7A5A32 : isHoveredOrFocused() ? 0xFFC9AA72 : 0xFFD9BF8A;
        g.fill(getX(), getY(), getX() + width, getY() + height, MapRender.INK);
        g.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, bg);
        int tc = act ? 0xFFFFF1CF : MapRender.INK;
        var font = Minecraft.getInstance().font;
        int tx = getX() + width / 2 - (swatch != null ? 5 : 0);
        g.drawCenteredString(font, label.get(), tx, getY() + (height - 8) / 2, tc);
        if (swatch != null) {
            int sx = getX() + width - 12, sy = getY() + (height - 7) / 2;
            g.fill(sx - 1, sy - 1, sx + 8, sy + 8, MapRender.INK);
            g.fill(sx, sy, sx + 7, sy + 7, 0xFF000000 | swatch.getAsInt());
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) { defaultButtonNarrationText(out); }
}
