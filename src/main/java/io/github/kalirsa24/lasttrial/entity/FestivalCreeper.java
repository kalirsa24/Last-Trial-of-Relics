package io.github.kalirsa24.lasttrial.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;

/**
 * Крипер арены: втрое больше HP.
 *
 * Взрыв по умолчанию не разрушает блоки ({@code creeperBlockDamage=false}):
 * на сервере крипер, гоняющийся за игроком, иначе сносит чужие постройки
 * далеко за пределами арены. Урон по существам сохраняется — крипер остаётся
 * угрозой, но не инструментом гриферства.
 */
public class FestivalCreeper extends Creeper {

	public FestivalCreeper(EntityType<? extends Creeper> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Creeper.createAttributes()
				.add(Attributes.MAX_HEALTH, 60.0D)   // x3 от 20
				.add(Attributes.MOVEMENT_SPEED, 0.28D);
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	// Взрыв без разрушения блоков реализован в CreeperExplosionMixin:
	// Creeper#explodeCreeper объявлен final и не переопределяется.
}
