/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CoreTest {
  @TempDir Path temp;

  private static byte[] png(int w, int h) throws IOException {
    BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xffff0000);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }

  @Test
  void imageDimensionsAndTransparency() throws Exception {
    Images.Animation a = new Images(1000, 10000, 10).decode(png(20, 10), 5, 1000);
    assertEquals(10, a.width());
    assertEquals(5, a.height());
    assertEquals(1, a.frames.size());
    assertEquals(0, a.frames.get(0).getRGB(9, 4) >>> 24);
  }

  @Test
  void rejectSourceBombBeforePixelAllocation() throws Exception {
    assertThrows(IOException.class, () -> new Images(100, 10000, 10).decode(png(20, 10), 5, 1000));
  }

  @Test
  void rejectFinalOutputBudget() throws Exception {
    assertThrows(IOException.class, () -> new Images(1000, 10000, 10).decode(png(20, 10), 20, 799));
  }

  @Test
  void rejectUnknownFormat() {
    assertThrows(
        IOException.class, () -> new Images(1000, 10000, 10).decode(new byte[] {1, 2, 3}, 0, 1000));
  }

  @Test
  void truncatedPngFails() throws Exception {
    byte[] png = png(20, 10);
    assertThrows(
        IOException.class,
        () -> new Images(1000, 10000, 10).decode(Arrays.copyOf(png, 30), 0, 1000));
  }

  @Test
  void boundedStreams() throws Exception {
    assertArrayEquals(new byte[5], HttpSource.read(new ByteArrayInputStream(new byte[5]), 5));
    assertThrows(
        IOException.class, () -> HttpSource.read(new ByteArrayInputStream(new byte[6]), 5));
  }

  @Test
  void filenamesCannotEscape() throws Exception {
    HttpSource source = new HttpSource(temp.toFile(), 10, 1000, Collections.emptyList());
    for (String name :
        Arrays.asList(
            "../a.png", "..\\a.png", "C:\\a.png", "a/b.png", "x..png", ".hidden", "a:stream"))
      assertThrows(IOException.class, () -> source.file(name), name);
    assertEquals(temp.resolve("logo.png").toFile(), source.file("logo.png"));
  }

  @Test
  void localFilesRemainBounded() throws Exception {
    Files.write(temp.resolve("big.png"), new byte[11]);
    HttpSource source = new HttpSource(temp.toFile(), 10, 1000, Collections.emptyList());
    assertThrows(IOException.class, () -> source.load("big.png"));
  }

  @Test
  void rejectLocalAndNonHttpUrls() throws Exception {
    HttpSource source = new HttpSource(temp.toFile(), 100, 1000, Collections.emptyList());
    for (String url :
        Arrays.asList(
            "file:///etc/passwd",
            "http://127.0.0.1/a",
            "http://[::1]/a",
            "ftp://example.com/a",
            "http://user:pass@example.com/a"))
      assertThrows(IOException.class, () -> source.download(url));
  }

  @Test
  void privateAddresses() throws Exception {
    for (String ip :
        Arrays.asList(
            "0.0.0.0",
            "127.0.0.1",
            "10.3.4.5",
            "192.168.1.2",
            "172.16.1.2",
            "169.254.169.254",
            "100.64.0.1",
            "198.18.0.1",
            "::1",
            "fc00::1",
            "fe80::1",
            "224.1.2.3")) assertFalse(HttpSource.publicAddress(InetAddress.getByName(ip)), ip);
    assertTrue(HttpSource.publicAddress(InetAddress.getByName("8.8.8.8")));
  }

  @ParameterizedTest
  @EnumSource(Direction.class)
  void directionsAreOrthogonalAndReversible(Direction dir) {
    assertEquals(0, dir.ux * dir.vx + dir.uy * dir.vy + dir.uz * dir.vz);
    assertEquals(1, dir.ux * dir.ux + dir.uy * dir.uy + dir.uz * dir.uz);
    assertEquals(1, dir.vx * dir.vx + dir.vy * dir.vy + dir.vz * dir.vz);
    assertEquals(dir, Direction.parse(dir.name()));
  }

  @Test
  void aliasesAndInvalidDirections() {
    assertEquals(Direction.SOUTH, Direction.parse("up_south"));
    assertEquals(Direction.FLATNORTHEAST, Direction.parse("FlatNorthEast"));
    assertThrows(IllegalArgumentException.class, () -> Direction.parse("hello"));
  }

  @Test
  void labReference() {
    assertEquals(100, ColorMath.lab(0xffffff)[0], .01);
    assertEquals(0, ColorMath.lab(0)[0], .01);
    assertTrue(ColorMath.distance(ColorMath.lab(0xff0000), ColorMath.lab(0x00ff00)) > 1000);
  }

  private byte[] gif(String disposal) throws Exception {
    return gif(disposal, false);
  }

  private byte[] gif(String disposal, boolean transparentBackground) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    try (ImageOutputStream io = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(io);
      IIOMetadata stream = writer.getDefaultStreamMetadata(writer.getDefaultWriteParam());
      IIOMetadataNode tree = (IIOMetadataNode) stream.getAsTree("javax_imageio_gif_stream_1.0");
      IIOMetadataNode screen =
          (IIOMetadataNode) tree.getElementsByTagName("LogicalScreenDescriptor").item(0);
      screen.setAttribute("logicalScreenWidth", "3");
      screen.setAttribute("logicalScreenHeight", "1");
      stream.setFromTree("javax_imageio_gif_stream_1.0", tree);
      writer.prepareWriteSequence(stream);
      for (int i = 0; i < 3; i++) {
        BufferedImage frame = new BufferedImage(i == 0 ? 3 : 1, 1, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < frame.getWidth(); x++)
          frame.setRGB(x, 0, i == 0 ? 0xffff0000 : i == 1 ? 0xff00ff00 : 0xff0000ff);
        IIOMetadata meta =
            writer.getDefaultImageMetadata(
                ImageTypeSpecifier.createFromRenderedImage(frame), writer.getDefaultWriteParam());
        IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree("javax_imageio_gif_image_1.0"),
            control =
                (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0),
            desc = (IIOMetadataNode) root.getElementsByTagName("ImageDescriptor").item(0);
        desc.setAttribute("imageLeftPosition", String.valueOf(i == 2 ? 2 : 0));
        control.setAttribute("disposalMethod", i == 1 ? disposal : "none");
        control.setAttribute("transparentColorFlag", transparentBackground ? "TRUE" : "FALSE");
        control.setAttribute("transparentColorIndex", "0");
        control.setAttribute("delayTime", String.valueOf(5 + i * 5));
        meta.setFromTree("javax_imageio_gif_image_1.0", root);
        writer.writeToSequence(new IIOImage(frame, null, meta), writer.getDefaultWriteParam());
      }
      writer.endWriteSequence();
    } finally {
      writer.dispose();
    }
    byte[] encoded = out.toByteArray();
    // The writer can replace the requested index during conversion to an indexed palette.
    // Set a GCE transparency index that matches the actual global background index.
    if (transparentBackground)
      for (int i = 13; i < encoded.length - 7; i++)
        if ((encoded[i] & 255) == 0x21 && (encoded[i + 1] & 255) == 0xf9 && encoded[i + 2] == 4) {
          encoded[i + 3] |= 1;
          encoded[i + 6] = encoded[11];
        }
    return encoded;
  }

  @Test
  void gifDeltaFramesAndTiming() throws Exception {
    Images.Animation a = new Images(100, 1000, 10).decode(gif("none"), 0, 100);
    assertEquals(3, a.frames.size());
    assertArrayEquals(new int[] {1, 2, 3}, a.delays);
    assertEquals(0xff00ff00, a.frames.get(2).getRGB(0, 0));
    assertEquals(0xffff0000, a.frames.get(2).getRGB(1, 0));
    assertEquals(0xff0000ff, a.frames.get(2).getRGB(2, 0));
  }

  @Test
  void gifRestorePrevious() throws Exception {
    Images.Animation a = new Images(100, 1000, 10).decode(gif("restoreToPrevious"), 0, 100);
    assertEquals(0xffff0000, a.frames.get(2).getRGB(0, 0));
  }

  @Test
  void gifRestoreBackground() throws Exception {
    Images.Animation a = new Images(100, 1000, 10).decode(gif("restoreToBackgroundColor"), 0, 100);
    assertEquals(255, a.frames.get(2).getRGB(0, 0) >>> 24);
    assertEquals(0xffff0000, a.frames.get(2).getRGB(1, 0));
  }

  @Test
  void gifTransparentBackground() throws Exception {
    Images.Animation a =
        new Images(100, 1000, 10).decode(gif("restoreToBackgroundColor", true), 0, 100);
    assertEquals(0, a.frames.get(2).getRGB(0, 0) >>> 24);
  }

  @Test
  void gifFrameAndMemoryLimits() throws Exception {
    byte[] data = gif("none");
    assertThrows(IOException.class, () -> new Images(100, 1000, 2).decode(data, 0, 100));
    assertThrows(IOException.class, () -> new Images(100, 17, 3).decode(data, 0, 100));
  }

  @Test
  void paletteAlphaAndDiffusion() {
    Palette palette = new Palette(Collections.emptyList());
    BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 0xffaabbcc);
    Palette.Swatch[] result = palette.quantize(image, true, true, 128);
    assertNotNull(result[0]);
    assertNull(result[1]);
    assertNotNull(palette.quantize(image, false, false, 128)[1]);
  }

  private static Palette.Swatch at(BuildPlan plan, int x, int y, int z) {
    for (int i = 0; i < plan.positions.length; i++)
      if (Arrays.equals(plan.positions[i], new int[] {x, y, z})) return plan.frames.get(0)[i];
    throw new AssertionError("Missing coordinate " + x + "," + y + "," + z);
  }

  @Test
  void statueFrontOrientationAndUv() {
    Palette palette = new Palette(Collections.emptyList());
    BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) skin.setRGB(x, y, 0xffff0000);
    for (int y = 8; y < 16; y++) for (int x = 24; x < 32; x++) skin.setRGB(x, y, 0xff0000ff);
    BuildPlan south = Skins.statue(new Skins.Skin(skin, false), Direction.SOUTH, palette, 1);
    assertSame(palette.nearest(0xff0000), at(south, 0, 28, 3));
    assertSame(palette.nearest(0x0000ff), at(south, 0, 28, -4));
    BuildPlan east = Skins.statue(new Skins.Skin(skin, false), Direction.EAST, palette, 1);
    assertSame(palette.nearest(0xff0000), at(east, 3, 28, 0));
  }

  @Test
  void statueScalingAndUniqueness() {
    Palette palette = new Palette(Collections.emptyList());
    BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    BuildPlan plan = Skins.statue(new Skins.Skin(skin, false), Direction.SOUTH, palette, 2);
    Set<String> positions = new HashSet<>();
    for (int[] p : plan.positions) positions.add(Arrays.toString(p));
    assertEquals(plan.positions.length, positions.size());
    assertEquals(63, Arrays.stream(plan.positions).mapToInt(p -> p[1]).max().getAsInt());
    assertEquals(-16, Arrays.stream(plan.positions).mapToInt(p -> p[0]).min().getAsInt());
    assertEquals(15, Arrays.stream(plan.positions).mapToInt(p -> p[0]).max().getAsInt());
    assertThrows(
        IllegalArgumentException.class,
        () -> Skins.statue(new Skins.Skin(skin, false), Direction.FLATNORTHEAST, palette, 1));
  }

  @Test
  void customTextureOverridesOneLegacyVariant() throws Exception {
    Palette palette = new Palette(Collections.emptyList());
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++) image.setRGB(x, y, 0xff123456);
    ImageIO.write(image, "png", temp.resolve("red_wool.png").toFile());
    long before = palette.entries().stream().filter(s -> s.material.name().equals("WOOL")).count();
    palette.textures(temp.toFile(), 100);
    assertEquals(
        before, palette.entries().stream().filter(s -> s.material.name().equals("WOOL")).count());
    assertEquals(
        0x123456,
        palette.entries().stream()
            .filter(s -> s.material.name().equals("WOOL") && s.data == 14)
            .findFirst()
            .get()
            .rgb);
  }
}
