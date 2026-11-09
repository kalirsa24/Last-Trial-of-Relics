package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Бонус от Реликвий, которые игрок носит в инвентаре.
 *
 * Держать Реликвию можно в любой ячейке — считается любая. Каждый тир снижает
 * получаемый урон: 1-й — на 15%, 2-й — на 30%, 3-й (вечная) — на 45%.
 * Считаются максимум {@link #MAX_RELICS} Реликвий (самые сильные), сумма
 * упирается в {@link #CAP}, чтобы урон не ушёл в минус.
 */
public final class RelicBonus {

	/** Сколько Реликвий в инвентаре учитывается одновременно. */
	public static final int MAX_RELICS = 4;
	/** Потолок суммарного снижения. */
	public static final double CAP = 0.75D;

	private RelicBonus() {
	}

	/** Снижение входящего урона для игрока (0..CAP). */
	public static double reduction(ServerPlayer player) {
		List<Double> values = new ArrayList<>();
		var inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize(); i++) {
			double value = valueOf(inventory.getItem(i));
			if (value > 0.0D) {
				values.add(value);
			}
		}
		if (values.isEmpty()) {
			return 0.0D;
		}

		values.sort(java.util.Comparator.reverseOrder());
		double total = 0.0D;
		for (int i = 0; i < Math.min(MAX_RELICS, values.size()); i++) {
			total += values.get(i);
		}
		return Math.min(CAP, total);
	}

	/** Вклад одной Реликвии в снижение урона. */
	public static double valueOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0.0D;
		}
		return valueOf(stack.getItem());
	}

	public static double valueOf(Item item) {
		if (item == ModItems.RELIC_BASIC) {
			return 0.15D;
		}
		if (item == ModItems.RELIC) {
			return 0.30D;
		}
		if (item == ModItems.RELIC_ETERNAL) {
			return 0.45D;
		}
		return 0.0D;
	}
}
