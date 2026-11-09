package com.example.festival.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Сервер → клиент: обновить монеты, жизни, трофеи, тотем и положение в волне. */
public record SyncTrialPayload(int coins, int lives, int maxLives, int totemTier, int trophies,
							   int wave, int maxWaves, int stage, int stages,
							   int stopPrice, boolean paused, int priceMultiplier) {

	public static final ResourceLocation ID = new ResourceLocation("festival", "sync_trial");

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(coins);
		buf.writeVarInt(lives);
		buf.writeVarInt(maxLives);
		buf.writeVarInt(totemTier);
		buf.writeVarInt(trophies);
		buf.writeVarInt(wave);
		buf.writeVarInt(maxWaves);
		buf.writeVarInt(stage);
		buf.writeVarInt(stages);
		buf.writeVarInt(stopPrice);
		buf.writeBoolean(paused);
		buf.writeVarInt(priceMultiplier);
	}

	public static SyncTrialPayload read(FriendlyByteBuf buf) {
		return new SyncTrialPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
	}
}
