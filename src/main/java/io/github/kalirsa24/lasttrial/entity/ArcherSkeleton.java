package io.github.kalirsa24.lasttrial.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Скелет-лучник: стоит на месте и стреляет из лука с «Силой V».
 */
public class ArcherSkeleton extends Skeleton {

	public ArcherSkeleton(EntityType<? extends Skeleton> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Skeleton.createAttributes()
				.add(Attributes.MAX_HEALTH, 24.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.0D)   // стоит на месте
				.add(Attributes.FOLLOW_RANGE, 48.0D);
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
										MobSpawnType reason, @Nullable SpawnGroupData spawnData,
										@Nullable CompoundTag dataTag) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

		ItemStack bow = new ItemStack(Items.BOW);
		bow.enchant(Enchantments.POWER_ARROWS, 5);

		this.setItemSlot(EquipmentSlot.MAINHAND, bow);
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		return data;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}
}
