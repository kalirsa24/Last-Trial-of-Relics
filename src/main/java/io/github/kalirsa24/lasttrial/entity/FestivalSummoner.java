package io.github.kalirsa24.lasttrial.entity;

import io.github.kalirsa24.lasttrial.core.SummonRavagerGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Призыватель празднества.
 *
 * Стоит в стороне от алтаря (скорость 0 — сам никуда не идёт) и раз в ~12 секунд
 * призывает разорителя вместо вексов. 30 сердец (60 HP). Если убить — через
 * 30 секунд вернётся на то же место, пока идёт испытание.
 */
public class FestivalSummoner extends Evoker {

	/** 30 сердец. */
	public static final double MAX_HEALTH = 60.0D;

	public FestivalSummoner(EntityType<? extends Evoker> type, Level level) {
		super(type, level);
		this.xpReward = 25;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Evoker.createAttributes()
				.add(Attributes.MAX_HEALTH, MAX_HEALTH)
				.add(Attributes.MOVEMENT_SPEED, 0.0D)      // стоит на месте
				.add(Attributes.ATTACK_DAMAGE, 6.0D)
				.add(Attributes.ARMOR, 4.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
				.add(Attributes.FOLLOW_RANGE, 64.0D);
	}

	/**
	 * Свои цели вместо ванильных: никаких вексов, полётов и походов к алтарю.
	 */
	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(1, new SummonRavagerGoal(this));
		this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 24.0F));
		this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}
}
