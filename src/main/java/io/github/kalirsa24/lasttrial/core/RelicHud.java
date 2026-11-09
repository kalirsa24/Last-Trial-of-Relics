package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.blockentity.RelicBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.List;

/**
 * Полосы испытания: прогресс волны и здоровье Реликвии.
 *
 * Полосы принадлежат забегу и показываются только его участникам —
 * чужой бой в другом конце мира больше не лезет в экран.
 */
public final class RelicHud {

	/** Радиус, в котором игрок считается участником забега. */
	private static final int SYNC_TICKS = 20;

	private final Trial trial;
	private ServerBossEvent waveBar;
	private ServerBossEvent relicBar;
	private int syncTimer;
	private boolean visible;

	RelicHud(Trial trial) {
		this.trial = trial;
	}

	public void show() {
		if (waveBar == null) {
			waveBar = new ServerBossEvent(Component.empty(),
					BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
			relicBar = new ServerBossEvent(Component.empty(),
					BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
		}
		visible = true;
		waveBar.setVisible(true);
		relicBar.setVisible(true);
		syncPlayers();
		refresh();
	}

	public void hide() {
		visible = false;
		if (waveBar != null) {
			waveBar.removeAllPlayers();
			waveBar.setVisible(false);
		}
		if (relicBar != null) {
			relicBar.removeAllPlayers();
			relicBar.setVisible(false);
		}
	}

	public void tick() {
		if (!visible) {
			return;
		}
		if (++syncTimer >= SYNC_TICKS) {
			syncTimer = 0;
			syncPlayers();
			refresh();
		}
	}

	/** Обновить текст и полосы немедленно (смена этапа, урон по Реликвии). */
	public void refresh() {
		if (!visible || waveBar == null) {
			return;
		}

		int wave = trial.currentWave();
		int maxWaves = trial.maxWaves();
		int stage = trial.currentStage() + 1;
		int stages = trial.stages();

		waveBar.setName(wave <= 0
				? Component.translatable("hud.lasttrial.wave_idle", maxWaves)
				: trial.isPaused()
				? Component.translatable("hud.lasttrial.wave_paused", wave, maxWaves)
				: Component.translatable("hud.lasttrial.wave_value",
						wave, maxWaves, stage, stages, trial.remainingInStage()));
		waveBar.setProgress(wave <= 0 ? 0.0F : Math.min(1.0F, (float) stage / stages));
		waveBar.setColor(trial.isFlagWave()
				? BossEvent.BossBarColor.RED
				: BossEvent.BossBarColor.PURPLE);

		RelicBlockEntity relic = trial.relicEntity();
		int health = relic == null ? 0 : relic.getHealth();
		int maxHealth = relic == null ? RelicBlockEntity.BASE_HEALTH : relic.getMaxHealth();
		float progress = maxHealth <= 0 ? 0.0F
				: Math.max(0.0F, Math.min(1.0F, (float) health / maxHealth));

		relicBar.setName(Component.translatable("hud.lasttrial.relic_value",
				health, maxHealth, trial.lives(), GameManager.MAX_LIVES));
		relicBar.setProgress(progress);
		relicBar.setColor(progress > 0.5F ? BossEvent.BossBarColor.GREEN
				: (progress > 0.25F ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED));
	}

	private void syncPlayers() {
		if (waveBar == null) {
			return;
		}
		List<ServerPlayer> wanted = trial.participants();

		for (ServerPlayer player : List.copyOf(waveBar.getPlayers())) {
			if (!wanted.contains(player)) {
				waveBar.removePlayer(player);
				relicBar.removePlayer(player);
			}
		}
		for (ServerPlayer player : wanted) {
			if (!waveBar.getPlayers().contains(player)) {
				waveBar.addPlayer(player);
				relicBar.addPlayer(player);
			}
		}
	}
}
