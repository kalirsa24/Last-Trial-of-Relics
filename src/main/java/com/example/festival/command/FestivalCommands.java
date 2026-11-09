package com.example.festival.command;

import com.example.festival.FestivalMod;
import com.example.festival.core.ArenaData;
import com.example.festival.core.ArenaManager;
import com.example.festival.core.GameManager;
import com.example.festival.core.RelicTier;
import com.example.festival.core.RoleManager;
import com.example.festival.core.ShopManager;
import com.example.festival.core.SkillManager;
import com.example.festival.core.Trial;
import com.example.festival.core.TrialManager;
import com.example.festival.core.WaveBudget;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/**
 * Команды мода.
 *
 * Игрокам: start, pickup, arena info/reset, shop, buy, role, lives, wave.
 * Операторам: stop, forcestart, arena remove, admin waves/clearwaves, shoplist.
 */
public class FestivalCommands {

	private static final double PICKUP_DISTANCE = 5.0D;

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("festival")
					// ---------------- для игроков ----------------
					.then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
					.then(Commands.literal("start").executes(ctx -> startAtRelic(ctx.getSource())))
					.then(Commands.literal("pickup").executes(ctx -> pickup(ctx.getSource())))
					.then(Commands.literal("shop").executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						ShopManager.open(player, ShopManager.tierFor(player));
						return 1;
					}))
					.then(Commands.literal("buy")
							.then(Commands.argument("good", StringArgumentType.greedyString())
									.suggests((ctx, builder) -> {
										for (ShopManager.Good good : ShopManager.all()) {
											builder.suggest(good.id());
										}
										return builder.buildFuture();
									})
									.executes(ctx -> {
										ServerPlayer player = ctx.getSource().getPlayerOrException();
										String id = StringArgumentType.getString(ctx, "good");
										return ShopManager.buy(player, id) ? 1 : 0;
									})))
					.then(Commands.literal("role")
							.then(Commands.argument("role", StringArgumentType.word())
									.suggests((ctx, builder) -> {
										builder.suggest("tank").suggest("dps").suggest("support");
										return builder.buildFuture();
									})
									.executes(ctx -> {
										ServerPlayer player = ctx.getSource().getPlayerOrException();
										String role = StringArgumentType.getString(ctx, "role").toUpperCase();
										try {
											RoleManager.setRole(player, RoleManager.PlayerRole.valueOf(role));
										} catch (IllegalArgumentException e) {
											ctx.getSource().sendFailure(
													Component.translatable("message.festival.bad_role"));
											return 0;
										}
										return 1;
									})))
					.then(Commands.literal("lives").executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayer();
						Trial trial = TrialManager.of(player);
						if (trial == null) {
							ctx.getSource().sendFailure(Component.translatable("message.festival.no_trial"));
							return 0;
						}
						ctx.getSource().sendSuccess(() -> Component.translatable("message.festival.lives_status",
								trial.lives(), GameManager.MAX_LIVES), false);
						return 1;
					}))
					.then(Commands.literal("wave")
							.executes(ctx -> waveInfo(ctx.getSource()))
							.then(Commands.literal("info").executes(ctx -> waveInfo(ctx.getSource())))
							.then(Commands.literal("stage")
									.requires(source -> source.hasPermission(2))
									.then(Commands.argument("stage", IntegerArgumentType.integer(1, 8))
											.executes(ctx -> waveStage(ctx.getSource(),
													IntegerArgumentType.getInteger(ctx, "stage")))))
							.then(Commands.literal("spawn")
									.requires(source -> source.hasPermission(2))
									.then(Commands.argument("type", StringArgumentType.word())
											.suggests((ctx, builder) -> {
												for (WaveBudget.Type type : WaveBudget.Type.values()) {
													builder.suggest(type.id());
												}
												return builder.buildFuture();
											})
											.then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
													.executes(ctx -> waveSpawn(ctx.getSource(),
															StringArgumentType.getString(ctx, "type"),
															IntegerArgumentType.getInteger(ctx, "count"))))))
							.then(Commands.literal("skip")
									.requires(source -> source.hasPermission(2))
									.then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
											.executes(ctx -> waveSkip(ctx.getSource(),
													IntegerArgumentType.getInteger(ctx, "count"))))))
					// /festival arena info|reset — игрокам, remove — админам
					.then(Commands.literal("arena")
							.then(Commands.literal("info").executes(ctx -> arenaInfo(ctx.getSource())))
							.then(Commands.literal("reset").executes(ctx -> arenaReset(ctx.getSource())))
							.then(Commands.literal("remove")
									.requires(source -> source.hasPermission(2))
									.then(Commands.argument("player", StringArgumentType.word())
											.executes(ctx -> adminRemoveArena(ctx.getSource(),
													StringArgumentType.getString(ctx, "player"))))))
					// ---------------- для операторов ----------------
					.then(Commands.literal("stop")
							.requires(source -> source.hasPermission(2))
							.executes(ctx -> {
								ServerPlayer player = ctx.getSource().getPlayer();
								if (player != null) {
									GameManager.stop(player);
								} else {
									GameManager.stopAll();
								}
								ctx.getSource().sendSuccess(
										() -> Component.translatable("message.festival.stopped"), true);
								return 1;
							}))
					.then(Commands.literal("forcestart")
							.requires(source -> source.hasPermission(2))
							.then(Commands.argument("tier", StringArgumentType.word())
									.suggests((ctx, builder) -> {
										builder.suggest("basic").suggest("greater").suggest("eternal");
										return builder.buildFuture();
									})
									.executes(ctx -> forceStart(ctx.getSource(),
											RelicTier.byId(StringArgumentType.getString(ctx, "tier"))))))
					// /festival skill — древо развития за награды празднества
					.then(Commands.literal("skill").executes(ctx -> {
						ServerPlayer player = ctx.getSource().getPlayerOrException();
						SkillManager.open(player);
						return 1;
					}))
					// /festival admin waves <player> <count> — своё число волн игроку
					.then(Commands.literal("admin")
							.requires(source -> source.hasPermission(2))
							.then(Commands.literal("waves")
									.then(Commands.argument("player", StringArgumentType.word())
											.then(Commands.argument("count", IntegerArgumentType.integer(1, 500))
													.executes(ctx -> adminSetWaves(ctx.getSource(),
															StringArgumentType.getString(ctx, "player"),
															IntegerArgumentType.getInteger(ctx, "count"))))))
							.then(Commands.literal("clearwaves")
									.then(Commands.argument("player", StringArgumentType.word())
											.executes(ctx -> adminClearWaves(ctx.getSource(),
													StringArgumentType.getString(ctx, "player"))))))
					.then(Commands.literal("shoplist")
							.requires(source -> source.hasPermission(2))
							.executes(ctx -> {
								CommandSourceStack source = ctx.getSource();
								source.sendSuccess(() -> Component.translatable("message.festival.shop_title"), false);
								for (ShopManager.Category category : ShopManager.Category.values()) {
									// вечная Реликвия видит весь ассортимент
									var goods = ShopManager.inCategory(category, com.example.festival.core.RelicTier.ETERNAL);
									if (goods.isEmpty()) {
										continue;
									}
									source.sendSuccess(() -> Component.translatable("message.festival.shop_category",
											Component.translatable(category.nameKey()), goods.size()), false);
									for (ShopManager.Good good : goods) {
										source.sendSuccess(() -> Component.translatable("message.festival.shop_line",
												good.id(), good.name(), good.price()), false);
									}
								}
								return 1;
							})));

			dispatcher.register(Commands.literal("fest").redirect(root));
		});
	}

	// ------------------------------------------------------------------
	// Игровые команды
	// ------------------------------------------------------------------

	/** Список всех команд: доступные и операторские. */
	private static int help(CommandSourceStack source) {
		source.sendSuccess(() -> Component.translatable("help.festival.title"), false);

		String[][] player = {
				{"/festival start", "help.festival.start"},
				{"/festival pickup", "help.festival.pickup"},
				{"/festival shop", "help.festival.shop"},
				{"/festival buy <n>", "help.festival.buy"},
				{"/festival role <tank|dps|support>", "help.festival.role"},
				{"/festival lives", "help.festival.lives"},
				{"/festival wave", "help.festival.wave"},
				{"/festival arena info", "help.festival.arena_info"},
				{"/festival arena reset", "help.festival.arena_reset"},
				{"/festival skill", "help.festival.skill"},
				{"/festival help", "help.festival.help"},
		};
		String[][] admin = {
				{"/festival stop", "help.festival.stop"},
				{"/festival forcestart <tier>", "help.festival.forcestart"},
				{"/festival wave info", "help.festival.wave_info"},
				{"/festival wave stage <n>", "help.festival.wave_stage"},
				{"/festival wave spawn <моб> <n>", "help.festival.wave_spawn"},
				{"/festival wave skip <n>", "help.festival.wave_skip"},
				{"/festival arena remove <ник>", "help.festival.arena_remove"},
				{"/festival admin waves <ник> <n>", "help.festival.admin_waves"},
				{"/festival admin clearwaves <ник>", "help.festival.admin_clearwaves"},
				{"/festival shoplist", "help.festival.shoplist"},
		};

		for (String[] row : player) {
			source.sendSuccess(() -> Component.translatable("help.festival.line", row[0],
					Component.translatable(row[1])), false);
		}

		source.sendSuccess(() -> Component.translatable("help.festival.admin_title"), false);
		for (String[] row : admin) {
			source.sendSuccess(() -> Component.translatable("help.festival.admin_line", row[0],
					Component.translatable(row[1])), false);
		}
		return 1;
	}

	private static int startAtRelic(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		return GameManager.requestStart(player) ? 1 : 0;
	}

	private static int forceStart(CommandSourceStack source, RelicTier tier) {
		try {
			ServerPlayer player = source.getPlayerOrException();
			Trial trial = GameManager.forceStart(player, tier);
			if (trial == null) {
				source.sendFailure(Component.translatable("message.festival.no_relic"));
				return 0;
			}
			return 1;
		} catch (CommandSyntaxException e) {
			source.sendFailure(Component.translatable("message.festival.need_player"));
			return 0;
		}
	}

	// ------------------------------------------------------------------
	// Волна: информация и отладка
	// ------------------------------------------------------------------

	private static int waveInfo(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Trial trial = TrialManager.of(player);
		if (trial == null) {
			source.sendFailure(Component.translatable("message.festival.no_trial"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("message.festival.wave_info",
				trial.currentWave(), trial.maxWaves(),
				trial.currentStage() + 1, trial.stages(),
				trial.aliveCount(), trial.queueSize(), trial.lives()), false);
		return 1;
	}

	private static int waveStage(CommandSourceStack source, int stage) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Trial trial = TrialManager.of(player);
		if (trial == null || !trial.debugStage(stage - 1)) {
			source.sendFailure(Component.translatable("message.festival.no_trial"));
			return 0;
		}
		source.sendSuccess(() -> Component.translatable("message.festival.wave_info",
				trial.currentWave(), trial.maxWaves(),
				trial.currentStage() + 1, trial.stages(),
				trial.aliveCount(), trial.queueSize(), trial.lives()), true);
		return 1;
	}

	private static int waveSpawn(CommandSourceStack source, String typeId, int count) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Trial trial = TrialManager.of(player);
		WaveBudget.Type type = WaveBudget.Type.byId(typeId);
		if (trial == null || type == null) {
			source.sendFailure(Component.translatable(type == null
					? "message.festival.unknown_mob" : "message.festival.no_trial", typeId));
			return 0;
		}
		int spawned = trial.debugSpawn(type, count);
		source.sendSuccess(() -> Component.translatable("message.festival.debug_spawned", spawned, type.id()), true);
		return spawned > 0 ? 1 : 0;
	}

	private static int waveSkip(CommandSourceStack source, int count) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Trial trial = TrialManager.of(player);
		if (trial == null) {
			source.sendFailure(Component.translatable("message.festival.no_trial"));
			return 0;
		}
		return trial.skipWaves(count, true) ? 1 : 0;
	}

	private static int pickup(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = (ServerLevel) player.level();

		HitResult hit = player.pick(PICKUP_DISTANCE, 0.0F, false);
		if (!(hit instanceof BlockHitResult blockHit)) {
			source.sendFailure(Component.translatable("message.festival.pickup_no_block"));
			return 0;
		}

		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();

		if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(FestivalMod.MOD_ID)) {
			source.sendFailure(Component.translatable("message.festival.pickup_not_festival"));
			return 0;
		}

		if (TrialManager.at(level, pos) != null) {
			TrialManager.stopAt(level, pos);
		}

		ItemStack drop = new ItemStack(block.asItem());
		level.removeBlock(pos, false);

		if (!player.getInventory().add(drop)) {
			player.drop(drop, false);
		}

		source.sendSuccess(() -> Component.translatable("message.festival.picked_up", drop.getHoverName()), true);
		return 1;
	}

	private static int arenaInfo(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ArenaManager.ensureLoaded((ServerLevel) player.level());

		Optional<ArenaData.Arena> own = ArenaManager.arenaOf(player.getUUID());
		Optional<ArenaData.Arena> here = ArenaManager.arenaAt(player.blockPosition());

		if (own.isPresent()) {
			BlockPos pos = own.get().pos();
			source.sendSuccess(() -> Component.translatable("message.festival.arena_info",
					pos.getX(), pos.getY(), pos.getZ()), false);
			return 1;
		}
		if (here.isPresent()) {
			String owner = here.get().ownerName();
			source.sendSuccess(() -> Component.translatable("message.festival.arena_foreign", owner), false);
			return 1;
		}
		source.sendSuccess(() -> Component.translatable("message.festival.arena_none"), false);
		return 1;
	}

	private static int arenaReset(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ArenaManager.ensureLoaded((ServerLevel) player.level());

		if (ArenaManager.arenaOf(player.getUUID()).isEmpty()) {
			source.sendFailure(Component.translatable("message.festival.arena_not_owner"));
			return 0;
		}

		if (TrialManager.of(player) != null) {
			GameManager.stop(player);
		}
		ArenaManager.removeOwn((ServerLevel) player.level(), player);
		source.sendSuccess(() -> Component.translatable("message.festival.arena_reset"), true);
		return 1;
	}

	// ------------------------------------------------------------------
	// Админские команды
	// ------------------------------------------------------------------

	private static int adminRemoveArena(CommandSourceStack source, String playerName) {
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			source.sendFailure(Component.translatable("message.festival.player_offline", playerName));
			return 0;
		}

		ArenaManager.ensureLoaded((ServerLevel) target.level());
		Trial targetTrial = TrialManager.of(target);
		if (targetTrial != null) {
			targetTrial.stop();
		}
		boolean removed = ArenaManager.removeOf((ServerLevel) target.level(), target.getUUID());

		if (removed) {
			source.sendSuccess(() -> Component.translatable("message.festival.arena_removed", playerName), true);
			return 1;
		}
		source.sendFailure(Component.translatable("message.festival.arena_not_owner_target", playerName));
		return 0;
	}

	private static int adminSetWaves(CommandSourceStack source, String playerName, int count) {
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			source.sendFailure(Component.translatable("message.festival.player_offline", playerName));
			return 0;
		}
		ArenaManager.ensureLoaded((ServerLevel) target.level());
		ArenaManager.setWaveOverride(target.getUUID(), count);
		source.sendSuccess(() -> Component.translatable("message.festival.waves_set", playerName, count), true);
		return 1;
	}

	private static int adminClearWaves(CommandSourceStack source, String playerName) {
		ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
		if (target == null) {
			source.sendFailure(Component.translatable("message.festival.player_offline", playerName));
			return 0;
		}
		ArenaManager.ensureLoaded((ServerLevel) target.level());
		ArenaManager.clearWaveOverride(target.getUUID());
		source.sendSuccess(() -> Component.translatable("message.festival.waves_cleared", playerName), true);
		return 1;
	}
}
