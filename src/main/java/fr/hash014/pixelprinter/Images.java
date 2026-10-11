/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.Node;

public final class Images {
  public static final class Animation {
    public final List<BufferedImage> frames;
    public final int[] delays;

    public Animation(List<BufferedImage> frames, int[] delays) {
      this.frames = Collections.unmodifiableList(frames);
      this.delays = delays;
    }

    public int width() {
      return frames.get(0).getWidth();
    }

    public int height() {
      return frames.get(0).getHeight();
    }
  }

  public final int maxSourcePixels, maxDecodedPixels, maxFrames;

  public Images(int source, int decoded, int frames) {
    maxSourcePixels = source;
    maxDecodedPixels = decoded;
    maxFrames = frames;
  }

  private static Node child(Node node, String name) {
    for (Node c = node.getFirstChild(); c != null; c = c.getNextSibling())
      if (c.getNodeName().equals(name)) return c;
    return null;
  }

  private static String attr(Node n, String name, String fallback) {
    return n == null || n.getAttributes().getNamedItem(name) == null
        ? fallback
        : n.getAttributes().getNamedItem(name).getNodeValue();
  }

  private static int number(Node n, String name, int fallback) {
    return Integer.parseInt(attr(n, name, String.valueOf(fallback)));
  }

  private static BufferedImage copy(BufferedImage src) {
    BufferedImage dst =
        new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = dst.createGraphics();
    g.drawImage(src, 0, 0, null);
    g.dispose();
    return dst;
  }

  public static BufferedImage resize(BufferedImage src, int w, int h) {
    BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = dst.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.drawImage(src, 0, 0, w, h, null);
    g.dispose();
    return dst;
  }

  public Animation decode(byte[] data, int height, int maxOutputPixels) throws IOException {
    try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("Format image non reconnu (PNG/JPEG/GIF/BMP).");
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, false, false);
        boolean gif = reader.getFormatName().equalsIgnoreCase("gif");
        int width = reader.getWidth(0), sourceHeight = reader.getHeight(0);
        int background = 0, backgroundIndex = -1;
        if (gif) {
          IIOMetadata meta = reader.getStreamMetadata();
          if (meta != null) {
            Node logical =
                child(meta.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            width = number(logical, "logicalScreenWidth", width);
            sourceHeight = number(logical, "logicalScreenHeight", sourceHeight);
            Node table = child(meta.getAsTree("javax_imageio_gif_stream_1.0"), "GlobalColorTable");
            backgroundIndex = number(table, "backgroundColorIndex", -1);
            if (table != null)
              for (Node entry = table.getFirstChild();
                  entry != null;
                  entry = entry.getNextSibling())
                if (number(entry, "index", -2) == backgroundIndex)
                  background =
                      0xff000000
                          | number(entry, "red", 0) << 16
                          | number(entry, "green", 0) << 8
                          | number(entry, "blue", 0);
          }
        }
        if (width < 1 || sourceHeight < 1 || (long) width * sourceHeight > maxSourcePixels)
          throw new IOException("Dimensions source trop grandes.");
        int h = height == 0 ? sourceHeight : height,
            w =
                height == 0
                    ? width
                    : Math.max(1, (int) Math.round((double) width * h / sourceHeight));
        if (h < 1 || w < 1 || (long) w * h > maxOutputPixels)
          throw new IOException("Image finale trop grande.");
        int n = gif ? reader.getNumImages(true) : 1;
        if (n < 1
            || n > maxFrames
            || (long) n * (width * (long) sourceHeight + w * (long) h) > maxDecodedPixels)
          throw new IOException("Trop de frames ou de pixels décodés.");
        List<BufferedImage> frames = new ArrayList<>();
        int[] delays = new int[n];
        BufferedImage canvas = new BufferedImage(width, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Rectangle previousRect = null;
        String disposal = "none";
        int clearColor = background;
        BufferedImage previous = null;
        for (int i = 0; i < n; i++) {
          if (Thread.currentThread().isInterrupted()) throw new IOException("Chargement annulé.");
          if (reader.getWidth(i) * (long) reader.getHeight(i) > maxSourcePixels)
            throw new IOException("Frame trop grande.");
          if (gif) {
            if ("restoreToBackgroundColor".equals(disposal) && previousRect != null) {
              Graphics2D g = canvas.createGraphics();
              g.setComposite(AlphaComposite.Src);
              g.setColor(new Color(clearColor, true));
              g.fill(previousRect);
              g.dispose();
            } else if ("restoreToPrevious".equals(disposal) && previous != null)
              canvas = copy(previous);
            Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0"),
                desc = child(tree, "ImageDescriptor"),
                control = child(tree, "GraphicControlExtension");
            clearColor =
                "TRUE".equalsIgnoreCase(attr(control, "transparentColorFlag", "FALSE"))
                        && number(control, "transparentColorIndex", -2) == backgroundIndex
                    ? 0
                    : background;
            if (i == 0 && clearColor != 0) {
              Graphics2D initial = canvas.createGraphics();
              initial.setColor(new Color(clearColor, true));
              initial.fillRect(0, 0, width, sourceHeight);
              initial.dispose();
            }
            previousRect =
                new Rectangle(
                    number(desc, "imageLeftPosition", 0),
                    number(desc, "imageTopPosition", 0),
                    reader.getWidth(i),
                    reader.getHeight(i));
            if (previousRect.x < 0
                || previousRect.y < 0
                || previousRect.x + (long) previousRect.width > width
                || previousRect.y + (long) previousRect.height > sourceHeight)
              throw new IOException("Frame GIF hors du canevas.");
            disposal = attr(control, "disposalMethod", "none");
            previous = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
            BufferedImage patch = reader.read(i);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(patch, previousRect.x, previousRect.y, null);
            g.dispose();
            delays[i] = Math.max(1, (number(control, "delayTime", 10) + 4) / 5);
          } else {
            canvas = reader.read(i);
            delays[i] = Integer.MAX_VALUE;
          }
          frames.add(resize(canvas, w, h));
        }
        return new Animation(frames, delays);
      } finally {
        reader.dispose();
      }
    } catch (IllegalArgumentException ex) {
      throw new IOException("Métadonnées image invalides.", ex);
    }
  }
}
