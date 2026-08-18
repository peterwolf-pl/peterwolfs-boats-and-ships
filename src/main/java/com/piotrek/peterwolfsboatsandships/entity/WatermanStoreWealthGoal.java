package com.piotrek.peterwolfsboatsandships.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** After an atoll voyage, walk to the bed-side chest and empty the treasure into it. */
final class WatermanStoreWealthGoal extends Goal {
	private static final int SEARCH_RADIUS = 28;
	private static final int MAX_STORE_TICKS = 800;
	private static final double REACH_DISTANCE = 2.6D;

	private final WatermanEntity waterman;
	@Nullable
	private BlockPos chestPos;
	private int storeTicks;
	private boolean active;

	WatermanStoreWealthGoal(WatermanEntity waterman) {
		this.waterman = waterman;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.waterman.isAlive()
			|| this.waterman.isBaby()
			|| this.waterman.isPassenger()
			|| this.waterman.isTrading()
			|| !this.waterman.hasPendingHomeDeposit()) {
			return false;
		}
		this.chestPos = this.findChestBesideBed((ServerLevel)this.waterman.level());
		return this.chestPos != null;
	}

	@Override
	public boolean canContinueToUse() {
		return this.active
			&& this.waterman.isAlive()
			&& !this.waterman.isPassenger()
			&& this.waterman.hasPendingHomeDeposit()
			&& this.chestPos != null
			&& this.storeTicks < MAX_STORE_TICKS;
	}

	@Override
	public void start() {
		this.active = true;
		this.storeTicks = 0;
		this.moveToChest();
	}

	@Override
	public void stop() {
		this.waterman.getNavigation().stop();
		this.active = false;
		this.chestPos = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.chestPos == null) {
			this.active = false;
			return;
		}
		this.storeTicks++;
		Vec3 target = Vec3.atBottomCenterOf(this.chestPos);
		this.waterman.getLookControl().setLookAt(target.x, target.y + 0.6D, target.z, 30.0F, 30.0F);
		if (this.waterman.distanceToSqr(target) > REACH_DISTANCE * REACH_DISTANCE) {
			if (this.waterman.getNavigation().isDone() || this.storeTicks % 25 == 0) {
				this.moveToChest();
			}
			return;
		}

		this.waterman.getNavigation().stop();
		if (this.depositIntoChest((ServerLevel)this.waterman.level(), this.chestPos)) {
			this.active = false;
		} else {
			this.waterman.clearPendingHomeDeposit();
			this.active = false;
		}
	}

	boolean isActive() {
		return this.active;
	}

	private void moveToChest() {
		if (this.chestPos != null) {
			this.waterman.getNavigation().moveTo(
				this.chestPos.getX() + 0.5D,
				this.chestPos.getY(),
				this.chestPos.getZ() + 0.5D,
				1.12D
			);
		}
	}

	private boolean depositIntoChest(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		if (!(blockEntity instanceof Container container)) {
			return false;
		}
		if (container instanceof RandomizableContainer randomizable) {
			randomizable.unpackLootTable(null);
		}

		int deposited = this.waterman.depositAtollWealth(container);
		level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.55F, 1.05F);
		level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.45F, 0.95F);
		if (deposited > 0) {
			level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				this.waterman.getX(),
				this.waterman.getY() + 1.2D,
				this.waterman.getZ(),
				8,
				0.35D,
				0.25D,
				0.35D,
				0.02D
			);
			level.playSound(
				null,
				this.waterman.blockPosition(),
				SoundEvents.VILLAGER_YES,
				SoundSource.NEUTRAL,
				0.9F,
				1.05F
			);
		}
		this.waterman.clearPendingHomeDeposit();
		return deposited > 0;
	}

	@Nullable
	private BlockPos findChestBesideBed(ServerLevel level) {
		BlockPos bed = this.waterman.findHomeBed(level, SEARCH_RADIUS);
		if (bed == null) {
			return null;
		}
		BlockPos chest = findExistingChestBesideBed(level, bed);
		if (chest != null) {
			return chest;
		}
		return placeChestBesideBed(level, bed);
	}

	@Nullable
	private static BlockPos findExistingChestBesideBed(ServerLevel level, BlockPos bed) {
		BlockPos best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (BlockPos part : bedParts(level, bed)) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int dy = -1; dy <= 1; dy++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos candidate = part.offset(dx, dy, dz);
						if (!isHouseholdChest(level, candidate)) {
							continue;
						}
						int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
						if (distance < bestDistance) {
							bestDistance = distance;
							best = candidate.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	@Nullable
	private static BlockPos placeChestBesideBed(ServerLevel level, BlockPos bed) {
		for (BlockPos part : bedParts(level, bed)) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos candidate = part.relative(direction);
				if (!level.getBlockState(candidate).isAir()) {
					continue;
				}
				BlockState below = level.getBlockState(candidate.below());
				if (below.getCollisionShape(level, candidate.below()).isEmpty()) {
					continue;
				}
				level.setBlockAndUpdate(candidate, Blocks.CHEST.defaultBlockState());
				return candidate.immutable();
			}
		}
		return null;
	}

	private static boolean isHouseholdChest(ServerLevel level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof Container && blockEntity instanceof RandomizableContainer;
	}

	private static BlockPos[] bedParts(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof BedBlock)) {
			return new BlockPos[] {pos};
		}
		Direction facing = state.getValue(BedBlock.FACING);
		BlockPos other = state.getValue(BedBlock.PART) == BedPart.HEAD
			? pos.relative(facing.getOpposite())
			: pos.relative(facing);
		if (level.getBlockState(other).getBlock() instanceof BedBlock) {
			return new BlockPos[] {pos, other};
		}
		return new BlockPos[] {pos};
	}
}
