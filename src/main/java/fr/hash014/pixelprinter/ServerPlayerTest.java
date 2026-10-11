/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.scheduler.BukkitRunnable;

/** Functional command tests against a real connected player, on disposable servers only. */
public final class ServerPlayerTest {
  private final PixelPrinterReforged plugin;
  private int checks;

  ServerPlayerTest(PixelPrinterReforged plugin) {
    this.plugin = plugin;
  }

  private void check(boolean value, String name) {
    if (!value) throw new IllegalStateException(name);
    checks++;
    plugin.getLogger().info("PLAYERTEST CHECK " + name);
  }

  public void run(Player player) throws Exception {
    if (!Boolean.getBoolean("pixelprinter.reforged.test"))
      throw new IllegalArgumentException("Serveur jetable uniquement.");
    World world = player.getWorld();
    int y = Math.min(world.getMaxHeight() - 40, 120);
    player.teleport(new Location(world, 0, y, 8));
    player.setGameMode(GameMode.CREATIVE);
    // The protocol probe is not a graphical client. Keep the inspected region
    // loaded so legacy hanging physics actually runs for the full waiting period.
    Listener testChunks =
        new Listener() {
          @EventHandler
          public void unload(ChunkUnloadEvent event) {
            Chunk chunk = event.getChunk();
            if (chunk.getWorld().equals(world)
                && chunk.getX() >= -1
                && chunk.getX() <= 2
                && chunk.getZ() >= -1
                && chunk.getZ() <= 0) Compat.preventUnload(event);
          }
        };
    Bukkit.getPluginManager().registerEvents(testChunks, plugin);
    for (Entity entity : world.getEntities()) if (entity instanceof Hanging) entity.remove();
    File file = new File(plugin.getDataFolder(), "images/ppr-test.png");
    BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
    for (int yy = 0; yy < 4; yy++) for (int x = 0; x < 4; x++) image.setRGB(x, yy, 0xffdd4433);
    ImageIO.write(image, "png", file);
    File skinFile = new File(plugin.getDataFolder(), "images/ppr-skin.png");
    BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int yy = 0; yy < 64; yy++) for (int x = 0; x < 64; x++) skin.setRGB(x, yy, 0xff4488cc);
    ImageIO.write(skin, "png", skinFile);
    plugin.jobs().clear(player.getUniqueId());
    player.setOp(false);
    player.performCommand("pp create South ppr-test.png 4");
    new BukkitRunnable() {
      int stage = -2, ticks = 0;
      Integer id;
      int eventCount;
      Set<UUID> frames = new HashSet<>();
      Direction[] directions = {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
      int wallIndex = 0;
      Listener blocker;

      @Override
      public void run() {
        try {
          if (++ticks > 1600) throw new IllegalStateException("timeout stage=" + stage);
          if (!player.isOnline()) throw new IllegalStateException("client disconnected");
          List<Integer> history = plugin.jobs().history(player.getUniqueId());
          if (stage == -2 && ticks > 10) {
            check(plugin.jobs().active().isEmpty() && history.isEmpty(), "permission-denied");
            player.setOp(true);
            stage = 0;
          } else if (stage == 0) {
            player.performCommand("pp create South ppr-test.png 4 true true");
            stage = 1;
          } else if (stage == 1 && !history.isEmpty()) {
            check(
                world.getBlockAt(0, y + 3, 8).getType()
                    == plugin.palette().nearest(0xdd4433).material,
                "command-create-world");
            id = history.get(0);
            player.performCommand("pp undo " + id);
            stage = 2;
          } else if (stage == 2 && plugin.jobs().active().isEmpty() && history.isEmpty()) {
            check(
                plugin.jobs().reserved() == 0, "command-undo reserved=" + plugin.jobs().reserved());
            blocker =
                new Listener() {
                  @EventHandler
                  public void place(BlockPlaceEvent event) {
                    if (event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
                      eventCount++;
                      event.setCancelled(true);
                    }
                  }
                };
            Bukkit.getPluginManager().registerEvents(blocker, plugin);
            player.performCommand("pp create South ppr-test.png 4");
            stage = 3;
          } else if (stage == 3 && !history.isEmpty()) {
            HandlerList.unregisterAll(blocker);
            check(eventCount > 0, "protection-event-fired");
            id = history.get(0);
            player.performCommand("pp undo " + id);
            stage = 4;
          } else if (stage == 4 && plugin.jobs().active().isEmpty() && history.isEmpty()) {
            check(plugin.jobs().reserved() == 0, "protected-build-clean");
            stage = 5;
          } else if (stage == 5) {
            if (wallIndex == directions.length) {
              player.performCommand("pp map ppr-test.png 1");
              stage = 8;
              ticks = 0;
            } else {
              player.teleport(new Location(world, wallIndex * 6, y, 0));
              player.performCommand("pp mapwall " + directions[wallIndex] + " ppr-test.png 1");
              stage = 6;
              ticks = 0;
            }
          } else if (stage == 6
              && !history.isEmpty()
              && ticks > (Bukkit.getBukkitVersion().startsWith("1.7.") ? 125 : 10)) {
            ItemFrame found = null;
            for (Entity e : world.getEntities())
              if (e instanceof ItemFrame && !frames.contains(e.getUniqueId()))
                found = (ItemFrame) e;
            check(
                found != null
                    && found.getItem().getType()
                        == (plugin.palette().modern()
                            ? Material.getMaterial("FILLED_MAP")
                            : Material.getMaterial("MAP")),
                "mapwall-"
                    + directions[wallIndex]
                    + " found="
                    + (found == null ? "none" : found.getItem().getType()));
            frames.add(found.getUniqueId());
            check(
                found.getFacing().name().equals(directions[wallIndex].name()),
                "frame-facing-" + directions[wallIndex]);
            player.performCommand("pp undo " + history.get(0));
            stage = 7;
          } else if (stage == 7 && plugin.jobs().active().isEmpty() && history.isEmpty()) {
            boolean left = false;
            for (Entity entity : world.getEntities())
              if (entity.isValid() && frames.contains(entity.getUniqueId())) left = true;
            check(!left, "mapwall-undo-" + directions[wallIndex]);
            wallIndex++;
            stage = 5;
          } else if (stage == 8 && ticks > 20) {
            Material type =
                plugin.palette().modern()
                    ? Material.getMaterial("FILLED_MAP")
                    : Material.getMaterial("MAP");
            check(player.getInventory().contains(type), "map-command-inventory");
            player.performCommand("pp createSkin South ppr-skin.png 1");
            stage = 9;
          } else if (stage == 9 && !history.isEmpty()) {
            check(
                world.getBlockAt(player.getLocation().getBlockX(), y + 31, 0).getType()
                    != Material.AIR,
                "skin-command-world");
            player.performCommand("pp undo " + history.get(0));
            stage = 10;
          } else if (stage == 10 && plugin.jobs().active().isEmpty() && history.isEmpty()) {
            check(plugin.jobs().reserved() == 0, "player-final-memory");
            plugin
                .getLogger()
                .info(
                    "PLAYERTEST PASS checks=" + checks + " minecraft=" + Bukkit.getBukkitVersion());
            HandlerList.unregisterAll(testChunks);
            cancel();
          }
        } catch (Throwable ex) {
          plugin.getLogger().severe("PLAYERTEST FAIL stage=" + stage + " " + ex);
          ex.printStackTrace();
          if (blocker != null) HandlerList.unregisterAll(blocker);
          HandlerList.unregisterAll(testChunks);
          cancel();
        }
      }
    }.runTaskTimer(plugin, 1, 1);
  }
}
