/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.bukkit.Material;

public final class Palette {
  public static final class Swatch {
    public final Material material;
    public final byte data;
    public final int rgb;
    public final double[] lab;

    Swatch(Material m, int data, int rgb) {
      material = m;
      this.data = (byte) data;
      this.rgb = rgb;
      lab = ColorMath.lab(rgb);
    }

    public String key() {
      return material.name() + ":" + data;
    }
  }

  private final List<Swatch> entries = new ArrayList<>();
  private final Map<String, Swatch> textureNames = new HashMap<>();
  private final boolean modern;

  public Palette(List<String> allow) {
    modern = Material.getMaterial("WHITE_WOOL") != null;
    String[] colors = {
      "WHITE",
      "ORANGE",
      "MAGENTA",
      "LIGHT_BLUE",
      "YELLOW",
      "LIME",
      "PINK",
      "GRAY",
      "LIGHT_GRAY",
      "CYAN",
      "PURPLE",
      "BLUE",
      "BROWN",
      "GREEN",
      "RED",
      "BLACK"
    };
    int[] wool = {
      0xE9ECEC, 0xF07613, 0xBD44B3, 0x3AAFD9, 0xF8C527, 0x70B919, 0xED8DAC, 0x3E4447, 0x8E8E86,
      0x158991, 0x792AAC, 0x35399D, 0x724728, 0x546D1B, 0xA12722, 0x141519
    };
    int[] clay = {
      0xD1B1A1, 0xA15325, 0x95576C, 0x706C8A, 0xBA8523, 0x677535, 0xA14E4E, 0x392A23, 0x876B62,
      0x575B5B, 0x764656, 0x4A3B5B, 0x4D3323, 0x4C532A, 0x8F3D2F, 0x251610
    };
    int[] concrete = {
      0xCFD5D6, 0xE06101, 0xA9309F, 0x2389C6, 0xF1AF15, 0x5EA818, 0xD5658E, 0x36393D, 0x7D7D73,
      0x157788, 0x64209C, 0x2D2F8F, 0x603B1F, 0x495B24, 0x8E2020, 0x080A0F
    };
    for (int i = 0; i < 16; i++) {
      add(colors[i] + "_WOOL", "WOOL", i, wool[i]);
      add(colors[i] + "_TERRACOTTA", "STAINED_CLAY", i, clay[i]);
      add(colors[i] + "_CONCRETE", "CONCRETE", i, concrete[i]);
    }
    add("STONE", "STONE", 0, 0x7D7D7D);
    add("COBBLESTONE", "COBBLESTONE", 0, 0x747474);
    add("DIRT", "DIRT", 0, 0x866043);
    add("OAK_PLANKS", "WOOD", 0, 0xA2834F);
    add("SPRUCE_PLANKS", "WOOD", 1, 0x725430);
    add("BIRCH_PLANKS", "WOOD", 2, 0xC0AF79);
    add("JUNGLE_PLANKS", "WOOD", 3, 0xA07350);
    add("ACACIA_PLANKS", "WOOD", 4, 0xA85832);
    add("DARK_OAK_PLANKS", "WOOD", 5, 0x422B14);
    add("OBSIDIAN", "OBSIDIAN", 0, 0x100B18);
    add("NETHERRACK", "NETHERRACK", 0, 0x612626);
    add("END_STONE", "ENDER_STONE", 0, 0xDBDE9E);
    add("BRICKS", "BRICK", 0, 0x97614F);
    add("STONE_BRICKS", "SMOOTH_BRICK", 0, 0x7A797A);
    add("QUARTZ_BLOCK", "QUARTZ_BLOCK", 0, 0xECE6DC);
    add("GOLD_BLOCK", "GOLD_BLOCK", 0, 0xF6CF42);
    add("IRON_BLOCK", "IRON_BLOCK", 0, 0xDCDCDC);
    add("DIAMOND_BLOCK", "DIAMOND_BLOCK", 0, 0x62E4DA);
    add("EMERALD_BLOCK", "EMERALD_BLOCK", 0, 0x2ACB57);
    add("LAPIS_BLOCK", "LAPIS_BLOCK", 0, 0x1E438C);
    add("COAL_BLOCK", "COAL_BLOCK", 0, 0x111111);
    add("SNOW_BLOCK", "SNOW_BLOCK", 0, 0xF9FEFE);
    add("PRISMARINE", "PRISMARINE", 0, 0x64A99B);
    add("DARK_PRISMARINE", "PRISMARINE", 2, 0x335B4B);
    add("DEEPSLATE", "DEEPSLATE", 0, 0x505052);
    add("TUFF", "TUFF", 0, 0x6C6D65);
    add("CALCITE", "CALCITE", 0, 0xDFE0DC);
    add("MUD_BRICKS", "MUD_BRICKS", 0, 0x89674F);
    add("CHERRY_PLANKS", "CHERRY_PLANKS", 0, 0xE3B1AD);
    add("BAMBOO_PLANKS", "BAMBOO_PLANKS", 0, 0xC2AE51);
    add("CRIMSON_PLANKS", "CRIMSON_PLANKS", 0, 0x653046);
    add("WARPED_PLANKS", "WARPED_PLANKS", 0, 0x2B6963);
    add("PALE_OAK_PLANKS", "PALE_OAK_PLANKS", 0, 0xE4DBC8);
    add("POPLAR_PLANKS", "POPLAR_PLANKS", 0, 0xBCB091);
    add("PACKED_MUD", "PACKED_MUD", 0, 0x896B4F);
    add("MOSS_BLOCK", "MOSS_BLOCK", 0, 0x596D2D);
    filter(allow);
  }

