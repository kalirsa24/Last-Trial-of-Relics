package com.example.festival.client;

import com.example.festival.network.OpenShopPayload;
import com.example.festival.network.OpenSkillPayload;
import com.example.festival.network.StartTrialPayload;
import com.example.festival.network.SyncTrialPayload;
import com.example.festival.registry.ModEntities;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.network.FriendlyByteBuf;
import org.lwjgl.glfw.GLFW;

/**
 * Клиентская часть: рендереры мобов, магазин, древо навыков и клавиша запуска.
 *
 * В 1.20.1 клавиши регистрируются через KeyBindingHelper, каналы — через
 * ResourceLocation + FriendlyByteBuf, а экран открывается через setScreen.
 */
public class FestivalClient implements ClientModInitializer {

	private static KeyMapping startKey;

	public static void registerRenderers() {
		EntityRendererRegistry.register(ModEntities.MINER_ZOMBIE, ZombieRenderer::new);
		EntityRendererRegistry.register(ModEntities.FESTIVAL_BOSS, ZombieRenderer::new);
		EntityRendererRegistry.register(ModEntities.ARCHER_SKELETON, SkeletonRenderer::new);
		EntityRendererRegistry.register(ModEntities.FESTIVAL_CREEPER, CreeperRenderer::new);
		EntityRendererRegistry.register(ModEntities.FESTIVAL_SUMMONER, EvokerRenderer::new);
	}

	@Override
	public void onInitializeClient() {
		registerRenderers();
		com.example.festival.item.RelicItem.setArenaStatusProvider(ArenaTooltip::append);

		ClientPlayNetworking.registerGlobalReceiver(OpenShopPayload.ID, (client, handler, buf, sender) -> {
			OpenShopPayload payload = OpenShopPayload.read(buf);
			client.execute(() -> client.setScreen(new ShopScreen(payload.coins(), payload.lives(),
					payload.maxLives(), payload.totemTier(), payload.tier(), payload.trophies(),
					payload.wave(), payload.maxWaves(), payload.stage(), payload.stages(),
					payload.stopPrice(), payload.paused(), payload.priceMultiplier())));
		});

		ClientPlayNetworking.registerGlobalReceiver(SyncTrialPayload.ID, (client, handler, buf, sender) -> {
			SyncTrialPayload payload = SyncTrialPayload.read(buf);
			client.execute(() -> ShopScreen.updateTrial(payload.coins(), payload.lives(),
					payload.maxLives(), payload.totemTier(), payload.trophies(),
					payload.wave(), payload.maxWaves(), payload.stage(), payload.stages(),
					payload.stopPrice(), payload.paused(), payload.priceMultiplier()));
		});

		ClientPlayNetworking.registerGlobalReceiver(OpenSkillPayload.ID, (client, handler, buf, sender) -> {
			OpenSkillPayload payload = OpenSkillPayload.read(buf);
			client.execute(() -> client.setScreen(new SkillScreen(payload.levels(), payload.trophies())));
		});

		// Клавиша запуска испытания / досрочной волны (по умолчанию V)
		startKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.festival.start",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				"key.categories.gameplay"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (startKey.consumeClick()) {
				if (client.player != null) {
					FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
					new StartTrialPayload().write(buf);
					ClientPlayNetworking.send(StartTrialPayload.ID, buf);
				}
			}
		});
	}
}
