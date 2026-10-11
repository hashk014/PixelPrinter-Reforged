/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 hash014
 */
package fr.hash014.pixelprinter;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/** One scheduler, shared operation/time budgets, fair rotation and bounded undo. */
public final class JobManager {
  public static final class Record {
    public final int id;
    public final UUID owner;
    public final String source;
    public final Location origin;
    public final BuildPlan plan;
    public final LinkedHashMap<Integer, BlockState> originals = new LinkedHashMap<>();
    public final Map<UUID, Location> entities = new LinkedHashMap<>();
    public final Map<Integer, Map<String, Object>> durableOriginals = new LinkedHashMap<>();
    public Map<String, Object> descriptor;
    public boolean savedFirstFrame;
    public final Map<Integer, ItemStack[]> inventories = new HashMap<>();
    public final Map<Integer, String[]> signs = new HashMap<>();
    public final Set<Long> chunks = new HashSet<>();
    public int cursor, frame;
    public long due;
    public boolean paused, done, undoing;
    public String failure;
    public Runnable onComplete;
    private List<BlockState> undo;
    private List<Integer> undoIndices;
    private int undoCursor;
    private UUID undoActor;

    Record(int id, UUID owner, String source, Location origin, BuildPlan plan) {
      this.id = id;
      this.owner = owner;
      this.source = source;
      this.origin = origin.clone();
      this.plan = plan;
    }
  }

  private final PixelPrinterReforged plugin;
  private final LinkedHashMap<Integer, Record> active = new LinkedHashMap<>();
  private final Map<UUID, Deque<Record>> history = new HashMap<>();
  private int sequence = 1, rotation = 0;
  private long tick;
  private int snapshots;

  public JobManager(PixelPrinterReforged plugin) {
    this.plugin = plugin;
  }

  private long framePixels;

  private int limit(String key, int value) {
    return plugin.getConfig().getInt(key, value);
  }

  public Collection<Record> active() {
    return Collections.unmodifiableCollection(active.values());
  }

  public Record get(int id) {
    return active.get(id);
  }

  public Record submit(UUID owner, String source, Location origin, BuildPlan plan) {
    if (active.size() >= limit("max-jobs", 8))
      throw new IllegalArgumentException("Trop de travaux actifs.");
    long pixels = (long) plan.positions.length * plan.frames.size();
    if (framePixels + pixels > limit("max-active-frame-pixels", 8388608))
      throw new IllegalArgumentException(
          "Mémoire des frames pleine. Arrêtez un GIF ou libérez l'historique.");
    if (plan.positions.length > limit("max-blocks", 65536))
      throw new IllegalArgumentException("Trop de blocs.");
    if (snapshots + (long) plan.positions.length > limit("max-undo-blocks", 262144))
      throw new IllegalArgumentException(
          "Mémoire d'annulation pleine. /pp clearundo libère votre historique.");
    World world = origin.getWorld();
    int min = Compat.minHeight(world), max = world.getMaxHeight();
    for (int[] p : plan.positions)
      if (origin.getBlockY() + p[1] < min
          || origin.getBlockY() + p[1] >= max
          || Math.abs((long) origin.getBlockX() + p[0]) > 29999984
          || Math.abs((long) origin.getBlockZ() + p[2]) > 29999984)
        throw new IllegalArgumentException("Construction hors des limites du monde.");
    Record r = new Record(sequence++, owner, source, origin, plan);
    active.put(r.id, r);
    snapshots += plan.positions.length;
    framePixels += pixels;
    return r;
  }

  private Block block(Record r, int index) {
    int[] p = r.plan.positions[index];
    return r.origin
        .getWorld()
        .getBlockAt(
            r.origin.getBlockX() + p[0], r.origin.getBlockY() + p[1], r.origin.getBlockZ() + p[2]);
  }

  private void pin(Record r, Block block) {
    int x = block.getX() >> 4, z = block.getZ() >> 4;
    long key = (long) x << 32 | (z & 0xffffffffL);
    if (r.chunks.add(key)) {
      block.getWorld().getChunkAt(x, z).load();
      try {
        World.class
            .getMethod("addPluginChunkTicket", int.class, int.class, org.bukkit.plugin.Plugin.class)
            .invoke(block.getWorld(), x, z, plugin);
      } catch (NoSuchMethodException old) {
      } catch (ReflectiveOperationException ex) {
        throw new IllegalStateException(ex);
      }
    }
  }

  private void unpin(Record r) {
    for (long key : r.chunks)
      try {
        World.class
            .getMethod(
                "removePluginChunkTicket", int.class, int.class, org.bukkit.plugin.Plugin.class)
            .invoke(r.origin.getWorld(), (int) (key >> 32), (int) key, plugin);
      } catch (NoSuchMethodException old) {
      } catch (ReflectiveOperationException ex) {
        plugin.getLogger().warning("Chunk ticket: " + ex.getMessage());
      }
    r.chunks.clear();
  }

