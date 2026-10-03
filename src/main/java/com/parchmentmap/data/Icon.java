package com.parchmentmap.data;

public enum Icon {
    HOME(new String[]{"...#...", "..###..", ".#####.", "#######", ".##.##.", ".##.##.", ".#####."}),
    FLAG(new String[]{"#......", "####...", "#####..", "####...", "#......", "#......", "#......"}),
    STAR(new String[]{"...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", ".#...#."}),
    DANGER(new String[]{"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"}),
    ANCHOR(new String[]{"...#...", "..###..", "...#...", "#..#..#", "#..#..#", ".#####.", "..###.."});

    public final String[] rows;

    Icon(String[] rows) { this.rows = rows; }

    public static Icon of(String s) {
        try { return valueOf(s); } catch (Exception e) { return FLAG; }
    }

    public String langKey() { return "gui.parchmentmap.icon." + name().toLowerCase(); }
}
