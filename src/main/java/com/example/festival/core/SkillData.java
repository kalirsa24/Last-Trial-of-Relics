package com.example.festival.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Постоянные вложения наград празднества: уровни навыков по игрокам.
 * Хранится в данных мира, переживает перезапуск сервера.
 */
public class SkillData extends SavedData {

	public static final String NAME = "festival_skills";

	private final Map<UUID, int[]> levels = new HashMap<>();
	/** Сколько наград игрок уже вложил в древо (влияет на цены магазина). */
	private final Map<UUID, Integer> spent = new HashMap<>();
	/** Кому уже переносили старые награды в персональный формат. */
	private final Set<UUID> migrated = new HashSet<>();

	public SkillData() {
	}

	// ------------------------------------------------------------------
	// Сериализация
	// ------------------------------------------------------------------

	public static SkillData load(CompoundTag tag) {
		SkillData data = new SkillData();
		CompoundTag players = tag.getCompound("players");
		for (String key : players.getAllKeys()) {
			try {
				UUID uuid = UUID.fromString(key);
				CompoundTag entry = players.getCompound(key);
				int[] values = new int[SkillManager.Skill.values().length];
				for (SkillManager.Skill skill : SkillManager.Skill.values()) {
					values[skill.ordinal()] = entry.getInt(skill.id());
				}
				data.levels.put(uuid, values);
				data.spent.put(uuid, entry.getInt("spent"));
			} catch (IllegalArgumentException ignored) {
				// битая запись — пропускаем
			}
		}
		ListTag migratedList = tag.getList("migrated", Tag.TAG_STRING);
		for (int i = 0; i < migratedList.size(); i++) {
			try {
				data.migrated.add(UUID.fromString(migratedList.getString(i)));
			} catch (IllegalArgumentException ignored) {
				// битая запись — пропускаем
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		CompoundTag players = new CompoundTag();
		levels.forEach((uuid, values) -> {
			CompoundTag entry = new CompoundTag();
			for (SkillManager.Skill skill : SkillManager.Skill.values()) {
				entry.putInt(skill.id(), values[skill.ordinal()]);
			}
			entry.putInt("spent", spent.getOrDefault(uuid, 0));
			players.put(uuid.toString(), entry);
		});
		tag.put("players", players);

		ListTag migratedList = new ListTag();
		for (UUID uuid : migrated) {
			migratedList.add(StringTag.valueOf(uuid.toString()));
		}
		tag.put("migrated", migratedList);
		return tag;
	}

	// ------------------------------------------------------------------
	// Доступ
	// ------------------------------------------------------------------

	public int[] levelsOf(UUID player) {
		int[] stored = levels.get(player);
		if (stored == null) {
			return new int[SkillManager.Skill.values().length];
		}
		return stored.clone();
	}

	public int levelOf(UUID player, SkillManager.Skill skill) {
		int[] stored = levels.get(player);
		return stored == null ? 0 : stored[skill.ordinal()];
	}

	public void setLevel(UUID player, SkillManager.Skill skill, int level) {
		int[] stored = levels.computeIfAbsent(player,
				key -> new int[SkillManager.Skill.values().length]);
		stored[skill.ordinal()] = Math.max(0, Math.min(skill.maxLevel(), level));
		setDirty();
	}

	public int spentOf(UUID player) {
		return spent.getOrDefault(player, 0);
	}

	public void addSpent(UUID player, int amount) {
		spent.merge(player, Math.max(0, amount), Integer::sum);
		setDirty();
	}

	public boolean isMigrated(UUID player) {
		return migrated.contains(player);
	}

	public void markMigrated(UUID player) {
		if (migrated.add(player)) {
			setDirty();
		}
	}
}
