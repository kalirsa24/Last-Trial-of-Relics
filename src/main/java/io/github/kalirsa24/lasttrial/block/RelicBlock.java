package io.github.kalirsa24.lasttrial.block;

import io.github.kalirsa24.lasttrial.blockentity.RelicBlockEntity;
import io.github.kalirsa24.lasttrial.core.ArenaData;
import io.github.kalirsa24.lasttrial.core.ArenaManager;
import io.github.kalirsa24.lasttrial.core.ArenaValidator;
import io.github.kalirsa24.lasttrial.core.FestivalConfig;
import io.github.kalirsa24.lasttrial.core.Msg;
import io.github.kalirsa24.lasttrial.core.RelicTier;
import io.github.kalirsa24.lasttrial.core.ShopManager;
import io.github.kalirsa24.lasttrial.core.TrialManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Реликвия — центральный объект арены. ПКМ запускает «Празднество»,
 * Shift+ПКМ открывает магазин. Уровень (10 / 25 / бесконечные волны)
 * задаётся при создании блока.
 *
 * В 1.20.1 у блока нет codec-описания и {@code useItemOn} — только
 * классический {@code use(...)}.
 */
public class RelicBlock extends Block implements EntityBlock {

	private final RelicTier tier;

	public RelicBlock(RelicTier tier, BlockBehaviour.Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public RelicTier getTier() {
		return tier;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RelicBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
																  BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof RelicBlockEntity relic) {
				RelicBlockEntity.serverTick(lvl, pos, st, relic);
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
								 InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			// Shift + ПКМ — магазин, обычный ПКМ — запуск
			if (player.isShiftKeyDown()) {
				ShopManager.open(serverPlayer, tier);
				return InteractionResult.CONSUME;
			}
			tryStart(serverLevel, pos, serverPlayer);
		}
		return InteractionResult.CONSUME;
	}

	/**
	 * Запуск забега с блока: те же проверки, что и у команды/клавиши —
	 * целый пол, свободный слот и право владельца. Раньше ПКМ по блоку
	 * обходил валидацию арены и позволял стартовать чужое испытание.
	 */
	private void tryStart(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (TrialManager.of(player) != null || TrialManager.at(level, pos) != null) {
			Msg.overlay(player, Component.translatable("message.lasttrial.already_running"));
			return;
		}

		Optional<ArenaData.Arena> arena = ArenaManager.arenaAt(level, pos);
		if (arena.isPresent() && !arena.get().owner().equals(player.getUUID())
				&& !player.hasPermissions(2)) {
			Msg.overlay(player, Component.translatable("message.lasttrial.arena_foreign_start",
					arena.get().ownerName()));
			return;
		}

		ArenaValidator.Result check = ArenaValidator.validate(level, pos, 1.0D);
		if (!check.ok()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.arena_damaged",
					Math.round(check.floorRatio() * 100.0D)));
			return;
		}

		if (TrialManager.isFull()) {
			Msg.overlay(player, Component.translatable("message.lasttrial.limit_reached",
					FestivalConfig.get().maxActiveTrials));
			return;
		}

		TrialManager.start(level, pos, tier, player);
	}
}
