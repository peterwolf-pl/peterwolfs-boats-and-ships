package com.piotrek.peterwolfsboatsandships.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import org.spongepowered.asm.mixin.Mixin;

/**
 * UnionLib 13.x on Minecraft 26.2 injects into {@code AbstractBoat.canAddPassenger}
 * (mapped as {@code updateFluidOnEyes}) and NPEs when the eye FluidState is null.
 * Water World fishermen and other mods call {@code startRiding} every few ticks, so
 * that takes the whole server down. Fall back to the vanilla passenger-count check.
 */
@Mixin(AbstractBoat.class)
public abstract class AbstractBoatPassengerMixin {
	@WrapMethod(method = "canAddPassenger")
	private boolean peterwolfs$guardUnionLibFluidNpe(Entity passenger, Operation<Boolean> original) {
		try {
			return original.call(passenger);
		} catch (NullPointerException ignored) {
			return ((AbstractBoat)(Object)this).getPassengers().size() < 2;
		}
	}
}
