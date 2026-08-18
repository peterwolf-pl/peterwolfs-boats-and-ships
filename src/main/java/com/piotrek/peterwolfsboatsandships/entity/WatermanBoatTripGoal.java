package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Walk to an empty mod ship, take the helm, make a short cruise and return. */
final class WatermanBoatTripGoal extends Goal {
	private static final double SEARCH_RANGE = 28.0D;
	private static final double BOARD_DISTANCE_SQR = 10.0D;
	private static final int MAX_TRIP_TICKS = 2400;
	private static final int MAX_ATOLL_TRIP_TICKS = 12000;
	private static final int MAX_OUTBOUND_TICKS = 900;
	private static final int MAX_ATOLL_OUTBOUND_TICKS = 6000;
	private static final int ROUTE_CHECK_INTERVAL = 20;
	private static final int MAX_OUTBOUND_BLOCKED_TICKS = 120;
	private static final int ATOLL_TRADE_TICKS = 140;
	private static final int VOYAGE_TICKET_RADIUS = 4;
	private static final double LOCAL_ROUTE_LEG = 36.0D;
	private static final float ATOLL_TRIP_CHANCE = 0.60F;

	private final WatermanEntity waterman;
	@Nullable
	private AbstractShipEntity ship;
	@Nullable
	private Vec3 portWater;
	@Nullable
	private Vec3 excursionTarget;
	@Nullable
	private BlockPos portLand;
	private List<Vec3> routeWaypoints = List.of();
	private List<Vec3> returnFallback = List.of();
	private final List<Vec3> outboundTrail = new ArrayList<>();
	private int routeWaypointIndex;
	private int routeCheckCooldown;
	private int blockedRouteTicks;
	private int tradingTicks;
	private boolean atollTradeVoyage;
	private boolean atollWealthLoaded;
	@Nullable
	private ChunkPos voyageTicketChunk;
	private Phase phase = Phase.APPROACHING;
	private int tripTicks;
	private boolean active;
	private boolean tripCounted;

	WatermanBoatTripGoal(WatermanEntity waterman) {
		this.waterman = waterman;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
	}

	@Override
	public boolean canUse() {
		if (!this.waterman.isAlive() || this.waterman.isBaby() || this.waterman.isTrading()) {
			return false;
		}
		if (this.waterman.getVehicle() instanceof AbstractShipEntity ridden && ridden.isHelmsman(this.waterman)) {
			this.ship = ridden;
			return true;
		}
		if (this.waterman.hasPendingHomeDeposit()
			|| this.waterman.getBoatTripCooldown() > 0
			|| this.waterman.isPassenger()) {
			return false;
		}
		this.ship = this.findAvailableShip();
		if (this.ship == null) {
			// Avoid scanning a wide entity box every tick when no harbour boat exists.
			this.waterman.setBoatTripCooldown(40 + this.waterman.getRandom().nextInt(41));
			return false;
		}
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (!this.active
			|| !this.waterman.isAlive()
			|| this.waterman.isTrading()
			|| this.ship == null
			|| !this.ship.isAlive()) {
			return false;
		}
		int limit = this.atollTradeVoyage ? MAX_ATOLL_TRIP_TICKS : MAX_TRIP_TICKS;
		// Never abandon a return mid-ocean just because the outbound clock ran out.
		if (this.phase == Phase.RETURNING || this.phase == Phase.WALKING_HOME || this.phase == Phase.TRADING) {
			limit += MAX_ATOLL_TRIP_TICKS;
		}
		return this.tripTicks < limit;
	}

	@Override
	public void start() {
		this.active = true;
		this.tripTicks = 0;
		this.tripCounted = false;
		this.outboundTrail.clear();
		this.portLand = this.waterman.getPortPos();
		if (this.portLand == null) {
			this.portLand = this.waterman.blockPosition().immutable();
			this.waterman.setPortPos(this.portLand);
		}

		if (this.ship != null && this.waterman.getVehicle() == this.ship && this.ship.isHelmsman(this.waterman)) {
			this.beginCruise();
		} else {
			this.phase = Phase.APPROACHING;
			this.moveToShip();
		}
	}

