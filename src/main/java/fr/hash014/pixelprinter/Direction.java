/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.util.Locale;

/** Image origin is the lower-left block; source y=0 is the top row. */
public enum Direction {
  SOUTH(1, 0, 0, 0, 1, 0),
  NORTH(-1, 0, 0, 0, 1, 0),
  EAST(0, 0, -1, 0, 1, 0),
  WEST(0, 0, 1, 0, 1, 0),
  FLATNORTHEAST(1, 0, 0, 0, 0, -1),
  FLATSOUTHEAST(1, 0, 0, 0, 0, 1),
  FLATNORTHWEST(-1, 0, 0, 0, 0, -1),
  FLATSOUTHWEST(-1, 0, 0, 0, 0, 1);
  public final int ux, uy, uz, vx, vy, vz;

  Direction(int a, int b, int c, int d, int e, int f) {
    ux = a;
    uy = b;
    uz = c;
    vx = d;
    vy = e;
    vz = f;
  }

  public int[] at(int x, int bottomY) {
    return new int[] {ux * x + vx * bottomY, uy * x + vy * bottomY, uz * x + vz * bottomY};
  }

  public boolean flat() {
    return vy == 0;
  }

  public static Direction parse(String text) {
    String s = text.toUpperCase(Locale.ROOT).replace("_", "").replace("-", "");
    if (s.startsWith("UP")) s = s.substring(2);
    try {
      return valueOf(s);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(
          "Direction: South, North, East, West, FlatNorthEast, FlatSouthEast, FlatNorthWest,"
              + " FlatSouthWest.");
    }
  }
}
