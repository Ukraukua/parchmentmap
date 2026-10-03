package com.parchmentmap;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

/** Клієнтський мод: сервер його не потребує. */
@Mod(ParchmentMap.MOD_ID)
public class ParchmentMap {
    public static final String MOD_ID = "parchmentmap";

    public ParchmentMap() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, isServer) -> true));
    }
}
