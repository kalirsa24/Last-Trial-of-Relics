package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.core.Msg;

import io.github.kalirsa24.lasttrial.entity.FestivalSummoner;
import io.github.kalirsa24.lasttrial.item.TotemOfPower;
import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;

public class ServerEvents {

	private static int floorTimer = 0;

	public static void register() {
		// Игровой цикл: тикают все забеги, каждый сам по себе
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TrialManager.tick();

			// Пол арены не должен ломаться взрывами — периодически заделываем дыры
			if (++floorTimer >= 40) {
				floorTimer = 0;
				for (Trial trial : TrialManager.all()) {
					if (trial.isRunning() && trial.isRelicPresent()) {
						ArenaManager.restoreFloor(trial.level(), trial.relicPos());
					}
				}
			}
		});

		// Загрузка арен и волн из датапака
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ArenaManager.ensureLoaded(server.overworld());
			SkillManager.ensureLoaded(server.overworld());
			WaveLibrary.reload(server.getResourceManager());
		});

		// Вход в игру: постоянные бонусы древа навыков
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			SkillManager.ensureLoaded(server.overworld());
			SkillManager.migrateLegacyTrophies(handler.player);
			SkillManager.applyAttributes(handler.player);
		});

		// /reload — перечитываем waves.json, не ломая текущие забеги
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
			if (success) {
				WaveLibrary.reload(manager);
			}
		});

		// Остановка сервера — снимаем забеги и чистим кеши
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			TrialManager.stopAll();
			RoleManager.clear();
			ArenaManager.resetCache();
			SkillManager.resetCache();
		});

		// Тотем усиления работает как тотем бессмертия: смерть отменяется,
		// тотем расходуется, игрок получает стандартные эффекты спасения.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, amount) -> {
			if (!(entity instanceof ServerPlayer player)) {
				return true;
			}
			ItemStack totem = TotemOfPower.findTotem(player);
			if (totem == null) {
				return true;
			}
			totem.shrink(1);
			int tier = TotemOfPower.getTier(totem);
			player.setHealth(1.0F);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
			player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
			player.level().broadcastEntityEvent(player, (byte) 35);
			Msg.overlay(player, Component.translatable("message.lasttrial.totem_saved",
					TotemOfPower.roman(tier)));
			return false;
		});

		// Фантомы мешают испытанию: пока идёт бой, в мир их не пускаем
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Phantom && TrialManager.isRunningIn(level)) {
				entity.discard();
			}
		});

		// Награда за убийство моба волны + снятие со счёта живых
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
			Trial trial = TrialManager.forMob(entity);

			// Призыватель: своя награда и возврат через полминуты
			if (entity instanceof FestivalSummoner summoner) {
				if (trial != null) {
					trial.summoners().onDeath(summoner);
				}
				if (damageSource.getEntity() instanceof ServerPlayer player) {
					player.getInventory().add(new ItemStack(ModItems.FESTIVAL_COIN, 30));
					Msg.overlay(player, Component.translatable("message.lasttrial.summoner_killed"));
				}
				return;
			}

			if (!(entity instanceof Mob mob) || !mob.getTags().contains(TrialTags.MOB)) {
				return;
			}
			if (trial != null) {
				trial.onMobRemoved(mob.getUUID());
			}
			if (!(damageSource.getEntity() instanceof ServerPlayer player)) {
				return;
			}
			// Монет за убийство — по «цене» моба в очках волны
			int reward = (int) Math.max(1, Math.round(MobValues.pointsOf(mob)));
			if (trial != null && trial.hardMode()) {
				reward *= 2;
			}
			player.getInventory().add(new ItemStack(ModItems.FESTIVAL_COIN, reward));
		});

		// Моб выгрузился: удалённого снимаем со счёта, ушедшего в незагруженный чанк — нет
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob
					&& (mob.getTags().contains(TrialTags.MOB) || mob.getTags().contains(TrialTags.SUMMONER))) {
				Trial trial = TrialManager.forMob(mob);
				if (trial != null) {
					trial.onMobUnloaded(mob.getUUID(), mob.isRemoved());
				}
			}
		});

		// Возрождение во время испытания — над своей Реликвией
		ServerPlayerEvents.AFTER_RESPAWN.register((newPlayer, oldPlayer, alive) -> {
			SkillManager.applyAttributes(newPlayer);
			Trial trial = TrialManager.of(newPlayer);
			if (trial == null || !trial.isRunning()) {
				return;
			}
			BlockPos relic = trial.relicPos();
			newPlayer.teleportTo(relic.getX() + 0.5D, relic.getY() + 1.0D, relic.getZ() + 0.5D);
			Msg.overlay(newPlayer, Component.translatable("message.lasttrial.respawned_at_relic"));
		});
	}
}
