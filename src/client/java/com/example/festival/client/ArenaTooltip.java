package com.example.festival.client;

import com.example.festival.block.RelicBlock;
import com.example.festival.core.ArenaValidator;
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

		ArenaValidator.Result result = ArenaValidator.validate(minecraft.level, pos, 0.0D);
		boolean ready = result.floorRatio() >= 1.0D;

		tooltip.add(Component.translatable("tooltip.festival.arena_core_status",
						percent(result.floorRatio()), percent(1.0D))
				.withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.RED));
		tooltip.add(Component.translatable(result.bonus()
						? "tooltip.festival.arena_bonus_yes" : "tooltip.festival.arena_bonus_no",
						percent(result.bonusRatio()), percent(1.0D))
				.withStyle(result.bonus() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}
}
