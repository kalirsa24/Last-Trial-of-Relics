package io.github.kalirsa24.lasttrial.core;

import java.util.Locale;

/**
 * Чистая экономика без Minecraft-классов: наценки на оружие и множители цен.
 *
 * Вынесено отдельно, чтобы правила можно было покрыть юнит-тестами
 * (см. {@code src/test/java/.../EconomyTest.java}) и менять баланс,
 * не трогая серверную логику магазина.
 *
 * Классификация идёт по id предмета, потому что единого API «это пистолет»
 * у сторонних оружейных модов нет. Списки ниже покрывают распространённые
 * названия моделей TACZ и соседних модов.
 */
public final class Economy {

	/** Порог вложений в древо, после которого цены растут. */
	public static final int PRICE_THRESHOLD = 20;
	/** Надбавка к ценам после порога. */
	public static final double PRICE_PENALTY = 1.2D;

	/** Снайперки, дробовики, пулемёты и патроны — цена ×2. */
	private static final String[] PRICE_X2 = {
			"sniper", "awp", "mosin", "kar98", "svd", "intervention", "barrett",
			"shotgun", "spas", "remington", "mossberg", "benelli",
			"lmg", "pkm", "rpk", "minimi", "machinegun",
			"ammo", "bullet", "cartridge", "shell"
	};

	/** Пистолеты и револьверы — +10 монет. */
	private static final String[] PRICE_PLUS_10 = {
			"pistol", "handgun", "glock", "deagle", "revolver", "usp", "m1911", "p226"
	};

	/** Автоматы, ПП и карабины — +20 монет. */
	private static final String[] PRICE_PLUS_20 = {
			"rifle", "smg", "carbine", "assault", "ak", "m4", "m16",
			"mp5", "mp7", "ump", "vector", "p90", "bizon"
	};

	private Economy() {
	}

	/**
	 * Наценка на оружие и патроны по типу ствола.
	 *
	 * <ul>
	 *   <li>снайперки, дробовики, пулемёты, патроны — ×2;</li>
	 *   <li>пистолеты и револьверы — +10;</li>
	 *   <li>автоматы, ПП, карабины — +20;</li>
	 *   <li>остальное — без наценки.</li>
	 * </ul>
	 */
	public static int markup(String itemId, int base) {
		if (base <= 0) {
			return base;
		}
		String path = itemId == null ? "" : itemId.toLowerCase(Locale.ROOT);

		if (containsAny(path, PRICE_X2)) {
			return base * 2;
		}
		if (containsAny(path, PRICE_PLUS_10)) {
			return base + 10;
		}
		if (containsAny(path, PRICE_PLUS_20)) {
			return base + 20;
		}
		return base;
	}

	/** Множитель цен магазина: больше 20 вложенных наград — всё дороже на 20%. */
	public static double priceMultiplier(int spentTrophies) {
		return spentTrophies > PRICE_THRESHOLD ? PRICE_PENALTY : 1.0D;
	}

	/** Применить множитель к цене с округлением вверх (в пользу сервера). */
	public static int applyMultiplier(int price, double multiplier) {
		if (price <= 0) {
			return price;
		}
		return (int) Math.ceil(price * Math.max(1.0D, multiplier));
	}

	/** Итоговая цена в монетах с учётом развития игрока. */
	public static int finalPrice(int basePrice, int spentTrophies) {
		return applyMultiplier(basePrice, priceMultiplier(spentTrophies));
	}

	private static boolean containsAny(String path, String[] needles) {
		for (String needle : needles) {
			if (path.contains(needle)) {
				return true;
			}
		}
		return false;
	}
}
