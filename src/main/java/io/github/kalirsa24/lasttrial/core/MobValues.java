package io.github.kalirsa24.lasttrial.core;

import net.minecraft.world.entity.Entity;

/**
 * «Ценность» моба — сколько монет он стоит и сколько очков занимал в бюджете.
 * Держится отдельно от {@link WaveBudget}, чтобы математика волн оставалась
 * чистой и тестируемой без Minecraft.
 */
public final class MobValues {

	private MobValues() {
	}

	public static double pointsOf(Entity entity) {
		if (entity instanceof io.github.kalirsa24.lasttrial.entity.FestivalSummoner) {
			return 30.0D;
		}
		if (entity instanceof net.minecraft.world.entity.monster.Ravager) {
			return 15.0D;
		}
		if (entity instanceof io.github.kalirsa24.lasttrial.entity.FestivalBoss) {
			return 25.0D;
		}
		if (entity instanceof io.github.kalirsa24.lasttrial.entity.ArcherSkeleton) {
			return 12.0D;
		}
		if (entity instanceof io.github.kalirsa24.lasttrial.entity.FestivalCreeper) {
			return 3.0D;
		}
		if (entity instanceof io.github.kalirsa24.lasttrial.entity.MinerZombie) {
			return 2.0D;
		}
		if (entity instanceof net.minecraft.world.entity.monster.Skeleton) {
			return 1.5D;
		}
		if (entity instanceof net.minecraft.world.entity.monster.Zombie) {
			return 1.0D;
		}
		return 1.0D;
	}
}
