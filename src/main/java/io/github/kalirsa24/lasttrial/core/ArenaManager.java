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
 *
 * Данные лежат в Обычном мире ({@code getServer().overworld()}), а не в том
 * измерении, где игрок поставил блок: иначе арены из Нижнего мира и Обычного
 * мира оказывались бы в разных файлах и не видели друг друга. Принадлежность
 * к измерению хранится в самой записи арены.
 */
public class ArenaManager {

	/** Снимки пола по аренам: ключ — измерение + позиция Реликвии. */
	private static final Map<String, Map<BlockPos, BlockState>> FLOORS = new HashMap<>();

	private static ArenaData data = null;

	// ------------------------------------------------------------------
	// Загрузка
	// ------------------------------------------------------------------

	public static void ensureLoaded(ServerLevel level) {
		if (data != null) {
			return;
		}
		ServerLevel storage = level.getServer() == null ? level : level.getServer().overworld();
		data = storage.getDataStorage().computeIfAbsent(ArenaData::load, ArenaData::new, ArenaData.NAME);
	}

	public static void resetCache() {
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

	/** Арена в этом измерении рядом с точкой. */
	public static Optional<ArenaData.Arena> arenaAt(ServerLevel level, BlockPos pos) {
		if (data == null) {
			return Optional.empty();
		}
		return data.at(pos, ArenaValidator.RADIUS + 4, ArenaData.dimensionId(level));
	}

	public static boolean isInside(ServerLevel level, BlockPos pos) {
		return arenaAt(level, pos).isPresent();
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
			return new ArenaValidator.Result(false, "arena.lasttrial.already_owned",
					1.0D, 1.0D, 1.0D, false);
		}
		if (arenaAt(level, pos).isPresent()) {
			return new ArenaValidator.Result(false, "arena.lasttrial.occupied",
					1.0D, 1.0D, 1.0D, false);
		}

		ArenaValidator.Result result = ArenaValidator.validate(level, pos);
		if (result.ok()) {
			data().addArena(new ArenaData.Arena(pos, player.getUUID(), player.getScoreboardName(),
					ArenaData.dimensionId(level)));
		}
		return result;
	}

	/** Снять свою арену. */
	public static boolean removeOwn(ServerLevel level, ServerPlayer player) {
		ensureLoaded(level);
		arenaOf(player.getUUID()).ifPresent(arena -> FLOORS.remove(floorKey(arena)));
		return data().removeOwned(player.getUUID());
	}

	/** Снять арену игрока (для админа). */
	public static boolean removeOf(ServerLevel level, UUID owner) {
		ensureLoaded(level);
		arenaOf(owner).ifPresent(arena -> FLOORS.remove(floorKey(arena)));
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
		FLOORS.put(floorKey(level, center), floor);
	}

	/** Заделать дыры конкретной арены (не трогая чужие). */
	public static void restoreFloor(ServerLevel level, BlockPos center) {
		Map<BlockPos, BlockState> floor = FLOORS.get(floorKey(level, center));
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
	public static void clearFloor(ServerLevel level, BlockPos center) {
		FLOORS.remove(floorKey(level, center));
	}

	private static String floorKey(ServerLevel level, BlockPos pos) {
		return ArenaData.dimensionId(level) + "|" + pos.asLong();
	}

	private static String floorKey(ArenaData.Arena arena) {
		return arena.dimension() + "|" + arena.pos().asLong();
	}
}
