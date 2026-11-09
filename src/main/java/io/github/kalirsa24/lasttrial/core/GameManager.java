package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.core.Msg;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Публичный фасад испытания для команд, предметов и блоков.
 *
 * Само состояние живёт в {@link Trial}; здесь только «как игрок начинает,
 * останавливает и вызывает волну». Никаких статических полей забега.
 */
public final class GameManager {

	/** Жизни Реликвии: каждое разрушение — минус одна. */
	public static final int MAX_LIVES = 5;

	private GameManager() {
	}

	public static Trial trialOf(ServerPlayer player) {
		return TrialManager.of(player);
	}

	/**
	 * Запрос игрока (клавиша V или {@code /lasttrial start}): запустить испытание
	 * у Реликвии, вызвать следующий этап или следующую волну.
	 */
	public static boolean requestStart(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();

		Trial running = TrialManager.of(player);
		if (running != null) {
			if (running.callNextWave()) {
				return true;
			}
			int seconds = running.intermissionSeconds();
			Msg.overlay(player, seconds > 0
					? Component.translatable("message.lasttrial.intermission", seconds)
					: Component.translatable("message.lasttrial.wave_in_progress"));
			return false;
		}

		BlockPos relicPos = findRelic(player);
		if (relicPos == null) {
			Msg.overlay(player, Component.translatable("message.lasttrial.no_relic"));
			return false;
		}

		// Перед стартом площадка должна быть целой на 100%
		ArenaValidator.Result check = ArenaValidator.validate(level, relicPos, 1.0D);
		if (!check.ok()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.arena_damaged",
					Math.round(check.floorRatio() * 100.0D)));
			return false;
		}

		RelicTier tier = level.getBlockState(relicPos).getBlock()
				instanceof io.github.kalirsa24.lasttrial.block.RelicBlock relic ? relic.getTier() : RelicTier.GREATER;
		Trial started = TrialManager.start(level, relicPos, tier, player);
		if (started == null) {
			Msg.overlay(player, Component.translatable(TrialManager.isFull()
					? "message.lasttrial.limit_reached" : "message.lasttrial.already_running",
					FestivalConfig.get().maxActiveTrials));
			return false;
		}
		return true;
	}

	/** Пропуск волн предметом: 3 волны вперёд, следующая — усиленная. */
	public static boolean requestSkip(ServerPlayer player) {
		Trial trial = TrialManager.of(player);
		if (trial == null) {
			Msg.overlay(player, Component.translatable("message.lasttrial.skip_no_trial"));
			return false;
		}
		return trial.skipWaves(3, true);
	}

	/**
	 * Принудительный старт для операторов: у Реликвии, на которую смотрит игрок.
	 * Без блока Реликвии забег не имеет смысла — алтарю нужен носитель.
	 */
	public static Trial forceStart(ServerPlayer player, RelicTier tier) {
		BlockPos pos = findRelic(player);
		if (pos == null) {
			Msg.overlay(player, Component.translatable("message.lasttrial.no_relic"));
			return null;
		}
		return TrialManager.start((ServerLevel) player.level(), pos, tier, player);
	}

	public static void stop(ServerPlayer player) {
		Trial trial = TrialManager.of(player);
		if (trial != null) {
			trial.stop();
		}
	}

	public static void stopAll() {
		TrialManager.stopAll();
	}

	public static int getLives(ServerPlayer player) {
		Trial trial = TrialManager.of(player);
		return trial == null ? 0 : trial.lives();
	}

	/** Реликвия, на которую смотрит игрок, либо ближайшая в радиусе 8 блоков. */
	private static BlockPos findRelic(ServerPlayer player) {
		var hit = player.pick(5.0D, 0.0F, false);
		if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
				&& player.level().getBlockState(blockHit.getBlockPos()).getBlock()
				instanceof io.github.kalirsa24.lasttrial.block.RelicBlock) {
			return blockHit.getBlockPos();
		}

		BlockPos origin = player.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;

		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -4, -8), origin.offset(8, 4, 8))) {
			if (player.level().getBlockState(pos).getBlock()
					instanceof io.github.kalirsa24.lasttrial.block.RelicBlock) {
				double distance = pos.distSqr(origin);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.immutable();
				}
			}
		}
		return best;
	}
}
