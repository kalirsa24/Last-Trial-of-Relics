package com.example.festival.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Предмет тотема: имя зависит от тира, подсказка объясняет прибавку к урону,
 * лимит в два тотема и работу при смерти.
 */
public class TotemItem extends Item {

	public TotemItem(Properties properties) {
		super(properties);
	}

	@Override
	public Component getName(ItemStack stack) {
		int tier = TotemOfPower.getTier(stack);
		return Component.translatable("item.festival.totem_of_power.tier", TotemOfPower.roman(tier));
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level,
								List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		int tier = TotemOfPower.getTier(stack);

		tooltip.add(Component.translatable("tooltip.festival.totem_damage", TotemOfPower.percent(tier))
				.withStyle(ChatFormatting.RED));
		tooltip.add(Component.translatable("tooltip.festival.totem_shield", TotemOfPower.SHIELD_SLOTS)
				.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.festival.totem_death")
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
