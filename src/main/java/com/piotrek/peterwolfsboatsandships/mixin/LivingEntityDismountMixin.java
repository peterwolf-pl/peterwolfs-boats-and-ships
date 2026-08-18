package com.piotrek.peterwolfsboatsandships.mixin;

import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents riders from detaching until the ship is stopped beside a safe shore block. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDismountMixin {
	@Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
	private void peterwolfs$keepAboardMovingShip(CallbackInfo ci) {
		LivingEntity passenger = (LivingEntity)(Object)this;
		// Clientbound passenger packets rebuild the complete rider list by first
		// calling ejectPassengers(). Cancelling that client-side bookkeeping leaves
		// stale ghost riders, so dismount authority is enforced only by the server.
		if (!passenger.level().isClientSide()
			&& passenger.getVehicle() instanceof AbstractShipEntity ship
			&& !ship.canPassengerDismount(passenger)) {
			ci.cancel();
		}
	}
}
