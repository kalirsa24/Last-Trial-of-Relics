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
 * Предмет с пояснением в подсказке: где применяется и что с ним происходит.
 * Используется для валюты празднества.
 */
public class LoreItem extends Item {

	private final String usageKey;
	private final String hintKey;

	public LoreItem(Properties properties, String usageKey, String hintKey) {
		super(properties);
		this.usageKey = usageKey;
		this.hintKey = hintKey;
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level,
								List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		tooltip.add(Component.translatable(usageKey).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY));
	}
}
