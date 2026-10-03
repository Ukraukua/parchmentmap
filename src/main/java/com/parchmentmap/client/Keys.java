package com.parchmentmap.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class Keys {
    private static final String CAT = "key.categories.parchmentmap";
    public static final KeyMapping OPEN = new KeyMapping("key.parchmentmap.open", InputConstants.KEY_M, CAT);
    public static final KeyMapping TOGGLE = new KeyMapping("key.parchmentmap.toggle", InputConstants.KEY_N, CAT);
    public static final KeyMapping WAYPOINT = new KeyMapping("key.parchmentmap.waypoint", InputConstants.KEY_B, CAT);
    public static final KeyMapping ZOOM = new KeyMapping("key.parchmentmap.zoom", InputConstants.KEY_Z, CAT);

    private Keys() {}
}
