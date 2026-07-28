package com.piotrek.peterwolfsboatsandships.entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared hull physics. This deliberately never grants client movement authority:
 * client packets set captain intent and the dedicated server moves the vessel.
 *
 * <p>Boarding is automatic when a player steps onto the deck. Right-click claims
 * the helm for steering; sneak + right-click opens cargo on ships that have it.
 */
public abstract class AbstractShipEntity extends Entity {
	private static final EntityDataAccessor<Float> THRUST = SynchedEntityData.defineId(AbstractShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> RUDDER = SynchedEntityData.defineId(AbstractShipEntity.class, EntityDataSerializers.FLOAT);
	/** Entity id of the player who claimed the helm, or -1 when nobody is steering. */
	private static final EntityDataAccessor<Integer> HELMSMAN_ID = SynchedEntityData.defineId(AbstractShipEntity.class, EntityDataSerializers.INT);

	/** Double-tap W may send thrust above 1.0; keep room for that. */
	public static final float MAX_THRUST = 1.75F;
	/** Double-tap A/D may send |rudder| above 1.0 for a sharp turn. */
	public static final float MAX_RUDDER = 2.5F;
	/** Heel (degrees) opposite the turn while sharp-turn boost is active. */
	private static final float SHARP_TURN_HEEL_DEG = 30.0F;
	/** How long (ticks) a client packet keeps control authority vs vanilla fallback. */
	private static final int INPUT_FRESH_TICKS = 10;
	/** Speed multiplier while double-tap W boost is held. */
	private static final double BOOST_SPEED_MULT = 1.5D;

	private float visualHeel;
	private float visualHeelO;
	private float sailPhase;
	private float sailPhaseO;
	private float oarPhase;
	private float oarPhaseO;
	private int inputFreshTicks;
	/** Stable helmsman identity across entity-id reuse / reloads. */
	@Nullable
	private UUID helmsmanUuid;
	private final SimpleContainer cargo;
	// Entity itself has no interpolation handler in 26.2. A server-authoritative
	// vehicle must provide one or client position packets are not blended/applied
	// like those of vanilla boats.
	private final InterpolationHandler interpolation = new InterpolationHandler(this, 3);

	protected AbstractShipEntity(EntityType<? extends AbstractShipEntity> type, Level level, int cargoSlots) {
		super(type, level);
		this.blocksBuilding = true;
		this.cargo = new SimpleContainer(cargoSlots);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(THRUST, 0.0F);
		builder.define(RUDDER, 0.0F);
		builder.define(HELMSMAN_ID, -1);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setControl(input.getFloatOr("Thrust", 0.0F), input.getFloatOr("Rudder", 0.0F));
		ContainerHelper.loadAllItems(input, this.cargo.getItems());
		Optional<UUID> savedHelmsman = input.read("Helmsman", UUIDUtil.CODEC);
		this.helmsmanUuid = savedHelmsman.orElse(null);
		this.entityData.set(HELMSMAN_ID, -1);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("Thrust", this.getThrust());
		output.putFloat("Rudder", this.getRudder());
		ContainerHelper.saveAllItems(output, this.cargo.getItems());
		if (this.helmsmanUuid != null) {
			output.store("Helmsman", UUIDUtil.CODEC, this.helmsmanUuid);
		}
	}

	/** Client packet path: marks input as fresh so boost magnitudes are not overwritten. */
	public final void setControl(float thrust, float rudder) {
		this.applyControl(thrust, rudder);
		this.inputFreshTicks = INPUT_FRESH_TICKS;
	}

	private void applyControl(float thrust, float rudder) {
		this.entityData.set(THRUST, Mth.clamp(Float.isFinite(thrust) ? thrust : 0.0F, -0.55F, MAX_THRUST));
		this.entityData.set(RUDDER, Mth.clamp(Float.isFinite(rudder) ? rudder : 0.0F, -MAX_RUDDER, MAX_RUDDER));
	}

	private void clearControl() {
		this.entityData.set(THRUST, 0.0F);
		this.entityData.set(RUDDER, 0.0F);
		this.inputFreshTicks = 0;
	}

	public final float getThrust() { return this.entityData.get(THRUST); }
	public final float getRudder() { return this.entityData.get(RUDDER); }
	public final float getVisualHeel(float partialTick) { return Mth.lerp(partialTick, this.visualHeelO, this.visualHeel); }
	public final float getSailPhase(float partialTick) { return Mth.lerp(partialTick, this.sailPhaseO, this.sailPhase); }
	public final float getOarPhase(float partialTick) { return Mth.lerp(partialTick, this.oarPhaseO, this.oarPhase); }
	public final double getHorizontalSpeed() { Vec3 velocity = this.getDeltaMovement(); return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z); }

