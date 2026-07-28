package com.piotrek.peterwolfsboatsandships.block;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Spot-mode beam angle is derived from game time on the client.
 * Flash mode drives a lit/unlit blink via server tick; off stays dark.
 */
public final class LighthouseLightBlockEntity extends BlockEntity {
	/** Degrees of yaw advance per tick (~full circle every 8 seconds). */
	public static final float DEGREES_PER_TICK = 2.25F;
	/** Flash: ~1s bright, ~1.5s dark (20 on / 30 off). */
	public static final int FLASH_ON_TICKS = 20;
	public static final int FLASH_CYCLE_TICKS = 50;

	public LighthouseLightBlockEntity(final BlockPos pos, final BlockState state) {
		super(PeterwolfsBoatsAndShipsMod.LIGHTHOUSE_LIGHT_BLOCK_ENTITY, pos, state);
	}

	public static void serverTick(final Level level, final BlockPos pos, final BlockState state, final LighthouseLightBlockEntity entity) {
		LighthouseLightMode mode = state.getValue(LighthouseLightBlock.MODE);
		boolean shouldLit = switch (mode) {
			case SPOT -> true;
			case OFF -> false;
			case FLASH -> isFlashOn(level.getGameTime());
		};
		if (state.getValue(LighthouseLightBlock.LIT) != shouldLit) {
			level.setBlock(pos, state.setValue(LighthouseLightBlock.LIT, shouldLit), Block.UPDATE_ALL);
		}
	}

	public static boolean isFlashOn(final long gameTime) {
		long phase = Math.floorMod(gameTime, FLASH_CYCLE_TICKS);
		return phase < FLASH_ON_TICKS;
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
