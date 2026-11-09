package io.github.kalirsa24.lasttrial.mixin;

import io.github.kalirsa24.lasttrial.core.FestivalConfig;
import io.github.kalirsa24.lasttrial.entity.FestivalCreeper;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Взрыв крипера арены без разрушения блоков.
 *
 * {@code Creeper#explodeCreeper} объявлен {@code final}, поэтому переопределить
 * его нельзя — подменяем поведение инъекцией. Взрыв создаётся с
 * {@link Level.ExplosionInteraction#NONE}: урон по существам остаётся, ландшафт
 * и чужие постройки целы.
 *
 * Ванильные криперы и криперы из других модов не затрагиваются: проверка идёт
 * по типу сущности.
 */
@Mixin(Creeper.class)
public abstract class CreeperExplosionMixin {

	/** Радиус как у ванильного крипера (заряженный — вдвое больше). */
	private static final float BASE_RADIUS = 3.0F;

	@Inject(method = "explodeCreeper", at = @At("HEAD"), cancellable = true)
	private void festival$explodeWithoutGrief(CallbackInfo info) {
		Creeper self = (Creeper) (Object) this;
		if (!(self instanceof FestivalCreeper) || FestivalConfig.get().creeperBlockDamage) {
			return;
		}
		if (self.level().isClientSide()) {
			return;
		}

		float radius = self.isPowered() ? BASE_RADIUS * 2.0F : BASE_RADIUS;
		self.level().explode(self, self.getX(), self.getY(), self.getZ(), radius,
				Level.ExplosionInteraction.NONE);
		self.discard();
		info.cancel();
	}
}
