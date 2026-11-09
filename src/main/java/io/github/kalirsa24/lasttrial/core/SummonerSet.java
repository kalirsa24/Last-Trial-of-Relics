package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.entity.FestivalSummoner;
import io.github.kalirsa24.lasttrial.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Призыватели одного забега.
 *
 * Стоят кольцом вокруг Реликвии, не идут к алтарю и не считаются мобами волны:
 * иначе волна не закончится, пока жив хоть один призыватель. Убитый возвращается
 * через {@link #RESPAWN_TICKS}. Набор принадлежит конкретному {@link Trial},
 * поэтому два забега на сервере больше не отбирают призывателей друг у друга.
 */
final class SummonerSet {

	private static final int RESPAWN_TICKS = 600;
	private static final int FIRST_WAVE = 3;
	private static final int MAX_COUNT = 3;
	private static final double RING_RADIUS = 26.0D;

	private static final class Slot {
		private final BlockPos pos;
		private FestivalSummoner entity;
		private int respawnTimer;

		private Slot(BlockPos pos) {
			this.pos = pos;
		}
	}

	private final Trial trial;
	private final List<Slot> slots = new ArrayList<>();

	SummonerSet(Trial trial) {
		this.trial = trial;
	}

	/** Вызывается на старте каждой волны. */
	void startWave(int wave) {
		clear();
		// Призыватели могли остаться с прошлой волны или восстановиться из мира —
		// убираем всех «своих», чтобы не было дублей на кольце
		discardOrphans();
		if (wave < FIRST_WAVE) {
			return;
		}

		int count = Math.min(MAX_COUNT, 1 + (wave - FIRST_WAVE) / 7);
		for (int i = 0; i < count; i++) {
			BlockPos pos = ringPos(i, count);
			if (pos == null) {
				continue;
			}
			Slot slot = new Slot(pos);
			slot.entity = spawn(pos);
			if (slot.entity != null) {
				slots.add(slot);
			}
		}
	}

	/** Каждый тик забега: возвращаем убитых призывателей. */
	void tick() {
		if (slots.isEmpty()) {
			return;
		}
		for (Iterator<Slot> it = slots.iterator(); it.hasNext(); ) {
			Slot slot = it.next();
			if (slot.entity != null && slot.entity.isAlive()) {
				continue;
			}
			if (slot.entity != null) {
				slot.entity = null;
				if (slot.respawnTimer <= 0) {
					slot.respawnTimer = RESPAWN_TICKS;
				}
			}
			if (slot.respawnTimer > 0) {
				slot.respawnTimer--;
				continue;
			}
			slot.entity = spawn(slot.pos);
			if (slot.entity != null) {
				trial.broadcast(Component.translatable("message.lasttrial.summoner_back"));
			}
		}
	}

	void onDeath(FestivalSummoner summoner) {
		for (Slot slot : slots) {
			if (slot.entity == summoner) {
				slot.entity = null;
				slot.respawnTimer = RESPAWN_TICKS;
				return;
			}
		}
	}

	void clear() {
		for (Slot slot : slots) {
			if (slot.entity != null && slot.entity.isAlive()) {
				slot.entity.discard();
			}
		}
		slots.clear();
	}

	/** Призыватели с меткой нашего забега, но не в слотах (сироты после рестарта). */
	private void discardOrphans() {
		ServerLevel level = trial.level();
		for (FestivalSummoner summoner : level.getEntitiesOfClass(FestivalSummoner.class,
				new net.minecraft.world.phys.AABB(trial.relicPos()).inflate(64.0D),
				summoner -> trial.owner().equals(TrialTags.ownerOf(summoner)))) {
			summoner.discard();
		}
	}

	int count() {
		return slots.size();
	}

	// ------------------------------------------------------------------

	private FestivalSummoner spawn(BlockPos pos) {
		ServerLevel level = trial.level();
		FestivalSummoner summoner = ModEntities.FESTIVAL_SUMMONER.create(level);
		if (summoner == null) {
			return null;
		}
		summoner.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
				level.getRandom().nextFloat() * 360.0F, 0.0F);
		summoner.addTag(TrialTags.SUMMONER);
		summoner.addTag(TrialTags.owner(trial.owner()));
		summoner.setPersistenceRequired();
		summoner.setGlowingTag(true);
		summoner.setCustomName(Component.translatable("entity.lasttrial.lasttrial_summoner"));
		summoner.setCustomNameVisible(false);
		level.addFreshEntity(summoner);
		return summoner;
	}

	/** Точка на кольце вокруг Реликвии, поднятая до свободного места. */
	private BlockPos ringPos(int index, int count) {
		ServerLevel level = trial.level();
		BlockPos relic = trial.relicPos();
		double angle = (Math.PI * 2.0D / count) * index + Math.PI / 4.0D;
		int x = relic.getX() + (int) Math.round(Math.cos(angle) * RING_RADIUS);
		int z = relic.getZ() + (int) Math.round(Math.sin(angle) * RING_RADIUS);

		for (int dy = 0; dy <= 6; dy++) {
			BlockPos candidate = new BlockPos(x, relic.getY() + dy, z);
			if (level.getBlockState(candidate).getCollisionShape(level, candidate).isEmpty()
					&& level.getBlockState(candidate.above()).getCollisionShape(level, candidate.above()).isEmpty()) {
				return candidate;
			}
		}
		return new BlockPos(x, relic.getY(), z);
	}
}
