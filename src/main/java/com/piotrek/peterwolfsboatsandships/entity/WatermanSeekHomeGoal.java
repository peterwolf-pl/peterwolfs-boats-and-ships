package com.piotrek.peterwolfsboatsandships.entity;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Walk toward a remembered port, or keep searching for a waterside home. */
final class WatermanSeekHomeGoal extends Goal {
	private final WatermanEntity waterman;
	private int searchTicks;
	private boolean active;

	WatermanSeekHomeGoal(WatermanEntity waterman) {
		this.waterman = waterman;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (!this.waterman.isReturnHomeRequested()
			|| !this.waterman.isAlive()
			|| this.waterman.isBaby()
			|| this.waterman.isPassenger()
			|| this.waterman.isTrading()) {
			return false;
		}
		return !this.waterman.isAtRememberedHome();
	}

	@Override
	public boolean canContinueToUse() {
		return this.active
			&& this.waterman.isReturnHomeRequested()
			&& this.waterman.isAlive()
			&& !this.waterman.isPassenger()
			&& !this.waterman.isAtRememberedHome();
	}

	@Override
	public void start() {
		this.active = true;
		this.searchTicks = 0;
		this.moveTowardHome();
	}

	@Override
	public void stop() {
		this.waterman.getNavigation().stop();
		this.active = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.searchTicks++;
		if (this.waterman.isAtRememberedHome()) {
			this.waterman.clearReturnHomeRequest();
			this.active = false;
			return;
		}
		if (this.searchTicks % 40 == 0 && this.waterman.getPortPos() == null) {
			BlockPos found = this.waterman.seekHomePort((ServerLevel)this.waterman.level());
			if (found != null && !WatermanEntity.isNearAtoll((ServerLevel)this.waterman.level(), found, 72.0D)) {
				this.waterman.setPortPos(found);
			}
		}
		if (this.searchTicks % 30 == 0 || this.waterman.getNavigation().isDone()) {
			this.moveTowardHome();
		}
	}

	boolean isActive() {
		return this.active;
	}

	private void moveTowardHome() {
		BlockPos port = this.waterman.getPortPos();
		if (port != null) {
			boolean path = this.waterman.getNavigation().moveTo(
				port.getX() + 0.5D,
				port.getY(),
				port.getZ() + 0.5D,
				1.15D
			);
			this.waterman.getLookControl().setLookAt(Vec3.atCenterOf(port));
			if (path && !this.waterman.getNavigation().isDone()) {
				return;
			}
			Vec3 here = this.waterman.position();
			double dx = port.getX() + 0.5D - here.x;
			double dz = port.getZ() + 0.5D - here.z;
			double length = Math.sqrt(dx * dx + dz * dz);
			if (length > 1.0D) {
				this.waterman.getNavigation().moveTo(
					here.x + dx / length * 10.0D,
					here.y,
					here.z + dz / length * 10.0D,
					1.15D
				);
			}
			return;
		}
		BlockPos shore = this.pickNearbyShore((ServerLevel)this.waterman.level());
		if (shore != null) {
			this.waterman.getNavigation().moveTo(shore.getX() + 0.5D, shore.getY(), shore.getZ() + 0.5D, 1.05D);
		}
	}

	@Nullable
	private BlockPos pickNearbyShore(ServerLevel level) {
		BlockPos origin = this.waterman.blockPosition();
		BlockPos best = null;
		float bestScore = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < 20; i++) {
			int dx = this.waterman.getRandom().nextInt(33) - 16;
			int dz = this.waterman.getRandom().nextInt(33) - 16;
			int dy = this.waterman.getRandom().nextInt(7) - 3;
			BlockPos sample = origin.offset(dx, dy, dz);
			float score = this.waterman.getWalkTargetValue(sample, level);
			if (score > bestScore) {
				bestScore = score;
				best = sample;
			}
		}
		return bestScore > 0.0F ? best : null;
	}
}