  private void add(String modernName, String legacy, int data, int rgb) {
    Material m = Material.getMaterial(modern ? modernName : legacy);
    if (m != null && m.isBlock()) {
      Swatch s = new Swatch(m, modern ? 0 : data, rgb);
      entries.add(s);
      textureNames.put(modernName, s);
    }
  }

  public boolean modern() {
    return modern;
  }

  public List<Swatch> entries() {
    return Collections.unmodifiableList(entries);
  }

  public void filter(List<String> allowed) {
    if (!allowed.isEmpty())
      entries.removeIf(s -> !allowed.contains(s.material.name()) && !allowed.contains(s.key()));
  }

  public void textures(File folder, int maxPixels) throws IOException {
    File[] files = folder.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file.getName().endsWith(".png")) {
        try (InputStream in = new FileInputStream(file)) {
          texture(file.getName(), HttpSource.read(in, 4194304), maxPixels);
        }
      } else if (file.getName().endsWith(".zip")) {
        if (file.length() > 67108864) throw new IOException("Resource pack trop grand.");
        try (ZipFile zip = new ZipFile(file)) {
          Enumeration<? extends ZipEntry> en = zip.entries();
          int count = 0;
          while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.getName().matches("assets/minecraft/textures/blocks?/[^/]+\\.png")) continue;
            if (++count > 4096) throw new IOException("Trop de textures.");
            try (InputStream in = zip.getInputStream(e)) {
              texture(
                  e.getName().substring(e.getName().lastIndexOf('/') + 1),
                  HttpSource.read(in, 4194304),
                  maxPixels);
            }
          }
        }
      }
    }
  }

  private void texture(String name, byte[] bytes, int maxPixels) throws IOException {
    String key = name.substring(0, name.length() - 4).toUpperCase(Locale.ROOT);
    // Side-specific entries can override a known material, e.g. oak_log_side.png.
    key = key.replaceFirst("_(SIDE|TOP|BOTTOM)$", "");
    Swatch existing = textureNames.get(key);
    Material m = existing == null ? Material.getMaterial(key) : existing.material;
    if (m == null
        || !m.isBlock()
        || !m.isSolid()
        || key.contains("SAND")
        || key.contains("GRAVEL")
        || key.contains("POWDER")
        || key.contains("ANVIL")
        || key.contains("TNT")
        || key.contains("COMMAND")
        || key.contains("SPAWNER")
        || key.contains("STRUCTURE")) return;
    Images.Animation a = new Images(maxPixels, maxPixels * 2, 1).decode(bytes, 0, maxPixels);
    BufferedImage image = a.frames.get(0);
    long r = 0, g = 0, b = 0, n = 0;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) {
        int c = image.getRGB(x, y);
        if ((c >>> 24) < 128) continue;
        r += c >> 16 & 255;
        g += c >> 8 & 255;
        b += c & 255;
        n++;
      }
    if (n == 0) return;
    int rgb = (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    int variant = existing == null ? 0 : existing.data;
    entries.removeIf(s -> s.material == m && s.data == variant);
    entries.add(new Swatch(m, variant, rgb));
  }

  public Swatch nearest(int rgb) {
    if (entries.isEmpty()) throw new IllegalStateException("Palette vide.");
    double[] lab = ColorMath.lab(rgb);
    double best = Double.MAX_VALUE;
    Swatch result = null;
    for (Swatch s : entries) {
      double d = ColorMath.distance(lab, s.lab);
      if (d < best) {
        best = d;
        result = s;
      }
    }
    return result;
  }

  public Swatch[] quantize(BufferedImage image, boolean transparent, boolean dither, int alpha) {
    int w = image.getWidth(), h = image.getHeight();
    Swatch[] result = new Swatch[w * h];
    Map<Integer, Swatch> cache = new HashMap<>();
    double[][] errors = new double[2][(w + 2) * 3];
    for (int y = 0; y < h; y++) {
      double[] row = errors[y % 2], next = errors[(y + 1) % 2];
      Arrays.fill(next, 0);
      for (int x = 0; x < w; x++) {
        int c = image.getRGB(x, y), a = c >>> 24;
        if (transparent && a < alpha) continue;
        int p = (x + 1) * 3;
        int r = ColorMath.clamp(((c >> 16 & 255) * a + 255 * (255 - a)) / 255.0 + row[p]);
        int g = ColorMath.clamp(((c >> 8 & 255) * a + 255 * (255 - a)) / 255.0 + row[p + 1]);
        int b = ColorMath.clamp(((c & 255) * a + 255 * (255 - a)) / 255.0 + row[p + 2]);
        int rgb = r << 16 | g << 8 | b;
        Swatch s = cache.get(rgb);
        if (s == null) {
          s = nearest(rgb);
          if (cache.size() < 65536) cache.put(rgb, s);
        }
        result[y * w + x] = s;
        if (dither) {
          double[] e = {r - (s.rgb >> 16 & 255), g - (s.rgb >> 8 & 255), b - (s.rgb & 255)};
          for (int k = 0; k < 3; k++) {
            row[p + 3 + k] += e[k] * 7 / 16;
            next[p - 3 + k] += e[k] * 3 / 16;
            next[p + k] += e[k] * 5 / 16;
            next[p + 3 + k] += e[k] / 16;
          }
        }
      }
    }
    return result;
  }
}
