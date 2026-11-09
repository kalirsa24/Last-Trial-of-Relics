package com.example.festival.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: купить товар по строковому id. */
public record BuyItemPayload(String goodId) {

	public static final ResourceLocation ID = new ResourceLocation("festival", "buy_item");
	private static final int MAX_LENGTH = 128;

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(goodId, MAX_LENGTH);
	}

	public static BuyItemPayload read(FriendlyByteBuf buf) {
		return new BuyItemPayload(buf.readUtf(MAX_LENGTH));
	}
}
