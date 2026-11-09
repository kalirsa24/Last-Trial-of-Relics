package com.example.festival.core;

import com.example.festival.FestivalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Реестр забегов: по одному на владельца.
 *
 * Раньше «активным» считался единственный забег в статике — второй игрок
 * ломал первому волны. Теперь забеги живут в карте и тикают независимо.
 */
public final class TrialManager {

	/** Потолок одновременных забегов по умолчанию (переопределяется конфигом). */
	public static final int MAX_ACTIVE = 3;

	private static final Map<UUID, Trial> BY_OWNER = new LinkedHashMap<>();

	private TrialManager() {
	}

	/** Достигнут ли потолок одновременных забегов. */
	public static boolean isFull() {
		int limit = FestivalConfig.get().maxActiveTrials;
		return BY_OWNER.size() >= (limit > 0 ? limit : MAX_ACTIVE);
	}

	/** Вызывается каждый серверный тик. */
	public static void tick() {
		if (BY_OWNER.isEmpty()) {
			return;
		}
		for (Trial trial : List.copyOf(BY_OWNER.values())) {
			try {
				trial.tick();
			} catch (RuntimeException e) {
				// Один сломанный забег не должен останавливать остальные:
				// убираем мобов, прячем HUD и снимаем забег
				FestivalMod.LOGGER.error("[Festival] Забег {} упал и снят", trial.ownerName(), e);
				try {
					trial.stop();
				} catch (RuntimeException ignored) {
					BY_OWNER.remove(trial.owner(), trial);
				}
			}
		}
	}

	public static Trial of(ServerPlayer player) {
		return player == null ? null : BY_OWNER.get(player.getUUID());
	}

	public static Trial of(UUID owner) {
		return owner == null ? null : BY_OWNER.get(owner);
	}

	/** Забег по блоку Реликвии. */
	public static Trial at(ServerLevel level, BlockPos pos) {
		for (Trial trial : BY_OWNER.values()) {
			if (trial.level() == level && trial.relicPos().equals(pos)) {
				return trial;
			}
		}
		return null;
	}

	/** Забег, которому принадлежит моб (по метке владельца). */
	public static Trial forMob(Entity entity) {
		return of(TrialTags.ownerOf(entity));
	}

	public static Trial start(ServerLevel level, BlockPos pos, RelicTier tier, ServerPlayer starter) {
		Trial existing = BY_OWNER.get(starter.getUUID());
		if (existing != null) {
			return existing;
		}
		if (at(level, pos) != null) {
			return null;
		}
		// Три забега — предел: дальше сервер начинает «тонуть» в мобах
		if (isFull()) {
			return null;
		}
		Trial trial = Trial.create(level, pos, tier, starter);
		BY_OWNER.put(trial.owner(), trial);
		return trial;
	}

	/** Поднять забег из сохранения Реликвии после перезапуска сервера. */
	public static void restore(ServerLevel level, BlockPos pos, RelicTier tier, Trial.SavedState saved) {
		if (saved == null || BY_OWNER.containsKey(saved.owner()) || at(level, pos) != null) {
			return;
		}
		Trial trial = Trial.restore(level, pos, tier, saved);
		BY_OWNER.put(trial.owner(), trial);
	}

	public static void remove(Trial trial) {
		BY_OWNER.remove(trial.owner(), trial);
	}

	public static void stopAt(ServerLevel level, BlockPos pos) {
		Trial trial = at(level, pos);
		if (trial != null) {
			trial.stop();
		}
	}

	public static void stopAll() {
		for (Trial trial : List.copyOf(BY_OWNER.values())) {
			try {
				trial.stop();
			} catch (RuntimeException e) {
				FestivalMod.LOGGER.warn("[Festival] Забег {} не остановился штатно: {}",
						trial.ownerName(), e.getMessage());
			}
		}
		BY_OWNER.clear();
	}

	public static boolean isRunningIn(ServerLevel level) {
		for (Trial trial : BY_OWNER.values()) {
			if (trial.level() == level && trial.isRunning()) {
				return true;
			}
		}
		return false;
	}

	/** Идёт ли хоть один забег (для мелочей вроде блокировки стрельбы). */
	public static boolean anyRunning() {
		for (Trial trial : BY_OWNER.values()) {
			if (trial.isRunning()) {
				return true;
			}
		}
		return false;
	}

	public static Collection<Trial> all() {
		return List.copyOf(BY_OWNER.values());
	}
}
