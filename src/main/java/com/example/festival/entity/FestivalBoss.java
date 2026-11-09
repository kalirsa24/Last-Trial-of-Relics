package com.example.festival.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Босс 25-й волны: усиленный зомби, не горит на солнце и не исчезает.
 */
public class FestivalBoss extends Zombie {

	public FestivalBoss(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
		this.xpReward = 200;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 300.0D)
				.add(Attributes.ATTACK_DAMAGE, 12.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.26D)
				.add(Attributes.ARMOR, 10.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
				.add(Attributes.FOLLOW_RANGE, 64.0D);
	}

	@Override
	protected boolean isSunSensitive() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}
}
