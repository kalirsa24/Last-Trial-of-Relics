package com.example.festival.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Проверка места под арену.
 *
 * Обязательная платформа — 20×20 сплошного пола: без неё событие не запустить.
 * Бонусная — 30×30: если весь внешний пояс тоже застроен, забег получает
 * дополнительную монету за каждую волну и лишнюю награду по победе.
 */
public final class ArenaValidator {

	/** Обязательная платформа (20×20). */
	public static final int REQUIRED_SIZE = 20;
	/** Бонусная платформа (30×30). */
	public static final int BONUS_SIZE = 30;

	public static final int REQUIRED_RADIUS = REQUIRED_SIZE / 2;
	public static final int BONUS_RADIUS = BONUS_SIZE / 2;

	/** Радиус, по которому снимается снимок пола и ограничиваются мобы. */
	public static final int RADIUS = BONUS_RADIUS;

	/** Порог пола при установке Реликвии. */
	public static final double MIN_FLOOR_RATIO = 0.95D;

	/** Доля площади, над которой видно небо. */
	public static final double MIN_SKY_RATIO = 0.65D;

	/** Насколько высоко проверяем небо. */
	private static final int SKY_CHECK_HEIGHT = 96;

	private ArenaValidator() {
	}

	/**
	 * @param floorRatio доля твёрдого пола в обязательных 20×20
	 * @param skyRatio   доля открытого неба над обязательной площадкой
	 * @param bonusRatio доля твёрдого пола в бонусных 30×30
	 * @param bonus      набран ли бонус (пол 30×30 целый)
	 */
	public record Result(boolean ok, String reason, double floorRatio, double skyRatio,
						 double bonusRatio, boolean bonus) {
	}

	public static Result validate(Level level, BlockPos center) {
		return validate(level, center, MIN_FLOOR_RATIO);
	}

	/** Проверка с явным порогом пола (1.0 — площадка должна быть целой). */
	public static Result validate(Level level, BlockPos center, double minFloorRatio) {
		int floorY = center.getY() - 1;

		Ratio required = floorRatio(level, center, floorY, REQUIRED_RADIUS);
		Ratio bonus = floorRatio(level, center, floorY, BONUS_RADIUS);
		double skyRatio = skyRatio(level, center, floorY, REQUIRED_RADIUS);

		boolean bonusReached = bonus.total() > 0 && bonus.solid() == bonus.total();

		if (required.ratio() < minFloorRatio) {
			return new Result(false, "arena.festival.bad_floor",
					required.ratio(), skyRatio, bonus.ratio(), bonusReached);
		}
		if (skyRatio < MIN_SKY_RATIO) {
			return new Result(false, "arena.festival.bad_sky",
					required.ratio(), skyRatio, bonus.ratio(), bonusReached);
		}
		return new Result(true, "", required.ratio(), skyRatio, bonus.ratio(), bonusReached);
	}

	/** Набран ли бонус 30×30 (для награды). */
	public static boolean bonusComplete(Level level, BlockPos center) {
		return validate(level, center, 0.0D).bonus();
	}

	private record Ratio(int solid, int total) {
		double ratio() {
			return total <= 0 ? 0.0D : (double) solid / total;
		}
	}

	private static Ratio floorRatio(Level level, BlockPos center, int floorY, int radius) {
		int solid = 0;
		int total = 0;
		for (int dx = -radius; dx < radius; dx++) {
			for (int dz = -radius; dz < radius; dz++) {
				total++;
				BlockState state = level.getBlockState(
						new BlockPos(center.getX() + dx, floorY, center.getZ() + dz));
				if (!state.isAir() && state.blocksMotion()) {
					solid++;
				}
			}
		}
		return new Ratio(solid, total);
	}

	private static double skyRatio(Level level, BlockPos center, int floorY, int radius) {
		int maxY = Math.min(level.getMaxBuildHeight(), floorY + SKY_CHECK_HEIGHT);
		int open = 0;
		int total = 0;
		for (int dx = -radius; dx < radius; dx++) {
			for (int dz = -radius; dz < radius; dz++) {
				total++;
				boolean clear = true;
				for (int y = floorY + 1; y <= maxY; y++) {
					if (!level.getBlockState(
							new BlockPos(center.getX() + dx, y, center.getZ() + dz)).isAir()) {
						clear = false;
						break;
					}
				}
				if (clear) {
					open++;
				}
			}
		}
		return total <= 0 ? 0.0D : (double) open / total;
	}
}
