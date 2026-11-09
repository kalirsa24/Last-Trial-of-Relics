package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.core.Msg;

import io.github.kalirsa24.lasttrial.item.TrophyItem;
import io.github.kalirsa24.lasttrial.network.FestivalNetworking;
import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Магазин: каталог по вкладкам + логика покупки.
 *
 * GUI (ShopScreen) и сервер строят каталог одним и тем же кодом, поэтому
 * товар однозначно определяется строковым id (например {@code minecraft:bread}
 * или {@code tacz:ak47}) — индекс в списке больше не пересылается.
 *
 * Ассортимент оружия зависит от уровня Реликвии: на 10 волнах только пистолеты,
 * на 25 — плюс ПП и винтовки, на вечной — всё. Обвесы и патроны доступны всегда.
 */
public class ShopManager {

	/** Динамические карточки: цену считаем на месте, предмета в каталоге нет. */
	public static final String HEAL_RELIC_ID = "lasttrial:heal_relic";
	/** Мгновенный пропуск одной волны (без предмета). */
	public static final String SKIP_NOW_ID = "lasttrial:skip_now";
	/** Включение хард-режима на текущее испытание. */
	public static final String HARD_MODE_ID = "lasttrial:hard_mode";
	/** Остановить волну: убрать мобов, спавн замирает. */
	public static final String STOP_WAVE_ID = "lasttrial:stop_wave";
	/** Продолжить остановленную волну (всегда бесплатно). */
	public static final String RESUME_WAVE_ID = "lasttrial:resume_wave";

	/** Вкладки магазина — порядок задаёт порядок кнопок в GUI. */
	public enum Category {
		BLOCKS("blocks"),
		WEAPONS("weapons"),
		AMMO("ammo"),
		ATTACHMENTS("attachments"),
		KITS("kits"),
		FOOD("food"),
		BUFFS("buffs"),
		GOLEMS("golems"),
		SETTINGS("settings");

		private final String id;

		Category(String id) {
			this.id = id;
		}

		public String id() {
			return id;
		}

		/** Ключ локализации подписи вкладки. */
		public String nameKey() {
			return "tab.lasttrial." + id;
		}
	}

	/**
	 * Товар.
	 *
	 * @param icon        что рисуется на карточке
	 * @param contents    что реально попадает в инвентарь (у наборов несколько стаков,
	 *                    у «починки реликвии» предмета нет вообще — только иконка)
	 * @param price       цена в монетах празднества (до множителя развития)
	 * @param minTier     с какого уровня Реликвии товар вообще виден в магазине
	 * @param trophies    сколько наград нужно сверх монет (обязательная доплата)
	 * @param trophyPrice альтернативная цена в наградах: платить можно монетами
	 *                    <b>или</b> наградами (0 — альтернативы нет)
	 */
	public record Good(String id, Category category, Component name, ItemStack icon,
					   List<ItemStack> contents, int price, RelicTier minTier, int trophies,
					   int trophyPrice) {

		public static Good of(String id, Category category, Component name, int price, ItemStack... contents) {
			List<ItemStack> list = List.of(contents);
			return new Good(id, category, name, list.isEmpty() ? ItemStack.EMPTY : list.get(0),
					list, price, RelicTier.BASIC, 0, 0);
		}

		public Good withMinTier(RelicTier tier) {
			return new Good(id, category, name, icon, contents, price, tier, trophies, trophyPrice);
		}

		public Good withTrophies(int count) {
			return new Good(id, category, name, icon, contents, price, minTier, count, trophyPrice);
		}

		public Good withTrophyPrice(int count) {
			return new Good(id, category, name, icon, contents, price, minTier, trophies, count);
		}

		public ItemStack preview() {
			return icon;
		}
	}

	/** Полный каталог: строится один раз, без фильтра по уровню. */
	private static volatile List<Good> everything;
	/** Отфильтрованные по уровню Реликвии списки. */
	private static final Map<RelicTier, List<Good>> byTier = new EnumMap<>(RelicTier.class);

	/** Какой уровень Реликвии открыл игрок (для проверки покупки). */
	private static final Map<UUID, RelicTier> shopTier = new java.util.HashMap<>();

	/** Забыть состояние игрока при выходе: карты не должны расти бесконечно. */
	public static void forget(UUID player) {
		shopTier.remove(player);
		LAST_BUY.remove(player);
	}

