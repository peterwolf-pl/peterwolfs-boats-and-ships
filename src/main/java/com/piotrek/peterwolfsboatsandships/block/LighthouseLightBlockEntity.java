package com.piotrek.peterwolfsboatsandships.block;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marker BE for the lighthouse lamp. Beam angle is derived from game time in the
 * client renderer so all clients stay in sync without extra packets.
 */
public final class LighthouseLightBlockEntity extends BlockEntity {
	/** Degrees of yaw advance per tick (~full circle every 8 seconds). */
	public static final float DEGREES_PER_TICK = 2.25F;

	public LighthouseLightBlockEntity(final BlockPos pos, final BlockState state) {
		super(PeterwolfsBoatsAndShipsMod.LIGHTHOUSE_LIGHT_BLOCK_ENTITY, pos, state);
	}

	/** World-synced rotation angle in degrees. */
	public float getBeamYaw(final float partialTick) {
		if (this.level == null) {
			return 0.0F;
		}
		return (this.level.getGameTime() + partialTick) * DEGREES_PER_TICK;
	}

	/** Animation phase used for beam scroll. */
	public float getAnimationTime(final float partialTick) {
		if (this.level == null) {
			return 0.0F;
		}
		return Math.floorMod(this.level.getGameTime(), 40) + partialTick;
	}
}
