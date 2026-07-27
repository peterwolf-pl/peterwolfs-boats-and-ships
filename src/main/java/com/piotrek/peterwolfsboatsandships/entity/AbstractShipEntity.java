package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import java.util.List;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared hull physics. This deliberately never grants client movement authority:
 * client packets set captain intent and the dedicated server moves the vessel.
 */
public abstract class AbstractShipEntity extends Entity {
	private static final EntityDataAccessor<Float> THRUST = SynchedEntityData.defineId(AbstractShipEntity.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> RUDDER = SynchedEntityData.defineId(AbstractShipEntity.class, EntityDataSerializers.FLOAT);

	private float visualHeel;
	private float visualHeelO;
	private float sailPhase;
	private float sailPhaseO;
	private float oarPhase;
	private float oarPhaseO;
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
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.setControl(input.getFloatOr("Thrust", 0.0F), input.getFloatOr("Rudder", 0.0F));
		ContainerHelper.loadAllItems(input, this.cargo.getItems());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putFloat("Thrust", this.getThrust());
		output.putFloat("Rudder", this.getRudder());
		ContainerHelper.saveAllItems(output, this.cargo.getItems());
	}

	public final void setControl(float thrust, float rudder) {
		this.entityData.set(THRUST, Mth.clamp(Float.isFinite(thrust) ? thrust : 0.0F, -0.55F, 1.0F));
		this.entityData.set(RUDDER, Mth.clamp(Float.isFinite(rudder) ? rudder : 0.0F, -1.0F, 1.0F));
	}

	public final float getThrust() { return this.entityData.get(THRUST); }
	public final float getRudder() { return this.entityData.get(RUDDER); }
	public final float getVisualHeel(float partialTick) { return Mth.lerp(partialTick, this.visualHeelO, this.visualHeel); }
	public final float getSailPhase(float partialTick) { return Mth.lerp(partialTick, this.sailPhaseO, this.sailPhase); }
	public final float getOarPhase(float partialTick) { return Mth.lerp(partialTick, this.oarPhaseO, this.oarPhase); }
	public final double getHorizontalSpeed() { Vec3 velocity = this.getDeltaMovement(); return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z); }

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
		this.tickServerPhysics();
	}

	private void tickServerPhysics() {
		Entity captain = this.getFirstPassenger();
		if (captain instanceof ServerPlayer player) {
			// This is the actual server-side representation of the WASD packet.
			// Player.zza/xxa are not updated while an entity is being ridden.
			var input = player.getLastClientInput();
			float thrust = input.forward() ? 1.0F : input.backward() ? -0.55F : 0.0F;
			float rudder = input.left() ? -1.0F : input.right() ? 1.0F : 0.0F;
			this.setControl(thrust, rudder);
		} else {
			this.setControl(0.0F, 0.0F);
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
		double input = this.getThrust();
		speed += input * this.acceleration();
		speed *= 0.985D - Math.min(0.05D, Math.abs(this.getRudder()) * 0.015D);
		speed = Mth.clamp(speed, -this.maxSpeed() * 0.36D, this.maxSpeed());
		if (Math.abs(speed) < 0.003D) speed = 0.0D;
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
		float targetHeel = -this.getRudder() * Mth.clamp(speed * 22.0F, 0.0F, this.hasSails() ? 9.0F : 6.0F);
		this.visualHeel = Mth.lerp(0.22F, this.visualHeel, targetHeel);
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
	protected void positionRider(Entity passenger, MoveFunction moveFunction) {
		List<Entity> passengers = this.getPassengers();
		int index = passengers.indexOf(passenger);
		if (index < 0) return;
		Vec3 seat = this.seatOffset(index);
		double radians = Math.toRadians(this.getYRot());
		double x = this.getX() + Math.cos(radians) * seat.x - Math.sin(radians) * seat.z;
		double z = this.getZ() + Math.sin(radians) * seat.x + Math.cos(radians) * seat.z;
		moveFunction.accept(passenger, x, this.getY() + seat.y, z);
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
		return this.getFirstPassenger() instanceof LivingEntity living ? living : null;
	}

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
		if (!this.level().isClientSide()) return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
		return InteractionResult.SUCCESS;
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
