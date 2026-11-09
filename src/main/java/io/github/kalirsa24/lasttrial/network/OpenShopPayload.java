package io.github.kalirsa24.lasttrial.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Сервер → клиент: открыть магазин с текущими числами. */
public record OpenShopPayload(int coins, int lives, int maxLives, int totemTier, int tier, int trophies,
							  int wave, int maxWaves, int stage, int stages,
							  int stopPrice, boolean paused, int priceMultiplier) {

	public static final ResourceLocation ID = new ResourceLocation("lasttrial", "open_shop");

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(coins);
		buf.writeVarInt(lives);
		buf.writeVarInt(maxLives);
		buf.writeVarInt(totemTier);
		buf.writeVarInt(tier);
		buf.writeVarInt(trophies);
		buf.writeVarInt(wave);
		buf.writeVarInt(maxWaves);
		buf.writeVarInt(stage);
		buf.writeVarInt(stages);
		buf.writeVarInt(stopPrice);
		buf.writeBoolean(paused);
		buf.writeVarInt(priceMultiplier);
	}

	public static OpenShopPayload read(FriendlyByteBuf buf) {
		return new OpenShopPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
				buf.readVarInt(), buf.readBoolean(), buf.readVarInt());
	}
}
