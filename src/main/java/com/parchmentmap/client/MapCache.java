package com.parchmentmap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.parchmentmap.ParchmentMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Кеш відкритої місцевості. Світ ділиться на регіони 512x512 блоків (1 піксель = 1 блок),
 * кожен регіон — текстура, яка зберігається у PNG у теці гри (parchmentmap/&lt;світ&gt;/&lt;вимір&gt;/).
 */
public class MapCache {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int[][] ALL_OFFSETS = offsets(16);
    private static final int[] WATER = {0x5FA2D6, 0x4E92CB, 0x3F82BF, 0x3374B0, 0x2A66A0, 0x225A92, 0x1B4E83};
    private static int texCounter;

    private final Path dir;
    private final Map<String, Map<Long, Region>> dims = new HashMap<>();
    private final Set<String> missing = new HashSet<>();
    private int tick, cursor, activeR = -1;
    private int[][] active = ALL_OFFSETS;

    public MapCache(Path dir) { this.dir = dir; }

    public static final class Region {
        final String dim; final int rx, rz;
        final NativeImage image;
        DynamicTexture texture;
        ResourceLocation loc;
        boolean needsUpload = true, needsSave;

        Region(String dim, int rx, int rz, NativeImage image) { this.dim = dim; this.rx = rx; this.rz = rz; this.image = image; }

        ResourceLocation texture() {
            if (texture == null) {
                texture = new DynamicTexture(image);
                loc = new ResourceLocation(ParchmentMap.MOD_ID, "region/r" + (texCounter++));
                Minecraft.getInstance().getTextureManager().register(loc, texture);
                needsUpload = false;
            } else if (needsUpload) {
                texture.upload();
                needsUpload = false;
            }
            return loc;
        }

        void close() {
            if (texture != null) {
                try { Minecraft.getInstance().getTextureManager().release(loc); } catch (Exception ignored) { }
            } else {
                image.close();
            }
        }
    }

