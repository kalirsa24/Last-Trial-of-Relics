package io.github.kalirsa24.lasttrial.core;

import io.github.kalirsa24.lasttrial.core.Msg;

import io.github.kalirsa24.lasttrial.block.RelicBlock;
import io.github.kalirsa24.lasttrial.blockentity.RelicBlockEntity;
import io.github.kalirsa24.lasttrial.entity.FestivalBoss;
import io.github.kalirsa24.lasttrial.entity.MinerZombie;
import io.github.kalirsa24.lasttrial.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Один забег «Празднества»: своя Реликвия, свои волны, свои жизни.
 *
 * Раньше всё это лежало в статиках {@code WaveManager}, поэтому на сервере
 * мог идти ровно один забег. Теперь состояние живёт в объекте, а
 * {@link TrialManager} держит по забегу на владельца — два игрока могут
 * защищать две Реликвии одновременно.
 *
 * Волна делится на этапы (как в PVZ). Этап получает долю бюджета волны,
 * выдаёт мобов ручейком и сменяется либо по зачистке, либо по таймауту.
 * Живые мобы учитываются множеством UUID, а не сканом мира каждый тик.
 */
public final class Trial {

	public enum State { RUNNING, VICTORY, DEFEAT, STOPPED }

	/** Как часто сверяем множество живых мобов с миром (страховка от рассинхрона). */
	private static final int RECONCILE_TICKS = 200;

	/** Пауза в тиках дольше этого промежутка считается выходом в меню. */
	private static final long PAUSE_GAP_MS = 5000L;

	/** Цена остановки волны после бесплатной попытки. */
	public static final int STOP_PRICE = 10;

	/** Потолок живых мобов одного забега — защита сервера от «мега-ферм». */
	private static final int MAX_ALIVE_MOBS = 70;

	/** Бонус за присутствие: 5 минут в зоне, отсутствие не дольше 15 секунд. */
	private static final int PRESENCE_REQUIRED_TICKS = 20 * 60 * 5;
	private static final int PRESENCE_GRACE_TICKS = 20 * 15;
	private static final int PRESENCE_STEP = 20;
	private static final double PRESENCE_RADIUS = ArenaValidator.RADIUS + 9.0D;

	/** Радиус выдачи награды: чуть больше арены, чтобы не терять бойцов на краю. */
	private static final double REWARD_RADIUS = ArenaValidator.RADIUS + 24.0D;

	private final ServerLevel level;
	private final BlockPos relicPos;
	private final UUID owner;
	private final String ownerName;
	private final RelicTier tier;
	private final int maxWaves;

	private final SummonerSet summoners = new SummonerSet(this);
	private final RelicHud hud = new RelicHud(this);
	private final Deque<WaveBudget.Type> queue = new ArrayDeque<>();
	private final Set<UUID> alive = new HashSet<>();
	private final Random random = new Random();

	private State state = State.RUNNING;
	private int currentWave;
	private int currentStage;
	private int lives;
	private boolean hardMode;
	private boolean buffed;
	private boolean pendingRepeat;
	private int waveCooldown;
	private int lastRewardedWave;
	private int dripTimer;
	private int stageTimer;
	private double carry;
	private int reconcileTimer;
	private int boundsTimer;
	private int missingRelicTicks;

	/** Волна остановлена игроком или паузой: спавн не идёт, мобы убраны. */
	private boolean paused;
	/** Бесплатная остановка уже израсходована. */
	private boolean stopUsed;
	/** Платформа 30×30 застроена целиком — бонусные награды. */
	private boolean bonusArena;
	/** Время прошлого тика — для определения паузы. */
	private long lastTickMillis;

	/** Сколько тиков арена была вне загруженных чанков. */
	private int unloadedTicks;

	/** Присутствие игроков у арены: копится 5 минут без отлучек дольше 15 секунд. */
	private final Map<UUID, Presence> presence = new HashMap<>();
	private int presenceTimer;

	/** Состояние присутствия одного игрока. */
	private static final class Presence {
		int presentTicks;
		int absentTicks;
		boolean bonus;
	}

	private Trial(ServerLevel level, BlockPos relicPos, UUID owner, String ownerName,
				  RelicTier tier, int maxWaves, int lives) {
		this.level = level;
		this.relicPos = relicPos.immutable();
		this.owner = owner;
		this.ownerName = ownerName;
		this.tier = tier;
		this.maxWaves = Math.max(1, maxWaves);
		this.lives = Math.max(1, Math.min(GameManager.MAX_LIVES, lives));
	}

	// ------------------------------------------------------------------
	// Создание и восстановление
	// ------------------------------------------------------------------

