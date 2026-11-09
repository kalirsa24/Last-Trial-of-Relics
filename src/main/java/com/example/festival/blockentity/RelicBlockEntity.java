package com.example.festival.blockentity;

import com.example.festival.block.RelicBlock;
import com.example.festival.core.RelicTier;
import com.example.festival.core.Trial;
import com.example.festival.core.TrialManager;
import com.example.festival.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Реликвия: здоровье по уровню, регенерация и сохранение забега.
 *
 * Забег хранится рядом с блоком — перезапуск сервера посреди волны больше
 * не обнуляет прогресс: волна, этап, жизни и очередь мобов восстанавливаются.
 */
public class RelicBlockEntity extends BlockEntity {

	public static final int BASE_HEALTH = 100;
	private static final String NBT_HEALTH = "Health";
	private static final String NBT_TRIAL = "Trial";

	private final RelicTier tier;
	private final int maxHealth;
	private int health;
	private int regenCooldown = 0;

	/** Забег, прочитанный из NBT: восстанавливаем на первом серверном тике. */
	private Trial.SavedState pendingTrial;

	public RelicBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.RELIC, pos, state);
		this.tier = state.getBlock() instanceof RelicBlock relic ? relic.getTier() : RelicTier.GREATER;
		this.maxHealth = (int) Math.round(BASE_HEALTH * tier.getHealthMultiplier());
		this.health = maxHealth;
	}

	public RelicTier getTier() {
		return tier;
	}

	public int getMaxHealth() {
		return maxHealth;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RelicBlockEntity be) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}

		// На loadAdditional уровень ещё не готов — восстанавливаем забег здесь
		if (be.pendingTrial != null) {
			Trial.SavedState saved = be.pendingTrial;
			be.pendingTrial = null;
			TrialManager.restore(serverLevel, pos, be.tier, saved);
		}

		Trial trial = TrialManager.at(serverLevel, pos);

		if (be.health <= 0) {
			if (trial != null) {
				trial.onRelicDestroyed();
			}
			return;
		}

		// Медленное восстановление вне боя
		if (be.regenCooldown > 0) {
			be.regenCooldown--;
		} else if (be.health < be.maxHealth) {
			be.heal(1);
			be.regenCooldown = 60;
		}
	}

	public void damage(int amount) {
		if (health <= 0 || amount <= 0) {
			return;
		}
		this.health = Math.max(0, this.health - amount);
		setChanged();
		refreshHud();
	}

	public void heal(int amount) {
		if (amount <= 0) {
			return;
		}
		this.health = Math.min(maxHealth, this.health + amount);
		setChanged();
		refreshHud();
	}

	public void resetHealth() {
		this.health = maxHealth;
		setChanged();
		refreshHud();
	}

	public int getHealth() {
		return health;
	}

	private void refreshHud() {
		if (level instanceof ServerLevel serverLevel) {
			Trial trial = TrialManager.at(serverLevel, worldPosition);
			if (trial != null) {
				trial.hud().refresh();
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putInt(NBT_HEALTH, health);
		if (level instanceof ServerLevel serverLevel) {
			Trial trial = TrialManager.at(serverLevel, worldPosition);
			if (trial != null) {
				CompoundTag trialTag = new CompoundTag();
				trial.save(trialTag);
				tag.put(NBT_TRIAL, trialTag);
			}
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		health = Math.min(maxHealth, tag.contains(NBT_HEALTH) ? tag.getInt(NBT_HEALTH) : maxHealth);
		if (tag.contains(NBT_TRIAL)) {
			pendingTrial = Trial.read(tag.getCompound(NBT_TRIAL));
		}
	}
}
