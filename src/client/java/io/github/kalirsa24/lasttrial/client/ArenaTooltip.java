package io.github.kalirsa24.lasttrial.client;

import io.github.kalirsa24.lasttrial.block.RelicBlock;
import io.github.kalirsa24.lasttrial.core.ArenaValidator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Подсказка Реликвии: показывает проценты готовности площадки, на которую
 * смотрит игрок. Живёт в клиентском сорсете, потому что использует рейкаст
 * и клиентский уровень.
 */
public final class ArenaTooltip {

	/** Кэш: подсказка перерисовывается каждый кадр, а валидация читает ~100k блоков. */
	private static final long CACHE_MS = 500L;
	private static BlockPos cachedPos;
	private static long cachedAt;
	private static ArenaValidator.Result cached;

	private ArenaTooltip() {
	}

	public static void append(List<Component> tooltip) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) {
			return;
		}
		var hit = minecraft.player.pick(6.0D, 0.0F, false);
		if (!(hit instanceof BlockHitResult blockHit)) {
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		if (!(minecraft.level.getBlockState(pos).getBlock() instanceof RelicBlock)) {
			return;
		}

		ArenaValidator.Result result = cachedResult(minecraft.level, pos);
		boolean ready = result.floorRatio() >= 1.0D;

		tooltip.add(Component.translatable("tooltip.lasttrial.arena_core_status",
						percent(result.floorRatio()), percent(1.0D))
				.withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.RED));
		tooltip.add(Component.translatable(result.bonus()
						? "tooltip.lasttrial.arena_bonus_yes" : "tooltip.lasttrial.arena_bonus_no",
						percent(result.bonusRatio()), percent(1.0D))
				.withStyle(result.bonus() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	private static ArenaValidator.Result cachedResult(net.minecraft.world.level.Level level, BlockPos pos) {
		long now = System.currentTimeMillis();
		if (cached == null || !pos.equals(cachedPos) || now - cachedAt > CACHE_MS) {
			cached = ArenaValidator.validate(level, pos, 0.0D);
			cachedPos = pos.immutable();
			cachedAt = now;
		}
		return cached;
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}
}
