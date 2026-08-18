package com.piotrek.peterwolfsboatsandships.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Walk to a shore, visibly cast a rod and store an actual caught fish. */
final class WatermanFishingGoal extends Goal {
	private static final int SEARCH_RADIUS = 16;
	private static final int CAST_TICKS = 100;
	private static final int SHOW_CATCH_TICKS = 35;

	private final WatermanEntity waterman;
	@Nullable
	private ShoreSpot shore;
	private int fishingTicks;
	private boolean active;
	private boolean caught;

	WatermanFishingGoal(WatermanEntity waterman) {
		this.waterman = waterman;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.waterman.isAlive()
			|| this.waterman.isBaby()
			|| this.waterman.isPassenger()
			|| this.waterman.isTrading()
			|| this.waterman.isDisplayingAtollWealth()
			|| this.waterman.hasPendingHomeDeposit()
			|| this.waterman.getFishingCooldown() > 0) {
			return false;
		}
		this.shore = this.findShoreSpot((ServerLevel)this.waterman.level());
		if (this.shore == null) {
			// An inland-spawned egg must not trigger the expensive shore scan every tick.
			this.waterman.setFishingCooldown(80 + this.waterman.getRandom().nextInt(81));
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		return this.active
			&& this.waterman.isAlive()
			&& !this.waterman.isPassenger()
			&& !this.waterman.isTrading()
			&& this.shore != null
			&& this.fishingTicks < CAST_TICKS + SHOW_CATCH_TICKS + 60;
	}

	@Override
	public void start() {
		this.active = true;
		this.caught = false;
		this.fishingTicks = 0;
		this.equipRod();
		this.moveToShore();
	}

	@Override
	public void stop() {
		this.storeDisplayedCatch();
		this.equipRod();
		this.waterman.getNavigation().stop();
		this.waterman.setFishingCooldown(260 + this.waterman.getRandom().nextInt(341));
		this.active = false;
		this.shore = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.shore == null) {
			this.active = false;
			return;
		}

		Vec3 standingTarget = Vec3.atBottomCenterOf(this.shore.standing());
		if (this.waterman.distanceToSqr(standingTarget) > 2.5D) {
			this.fishingTicks = 0;
			if (this.waterman.getNavigation().isDone() || this.waterman.tickCount % 30 == 0) {
				this.moveToShore();
			}
			return;
		}

		this.waterman.getNavigation().stop();
		if (this.waterman.getPortPos() == null) {
			this.waterman.setPortPos(this.shore.standing());
		}
		Vec3 floatPos = Vec3.atCenterOf(this.shore.water()).add(0.0D, 0.45D, 0.0D);
		this.waterman.getLookControl().setLookAt(floatPos.x, floatPos.y, floatPos.z, 30.0F, 30.0F);
		this.fishingTicks++;

		ServerLevel level = (ServerLevel)this.waterman.level();
		if (this.fishingTicks == 1) {
			level.playSound(null, this.waterman.blockPosition(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.8F, 0.9F + this.waterman.getRandom().nextFloat() * 0.2F);
		}
		if (!this.caught && this.fishingTicks % 12 == 0) {
			level.sendParticles(ParticleTypes.BUBBLE, floatPos.x, floatPos.y, floatPos.z, 4, 0.18D, 0.05D, 0.18D, 0.02D);
		}
		if (!this.caught && this.fishingTicks >= CAST_TICKS) {
			this.catchFish(level, floatPos);
		}
		if (this.caught && this.fishingTicks >= CAST_TICKS + SHOW_CATCH_TICKS) {
			this.active = false;
		}
	}

	boolean isActive() {
		return this.active;
	}

	private void catchFish(ServerLevel level, Vec3 floatPos) {
		this.caught = true;
		level.sendParticles(ParticleTypes.SPLASH, floatPos.x, floatPos.y, floatPos.z, 12, 0.28D, 0.12D, 0.28D, 0.08D);
		level.playSound(null, this.waterman.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.9F + this.waterman.getRandom().nextFloat() * 0.2F);
		level.playSound(null, this.waterman.blockPosition(), SoundEvents.VILLAGER_WORK_FISHERMAN, SoundSource.NEUTRAL, 0.85F, 1.0F);
		this.waterman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(this.randomFish()));
		this.waterman.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		this.waterman.markFishCaught();
	}

	private Item randomFish() {
		int roll = this.waterman.getRandom().nextInt(12);
		if (roll == 0) {
			return Items.PUFFERFISH;
		}
		if (roll <= 2) {
			return Items.SALMON;
		}
		if (roll == 3) {
			return Items.TROPICAL_FISH;
		}
		return Items.COD;
	}

	private void storeDisplayedCatch() {
		ItemStack held = this.waterman.getItemBySlot(EquipmentSlot.MAINHAND);
		if (held.isEmpty() || held.is(Items.FISHING_ROD)) {
			return;
		}
		ItemStack remainder = this.waterman.getInventory().addItem(held.copy());
		if (!remainder.isEmpty() && this.waterman.level() instanceof ServerLevel level) {
			this.waterman.spawnAtLocation(level, remainder);
		}
		this.waterman.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
	}

	private void equipRod() {
		this.waterman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
		this.waterman.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	private void moveToShore() {
		if (this.shore != null) {
			BlockPos target = this.shore.standing();
			this.waterman.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, 1.05D);
		}
	}

	@Nullable
	private ShoreSpot findShoreSpot(ServerLevel level) {
		BlockPos center = this.waterman.getPortPos();
		if (center == null || center.distSqr(this.waterman.blockPosition()) > SEARCH_RADIUS * SEARCH_RADIUS) {
			center = this.waterman.blockPosition();
		}
		ShoreSpot best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
			for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
				for (int y = -3; y <= 3; y++) {
					BlockPos standing = center.offset(x, y, z);
					if (!isWalkable(level, standing)) {
						continue;
					}
					BlockPos water = adjacentWater(level, standing);
					if (water == null) {
						continue;
					}
					double distance = standing.distSqr(this.waterman.blockPosition());
					if (distance < bestDistance) {
						bestDistance = distance;
						best = new ShoreSpot(standing.immutable(), water.immutable());
					}
				}
			}
		}
		return best;
	}

	private static boolean isWalkable(ServerLevel level, BlockPos standing) {
		return !level.getBlockState(standing.below()).getCollisionShape(level, standing.below()).isEmpty()
			&& level.getBlockState(standing).getCollisionShape(level, standing).isEmpty()
			&& level.getBlockState(standing.above()).getCollisionShape(level, standing.above()).isEmpty()
			&& level.getFluidState(standing).isEmpty();
	}

	@Nullable
	private static BlockPos adjacentWater(ServerLevel level, BlockPos standing) {
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos side = standing.relative(direction);
			if (level.getFluidState(side).is(FluidTags.WATER)) {
				return side;
			}
			if (level.getFluidState(side.below()).is(FluidTags.WATER)) {
				return side.below();
			}
		}
		return null;
	}

	private record ShoreSpot(BlockPos standing, BlockPos water) {
	}
}
