/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minecraft skin UV layout, six faces per cuboid, outer clothing/hat layers composited. */
public final class Skins {
  public static final class Skin {
    public final BufferedImage image, cape;
    public final boolean slim;

    Skin(BufferedImage i, boolean s) {
      this(i, s, null);
    }

    Skin(BufferedImage i, boolean s, BufferedImage cape) {
      image = i;
      slim = s;
      this.cape = cape;
    }
  }

  private Skins() {}

  public static Skin fetch(String player, HttpSource source, Images images) throws IOException {
    String uuid = player.replace("-", "");
    if (!uuid.matches("[a-fA-F0-9]{32}")) {
      if (!player.matches("[A-Za-z0-9_]{1,16}"))
        throw new IOException("Nom/UUID de joueur invalide.");
      JsonObject profile =
          JsonParser.parseString(
                  new String(
                      source.download("https://api.mojang.com/users/profiles/minecraft/" + player),
                      StandardCharsets.UTF_8))
              .getAsJsonObject();
      uuid = profile.get("id").getAsString();
    }
    JsonObject profile =
        JsonParser.parseString(
                new String(
                    source.download(
                        "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid),
                    StandardCharsets.UTF_8))
            .getAsJsonObject();
    for (JsonElement entry : profile.getAsJsonArray("properties")) {
      JsonObject property = entry.getAsJsonObject();
      if (!property.get("name").getAsString().equals("textures")) continue;
      JsonObject textures =
          JsonParser.parseString(
                  new String(
                      Base64.getDecoder().decode(property.get("value").getAsString()),
                      StandardCharsets.UTF_8))
              .getAsJsonObject()
              .getAsJsonObject("textures");
      JsonObject texture = textures.getAsJsonObject("SKIN");
      String url = texture.get("url").getAsString();
      if (!new java.net.URL(url).getHost().equals("textures.minecraft.net"))
        throw new IOException("Domaine de skin inattendu.");
      BufferedImage image =
          images.decode(source.download(url.replace("http://", "https://")), 0, 4096).frames.get(0);
      boolean slim =
          texture.has("metadata")
              && texture.getAsJsonObject("metadata").has("model")
              && texture.getAsJsonObject("metadata").get("model").getAsString().equals("slim");
      BufferedImage cape = null;
      if (textures.has("CAPE")) {
        String capeUrl = textures.getAsJsonObject("CAPE").get("url").getAsString();
        if (!new java.net.URL(capeUrl).getHost().equals("textures.minecraft.net"))
          throw new IOException("Domaine de cape inattendu.");
        cape =
            images
                .decode(source.download(capeUrl.replace("http://", "https://")), 0, 4096)
                .frames
                .get(0);
      }
      return new Skin(image, slim, cape);
    }
    throw new IOException("Texture de skin absente.");
  }

  public static BuildPlan statue(Skin skin, Direction direction, Palette palette, int scale) {
    BufferedImage image = skin.image;
    if (image.getWidth() != 64 || (image.getHeight() != 32 && image.getHeight() != 64))
      throw new IllegalArgumentException("Skin attendu: 64x32 ou 64x64.");
    if (direction.flat())
      throw new IllegalArgumentException("La statue exige une direction verticale.");
    Map<String, int[]> coordinates = new LinkedHashMap<>();
    Map<String, Palette.Swatch> colors = new LinkedHashMap<>();
    // UV origin is the top-left of the whole unfolded box, in the standard skin atlas.
    box(
        image,
        palette,
        coordinates,
        colors,
        -4,
        0,
        -2,
        4,
        12,
        4,
        0,
        16,
        image.getHeight() == 64 ? 0 : -1,
        32,
        false,
        scale);
    box(
        image,
        palette,
        coordinates,
        colors,
        0,
        0,
        -2,
        4,
        12,
        4,
        image.getHeight() == 64 ? 16 : 0,
        image.getHeight() == 64 ? 48 : 16,
        image.getHeight() == 64 ? 0 : -1,
        48,
        image.getHeight() == 32,
        scale);
    box(
        image,
        palette,
        coordinates,
        colors,
        -4,
        12,
        -2,
        8,
        12,
        4,
        16,
        16,
        image.getHeight() == 64 ? 16 : -1,
        32,
        false,
        scale);
    int arm = skin.slim ? 3 : 4;
    box(
        image,
        palette,
        coordinates,
        colors,
        -4 - arm,
        12,
        -2,
        arm,
        12,
        4,
        40,
        16,
        image.getHeight() == 64 ? 40 : -1,
        32,
        false,
        scale);
    box(
        image,
        palette,
        coordinates,
        colors,
        4,
        12,
        -2,
        arm,
        12,
        4,
        image.getHeight() == 64 ? 32 : 40,
        image.getHeight() == 64 ? 48 : 16,
        image.getHeight() == 64 ? 48 : -1,
        48,
        image.getHeight() == 32,
        scale);
    box(image, palette, coordinates, colors, -4, 24, -4, 8, 8, 8, 0, 0, 32, 0, false, scale);
    if (skin.cape != null && skin.cape.getWidth() >= 22 && skin.cape.getHeight() >= 17)
      box(skin.cape, palette, coordinates, colors, -5, 8, -4, 10, 16, 1, 0, 0, -1, 0, false, scale);
    int[][] positions = new int[coordinates.size()][];
    Palette.Swatch[] swatches = new Palette.Swatch[positions.length];
    int i = 0;
    for (String key : coordinates.keySet()) {
      int[] p = coordinates.get(key);
      positions[i] =
          new int[] {
            direction.ux * p[0] - direction.uz * p[2],
            p[1],
            direction.uz * p[0] + direction.ux * p[2]
          };
      swatches[i++] = colors.get(key);
    }
    return new BuildPlan(
        positions, Collections.singletonList(swatches), new int[] {Integer.MAX_VALUE});
  }

  private static void box(
      BufferedImage skin,
      Palette palette,
      Map<String, int[]> positions,
      Map<String, Palette.Swatch> colors,
      int x,
      int y,
      int z,
      int w,
      int h,
      int d,
      int u,
      int v,
      int ou,
      int ov,
      boolean mirrored,
      int scale) {
    face(
        skin,
        palette,
        positions,
        colors,
        x,
        y,
        z + d - 1,
        w,
        h,
        1,
        0,
        0,
        0,
        1,
        0,
        u + d,
        v + d,
        ou < 0 ? -1 : ou + d,
        ov + d,
        mirrored,
        scale); // front
    face(
        skin,
        palette,
        positions,
        colors,
        x + w - 1,
        y,
        z,
        w,
        h,
        -1,
        0,
        0,
        0,
        1,
        0,
        u + 2 * d + w,
        v + d,
        ou < 0 ? -1 : ou + 2 * d + w,
        ov + d,
        mirrored,
        scale);
    face(
        skin, palette, positions, colors, x, y, z, d, h, 0, 0, 1, 0, 1, 0, u, v + d, ou, ov + d,
        mirrored, scale);
    face(
        skin,
        palette,
        positions,
        colors,
        x + w - 1,
        y,
        z + d - 1,
        d,
        h,
        0,
        0,
        -1,
        0,
        1,
        0,
        u + d + w,
        v + d,
        ou < 0 ? -1 : ou + d + w,
        ov + d,
        mirrored,
        scale);
    face(
        skin,
        palette,
        positions,
        colors,
        x,
        y + h - 1,
        z,
        w,
        d,
        1,
        0,
        0,
        0,
        0,
        1,
        u + d,
        v,
        ou < 0 ? -1 : ou + d,
        ov,
        mirrored,
        scale);
    face(
        skin,
        palette,
        positions,
        colors,
        x,
        y,
        z + d - 1,
        w,
        d,
        1,
        0,
        0,
        0,
        0,
        -1,
        u + d + w,
        v,
        ou < 0 ? -1 : ou + d + w,
        ov,
        mirrored,
        scale);
  }

  private static void face(
      BufferedImage skin,
      Palette palette,
      Map<String, int[]> positions,
      Map<String, Palette.Swatch> colors,
      int x,
      int y,
      int z,
      int w,
      int h,
      int ax,
      int ay,
      int az,
      int bx,
      int by,
      int bz,
      int u,
      int v,
      int ou,
      int ov,
      boolean mirrored,
      int scale) {
    for (int row = 0; row < h; row++)
      for (int col = 0; col < w; col++) {
        int xx = mirrored ? w - 1 - col : col;
        int rgb = skin.getRGB(u + xx, v + row);
        if (ou >= 0) {
          int over = skin.getRGB(ou + xx, ov + row), alpha = over >>> 24;
          if (alpha > 0) {
            int r = ((over >> 16 & 255) * alpha + (rgb >> 16 & 255) * (255 - alpha)) / 255,
                g = ((over >> 8 & 255) * alpha + (rgb >> 8 & 255) * (255 - alpha)) / 255,
                b = ((over & 255) * alpha + (rgb & 255) * (255 - alpha)) / 255;
            rgb = 0xff000000 | r << 16 | g << 8 | b;
          }
        }
        Palette.Swatch swatch = palette.nearest(rgb);
        for (int sz = 0; sz < scale; sz++)
          for (int sy = 0; sy < scale; sy++)
            for (int sx = 0; sx < scale; sx++) {
              int px = (x + ax * col + bx * (h - 1 - row)) * scale + sx,
                  py = (y + ay * col + by * (h - 1 - row)) * scale + sy,
                  pz = (z + az * col + bz * (h - 1 - row)) * scale + sz;
              String key = px + "," + py + "," + pz;
              positions.put(key, new int[] {px, py, pz});
              colors.put(key, swatch);
            }
      }
  }
}
