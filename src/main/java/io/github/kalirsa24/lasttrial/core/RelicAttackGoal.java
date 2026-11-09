package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.blockentity.RelicBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Цель ИИ: подойти к Реликвии и бить её.
 * Без неё мобы не могут атаковать блок (блоки не являются целью мобов в ванилле).
 *
 * Здесь же живёт «прокапывание»: если моб упёрся в стену и не сдвинулся за
 * {@link #STUCK_TICKS} тиков, он ломает блок на прямой к Реликвии. Раньше это
 * была отдельная цель с флагом {@code MOVE} — она не могла запуститься, потому
 * что {@code MOVE} уже занят этой целью, и мобы просто застывали у стен.
 */
public class RelicAttackGoal extends Goal {

	/** Сколько тиков без движения считаем «застрял». */
	private static final int STUCK_TICKS = 20;
	/** Насколько моб должен сдвинуться, чтобы считать, что он идёт. */
	private static final double MOVE_EPSILON = 0.0025D;
	/** Как далеко вперёд моб ломает мешающий блок. */
	private static final int REACH = 3;
	/**
	 * Входящий урон по Реликвии ×2: алтарь — главная цель волны,
	 * поэтому мобы бьют его вдвое больнее, чем обычные блоки-суррогаты.
	 */
	private static final double RELIC_DAMAGE_MULT = 2.0D;

	private final Mob mob;
	private final double speedModifier;
	private final double attackRangeSqr;
	private final int digInterval;

	private int attackCooldown;
	private int digCooldown;
	private int stuckTicks;
	private Vec3 lastPos;
	private UUID owner;
	private boolean ownerResolved;

	public RelicAttackGoal(Mob mob, double speedModifier) {
		this(mob, speedModifier, 12);
	}

	public RelicAttackGoal(Mob mob, double speedModifier, int digInterval) {
		this.mob = mob;
		this.speedModifier = speedModifier;
		this.attackRangeSqr = 9.0D;
		this.digInterval = digInterval;
		this.attackCooldown = 0;
		this.digCooldown = 0;
		this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (getTargetPos() == null || mob.level().isClientSide()) {
			return false;
		}
		// Если моб уже выбрал живую цель (игрока) — к алтарю не идём:
		// приоритет у того, кто ближе, а это решает NearestThreatGoal.
		LivingEntity target = mob.getTarget();
		return target == null || !target.isAlive();
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void start() {
		this.attackCooldown = 0;
		this.digCooldown = 0;
		this.stuckTicks = 0;
		this.lastPos = this.mob.position();
	}

	@Override
	public void stop() {
		this.mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		BlockPos pos = getTargetPos();
		if (pos == null) {
			return;
		}

		Vec3 center = Vec3.atCenterOf(pos);
		double distSqr = this.mob.distanceToSqr(center);

		if (distSqr > attackRangeSqr) {
			this.mob.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.speedModifier);
			this.trackStuck(pos);
			return;
		}

		this.stuckTicks = 0;
		this.mob.getNavigation().stop();
		this.mob.getLookControl().setLookAt(center.x, center.y, center.z);

		if (this.attackCooldown > 0) {
			this.attackCooldown--;
			return;
		}
		this.attackCooldown = 20;

		if (this.mob.level().getBlockEntity(pos) instanceof RelicBlockEntity relic) {
			double attack = this.mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
			double damage = attack * RELIC_DAMAGE_MULT * (1.0D - altarReduction());
			relic.damage(Math.max(1, (int) Math.round(damage)));
			this.mob.swing(InteractionHand.MAIN_HAND);
		}
	}

	/**
	 * Реликвии в инвентаре того, кто начал испытание, защищают и алтарь:
	 * тот же бонус, что снижает урон по игроку, снижает и урон по Реликвии.
	 */
	private double altarReduction() {
		Trial trial = trial();
		if (trial == null) {
			return 0.0D;
		}
		var starter = trial.ownerPlayer();
		return starter == null ? 0.0D : RelicBonus.reduction(starter);
	}

	/** Считает, стоит ли моб на месте, и если да — ломает блок перед собой. */
	private void trackStuck(BlockPos relic) {
		Vec3 current = this.mob.position();
		if (this.lastPos != null && current.distanceToSqr(this.lastPos) < MOVE_EPSILON) {
			this.stuckTicks++;
		} else {
			this.stuckTicks = 0;
		}
		this.lastPos = current;

		if (this.stuckTicks < STUCK_TICKS) {
			return;
		}
		if (this.digCooldown > 0) {
			this.digCooldown--;
			return;
		}
		this.digCooldown = this.digInterval;

		BlockPos blocker = findBlocker(relic);
		if (blocker != null) {
			this.mob.level().destroyBlock(blocker, false);
			this.mob.swing(InteractionHand.MAIN_HAND);
			this.stuckTicks = 0;
		}
	}

	/**
	 * Первый блок на прямой к Реликвии в пределах вытянутой руки.
	 * Пол и саму Реликвию не трогаем.
	 */
	private BlockPos findBlocker(BlockPos target) {
		BlockPos from = this.mob.blockPosition();
		int dx = target.getX() - from.getX();
		int dz = target.getZ() - from.getZ();
		int steps = Math.min(REACH, Math.max(Math.abs(dx), Math.abs(dz)));
		if (steps <= 0) {
			return null;
		}

		for (int i = 1; i <= steps; i++) {
			int x = from.getX() + Math.round((float) dx * i / steps);
			int z = from.getZ() + Math.round((float) dz * i / steps);

			for (int y = from.getY(); y <= from.getY() + 1; y++) {
				if (y < target.getY()) {
					continue;
				}
				BlockPos candidate = new BlockPos(x, y, z);
				if (candidate.equals(target)) {
					return null;
				}
				BlockState state = this.mob.level().getBlockState(candidate);
				if (!state.isAir() && state.blocksMotion()) {
					return candidate;
				}
			}
		}
		return null;
	}

	private BlockPos getTargetPos() {
		Trial trial = trial();
		if (trial == null || !trial.isRunning()) {
			return null;
		}
		return trial.relicPos();
	}

	/** Забег моба: владелец кэшируется, чтобы не читать метки каждый тик. */
	private Trial trial() {
		if (!ownerResolved) {
			owner = TrialTags.ownerOf(mob);
			ownerResolved = true;
		}
		return owner == null ? null : TrialManager.of(owner);
	}
}
