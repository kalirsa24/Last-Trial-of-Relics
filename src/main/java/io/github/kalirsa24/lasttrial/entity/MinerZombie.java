package io.github.kalirsa24.lasttrial.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * Зомби-шахтёр: крепче обычного зомби и грызёт стены втрое быстрее
 * (интервал копания задаётся в {@code Trial.applyGoals}).
 *
 * Само прокапывание живёт в {@code RelicAttackGoal}: отдельная цель с флагом
 * {@code MOVE} не могла запуститься, пока {@code MOVE} занят атакой Реликвии.
 */
public class MinerZombie extends Zombie {

	public MinerZombie(EntityType<? extends Zombie> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 24.0D)
				.add(Attributes.ATTACK_DAMAGE, 3.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.25D)
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
