package com.example.festival.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class DifficultyScaler {

	/** Хард-режим: злее характеристики. */
	public static final double HARD_STATS = 1.5D;

	private DifficultyScaler() {
	}

	public static int getPlayerCount(ServerLevel level) {
		return Math.max(1, level.players().size());
	}

	/** Множитель сложности: корень из числа игроков. */
	public static double getMultiplier(ServerLevel level) {
		return Math.sqrt(getPlayerCount(level));
	}

	public static void scaleStats(LivingEntity entity, Trial trial, double extra) {
		// Этап добавляет злости: натиск больнее разгона, а не только гуще
		double stageBonus = 1.0D + trial.currentStage() * FestivalConfig.get().stageStatBonus;
		double mult = getMultiplier(trial.level())
				* (1.0D + trial.currentWave() * 0.04D)
				* stageBonus
				* extra;
		if (trial.hardMode()) {
			mult *= HARD_STATS;
		}

		AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth != null) {
			maxHealth.setBaseValue(maxHealth.getBaseValue() * mult);
		}

		AttributeInstance attack = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack != null) {
			attack.setBaseValue(attack.getBaseValue() * mult);
		}

		AttributeInstance armor = entity.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.setBaseValue(Math.min(20.0D,
					trial.currentWave() * 0.5D + trial.currentStage() * 0.5D));
		}

		entity.setHealth(entity.getMaxHealth());
	}
}