	@Override
	public void stop() {
		if (this.ship != null && this.ship.isHelmsman(this.waterman)) {
			this.ship.releaseHelm(this.waterman);
		}
		this.waterman.getNavigation().stop();
		this.waterman.setBoatTripCooldown(400 + this.waterman.getRandom().nextInt(401));
		this.active = false;
		this.ship = null;
		this.excursionTarget = null;
		this.portWater = null;
		this.routeWaypoints = List.of();
		this.returnFallback = List.of();
		this.outboundTrail.clear();
		this.releaseVoyageTicket();
		if (!this.waterman.isDisplayingAtollWealth()) {
			ItemStack held = this.waterman.getItemBySlot(EquipmentSlot.MAINHAND);
			if (held.is(Items.EMERALD) || held.is(Items.GOLD_BLOCK)) {
				this.waterman.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}
		this.atollTradeVoyage = false;
		this.atollWealthLoaded = false;
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		this.tripTicks++;
		if (this.ship == null) {
			return;
		}
		if (this.atollTradeVoyage || this.phase == Phase.RETURNING || this.phase == Phase.OUTBOUND) {
			this.refreshVoyageTicket();
		}

		switch (this.phase) {
			case APPROACHING -> this.tickApproaching();
			case OUTBOUND -> this.tickOutbound();
			case TRADING -> this.tickTrading();
			case RETURNING -> this.tickReturning();
			case WALKING_HOME -> this.tickWalkingHome();
		}
	}

	boolean isActive() {
		return this.active;
	}

	private void tickApproaching() {
		if (this.ship == null || this.ship.getHelmsman() != null && !this.ship.isHelmsman(this.waterman)) {
			this.active = false;
			return;
		}
		this.waterman.getLookControl().setLookAt(this.ship, 30.0F, 30.0F);
		if (this.waterman.distanceToSqr(this.ship) <= BOARD_DISTANCE_SQR) {
			if (this.ship.tryClaimHelm(this.waterman)) {
				this.beginCruise();
			} else {
				this.active = false;
			}
		} else if (this.tripTicks % 20 == 0 || this.waterman.getNavigation().isDone()) {
			this.moveToShip();
		}
	}

	private void beginCruise() {
		if (this.ship == null) {
			this.active = false;
			return;
		}
		this.waterman.getNavigation().stop();
		this.tripTicks = 0;
		this.portWater = this.ship.position();
		this.atollTradeVoyage = false;
		this.atollWealthLoaded = false;
		this.tradingTicks = 0;

		Vec3 atollTradeTarget = this.findAtollTradeTarget((ServerLevel)this.waterman.level());
		if (atollTradeTarget != null) {
			this.atollTradeVoyage = true;
			this.excursionTarget = atollTradeTarget;
			if (this.planRouteTo(atollTradeTarget)) {
				this.phase = Phase.OUTBOUND;
				this.refreshVoyageTicket();
				return;
			}
			this.atollTradeVoyage = false;
			this.excursionTarget = null;
			this.releaseVoyageTicket();
		}

		WaterRoutePlanner.Route excursionRoute = this.findExcursionRoute((ServerLevel)this.waterman.level(), this.ship.blockPosition());
		if (excursionRoute == null) {
			// There is no water route wide enough for this particular hull. Do not
			// blindly drive into a bank just because a distant water block exists.
			this.ship.setControl(0.0F, 0.0F);
			this.ship.releaseHelm(this.waterman);
			this.active = false;
			return;
		}
		this.excursionTarget = excursionRoute.destination();
		this.installRoute(excursionRoute);
		this.rememberReturnRoute(excursionRoute);
		this.phase = Phase.OUTBOUND;
	}

	private void tickOutbound() {
		if (!this.hasHelm()) {
			this.active = false;
			return;
		}
		if (this.excursionTarget == null) {
			this.beginReturnTrip();
			return;
		}
		if (horizontalDistanceSqr(this.ship.position(), this.excursionTarget) < 7.0D) {
			if (this.atollTradeVoyage) {
				this.beginAtollTrade();
			} else {
				this.beginReturnTrip();
			}
			return;
		}
		int outboundLimit = this.atollTradeVoyage ? MAX_ATOLL_OUTBOUND_TICKS : MAX_OUTBOUND_TICKS;
		if (this.tripTicks > outboundLimit
			|| this.blockedRouteTicks > MAX_OUTBOUND_BLOCKED_TICKS) {
			this.beginReturnTrip();
			return;
		}
		this.followRoute(this.excursionTarget, 0.82F);
	}

	private void tickReturning() {
		if (!this.hasHelm() || this.portWater == null) {
			this.active = false;
			return;
		}
		double portDistanceSqr = horizontalDistanceSqr(this.ship.position(), this.portWater);
		if (portDistanceSqr < 7.0D && this.ship.hasSafeDismountLocation(this.waterman)) {
			this.ship.setControl(0.0F, 0.0F);
			this.ship.setDeltaMovement(Vec3.ZERO);
			if (!this.ship.releaseHelm(this.waterman)) {
				// Never jump into open water. Stay aboard until the hull is both
				// motionless and alongside a solid bank or pier.
				return;
			}
			if (!this.tripCounted) {
				this.tripCounted = true;
				this.waterman.markBoatTripCompleted();
				if (this.atollTradeVoyage) {
					if (!this.atollWealthLoaded) {
						this.waterman.loadAtollTradeWealth(this.ship);
						this.atollWealthLoaded = true;
					}
					this.waterman.collectAtollWealthFrom(this.ship);
					this.waterman.markAtollTradeCompleted();
				}
			}
			this.releaseVoyageTicket();
			this.phase = Phase.WALKING_HOME;
			this.moveHome();
			return;
		}
		if (portDistanceSqr < 7.0D) {
			// The old broad arrival radius could stop a small hull several blocks
			// offshore. Continue a slow final approach until a real bank/pier is
			// beside the hull, then the branch above performs the full stop.
			this.steerToward(this.portWater, 0.20F);
			return;
		}
		this.followRoute(this.portWater, 0.72F);
	}

	private void beginAtollTrade() {
		if (!this.hasHelm()) {
			this.active = false;
			return;
		}
		this.phase = Phase.TRADING;
		this.tradingTicks = 0;
		this.ship.setControl(0.0F, 0.0F);
		this.waterman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD));
		this.waterman.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	private void tickTrading() {
		if (!this.hasHelm()) {
			this.active = false;
			return;
		}
		this.ship.setControl(0.0F, 0.0F);
		this.tradingTicks++;
		ServerLevel level = (ServerLevel)this.waterman.level();
		if (this.tradingTicks % 12 == 0) {
			level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				this.waterman.getX(),
				this.waterman.getY() + 1.1D,
				this.waterman.getZ(),
				5,
				0.45D,
				0.35D,
				0.45D,
				0.02D
			);
		}
		if (this.tradingTicks % 40 == 1) {
			level.playSound(
				null,
				this.waterman.blockPosition(),
				SoundEvents.VILLAGER_TRADE,
				SoundSource.NEUTRAL,
				1.0F,
				0.9F + this.waterman.getRandom().nextFloat() * 0.2F
			);
		}
		if (this.tradingTicks < ATOLL_TRADE_TICKS) {
			return;
		}

