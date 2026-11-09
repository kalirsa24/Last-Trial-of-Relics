package com.example.festival.core;

/**
 * Уровни Реликвии. Отличаются числом волн, сложностью, крафтом и тем,
 * какое снаряжение доступно в магазине.
 */
public enum RelicTier {

	/** Уровень 1: 10 волн. Только пистолеты. */
	BASIC("basic", 10, 0.9D, 0.8D, 1),

	/** Основная: 25 волн. Пистолеты, ПП и штурмовые винтовки. */
	GREATER("greater", 25, 1.0D, 1.0D, 3),

	/** Бесконечная: любое снаряжение. Трофеев за неё не дают. */
	ETERNAL("eternal", 100, 1.15D, 1.2D, 0);

	private final String id;
	private final int maxWaves;
	private final double healthMultiplier;
	private final double spawnMultiplier;
	private final int trophyReward;

	RelicTier(String id, int maxWaves, double healthMultiplier, double spawnMultiplier, int trophyReward) {
		this.id = id;
		this.maxWaves = maxWaves;
		this.healthMultiplier = healthMultiplier;
		this.spawnMultiplier = spawnMultiplier;
		this.trophyReward = trophyReward;
	}

	public String getId() {
		return id;
	}

	/** Победная волна: на ней испытание заканчивается. */
	public int getMaxWaves() {
		return maxWaves;
	}

	public double getHealthMultiplier() {
		return healthMultiplier;
	}

	public double getSpawnMultiplier() {
		return spawnMultiplier;
	}

	/** Сколько трофеев падает по итогу испытания на этой Реликвии. */
	public int getTrophyReward() {
		return trophyReward;
	}

	public boolean isEndless() {
		return this == ETERNAL;
	}

	/** Разрешено ли на этом уровне Реликвии снаряжение, требующее {@code required}. */
	public boolean allows(RelicTier required) {
		return this.ordinal() >= required.ordinal();
	}

	public static RelicTier byId(String id) {
		for (RelicTier tier : values()) {
			if (tier.id.equals(id)) {
				return tier;
			}
		}
		return GREATER;
	}

	public static RelicTier byOrdinal(int ordinal) {
		RelicTier[] all = values();
		return ordinal >= 0 && ordinal < all.length ? all[ordinal] : GREATER;
	}
}
