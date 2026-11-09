package com.example.festival.core;

import com.example.festival.entity.FestivalSummoner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Призыв разорителя вместо вексов.
 *
 * Раз в {@link #INTERVAL} тиков призыватель создаёт разорителя рядом с собой.
 * Одновременно у одного призывателя не больше {@link #MAX_SUMMONED} живых
 * разорителей, иначе волна никогда не закончится.
 */
public class SummonRavagerGoal extends Goal {

	/** Интервал призыва: 12 секунд. */
	private static final int INTERVAL = 240;
	/** Сколько призванных разорителей держим одновременно. */
	private static final int MAX_SUMMONED = 2;
	/** Метка, чтобы считать только своих. */
	public static final String SUMMONED_TAG = TrialTags.SUMMONED;

	private final FestivalSummoner summoner;
	private int cooldown = 80;
	private UUID owner;
	private boolean ownerResolved;

	public SummonRavagerGoal(FestivalSummoner summoner) {
		this.summoner = summoner;
		this.setFlags(EnumSet.of(Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		Trial trial = trial();
		return trial != null && trial.isRunning() && !summoner.level().isClientSide();
	}

	/** Забег призывателя: владелец кэшируется. */
	private Trial trial() {
		if (!ownerResolved) {
			owner = TrialTags.ownerOf(summoner);
			ownerResolved = true;
		}
		return owner == null ? null : TrialManager.of(owner);
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}

	@Override
	public void tick() {
		if (this.cooldown > 0) {
			this.cooldown--;
			return;
		}
		this.cooldown = INTERVAL;

		if (!(summoner.level() instanceof ServerLevel level)) {
			return;
		}
		Trial trial = trial();
		if (trial == null) {
			return;
		}

		int alive = level.getEntitiesOfClass(Ravager.class,
				summoner.getBoundingBox().inflate(48.0D),
				ravager -> ravager.isAlive() && ravager.getTags().contains(SUMMONED_TAG)).size();
		if (alive >= MAX_SUMMONED) {
			return;
		}

		Ravager ravager = EntityType.RAVAGER.create(level);
		if (ravager == null) {
			return;
		}

		BlockPos base = summoner.blockPosition();
		Vec3 spot = spotAround(level, base);

		ravager.moveTo(spot.x, spot.y, spot.z, summoner.getYRot(), 0.0F);
		ravager.addTag(TrialTags.MOB);
		ravager.addTag(SUMMONED_TAG);
		ravager.addTag(TrialTags.owner(trial.owner()));
		ravager.setPersistenceRequired();
		ravager.setGlowingTag(true);
		DifficultyScaler.scaleStats(ravager, trial, 1.0D);
		Trial.applyGoals(ravager);

		level.addFreshEntity(ravager);
	}

	/** Точка в 2–4 блоках от призывателя, с запасом по высоте. */
	private Vec3 spotAround(ServerLevel level, BlockPos base) {
		double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
		double radius = 2.0D + level.getRandom().nextDouble() * 2.0D;
		double x = base.getX() + 0.5D + Math.cos(angle) * radius;
		double z = base.getZ() + 0.5D + Math.sin(angle) * radius;

		for (int dy = 0; dy <= 3; dy++) {
			BlockPos candidate = BlockPos.containing(x, base.getY() + dy, z);
			if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
					&& level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
				return new Vec3(x, candidate.getY(), z);
			}
		}
		return new Vec3(x, base.getY(), z);
	}
}