	static Trial create(ServerLevel level, BlockPos pos, RelicTier tier, ServerPlayer starter) {
		int waves = ArenaManager.waveOverride(starter.getUUID(), tier.getMaxWaves());
		Trial trial = new Trial(level, pos, starter.getUUID(), starter.getScoreboardName(),
				tier, waves, GameManager.MAX_LIVES);
		trial.bonusArena = ArenaValidator.bonusComplete(level, pos);
		ArenaManager.snapshotFloor(level, pos);
		trial.begin();
		return trial;
	}

	static Trial restore(ServerLevel level, BlockPos pos, RelicTier tier, SavedState saved) {
		int waves = ArenaManager.waveOverride(saved.owner(), tier.getMaxWaves());
		Trial trial = new Trial(level, pos, saved.owner(), saved.ownerName(), tier, waves, saved.lives());

		trial.currentWave = clamp(saved.wave(), 0, trial.maxWaves);
		trial.currentStage = clamp(saved.stage(), 0, trial.stages() - 1);
		trial.hardMode = saved.hardMode();
		trial.buffed = saved.buffed();
		trial.pendingRepeat = saved.pendingRepeat();
		trial.waveCooldown = Math.max(0, saved.waveCooldown());
		trial.lastRewardedWave = clamp(saved.rewardedWave(), 0, trial.currentWave);
		trial.dripTimer = Math.max(0, saved.dripTimer());
		trial.stageTimer = Math.max(0, saved.stageTimer());
		trial.carry = saved.carry();
		trial.paused = saved.paused();
		trial.stopUsed = saved.stopUsed();
		trial.queue.addAll(saved.queue());
		trial.reconcileAlive();

		// Снимок пола живёт в памяти — после рестарта его надо снять заново
		ArenaManager.snapshotFloor(level, pos);
		trial.bonusArena = ArenaValidator.bonusComplete(level, pos);

		if (trial.currentWave >= 3 && !trial.isWaveComplete()) {
			trial.summoners.startWave(trial.currentWave);
		}
		trial.hud.show();
		return trial;
	}

	private void begin() {
		hud.show();
		waveCooldown = FestivalConfig.get().intermissionTicks;
		broadcast(Component.translatable("message.lasttrial.started",
				Component.translatable("tier.lasttrial." + tier.getId()), maxWaves));
	}

	// ------------------------------------------------------------------
	// Основной цикл
	// ------------------------------------------------------------------

	public void tick() {
		if (state != State.RUNNING) {
			return;
		}

		// Выход в меню (одиночная игра) останавливает серверные тики.
		// Возвращаемся — и текущая волна начинается заново, без «замороженных» мобов.
		long now = System.currentTimeMillis();
		boolean singlePlayer = level.getServer() != null && !level.getServer().isDedicatedServer();
		if (singlePlayer && lastTickMillis != 0L && now - lastTickMillis > PAUSE_GAP_MS) {
			onPauseDetected();
		}
		lastTickMillis = now;

		// Арена вне загруженных чанков: забег ждёт, но не бесконечно.
		// Иначе выгруженная арена «замораживает» слот навсегда и занимает лимит забегов.
		if (!level.isLoaded(relicPos)) {
			int limitSeconds = FestivalConfig.get().arenaUnloadStopSeconds;
			if (limitSeconds > 0 && ++unloadedTicks > limitSeconds * 20) {
				broadcast(Component.translatable("message.lasttrial.arena_unloaded_stop", limitSeconds / 60));
				stop();
			}
			return;
		}
		unloadedTicks = 0;

		// Блок снят или сломан: даём короткую паузу и закрываем забег
		if (!(level.getBlockEntity(relicPos) instanceof RelicBlockEntity)) {
			if (++missingRelicTicks > FestivalConfig.get().relicMissingGraceTicks) {
				stop();
			}
			return;
		}
		missingRelicTicks = 0;

		summoners.tick();
		hud.tick();

		// Волна остановлена: спавн не идёт, пока игрок не нажмёт «Продолжить»
		if (paused) {
			return;
		}

		tickPresence();

		// Повтор волны после потери жизни: выжидаем паузу и начинаем ту же волну
		// заново с первого этапа. Проверка стоит до этапов, иначе очищенная
		// арена «завершала» бы волну и съедала повтор.
		if (pendingRepeat) {
			if (waveCooldown > 0) {
				waveCooldown--;
				return;
			}
			pendingRepeat = false;
			beginWave();
			return;
		}

		if (++boundsTimer >= 40) {
			boundsTimer = 0;
			enforceArenaBounds();
		}
		if (++reconcileTimer >= RECONCILE_TICKS) {
			reconcileTimer = 0;
			reconcileAlive();
		}

		tickStage();

		if (!isWaveComplete()) {
			return;
		}

		// Награда за волну — ровно один раз
		if (currentWave > 0 && currentWave > lastRewardedWave) {
			lastRewardedWave = currentWave;
			RewardManager.onWaveCleared(this, currentWave);
			buffed = false;
			waveCooldown = FestivalConfig.get().intermissionTicks;
		}

		if (currentWave >= maxWaves && currentWave > 0) {
			if (waveCooldown > 0) {
				waveCooldown--;
				countdown();
				return;
			}
			victory();
			return;
		}

		if (waveCooldown > 0) {
			waveCooldown--;
			countdown();
			return;
		}
		startNextWave();
	}

