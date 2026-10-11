/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.*;

/** Vanilla map rendering (also animated) with durable per-map assets. */
public final class MapService {
  public static final class Tile {
    public final String asset;
    public final Images.Animation animation;
    public final int x, y;

    Tile(String asset, Images.Animation animation, int x, int y) {
      this.asset = asset;
      this.animation = animation;
      this.x = x;
      this.y = y;
    }
  }

  private final PixelPrinterReforged plugin;
  private final File assets, registryFile;
  private final YamlConfiguration registry;
  private volatile long storedPixels;
  private final int maxStoredPixels;

  public MapService(PixelPrinterReforged plugin) {
    this.plugin = plugin;
    assets = new File(plugin.getDataFolder(), "maps");
    assets.mkdirs();
    registryFile = new File(assets, "registry.yml");
    registry = YamlConfiguration.loadConfiguration(registryFile);
    maxStoredPixels = plugin.getConfig().getInt("max-stored-map-pixels", 8388608);
    for (String id : registry.getKeys(false))
      storedPixels += (long) registry.getIntegerList(id + ".delays").size() * 16384;
  }

  public List<Tile> prepare(Images.Animation image) throws IOException {
    int cols = (image.width() + 127) / 128, rows = (image.height() + 127) / 128;
    if (cols * (long) rows > plugin.maxMapTiles()) throw new IOException("Trop de cartes.");
    if (storedPixels + (long) cols * rows * image.frames.size() * 16384 > maxStoredPixels)
      throw new IOException("Mémoire des cartes persistantes pleine (max-stored-map-pixels).");
    List<Tile> tiles = new ArrayList<>();
    String id = UUID.randomUUID().toString();
    File folder = new File(assets, id);
    if (!folder.mkdirs()) throw new IOException("Création du dossier cartes impossible.");
    for (int y = 0; y < rows; y++)
      for (int x = 0; x < cols; x++) {
        List<BufferedImage> frames = new ArrayList<>();
        for (int f = 0; f < image.frames.size(); f++) {
          BufferedImage frame = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = frame.createGraphics();
          g.setColor(java.awt.Color.WHITE);
          g.fillRect(0, 0, 128, 128);
          g.drawImage(image.frames.get(f), -x * 128, -y * 128, null);
          g.dispose();
          frames.add(frame);
          ImageIO.write(frame, "png", new File(folder, x + "-" + y + "-" + f + ".png"));
        }
        tiles.add(
            new Tile(
                id + "/" + x + "-" + y, new Images.Animation(frames, image.delays.clone()), x, y));
      }
    return tiles;
  }

  public List<ItemStack> create(World world, List<Tile> tiles) {
    long pixels = 0;
    for (Tile t : tiles) pixels += (long) t.animation.frames.size() * 16384;
    if (storedPixels + pixels > maxStoredPixels)
      throw new IllegalArgumentException("Mémoire des cartes persistantes pleine.");
    List<ItemStack> result = new ArrayList<>();
    for (Tile tile : tiles) {
      MapView view = Bukkit.createMap(world);
      attach(view, tile.animation);
      int id = Compat.mapId(view);
      registry.set(id + ".asset", tile.asset);
      List<Integer> delays = new ArrayList<>();
      for (int delay : tile.animation.delays) delays.add(delay);
      registry.set(id + ".delays", delays);
      result.add(Compat.mapItem(view));
    }
    storedPixels += pixels;
    save();
    return result;
  }

  private void attach(MapView view, Images.Animation image) {
    for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
    view.addRenderer(new AnimationRenderer(image));
  }

  public void restore() {
    restore(null);
  }

  public void restore(Runnable complete) {
    Deque<Runnable> loaders = new ArrayDeque<>();
    long restoredPixels = 0;
    for (String id : registry.getKeys(false)) {
      String asset = registry.getString(id + ".asset");
      List<Integer> times = registry.getIntegerList(id + ".delays");
      if (asset == null
          || !asset.matches("[a-f0-9-]{36}/[0-9]+-[0-9]+")
          || times.isEmpty()
          || times.size() > 128) {
        plugin.getLogger().warning("Entrée carte invalide: " + id);
        continue;
      }
      restoredPixels += (long) times.size() * 16384;
      if (restoredPixels > maxStoredPixels) {
        plugin
            .getLogger()
            .warning("Limite de mémoire des cartes atteinte pendant la restauration.");
        break;
      }
      loaders.add(
          () ->
              plugin.worker(
                  () -> {
                    List<BufferedImage> frames = new ArrayList<>();
                    for (int i = 0; i < times.size(); i++) {
                      File file = new File(assets, asset + "-" + i + ".png");
                      BufferedImage frame =
                          plugin
                              .images()
                              .decode(java.nio.file.Files.readAllBytes(file.toPath()), 0, 16384)
                              .frames
                              .get(0);
                      frames.add(frame);
                    }
                    int[] delays = times.stream().mapToInt(Integer::intValue).toArray();
                    return new Images.Animation(frames, delays);
                  },
                  animation -> {
                    MapView view = Compat.map(Integer.parseInt(id));
                    if (view != null) attach(view, animation);
                    restoreNext(loaders, complete);
                  },
                  error -> {
                    plugin
                        .getLogger()
                        .warning("Restauration carte " + id + ": " + error.getMessage());
                    restoreNext(loaders, complete);
                  }));
    }
    restoreNext(loaders, complete);
  }

