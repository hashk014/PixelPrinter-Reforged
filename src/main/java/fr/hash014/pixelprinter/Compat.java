/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.lang.reflect.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapView;

/** Capability checks instead of package-version/NMS assumptions. */
public final class Compat {
  private static final boolean MODERN = Material.getMaterial("WHITE_WOOL") != null;
  private static final Method SET_TYPE =
      find(Block.class, "setType", Material.class, boolean.class);

  private static Method find(Class<?> type, String name, Class<?>... args) {
    try {
      return type.getMethod(name, args);
    } catch (NoSuchMethodException ex) {
      return null;
    }
  }

  private Compat() {}

  /** Old servers cancel unloads; modern servers use the plugin chunk tickets. */
  public static void preventUnload(ChunkUnloadEvent event) {
    Method cancel = find(ChunkUnloadEvent.class, "setCancelled", boolean.class);
    if (cancel == null) return;
    try {
      cancel.invoke(event, true);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Chunk unload adapter unavailable", ex);
    }
  }

  public static void set(Block b, Palette.Swatch s) {
    try {
      if (SET_TYPE != null) {
        SET_TYPE.invoke(b, s.material, false);
        if (!MODERN)
          Block.class.getMethod("setData", byte.class, boolean.class).invoke(b, s.data, false);
      } else {
        int id = ((Number) Material.class.getMethod("getId").invoke(s.material)).intValue();
        Block.class
            .getMethod("setTypeIdAndData", int.class, byte.class, boolean.class)
            .invoke(b, id, s.data, false);
      }
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Block adapter unavailable", ex);
    }
  }

  public static int minHeight(World w) {
    try {
      return ((Number) World.class.getMethod("getMinHeight").invoke(w)).intValue();
    } catch (ReflectiveOperationException ignored) {
      return 0;
    }
  }

  public static int mapId(MapView view) {
    try {
      return ((Number) MapView.class.getMethod("getId").invoke(view)).intValue();
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static MapView map(int id) {
    try {
      return (MapView) Bukkit.class.getMethod("getMap", int.class).invoke(null, id);
    } catch (NoSuchMethodException old) {
      try {
        return (MapView) Bukkit.class.getMethod("getMap", short.class).invoke(null, (short) id);
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException(ex);
      }
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
  }

  public static ItemStack mapItem(MapView view) {
    Material type = Material.getMaterial("FILLED_MAP");
    if (type == null) type = Material.getMaterial("MAP");
    ItemStack item = new ItemStack(type, 1);
    int id = mapId(view);
    ItemMeta meta = item.getItemMeta();
    try {
      Class<?> mm = Class.forName("org.bukkit.inventory.meta.MapMeta");
      Method setView = find(mm, "setMapView", MapView.class);
      if (setView != null) setView.invoke(meta, view);
      else {
        Method setId = find(mm, "setMapId", int.class);
        if (setId != null) setId.invoke(meta, id);
        else {
          item.setDurability((short) id);
          return item;
        }
      }
      item.setItemMeta(meta);
    } catch (ClassNotFoundException old) {
      item.setDurability((short) id);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Map metadata unavailable", ex);
    }
    return item;
  }

  public static MapView mapView(ItemStack item) {
    try {
      Class<?> mm = Class.forName("org.bukkit.inventory.meta.MapMeta");
      Method view = find(mm, "getMapView");
      if (view != null) return (MapView) view.invoke(item.getItemMeta());
      Method id = find(mm, "getMapId");
      if (id != null) return map(((Number) id.invoke(item.getItemMeta())).intValue());
    } catch (ClassNotFoundException old) {
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return map(item.getDurability());
  }

  public static HangingPlaceEvent hangingEvent(
      Hanging frame, Player player, Block block, BlockFace face, ItemStack item) {
    try {
      Constructor<?> old = null;
      try {
        old =
            HangingPlaceEvent.class.getConstructor(
                Hanging.class, Player.class, Block.class, BlockFace.class);
      } catch (NoSuchMethodException ignored) {
      }
      if (old != null) return (HangingPlaceEvent) old.newInstance(frame, player, block, face);
      Class<?> slot = Class.forName("org.bukkit.inventory.EquipmentSlot");
      Object hand = slot.getField("HAND").get(null);
      return (HangingPlaceEvent)
          HangingPlaceEvent.class
              .getConstructor(
                  Hanging.class, Player.class, Block.class, BlockFace.class, slot, ItemStack.class)
              .newInstance(frame, player, block, face, hand, item);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("Hanging event adapter unavailable", ex);
    }
  }

  /** CraftBukkit 1.7 rotates a hanging entity around the wrong attachment coordinate. */
  public static void repairLegacyFrame(ItemFrame frame, Location backing) {
    if (!Bukkit.getBukkitVersion().startsWith("1.7.")) return;
    try {
      Object handle = frame.getClass().getMethod("getHandle").invoke(frame);
      Class<?> type = handle.getClass();
      int facing = type.getField("direction").getInt(handle);
      type.getField("x").setInt(handle, backing.getBlockX());
      type.getField("y").setInt(handle, backing.getBlockY());
      type.getField("z").setInt(handle, backing.getBlockZ());
      type.getMethod("setDirection", int.class).invoke(handle, facing);
    } catch (ReflectiveOperationException ex) {
      frame.remove();
      throw new IllegalStateException("Legacy frame attachment adapter unavailable", ex);
    }
  }
}
