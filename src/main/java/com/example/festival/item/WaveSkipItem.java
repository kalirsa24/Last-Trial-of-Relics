package com.example.festival.item;

import com.example.festival.core.GameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Пропуск волн: перескакивает 3 волны, но следующая приходит усиленной
 * (вдвое больше мобов, +20% к характеристикам).
 */
public class WaveSkipItem extends Item {

	public WaveSkipItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!GameManager.requestSkip(serverPlayer)) {
			return InteractionResultHolder.fail(stack);
		}
		player.getItemInHand(hand).shrink(1);
		return InteractionResultHolder.consume(stack);
	}
}
