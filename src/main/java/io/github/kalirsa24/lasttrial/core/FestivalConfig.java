package io.github.kalirsa24.lasttrial.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
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
 */
public final class FestivalConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MIN_STAGES = 1;
	private static final int MAX_STAGES = 8;

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

	private FestivalConfig() {
	}

	public static FestivalConfig get() {
		return instance;
	}

	public static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("festival.json");
		FestivalConfig loaded = new FestivalConfig();

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				com.google.gson.JsonObject raw =
						com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
				FestivalConfig parsed = GSON.fromJson(raw, FestivalConfig.class);
				if (parsed != null) {
					// Gson создаёт объект в обход конструктора: булевы поля
					// без ключа в файле получают false. Возвращаем дефолт.
					if (!raw.has("damageRules")) {
						parsed.damageRules = true;
					}
					if (!raw.has("maxActiveTrials")) {
						parsed.maxActiveTrials = 3;
					}
					if (!raw.has("arenaUnloadStopSeconds")) {
						parsed.arenaUnloadStopSeconds = 300;
					}
					loaded = parsed;
				}
			} catch (IOException | JsonSyntaxException | IllegalStateException e) {
				io.github.kalirsa24.lasttrial.FestivalMod.LOGGER.warn(
						"[Festival] Конфиг не прочитан, взяты значения по умолчанию: {}", e.getMessage());
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
			io.github.kalirsa24.lasttrial.FestivalMod.LOGGER.warn("[Festival] Конфиг не сохранён: {}", e.getMessage());
		}
	}

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
