package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.FestivalMod;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Волны из датапака: {@code data/<namespace>/waves.json}.
 *
 * Если для волны и этапа есть явное описание — используется оно, иначе
 * состав собирается процедурно из бюджета. Формат:
 *
 * <pre>
 * { "waves": [ { "wave": 3, "stages": [
 *     { "mobs": [ { "id": "minecraft:zombie", "count": 6 } ] },
 *     { "mobs": [ { "id": "lasttrial:creeper", "count": 2 } ] },
 *     { "mobs": [ { "id": "lasttrial:archer_skeleton", "count": 1 } ] } ] } ] }
 * </pre>
 *
 * Файл перечитывается на старте сервера и на {@code /reload}; при ошибке
 * парсинга остаётся предыдущая версия — сервер не падает из-за кривого JSON.
 *
 * Раньше здесь использовался {@code FileToIdConverter.json("festival/waves")},
 * который искал {@code data/<ns>/festival/waves/*.json} — то есть не находил
 * ни наш собственный файл, ни файлы датапаков. Теперь перебираем namespace'ы
 * и читаем {@code waves.json} в корне каждого.
 */
public final class WaveLibrary {

	/** Предохранитель от конфига-бомбы: больше мобов на этап не читаем. */
	private static final int MAX_MOBS_PER_STAGE = 200;

	private static volatile Map<Integer, List<List<WaveBudget.Type>>> waves = Map.of();

	private WaveLibrary() {
	}

	/** Состав этапа из датапака, если он там описан. */
	public static Optional<List<WaveBudget.Type>> stage(int wave, int stage) {
		List<List<WaveBudget.Type>> stages = waves.get(wave);
		if (stages == null || stage < 0 || stage >= stages.size()) {
			return Optional.empty();
		}
		return Optional.of(stages.get(stage));
	}

	public static int definedWaves() {
		return waves.size();
	}

	public static void reload(ResourceManager manager) {
		Map<Integer, List<List<WaveBudget.Type>>> parsed = new HashMap<>();

		for (String namespace : manager.getNamespaces()) {
			ResourceLocation id = new ResourceLocation(namespace, "waves.json");
			Optional<Resource> resource = manager.getResource(id);
			if (resource.isEmpty()) {
				continue;
			}
			try (Reader reader = resource.get().openAsReader()) {
				parse(reader, parsed);
			} catch (Exception e) {
				FestivalMod.LOGGER.warn("[Festival] Волны из {} не прочитаны: {}", id, e.getMessage());
			}
		}

		waves = Map.copyOf(parsed);
		if (!waves.isEmpty()) {
			FestivalMod.LOGGER.info("[Festival] Загружено волн из датапака: {}", waves.size());
		}
	}

	private static void parse(Reader reader, Map<Integer, List<List<WaveBudget.Type>>> out) {
		JsonElement rootElement = JsonParser.parseReader(reader);
		if (!rootElement.isJsonObject()) {
			return;
		}
		JsonObject root = rootElement.getAsJsonObject();
		JsonArray wavesArray = root.getAsJsonArray("waves");
		if (wavesArray == null) {
			return;
		}

		for (JsonElement waveElement : wavesArray) {
			if (!waveElement.isJsonObject()) {
				continue;
			}
			JsonObject waveObject = waveElement.getAsJsonObject();
			int wave = waveObject.has("wave") ? waveObject.get("wave").getAsInt() : -1;
			JsonArray stagesArray = waveObject.getAsJsonArray("stages");
			if (wave < 1 || stagesArray == null) {
				continue;
			}

			List<List<WaveBudget.Type>> stages = new ArrayList<>();
			for (JsonElement stageElement : stagesArray) {
				stages.add(parseStage(stageElement));
			}
			out.put(wave, List.copyOf(stages));
		}
	}

	private static List<WaveBudget.Type> parseStage(JsonElement stageElement) {
		List<WaveBudget.Type> mobs = new ArrayList<>();
		if (!stageElement.isJsonObject()) {
			return List.of();
		}
		JsonArray mobsArray = stageElement.getAsJsonObject().getAsJsonArray("mobs");
		if (mobsArray == null) {
			return List.of();
		}

		for (JsonElement mobElement : mobsArray) {
			if (!mobElement.isJsonObject()) {
				continue;
			}
			JsonObject mobObject = mobElement.getAsJsonObject();
			String id = mobObject.has("id") ? mobObject.get("id").getAsString() : "";
			int count = mobObject.has("count") ? mobObject.get("count").getAsInt() : 0;

			WaveBudget.Type type = WaveBudget.Type.byId(id);
			if (type == null) {
				FestivalMod.LOGGER.warn("[Festival] Неизвестный моб в waves.json: {}", id);
				continue;
			}
			count = Math.max(0, Math.min(MAX_MOBS_PER_STAGE, count));
			for (int i = 0; i < count && mobs.size() < MAX_MOBS_PER_STAGE; i++) {
				mobs.add(type);
			}
		}
		return List.copyOf(mobs);
	}
}
