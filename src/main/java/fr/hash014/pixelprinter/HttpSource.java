/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** The same limits and address checks apply at every redirect. No Bukkit on worker threads. */
public final class HttpSource {
  private final File root;
  private final int maxBytes, timeout;
  private final List<String> allowed;

  public HttpSource(File root, int maxBytes, int timeout, List<String> allowed) {
    this.root = root;
    this.maxBytes = maxBytes;
    this.timeout = timeout;
    this.allowed = new ArrayList<>(allowed);
  }

  public File file(String name) throws IOException {
    if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,120}") || name.contains(".."))
      throw new IOException("Nom de fichier invalide.");
    File f = new File(root, name).getCanonicalFile();
    if (!f.getParentFile().equals(root.getCanonicalFile()))
      throw new IOException("Chemin hors du dossier images.");
    return f;
  }

  public byte[] load(String name) throws IOException {
    if (name.startsWith("https://") || name.startsWith("http://")) return download(name);
    File f = file(name);
    if (!f.isFile()) throw new IOException("Image introuvable: " + name);
    if (f.length() > maxBytes) throw new IOException("Fichier trop volumineux.");
    byte[] data;
    try (InputStream in = Files.newInputStream(f.toPath())) {
      data = read(in, maxBytes);
    }
    if (name.toLowerCase(Locale.ROOT).endsWith(".txt"))
      return download(new String(data, StandardCharsets.UTF_8).trim());
    return data;
  }

  public static boolean publicAddress(InetAddress a) {
    if (a.isAnyLocalAddress()
        || a.isLoopbackAddress()
        || a.isLinkLocalAddress()
        || a.isSiteLocalAddress()
        || a.isMulticastAddress()) return false;
    byte[] b = a.getAddress();
    if (b.length == 4) {
      int x = b[0] & 255, y = b[1] & 255;
      return x != 0
          && x < 224
          && !(x == 100 && y >= 64 && y <= 127)
          && !(x == 169 && y == 254)
          && !(x == 198 && (y == 18 || y == 19));
    }
    return (b[0] & 0xfe) != 0xfc; // IPv6 unique-local
  }

  public byte[] download(String text) throws IOException {
    URL url = new URL(text);
    for (int redirect = 0; redirect < 6; redirect++) {
      if (!url.getProtocol().equals("https") && !url.getProtocol().equals("http"))
        throw new IOException("Seuls HTTP et HTTPS sont acceptés.");
      if (url.getUserInfo() != null) throw new IOException("URL avec identifiants interdite.");
      if (!allowed.isEmpty() && !allowed.contains(url.getHost().toLowerCase(Locale.ROOT)))
        throw new IOException("Domaine absent de allowed-hosts.");
      InetAddress[] addresses = InetAddress.getAllByName(url.getHost());
      for (InetAddress address : addresses)
        if (!publicAddress(address)) throw new IOException("Adresse privée ou locale interdite.");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setInstanceFollowRedirects(false);
      conn.setConnectTimeout(timeout);
      conn.setReadTimeout(timeout);
      conn.setRequestProperty("User-Agent", "PixelPrinterReforged/2.0 (hash014)");
      try {
        int status = conn.getResponseCode();
        if (status >= 300 && status < 400) {
          String location = conn.getHeaderField("Location");
          if (location == null) throw new IOException("Redirection invalide.");
          url = new URL(url, location);
          continue;
        }
        if (status != 200) throw new IOException("Serveur HTTP: " + status);
        if (conn.getContentLengthLong() > maxBytes)
          throw new IOException("Téléchargement trop volumineux.");
        try (InputStream in = conn.getInputStream()) {
          return read(in, maxBytes);
        }
      } finally {
        conn.disconnect();
      }
    }
    throw new IOException("Trop de redirections.");
  }

  public static byte[] read(InputStream in, int max) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = in.read(buffer)) != -1) {
      if ((long) out.size() + count > max) throw new IOException("Limite de taille dépassée.");
      out.write(buffer, 0, count);
    }
    return out.toByteArray();
  }
}
