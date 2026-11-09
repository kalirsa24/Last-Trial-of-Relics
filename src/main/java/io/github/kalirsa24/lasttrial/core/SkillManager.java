package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.network.FestivalNetworking;
import io.github.kalirsa24.lasttrial.item.TrophyItem;
import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Древо развития за награды празднества.
 *
 * Пять ветвей, каждая покупается навсегда и действует во всех забегах:
 * <ul>
 *   <li><b>Живучесть</b> — +2 HP за уровень (суммируется с зельями);</li>
 *   <li><b>Сила</b> — +3% исходящего урона за уровень;</li>
 *   <li><b>Оберег</b> — −3% входящего урона за уровень;</li>
 *   <li><b>Быстрота</b> — +3% скорости передвижения за уровень;</li>
 *   <li><b>Удача</b> — +0.3% шанса двойного урона за уровень (максимум 1.5%).</li>
 * </ul>
 */
public final class SkillManager {

	/** Ветви развития. */
	public enum Skill {
		VITALITY("vitality", 5),
		POWER("power", 5),
		WARD("ward", 5),
		SWIFTNESS("swiftness", 3),
		FORTUNE("fortune", 5);

		private final String id;
		private final int maxLevel;

		Skill(String id, int maxLevel) {
			this.id = id;
			this.maxLevel = maxLevel;
		}

		public String id() {
			return id;
		}

		public int maxLevel() {
			return maxLevel;
		}

		public String nameKey() {
			return "skill.lasttrial." + id;
		}

		public String descKey() {
			return "skill.lasttrial." + id + ".desc";
		}

		public UUID modifierId() {
			return UUID.nameUUIDFromBytes(("festival.skill." + id).getBytes(StandardCharsets.UTF_8));
		}

		public static Skill byId(String id) {
			for (Skill skill : values()) {
				if (skill.id.equals(id)) {
					return skill;
				}
			}
			return null;
		}
	}

	/** Цена следующего уровня в наградах празднества. */
	private static final int[] COST = {1, 2, 3, 5, 8};

	private static SkillData data;

	private SkillManager() {
	}

	// ------------------------------------------------------------------
	// Загрузка
	// ------------------------------------------------------------------

	public static void ensureLoaded(ServerLevel level) {
		if (data == null) {
			data = level.getDataStorage().computeIfAbsent(SkillData::load, SkillData::new, SkillData.NAME);
		}
	}

	public static void resetCache() {
		data = null;
	}

	public static int level(ServerPlayer player, Skill skill) {
		if (data == null) {
			return 0;
		}
		return data.levelOf(player.getUUID(), skill);
	}

	public static int[] levels(ServerPlayer player) {
		if (data == null) {
			return new int[Skill.values().length];
		}
		return data.levelsOf(player.getUUID());
	}

	/** Стоимость следующего уровня; 0 — ветка прокачана полностью. */
	public static int cost(Skill skill, int currentLevel) {
		if (currentLevel >= skill.maxLevel()) {
			return 0;
		}
		return COST[Math.min(COST.length - 1, currentLevel)];
	}

	// ------------------------------------------------------------------
	// Покупка
	// ------------------------------------------------------------------

	public static boolean buy(ServerPlayer player, Skill skill) {
		if (data == null) {
			ensureLoaded((ServerLevel) player.level());
		}
		int current = level(player, skill);
		if (current >= skill.maxLevel()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.skill_max"));
			return false;
		}
		int price = cost(skill, current);
		if (ShopManager.countTrophies(player) < price) {
			Msg.overlay(player, Component.translatable("message.lasttrial.no_trophies", price));
			return false;
		}
		consumeTrophies(player, price);
		data.setLevel(player.getUUID(), skill, current + 1);
		data.addSpent(player.getUUID(), price);
		applyAttributes(player);
		Msg.overlay(player, Component.translatable("message.lasttrial.skill_bought",
				Component.translatable(skill.nameKey()), current + 1));
		FestivalNetworking.openSkills(player);
		return true;
	}

	private static void consumeTrophies(ServerPlayer player, int amount) {
		int left = amount;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.TROPHY) && TrophyItem.ownedBy(stack, player.getUUID())) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
			}
		}
	}

	/** Сколько наград игрок уже вложил в древо (порог 20 меняет цены магазина). */
	public static int spentTrophies(ServerPlayer player) {
		return data == null ? 0 : data.spentOf(player.getUUID());
	}

	/**
	 * Один раз на игрока: старые награды без владельца привязываем к нему.
	 * После миграции «пустые» награды остаются недействительными — так админ
	 * не может выдать валюту другому игроку.
	 */
	public static void migrateLegacyTrophies(ServerPlayer player) {
		if (data == null || data.isMigrated(player.getUUID())) {
			return;
		}
		int bound = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.TROPHY) && TrophyItem.ownerOf(stack) == null) {
				TrophyItem.bind(stack, player.getUUID());
				bound += stack.getCount();
			}
		}
		data.markMigrated(player.getUUID());
		if (bound > 0) {
			Msg.overlay(player, Component.translatable("message.lasttrial.trophy_migrated", bound));
		}
	}

	public static void open(ServerPlayer player) {
		ensureLoaded((ServerLevel) player.level());
		FestivalNetworking.openSkills(player);
	}

	// ------------------------------------------------------------------
	// Боевые бонусы (вызываются из миксина урона)
	// ------------------------------------------------------------------

	/** Прибавка к исходящему урону (0..0.15). */
	public static float damageBonus(ServerPlayer player) {
		if (data == null) {
			return 0.0F;
		}
		return 0.03F * data.levelOf(player.getUUID(), Skill.POWER);
	}

	/** Снижение входящего урона (0..0.15). */
	public static double wardBonus(ServerPlayer player) {
		if (data == null) {
			return 0.0D;
		}
		return 0.03D * data.levelOf(player.getUUID(), Skill.WARD);
	}

	/** Шанс двойного удара (0..0.015). */
	public static float doubleChance(ServerPlayer player) {
		if (data == null) {
			return 0.0F;
		}
		return Math.min(0.015F, 0.003F * data.levelOf(player.getUUID(), Skill.FORTUNE));
	}

	public static boolean rollDoubleDamage(ServerPlayer player) {
		float chance = doubleChance(player);
		return chance > 0.0F && player.getRandom().nextFloat() < chance;
	}

	// ------------------------------------------------------------------
	// Атрибуты
	// ------------------------------------------------------------------

	/** Пересчитать постоянные модификаторы игрока (вход в игру, покупка, респавн). */
	public static void applyAttributes(ServerPlayer player) {
		apply(player, Attributes.MAX_HEALTH, Skill.VITALITY, 2.0D,
				AttributeModifier.Operation.ADDITION);
		apply(player, Attributes.MOVEMENT_SPEED, Skill.SWIFTNESS, 0.03D,
				AttributeModifier.Operation.MULTIPLY_TOTAL);

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void apply(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
							  Skill skill, double perLevel, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		instance.removeModifier(skill.modifierId());
		int level = data == null ? 0 : data.levelOf(player.getUUID(), skill);
		if (level <= 0) {
			return;
		}
		instance.addPermanentModifier(new AttributeModifier(skill.modifierId(),
				"lasttrial_skill_" + skill.id(), perLevel * level, operation));
	}
}
