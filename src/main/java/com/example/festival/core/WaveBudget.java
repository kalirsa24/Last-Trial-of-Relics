package com.example.festival.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Очки волны — бюджет, за который «покупается» состав волны.
 *
 * Класс намеренно не знает про Minecraft: только числа, веса и {@link Random}.
 * Это позволяет проверять баланс юнит-тестами, а не забегами в игре.
 *
 * Бюджет волны делится между этапами по {@link FestivalConfig#stageShare(int)},
 * остаток каждого этапа переносится в следующий (не сгорает), а на последнем
 * этапе гарантирован «якорь» — тяжёлый моб, чтобы натиск не оказался толпой зомби.
 */
public final class WaveBudget {

	/** Цена моба в очках. */
	public enum Type {
		ZOMBIE("zombie", 1.0D, 1, 6),
		SKELETON("skeleton", 1.5D, 2, 4),
		MINER("miner", 2.0D, 7, 3),
		CREEPER("creeper", 3.0D, 3, 3),
		ARCHER("archer", 12.0D, 5, 1);

		private final String id;
		private final double cost;
		private final int minWave;
		private final int weight;

		Type(String id, double cost, int minWave, int weight) {
			this.id = id;
			this.cost = cost;
			this.minWave = minWave;
			this.weight = weight;
		}

		public String id() {
			return id;
		}

		public double cost() {
			return cost;
		}

		public int minWave() {
			return minWave;
		}

		public int weight() {
			return weight;
		}

		/** Тяжёлый моб — тот, кого имеет смысл ставить якорем натиска. */
		public boolean heavy() {
			return cost >= 2.0D;
		}

		public static Type byId(String id) {
			String clean = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
			for (Type type : values()) {
				if (type.id.equalsIgnoreCase(clean)) {
					return type;
				}
			}
			return null;
		}
	}

	/** Итог сборки этапа: состав и неистраченный остаток бюджета. */
	public record Composition(List<Type> mobs, double leftover) {
	}

	/**
	 * Предохранители от «конфига-бомбы»: бюджет растёт линейно, и на 100-й волне
	 * вечной Реликвии он иначе выдал бы сотни мобов разом.
	 */
	public static final double MAX_WAVE_POINTS = 150.0D;
	public static final int MAX_MOBS_PER_STAGE = 60;

	private WaveBudget() {
	}

	/** Полный бюджет волны (все этапы вместе). */
	public static double wavePoints(int wave, int players, double tierMultiplier,
									boolean buffed, boolean hard) {
		double points = 6.0D + wave * 2.5D;
		points *= Math.sqrt(Math.max(1, players));
		points *= Math.max(0.1D, tierMultiplier);
		if (buffed) {
			points *= 2.0D;
		}
		if (hard) {
			points *= 1.35D;
		}
		return Math.min(MAX_WAVE_POINTS, Math.max(4.0D, points));
	}

	/**
	 * Состав одного этапа.
	 *
	 * @param budget бюджет этапа вместе с перенесённым остатком прошлого
	 */
	public static Composition compose(Random random, int wave, int stage, int stages, double budget) {
		List<Type> mobs = new ArrayList<>();
		double left = budget;
		boolean finalPush = stage >= stages - 1;

		// Якорь натиска: один гарантированно тяжёлый моб, а не лотерея
		if (finalPush && wave >= 3) {
			Type anchor = pickAnchor(random, wave, left);
			if (anchor != null) {
				mobs.add(anchor);
				left -= anchor.cost();
			}
		}

		while (left > 0.0D && mobs.size() < MAX_MOBS_PER_STAGE) {
			Type type = pick(random, wave, left, finalPush);
			if (type == null) {
				break;
			}
			mobs.add(type);
			left -= type.cost();
		}

		// Этап не должен быть пустым даже при крошечном бюджете
		if (mobs.isEmpty()) {
			mobs.add(Type.ZOMBIE);
			left -= Type.ZOMBIE.cost();
		}
		return new Composition(List.copyOf(mobs), left);
	}

	/** Случайный моб, который влезает в остаток бюджета, или null. */
	public static Type pick(Random random, int wave, double budget, boolean finalPush) {
		int total = 0;
		for (Type type : Type.values()) {
			if (available(type, wave, budget)) {
				total += weightOf(type, finalPush);
			}
		}
		if (total <= 0) {
			return null;
		}

		int roll = random.nextInt(total);
		for (Type type : Type.values()) {
			if (!available(type, wave, budget)) {
				continue;
			}
			roll -= weightOf(type, finalPush);
			if (roll < 0) {
				return type;
			}
		}
		return null;
	}

	/** Тяжёлый моб для якоря натиска, либо null, если бюджета не хватает. */
	private static Type pickAnchor(Random random, int wave, double budget) {
		int total = 0;
		for (Type type : Type.values()) {
			if (type.heavy() && available(type, wave, budget)) {
				total += type.weight();
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Type type : Type.values()) {
			if (!type.heavy() || !available(type, wave, budget)) {
				continue;
			}
			roll -= type.weight();
			if (roll < 0) {
				return type;
			}
		}
		return null;
	}

	private static boolean available(Type type, int wave, double budget) {
		return type.minWave() <= wave && type.cost() <= budget;
	}

	/**
	 * Вес моба: на разгоне охотнее идут дешёвые, на натиске — тяжёлые,
	 * середина волны играет по обычным весам.
	 */
	private static int weightOf(Type type, boolean finalPush) {
		if (finalPush) {
			return type.heavy() ? type.weight() * 2 : type.weight();
		}
		return type.cost() <= 1.5D ? type.weight() * 2 : type.weight();
	}
}