	/** Время последней покупки — защита от спама пакетами. */
	private static final Map<UUID, Long> LAST_BUY = new java.util.HashMap<>();
	private static final long BUY_COOLDOWN_MS = 120L;

	private static List<Good> buildOnce() {
		List<Good> local = everything;
		if (local == null) {
			synchronized (ShopManager.class) {
				local = everything;
				if (local == null) {
					local = List.copyOf(build());
					everything = local;
				}
			}
		}
		return local;
	}

	/** Весь каталог без фильтра (для {@link #find}). */
	public static List<Good> all() {
		return buildOnce();
	}

	/** Каталог, доступный на этом уровне Реликвии. */
	public static List<Good> catalogue(RelicTier tier) {
		synchronized (byTier) {
			return byTier.computeIfAbsent(tier, t -> {
				List<Good> result = new ArrayList<>();
				for (Good good : buildOnce()) {
					if (t.allows(good.minTier())) {
						result.add(good);
					}
				}
				return List.copyOf(result);
			});
		}
	}

	public static List<Good> inCategory(Category category, RelicTier tier) {
		List<Good> result = new ArrayList<>();
		for (Good good : catalogue(tier)) {
			if (good.category() == category) {
				result.add(good);
			}
		}
		return result;
	}

	public static Good find(String id) {
		for (Good good : buildOnce()) {
			if (good.id().equals(id)) {
				return good;
			}
		}
		return null;
	}

	/** Цена карточки (динамические цены считает GUI по данным забега). */
	public static int priceFor(Good good) {
		return good.price();
	}


	// ------------------------------------------------------------------
	// Каталог
	// ------------------------------------------------------------------

	private static List<Good> build() {
		List<Good> all = new ArrayList<>();
		addBlocks(all);
		TaczShop.addWeapons(all);
		TaczShop.addAmmo(all);
		TaczShop.addAttachments(all);
		addKits(all);
		addFood(all);
		addBuffs(all);
		addGolems(all);
		addSettings(all);
		return all;
	}

	/** Реликвии (ядро арены) + стройматериалы для платформы. */
	private static void addBlocks(List<Good> all) {
		Category c = Category.BLOCKS;
		// Реликвии видны всегда: за них платят трофеями, а не уровнем текущей Реликвии
		all.add(item("lasttrial:relic_basic", c, "good.lasttrial.relic_basic", 50,
				new ItemStack(ModItems.RELIC_BASIC)));
		all.add(item("lasttrial:relic", c, "good.lasttrial.relic", 110,
				new ItemStack(ModItems.RELIC)).withTrophies(3));
		all.add(item("lasttrial:relic_eternal", c, "good.lasttrial.relic_eternal", 220,
				new ItemStack(ModItems.RELIC_ETERNAL)).withTrophies(10));
		all.add(item("lasttrial:arena_tile", c, "good.lasttrial.arena_tile", 6, new ItemStack(ModItems.ARENA_TILE, 16)));
		all.add(item("minecraft:dirt", c, "good.lasttrial.dirt", 2, new ItemStack(Items.DIRT, 32)));
		all.add(item("minecraft:grass_block", c, "good.lasttrial.grass_block", 3, new ItemStack(Items.GRASS_BLOCK, 32)));
		all.add(item("minecraft:stone", c, "good.lasttrial.stone", 3, new ItemStack(Items.STONE, 32)));
		all.add(item("minecraft:cobblestone", c, "good.lasttrial.cobblestone", 2, new ItemStack(Items.COBBLESTONE, 32)));
		all.add(item("minecraft:oak_planks", c, "good.lasttrial.oak_planks", 3, new ItemStack(Items.OAK_PLANKS, 32)));
		all.add(item("minecraft:glass", c, "good.lasttrial.glass", 4, new ItemStack(Items.GLASS, 16)));
	}

	/** Готовые наборы: один клик — сразу комплект. */
	private static void addKits(List<Good> all) {
		Category c = Category.KITS;
		all.add(Good.of("lasttrial:kit_archer", c, Component.translatable("good.lasttrial.kit_archer"), 45,
				new ItemStack(Items.BOW),
				new ItemStack(Items.ARROW, 64),
				new ItemStack(Items.LEATHER_CHESTPLATE),
				new ItemStack(Items.BREAD, 16)));
		all.add(Good.of("lasttrial:kit_fighter", c, Component.translatable("good.lasttrial.kit_fighter"), 60,
				new ItemStack(Items.IRON_SWORD),
				new ItemStack(Items.IRON_CHESTPLATE),
				new ItemStack(Items.IRON_HELMET),
				new ItemStack(Items.SHIELD),
				new ItemStack(Items.BREAD, 16)));
		all.add(Good.of("lasttrial:kit_tank", c, Component.translatable("good.lasttrial.kit_tank"), 110,
				new ItemStack(Items.DIAMOND_CHESTPLATE),
				new ItemStack(Items.DIAMOND_LEGGINGS),
				new ItemStack(Items.IRON_HELMET),
				new ItemStack(Items.SHIELD),
				new ItemStack(Items.BREAD, 32)).withMinTier(RelicTier.GREATER));
	}

