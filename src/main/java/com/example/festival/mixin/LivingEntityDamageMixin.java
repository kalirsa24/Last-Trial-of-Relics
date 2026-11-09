package com.example.festival.mixin;

import com.example.festival.core.FestivalConfig;
import com.example.festival.core.RelicBonus;
import com.example.festival.core.SkillManager;
import com.example.festival.core.TrialTags;
import com.example.festival.item.TotemOfPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Правила урона празднества.
 *
 * Исходящий урон игрока усиливают тотемы (два лучших) и ветка «Сила»:
 * итог = базовый × (1 + бонус тотемов + бонус навыка), плюс шанс двойного удара.
 *
 * Входящий урон игрока снижают Реликвии в инвентаре (15/30/45%, максимум 4 штуки
 * и не больше 75%) и ветка «Оберег». Мобы волны бьют вполсилы.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

	/** Предохранитель от «снежного кома» множителей из разных модов. */
	private static final float MAX_MULTIPLIER = 5.0F;

	/** Исходящий урон: тотемы + навык + шанс двойного удара. */
	@ModifyVariable(
			method = "hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
			at = @At("HEAD"),
			argsOnly = true)
	private float festival$outgoingDamage(float amount, DamageSource source) {
		if (!FestivalConfig.get().damageRules) {
			return amount;
		}
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			return amount;
		}
		float multiplier = 1.0F + TotemOfPower.playerBonus(player) + SkillManager.damageBonus(player);
		if (SkillManager.rollDoubleDamage(player)) {
			multiplier *= 2.0F;
		}
		return amount * Math.min(MAX_MULTIPLIER, multiplier);
	}

	/** Входящий урон: Реликвии, навык «Оберег» и правило мобов празднества. */
	@ModifyVariable(
			method = "actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V",
			at = @At("HEAD"),
			argsOnly = true)
	private float festival$incomingDamage(float amount, DamageSource source) {
		LivingEntity self = (LivingEntity) (Object) this;

		float result = amount;

		if (!FestivalConfig.get().damageRules) {
			return result;
		}
		// мобы празднества бьют вполсилы — кого угодно: игрока, голема, друг друга
		if (isFestivalMob(source.getEntity())) {
			result *= 0.5F;
		}

		if (!(self instanceof ServerPlayer player)) {
			return result;
		}

		double bonus = RelicBonus.reduction(player) + SkillManager.wardBonus(player);
		if (bonus > 0.0D) {
			result *= (float) Math.max(0.0D, 1.0D - bonus);
		}
		return result;
	}

	private static boolean isFestivalMob(Entity attacker) {
		return attacker instanceof Mob mob && mob.getTags().contains(TrialTags.MOB);
	}
}
