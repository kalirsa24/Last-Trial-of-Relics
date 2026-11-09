package com.example.festival.core;

import com.example.festival.item.TrophyItem;
import com.example.festival.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Награды забега.
 *
 * Внутри испытания игрок копит монеты (валюта магазина) — они сгорают по итогу.
 * Между испытаниями переносятся награды празднества: они персональные,
 * привязаны к владельцу и не передаются другим игрокам.
 *
 * Множитель награды считает сам забег: игрок, который пять минут не отходил
 * от арены дольше чем на 15 секунд, получает вдвое больше.
 */
public final class RewardManager {

	private RewardManager() {
	}

	/** Награда за зачищенную волну. В хард-режиме монет вдвое больше. */
	public static void onWaveCleared(Trial trial, int wave) {
		int base = (2 + wave / 2) * (trial.hardMode() ? 2 : 1);
		// Бонусная платформа 30×30: лишняя монета за каждую волну
		if (trial.hasBonusArena()) {
			base += 1;
		}

		for (ServerPlayer player : trial.participants()) {
			int coins = (int) Math.round(base * trial.rewardMultiplier(player));
			player.getInventory().add(new ItemStack(ModItems.FESTIVAL_COIN, coins));
		}

		// Каждые 5 волн — награда, пир и бесплатная жизнь
		if (wave % 5 == 0) {
			for (ServerPlayer player : trial.participants()) {
				int trophies = (int) Math.round(trial.rewardMultiplier(player));
				TrophyItem.grant(player, trophies);
				player.getInventory().add(new ItemStack(ModItems.FEAST, 1));
			}
			trial.broadcast(Component.translatable("message.festival.wave_trophy", wave));

			if (trial.addLife()) {
				trial.broadcast(Component.translatable("message.festival.free_repair",
						trial.lives(), GameManager.MAX_LIVES));
			}
		}

		// Флаговая волна: лишняя награда за натиск
		if (trial.isFlagWave()) {
			for (ServerPlayer player : trial.participants()) {
				TrophyItem.grant(player, (int) Math.round(trial.rewardMultiplier(player)));
			}
			trial.broadcast(Component.translatable("message.festival.flag_bonus", wave));
		}

		// Каждые 15 волн — пропуск волн
		if (wave % 15 == 0) {
			for (ServerPlayer player : trial.participants()) {
				player.getInventory().add(new ItemStack(ModItems.WAVE_SKIP, 1));
			}
			trial.broadcast(Component.translatable("message.festival.wave_skip_reward", wave));
		}

		trial.broadcast(Component.translatable("message.festival.wave_reward", wave, base));
	}

	/**
	 * Итог испытания: награды выдаются, монеты сжигаются.
	 * Победа даёт полную ставку наград, поражение — половину.
	 */
	public static void finish(Trial trial, boolean victory) {
		int waves = trial.currentWave();
		int tierReward = trial.tier().getTrophyReward();
		int base = victory ? tierReward : tierReward / 2;
		if (trial.hardMode()) {
			base *= 2;
		}
		// Полная платформа 30×30: дополнительная награда по победе
		if (victory && trial.hasBonusArena()) {
			base += 1;
		}

		for (ServerPlayer player : trial.participants()) {
			int trophies = (int) Math.round(base * trial.rewardMultiplier(player));
			if (trophies > 0) {
				TrophyItem.grant(player, trophies);
			}

			if (victory) {
				int keys = 1 + waves / 50;
				player.getInventory().add(new ItemStack(ModItems.FESTIVAL_KEY, keys));
			} else if (waves >= 10) {
				player.getInventory().add(new ItemStack(ModItems.FEAST, 1));
			}

			int coinsBurned = clearCoins(player);
			player.sendSystemMessage(Component.translatable("message.festival.reward_summary",
					trophies, waves, coinsBurned));
		}

		trial.broadcast(Component.translatable(
				victory ? "message.festival.reward_victory" : "message.festival.reward_defeat"));
	}

	/** Убрать монеты испытания из инвентаря, вернуть сколько убрано. */
	public static int clearCoins(ServerPlayer player) {
		int removed = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ModItems.FESTIVAL_COIN)) {
				removed += stack.getCount();
				stack.setCount(0);
			}
		}
		return removed;
	}
}