  private void restoreNext(Deque<Runnable> loaders, Runnable complete) {
    Runnable next = loaders.pollFirst();
    if (next != null) next.run();
    else if (complete != null) complete.run();
  }

  private void save() {
    try {
      registry.save(registryFile);
    } catch (IOException ex) {
      plugin.getLogger().severe("Sauvegarde des cartes: " + ex.getMessage());
    }
  }

  public JobManager.Record wall(Player player, Location origin, Direction dir, List<Tile> tiles) {
    if (dir.flat())
      throw new IllegalArgumentException("Le mur de cartes exige South/North/East/West.");
    int rows = tiles.stream().mapToInt(t -> t.y).max().orElse(0) + 1;
    int nx = -dir.uz, nz = dir.ux;
    int[][] coords = new int[tiles.size() * 2][];
    Palette.Swatch[] values = new Palette.Swatch[coords.length];
    Palette.Swatch backing = new Palette.Swatch(Material.STONE, 0, 0x7D7D7D),
        air = new Palette.Swatch(Material.AIR, 0, 0);
    int index = 0;
    for (Tile tile : tiles) {
      int[] p = dir.at(tile.x, rows - 1 - tile.y);
      coords[index] = p;
      values[index++] = backing;
      coords[index] = new int[] {p[0] + nx, p[1], p[2] + nz};
      values[index++] = air;
      Location loc = origin.clone().add(p[0] + nx + .5, p[1] + .5, p[2] + nz + .5);
      for (Entity e : origin.getWorld().getEntities())
        if (e instanceof Hanging && e.getLocation().distanceSquared(loc) < 1.0)
          throw new IllegalArgumentException("Un cadre ou tableau occupe déjà le mur.");
    }
    JobManager.Record record =
        plugin
            .jobs()
            .submit(
                player.getUniqueId(),
                "mapwall",
                origin,
                new BuildPlan(
                    coords, Collections.singletonList(values), new int[] {Integer.MAX_VALUE}));
    record.onComplete =
        () -> {
          List<ItemStack> stacks = create(origin.getWorld(), tiles);
          org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.valueOf(dir.name());
          for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            int[] p = dir.at(tile.x, rows - 1 - tile.y);
            Location loc = origin.clone().add(p[0] + nx, p[1], p[2] + nz);
            if (Bukkit.getBukkitVersion().startsWith("1.7."))
              loc = origin.clone().add(p[0], p[1], p[2]);
            ItemFrame frame = (ItemFrame) origin.getWorld().spawnEntity(loc, EntityType.ITEM_FRAME);
            if (frame.getFacing() != face && !frame.setFacingDirection(face, true)) {
              frame.remove();
              throw new IllegalStateException("Orientation du cadre refusée.");
            }
            Compat.repairLegacyFrame(frame, origin.clone().add(p[0], p[1], p[2]));
            record.entities.put(frame.getUniqueId(), frame.getLocation());
            org.bukkit.event.hanging.HangingPlaceEvent event =
                Compat.hangingEvent(
                    frame,
                    player,
                    origin.clone().add(p[0], p[1], p[2]).getBlock(),
                    face,
                    stacks.get(i));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
              frame.remove();
              throw new IllegalStateException("Cadre refusé par la protection de zone.");
            }
            frame.setItem(stacks.get(i));
          }
        };
    return record;
  }

  private static final class AnimationRenderer extends MapRenderer {
    private final Images.Animation animation;
    private final long start = System.nanoTime();
    private int last = -1;

    AnimationRenderer(Images.Animation image) {
      super(false);
      animation = image;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
      int index = 0;
      if (animation.frames.size() > 1) {
        long total = 0;
        for (int delay : animation.delays) total += Math.max(1, delay);
        long time = ((System.nanoTime() - start) / 50000000L) % total;
        while (index < animation.delays.length - 1 && time >= animation.delays[index])
          time -= animation.delays[index++];
      }
      if (index != last) {
        canvas.drawImage(0, 0, animation.frames.get(index));
        last = index;
      }
    }
  }
}
