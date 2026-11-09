package io.github.kalirsa24.lasttrial.item;

import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Тотем усиления: 15 уровней, каждый даёт прибавку к исходящему урону.
 *
 * Таблица прибавок (доля от базового урона):
 * <pre>
 *   I   +3%    II  +6%    III +9%    IV  +12%   V   +15%
 *   VI  +30%   VII +45%   VIII +50%  IX  +60%   X   +75%
 *   XI  +80%   XII +95%   XIII +105% XIV +115%  XV  +125%
 * </pre>
 *
 * В «щите» одновременно учитываются только два лучших тотема
 * ({@link #SHIELD_SLOTS}) — остальные лежат про запас и тратятся при смерти.
 * Тотем в инвентаре работает как тотем бессмертия: при смертельном уроне
 * он расходуется и спасает носителя.
 */
public final class TotemOfPower {

	public static final int MAX_TIER = 15;

	/** Сколько тотемов одновременно дают бонус к урону. */
	public static final int SHIELD_SLOTS = 2;

	private static final String TAG_TIER = "Tier";
	private static final String TAG_MODEL = "CustomModelData";

	/** Прибавка к исходящему урону по тирам (индекс 0 — I тир). */
	private static final float[] BONUS = {
			0.03F, 0.06F, 0.09F, 0.12F, 0.15F,
			0.30F, 0.45F, 0.50F, 0.60F, 0.75F,
			0.80F, 0.95F, 1.05F, 1.15F, 1.25F
	};

	private static final String[] ROMAN = {
			"I", "II", "III", "IV", "V", "VI", "VII", "VIII",
			"IX", "X", "XI", "XII", "XIII", "XIV", "XV"
	};

	private TotemOfPower() {
	}

	// ------------------------------------------------------------------
	// Данные стака
	// ------------------------------------------------------------------

	public static int getTier(ItemStack stack) {
		if (!stack.is(ModItems.TOTEM_OF_POWER)) {
			return 0;
		}
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG_TIER)) {
			return 1;
		}
		return Math.max(1, Math.min(MAX_TIER, tag.getInt(TAG_TIER)));
	}

	public static void setTier(ItemStack stack, int tier) {
		int clamped = Math.max(1, Math.min(MAX_TIER, tier));
		CompoundTag tag = stack.getOrCreateTag();
		tag.putInt(TAG_TIER, clamped);
		// Модель выбирается по «полосе» глаз: синие / зелёные / красные
		tag.putInt(TAG_MODEL, band(clamped));
	}

	public static ItemStack create(int tier) {
		ItemStack stack = new ItemStack(ModItems.TOTEM_OF_POWER);
		setTier(stack, tier);
		return stack;
	}

	// ------------------------------------------------------------------
	// Бонусы
	// ------------------------------------------------------------------

	/** Прибавка к исходящему урону для одного тотема (0..1.25). */
	public static float damageBonus(int tier) {
		if (tier <= 0) {
			return 0.0F;
		}
		return BONUS[Math.min(MAX_TIER, tier) - 1];
	}

	/** Лучший тотем в инвентаре (0 — если нет). */
	public static int getPlayerTier(Player player) {
		int best = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.TOTEM_OF_POWER)) {
				best = Math.max(best, getTier(stack));
			}
		}
		return best;
	}

	/**
	 * Суммарная прибавка к исходящему урону: два лучших тотема в инвентаре.
	 */
	public static float playerBonus(Player player) {
		int[] best = bestTiers(player);
		return damageBonus(best[0]) + damageBonus(best[1]);
	}

	/** Два старших тира в инвентаре по убыванию (0 — пустой слот). */
	public static int[] bestTiers(Player player) {
		int first = 0;
		int second = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.is(ModItems.TOTEM_OF_POWER)) {
				continue;
			}
			int tier = getTier(stack);
			if (tier > first) {
				second = first;
				first = tier;
			} else if (tier > second) {
				second = tier;
			}
		}
		return new int[]{first, second};
	}

	/** Первый тотем в инвентаре (для расхода при смерти); {@code null} — если нет. */
	public static ItemStack findTotem(Player player) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.TOTEM_OF_POWER)) {
				return stack;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Отображение
	// ------------------------------------------------------------------

	/** Полоса оформления: 1 — синие глаза, 2 — зелёные, 3 — красные. */
	public static int band(int tier) {
		if (tier <= 5) {
			return 1;
		}
		return tier <= 10 ? 2 : 3;
	}

	/** Римское имя тира. */
	public static String roman(int tier) {
		int index = Math.max(1, Math.min(MAX_TIER, tier)) - 1;
		return ROMAN[index];
	}

	/** Подпись прибавки для подсказки: «+15%». */
	public static String percent(int tier) {
		return "+" + Math.round(damageBonus(tier) * 100.0F) + "%";
	}
}
