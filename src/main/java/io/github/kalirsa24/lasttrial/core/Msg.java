package io.github.kalirsa24.lasttrial.core;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Сообщения над хотбаром.
 *
 * В 26.2 у игрока был {@code sendOverlayMessage}, в 1.20.1 — только
 * {@code displayClientMessage(component, true)}. Один хелпер, чтобы не
 * размазывать флаг по десяти вызовам.
 */
public final class Msg {

	private Msg() {
	}

	public static void overlay(Player player, Component message) {
		player.displayClientMessage(message, true);
	}
}
