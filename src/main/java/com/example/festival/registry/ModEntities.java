package com.example.festival.registry;

import com.example.festival.entity.ArcherSkeleton;
import com.example.festival.entity.FestivalBoss;
import com.example.festival.entity.FestivalCreeper;
import com.example.festival.entity.FestivalSummoner;
import com.example.festival.entity.MinerZombie;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class ModEntities {

	public static final EntityType<FestivalBoss> FESTIVAL_BOSS = register("festival_boss",
			EntityType.Builder.of(FestivalBoss::new, MobCategory.MONSTER)
					.sized(0.8F, 2.2F)
					.clientTrackingRange(10));

	public static final EntityType<FestivalCreeper> FESTIVAL_CREEPER = register("festival_creeper",
			EntityType.Builder.of(FestivalCreeper::new, MobCategory.MONSTER)
					.sized(0.6F, 1.7F)
					.clientTrackingRange(8));

	public static final EntityType<MinerZombie> MINER_ZOMBIE = register("miner_zombie",
			EntityType.Builder.of(MinerZombie::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(8));

	public static final EntityType<ArcherSkeleton> ARCHER_SKELETON = register("archer_skeleton",
			EntityType.Builder.of(ArcherSkeleton::new, MobCategory.MONSTER)
					.sized(0.6F, 1.99F)
					.clientTrackingRange(8));

	public static final EntityType<FestivalSummoner> FESTIVAL_SUMMONER = register("festival_summoner",
			EntityType.Builder.of(FestivalSummoner::new, MobCategory.MONSTER)
					.sized(0.6F, 1.95F)
					.clientTrackingRange(10));

	private static <T extends Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, ModBlocks.id(name), builder.build(name));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(FESTIVAL_BOSS, FestivalBoss.createAttributes());
		FabricDefaultAttributeRegistry.register(FESTIVAL_CREEPER, FestivalCreeper.createAttributes());
		FabricDefaultAttributeRegistry.register(MINER_ZOMBIE, MinerZombie.createAttributes());
		FabricDefaultAttributeRegistry.register(ARCHER_SKELETON, ArcherSkeleton.createAttributes());
		FabricDefaultAttributeRegistry.register(FESTIVAL_SUMMONER, FestivalSummoner.createAttributes());
	}
}
