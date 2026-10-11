/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * Durable original states for GIFs; tile entities are deliberately excluded from GIF footprints.
 */
final class OriginalBlock {
  private OriginalBlock() {}

  static boolean tile(BlockState state) {
    try {
      return Class.forName("org.bukkit.block.TileState").isInstance(state);
    } catch (ClassNotFoundException old) {
      return !state.getClass().getSimpleName().equals("CraftBlockState");
    }
  }

  static Map<String, Object> capture(BlockState state) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("type", state.getType().name());
    try {
      Class<?> bd = Class.forName("org.bukkit.block.data.BlockData");
      Object blockData = BlockState.class.getMethod("getBlockData").invoke(state);
      data.put("blockData", bd.getMethod("getAsString").invoke(blockData));
    } catch (ClassNotFoundException | NoSuchMethodException old) {
      data.put("legacyData", (int) state.getRawData());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return data;
  }

  static BlockState load(Block current, Map<String, Object> data) {
    BlockState state = current.getState();
    Material type = Material.getMaterial(String.valueOf(data.get("type")));
    if (type == null) throw new IllegalArgumentException("Matériau disparu: " + data.get("type"));
    state.setType(type);
    try {
      if (data.containsKey("blockData")) {
        Class<?> bd = Class.forName("org.bukkit.block.data.BlockData");
        Object value =
            org.bukkit.Bukkit.class
                .getMethod("createBlockData", String.class)
                .invoke(null, data.get("blockData"));
        BlockState.class.getMethod("setBlockData", bd).invoke(state, value);
      } else state.setRawData(((Number) data.get("legacyData")).byteValue());
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException(ex);
    }
    return state;
  }
}