  public boolean holds(World world, int x, int z) {
    long key = (long) x << 32 | (z & 0xffffffffL);
    for (Record r : active.values())
      if (r.origin.getWorld().equals(world) && r.chunks.contains(key)) return true;
    return false;
  }

  public void close() {
    for (Record r : active.values()) unpin(r);
  }

  private boolean allowed(Record r, Block b, Palette.Swatch swatch, BlockState previous) {
    UUID actor = r.undoing ? r.undoActor : r.owner;
    if (actor == null) return true;
    Player player = Bukkit.getPlayer(actor);
    if (player == null || !player.isOnline()) return false;
    if (!player.hasPermission("pixelprinter.create")) return false;
    BlockPlaceEvent event =
        new BlockPlaceEvent(
            b,
            previous,
            b.getRelative(BlockFace.DOWN),
            new ItemStack(swatch == null ? Material.AIR : swatch.material),
            player,
            true);
    Bukkit.getPluginManager().callEvent(event);
    return !event.isCancelled() && event.canBuild();
  }

  public void tick() {
    tick++;
    if (active.isEmpty()) return;
    int operations = Math.max(1, Math.min(5000, limit("blocks-per-tick", 200)));
    long deadline =
        System.nanoTime() + Math.max(1, Math.min(25, limit("tick-budget-ms", 6))) * 1000000L;
    List<Record> records = new ArrayList<>(active.values());
    int idle = 0;
    while (operations > 0 && System.nanoTime() < deadline && !records.isEmpty()) {
      Record r = records.get((rotation++ & Integer.MAX_VALUE) % records.size());
      if (!active.containsKey(r.id)
          || r.paused
          || r.due > tick
          || (!r.undoing && r.owner != null && Bukkit.getPlayer(r.owner) == null)) {
        if (++idle >= records.size()) break;
        continue;
      }
      idle = 0;
      operations--;
      try {
        if (r.undoing) undoStep(r);
        else step(r);
      } catch (Exception | LinkageError ex) {
        r.failure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        finish(r);
        plugin.getLogger().warning("Travail #" + r.id + " arrêté: " + r.failure);
      }
    }
  }

  private void step(Record r) {
    int i = r.cursor;
    Block b = block(r, i);
    pin(r, b);
    Palette.Swatch desired = r.plan.frames.get(r.frame)[i];
    if (desired != null || r.plan.animated()) {
      BlockState before = b.getState();
      if (!allowed(r, b, desired, before))
        throw new IllegalStateException(
            "Placement refusé par les permissions ou la protection de zone.");
      if (r.plan.animated() && OriginalBlock.tile(before))
        throw new IllegalStateException(
            "Un GIF ne peut remplacer un coffre, panneau ou autre bloc à données.");
      if (!r.originals.containsKey(i)) {
        r.originals.put(i, before);
        if (before instanceof org.bukkit.inventory.InventoryHolder) {
          org.bukkit.inventory.Inventory inv =
              before instanceof Chest
                  ? ((Chest) before).getBlockInventory()
                  : ((org.bukkit.inventory.InventoryHolder) before).getInventory();
          ItemStack[] items = inv.getContents();
          for (int n = 0; n < items.length; n++) if (items[n] != null) items[n] = items[n].clone();
          r.inventories.put(i, items);
        }
        if (before instanceof Sign) r.signs.put(i, ((Sign) before).getLines().clone());
        if (r.plan.animated()) r.durableOriginals.put(i, OriginalBlock.capture(before));
      }
      if (desired == null) {
        r.originals.get(i).update(true, false);
      } else {
        Palette.Swatch previous = r.frame == 0 ? null : r.plan.frames.get(r.frame - 1)[i];
        if (r.frame == 0 || desired != previous || b.getType() != desired.material)
          Compat.set(b, desired);
      }
    }
    r.cursor++;
    if (r.cursor == r.plan.positions.length) {
      if (r.plan.animated()) {
        r.cursor = 0;
        r.due = tick + r.plan.delays[r.frame];
        r.frame = (r.frame + 1) % r.plan.frames.size();
        if (!r.savedFirstFrame && r.descriptor != null) {
          r.savedFirstFrame = true;
          plugin.saveAnimations();
        }
      } else finish(r);
    }
  }

