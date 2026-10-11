/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

public final class ColorMath {
  private ColorMath() {}

  private static double linear(int v) {
    double d = v / 255.0;
    return d > 0.04045 ? Math.pow((d + 0.055) / 1.055, 2.4) : d / 12.92;
  }

  private static double f(double d) {
    return d > 0.008856 ? Math.cbrt(d) : 7.787 * d + 16.0 / 116;
  }

  public static double[] lab(int rgb) {
    double r = linear(rgb >> 16 & 255), g = linear(rgb >> 8 & 255), b = linear(rgb & 255);
    double x = f((r * .4124564 + g * .3575761 + b * .1804375) / .95047),
        y = f(r * .2126729 + g * .7151522 + b * .072175),
        z = f((r * .0193339 + g * .119192 + b * .9503041) / 1.08883);
    return new double[] {116 * y - 16, 500 * (x - y), 200 * (y - z)};
  }

  public static double distance(double[] a, double[] b) {
    double l = a[0] - b[0], x = a[1] - b[1], y = a[2] - b[2];
    return l * l + x * x + y * y;
  }

  public static int clamp(double d) {
    return (int) Math.max(0, Math.min(255, Math.round(d)));
  }
}
