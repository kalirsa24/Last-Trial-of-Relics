package io.github.kalirsa24.lasttrial.registry;

import io.github.kalirsa24.lasttrial.blockentity.RelicBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;


public class ModBlockEntities {

	public static final BlockEntityType<RelicBlockEntity> RELIC = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ModBlocks.id("relic"),
			BlockEntityType.Builder.of(RelicBlockEntity::new,
					ModBlocks.RELIC, ModBlocks.RELIC_BASIC, ModBlocks.RELIC_ETERNAL).build(null));

	public static void register() {
		// Регистрация выполняется в статическом инициализаторе.
	}
}
