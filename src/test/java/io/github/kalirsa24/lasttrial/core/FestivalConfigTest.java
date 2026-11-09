package io.github.kalirsa24.lasttrial.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Загрузка конфига: JSON накладывается на дефолты, а не заменяет объект целиком.
 *
 * Раньше конфиг читался через Gson в новый объект, и любое поле, которого нет
 * в файле (например, после обновления мода), получало 0/false. Эти тесты
 * фиксируют правильное поведение.
 */
class FestivalConfigTest {

	private static FestivalConfig merged(String json) {
		FestivalConfig config = new FestivalConfig();
		JsonObject raw = JsonParser.parseString(json).getAsJsonObject();
		FestivalConfig.merge(raw, config);
		return config;
	}

	@Test
	@DisplayName("Пустой конфиг оставляет все значения по умолчанию")
	void emptyJsonKeepsDefaults() {
		FestivalConfig config = merged("{}");

		assertEquals(3, config.stagesPerWave);
		assertEquals(100, config.intermissionTicks);
		assertEquals(3, config.maxActiveTrials);
		assertEquals(300, config.arenaUnloadStopSeconds);
		assertTrue(config.damageRules);
		assertTrue(config.spawnEffects);
		assertTrue(config.announceEveryStage);
		// серверная безопасность: защита включена, гриферство выключено
		assertTrue(config.protectArena);
		assertTrue(config.mobDigging);
		assertFalse(config.creeperBlockDamage);
	}

	@Test
	@DisplayName("Указанные ключи переопределяют только себя")
	void partialJsonOverridesOnlyGivenKeys() {
		FestivalConfig config = merged("""
				{"stagesPerWave": 5, "damageRules": false}
				""");

		assertEquals(5, config.stagesPerWave);
		assertFalse(config.damageRules);
		// остальное — дефолты, а не нули
		assertEquals(100, config.intermissionTicks);
		assertEquals(3, config.maxActiveTrials);
	}

	@Test
	@DisplayName("Массив долей этапов читается целиком")
	void stageSharesArray() {
		FestivalConfig config = merged("""
				{"stageShares": [0.2, 0.3, 0.5]}
				""");

		assertArrayEquals(new double[]{0.2, 0.3, 0.5}, config.stageShares, 1.0e-9);
	}

	@Test
	@DisplayName("Ключ неверного типа не ломает загрузку и не обнуляет значение")
	void wrongTypeFallsBack() {
		FestivalConfig config = merged("""
				{"stagesPerWave": "много", "damageRules": "да"}
				""");

		assertEquals(3, config.stagesPerWave);
		assertTrue(config.damageRules);
	}
}
