package io.github.kalirsa24.lasttrial.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Уровни Реликвии: число волн и совместимость снаряжения. */
class RelicTierTest {

	@Test
	@DisplayName("Число волн совпадает с заявленным в названии")
	void maxWaves() {
		assertEquals(10, RelicTier.BASIC.getMaxWaves());
		assertEquals(25, RelicTier.GREATER.getMaxWaves());
		assertEquals(100, RelicTier.ETERNAL.getMaxWaves());
	}

	@Test
	@DisplayName("Старший тир открывает снаряжение младших, но не наоборот")
	void allows() {
		assertTrue(RelicTier.GREATER.allows(RelicTier.BASIC));
		assertTrue(RelicTier.ETERNAL.allows(RelicTier.GREATER));
		assertFalse(RelicTier.BASIC.allows(RelicTier.GREATER));
	}

	@Test
	@DisplayName("Неизвестный id не ломает загрузку и падает на основной тир")
	void byIdFallback() {
		assertEquals(RelicTier.BASIC, RelicTier.byId("basic"));
		assertEquals(RelicTier.GREATER, RelicTier.byId("нет-такого"));
	}

	@Test
	@DisplayName("Бесконечный тир — только ETERNAL")
	void endlessOnlyForEternal() {
		assertTrue(RelicTier.ETERNAL.isEndless());
		assertFalse(RelicTier.GREATER.isEndless());
	}
}