	private void tickStage() {
		if (currentWave <= 0) {
			return;
		}

		// Таймер этапа идёт от его старта, а не от последнего моба в очереди
		stageTimer--;

		if (!queue.isEmpty()) {
			if (--dripTimer <= 0) {
				spawnMob(queue.poll());
				dripTimer = dripTicks();
			}
			return;
		}

		if (currentStage >= stages() - 1) {
			return;
		}
		if (alive.isEmpty() || stageTimer <= 0) {
			beginStage(currentStage + 1);
		}
	}

	private void startNextWave() {
		if (pendingRepeat) {
			pendingRepeat = false;
			beginWave();
			return;
		}
		if (currentWave >= maxWaves) {
			victory();
			return;
		}
		currentWave++;
		buffed = false;
		beginWave();
	}

	private void beginWave() {
		purgePhantoms();
		summoners.startWave(currentWave);
		carry = 0.0D;
		beginStage(0);
	}

	private void beginStage(int stage) {
		currentStage = stage;
		queue.clear();
		dripTimer = 0;

		FestivalConfig config = FestivalConfig.get();
		int stages = stages();
		boolean finalPush = stage >= stages - 1;

		double budget = WaveBudget.wavePoints(currentWave, playerCount(), tier.getSpawnMultiplier(),
				buffed, hardMode) * config.stageShare(stage) + carry;

		var scripted = WaveLibrary.stage(currentWave, stage);
		if (scripted.isPresent()) {
			queue.addAll(scripted.get());
			carry = 0.0D;
		} else {
			WaveBudget.Composition composition = WaveBudget.compose(random, currentWave, stage, stages, budget);
			queue.addAll(composition.mobs());
			carry = Math.max(-8.0D, Math.min(8.0D, composition.leftover()));
		}

		stageTimer = config.stageTimeoutSeconds * 20 + queue.size() * config.stageTimeoutPerMobTicks;

		// Тяжёлые гости приходят с последним этапом — финал должен быть финалом
		if (finalPush) {
			if (isFlagWave()) {
				spawnRavager();
			}
			if (isBossWave()) {
				spawnBoss();
			}
		}

		announceStage(stage, queue.size());
		hud.refresh();
	}

	/**
	 * Вызвать следующий этап (или волну) вручную. Пауза между волнами
	 * не обходится — иначе клавишей можно было бы фармить волны без боя.
	 */
	public boolean callNextWave() {
		if (state != State.RUNNING || pendingRepeat) {
			return false;
		}
		if (paused) {
			return resumeWave();
		}
		if (isWaveComplete()) {
			if (waveCooldown > 0) {
				return false;
			}
			startNextWave();
			return true;
		}
		if (!queue.isEmpty() || currentStage >= stages() - 1) {
			return false;
		}
		beginStage(currentStage + 1);
		return true;
	}

	/** Сколько секунд осталось до следующей волны (0 — паузы нет). */
	public int intermissionSeconds() {
		return waveCooldown <= 0 ? 0 : (waveCooldown + 19) / 20;
	}

	// ------------------------------------------------------------------
	// Скипы, смерть Реликвии, итоги
	// ------------------------------------------------------------------

	public boolean skipWaves(int count, boolean empower) {
		if (state != State.RUNNING || count <= 0) {
			return false;
		}
		// Последнюю волну пропустить нельзя: победа должна быть заработана
		if (currentWave >= maxWaves - 1) {
			broadcast(Component.translatable("message.lasttrial.skip_last_wave"));
			return false;
		}

		int target = Math.min(maxWaves - 1, currentWave + count);
		if (target <= currentWave) {
			return false;
		}

		currentWave = target;
		buffed = empower;
		pendingRepeat = false;
		paused = false;
		waveCooldown = 0;
		clearMobs();
		beginWave();
		broadcast(Component.translatable("message.lasttrial.waves_skipped", currentWave, maxWaves));
		return true;
	}

	// ------------------------------------------------------------------
	// Остановка и продолжение волны
	// ------------------------------------------------------------------

