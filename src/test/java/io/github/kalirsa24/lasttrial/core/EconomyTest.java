package io.github.kalirsa24.lasttrial.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Экономика: наценки на оружие и множитель цен за вложения в древо.
 * Тесты чистые — Minecraft-мир не нужен.
 */
class EconomyTest {

	@ParameterizedTest(name = "{0} за {1} -> {2}")
	@DisplayName("Наценка по типу ствола")
	@CsvSource({
			"tacz:glock_17,      40,  50",
			"tacz:deagle,        40,  50",
			"tacz:ak47,          70,  90",
			"tacz:m4a1,          70,  90",
			"tacz:mp5,           60,  80",
			"tacz:awp,          120, 240",
			"tacz:mosin,        120, 240",
			"tacz:spas12,        90, 180",
			"tacz:ammo_762,       6,  12",
			"minecraft:bread,     3,   3"
	})
	void markupByWeaponType(String id, int base, int expected) {
		assertEquals(expected, Economy.markup(id, base));
	}

	@Test
	@DisplayName("Наценка не портит нулевую и отрицательную цену")
	void markupKeepsNonPositivePrice() {
		assertEquals(0, Economy.markup("tacz:awp", 0));
		assertEquals(-5, Economy.markup("tacz:awp", -5));
	}

	@Test
	@DisplayName("Наценка переживает null-id")
	void markupHandlesNullId() {
		assertEquals(25, Economy.markup(null, 25));
	}

	@Test
	@DisplayName("Множитель цен включается только после 20 вложенных наград")
	void priceMultiplierThreshold() {
		assertEquals(1.0D, Economy.priceMultiplier(0));
		assertEquals(1.0D, Economy.priceMultiplier(20));
		assertEquals(1.2D, Economy.priceMultiplier(21));
	}

	@Test
	@DisplayName("Итоговая цена округляется вверх")
	void finalPriceRoundsUp() {
		assertEquals(180, Economy.finalPrice(150, 25));
		assertEquals(900, Economy.finalPrice(750, 25));
		assertEquals(150, Economy.finalPrice(150, 0));
		assertEquals(0, Economy.finalPrice(0, 25));
	}
}