  private void finish(Record r) {
    active.remove(r.id);
    r.done = true;
    if (r.failure == null && r.onComplete != null) {
      Runnable callback = r.onComplete;
      r.onComplete = null;
      callback.run();
    }
    unpin(r);
    Deque<Record> h = history.computeIfAbsent(r.owner, k -> new ArrayDeque<>());
    h.addFirst(r);
    while (h.size() > Math.max(1, limit("undo-history", 5))) release(h.removeLast());
    Player player = r.owner == null ? null : Bukkit.getPlayer(r.owner);
    if (player != null)
      plugin.message(
          player,
          r.failure == null
              ? "Travail #" + r.id + " terminé (" + r.originals.size() + " blocs). /pp undo " + r.id
              : "Travail #" + r.id + " interrompu: " + r.failure + ". /pp undo " + r.id);
    plugin.saveAnimations();
  }

  public void stop(Record r) {
    if (r.undoing) throw new IllegalArgumentException("Annulation déjà en cours.");
    r.onComplete = null;
    finish(r);
  }

  public Record undo(UUID owner, Integer id, boolean admin) {
    Record selected = null;
    Deque<Record> h = history.get(owner);
    if (id != null) {
      selected = active.get(id);
      if (selected == null) {
        for (Deque<Record> list : history.values())
          for (Record r : list) if (r.id == id) selected = r;
      }
    } else if (h != null && !h.isEmpty()) selected = h.peekFirst();
    if (selected == null) throw new IllegalArgumentException("Aucun travail à annuler.");
    if (!admin && !Objects.equals(owner, selected.owner))
      throw new IllegalArgumentException("Ce travail appartient à un autre joueur.");
    if (selected.undoing) throw new IllegalArgumentException("Annulation déjà en cours.");
    if (!active.containsKey(selected.id) && active.size() >= limit("max-jobs", 8))
      throw new IllegalArgumentException("Trop de travaux actifs.");
    for (Deque<Record> list : history.values()) list.remove(selected);
    selected.undoing = true;
    selected.undoActor = owner;
    selected.paused = false;
    selected.done = false;
    selected.undo = new ArrayList<>(selected.originals.values());
    selected.undoIndices = new ArrayList<>(selected.originals.keySet());
    selected.undoCursor = 0;
    active.put(selected.id, selected);
    // Frames may have unloaded since placement, especially at chunk borders.
    for (Location location : selected.entities.values()) pin(selected, location.getBlock());
    for (Entity entity : selected.origin.getWorld().getEntities())
      if (selected.entities.containsKey(entity.getUniqueId())) entity.remove();
    plugin.saveAnimations();
    return selected;
  }

  private void undoStep(Record r) {
    if (r.undoCursor < r.undo.size()) {
      BlockState state = r.undo.get(r.undoCursor);
      pin(r, state.getBlock());
      if (r.undoActor != null) {
        Player player = Bukkit.getPlayer(r.undoActor);
        if (player == null) return;
        Palette.Swatch dummy = new Palette.Swatch(state.getType(), 0, 0);
        if (!allowed(r, state.getBlock(), dummy, state.getBlock().getState())) {
          r.paused = true;
          plugin.message(player, "Annulation suspendue: protection de zone. /pp resume " + r.id);
          return;
        }
      }
      if (!state.update(true, false))
        throw new IllegalStateException("Bloc impossible à restaurer.");
      Integer index = r.undoIndices.get(r.undoCursor);
      if (index != null) {
        BlockState restored = state.getBlock().getState();
        ItemStack[] items = r.inventories.get(index);
        if (items != null && restored instanceof org.bukkit.inventory.InventoryHolder) {
          org.bukkit.inventory.Inventory inv =
              restored instanceof Chest
                  ? ((Chest) restored).getBlockInventory()
                  : ((org.bukkit.inventory.InventoryHolder) restored).getInventory();
          inv.setContents(items);
        }
        String[] lines = r.signs.get(index);
        if (lines != null && restored instanceof Sign) {
          for (int line = 0; line < 4; line++) ((Sign) restored).setLine(line, lines[line]);
          restored.update(true, false);
        }
      }
      r.undoCursor++;
    }
    if (r.undoCursor >= r.undo.size()) {
      active.remove(r.id);
      r.done = true;
      unpin(r);
      release(r);
      Player player = r.owner == null ? null : Bukkit.getPlayer(r.owner);
      if (player != null) plugin.message(player, "Travail #" + r.id + " annulé.");
    }
  }

  private void release(Record r) {
    snapshots -= r.plan.positions.length;
    framePixels -= (long) r.plan.positions.length * r.plan.frames.size();
    r.originals.clear();
    r.durableOriginals.clear();
    r.inventories.clear();
    r.signs.clear();
  }

  public void clear(UUID owner) {
    Deque<Record> h = history.remove(owner);
    if (h != null) for (Record r : h) release(r);
  }

  public int reserved() {
    return snapshots;
  }

  public List<Integer> history(UUID owner) {
    List<Integer> ids = new ArrayList<>();
    Deque<Record> h = history.get(owner);
    if (h != null) for (Record r : h) ids.add(r.id);
    return ids;
  }
}
