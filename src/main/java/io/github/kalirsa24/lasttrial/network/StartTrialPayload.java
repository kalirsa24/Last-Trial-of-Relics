package io.github.kalirsa24.lasttrial.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: клавиша запуска испытания / вызова волны. */
public record StartTrialPayload() {

	public static final ResourceLocation ID = new ResourceLocation("lasttrial", "start_trial");

	public void write(FriendlyByteBuf buf) {
	}

	public static StartTrialPayload read(FriendlyByteBuf buf) {
		return new StartTrialPayload();
	}
}
