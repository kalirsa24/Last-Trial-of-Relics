package com.example.festival.item;

import com.example.festival.core.Msg;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ключ празднества — единственный источник тотемов усиления.
 *
 * ПКМ ставит перед игроком сундук празднества (лут: тотемы, монеты, пир)
 * и расходуется. Других способов получить тотем нет.
 */
public class FestivalKeyItem extends Item {

	private static final ResourceLocation CHEST_LOOT =
			new ResourceLocation("festival", "chests/festival_key");

	public FestivalKeyItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResultHolder.pass(stack);
		}

		BlockPos pos = findPlace(serverLevel, player);
		if (pos == null) {
			Msg.overlay(serverPlayer, Component.translatable("message.festival.key_no_room"));
			return InteractionResultHolder.fail(stack);
		}

		serverLevel.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		if (serverLevel.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
			chest.setLootTable(CHEST_LOOT, serverLevel.random.nextLong());
		}
		serverLevel.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
		Msg.overlay(serverPlayer, Component.translatable("message.festival.key_opened"));

		stack.shrink(1);
		return InteractionResultHolder.consume(stack);
	}

	/** Свободная клетка перед игроком: своя позиция, затем шаг вперёд и выше. */
	private static BlockPos findPlace(ServerLevel level, Player player) {
		BlockPos base = player.blockPosition();
		BlockPos forward = base.relative(player.getDirection());
		BlockPos[] candidates = {forward, base, forward.above(), base.above()};
		for (BlockPos pos : candidates) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.canBeReplaced()) {
				return pos;
			}
		}
		return null;
	}
}
