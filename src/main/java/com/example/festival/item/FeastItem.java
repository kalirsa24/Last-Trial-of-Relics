package com.example.festival.item;

import com.example.festival.core.Msg;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Пир празднества — награда для открытого мира.
 *
 * Съедается в любой момент и на 3 минуты даёт:
 * регенерацию II, сопротивление II и +6 сердец (поглощение).
 */
public class FeastItem extends Item {

	/** Длительность эффектов — 3 минуты. */
	private static final int DURATION = 20 * 180;

	public FeastItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}

		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, DURATION, 1, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, DURATION, 1, false, true));
		// Поглощение III = 12 HP = 6 сердец
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, DURATION, 2, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 20 * 5, 0, false, false));

		if (player instanceof ServerPlayer serverPlayer) {
			Msg.overlay(serverPlayer, Component.translatable("message.festival.feast_eaten"));
		}

		stack.shrink(1);
		return InteractionResultHolder.consume(stack);
	}
}