		if (!this.atollWealthLoaded) {
			this.waterman.loadAtollTradeWealth(this.ship);
			this.atollWealthLoaded = true;
		}
		this.waterman.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_BLOCK));
		this.waterman.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		level.playSound(
			null,
			this.waterman.blockPosition(),
			SoundEvents.PLAYER_LEVELUP,
			SoundSource.NEUTRAL,
			0.8F,
			1.1F
		);
		this.beginReturnTrip();
	}

	private void tickWalkingHome() {
		if (this.waterman.hasPendingHomeDeposit()
			|| this.portLand == null
			|| this.waterman.blockPosition().closerToCenterThan(Vec3.atCenterOf(this.portLand), 2.5D)) {
			this.active = false;
			return;
		}
		if (this.tripTicks % 30 == 0 || this.waterman.getNavigation().isDone()) {
			this.moveHome();
		}
	}

	private boolean hasHelm() {
		return this.ship != null
			&& this.waterman.getVehicle() == this.ship
			&& this.ship.isHelmsman(this.waterman);
	}

	private void moveToShip() {
		if (this.ship != null) {
			this.waterman.getNavigation().moveTo(this.ship, 1.18D);
		}
	}

	private void moveHome() {
		if (this.portLand != null) {
			this.waterman.getNavigation().moveTo(
				this.portLand.getX() + 0.5D,
				this.portLand.getY(),
				this.portLand.getZ() + 0.5D,
				1.12D
			);
		}
	}

	private void steerToward(Vec3 target, float cruiseThrust) {
		if (this.ship == null) {
			return;
		}
		double dx = target.x - this.ship.getX();
		double dz = target.z - this.ship.getZ();
		float desiredYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
		float yawError = Mth.wrapDegrees(desiredYaw - this.ship.getYRot());
		float rudder = Mth.clamp(yawError / 34.0F, -1.0F, 1.0F);
		float thrust = Math.abs(yawError) > 95.0F ? 0.22F : cruiseThrust;
		this.ship.setControl(thrust, rudder);
		this.waterman.getLookControl().setLookAt(target.x, target.y, target.z);
	}

	private void beginReturnTrip() {
		if (this.ship == null || this.portWater == null) {
			this.active = false;
			return;
		}
		this.phase = Phase.RETURNING;
		this.blockedRouteTicks = 0;
		if (this.planRouteTo(this.portWater)) {
			return;
		}
		List<Vec3> fallback = !this.returnFallback.isEmpty()
			? this.returnFallback
			: reversedTrail(this.outboundTrail);
		if (!fallback.isEmpty()) {
			List<Vec3> route = new ArrayList<>(fallback);
			route.add(this.portWater);
			this.routeWaypoints = List.copyOf(route);
			this.routeWaypointIndex = 0;
			this.routeCheckCooldown = 0;
		}
	}

	private boolean followRoute(Vec3 finalTarget, float cruiseThrust) {
		if (this.ship == null) {
			return false;
		}
		double waypointDistance = Math.max(2.25D, this.ship.getBbWidth() * 0.72D);
		double waypointDistanceSqr = waypointDistance * waypointDistance;
		while (this.routeWaypointIndex < this.routeWaypoints.size()
			&& horizontalDistanceSqr(this.ship.position(), this.routeWaypoints.get(this.routeWaypointIndex)) <= waypointDistanceSqr) {
			this.routeWaypointIndex++;
		}

		if (this.routeWaypointIndex >= this.routeWaypoints.size()) {
			if (horizontalDistanceSqr(this.ship.position(), finalTarget) <= waypointDistanceSqr) {
				this.blockedRouteTicks = 0;
				return true;
			}
			if (!this.planRouteTo(finalTarget)) {
				// Keep sailing toward the real destination. Spinning in place is how
				// watermen used to get stuck after leaving the pier.
				this.steerToward(finalTarget, cruiseThrust * 0.75F);
				this.blockedRouteTicks++;
				return false;
			}
		}
		if (this.routeWaypoints.isEmpty() || this.routeWaypointIndex >= this.routeWaypoints.size()) {
			this.steerToward(finalTarget, cruiseThrust);
			return true;
		}

		Vec3 waypoint = this.routeWaypoints.get(this.routeWaypointIndex);
		if (this.routeCheckCooldown > 0) {
			this.routeCheckCooldown--;
		}
		boolean stuckOnHull = this.ship.horizontalCollision && this.ship.getHorizontalSpeed() < 0.04D;
		boolean pathBlocked = false;
		if (!stuckOnHull && this.routeCheckCooldown <= 0) {
			this.routeCheckCooldown = ROUTE_CHECK_INTERVAL;
			pathBlocked = !WaterRoutePlanner.isSegmentNavigable(
				(ServerLevel)this.waterman.level(),
				this.ship,
				this.ship.position(),
				waypoint
			);
		}
		if (stuckOnHull || pathBlocked) {
			if (this.planRouteTo(finalTarget) && !this.routeWaypoints.isEmpty()
				&& this.routeWaypointIndex < this.routeWaypoints.size()) {
				waypoint = this.routeWaypoints.get(this.routeWaypointIndex);
			} else if (stuckOnHull) {
				this.backOffAndTurn(finalTarget);
				this.blockedRouteTicks++;
				return false;
			} else {
				this.steerToward(finalTarget, cruiseThrust * 0.75F);
				return true;
			}
		}

		this.blockedRouteTicks = 0;
		this.steerToward(waypoint, cruiseThrust);
		return true;
	}

	private void backOffAndTurn(Vec3 target) {
		if (this.ship == null) {
			return;
		}
		double dx = target.x - this.ship.getX();
		double dz = target.z - this.ship.getZ();
		float desiredYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
		float yawError = Mth.wrapDegrees(desiredYaw - this.ship.getYRot());
		float rudder = Mth.clamp(yawError / 28.0F, -1.0F, 1.0F);
		this.ship.setControl(-0.38F, rudder);
		this.waterman.getLookControl().setLookAt(target.x, target.y, target.z);
	}

	private boolean planRouteTo(Vec3 target) {
		if (this.ship == null) {
			return false;
		}
		WaterRoutePlanner.Route route = this.findLocalRouteToward(target);
		if (route == null || route.waypoints().isEmpty()) {
			return false;
		}
		this.installRoute(route);
		return true;
	}

	private WaterRoutePlanner.Route findLocalRouteToward(Vec3 finalTarget) {
		if (this.ship == null) {
			return null;
		}
		ServerLevel level = (ServerLevel)this.waterman.level();
		Vec3 start = this.ship.position();
		double dx = finalTarget.x - start.x;
		double dz = finalTarget.z - start.z;
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance <= LOCAL_ROUTE_LEG + 4.0D) {
			return WaterRoutePlanner.plan(level, this.ship, start, BlockPos.containing(finalTarget));
		}

		double baseAngle = Math.atan2(dz, dx);
		double[] angleOffsets = {0.0D, 22.5D, -22.5D, 45.0D, -45.0D, 67.5D, -67.5D, 90.0D, -90.0D};
		double[] legLengths = {LOCAL_ROUTE_LEG, 30.0D, 24.0D};
		double startDistanceSqr = horizontalDistanceSqr(start, finalTarget);
		for (double legLength : legLengths) {
			for (double angleOffset : angleOffsets) {
				double angle = baseAngle + Math.toRadians(angleOffset);
				BlockPos legTarget = BlockPos.containing(
					start.x + Math.cos(angle) * legLength,
					start.y,
					start.z + Math.sin(angle) * legLength
				);
				WaterRoutePlanner.Route route = WaterRoutePlanner.plan(level, this.ship, start, legTarget);
				if (route != null
					&& horizontalDistanceSqr(route.destination(), finalTarget) + 16.0D < startDistanceSqr) {
					return route;
				}
			}
		}
		return null;
	}

	private void installRoute(WaterRoutePlanner.Route route) {
		this.routeWaypoints = route.waypoints();
		this.routeWaypointIndex = 0;
		this.routeCheckCooldown = 0;
		this.blockedRouteTicks = 0;
		if (this.phase != Phase.RETURNING) {
			this.outboundTrail.addAll(route.waypoints());
		}
	}

	private static List<Vec3> reversedTrail(List<Vec3> trail) {
		if (trail.isEmpty()) {
			return List.of();
		}
		List<Vec3> reversed = new ArrayList<>(trail);
		Collections.reverse(reversed);
		return reversed;
	}

	private void rememberReturnRoute(WaterRoutePlanner.Route outboundRoute) {
		List<Vec3> reversed = new ArrayList<>(outboundRoute.waypoints());
		Collections.reverse(reversed);
		if (this.portWater != null) {
			reversed.add(this.portWater);
		}
		this.returnFallback = List.copyOf(reversed);
	}

	@Nullable
	private Vec3 findAtollTradeTarget(ServerLevel level) {
		BlockPos forcedTarget = this.waterman.consumeNextAtollTradeTargetForTesting();
		if (forcedTarget != null) {
			return Vec3.atCenterOf(forcedTarget);
		}
		if (this.waterman.getAtollTradeCooldown() > 0
			|| !AtollTradeCompat.isAvailable()
			|| this.waterman.getRandom().nextFloat() >= ATOLL_TRIP_CHANCE
			|| this.portWater == null) {
			return null;
		}

		BlockPos center = AtollTradeCompat.findNearestAtoll(level, this.waterman.blockPosition());
		if (center == null) {
			// A new world may not have a reachable atoll yet. Avoid repeating the
			// structure-set lookup on every short harbour cruise.
			this.waterman.setAtollTradeCooldown(1200 + this.waterman.getRandom().nextInt(1201));
			return null;
		}
		return AtollTradeCompat.initialBerth(center, this.portWater);
	}

	private void refreshVoyageTicket() {
		if (this.ship == null || !(this.waterman.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos shipPos = this.ship.blockPosition();
		ChunkPos currentChunk = new ChunkPos(shipPos.getX() >> 4, shipPos.getZ() >> 4);
		if (!currentChunk.equals(this.voyageTicketChunk)) {
			level.getChunkSource().addTicketWithRadius(
				PeterwolfsBoatsAndShipsMod.WATERMAN_VOYAGE_TICKET,
				currentChunk,
				VOYAGE_TICKET_RADIUS
			);
			if (this.voyageTicketChunk != null) {
				level.getChunkSource().removeTicketWithRadius(
					PeterwolfsBoatsAndShipsMod.WATERMAN_VOYAGE_TICKET,
					this.voyageTicketChunk,
					VOYAGE_TICKET_RADIUS
				);
			}
			this.voyageTicketChunk = currentChunk;
		} else if (this.tripTicks % 40 == 0) {
			// Re-adding the same ticket resets its short timeout.
			level.getChunkSource().addTicketWithRadius(
				PeterwolfsBoatsAndShipsMod.WATERMAN_VOYAGE_TICKET,
				currentChunk,
				VOYAGE_TICKET_RADIUS
			);
		}
	}

	private void releaseVoyageTicket() {
		if (this.voyageTicketChunk != null && this.waterman.level() instanceof ServerLevel level) {
			level.getChunkSource().removeTicketWithRadius(
				PeterwolfsBoatsAndShipsMod.WATERMAN_VOYAGE_TICKET,
				this.voyageTicketChunk,
				VOYAGE_TICKET_RADIUS
			);
		}
		this.voyageTicketChunk = null;
	}

	@Nullable
	private AbstractShipEntity findAvailableShip() {
		List<AbstractShipEntity> ships = this.waterman.level().getEntitiesOfClass(
			AbstractShipEntity.class,
			this.waterman.getBoundingBox().inflate(SEARCH_RANGE, 8.0D, SEARCH_RANGE),
			candidate -> candidate.isAlive() && !candidate.isVehicle() && candidate.getHelmsman() == null
		);
		AbstractShipEntity nearest = null;
		double nearestDistance = Double.MAX_VALUE;
		for (AbstractShipEntity candidate : ships) {
			double distance = this.waterman.distanceToSqr(candidate);
			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	private WaterRoutePlanner.Route findExcursionRoute(ServerLevel level, BlockPos origin) {
		BlockPos forcedTarget = this.waterman.consumeNextExcursionTargetForTesting();
		if (forcedTarget != null && this.ship != null) {
			return WaterRoutePlanner.plan(level, this.ship, this.ship.position(), forcedTarget);
		}
		for (int attempt = 0; attempt < 32; attempt++) {
			double angle = this.waterman.getRandom().nextDouble() * Math.PI * 2.0D;
			double distance = 13.0D + this.waterman.getRandom().nextDouble() * 8.0D;
			BlockPos candidate = BlockPos.containing(
				origin.getX() + Math.cos(angle) * distance,
				origin.getY(),
				origin.getZ() + Math.sin(angle) * distance
			);
			if (this.ship != null) {
				WaterRoutePlanner.Route route = WaterRoutePlanner.plan(level, this.ship, this.ship.position(), candidate);
				if (route != null && horizontalDistanceSqr(this.ship.position(), route.destination()) >= 100.0D) {
					return route;
				}
			}
		}
		return null;
	}

	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	private enum Phase {
		APPROACHING,
		OUTBOUND,
		TRADING,
		RETURNING,
		WALKING_HOME
	}
}
