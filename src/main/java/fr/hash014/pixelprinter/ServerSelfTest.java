/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitRunnable;

/** Only enabled by -Dpixelprinter.reforged.test=true on disposable test servers. */
public final class ServerSelfTest {
  private final PixelPrinterReforged plugin;
  private CommandSender sender;
  private int checks;

  ServerSelfTest(PixelPrinterReforged plugin) {
    this.plugin = plugin;
  }

  private void check(boolean condition, String name) {
    if (!condition) throw new IllegalStateException("SELFTEST FAIL: " + name);
    checks++;
    plugin.getLogger().info("SELFTEST CHECK " + name);
  }

  public void run(CommandSender sender) {
    if (!Boolean.getBoolean("pixelprinter.reforged.test"))
      throw new IllegalArgumentException(
          "Activer -Dpixelprinter.reforged.test=true uniquement sur un serveur jetable.");
    this.sender = sender;
    World world = Bukkit.getWorlds().get(0);
    Location origin = new Location(world, 0, Math.min(world.getMaxHeight() - 40, 120), 0);
    final Palette palette = plugin.palette();
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 4; y++)
      for (int x = 0; x < 4; x++) image.setRGB(x, y, ((x + y) % 2 == 0 ? 0xffff0000 : 0xff00ff00));
    image.setRGB(3, 3, 0);
    Images.Animation animation =
        new Images.Animation(Collections.singletonList(image), new int[] {1});
    List<BlockState> original = new ArrayList<>();
    for (int y = 0; y < 40; y++)
      for (int x = -12; x < 16; x++)
        for (int z = -12; z < 8; z++)
          original.add(world.getBlockAt(x, origin.getBlockY() + y, z).getState());
    Compat.set(origin.getBlock(), new Palette.Swatch(Material.CHEST, 0, 0));
    ((org.bukkit.block.Chest) origin.getBlock().getState())
        .getBlockInventory()
        .setItem(0, new ItemStack(Material.DIAMOND, 3));
    JobManager.Record record =
        plugin
            .jobs()
            .submit(
                null,
                "selftest-static",
                origin,
                BuildPlan.image(animation, Direction.SOUTH, palette, true, false, 128));
    new BukkitRunnable() {
      int stage = 0, elapsed = 0;
      JobManager.Record undo, gif;
      Material first;
      long pausedHash;
      MapService service;
      List<MapService.Tile> tiles;
      List<ItemStack> items;

      @Override
      public void run() {
        try {
          if (++elapsed > 1200) throw new IllegalStateException("SELFTEST timeout stage " + stage);
          if (stage == 0 && record.done) {
            check(record.failure == null, "static-placement");
            check(record.originals.size() == 15, "transparent-skip");
            Palette.Swatch red = palette.nearest(0xff0000);
            Block b = world.getBlockAt(0, origin.getBlockY() + 3, 0);
            check(b.getType() == red.material, "color-material");
            if (!palette.modern())
              check(
                  ((Number) Block.class.getMethod("getData").invoke(b)).byteValue() == red.data,
                  "legacy-data");
            undo = plugin.jobs().undo(null, record.id, true);
            stage = 1;
          } else if (stage == 1 && undo.done) {
            check(undo.originals.isEmpty(), "undo-releases-memory");
            check(
                world.getBlockAt(0, origin.getBlockY() + 3, 0).getType()
                    == find(original, 0, origin.getBlockY() + 3, 0).getType(),
                "undo-restores-block");
            check(
                origin.getBlock().getType() == Material.CHEST
                    && ((org.bukkit.block.Chest) origin.getBlock().getState())
                            .getBlockInventory()
                            .getItem(0)
                            .getAmount()
                        == 3,
                "undo-chest-inventory");
            // Remove the test chest before the GIF footprint; GIFs never overwrite tile entities.
            Compat.set(origin.getBlock(), new Palette.Swatch(Material.AIR, 0, 0));
            BufferedImage blue = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) blue.setRGB(x, y, 0xff0000ff);
            gif =
                plugin
                    .jobs()
                    .submit(
                        null,
                        "selftest-gif",
                        origin,
                        BuildPlan.image(
                            new Images.Animation(Arrays.asList(image, blue), new int[] {2, 2}),
                            Direction.SOUTH,
                            palette,
                            true,
                            false,
                            128));
            stage = 2;
            elapsed = 0;
          } else if (stage == 2 && elapsed > 20) {
            check(gif.failure == null && !gif.done && gif.originals.size() == 16, "gif-running");
            first = world.getBlockAt(0, origin.getBlockY() + 3, 0).getType();
            gif.paused = true;
            pausedHash = hash(world, origin);
            stage = 3;
            elapsed = 0;
          } else if (stage == 3 && elapsed > 5) {
            check(hash(world, origin) == pausedHash, "gif-pause");
            gif.paused = false;
            stage = 4;
            elapsed = 0;
          } else if (stage == 4 && elapsed > 15) {
            check(!gif.done && gif.cursor < gif.plan.positions.length, "gif-resume");
            undo = plugin.jobs().undo(null, gif.id, true);
            stage = 5;
          } else if (stage == 5 && undo.done) {
            check(plugin.jobs().reserved() == 0, "global-memory-budget");
            for (Direction d : Direction.values()) {
              int[] pos = d.at(3, 4);
              check(
                  pos[0] * pos[0] + pos[1] * pos[1] + pos[2] * pos[2] == 25,
                  "direction-" + d.name());
            }
            BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) skin.setRGB(x, y, 0xff55aaff);
            BuildPlan statue =
                Skins.statue(new Skins.Skin(skin, false), Direction.SOUTH, palette, 1);
            check(statue.positions.length > 600, "skin-3d-plan");
            record.onComplete = null;
            JobManager.Record statueJob =
                plugin.jobs().submit(null, "selftest-statue", origin, statue);
            undo = statueJob;
            stage = 6;
            elapsed = 0;
          } else if (stage == 6 && undo.done) {
            check(undo.failure == null && undo.originals.size() > 600, "skin-world-placement");
            undo = plugin.jobs().undo(null, undo.id, true);
            stage = 7;
            elapsed = 0;
          } else if (stage == 7 && undo.done) {
            service = new MapService(plugin);
            plugin.worker(
                () ->
                    service.prepare(
                        new Images.Animation(
                            Arrays.asList(
                                Images.resize(image, 256, 128), Images.resize(image, 256, 128)),
                            new int[] {2, 5})),
                prepared -> {
                  tiles = prepared;
                  stage = 8;
                },
                error -> {
                  plugin.getLogger().severe("SELFTEST FAIL map-prepare: " + error);
                  cancel();
                });
            stage = 70;
          } else if (stage == 8) {
            items = service.create(world, tiles);
            check(items.size() == 2, "map-tiling");
            check(
                items.get(0).getType()
                    == (palette.modern()
                        ? Material.getMaterial("FILLED_MAP")
                        : Material.getMaterial("MAP")),
                "map-item-type");
            // Round-trip through Bukkit item serialization and the live CraftMapView API.
            ItemStack round = ItemStack.deserialize(items.get(0).serialize());
            check(round.isSimilar(items.get(0)), "map-item-serialization");
            MapView view = Compat.mapView(items.get(0));
            check(view != null && view.getRenderers().size() == 1, "live-map-renderer");
            check(Compat.map(Compat.mapId(view)) != null, "map-id-roundtrip");
            service.restore();
            BlockState baseline = origin.getBlock().getState();
            JobManager.Record offline =
                plugin
                    .jobs()
                    .submit(
                        UUID.randomUUID(),
                        "selftest-offline-owner",
                        origin,
                        new BuildPlan(
                            new int[][] {{0, 0, 0}},
                            Collections.singletonList(
                                new Palette.Swatch[] {palette.nearest(0xff0000)}),
                            new int[] {1}));
            offline.originals.put(0, baseline);
            Compat.set(origin.getBlock(), new Palette.Swatch(Material.STONE, 0, 0));
            undo = plugin.jobs().undo(null, offline.id, true);
            stage = 9;
            elapsed = 0;
          } else if (stage == 9 && elapsed > 10 && undo.done) {
            check(origin.getBlock().getType() == Material.AIR, "admin-undo-offline-owner");
            check(plugin.jobs().reserved() == 0, "final-memory-clean");
            plugin
                .getLogger()
                .info("SELFTEST PASS checks=" + checks + " minecraft=" + Bukkit.getBukkitVersion());
            cancel();
          }
        } catch (Throwable ex) {
          plugin.getLogger().severe("SELFTEST FAIL stage=" + stage + " " + ex);
          ex.printStackTrace();
          cancel();
        }
      }

      @Override
      public void cancel() {
        super.cancel();
        for (BlockState state : original) state.update(true, false);
        plugin.jobs().clear(null);
      }
    }.runTaskTimer(plugin, 1, 1);
  }

  private static BlockState find(List<BlockState> list, int x, int y, int z) {
    for (BlockState state : list)
      if (state.getX() == x && state.getY() == y && state.getZ() == z) return state;
    throw new IllegalStateException();
  }

  private static long hash(World world, Location origin) {
    long hash = 1;
    for (int y = 0; y < 4; y++)
      for (int x = 0; x < 4; x++)
        hash =
            hash * 31 + world.getBlockAt(x, origin.getBlockY() + y, 0).getType().name().hashCode();
    return hash;
  }

  public void persistenceFixture() throws Exception {
    if (!Boolean.getBoolean("pixelprinter.reforged.test"))
      throw new IllegalArgumentException("Test only");
    World world = Bukkit.getWorlds().get(0);
    Location origin = new Location(world, 40, 120, 40);
    Compat.set(origin.getBlock(), new Palette.Swatch(Material.STONE, 0, 0));
    BufferedImage red = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
        blue = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    red.setRGB(0, 0, 0xffff0000);
    blue.setRGB(0, 0, 0xff0000ff);
    File file = new File(plugin.getDataFolder(), "images/ppr-persist.png");
    javax.imageio.ImageIO.write(red, "png", file);
    // Use an actual GIF file so the exact animation is decoded again after reboot.
    javax.imageio.ImageWriter writer =
        javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
    File gifFile = new File(plugin.getDataFolder(), "images/ppr-persist.gif");
    try (javax.imageio.stream.ImageOutputStream out =
        javax.imageio.ImageIO.createImageOutputStream(gifFile)) {
      writer.setOutput(out);
      writer.prepareWriteSequence(null);
      writer.writeToSequence(new javax.imageio.IIOImage(red, null, null), null);
      writer.writeToSequence(new javax.imageio.IIOImage(blue, null, null), null);
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    Images.Animation animation =
        plugin.images().decode(java.nio.file.Files.readAllBytes(gifFile.toPath()), 1, 1);
    JobManager.Record r =
        plugin
            .jobs()
            .submit(
                null,
                "ppr-persist.gif",
                origin,
                BuildPlan.image(animation, Direction.SOUTH, plugin.palette(), true, false, 128));
    r.descriptor = new LinkedHashMap<>();
    r.descriptor.put("direction", "SOUTH");
    r.descriptor.put("height", 1);
    r.descriptor.put("transparent", true);
    r.descriptor.put("dither", false);
    new BukkitRunnable() {
      @Override
      public void run() {
        if (r.savedFirstFrame) {
          plugin.getLogger().info("PERSISTENCE FIXTURE READY");
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1, 1);
  }

  public void protocolInfo() throws Exception {
    if (!Boolean.getBoolean("pixelprinter.reforged.test"))
      throw new IllegalArgumentException("Test only");
    Map<String, Object> data = new LinkedHashMap<>();
    Class<?> provider = Class.forName("net.minecraft.network.ProtocolInfo$DetailsProvider"),
        detailClass = Class.forName("net.minecraft.network.ProtocolInfo$Details"),
        visitorClass = Class.forName("net.minecraft.network.ProtocolInfo$Details$PacketVisitor"),
        packetClass = Class.forName("net.minecraft.network.protocol.PacketType");
    for (String state : Arrays.asList("login", "configuration", "game")) {
      String className =
          state.equals("game") ? "Game" : state.equals("login") ? "Login" : "Configuration";
      Class<?> protocols =
          Class.forName("net.minecraft.network.protocol." + state + "." + className + "Protocols");
      for (String flow : Arrays.asList("CLIENTBOUND", "SERVERBOUND")) {
        Object template = protocols.getField(flow + "_TEMPLATE").get(null),
            details = provider.getMethod("details").invoke(template);
        Map<String, Integer> ids = new LinkedHashMap<>();
        Object visitor =
            Proxy.newProxyInstance(
                visitorClass.getClassLoader(),
                new Class<?>[] {visitorClass},
                (proxy, method, args) -> {
                  if (method.getName().equals("accept"))
                    ids.put(
                        packetClass.getMethod("id").invoke(args[0]).toString(), (Integer) args[1]);
                  return null;
                });
        detailClass.getMethod("listPackets", visitorClass).invoke(details, visitor);
        data.put(state + "_" + flow.toLowerCase(java.util.Locale.ROOT), ids);
      }
    }
    java.nio.file.Files.write(
        new File(plugin.getDataFolder(), "protocol.json").toPath(),
        new com.google.gson.Gson().toJson(data).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    plugin.getLogger().info("PROTOCOL INFO READY");
  }

  public void persistenceCheck() {
    if (!Boolean.getBoolean("pixelprinter.reforged.test"))
      throw new IllegalArgumentException("Test only");
    JobManager.Record r = null;
    for (JobManager.Record candidate : plugin.jobs().active())
      if (candidate.source.equals("ppr-persist.gif")) r = candidate;
    if (r == null) throw new IllegalStateException("PERSISTENCE FAIL: GIF missing");
    check(
        r.originals.size() == 1
            && r.originals.values().iterator().next().getType() == Material.STONE,
        "persisted-original-state");
    JobManager.Record undo = plugin.jobs().undo(null, r.id, true);
    new BukkitRunnable() {
      @Override
      public void run() {
        if (undo.done) {
          if (undo.origin.getBlock().getType() == Material.STONE)
            plugin.getLogger().info("PERSISTENCE PASS");
          else plugin.getLogger().severe("PERSISTENCE FAIL original block");
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1, 1);
  }
}
