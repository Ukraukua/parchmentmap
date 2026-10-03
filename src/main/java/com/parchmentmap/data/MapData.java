package com.parchmentmap.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Користувацькі дані світу: мітки, маршрути, малюнки, налаштування міні-карти. */
public class MapData {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int[] PALETTE = {0xD94A3A, 0xE39B2D, 0xE8D44D, 0x4CAF50, 0x3FB6C9, 0x3F6FD9, 0x9B59B6, 0xF2F2F2};

    public boolean minimap = true;
    public int miniZoom = 1;
    public List<Waypoint> waypoints = new ArrayList<>();
    public List<Route> routes = new ArrayList<>();
    public List<Stroke> strokes = new ArrayList<>();
    private transient Path file;

    public static class Waypoint {
        public String name = "";
        public String dim = "";
        public int x, y, z;
        public int color = PALETTE[0];
        public String icon = "FLAG";
        public boolean visible = true;
    }

    public static class Route {
        public String name = "";
        public String dim = "";
        public int color = PALETTE[1];
        public List<int[]> points = new ArrayList<>();
    }

    public static class Stroke {
        public String dim = "";
        public int color = PALETTE[0];
        public List<int[]> points = new ArrayList<>();
    }

    public static MapData load(Path f) {
        MapData d = null;
        try {
            if (Files.exists(f)) d = GSON.fromJson(Files.readString(f), MapData.class);
        } catch (Exception e) {
            LOG.error("ParchmentMap: cannot read {}", f, e);
        }
        if (d == null) d = new MapData();
        if (d.waypoints == null) d.waypoints = new ArrayList<>();
        if (d.routes == null) d.routes = new ArrayList<>();
        if (d.strokes == null) d.strokes = new ArrayList<>();
        d.miniZoom = Math.max(0, Math.min(3, d.miniZoom));
        d.file = f;
        return d;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(this));
        } catch (Exception e) {
            LOG.error("ParchmentMap: cannot save {}", file, e);
        }
    }
}
