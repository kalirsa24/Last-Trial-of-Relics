package io.github.kalirsa24.lasttrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Защита арены от посторонних рук.
 *
 * Пока забег идёт, зона площадки — «чужой дом»: ломать и ставить блоки в ней
 * может только владелец арены или оператор. Это закрывает главный сценарий
 * гриферства на публичном сервере: зайти на чужую арену во время волны,
 * разобрать пол или застроить алтарь.
 *
 * Зона считается по горизонтали ({@link ArenaValidator#RADIUS} + 4) и по
 * высоте (±8 от Реликвии), чтобы не блокировать всю округу.
 */
public final class ArenaGuard {

	private static final int HORIZONTAL_MARGIN = 4;
	private static final int VERTICAL_MARGIN = 8;

	private ArenaGuard() {
	}

	/** Идущий забег, которому принадлежит эта точка, либо {@code null}. */
	public static Trial runningTrial(ServerLevel level, BlockPos pos) {
		for (Trial trial : TrialManager.all()) {
			if (trial.level() != level || !trial.isRunning()) {
				continue;
			}
			BlockPos relic = trial.relicPos();
			if (Math.abs(relic.getX() - pos.getX()) <= ArenaValidator.RADIUS + HORIZONTAL_MARGIN
					&& Math.abs(relic.getZ() - pos.getZ()) <= ArenaValidator.RADIUS + HORIZONTAL_MARGIN
					&& Math.abs(relic.getY() - pos.getY()) <= VERTICAL_MARGIN) {
				return trial;
			}
		}
		return null;
	}

	/** Можно ли игроку менять блок в этой точке. */
	public static boolean canModify(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!FestivalConfig.get().protectArena) {
			return true;
		}
		Trial trial = runningTrial(level, pos);
		if (trial == null) {
			return true;
		}
		if (player.hasPermissions(2)) {
			return true;
		}
		return trial.owner().equals(player.getUUID());
	}
}
