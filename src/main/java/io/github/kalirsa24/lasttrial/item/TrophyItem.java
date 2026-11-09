package io.github.kalirsa24.lasttrial.item;

import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Награда празднества — персональная валюта.
 *
 * Предмет привязан к владельцу: в NBT лежит UUID того, кто его получил.
 * Такую награду нельзя передать — выброс заблокирован, в контейнеры
 * (сундуки, бочки, воронки) она не кладётся, а чужие стаки не считаются
 * при оплате. Админ через {@code /give} получает «пустую» награду без
 * владельца: она не засчитывается никому, то есть выдать валюту другому
 * игроку нельзя.
 */
public class TrophyItem extends Item {

	private static final String TAG_OWNER = "Owner";
	/** Размер стака наград; совпадает с {@code stacksTo(64)} в реестре. */
	public static final int MAX_STACK = 64;

	public TrophyItem(Properties properties) {
		super(properties);
	}

	// ------------------------------------------------------------------
	// Владелец
	// ------------------------------------------------------------------

	@Nullable
	public static UUID ownerOf(ItemStack stack) {
		if (!(stack.getItem() instanceof TrophyItem)) {
			return null;
		}
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG_OWNER)) {
			return null;
		}
		try {
			return UUID.fromString(tag.getString(TAG_OWNER));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static boolean ownedBy(ItemStack stack, UUID player) {
		UUID owner = ownerOf(stack);
		return owner != null && owner.equals(player);
	}

	public static void bind(ItemStack stack, UUID player) {
		stack.getOrCreateTag().putString(TAG_OWNER, player.toString());
	}

	// ------------------------------------------------------------------
	// Выдача
	// ------------------------------------------------------------------

	/** Выдать награды: сначала досыпаем в уже привязанные стаки, потом новые. */
	public static void grant(ServerPlayer player, int count) {
		if (count <= 0) {
			return;
		}
		int left = count;
		var inventory = player.getInventory();

		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() instanceof TrophyItem && ownedBy(stack, player.getUUID())) {
				int space = MAX_STACK - stack.getCount();
				if (space > 0) {
					int add = Math.min(space, left);
					stack.grow(add);
					left -= add;
				}
			}
		}

		while (left > 0) {
			int add = Math.min(MAX_STACK, left);
			ItemStack stack = new ItemStack(ModItems.TROPHY, add);
			bind(stack, player.getUUID());
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			left -= add;
		}
	}

	// ------------------------------------------------------------------
	// Запрет передачи
	// ------------------------------------------------------------------

	/**
	 * В контейнеры награда не кладётся: сундуки, бочки и воронки — самый
	 * простой способ передать предмет другому игроку.
	 */
	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	// ------------------------------------------------------------------
	// Отображение
	// ------------------------------------------------------------------

	@Override
	public Component getName(ItemStack stack) {
		return ownerOf(stack) == null
				? Component.translatable("item.lasttrial.trophy.unbound")
				: super.getName(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level,
								List<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, level, tooltip, flag);
		tooltip.add(Component.translatable("tooltip.lasttrial.trophy_use").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.lasttrial.trophy_hint").withStyle(ChatFormatting.DARK_GRAY));

		if (ownerOf(stack) == null) {
			tooltip.add(Component.translatable("tooltip.lasttrial.trophy_unbound")
					.withStyle(ChatFormatting.RED));
		} else {
			tooltip.add(Component.translatable("tooltip.lasttrial.trophy_personal")
					.withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
