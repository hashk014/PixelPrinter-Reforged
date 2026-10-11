/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.awt.image.BufferedImage;
import java.util.*;

/** Immutable CPU-side plan. All positions are relative to its origin. */
public final class BuildPlan {
  public final int[][] positions;
  public final List<Palette.Swatch[]> frames;
  public final int[] delays;

  public BuildPlan(int[][] positions, List<Palette.Swatch[]> frames, int[] delays) {
    this.positions = positions;
    this.frames = frames;
    this.delays = delays;
  }

  public static BuildPlan image(
      Images.Animation image,
      Direction dir,
      Palette palette,
      boolean transparent,
      boolean dither,
      int alpha) {
    int w = image.width(), h = image.height();
    int[][] positions = new int[w * h][];
    for (int y = 0; y < h; y++)
      for (int x = 0; x < w; x++) positions[y * w + x] = dir.at(x, h - 1 - y);
    List<Palette.Swatch[]> frames = new ArrayList<>();
    for (BufferedImage frame : image.frames)
      frames.add(palette.quantize(frame, transparent, dither, alpha));
    return new BuildPlan(positions, frames, image.delays);
  }

  public boolean animated() {
    return frames.size() > 1;
  }
}