	/**
	 * Остановить текущую волну: убрать всех мобов и заморозить спавн.
	 * Возвращает {@code false}, если забег не идёт или уже на паузе.
	 */
	public boolean stopWave() {
		if (state != State.RUNNING || paused) {
			return false;
		}
		clearMobs();
		paused = true;
		pendingRepeat = false;
		waveCooldown = 0;
		hud.refresh();
		broadcast(Component.translatable("message.lasttrial.wave_stopped"));
		return true;
	}

	/** Продолжить: волна начинается заново с первого этапа. */
	public boolean resumeWave() {
		if (state != State.RUNNING || !paused) {
			return false;
		}
		paused = false;
		pendingRepeat = false;
		waveCooldown = 0;
		if (currentWave > 0) {
			beginWave();
		} else {
			startNextWave();
		}
		broadcast(Component.translatable("message.lasttrial.wave_resumed"));
		return true;
	}

	/** Пауза игры: волна сбрасывается и начнётся заново после возвращения. */
	private void onPauseDetected() {
		if (paused || currentWave <= 0) {
			return;
		}
		clearMobs();
		pendingRepeat = true;
		waveCooldown = FestivalConfig.get().intermissionTicks;
		hud.refresh();
		broadcast(Component.translatable("message.lasttrial.wave_reset_pause"));
	}

	public boolean isPaused() {
		return paused;
	}

	// ------------------------------------------------------------------
	// Бонус за присутствие
	// ------------------------------------------------------------------

	/**
	 * Раз в секунду проверяем, кто реально стоит у арены.
	 *
	 * Пять минут непрерывного присутствия (отлучки не дольше 15 секунд) дают
	 * этому игроку двойную награду до конца забега. Ушёл дольше — счётчик
	 * сбрасывается, накопленный бонус при этом остаётся.
	 */
	private void tickPresence() {
		if (++presenceTimer < PRESENCE_STEP) {
			return;
		}
		presenceTimer = 0;

		Vec3 center = Vec3.atCenterOf(relicPos);
		double radiusSqr = PRESENCE_RADIUS * PRESENCE_RADIUS;

		for (ServerPlayer player : level.players()) {
			Presence state = presence.computeIfAbsent(player.getUUID(), key -> new Presence());
			if (state.bonus) {
				continue;
			}
			boolean inside = player.distanceToSqr(center) <= radiusSqr;
			if (inside) {
				state.absentTicks = 0;
				state.presentTicks += PRESENCE_STEP;
				if (state.presentTicks >= PRESENCE_REQUIRED_TICKS) {
					state.bonus = true;
					Msg.overlay(player, Component.translatable("message.lasttrial.presence_bonus"));
					broadcast(Component.translatable("message.lasttrial.presence_bonus_broadcast",
							player.getScoreboardName()));
				}
			} else {
				state.absentTicks += PRESENCE_STEP;
				if (state.absentTicks > PRESENCE_GRACE_TICKS) {
					state.presentTicks = 0;
				}
			}
		}
	}

	/** Множитель награды для игрока: 2.0 при бонусе присутствия, иначе 1.0. */
	public double rewardMultiplier(ServerPlayer player) {
		Presence state = presence.get(player.getUUID());
		return state != null && state.bonus ? 2.0D : 1.0D;
	}

	/** Сколько секунд присутствия накоплено (для HUD и отладки). */
	public int presenceSeconds(ServerPlayer player) {
		Presence state = presence.get(player.getUUID());
		return state == null ? 0 : state.presentTicks / 20;
	}

	public boolean hasPresenceBonus(ServerPlayer player) {
		Presence state = presence.get(player.getUUID());
		return state != null && state.bonus;
	}

	/** Бесплатная остановка уже потрачена — следующая стоит монет. */
	public boolean isStopUsed() {
		return stopUsed;
	}

	public void markStopUsed() {
		this.stopUsed = true;
	}

	/** Текущая цена остановки волны (0 — первая бесплатна). */
	public int stopPrice() {
		return stopUsed ? STOP_PRICE : 0;
	}

	/** Застроена ли бонусная платформа 30×30. */
	public boolean hasBonusArena() {
		return bonusArena;
	}

	/** Реликвия разрушена: минус жизнь, повтор волны с первого этапа. */
	public void onRelicDestroyed() {
		if (state != State.RUNNING) {
			return;
		}
		lives--;
		if (lives <= 0) {
			defeat();
			return;
		}

		clearMobs();
		pendingRepeat = true;
		waveCooldown = FestivalConfig.get().intermissionTicks;

		RelicBlockEntity relic = relicEntity();
		if (relic == null) {
			defeat();
			return;
		}
		relic.resetHealth();
		hud.refresh();
		broadcast(Component.translatable("message.lasttrial.life_lost", lives, GameManager.MAX_LIVES));
	}