	private static void addFood(List<Good> all) {
		Category c = Category.FOOD;
		all.add(item("minecraft:bread", c, "good.lasttrial.bread", 3, new ItemStack(Items.BREAD, 8)));
		all.add(item("minecraft:cooked_beef", c, "good.lasttrial.cooked_beef", 6, new ItemStack(Items.COOKED_BEEF, 8)));
		all.add(item("minecraft:baked_potato", c, "good.lasttrial.baked_potato", 6, new ItemStack(Items.BAKED_POTATO, 16)));
		all.add(item("minecraft:golden_carrot", c, "good.lasttrial.golden_carrot", 10, new ItemStack(Items.GOLDEN_CARROT, 8)));
		// Золотые яблоки: 150 монет или 3 награды празднества
		all.add(item("minecraft:golden_apple", c, "good.lasttrial.golden_apple_x4", 150,
				new ItemStack(Items.GOLDEN_APPLE, 4)).withTrophyPrice(3));
		// Зачарованное яблоко: 750 монет или 15 наград
		all.add(item("minecraft:enchanted_golden_apple", c, "good.lasttrial.enchanted_apple", 750,
				new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)).withTrophyPrice(15));
		all.add(item("lasttrial:feast", c, "good.lasttrial.feast", 40, new ItemStack(ModItems.FEAST)));
	}

	/** Усиления: зелья + тотем усиления (динамическая карточка). */
	private static void addBuffs(List<Good> all) {
		Category c = Category.BUFFS;
		all.add(item("minecraft:potion_healing", c, "good.lasttrial.potion_healing", 15,
				potion(Potions.STRONG_HEALING)));
		all.add(item("minecraft:potion_strength", c, "good.lasttrial.potion_strength", 20,
				potion(Potions.STRONG_STRENGTH)));
		all.add(item("minecraft:potion_swiftness", c, "good.lasttrial.potion_swiftness", 15,
				potion(Potions.STRONG_SWIFTNESS)));
		all.add(item("minecraft:potion_regeneration", c, "good.lasttrial.potion_regeneration", 25,
				potion(Potions.LONG_REGENERATION)));
		all.add(item("minecraft:potion_fire_resistance", c, "good.lasttrial.potion_fire_resistance", 18,
				potion(Potions.LONG_FIRE_RESISTANCE)));
		all.add(item("minecraft:potion_night_vision", c, "good.lasttrial.potion_night_vision", 12,
				potion(Potions.LONG_NIGHT_VISION)));
		all.add(new Good(HEAL_RELIC_ID, c, Component.translatable("good.lasttrial.heal_relic"),
				new ItemStack(ModItems.RELIC), List.of(), 30, RelicTier.BASIC, 0, 0));
	}

	private static void addGolems(List<Good> all) {
		Category c = Category.GOLEMS;
		all.add(item("minecraft:iron_golem_spawn_egg", c, "good.lasttrial.iron_golem", 120,
				new ItemStack(Items.IRON_GOLEM_SPAWN_EGG)));
		all.add(item("minecraft:snow_golem_spawn_egg", c, "good.lasttrial.snow_golem", 40,
				new ItemStack(Items.SNOW_GOLEM_SPAWN_EGG)));
	}

	/** Настройки забега: пропуск, хард-режим, остановка и продолжение волны. */
	private static void addSettings(List<Good> all) {
		Category c = Category.SETTINGS;
		all.add(item("lasttrial:wave_skip", c, "good.lasttrial.wave_skip", 35, new ItemStack(ModItems.WAVE_SKIP)));
		all.add(new Good(SKIP_NOW_ID, c, Component.translatable("good.lasttrial.skip_now"),
				new ItemStack(ModItems.WAVE_SKIP), List.of(), 15, RelicTier.BASIC, 0, 0));
		all.add(new Good(HARD_MODE_ID, c, Component.translatable("good.lasttrial.hard_mode"),
				new ItemStack(Items.NETHER_STAR), List.of(), 120, RelicTier.BASIC, 0, 0));
		all.add(new Good(STOP_WAVE_ID, c, Component.translatable("good.lasttrial.stop_wave"),
				new ItemStack(Items.BARRIER), List.of(), 0, RelicTier.BASIC, 0, 0));
		all.add(new Good(RESUME_WAVE_ID, c, Component.translatable("good.lasttrial.resume_wave"),
				new ItemStack(Items.LIME_DYE), List.of(), 0, RelicTier.BASIC, 0, 0));
	}

	private static Good item(String id, Category category, String nameKey, int price, ItemStack stack) {
		return Good.of(id, category, Component.translatable(nameKey), price, stack);
	}

	// ------------------------------------------------------------------
	// Экономика
	// ------------------------------------------------------------------

	public static int countCoins(ServerPlayer player) {
		return count(player, ModItems.FESTIVAL_COIN);
	}

	/** Награды считаются только привязанные к этому игроку. */
	public static int countTrophies(ServerPlayer player) {
		int total = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.TROPHY) && TrophyItem.ownedBy(stack, player.getUUID())) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int count(ServerPlayer player, net.minecraft.world.item.Item item) {
		int total = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void consume(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
		int left = amount;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (left <= 0) {
				break;
			}
			ItemStack stack = inventory.getItem(i);
			if (!stack.is(item)) {
				continue;
			}
			// награды списываем только у владельца
			if (item == ModItems.TROPHY && !TrophyItem.ownedBy(stack, player.getUUID())) {
				continue;
			}
			int take = Math.min(left, stack.getCount());
			stack.shrink(take);
			left -= take;
		}
	}

	// ------------------------------------------------------------------
	// Цены и наценки
	// ------------------------------------------------------------------

	/** Множитель цен после вложений в древо: больше 20 наград — всё +20%. */
	public static double priceMultiplier(ServerPlayer player) {
		return Economy.priceMultiplier(SkillManager.spentTrophies(player));
	}

	/** Итоговая цена в монетах с учётом развития игрока. */
	public static int finalPrice(Good good, ServerPlayer player) {
		return Economy.finalPrice(good.price(), SkillManager.spentTrophies(player));
	}

	/**
	 * Наценка на оружие и патроны по типу ствола.
	 *
	 * @see Economy#markup(String, int)
	 */
	public static int markup(String itemId, int base) {
		return Economy.markup(itemId, base);
	}

	// ------------------------------------------------------------------
	// Уровень Реликвии, на котором открыт магазин
	// ------------------------------------------------------------------

	public static void rememberTier(ServerPlayer player, RelicTier tier) {
		shopTier.put(player.getUUID(), tier);
	}

	/** Уровень Реликвии: запомненный при открытии магазина, иначе текущий забег. */
	public static RelicTier tierFor(ServerPlayer player) {
		RelicTier remembered = shopTier.get(player.getUUID());
		if (remembered != null) {
			return remembered;
		}
		Trial trial = TrialManager.of(player);
		return trial != null ? trial.tier() : RelicTier.GREATER;
	}

	/** Открыть GUI магазина игроку. */
	public static void open(ServerPlayer player, RelicTier tier) {
		rememberTier(player, tier);
		FestivalNetworking.openShop(player);
	}

	// ------------------------------------------------------------------
	// Покупка
	// ------------------------------------------------------------------

	public static boolean buy(ServerPlayer player, String goodId) {
		// Защита от спама пакетами: не чаще одного запроса в 120 мс на игрока
		long now = System.currentTimeMillis();
		Long last = LAST_BUY.get(player.getUUID());
		if (last != null && now - last < BUY_COOLDOWN_MS) {
			return false;
		}
		LAST_BUY.put(player.getUUID(), now);

		Good good = find(goodId);
		if (good == null) {
			Msg.overlay(player, Component.translatable("message.lasttrial.no_good"));
			return false;
		}

		// Проверяем по фактическому тиру текущего забега, а не по «запомненному»
		// при открытии магазина: иначе можно открыть витрину на вечной Реликвии
		// и купить её товары, стоя у малой.
		Trial activeTrial = TrialManager.of(player);
		RelicTier currentTier = activeTrial != null ? activeTrial.tier() : tierFor(player);
		if (!currentTier.allows(good.minTier())) {
			Msg.overlay(player, Component.translatable("message.lasttrial.tier_locked",
					Component.translatable("tier.lasttrial." + good.minTier().getId())));
			return false;
		}

		// настройки забега имеют смысл только во время испытания
		boolean setting = SKIP_NOW_ID.equals(goodId) || HARD_MODE_ID.equals(goodId)
				|| STOP_WAVE_ID.equals(goodId) || RESUME_WAVE_ID.equals(goodId);
		Trial trial = activeTrial;
		if (setting && trial == null) {
			Msg.overlay(player, Component.translatable("message.lasttrial.mode_no_trial"));
			return false;
		}
		if (HARD_MODE_ID.equals(goodId) && trial != null && trial.hardMode()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.hard_already"));
			return false;
		}
		// последнюю волну не пропускаем — победа должна быть заработана
		if (SKIP_NOW_ID.equals(goodId) && trial != null
				&& trial.currentWave() >= trial.maxWaves() - 1) {
			Msg.overlay(player, Component.translatable("message.lasttrial.skip_last_wave"));
			return false;
		}
		if (STOP_WAVE_ID.equals(goodId) && trial != null && trial.isPaused()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.already_stopped"));
			return false;
		}
		if (RESUME_WAVE_ID.equals(goodId) && trial != null && !trial.isPaused()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.not_stopped"));
			return false;
		}

		// починку продаём только когда есть что чинить — иначе монеты сгорят зря
		if (HEAL_RELIC_ID.equals(goodId) && (trial == null || !trial.isRelicPresent())) {
			Msg.overlay(player, Component.translatable("message.lasttrial.no_relic_to_heal"));
			return false;
		}

		// трофеи — отдельная плата за старшие Реликвии
		int trophiesNeeded = good.trophies();
		if (trophiesNeeded > 0 && countTrophies(player) < trophiesNeeded) {
			Msg.overlay(player, Component.translatable("message.lasttrial.no_trophies", trophiesNeeded));
			return false;
		}

		// остановка: первая бесплатна, дальше — фиксированная цена
		int price = STOP_WAVE_ID.equals(goodId) && trial != null
				? trial.stopPrice()
				: finalPrice(good, player);

		// «150 монет или 3 награды»: если монет не хватает, платим наградами
		boolean payWithTrophies = false;
		if (countCoins(player) < price) {
			if (good.trophyPrice() > 0 && countTrophies(player) >= good.trophyPrice()) {
				payWithTrophies = true;
			} else {
				Msg.overlay(player, Component.translatable("message.lasttrial.no_coins_or_trophies",
						price, good.trophyPrice()));
				FestivalNetworking.syncTrial(player);
				return false;
			}
		}

		if (payWithTrophies) {
			consume(player, ModItems.TROPHY, good.trophyPrice());
		} else {
			consume(player, ModItems.FESTIVAL_COIN, price);
		}
		if (trophiesNeeded > 0) {
			consume(player, ModItems.TROPHY, trophiesNeeded);
		}

		if (SKIP_NOW_ID.equals(goodId)) {
			// дешёвый вариант: одна волна, без усиления следующей
			if (trial != null) {
				trial.skipWaves(1, false);
			}
			Msg.overlay(player, Component.translatable("message.lasttrial.skip_now_done"));
		} else if (HARD_MODE_ID.equals(goodId)) {
			if (trial != null) {
				trial.setHardMode(true);
				trial.broadcast(Component.translatable("message.lasttrial.hard_on"));
			}
		} else if (STOP_WAVE_ID.equals(goodId)) {
			if (trial != null) {
				trial.markStopUsed();
				trial.stopWave();
			}
		} else if (RESUME_WAVE_ID.equals(goodId)) {
			if (trial != null) {
				trial.resumeWave();
			}
		} else if (HEAL_RELIC_ID.equals(goodId)) {
			if (trial != null) {
				trial.healRelic(25);
			}
			Msg.overlay(player, Component.translatable("message.lasttrial.bought", good.name()));
		} else {
			for (ItemStack stack : good.contents()) {
				give(player, stack.copy());
			}
			Msg.overlay(player, Component.translatable("message.lasttrial.bought", good.name()));
		}

		FestivalNetworking.syncTrial(player);
		return true;
	}

	private static ItemStack potion(Potion potion) {
		ItemStack stack = new ItemStack(Items.POTION);
		PotionUtils.setPotion(stack, potion);
		return stack;
	}

	/** Выдать предмет; если инвентарь полон — выбросить под ноги, а не потерять. */
	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}
}
