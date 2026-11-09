package io.github.kalirsa24.lasttrial.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Выбор цели по принципу «кто ближе — того и бьём».
 *
 * Моб сравнивает расстояние до ближайшего игрока и до Реликвии:
 *   игрок ближе  -> цель игрок (дальше его добивает ванильный ИИ моба);
 *   Реликвия ближе -> цель сбрасывается, и работает {@link RelicAttackGoal}.
 *
 * Владелец забега кэшируется: метки читаются один раз, дальше только поиск в карте.
 */
public class NearestThreatGoal extends Goal {

	/** Радиус поиска игроков. */
	private static final double SEARCH_RANGE = 24.0D;

	private final Mob mob;
	private UUID owner;
	private boolean ownerResolved;
	private int cooldown;

	public NearestThreatGoal(Mob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Flag.TARGET));
	}

	/** Забег моба, либо {@code null}, если моб уже ничей. */
	private Trial trial() {
		if (!ownerResolved) {
			owner = TrialTags.ownerOf(mob);
			ownerResolved = true;
		}
		return owner == null ? null : TrialManager.of(owner);
	}

	@Override
	public boolean canUse() {
		Trial trial = trial();
		return trial != null && trial.isRunning() && !mob.level().isClientSide();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		this.cooldown = 0;
	}

	@Override
	public void tick() {
		if (this.cooldown > 0) {
			this.cooldown--;
			return;
		}
		this.cooldown = 10;

		Trial trial = trial();
		if (trial == null) {
			return;
		}

		Player nearest = mob.level().getNearestPlayer(mob, SEARCH_RANGE);
		BlockPos relic = trial.relicPos();
		double relicDistance = mob.distanceToSqr(Vec3.atCenterOf(relic));

		LivingEntity current = mob.getTarget();

		if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
			double playerDistance = mob.distanceToSqr(nearest);
			if (playerDistance < relicDistance) {
				if (current != nearest) {
					mob.setTarget(nearest);
				}
				return;
			}
		}

		// Игрок дальше алтаря — возвращаемся к Реликвии
		if (current != null) {
			mob.setTarget(null);
		}
	}
}