	private void victory() {
		state = State.VICTORY;
		clearMobs();
		RewardManager.finish(this, true);
		hud.hide();
		ArenaManager.clearFloor(level, relicPos);
		broadcast(Component.translatable("message.lasttrial.victory", maxWaves));
		TrialManager.remove(this);
	}

	private void defeat() {
		state = State.DEFEAT;
		clearMobs();
		RewardManager.finish(this, false);
		hud.hide();
		ArenaManager.clearFloor(level, relicPos);
		broadcast(Component.translatable("message.lasttrial.defeat"));

		// Ломаем Реликвию без дропа — следующим тиком, чтобы не ломать блок изнутри его тика
		BlockPos target = relicPos.immutable();
		level.getServer().execute(() -> {
			if (level.getBlockState(target).getBlock() instanceof RelicBlock) {
				level.destroyBlock(target, false);
			}
		});
		TrialManager.remove(this);
	}

	public void stop() {
		if (state == State.RUNNING) {
			state = State.STOPPED;
		}
		clearMobs();
		hud.hide();
		ArenaManager.clearFloor(level, relicPos);
		TrialManager.remove(this);
	}

	// ------------------------------------------------------------------
	// Спавн
	// ------------------------------------------------------------------

	private void spawnMob(WaveBudget.Type type) {
		// Потолок живых мобов: сервер не должен захлёбываться от «мега-ферм»
		if (alive.size() >= MAX_ALIVE_MOBS) {
			queue.addFirst(type);
			dripTimer = 40;
			return;
		}

		Mob mob = switch (type) {
			case ZOMBIE -> EntityType.ZOMBIE.create(level);
			case SKELETON -> EntityType.SKELETON.create(level);
			case MINER -> ModEntities.MINER_ZOMBIE.create(level);
			case CREEPER -> ModEntities.FESTIVAL_CREEPER.create(level);
			case ARCHER -> ModEntities.ARCHER_SKELETON.create(level);
		};
		if (mob == null) {
			return;
		}

		FestivalConfig config = FestivalConfig.get();
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double radius = config.spawnRadiusMin
				+ random.nextDouble() * (config.spawnRadiusMax - config.spawnRadiusMin);
		double x = relicPos.getX() + Math.cos(angle) * radius;
		double z = relicPos.getZ() + Math.sin(angle) * radius;

		mob.moveTo(x, relicPos.getY(), z, random.nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, relicPos.getY(), z)),
				MobSpawnType.EVENT, null, null);

		mob.addTag(TrialTags.MOB);
		mob.addTag(TrialTags.owner(owner));
		mob.setPersistenceRequired();
		mob.setGlowingTag(true);

		if (mob instanceof Zombie zombie) {
			zombie.setCanPickUpLoot(false);
		}
		if (mob instanceof Skeleton skeleton) {
			skeleton.setCanPickUpLoot(false);
		}

		// Крипер: втрое больше HP
		double extra = mob instanceof io.github.kalirsa24.lasttrial.entity.FestivalCreeper ? 3.0D : 1.0D;
		DifficultyScaler.scaleStats(mob, this, extra);
		applyGoals(mob);

		if (level.addFreshEntity(mob)) {
			alive.add(mob.getUUID());
		}
		spawnEffects(x, relicPos.getY(), z);
	}

	/** Мини-босс: разоритель с удвоенным HP. */
	private void spawnRavager() {
		Ravager ravager = EntityType.RAVAGER.create(level);
		if (ravager == null) {
			return;
		}
		double angle = random.nextDouble() * Math.PI * 2.0D;
		double x = relicPos.getX() + Math.cos(angle) * 18.0D;
		double z = relicPos.getZ() + Math.sin(angle) * 18.0D;

		ravager.moveTo(x, relicPos.getY(), z, 0.0F, 0.0F);
		ravager.addTag(TrialTags.MOB);
		ravager.addTag(TrialTags.MINIBOSS);
		ravager.addTag(TrialTags.owner(owner));
		ravager.setPersistenceRequired();
		ravager.setGlowingTag(true);

		DifficultyScaler.scaleStats(ravager, this, 2.0D);
		applyGoals(ravager);

		if (level.addFreshEntity(ravager)) {
			alive.add(ravager.getUUID());
		}
		spawnEffects(x, relicPos.getY(), z);
	}

	private void spawnBoss() {
		FestivalBoss boss = ModEntities.FESTIVAL_BOSS.create(level);
		if (boss == null) {
			return;
		}
		boss.moveTo(relicPos.getX() + 10.0D, relicPos.getY(), relicPos.getZ(), 0.0F, 0.0F);
		boss.addTag(TrialTags.MOB);
		boss.addTag(TrialTags.BOSS);
		boss.addTag(TrialTags.owner(owner));
		boss.setPersistenceRequired();
		boss.setGlowingTag(true);
		DifficultyScaler.scaleStats(boss, this, 1.0D);
		applyGoals(boss);
		boss.setCustomName(Component.translatable("entity.lasttrial.lasttrial_boss"));
		boss.setCustomNameVisible(true);
		if (level.addFreshEntity(boss)) {
			alive.add(boss.getUUID());
		}
	}

	/** Портальный «выход» на арену: моб не возникает из воздуха без следа. */
	private void spawnEffects(double x, double y, double z) {
		if (!FestivalConfig.get().spawnEffects) {
			return;
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
				x, y + 1.0D, z, 18, 0.35D, 0.6D, 0.35D, 0.08D);
		level.playSound(null, x, y + 1.0D, z, SoundEvents.ENDERMAN_TELEPORT,
				SoundSource.HOSTILE, 0.45F, 0.65F);
	}

	/** Общие цели моба волны: ближняя цель + атака алтаря с прокапыванием. */
	public static void applyGoals(Mob mob) {
		int digInterval = mob instanceof MinerZombie ? 4 : 12;
		mob.goalSelector.addGoal(0, new NearestThreatGoal(mob));
		mob.goalSelector.addGoal(1,
				new RelicAttackGoal(mob, mob instanceof Ravager ? 1.0D : 1.15D, digInterval));
	}

	// ------------------------------------------------------------------
	// Учёт живых
	// ------------------------------------------------------------------

	/** Моб погиб — убираем из счёта волны. */
	public void onMobRemoved(UUID uuid) {
		alive.remove(uuid);
	}

	/** Моб выгрузился: если он удалён — снимаем со счёта, если просто вне чанков — нет. */
	public void onMobUnloaded(UUID uuid, boolean removed) {
		if (removed) {
			alive.remove(uuid);
		}
	}

	/**
	 * Страховка: раз в 5 секунд сверяем множество с миром. Дешевле, чем скан
	 * каждый тик, и лечит рассинхрон, если моба убрал другой мод.
	 */
	private void reconcileAlive() {
		if (queue.isEmpty() && alive.isEmpty()) {
			return;
		}
		AABB area = new AABB(relicPos).inflate(160.0D);
		Set<UUID> found = new HashSet<>();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area, this::isWaveMob)) {
			found.add(mob.getUUID());
		}
		alive.clear();
		alive.addAll(found);
	}

	private boolean isWaveMob(Mob mob) {
		return mob.getTags().contains(TrialTags.MOB)
				&& !mob.getTags().contains(TrialTags.SUMMONED)
				&& owner.equals(TrialTags.ownerOf(mob));
	}

	/** Живые мобы волны (без призывателей и призванных разорителей). */
	public int aliveCount() {
		return alive.size();
	}

	public int remainingInStage() {
		return queue.size() + alive.size();
	}

	public int queueSize() {
		return queue.size();
	}

	// ------------------------------------------------------------------
	// Отладка (только для операторов через команду)
	// ------------------------------------------------------------------

	/** Перейти к этапу вручную; {@code false}, если забег не идёт. */
	public boolean debugStage(int stage) {
		if (state != State.RUNNING || currentWave <= 0) {
			return false;
		}
		beginStage(clamp(stage, 0, stages() - 1));
		return true;
	}

	/** Заспавнить мобов вне очереди; возвращает, сколько вышло. */
	public int debugSpawn(WaveBudget.Type type, int count) {
		if (state != State.RUNNING || type == null) {
			return 0;
		}
		int spawned = 0;
		for (int i = 0; i < Math.min(50, Math.max(0, count)); i++) {
			spawnMob(type);
			spawned++;
		}
		hud.refresh();
		return spawned;
	}

	public boolean isWaveComplete() {
		if (currentWave <= 0) {
			return true;
		}
		return currentStage >= stages() - 1 && queue.isEmpty() && alive.isEmpty();
	}

	public void clearMobs() {
		summoners.clear();
		queue.clear();
		dripTimer = 0;
		AABB area = new AABB(relicPos).inflate(160.0D);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
				mob -> owner.equals(TrialTags.ownerOf(mob)))) {
			mob.discard();
		}
		alive.clear();
	}

	/** Фантомы во время испытания не нужны — чистим тех, кто успел загрузиться. */
	private void purgePhantoms() {
		AABB area = new AABB(relicPos).inflate(160.0D);
		for (Phantom phantom : level.getEntitiesOfClass(Phantom.class, area)) {
			phantom.discard();
		}
	}

	/**
	 * Мобы не должны уходить с арены: убежавших возвращаем к Реликвии.
	 */
	private void enforceArenaBounds() {
		int maxHorizontal = ArenaValidator.RADIUS + 6;
		AABB area = new AABB(relicPos).inflate(200.0D);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
				mob -> mob.getTags().contains(TrialTags.MOB) && owner.equals(TrialTags.ownerOf(mob)))) {
			BlockPos pos = mob.blockPosition();
			boolean tooFar = Math.abs(pos.getX() - relicPos.getX()) > maxHorizontal
					|| Math.abs(pos.getZ() - relicPos.getZ()) > maxHorizontal;
			boolean tooHigh = Math.abs(pos.getY() - relicPos.getY()) > 5;

			if (tooFar || tooHigh) {
				double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
				double radius = 12.0D + level.getRandom().nextDouble() * 8.0D;
				mob.teleportTo(relicPos.getX() + Math.cos(angle) * radius, relicPos.getY() + 1,
						relicPos.getZ() + Math.sin(angle) * radius);
				mob.getNavigation().stop();
			}
		}
	}

	// ------------------------------------------------------------------
	// Объявления и HUD
	// ------------------------------------------------------------------

	private void announceStage(int stage, int mobCount) {
		FestivalConfig config = FestivalConfig.get();
		int stages = stages();

		if (stage <= 0) {
			broadcast(buffed
					? Component.translatable("message.lasttrial.wave_empowered", currentWave, maxWaves)
					: Component.translatable("message.lasttrial.wave", currentWave, maxWaves));
			return;
		}

		if (stage >= stages - 1) {
			broadcast(Component.translatable(
					isFlagWave() ? "message.lasttrial.wave_push" : "message.lasttrial.wave_final_stage",
					currentWave, maxWaves, stage + 1, stages, mobCount));
			if (isFlagWave()) {
				playHorn();
			}
			return;
		}

		if (config.announceEveryStage) {
			broadcast(Component.translatable("message.lasttrial.wave_stage",
					currentWave, maxWaves, stage + 1, stages, mobCount));
		}
	}

	private void playHorn() {
		level.playSound(null, relicPos.getX() + 0.5D, relicPos.getY() + 1.0D, relicPos.getZ() + 0.5D,
				SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.4F, 0.9F);
	}

	private void countdown() {
		if (waveCooldown <= 0 || waveCooldown % 20 != 0 || currentWave >= maxWaves) {
			return;
		}
		Component message = Component.translatable("message.lasttrial.next_wave_in",
				currentWave + 1, maxWaves, intermissionSeconds());
		for (ServerPlayer player : participants()) {
			Msg.overlay(player, message);
		}
	}

	public void broadcast(Component message) {
		for (ServerPlayer player : participants()) {
			player.sendSystemMessage(message);
		}
	}

	/**
	 * Кому выдавать награду: только тем, кто реально находится у арены.
	 * Владелец в другом конце мира и случайные прохожие в радиусе 96 блоков
	 * раньше получали полную награду, ничего не делая.
	 */
	public List<ServerPlayer> rewardTargets() {
		List<ServerPlayer> list = new ArrayList<>();
		Vec3 center = Vec3.atCenterOf(relicPos);
		double radiusSqr = REWARD_RADIUS * REWARD_RADIUS;
		for (ServerPlayer player : level.players()) {
			if (player.distanceToSqr(center) <= radiusSqr) {
				list.add(player);
			}
		}
		return list;
	}

	/** Участники забега: владелец и все, кто стоит рядом с Реликвией. */
	public List<ServerPlayer> participants() {
		List<ServerPlayer> list = new ArrayList<>();
		Vec3 center = Vec3.atCenterOf(relicPos);
		double radiusSqr = 96.0D * 96.0D;
		for (ServerPlayer player : level.players()) {
			if (player.getUUID().equals(owner) || player.distanceToSqr(center) <= radiusSqr) {
				list.add(player);
			}
		}
		return list;
	}

	// ------------------------------------------------------------------
	// Доступ к состоянию
	// ------------------------------------------------------------------

	public ServerLevel level() {
		return level;
	}

	public BlockPos relicPos() {
		return relicPos;
	}

	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public RelicTier tier() {
		return tier;
	}

	public int maxWaves() {
		return maxWaves;
	}

	public int currentWave() {
		return currentWave;
	}

	public int currentStage() {
		return currentStage;
	}

	public int stages() {
		return FestivalConfig.get().stagesPerWave;
	}

	public int lives() {
		return lives;
	}

	public boolean hardMode() {
		return hardMode;
	}

	public void setHardMode(boolean value) {
		this.hardMode = value;
	}

	public boolean isBuffed() {
		return buffed;
	}

	public boolean isRunning() {
		return state == State.RUNNING;
	}

	public boolean isFlagWave() {
		int every = FestivalConfig.get().flagEveryWaves;
		return every > 0 && currentWave > 0 && currentWave % every == 0;
	}

	public boolean isBossWave() {
		return tier.isEndless() ? currentWave % 10 == 0 : currentWave == maxWaves;
	}

	public boolean addLife() {
		if (lives >= GameManager.MAX_LIVES) {
			return false;
		}
		lives++;
		return true;
	}

	public RelicHud hud() {
		return hud;
	}

	public SummonerSet summoners() {
		return summoners;
	}

	public int playerCount() {
		return Math.max(1, level.players().size());
	}

	public boolean isRelicPresent() {
		return level.isLoaded(relicPos) && level.getBlockEntity(relicPos) instanceof RelicBlockEntity;
	}

	public RelicBlockEntity relicEntity() {
		BlockEntity be = level.getBlockEntity(relicPos);
		return be instanceof RelicBlockEntity relic ? relic : null;
	}

	/** Владелец забега, если он в сети. */
	public ServerPlayer ownerPlayer() {
		return level.getServer() == null ? null
				: level.getServer().getPlayerList().getPlayer(owner);
	}

	public void healRelic(int amount) {
		RelicBlockEntity relic = relicEntity();
		if (relic != null) {
			relic.heal(amount);
		}
	}

	private int dripTicks() {
		FestivalConfig config = FestivalConfig.get();
		return Math.max(config.dripMinTicks, config.dripTicks - currentWave / 3);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	// ------------------------------------------------------------------
	// Сохранение
	// ------------------------------------------------------------------

	public record SavedState(UUID owner, String ownerName, int wave, int stage, int lives,
							 boolean hardMode, boolean buffed, boolean pendingRepeat,
							 int waveCooldown, int rewardedWave, int dripTimer, int stageTimer,
							 double carry, List<WaveBudget.Type> queue,
							 boolean paused, boolean stopUsed) {
	}

	public void save(CompoundTag out) {
		out.putString("Owner", owner.toString());
		out.putString("OwnerName", ownerName);
		out.putInt("Wave", currentWave);
		out.putInt("Stage", currentStage);
		out.putInt("Lives", lives);
		out.putBoolean("Hard", hardMode);
		out.putBoolean("Buffed", buffed);
		out.putBoolean("Repeat", pendingRepeat);
		out.putInt("Cooldown", waveCooldown);
		out.putInt("Rewarded", lastRewardedWave);
		out.putInt("Drip", dripTimer);
		out.putInt("StageTimer", stageTimer);
		out.putDouble("Carry", carry);
		out.putBoolean("Paused", paused);
		out.putBoolean("StopUsed", stopUsed);

		StringBuilder queueText = new StringBuilder();
		for (WaveBudget.Type type : queue) {
			if (queueText.length() > 0) {
				queueText.append(',');
			}
			queueText.append(type.name());
		}
		out.putString("Queue", queueText.toString());
	}

	/** Прочитать сохранение; {@code null}, если данных нет или они битые. */
	public static SavedState read(CompoundTag in) {
		String rawOwner = in.getString("Owner");
		if (rawOwner.isBlank()) {
			return null;
		}
		UUID owner;
		try {
			owner = UUID.fromString(rawOwner);
		} catch (IllegalArgumentException e) {
			return null;
		}

		List<WaveBudget.Type> queue = new ArrayList<>();
		for (String part : in.getString("Queue").split(",")) {
			if (part.isBlank()) {
				continue;
			}
			try {
				queue.add(WaveBudget.Type.valueOf(part.trim()));
			} catch (IllegalArgumentException ignored) {
				// старый/битый тип просто выкидываем
			}
		}

		return new SavedState(owner,
				in.contains("OwnerName") ? in.getString("OwnerName") : "?",
				in.getInt("Wave"), in.getInt("Stage"),
				in.contains("Lives") ? in.getInt("Lives") : GameManager.MAX_LIVES,
				in.getBoolean("Hard"), in.getBoolean("Buffed"), in.getBoolean("Repeat"),
				in.getInt("Cooldown"), in.getInt("Rewarded"), in.getInt("Drip"),
				in.getInt("StageTimer"), in.getDouble("Carry"),
				List.copyOf(queue), in.getBoolean("Paused"), in.getBoolean("StopUsed"));
	}
}
