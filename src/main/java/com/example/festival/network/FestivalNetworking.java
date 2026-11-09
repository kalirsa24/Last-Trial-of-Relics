package com.example.festival.network;

import com.example.festival.core.GameManager;
import com.example.festival.core.ShopManager;
import com.example.festival.core.SkillManager;
import com.example.festival.core.Trial;
import com.example.festival.core.TrialManager;
import com.example.festival.item.TotemOfPower;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/**
 * Сеть на каналах 1.20.1: ResourceLocation + FriendlyByteBuf.
 *
 * В 26.2 это были CustomPacketPayload/StreamCodec, здесь — классический
 * вариант Fabric: регистрация приёмников по id канала и ручная запись полей.
 */
public class FestivalNetworking {

	/** Серверная сторона: обработка покупки и клавиши запуска. */
	public static void registerServerHandlers() {
		ServerPlayNetworking.registerGlobalReceiver(BuyItemPayload.ID,
				(server, player, handler, buf, responseSender) -> {
					BuyItemPayload payload = BuyItemPayload.read(buf);
					server.execute(() -> ShopManager.buy(player, payload.goodId()));
				});

		ServerPlayNetworking.registerGlobalReceiver(StartTrialPayload.ID,
				(server, player, handler, buf, responseSender) ->
						server.execute(() -> GameManager.requestStart(player)));

		ServerPlayNetworking.registerGlobalReceiver(BuySkillPayload.ID,
				(server, player, handler, buf, responseSender) -> {
					BuySkillPayload payload = BuySkillPayload.read(buf);
					server.execute(() -> {
						SkillManager.Skill skill = SkillManager.Skill.byId(payload.skillId());
						if (skill != null) {
							SkillManager.buy(player, skill);
						}
					});
				});
	}

	public static void openShop(ServerPlayer player) {
		Trial trial = TrialManager.of(player);
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		new OpenShopPayload(ShopManager.countCoins(player), GameManager.getLives(player),
				GameManager.MAX_LIVES, TotemOfPower.getPlayerTier(player),
				ShopManager.tierFor(player).ordinal(), ShopManager.countTrophies(player),
				trial != null ? trial.currentWave() : 0,
				trial != null ? trial.maxWaves() : 0,
				trial != null ? trial.currentStage() + 1 : 0,
				trial != null ? trial.stages() : 0,
				trial != null ? trial.stopPrice() : 0,
				trial != null && trial.isPaused(),
				(int) Math.round(ShopManager.priceMultiplier(player) * 100.0D)).write(buf);
		ServerPlayNetworking.send(player, OpenShopPayload.ID, buf);
	}

	public static void syncTrial(ServerPlayer player) {
		Trial trial = TrialManager.of(player);
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		new SyncTrialPayload(ShopManager.countCoins(player), GameManager.getLives(player),
				GameManager.MAX_LIVES, TotemOfPower.getPlayerTier(player),
				ShopManager.countTrophies(player),
				trial != null ? trial.currentWave() : 0,
				trial != null ? trial.maxWaves() : 0,
				trial != null ? trial.currentStage() + 1 : 0,
				trial != null ? trial.stages() : 0,
				trial != null ? trial.stopPrice() : 0,
				trial != null && trial.isPaused(),
				(int) Math.round(ShopManager.priceMultiplier(player) * 100.0D)).write(buf);
		ServerPlayNetworking.send(player, SyncTrialPayload.ID, buf);
	}

	public static void openSkills(ServerPlayer player) {
		FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
		new OpenSkillPayload(SkillManager.levels(player), ShopManager.countTrophies(player)).write(buf);
		ServerPlayNetworking.send(player, OpenSkillPayload.ID, buf);
	}
}