    private static int[][] offsets(int r) {
        List<int[]> l = new ArrayList<>();
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) l.add(new int[]{x, z});
        l.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        return l.toArray(new int[0][]);
    }

    private static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xFFFFFFFFL); }

    private Path file(String dim, int rx, int rz) {
        return dir.resolve(dim.replaceAll("[^a-zA-Z0-9._-]", "_")).resolve("r." + rx + "." + rz + ".png");
    }

    public Region get(String dim, int rx, int rz, boolean create) {
        Map<Long, Region> m = dims.computeIfAbsent(dim, k -> new HashMap<>());
        long k = key(rx, rz);
        Region r = m.get(k);
        if (r != null) return r;
        String mk = dim + "|" + k;
        if (!create && missing.contains(mk)) return null;
        NativeImage img = null;
        Path f = file(dim, rx, rz);
        if (Files.exists(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                img = NativeImage.read(in);
                if (img.getWidth() != 512 || img.getHeight() != 512) { img.close(); img = null; }
            } catch (IOException e) {
                LOG.warn("ParchmentMap: cannot read {}", f, e);
            }
        }
        if (img == null) {
            if (!create) { missing.add(mk); return null; }
            img = new NativeImage(512, 512, true);
        }
        r = new Region(dim, rx, rz, img);
        m.put(k, r);
        missing.remove(mk);
        return r;
    }

    // ---------------------------------------------------------------- сканування

    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null) return;
        String dim = level.dimension().location().toString();
        tick++;
        int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;

        if (tick % 10 == 1) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) scanChunk(level, dim, pcx + dx, pcz + dz, p.getBlockY());
        }

        int r = Math.max(2, Math.min(16, mc.options.getEffectiveRenderDistance()));
        if (r != activeR) {
            activeR = r;
            List<int[]> l = new ArrayList<>();
            for (int[] o : ALL_OFFSETS) if (Math.max(Math.abs(o[0]), Math.abs(o[1])) <= r) l.add(o);
            active = l.toArray(new int[0][]);
            cursor = 0;
        }
        for (int i = 0; i < 2; i++) {
            int[] o = active[cursor];
            cursor = (cursor + 1) % active.length;
            scanChunk(level, dim, pcx + o[0], pcz + o[1], p.getBlockY());
        }

        if (tick % 100 == 0) saveSome(1);
    }

    private void scanChunk(ClientLevel level, String dim, int cx, int cz, int py) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null) return;
        boolean ceil = level.dimensionType().hasCeiling();
        Region reg = get(dim, cx >> 5, cz >> 5, true);
        int baseX = (cx & 31) << 4, baseZ = (cz & 31) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = (cx << 4) + lx, wz = (cz << 4) + lz;
                int c = columnColor(level, chunk, wx, wz, lx, lz, py, ceil, pos);
                if (reg.image.getPixelRGBA(baseX + lx, baseZ + lz) != c) {
                    reg.image.setPixelRGBA(baseX + lx, baseZ + lz, c);
                    changed = true;
                }
            }
        }
        if (changed) { reg.needsUpload = true; reg.needsSave = true; }
    }

    private static int columnColor(ClientLevel level, LevelChunk chunk, int wx, int wz, int lx, int lz, int py, boolean ceil, BlockPos.MutableBlockPos pos) {
        int min = level.getMinBuildHeight();
        int y;
        if (ceil) {
            y = Math.min(py, level.getMaxBuildHeight() - 1);
            while (y > min && chunk.getBlockState(pos.set(wx, y, wz)).isAir()) y--;
        } else {
            y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) - 1;
        }
        if (y <= min) return 0;
        BlockState st = chunk.getBlockState(pos.set(wx, y, wz));
        // трава, квіти, папороть тощо не фарбують карту: беремо блок під ними (grass block => зелений)
        while (y > min && st.getFluidState().isEmpty()
                && (st.getBlock() instanceof BushBlock || st.getMapColor(level, pos) == MapColor.NONE)) {
            y--;
            st = chunk.getBlockState(pos.set(wx, y, wz));
        }
        if (st.getFluidState().is(FluidTags.WATER)) {
            int depth = 0, yy = y;
            while (yy > min && depth < 40 && chunk.getBlockState(pos.set(wx, yy, wz)).getFluidState().is(FluidTags.WATER)) { depth++; yy--; }
            int band = Math.min(depth / 3, 6);
            int c = WATER[band];
            if (((wx + wz) & 1) == 0 && depth % 3 == 2) c = WATER[Math.min(band + 1, 6)];
            return abgr(c);
        }
        int rgb = landColor(level, st, pos, wx, wz);
        if (rgb < 0) return 0;
        double f = 1.0;
        if (!ceil) {
            int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
            int hn = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1);
            if (hn > min + 1) f = h > hn ? 1.10 : h < hn ? 0.90 : 1.0;
        }
        // легкий «піксельний» шум, як на скріншоті
        int hs = (wx * 73856093) ^ (wz * 19349663);
        hs ^= hs >>> 13;
        f *= 1.0 + (((hs & 15) - 7.5) / 7.5) * 0.035;
        return abgr(scale(rgb, f));
    }

    private static int landColor(ClientLevel level, BlockState st, BlockPos pos, int wx, int wz) {
        if (st.is(Blocks.GRASS_BLOCK)) {
            int c = level.getBiome(pos).value().getGrassColor(wx, wz);
            return mix(c, 0x6C9A47, 0.45);
        }
        if (st.is(BlockTags.LEAVES)) {
            int c = level.getBiome(pos).value().getFoliageColor();
            return scale(mix(c, 0x3E6B2C, 0.55), 0.85);
        }
        int rgb = st.getMapColor(level, pos).col;
        return rgb == 0 ? -1 : rgb;
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int scale(int rgb, double f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    /** NativeImage зберігає піксель як 0xAABBGGRR. */
    private static int abgr(int rgb) {
        return 0xFF000000 | ((rgb & 255) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 255);
    }

    // ---------------------------------------------------------------- малювання

    public void drawTerrain(GuiGraphics g, String dim, MapRender.View v, int x1, int y1, int x2, int y2) {
        int rx0 = (int) Math.floor(v.wx(x1)) >> 9, rx1 = (int) Math.floor(v.wx(x2)) >> 9;
        int rz0 = (int) Math.floor(v.wz(y1)) >> 9, rz1 = (int) Math.floor(v.wz(y2)) >> 9;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                Region r = get(dim, rx, rz, false);
                if (r == null) continue;
                ResourceLocation loc = r.texture();
                g.pose().pushPose();
                g.pose().translate(v.sx(rx * 512.0), v.sy(rz * 512.0), 0);
                g.pose().scale((float) v.scale, (float) v.scale, 1f);
                g.blit(loc, 0, 0, 0f, 0f, 512, 512, 512, 512);
                g.pose().popPose();
            }
        }
    }

    // ---------------------------------------------------------------- збереження

    public void saveSome(int max) {
        int n = 0;
        for (Map<Long, Region> m : dims.values()) {
            for (Region r : m.values()) {
                if (!r.needsSave) continue;
                saveRegion(r);
                if (++n >= max) return;
            }
        }
    }

    public void saveAll() { saveSome(Integer.MAX_VALUE); }

    private void saveRegion(Region r) {
        Path f = file(r.dim, r.rx, r.rz);
        try {
            Files.createDirectories(f.getParent());
            r.image.writeToFile(f);
            r.needsSave = false;
        } catch (IOException e) {
            LOG.warn("ParchmentMap: cannot write {}", f, e);
        }
    }

    public void close() {
        saveAll();
        for (Map<Long, Region> m : dims.values()) for (Region r : m.values()) r.close();
        dims.clear();
        missing.clear();
    }
}
