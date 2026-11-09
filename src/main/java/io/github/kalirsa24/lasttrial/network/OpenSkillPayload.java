package io.github.kalirsa24.lasttrial.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Сервер → клиент: открыть древо навыков. */
public record OpenSkillPayload(int[] levels, int trophies) {

	public static final ResourceLocation ID = new ResourceLocation("lasttrial", "open_skills");

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(levels.length);
		for (int level : levels) {
			buf.writeVarInt(level);
		}
		buf.writeVarInt(trophies);
	}

	public static OpenSkillPayload read(FriendlyByteBuf buf) {
		int count = buf.readVarInt();
		int[] levels = new int[count];
		for (int i = 0; i < count; i++) {
			levels[i] = buf.readVarInt();
		}
		return new OpenSkillPayload(levels, buf.readVarInt());
	}
}
