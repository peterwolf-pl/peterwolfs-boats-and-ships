package com.piotrek.peterwolfsboatsandships.client.mixin;

import com.piotrek.peterwolfsboatsandships.client.PeterwolfsBoatsAndShipsClient;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Widens FOV slightly while the ship W speed boost camera pull is active,
 * so first-person also gets a speed "zoom-out" feel.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	/** Max FOV multiplier contribution at full boost (on top of vanilla FOV). */
	private static final float BOOST_FOV_EXTRA = 0.16F;

	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void peterwolfs$shipBoostFov(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		float intensity = PeterwolfsBoatsAndShipsClient.boostCameraIntensity();
		if (intensity <= 0.001F) {
			return;
		}
		// Match vanilla FOV-effect scale option so accessibility settings still apply.
		float extra = intensity * BOOST_FOV_EXTRA * effectScale;
		cir.setReturnValue(cir.getReturnValueF() * (1.0F + extra));
	}
}
