package com.example.festival.block;

import com.example.festival.blockentity.RelicBlockEntity;
import com.example.festival.core.Msg;
import com.example.festival.core.RelicTier;
import com.example.festival.core.ShopManager;
import com.example.festival.core.TrialManager;
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
			if (TrialManager.of(serverPlayer) == null && TrialManager.at(serverLevel, pos) == null) {
				TrialManager.start(serverLevel, pos, tier, serverPlayer);
			} else {
				Msg.overlay(serverPlayer, Component.translatable("message.festival.already_running"));
			}
		}
		return InteractionResult.CONSUME;
	}
}
