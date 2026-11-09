package io.github.kalirsa24.lasttrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Арены мира: у каждой есть владелец, одному игроку — одна арена.
 * Админ может снять чужую арену.
 */
public class ArenaManager {

	/** Пол арены по центру (позиции Реликвии): у каждого забега свой снимок. */
	private static final Map<BlockPos, Map<BlockPos, BlockState>> FLOORS = new HashMap<>();

	private static ArenaData data = null;
	private static boolean loaded = false;

	// ------------------------------------------------------------------
	// Загрузка
	// ------------------------------------------------------------------

	public static void ensureLoaded(ServerLevel level) {
		if (loaded && data != null) {
			return;
		}
		data = level.getDataStorage().computeIfAbsent(ArenaData::load, ArenaData::new, ArenaData.NAME);
		loaded = true;
	}

	public static void resetCache() {
		loaded = false;
		data = null;
		FLOORS.clear();
	}

	private static ArenaData data() {
		return data;
	}

	public static List<ArenaData.Arena> arenas() {
		return data == null ? List.of() : data.arenas();
	}

	public static Optional<ArenaData.Arena> arenaOf(UUID owner) {
		return data == null ? Optional.empty() : data.ownedBy(owner);
	}

	public static Optional<ArenaData.Arena> arenaAt(BlockPos pos) {
		return data == null ? Optional.empty() : data.at(pos, ArenaValidator.RADIUS + 4);
	}

	public static boolean isInside(BlockPos pos) {
		return arenaAt(pos).isPresent();
	}

	// ------------------------------------------------------------------
	// Регистрация / снятие
	// ------------------------------------------------------------------

	/**
	 * Проверить место и закрепить арену за игроком.
	 * Одна арена на игрока, чужие арены занимать нельзя.
	 */
	public static ArenaValidator.Result tryRegister(ServerLevel level, BlockPos pos, ServerPlayer player) {
		ensureLoaded(level);

		if (arenaOf(player.getUUID()).isPresent()) {
			return new ArenaValidator.Result(false, "arena.lasttrial.already_owned", 1.0D, 1.0D, 1.0D, false);
		}
		if (arenaAt(pos).isPresent()) {
			return new ArenaValidator.Result(false, "arena.lasttrial.occupied", 1.0D, 1.0D, 1.0D, false);
		}

		ArenaValidator.Result result = ArenaValidator.validate(level, pos);
		if (result.ok()) {
			data().addArena(new ArenaData.Arena(pos, player.getUUID(), player.getScoreboardName()));
		}
		return result;
	}

	/** Снять свою арену. */
	public static boolean removeOwn(ServerLevel level, ServerPlayer player) {
		ensureLoaded(level);
		arenaOf(player.getUUID()).ifPresent(arena -> FLOORS.remove(arena.pos()));
		return data().removeOwned(player.getUUID());
	}

	/** Снять арену игрока (для админа). */
	public static boolean removeOf(ServerLevel level, UUID owner) {
		ensureLoaded(level);
		arenaOf(owner).ifPresent(arena -> FLOORS.remove(arena.pos()));
		return data().removeOwned(owner);
	}

	public static int waveOverride(UUID player, int fallback) {
		return data == null ? fallback : data.waveOverride(player, fallback);
	}

	public static void setWaveOverride(UUID player, int waves) {
		if (data != null) {
			data.setWaveOverride(player, waves);
		}
	}

	public static void clearWaveOverride(UUID player) {
		if (data != null) {
			data.clearWaveOverride(player);
		}
	}

	// ------------------------------------------------------------------
	// Пол арены
	// ------------------------------------------------------------------

	/** Запомнить пол вокруг Реликвии — чтобы заделывать дыры от взрывов. */
	public static void snapshotFloor(ServerLevel level, BlockPos center) {
		Map<BlockPos, BlockState> floor = new HashMap<>();
		int floorY = center.getY() - 1;
		for (int dx = -ArenaValidator.RADIUS; dx < ArenaValidator.RADIUS; dx++) {
			for (int dz = -ArenaValidator.RADIUS; dz < ArenaValidator.RADIUS; dz++) {
				BlockPos pos = new BlockPos(center.getX() + dx, floorY, center.getZ() + dz);
				BlockState state = level.getBlockState(pos);
				if (!state.isAir()) {
					floor.put(pos, state);
				}
			}
		}
		FLOORS.put(center.immutable(), floor);
	}

	/** Заделать дыры конкретной арены (не трогая чужие). */
	public static void restoreFloor(ServerLevel level, BlockPos center) {
		Map<BlockPos, BlockState> floor = FLOORS.get(center);
		if (floor == null) {
			return;
		}
		for (Map.Entry<BlockPos, BlockState> entry : floor.entrySet()) {
			if (level.getBlockState(entry.getKey()).isAir()) {
				level.setBlockAndUpdate(entry.getKey(), entry.getValue());
			}
		}
	}

	/** Снять снимок пола (арена убрана или забег закончился). */
	public static void clearFloor(BlockPos center) {
		FLOORS.remove(center);
	}
}
