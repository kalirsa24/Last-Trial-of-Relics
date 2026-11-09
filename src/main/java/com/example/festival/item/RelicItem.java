package com.example.festival.item;

import com.example.festival.core.ArenaManager;
import com.example.festival.core.ArenaValidator;
import com.example.festival.core.Msg;
import com.example.festival.core.RelicBonus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Предмет Реликвии: ставится только на подготовленную площадку.
 *
 * Обязательна платформа 20×20 сплошного пола — без неё событие не запустить.
 * Платформа 30×30 даёт бонус: лишнюю монету за волну и награду по победе.
 * В подсказке показываются проценты готовности, если игрок смотрит на Реликвию.
 */
public class RelicItem extends BlockItem {

	public RelicItem(net.minecraft.world.level.block.Block block, Item.Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult place(BlockPlaceContext context) {
		Level level = context.getLevel();

		if (level instanceof ServerLevel serverLevel && context.getPlayer() instanceof ServerPlayer player) {
			BlockPos pos = context.getClickedPos();
			ArenaValidator.Result result = ArenaManager.tryRegister(serverLevel, pos, player);

			if (!result.ok()) {
				Msg.overlay(player, Component.translatable(result.reason(),
						percent(result.floorRatio()), percent(result.skyRatio())));
				return InteractionResult.FAIL;
			}
		}

		InteractionResult placed = super.place(context);

		if (placed.consumesAction() && level instanceof ServerLevel serverLevel) {
			ArenaManager.snapshotFloor(serverLevel, context.getClickedPos());
		}
		return placed;
	}

	private static String percent(double value) {
		return Math.round(value * 100.0D) + "%";
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level,
								List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);

		double value = RelicBonus.valueOf(this);
		if (value > 0.0D) {
			tooltip.add(Component.translatable("tooltip.festival.relic_bonus",
					Math.round(value * 100.0D)).withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.translatable("tooltip.festival.relic_bonus_hint")
					.withStyle(ChatFormatting.DARK_GRAY));
		}

		tooltip.add(Component.translatable("tooltip.festival.arena_required",
				ArenaValidator.REQUIRED_SIZE).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.festival.arena_bonus",
				ArenaValidator.BONUS_SIZE).withStyle(ChatFormatting.DARK_GRAY));

		if (level != null && level.isClientSide() && ARENA_STATUS != null) {
			ARENA_STATUS.accept(tooltip);
		}
	}

	/**
	 * Клиентский поставщик строк о готовности площадки: рендер и рейкаст живут
	 * в клиентском сорсете, основной код только вызывает его, если он есть.
	 */
	public static void setArenaStatusProvider(java.util.function.Consumer<List<Component>> provider) {
		ARENA_STATUS = provider;
	}

	private static java.util.function.Consumer<List<Component>> ARENA_STATUS;
}
