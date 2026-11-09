package io.github.kalirsa24.lasttrial.registry;

import io.github.kalirsa24.lasttrial.FestivalMod;
import io.github.kalirsa24.lasttrial.item.FeastItem;
import io.github.kalirsa24.lasttrial.item.FestivalKeyItem;
import io.github.kalirsa24.lasttrial.item.LoreItem;
import io.github.kalirsa24.lasttrial.item.RelicItem;
import io.github.kalirsa24.lasttrial.item.TotemItem;
import io.github.kalirsa24.lasttrial.item.TrophyItem;
import io.github.kalirsa24.lasttrial.item.WaveSkipItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public class ModItems {

	public static final Item RELIC_BASIC = new RelicItem(ModBlocks.RELIC_BASIC, new Item.Properties());
	public static final Item RELIC = new RelicItem(ModBlocks.RELIC, new Item.Properties());
	public static final Item RELIC_ETERNAL = new RelicItem(ModBlocks.RELIC_ETERNAL, new Item.Properties());
	public static final Item ARENA_TILE = blockItem("arena_tile", ModBlocks.ARENA_TILE);

	/** Валюта магазина — живёт только внутри испытания. */
	public static final Item FESTIVAL_COIN = new LoreItem(new Item.Properties(),
			"tooltip.lasttrial.coin_use", "tooltip.lasttrial.coin_hint");

	/** Награда празднества — персональная валюта, привязана к владельцу. */
	public static final Item TROPHY = new TrophyItem(new Item.Properties().stacksTo(64));

	/** Пир празднества — награда для открытого мира. */
	public static final Item FEAST = new FeastItem(new Item.Properties().stacksTo(16));

	/** Пропуск волн: 3 волны вперёд, следующая — усиленная. */
	public static final Item WAVE_SKIP = new WaveSkipItem(new Item.Properties().stacksTo(16));

	/** Ключ празднества — открывает сундук с тотемами. */
	public static final Item FESTIVAL_KEY = new FestivalKeyItem(new Item.Properties().stacksTo(16));

	/** Тотем усиления: 15 уровней, прибавка к исходящему урону. */
	public static final Item TOTEM_OF_POWER = new TotemItem(new Item.Properties().stacksTo(16));

	private static Item blockItem(String name, Block block) {
		return new BlockItem(block, new Item.Properties());
	}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, id("relic_basic"), RELIC_BASIC);
		Registry.register(BuiltInRegistries.ITEM, id("relic"), RELIC);
		Registry.register(BuiltInRegistries.ITEM, id("relic_eternal"), RELIC_ETERNAL);
		Registry.register(BuiltInRegistries.ITEM, id("arena_tile"), ARENA_TILE);
		Registry.register(BuiltInRegistries.ITEM, id("festival_coin"), FESTIVAL_COIN);
		Registry.register(BuiltInRegistries.ITEM, id("trophy"), TROPHY);
		Registry.register(BuiltInRegistries.ITEM, id("totem_of_power"), TOTEM_OF_POWER);
		Registry.register(BuiltInRegistries.ITEM, id("feast"), FEAST);
		Registry.register(BuiltInRegistries.ITEM, id("wave_skip"), WAVE_SKIP);
		Registry.register(BuiltInRegistries.ITEM, id("festival_key"), FESTIVAL_KEY);
	}

	public static void addToCreativeTabs() {
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.BUILDING_BLOCKS).register(entries -> {
			entries.accept(RELIC_BASIC);
			entries.accept(RELIC);
			entries.accept(RELIC_ETERNAL);
			entries.accept(ARENA_TILE);
		});
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries -> {
			entries.accept(FESTIVAL_COIN);
			entries.accept(TROPHY);
			entries.accept(TOTEM_OF_POWER);
			entries.accept(FEAST);
			entries.accept(WAVE_SKIP);
			entries.accept(FESTIVAL_KEY);
		});
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(FestivalMod.MOD_ID, path);
	}
}
