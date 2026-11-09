package com.example.festival.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: купить уровень навыка по id ветки. */
public record BuySkillPayload(String skillId) {

	public static final ResourceLocation ID = new ResourceLocation("festival", "buy_skill");
	private static final int MAX_LENGTH = 32;

	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(skillId, MAX_LENGTH);
	}

	public static BuySkillPayload read(FriendlyByteBuf buf) {
		return new BuySkillPayload(buf.readUtf(MAX_LENGTH));
	}
}