	/** True when this entity is the player who claimed the helm (right-click). */
	public final boolean isHelmsman(Entity entity) {
		return entity != null && entity.getId() == this.entityData.get(HELMSMAN_ID);
	}

	@Nullable
	public final Entity getHelmsman() {
		int id = this.entityData.get(HELMSMAN_ID);
		if (id < 0) return null;
		return this.level().getEntity(id);
	}

	private void setHelmsman(@Nullable Player player) {
		if (player == null) {
			this.helmsmanUuid = null;
			this.entityData.set(HELMSMAN_ID, -1);
			this.clearControl();
			return;
		}
		this.helmsmanUuid = player.getUUID();
		this.entityData.set(HELMSMAN_ID, player.getId());
	}

	/** Keep synched helmsman entity id in sync with the saved UUID among passengers. */
	private void refreshHelmsmanSync() {
		if (this.helmsmanUuid == null) {
			if (this.entityData.get(HELMSMAN_ID) != -1) {
				this.entityData.set(HELMSMAN_ID, -1);
			}
			return;
		}
		for (Entity passenger : this.getPassengers()) {
			if (this.helmsmanUuid.equals(passenger.getUUID())) {
				if (this.entityData.get(HELMSMAN_ID) != passenger.getId()) {
					this.entityData.set(HELMSMAN_ID, passenger.getId());
				}
				return;
			}
		}
		// Helmsman left the vessel.
		this.helmsmanUuid = null;
		this.entityData.set(HELMSMAN_ID, -1);
		this.clearControl();
	}

