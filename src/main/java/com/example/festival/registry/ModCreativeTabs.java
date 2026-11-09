package com.example.festival.registry;

import com.example.festival.item.TotemOfPower;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/**
 * Отдельная вкладка в творческом режиме со всем содержимым мода —
 * чтобы тестировать без команд и поиска по чужим вкладкам.
 *
 * Тотемы идут подряд, у каждого своё имя («Тотем усиления IV») и цвет глаз,
 * поэтому 15 уровней различаются без наведения.
 */
public final class ModCreativeTabs {

	public static final ResourceKey<CreativeModeTab> FESTIVAL =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, ModItems.id("festival"));

	private ModCreativeTabs() {
	}

	public static void register() {
		CreativeModeTab tab = FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.festival"))
				.icon(() -> TotemOfPower.create(TotemOfPower.MAX_TIER))
				.displayItems((params, output) -> {
					// Блоки
					output.accept(ModItems.RELIC_BASIC);
					output.accept(ModItems.RELIC);
					output.accept(ModItems.RELIC_ETERNAL);
					output.accept(ModItems.ARENA_TILE);

					// Валюта и награды
					output.accept(ModItems.FESTIVAL_COIN);
					output.accept(ModItems.TROPHY);
					output.accept(ModItems.FEAST);
					output.accept(ModItems.WAVE_SKIP);
					output.accept(ModItems.FESTIVAL_KEY);

					// Тотем усиления — все 15 уровней подряд
					for (int tier = 1; tier <= TotemOfPower.MAX_TIER; tier++) {
						output.accept(TotemOfPower.create(tier));
					}
				})
				.build();

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FESTIVAL, tab);
	}
}
