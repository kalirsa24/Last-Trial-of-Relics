package io.github.kalirsa24.lasttrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Арены мира и персональные настройки испытаний.
 * Арена принадлежит тому, кто её поставил; одному игроку — одна арена.
 *
 * Арена помнит измерение: без этого одинаковые координаты в Обычном мире и
 * Нижнем мире считались бы одной ареной, а снимок пола затирался бы чужим.
 * Старые сохранения без поля {@code dim} читаются как Обычный мир.
 *
 * В 1.20.1 {@link SavedData} сериализуется вручную в {@link CompoundTag}
 * (в 26.2 это был codec-based SavedDataType).
 */
public class ArenaData extends SavedData {

	public static final String NAME = "lasttrial_arenas";
	private static final String DEFAULT_DIMENSION = "minecraft:overworld";

	public record Arena(BlockPos pos, UUID owner, String ownerName, String dimension) {

		public Arena {
			if (dimension == null || dimension.isBlank()) {
				dimension = DEFAULT_DIMENSION;
			}
		}

		public boolean inDimension(String other) {
			return dimension.equals(other);
		}
	}

	private final List<Arena> arenas;
	private final Map<String, Integer> waveOverrides;

	public ArenaData() {
		this(List.of(), Map.of());
	}

	public ArenaData(List<Arena> arenas, Map<String, Integer> waveOverrides) {
		this.arenas = new ArrayList<>(arenas);
		this.waveOverrides = new HashMap<>(waveOverrides);
	}

	// ------------------------------------------------------------------
	// Сериализация
	// ------------------------------------------------------------------

	public static ArenaData load(CompoundTag tag) {
		List<Arena> arenas = new ArrayList<>();
		ListTag list = tag.getList("arenas", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			try {
				BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
				UUID owner = UUID.fromString(entry.getString("owner"));
				String dimension = entry.contains("dim") ? entry.getString("dim") : DEFAULT_DIMENSION;
				arenas.add(new Arena(pos, owner, entry.getString("name"), dimension));
			} catch (IllegalArgumentException ignored) {
				// битая запись — пропускаем, остальные арены живут
			}
		}

		Map<String, Integer> waves = new HashMap<>();
		CompoundTag waveTag = tag.getCompound("waves");
		for (String key : waveTag.getAllKeys()) {
			waves.put(key, waveTag.getInt(key));
		}
		return new ArenaData(arenas, waves);
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		for (Arena arena : arenas) {
			CompoundTag entry = new CompoundTag();
			entry.putInt("x", arena.pos().getX());
			entry.putInt("y", arena.pos().getY());
			entry.putInt("z", arena.pos().getZ());
			entry.putString("owner", arena.owner().toString());
			entry.putString("name", arena.ownerName());
			entry.putString("dim", arena.dimension());
			list.add(entry);
		}
		tag.put("arenas", list);

		CompoundTag waves = new CompoundTag();
		waveOverrides.forEach(waves::putInt);
		tag.put("waves", waves);
		return tag;
	}

	// ------------------------------------------------------------------
	// Арены
	// ------------------------------------------------------------------

	public List<Arena> arenas() {
		return List.copyOf(arenas);
	}

	public Optional<Arena> ownedBy(UUID owner) {
		return arenas.stream().filter(arena -> arena.owner().equals(owner)).findFirst();
	}

	/** Арена в этом измерении рядом с точкой. */
	public Optional<Arena> at(BlockPos pos, int radius, String dimension) {
		return arenas.stream()
				.filter(arena -> arena.inDimension(dimension))
				.filter(arena -> Math.abs(arena.pos().getX() - pos.getX()) <= radius
						&& Math.abs(arena.pos().getZ() - pos.getZ()) <= radius)
				.findFirst();
	}

	public void addArena(Arena arena) {
		arenas.add(arena);
		setDirty();
	}

	/** Убрать арену владельца. Возвращает true, если что-то убрали. */
	public boolean removeOwned(UUID owner) {
		boolean removed = arenas.removeIf(arena -> arena.owner().equals(owner));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public boolean removeAt(BlockPos pos, int radius, String dimension) {
		boolean removed = arenas.removeIf(arena -> arena.inDimension(dimension)
				&& Math.abs(arena.pos().getX() - pos.getX()) <= radius
				&& Math.abs(arena.pos().getZ() - pos.getZ()) <= radius);
		if (removed) {
			setDirty();
		}
		return removed;
	}

	// ------------------------------------------------------------------
	// Персональное число волн (админская настройка)
	// ------------------------------------------------------------------

	public Map<String, Integer> waveOverrides() {
		return Map.copyOf(waveOverrides);
	}

	public int waveOverride(UUID player, int fallback) {
		return waveOverrides.getOrDefault(player.toString(), fallback);
	}

	public void setWaveOverride(UUID player, int waves) {
		waveOverrides.put(player.toString(), waves);
		setDirty();
	}

	public void clearWaveOverride(UUID player) {
		if (waveOverrides.remove(player.toString()) != null) {
			setDirty();
		}
	}

	/** Id измерения для записи арены. */
	public static String dimensionId(Level level) {
		ResourceLocation id = level.dimension().location();
		return id.toString();
	}
}