	protected abstract int seatCount();
	protected abstract double maxSpeed();
	protected abstract double acceleration();
	protected abstract float turnRate();
	protected abstract boolean hasSails();
	protected abstract Item dropItem();
	protected abstract Component cargoTitle();

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			this.interpolation.interpolate();
			this.tickClientVisuals();
			return;
		}
		this.refreshHelmsmanSync();
		this.tryAutoBoard();
		this.tickServerPhysics();
	}

	/**
	 * Step onto the deck to board — no right-click required.
	 * Sneaking skips auto-board so players can swim past or work near the hull.
	 */
	private void tryAutoBoard() {
		if (this.getPassengers().size() >= this.seatCount()) return;
		AABB deck = this.getBoundingBox().inflate(0.2D, 0.4D, 0.2D);
		List<Player> candidates = this.level().getEntitiesOfClass(Player.class, deck, this::canAutoBoard);
		for (Player player : candidates) {
			if (this.getPassengers().size() >= this.seatCount()) break;
			player.startRiding(this);
		}
	}

	private boolean canAutoBoard(Player player) {
		if (player.isSpectator() || player.isPassenger() || player.isShiftKeyDown()) return false;
		if (!this.canAddPassenger(player)) return false;
		double dy = player.getY() - this.getY();
		// Feet on/above the hull — ignore players deep under the keel.
		if (dy < -0.2D || dy > Math.max(2.2D, this.getBbHeight() + 0.5D)) return false;
		// Do not yank fully submerged divers from under the ship.
		if (player.isUnderWater() && dy < 0.15D) return false;
		return true;
	}

	private void tickServerPhysics() {
		Entity helmsman = this.getHelmsman();
		if (!(helmsman instanceof ServerPlayer player)) {
			this.clearControl();
		} else if (this.inputFreshTicks > 0) {
			// Prefer captain packets so double-tap boost magnitudes survive.
			this.inputFreshTicks--;
		} else {
			// Fallback when client packets are missing (e.g. brief desync).
			// Player.zza/xxa are not updated while an entity is being ridden.
			// Do not mark input fresh — re-sample vanilla each tick until packets resume.
			var input = player.getLastClientInput();
			float thrust = input.forward() ? 1.0F : input.backward() ? -0.55F : 0.0F;
			float rudder = input.left() ? -1.0F : input.right() ? 1.0F : 0.0F;
			this.applyControl(thrust, rudder);
		}
		double surface = this.findWaterSurface();
		if (Double.isNaN(surface)) {
			// A ship can never climb dry land. It simply loses way and settles.
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.55D, 0.0D, 0.55D));
			return;
		}

		this.setPos(this.getX(), surface - 0.28D, this.getZ());
		Vec3 heading = this.heading();
		double speed = this.getDeltaMovement().dot(heading);
		float thrust = this.getThrust();
		boolean speedBoost = thrust > 1.01F;
		// Boost multiplies acceleration; reverse stays at the clamped negative value.
		speed += thrust * this.acceleration();
		float rudderAbs = Math.abs(this.getRudder());
		// Cap drag contribution so sharp-turn rudder does not stall the hull.
		speed *= 0.985D - Math.min(0.05D, Math.min(rudderAbs, 1.0F) * 0.015D);
		double maxSpd = this.maxSpeed() * (speedBoost ? BOOST_SPEED_MULT : 1.0D);
		speed = Mth.clamp(speed, -this.maxSpeed() * 0.36D, maxSpd);
		if (Math.abs(speed) < 0.003D) speed = 0.0D;
		// |rudder| > 1 (double-tap A/D) scales turn rate up to MAX_RUDDER for a much sharper turn.
		float steering = this.getRudder() * this.turnRate() * (float) Mth.clamp(Math.abs(speed) / this.maxSpeed(), 0.18D, 1.0D);
		this.setShipYaw(this.getYRot() + steering);
		heading = this.heading();
		Vec3 movement = heading.scale(speed);
		this.setDeltaMovement(movement);
		this.move(MoverType.SELF, movement);
		if (this.horizontalCollision) {
			this.setDeltaMovement(movement.scale(0.22D));
		}
	}

	private double findWaterSurface() {
		BlockPos center = this.blockPosition();
		double highest = Double.NaN;
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -2; y <= 3; y++) {
			BlockPos pos = center.offset(x, y, z);
			FluidState fluid = this.level().getFluidState(pos);
			if (fluid.is(FluidTags.WATER)) {
				double candidate = pos.getY() + fluid.getHeight(this.level(), pos);
				if (Double.isNaN(highest) || candidate > highest) highest = candidate;
			}
		}
		return highest;
	}

	private Vec3 heading() {
		double radians = Math.toRadians(this.getYRot());
		return new Vec3(-Math.sin(radians), 0.0D, Math.cos(radians));
	}

	private void setShipYaw(float yaw) {
		float wrapped = Mth.wrapDegrees(yaw);
		this.setYRot(wrapped);
		this.setYHeadRot(wrapped);
		this.setYBodyRot(wrapped);
	}

	private void tickClientVisuals() {
		this.visualHeelO = this.visualHeel;
		this.sailPhaseO = this.sailPhase;
		this.oarPhaseO = this.oarPhase;
		float speed = (float) this.getHorizontalSpeed();
		float rudder = this.getRudder();
		float targetHeel;
		if (Math.abs(rudder) > 1.01F) {
			// Double-tap A/D: lean up to 30° opposite the turn (outward heel).
			float lean = Mth.clamp(speed * 40.0F, 10.0F, SHARP_TURN_HEEL_DEG);
			targetHeel = Math.signum(rudder) * lean;
		} else {
			// Positive rudder (D/right) banks left visually, and vice versa.
			targetHeel = rudder * Mth.clamp(speed * 22.0F, 0.0F, this.hasSails() ? 9.0F : 6.0F);
		}
		// Snap into sharp heel a bit faster so the double-tap feel is immediate.
		float heelLerp = Math.abs(rudder) > 1.01F ? 0.35F : 0.22F;
		this.visualHeel = Mth.lerp(heelLerp, this.visualHeel, targetHeel);
		this.sailPhase += this.hasSails() ? 0.055F + speed * 0.42F : 0.0F;
		if (!this.hasSails() && speed > 0.015F) {
			this.oarPhase += 0.12F + speed * 1.4F;
		}
	}

	/**
	 * Ships are always server-simulated. Returning false here makes the
	 * dedicated/integrated server treat this entity as locally authoritative.
	 */
	@Override
	public boolean isClientAuthoritative() {
		return false;
	}

	/**
	 * Critical for riding players: Entity.isLocalInstanceAuthoritative() on the
	 * client uses this method (not isClientAuthoritative). If it returns true
	 * because a LocalPlayer is the controlling passenger, the client:
	 * <ul>
	 *   <li>ignores server position sync packets, and</li>
	 *   <li>never runs ship physics itself (see {@link #tick()}),</li>
	 * </ul>
	 * so the vessel freezes visually while the captain presses WASD.
	 */
	@Override
	protected boolean isLocalClientAuthoritative() {
		return false;
	}

	@Override
	public InterpolationHandler getInterpolation() {
		return this.interpolation;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < this.seatCount();
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (this.helmsmanUuid != null && this.helmsmanUuid.equals(passenger.getUUID())) {
			this.setHelmsman(null);
		}
	}

	@Override
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		int index = this.seatIndexFor(passenger);
		if (index < 0) return;
		Vec3 seat = this.seatOffset(index);
		double radians = Math.toRadians(this.getYRot());
		double x = this.getX() + Math.cos(radians) * seat.x - Math.sin(radians) * seat.z;
		double z = this.getZ() + Math.sin(radians) * seat.x + Math.cos(radians) * seat.z;
		moveFunction.accept(passenger, x, this.getY() + seat.y, z);
	}

	/**
	 * Helmsman always sits at seat 0 (stern helm). Other passengers fill seats 1+.
	 * Without a helmsman, boarding order is used as before.
	 */
	private int seatIndexFor(Entity passenger) {
		List<Entity> passengers = this.getPassengers();
		if (!passengers.contains(passenger)) return -1;
		if (this.isHelmsman(passenger)) return 0;
		if (this.getHelmsman() == null) {
			return passengers.indexOf(passenger);
		}
		int crew = 0;
		for (Entity p : passengers) {
			if (this.isHelmsman(p)) continue;
			if (p == passenger) return crew + 1;
			crew++;
		}
		return passengers.indexOf(passenger);
	}

	protected Vec3 seatOffset(int index) {
		if (index == 0) {
			return new Vec3(0.0D, 0.38D, -0.45D); // Captain sits closer to the stern (rufa)
		}
		int row = (index + 1) / 2;
		double side = (index & 1) == 1 ? -0.42D : 0.42D;
		return new Vec3(side, 0.38D, -0.45D + row * 0.75D);
	}

	@Override
	@Nullable
	public LivingEntity getControllingPassenger() {
		Entity helmsman = this.getHelmsman();
		return helmsman instanceof LivingEntity living ? living : null;
	}

	/**
	 * Right-click claims the helm (steering). Sneak + right-click opens cargo.
	 * Boarding itself is automatic when stepping onto the deck.
	 */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() && this.cargo.getContainerSize() > 0) {
			if (!this.level().isClientSide()) {
				player.openMenu(new SimpleMenuProvider((id, inventory, ignored) -> this.cargo.getContainerSize() == 9
					? new ChestMenu(MenuType.GENERIC_9x1, id, inventory, this.cargo, 1)
					: ChestMenu.threeRows(id, inventory, this.cargo), this.cargoTitle()));
			}
			return InteractionResult.SUCCESS;
		}
		if (!this.level().isClientSide()) {
			return this.claimHelm(player) ? InteractionResult.CONSUME : InteractionResult.PASS;
		}
		return InteractionResult.SUCCESS;
	}

	/** Right-click: board if needed and take the helm for WASD steering. */
	private boolean claimHelm(Player player) {
		if (player.isSpectator()) return false;
		if (player.getVehicle() != this) {
			if (!this.canAddPassenger(player)) return false;
			if (!player.startRiding(this)) return false;
		}
		this.setHelmsman(player);
		return true;
	}

	@Override
	public boolean isPickable() { return true; }

	@Override
	public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (this.getFirstPassenger() instanceof Player || !(source.getEntity() instanceof Player player) || player.isSpectator()) return false;
		Containers.dropContents(level, this, this.cargo);
		if (!player.getAbilities().instabuild) this.spawnAtLocation(level, this.dropItem());
		this.discard();
		return true;
	}
}
