package io.github.kalirsa24.lasttrial.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.kalirsa24.lasttrial.FestivalMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Настройки испытания: {@code config/lasttrial.json}.
 *
 * Всё, что раньше было константами в коде, теперь данные. Значения
 * проверяются и зажимаются в разумные рамки — сломанный конфиг не должен
 * ронять сервер и не должен позволять выкрутить бесконечных мобов.
 *
 * Загрузка идёт не через {@code Gson.fromJson(...)}, а через явное слияние
 * с дефолтами: Gson создаёт объект в обход конструктора, поэтому любое
 * поле, которого нет в файле (старый конфиг после обновления мода),
 * получало бы 0/false. {@link #merge(JsonObject, FestivalConfig)} читает
 * только те ключи, что реально есть, остальные оставляет дефолтными.
 */
public final class FestivalConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MIN_STAGES = 1;
	private static final int MAX_STAGES = 8;

	public static final String FILE_NAME = "lasttrial.json";
	/** Имя конфига до переименования мода — читается один раз для миграции. */
	private static final String LEGACY_FILE_NAME = "festival.json";

	private static FestivalConfig instance = new FestivalConfig();

	/** Сколько этапов (подволн) в одной волне. 10 волн × 3 = 30 подволн. */
	public int stagesPerWave = 3;
	/** Базовый интервал между мобами внутри этапа, тики. */
	public int dripTicks = 10;
	/** Минимальный интервал дрипа на поздних волнах. */
	public int dripMinTicks = 4;
	/** Сколько секунд этап ждёт зачистки, прежде чем накатить следующий. */
	public int stageTimeoutSeconds = 12;
	/** Надбавка к таймауту за каждого моба этапа, тики. */
	public int stageTimeoutPerMobTicks = 10;
	/** Доли бюджета волны по этапам (нормализуются при загрузке). */
	public double[] stageShares = {0.25D, 0.30D, 0.45D};
	/** Каждая N-я волна — «флаговая»: рог, титул, лишний трофей. */
	public int flagEveryWaves = 5;
	/** Прибавка к HP и урону мобов за каждый следующий этап. */
	public double stageStatBonus = 0.05D;
	/** Пауза между волнами, тики. */
	public int intermissionTicks = 100;
	public double spawnRadiusMin = 15.0D;
	public double spawnRadiusMax = 25.0D;
	/** Портальные партиклы и звук в точке выхода моба. */
	public boolean spawnEffects = true;
	/** Писать в чат о каждом этапе, а не только о волне и натиске. */
	public boolean announceEveryStage = true;
	/** Сколько тиков забег терпит отсутствие загруженной Реликвии, прежде чем замереть. */
	public int relicMissingGraceTicks = 200;
	/**
	 * Сколько секунд забег ждёт выгруженную арену, прежде чем закрыться.
	 * 0 — ждать бесконечно (старое поведение).
	 */
	public int arenaUnloadStopSeconds = 300;
	/** Сколько забегов сервер тянет одновременно. */
	public int maxActiveTrials = 3;
	/**
	 * Правила урона (тотемы, Реликвии, навыки). Выключите, если другой мод
	 * на урон/броню конфликтует с миксином.
	 */
	public boolean damageRules = true;
	/**
	 * Мобы празднества могут прокапывать проход к алтарю. Копают только
	 * внутри зоны арены и только «обычные» блоки — чужие постройки и
	 * неразрушимые блоки не трогают.
	 */
	public boolean mobDigging = true;
	/**
	 * Взрывы криперов арены ломают блоки. По умолчанию выключено: иначе
	 * крипер сносит постройки далеко за пределами площадки.
	 */
	public boolean creeperBlockDamage = false;
	/**
	 * Защита арены: во время забега посторонние не могут ломать и ставить
	 * блоки в зоне площадки. Владелец арены и операторы — могут.
	 */
	public boolean protectArena = true;

	/** Пакетный доступ — нужен тестам слияния конфига. */
	FestivalConfig() {
	}

	public static FestivalConfig get() {
		return instance;
	}

	// ------------------------------------------------------------------
	// Загрузка и сохранение
	// ------------------------------------------------------------------

	public static void load() {
		Path dir = FabricLoader.getInstance().getConfigDir();
		Path path = dir.resolve(FILE_NAME);
		Path legacy = dir.resolve(LEGACY_FILE_NAME);
		FestivalConfig loaded = new FestivalConfig();

		Path source = Files.exists(path) ? path : (Files.exists(legacy) ? legacy : null);
		if (source != null) {
			try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root.isJsonObject()) {
					merge(root.getAsJsonObject(), loaded);
				}
			} catch (IOException | JsonSyntaxException | IllegalStateException e) {
				FestivalMod.LOGGER.warn("[Festival] Конфиг не прочитан, взяты значения по умолчанию: {}",
						e.getMessage());
			}
			if (!source.equals(path)) {
				FestivalMod.LOGGER.info("[Festival] Конфиг {} перенесён в {}", LEGACY_FILE_NAME, FILE_NAME);
			}
		}

		loaded.sanitize();
		instance = loaded;
		save(path);
	}

	private static void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			FestivalMod.LOGGER.warn("[Festival] Конфиг не сохранён: {}", e.getMessage());
		}
	}

	/**
	 * Слить JSON поверх дефолтов: читаем только присутствующие ключи.
	 * Вынесено в отдельный статический метод, чтобы покрыть тестами.
	 */
	static void merge(JsonObject raw, FestivalConfig target) {
		target.stagesPerWave = intOr(raw, "stagesPerWave", target.stagesPerWave);
		target.dripTicks = intOr(raw, "dripTicks", target.dripTicks);
		target.dripMinTicks = intOr(raw, "dripMinTicks", target.dripMinTicks);
		target.stageTimeoutSeconds = intOr(raw, "stageTimeoutSeconds", target.stageTimeoutSeconds);
		target.stageTimeoutPerMobTicks = intOr(raw, "stageTimeoutPerMobTicks",
				target.stageTimeoutPerMobTicks);
		target.flagEveryWaves = intOr(raw, "flagEveryWaves", target.flagEveryWaves);
		target.stageStatBonus = doubleOr(raw, "stageStatBonus", target.stageStatBonus);
		target.intermissionTicks = intOr(raw, "intermissionTicks", target.intermissionTicks);
		target.spawnRadiusMin = doubleOr(raw, "spawnRadiusMin", target.spawnRadiusMin);
		target.spawnRadiusMax = doubleOr(raw, "spawnRadiusMax", target.spawnRadiusMax);
		target.spawnEffects = boolOr(raw, "spawnEffects", target.spawnEffects);
		target.announceEveryStage = boolOr(raw, "announceEveryStage", target.announceEveryStage);
		target.relicMissingGraceTicks = intOr(raw, "relicMissingGraceTicks",
				target.relicMissingGraceTicks);
		target.arenaUnloadStopSeconds = intOr(raw, "arenaUnloadStopSeconds",
				target.arenaUnloadStopSeconds);
		target.maxActiveTrials = intOr(raw, "maxActiveTrials", target.maxActiveTrials);
		target.damageRules = boolOr(raw, "damageRules", target.damageRules);
		target.mobDigging = boolOr(raw, "mobDigging", target.mobDigging);
		target.creeperBlockDamage = boolOr(raw, "creeperBlockDamage", target.creeperBlockDamage);
		target.protectArena = boolOr(raw, "protectArena", target.protectArena);

		JsonElement shares = raw.get("stageShares");
		if (shares != null && shares.isJsonArray()) {
			JsonArray array = shares.getAsJsonArray();
			double[] parsed = new double[array.size()];
			for (int i = 0; i < array.size(); i++) {
				JsonElement element = array.get(i);
				parsed[i] = element != null && element.isJsonPrimitive()
						? element.getAsDouble() : 0.0D;
			}
			if (parsed.length > 0) {
				target.stageShares = parsed;
			}
		}
	}

	private static int intOr(JsonObject raw, String key, int fallback) {
		JsonElement element = raw.get(key);
		if (element == null || !element.isJsonPrimitive()
				|| !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsInt();
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static double doubleOr(JsonObject raw, String key, double fallback) {
		JsonElement element = raw.get(key);
		if (element == null || !element.isJsonPrimitive()
				|| !element.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return element.getAsDouble();
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static boolean boolOr(JsonObject raw, String key, boolean fallback) {
		JsonElement element = raw.get(key);
		if (element == null || !element.isJsonPrimitive()
				|| !element.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return element.getAsBoolean();
	}

	// ------------------------------------------------------------------
	// Валидация
	// ------------------------------------------------------------------

	/** Зажать значения в безопасные рамки: конфиг — это ввод пользователя. */
	private void sanitize() {
		stagesPerWave = clamp(stagesPerWave, MIN_STAGES, MAX_STAGES);
		dripTicks = clamp(dripTicks, 1, 200);
		dripMinTicks = clamp(dripMinTicks, 1, dripTicks);
		stageTimeoutSeconds = clamp(stageTimeoutSeconds, 1, 600);
		stageTimeoutPerMobTicks = clamp(stageTimeoutPerMobTicks, 0, 200);
		flagEveryWaves = clamp(flagEveryWaves, 1, 100);
		stageStatBonus = clamp(stageStatBonus, 0.0D, 1.0D);
		intermissionTicks = clamp(intermissionTicks, 0, 20 * 60);
		spawnRadiusMin = clamp(spawnRadiusMin, 4.0D, 64.0D);
		spawnRadiusMax = clamp(spawnRadiusMax, spawnRadiusMin + 1.0D, 96.0D);
		relicMissingGraceTicks = clamp(relicMissingGraceTicks, 20, 20 * 60 * 10);
		maxActiveTrials = clamp(maxActiveTrials, 1, 16);
		arenaUnloadStopSeconds = clamp(arenaUnloadStopSeconds, 0, 3600);

		if (stageShares == null || stageShares.length == 0) {
			stageShares = new double[]{0.25D, 0.30D, 0.45D};
		}
		for (int i = 0; i < stageShares.length; i++) {
			stageShares[i] = Math.max(0.01D, stageShares[i]);
		}
	}

	public double stageShare(int stage) {
		int stages = stagesPerWave;
		int index = Math.max(0, Math.min(stages - 1, stage));
		if (stageShares.length == stages) {
			return stageShares[index];
		}
		// Другое число этапов — доли считаем линейно и нормализуем к 1.0
		double total = 0.0D;
		double[] raw = new double[stages];
		for (int i = 0; i < stages; i++) {
			raw[i] = 1.0D + (double) i / Math.max(1, stages - 1);
			total += raw[i];
		}
		return raw[index] / total;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
