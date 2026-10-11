/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class PixelPrinterReforged extends JavaPlugin implements TabCompleter, Listener {
  private static final String PREFIX =
      ChatColor.GOLD + "[PixelPrinter Reforged] " + ChatColor.RESET;
  private ExecutorService executor;
  private final AtomicInteger pending = new AtomicInteger();
  private volatile boolean shuttingDown;
  private HttpSource source;
  private Images images;
  private Palette palette;
  private JobManager jobs;
  private MapService maps;
  private int maxHeight, maxBlocks, maxMapTiles, alpha;
  private boolean dither;
  private final Set<UUID> loading = new HashSet<>();
  private boolean restoring = true;
  private final List<JsonObject> unrestoredAnimations = new ArrayList<>();
  private final Object persistenceLock = new Object();
  private final java.util.concurrent.atomic.AtomicLong persistenceRevision =
      new java.util.concurrent.atomic.AtomicLong();

  public JobManager jobs() {
    return jobs;
  }

  public Images images() {
    return images;
  }

  public int maxMapTiles() {
    return maxMapTiles;
  }

  public Palette palette() {
    return palette;
  }

  @Override
  public void onEnable() {
    saveDefaultConfig();
    loadSettings();
    File folder = new File(getDataFolder(), "images");
    folder.mkdirs();
    File textures = new File(getDataFolder(), "custom_textures");
    textures.mkdirs();
    for (String example : Arrays.asList("hash014.png", "hash014.gif"))
      if (!new File(folder, example).exists()) saveResource("images/" + example, false);
    source =
        new HttpSource(
            folder,
            setting("max-download-bytes", 16777216, 1024, 67108864),
            setting("network-timeout-ms", 10000, 1000, 30000),
            getConfig().getStringList("allowed-hosts"));
    images =
        new Images(
            setting("max-source-pixels", 16777216, 4096, 33554432),
            setting("max-decoded-pixels", 33554432, 8192, 67108864),
            setting("max-frames", 128, 1, 256));
    palette = new Palette(Collections.emptyList());
    try {
      palette.textures(textures, 1048576);
    } catch (IOException ex) {
      getLogger().warning("Textures personnalisées: " + ex.getMessage());
    }
    palette.filter(getConfig().getStringList("materials"));
    if (palette.entries().isEmpty()) {
      getLogger().severe("Palette vide: corrigez materials dans config.yml.");
      getServer().getPluginManager().disablePlugin(this);
      return;
    }
    executor =
        new ThreadPoolExecutor(
            2,
            2,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(128),
            r -> {
              Thread t = new Thread(r, "PixelPrinterReforged-image");
              t.setDaemon(true);
              return t;
            });
    jobs = new JobManager(this);
    maps = new MapService(this);
    getCommand("pixelprinter").setTabCompleter(this);
    getServer().getPluginManager().registerEvents(this, this);
    getServer().getScheduler().runTaskTimer(this, () -> jobs.tick(), 1, 1);
    getServer()
        .getScheduler()
        .runTask(
            this,
            () -> {
              maps.restore();
              restoreAnimations();
            });
    getLogger()
        .info(
            "PixelPrinter Reforged 2.0.0 — made by hash014 | Bukkit "
                + Bukkit.getBukkitVersion()
                + " | "
                + (palette.modern() ? "modern" : "legacy")
                + " | "
                + palette.entries().size()
                + " colors | READY");
  }

  private int setting(String key, int fallback, int min, int max) {
    int value = getConfig().getInt(key, fallback);
    if (value < min || value > max)
      throw new IllegalArgumentException(key + " doit être entre " + min + " et " + max);
    return value;
  }

  private void loadSettings() {
    maxHeight = setting("max-height", 256, 1, 1024);
    maxBlocks = setting("max-blocks", 65536, 1, 1048576);
    maxMapTiles = setting("max-map-tiles", 64, 1, 256);
    alpha = setting("alpha-threshold", 128, 0, 255);
    dither = getConfig().getBoolean("dither", false);
  }

  @Override
  public void onDisable() {
    shuttingDown = true;
    if (jobs != null) {
      saveAnimations();
      jobs.close();
    }
    if (executor != null) executor.shutdownNow();
  }

  @EventHandler
  public void chunkUnload(org.bukkit.event.world.ChunkUnloadEvent event) {
    if (jobs != null
        && jobs.holds(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ()))
      Compat.preventUnload(event);
  }

  public void message(CommandSender sender, String text) {
    sender.sendMessage(PREFIX + text);
  }

  public <T> void worker(Callable<T> task, Consumer<T> success, Consumer<Exception> failure) {
    if (shuttingDown) return;
    try {
      executor.execute(
          () -> {
            try {
              T result = task.call();
              sync(
                  () -> {
                    try {
                      success.accept(result);
                    } catch (Exception ex) {
                      failure.accept(ex);
                    }
                  });
            } catch (Exception ex) {
              sync(() -> failure.accept(ex));
            }
          });
    } catch (RejectedExecutionException ex) {
      failure.accept(new IOException("File de chargement pleine."));
    }
  }

  private void sync(Runnable task) {
    if (!shuttingDown && isEnabled())
      try {
        getServer()
            .getScheduler()
            .runTask(
                this,
                () -> {
                  if (!shuttingDown) task.run();
                });
      } catch (IllegalStateException ignored) {
      }
  }

  private <T> void load(CommandSender sender, Callable<T> task, Consumer<T> success) {
    UUID id = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
    if (id != null && loading.contains(id))
      throw new IllegalArgumentException("Un chargement est déjà en cours pour vous.");
    if (pending.get() >= getConfig().getInt("max-pending-loads", 4))
      throw new IllegalArgumentException("Trop de chargements en cours.");
    pending.incrementAndGet();
    if (id != null) loading.add(id);
    message(sender, "Chargement en cours…");
    worker(
        task,
        result -> {
          pending.decrementAndGet();
          loading.remove(id);
          if (id != null && Bukkit.getPlayer(id) == null) return;
          try {
            success.accept(result);
          } catch (Exception ex) {
            message(sender, "Erreur: " + clean(ex));
          }
        },
        error -> {
          pending.decrementAndGet();
          loading.remove(id);
          message(sender, "Erreur: " + clean(error));
        });
  }

  private static String clean(Exception ex) {
    String s = ex.getMessage();
    return s == null ? ex.getClass().getSimpleName() : s.replace('\n', ' ').replace('\r', ' ');
  }

  private void permission(CommandSender sender, String node) {
    if (!sender.hasPermission("pixelprinter." + node))
      throw new IllegalArgumentException("Permission requise: pixelprinter." + node);
  }

  private Player player(CommandSender sender) {
    if (!(sender instanceof Player))
      throw new IllegalArgumentException("Commande réservée aux joueurs.");
    return (Player) sender;
  }

  private static int integer(String text, int min, int max) {
    int v;
    try {
      v = Integer.parseInt(text);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Nombre entier attendu: " + text);
    }
    if (v < min || v > max)
      throw new IllegalArgumentException("Nombre attendu entre " + min + " et " + max + ".");
    return v;
  }

  private static void arguments(String[] args, int min, int max, String usage) {
    if (args.length < min || args.length > max)
      throw new IllegalArgumentException("Usage: /pp " + usage);
  }

  private static boolean bool(String text) {
    if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false"))
      throw new IllegalArgumentException("Valeur attendue: true ou false.");
    return Boolean.parseBoolean(text);
  }

  private void remotePermission(CommandSender sender, String name) {
    if (name.startsWith("http:") || name.startsWith("https:") || name.endsWith(".txt"))
      permission(sender, "download");
  }

  private Location origin(Player player) {
    Location loc = player.getLocation();
    return new Location(player.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    try {
      String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
      switch (sub) {
        case "help":
        case "?":
          help(sender);
          break;
        case "version":
        case "debug":
        case "debug2":
          permission(sender, "admin");
          message(
              sender,
              "2.0.0 made by hash014 | "
                  + Bukkit.getVersion()
                  + " | palette="
                  + palette.entries().size()
                  + " | actifs="
                  + jobs.active().size()
                  + " | snapshots="
                  + jobs.reserved()
                  + " | chargements="
                  + pending.get());
          break;
        case "list":
          {
            permission(sender, "create");
            File[] files = new File(getDataFolder(), "images").listFiles(File::isFile);
            List<String> names = new ArrayList<>();
            if (files != null) for (File f : files) names.add(f.getName());
            Collections.sort(names);
            message(
                sender,
                names.isEmpty()
                    ? "Aucune image. /pp downloadimage nom URL"
                    : "Images: " + String.join(", ", names));
            break;
          }
        case "create":
          create(sender, args);
          break;
        case "map":
        case "mapwall":
        case "createframe":
        case "cf":
          map(sender, args, sub);
          break;
        case "createskin":
          skin(sender, args);
          break;
        case "specs":
          {
            permission(sender, "create");
            arguments(args, 2, 2, "specs <image>");
            remotePermission(sender, args[1]);
            load(
                sender,
                () -> images.decode(source.load(args[1]), 0, images.maxSourcePixels),
                a ->
                    message(
                        sender,
                        a.width()
                            + "x"
                            + a.height()
                            + " pixels, "
                            + a.frames.size()
                            + " frame(s)."));
            break;
          }
        case "download":
        case "d":
        case "downloadimage":
        case "di":
          download(sender, args, sub.equals("download") || sub.equals("d"));
          break;
        case "delete":
          {
            permission(sender, "delete");
            arguments(args, 2, 2, "delete <fichier>");
            File file = source.file(args[1]);
            if (!file.isFile() || !file.delete()) throw new IOException("Suppression impossible.");
            message(sender, "Image supprimée: " + file.getName());
            break;
          }
        case "undo":
          {
            permission(sender, "create");
            arguments(args, 1, 2, "undo [id]");
            UUID owner = sender instanceof Player ? ((Player) sender).getUniqueId() : null;
            JobManager.Record r =
                jobs.undo(
                    owner,
                    args.length == 2 ? integer(args[1], 1, Integer.MAX_VALUE) : null,
                    sender.hasPermission("pixelprinter.admin"));
            message(sender, "Annulation #" + r.id + " en cours.");
            break;
          }
        case "history":
          {
            permission(sender, "create");
            message(sender, "Historique: " + jobs.history(player(sender).getUniqueId()));
            break;
          }
        case "clearundo":
          {
            permission(sender, "create");
            jobs.clear(player(sender).getUniqueId());
            message(sender, "Historique libéré.");
            break;
          }
        case "jobs":
        case "listgifs":
          {
            permission(sender, "create");
            message(sender, "Travaux actifs: " + jobs.active().size());
            for (JobManager.Record r : jobs.active())
              message(
                  sender,
                  "#"
                      + r.id
                      + " "
                      + r.source
                      + " "
                      + (r.undoing ? "undo" : r.plan.animated() ? "GIF" : "image")
                      + " "
                      + r.cursor
                      + "/"
                      + r.plan.positions.length
                      + (r.paused ? " [pause]" : ""));
            break;
          }
        case "stop":
        case "stopgif":
        case "pause":
        case "resume":
          {
            permission(sender, "stop");
            arguments(args, 2, 2, sub + " <id>");
            JobManager.Record r = jobs.get(integer(args[1], 1, Integer.MAX_VALUE));
            if (r == null) throw new IllegalArgumentException("Travail introuvable.");
            if (!(sender instanceof Player)
                || !Objects.equals(r.owner, ((Player) sender).getUniqueId()))
              permission(sender, "admin");
            if (sub.equals("pause")) r.paused = true;
            else if (sub.equals("resume")) r.paused = false;
            else jobs.stop(r);
            saveAnimations();
            message(sender, "Travail #" + r.id + ": " + sub);
            break;
          }
        case "stopallgifs":
          {
            permission(sender, "admin");
            for (JobManager.Record r : new ArrayList<>(jobs.active()))
              if (r.plan.animated() && !r.undoing) jobs.stop(r);
            message(sender, "GIFs arrêtés.");
            break;
          }
        case "setloadcount":
          {
            permission(sender, "admin");
            arguments(args, 2, 2, "setloadcount <1-5000>");
            int count = integer(args[1], 1, 5000);
            getConfig().set("blocks-per-tick", count);
            saveConfig();
            message(sender, "Budget global: " + count + " blocs/tick.");
            break;
          }
        case "reload":
          {
            permission(sender, "admin");
            reloadConfig();
            loadSettings();
            message(
                sender,
                "Limites et budget rechargés. Redémarrez pour changer la palette et les options"
                    + " réseau.");
            break;
          }
        case "selftest":
          {
            permission(sender, "admin");
            if (sender instanceof Player)
              throw new IllegalArgumentException(
                  "Selftest réservé à la console du serveur de test.");
            new ServerSelfTest(this).run(sender);
            break;
          }
        case "playercheck":
          {
            permission(sender, "admin");
            if (sender instanceof Player)
              throw new IllegalArgumentException("Test réservé à la console.");
            arguments(args, 2, 2, "playercheck <joueur>");
            new ServerPlayerTest(this).run(playerByName(args[1]));
            break;
          }
        case "persistfixture":
        case "persistcheck":
          {
            permission(sender, "admin");
            if (sender instanceof Player)
              throw new IllegalArgumentException("Test réservé à la console.");
            ServerSelfTest test = new ServerSelfTest(this);
            if (sub.equals("persistfixture")) test.persistenceFixture();
            else test.persistenceCheck();
            break;
          }
        case "protocolinfo":
          {
            permission(sender, "admin");
            new ServerSelfTest(this).protocolInfo();
            break;
          }
        default:
          throw new IllegalArgumentException("Commande inconnue. /pp help");
      }
    } catch (Exception ex) {
      message(sender, "Erreur: " + clean(ex));
    }
    return true;
  }

  private Player playerByName(String name) {
    Player player = Bukkit.getPlayerExact(name);
    if (player == null) throw new IllegalArgumentException("Joueur déconnecté.");
    return player;
  }

  private void create(CommandSender sender, String[] args) {
    if (restoring)
      throw new IllegalArgumentException(
          "Restauration des GIFs en cours, réessayez dans un instant.");
    permission(sender, "create");
    Player p = player(sender);
    arguments(args, 4, 6, "create <direction> <image> <hauteur-blocs> [transparent] [dither]");
    Direction dir = Direction.parse(args[1]);
    int height = integer(args[3], 1, maxHeight);
    boolean transparent = args.length < 5 || bool(args[4]),
        diffusion = args.length == 6 ? bool(args[5]) : dither;
    remotePermission(sender, args[2]);
    Location location = origin(p);
    Palette current = palette;
    load(
        sender,
        () ->
            BuildPlan.image(
                images.decode(source.load(args[2]), height, maxBlocks),
                dir,
                current,
                transparent,
                diffusion,
                alpha),
        plan -> {
          JobManager.Record r = jobs.submit(p.getUniqueId(), args[2], location, plan);
          if (plan.animated()) {
            Map<String, Object> description = new LinkedHashMap<>();
            description.put("direction", dir.name());
            description.put("height", height);
            description.put("transparent", transparent);
            description.put("dither", diffusion);
            r.descriptor = description;
            saveAnimations();
          }
          message(
              sender,
              "Travail #"
                  + r.id
                  + ": "
                  + plan.positions.length
                  + " positions, "
                  + plan.frames.size()
                  + " frame(s). /pp undo "
                  + r.id);
        });
  }

  private void map(CommandSender sender, String[] args, String sub) {
    permission(sender, "create");
    Player p = player(sender);
    boolean wall = !sub.equals("map");
    arguments(
        args,
        wall ? 4 : 2,
        wall ? 4 : 3,
        wall ? "mapwall <direction> <image> <rangées-cartes>" : "map <image> [rangées-cartes]");
    Direction dir = wall ? Direction.parse(args[1]) : Direction.SOUTH;
    if (dir.flat())
      throw new IllegalArgumentException("Mur de cartes: direction verticale uniquement.");
    String name = args[wall ? 2 : 1];
    remotePermission(sender, name);
    int height;
    if (sub.equals("createframe") || sub.equals("cf")) height = integer(args[3], 1, 1024);
    else height = integer(args.length > (wall ? 3 : 2) ? args[wall ? 3 : 2] : "1", 1, 8) * 128;
    Location location = origin(p);
    load(
        sender,
        () -> maps.prepare(images.decode(source.load(name), height, maxMapTiles * 16384)),
        tiles -> {
          if (wall) {
            JobManager.Record r = maps.wall(p, location, dir, tiles);
            message(sender, "Mur de " + tiles.size() + " cartes, travail #" + r.id + ".");
          } else {
            List<ItemStack> items = maps.create(p.getWorld(), tiles);
            for (ItemStack item : items)
              for (ItemStack leftover : p.getInventory().addItem(item).values())
                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
            message(sender, items.size() + " carte(s) créée(s), GIF animé si présent.");
          }
        });
  }

  private void skin(CommandSender sender, String[] args) {
    permission(sender, "create");
    Player p = player(sender);
    arguments(args, 3, 4, "createskin <direction> <joueur|UUID|fichier.png> [échelle 1-4]");
    Direction dir = Direction.parse(args[1]);
    int scale = args.length == 4 ? integer(args[3], 1, 4) : 1;
    Location location = origin(p);
    Palette current = palette;
    if (!args[2].endsWith(".png")) permission(sender, "download");
    load(
        sender,
        () -> {
          Skins.Skin skin =
              args[2].endsWith(".png")
                  ? new Skins.Skin(
                      images.decode(source.load(args[2]), 0, 4096).frames.get(0), false)
                  : Skins.fetch(args[2], source, images);
          return Skins.statue(skin, dir, current, scale);
        },
        plan -> {
          JobManager.Record r = jobs.submit(p.getUniqueId(), "skin:" + args[2], location, plan);
          message(sender, "Statue: " + plan.positions.length + " blocs, travail #" + r.id + ".");
        });
  }

  private void download(CommandSender sender, String[] args, boolean reference) throws IOException {
    permission(sender, "download");
    arguments(args, 3, 3, (reference ? "download" : "downloadimage") + " <nom> <URL>");
    source.file(args[1]);
    load(
        sender,
        () -> {
          byte[] bytes = source.download(args[2]);
          images.decode(bytes, 0, images.maxSourcePixels);
          boolean gif =
              bytes.length >= 6 && new String(bytes, 0, 3, StandardCharsets.US_ASCII).equals("GIF");
          String name = args[1];
          if (reference) {
            if (!name.endsWith(".txt")) name += ".txt";
            bytes = args[2].getBytes(StandardCharsets.UTF_8);
          } else {
            String extension = gif ? ".gif" : ".png";
            if (!name.toLowerCase(Locale.ROOT).endsWith(extension)) name += extension;
            if (!gif) {
              Images.Animation image = images.decode(bytes, 0, images.maxSourcePixels);
              ByteArrayOutputStream out = new ByteArrayOutputStream();
              javax.imageio.ImageIO.write(image.frames.get(0), "png", out);
              bytes = out.toByteArray();
            }
          }
          File file = source.file(name);
          try (OutputStream out =
              Files.newOutputStream(
                  file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            out.write(bytes);
          }
          return name;
        },
        name -> message(sender, "Image enregistrée: " + name));
  }

  private void help(CommandSender sender) {
    message(sender, "2.0.0 — made by hash014");
    for (String line :
        Arrays.asList(
            "/pp create <direction> <image> <hauteur blocs> [transparent=true] [dither=false]",
            "/pp map <image> [rangées=1] | mapwall <direction> <image> <rangées>",
            "/pp createFrame <direction> <image> <hauteur pixels>",
            "/pp createSkin <direction> <joueur/UUID/skin.png> [échelle=1]",
            "/pp downloadimage <nom> <URL> | download <nom> <URL>",
            "/pp list | specs <image> | delete <image>",
            "/pp jobs | pause/resume/stop <id> | stopAllGifs",
            "/pp undo [id] | history | clearundo",
            "/pp setLoadCount <budget> | reload | version",
            "Directions: South, North, East, West, FlatNorthEast, FlatSouthEast, FlatNorthWest,"
                + " FlatSouthWest")) sender.sendMessage(ChatColor.GRAY + line);
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] args) {
    List<String> values = new ArrayList<>();
    if (args.length == 1)
      values.addAll(
          Arrays.asList(
              "help",
              "create",
              "createSkin",
              "map",
              "mapwall",
              "createFrame",
              "download",
              "downloadimage",
              "list",
              "specs",
              "delete",
              "undo",
              "history",
              "clearundo",
              "jobs",
              "listGifs",
              "pause",
              "resume",
              "stop",
              "stopGif",
              "stopAllGifs",
              "setLoadCount",
              "reload",
              "version"));
    else {
      String sub = args[0].toLowerCase(Locale.ROOT);
      if (args.length == 2
          && Arrays.asList("create", "createskin", "mapwall", "createframe", "cf").contains(sub)) {
        for (Direction dir : Direction.values()) values.add(dir.name());
      } else if ((args.length == 3
              && Arrays.asList("create", "mapwall", "createframe", "cf", "createskin")
                  .contains(sub))
          || (args.length == 2 && Arrays.asList("map", "specs", "delete").contains(sub))) {
        File[] files = new File(getDataFolder(), "images").listFiles(File::isFile);
        if (files != null) for (File f : files) values.add(f.getName());
      } else if (args.length == 2
          && Arrays.asList("undo", "pause", "resume", "stop", "stopgif").contains(sub)) {
        for (JobManager.Record r : jobs.active()) values.add(String.valueOf(r.id));
        if (sender instanceof Player)
          for (Integer id : jobs.history(((Player) sender).getUniqueId()))
            values.add(String.valueOf(id));
      } else if (args.length >= 5 && sub.equals("create"))
        values.addAll(Arrays.asList("true", "false"));
    }
    String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
    values.removeIf(v -> !v.toLowerCase(Locale.ROOT).startsWith(prefix));
    Collections.sort(values);
    return values;
  }

  public void saveAnimations() {
    if (jobs == null || restoring) return;
    List<Map<String, Object>> entries = new ArrayList<>();
    for (JsonObject failed : unrestoredAnimations)
      entries.add(
          new Gson()
              .fromJson(
                  failed,
                  new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType()));
    for (JobManager.Record r : jobs.active())
      if (r.plan.animated() && !r.undoing && r.descriptor != null) {
        Map<String, Object> item = new LinkedHashMap<>(r.descriptor);
        item.put("source", r.source);
        item.put("owner", r.owner == null ? "console" : r.owner.toString());
        item.put("world", r.origin.getWorld().getName());
        item.put("x", r.origin.getBlockX());
        item.put("y", r.origin.getBlockY());
        item.put("z", r.origin.getBlockZ());
        item.put("paused", r.paused);
        item.put("originals", new LinkedHashMap<>(r.durableOriginals));
        entries.add(item);
      }
    long revision = persistenceRevision.incrementAndGet();
    Runnable write =
        () -> {
          synchronized (persistenceLock) {
            if (revision != persistenceRevision.get()) return;
            try {
              Path path = new File(getDataFolder(), "animations.json").toPath(),
                  temp = path.resolveSibling("animations.json.tmp");
              Files.write(temp, new Gson().toJson(entries).getBytes(StandardCharsets.UTF_8));
              try {
                Files.move(
                    temp,
                    path,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
              } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
              }
            } catch (IOException ex) {
              getLogger().warning("Sauvegarde GIFs: " + ex.getMessage());
            }
          }
        };
    if (shuttingDown) write.run();
    else
      try {
        executor.execute(write);
      } catch (RejectedExecutionException ex) {
        getLogger().warning("Sauvegarde GIF différée: file pleine.");
      }
  }

  private void restoreAnimations() {
    File file = new File(getDataFolder(), "animations.json");
    if (!file.isFile()) {
      restoring = false;
      return;
    }
    worker(
        () ->
            JsonParser.parseString(
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                .getAsJsonArray(),
        entries -> {
          if (entries.size() == 0) {
            restoring = false;
            return;
          }
          AtomicInteger remaining = new AtomicInteger(entries.size());
          Runnable restored =
              () -> {
                if (remaining.decrementAndGet() == 0) {
                  restoring = false;
                  saveAnimations();
                }
              };
          for (JsonElement entry : entries) {
            try {
              JsonObject item = entry.getAsJsonObject();
              World world = Bukkit.getWorld(item.get("world").getAsString());
              if (world == null) {
                getLogger().warning("Monde GIF absent: " + item.get("world"));
                unrestoredAnimations.add(item);
                restored.run();
                continue;
              }
              UUID owner =
                  item.get("owner").getAsString().equals("console")
                      ? null
                      : UUID.fromString(item.get("owner").getAsString());
              Location loc =
                  new Location(
                      world,
                      item.get("x").getAsInt(),
                      item.get("y").getAsInt(),
                      item.get("z").getAsInt());
              String name = item.get("source").getAsString();
              Direction dir = Direction.parse(item.get("direction").getAsString());
              int height = item.get("height").getAsInt();
              boolean transparent = item.get("transparent").getAsBoolean(),
                  diffusion = item.get("dither").getAsBoolean();
              worker(
                  () ->
                      BuildPlan.image(
                          images.decode(source.load(name), height, maxBlocks),
                          dir,
                          palette,
                          transparent,
                          diffusion,
                          alpha),
                  plan -> {
                    Map<Integer, org.bukkit.block.BlockState> originals = new LinkedHashMap<>();
                    Map<Integer, Map<String, Object>> durable = new LinkedHashMap<>();
                    Map<String, Object> descriptor = new LinkedHashMap<>();
                    descriptor.put("direction", dir.name());
                    descriptor.put("height", height);
                    descriptor.put("transparent", transparent);
                    descriptor.put("dither", diffusion);
                    for (Map.Entry<String, JsonElement> saved :
                        item.getAsJsonObject("originals").entrySet()) {
                      int index = Integer.parseInt(saved.getKey());
                      if (index < 0 || index >= plan.positions.length)
                        throw new IllegalArgumentException("Indice de snapshot GIF invalide.");
                      int[] p = plan.positions[index];
                      Map<String, Object> state =
                          new Gson()
                              .fromJson(
                                  saved.getValue(),
                                  new com.google.gson.reflect.TypeToken<
                                      Map<String, Object>>() {}.getType());
                      originals.put(
                          index,
                          OriginalBlock.load(
                              world.getBlockAt(
                                  loc.getBlockX() + p[0],
                                  loc.getBlockY() + p[1],
                                  loc.getBlockZ() + p[2]),
                              state));
                      durable.put(index, state);
                    }
                    JobManager.Record r = jobs.submit(owner, name, loc, plan);
                    r.descriptor = descriptor;
                    r.originals.putAll(originals);
                    r.durableOriginals.putAll(durable);
                    r.paused =
                        item.get("paused").getAsBoolean()
                            || !getConfig().getBoolean("resume-gifs", true);
                    r.savedFirstFrame = false;
                    getLogger()
                        .info("GIF restauré #" + r.id + ": " + name + " (annulation conservée).");
                    restored.run();
                  },
                  error -> {
                    unrestoredAnimations.add(item);
                    getLogger().warning("Restauration GIF " + name + ": " + error.getMessage());
                    restored.run();
                  });
            } catch (Exception ex) {
              if (entry.isJsonObject()) unrestoredAnimations.add(entry.getAsJsonObject());
              getLogger().warning("Descripteur GIF invalide: " + ex.getMessage());
              restored.run();
            }
          }
        },
        error -> {
          restoring = false;
          getLogger().warning("Restauration GIFs: " + error.getMessage());
        });
  }
}
