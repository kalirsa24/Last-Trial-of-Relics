package io.github.kalirsa24.lasttrial.registry;

import io.github.kalirsa24.lasttrial.FestivalMod;
import io.github.kalirsa24.lasttrial.block.RelicBlock;
import io.github.kalirsa24.lasttrial.core.RelicTier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

	/** Уровень 1: 10 волн (8 камня + алмазный блок). */
	public static final Block RELIC_BASIC = new RelicBlock(RelicTier.BASIC, relicProperties());

	/** Основная: 25 волн (8 алмазных блоков + маяк). */
	public static final Block RELIC = new RelicBlock(RelicTier.GREATER, relicProperties());

	/** Бесконечная: до 100-й волны или до 5 смертей. */
	public static final Block RELIC_ETERNAL = new RelicBlock(RelicTier.ETERNAL, relicProperties());

	public static final Block ARENA_TILE = new Block(BlockBehaviour.Properties.of()
			.strength(3.0F, 1200.0F)
			.sound(SoundType.STONE)
			.requiresCorrectToolForDrops());

	public static void register() {
		registerBlock("relic_basic", RELIC_BASIC);
		registerBlock("relic", RELIC);
		registerBlock("relic_eternal", RELIC_ETERNAL);
		registerBlock("arena_tile", ARENA_TILE);
	}

	private static void registerBlock(String name, Block block) {
		Registry.register(BuiltInRegistries.BLOCK, id(name), block);
	}

	private static BlockBehaviour.Properties relicProperties() {
		return BlockBehaviour.Properties.of()
				.strength(10.0F, 3600000.0F) // взрывобезопасна
				.noOcclusion()
				.lightLevel(state -> 10);
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(FestivalMod.MOD_ID, path);
	}
}
