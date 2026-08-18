package com.piotrek.peterwolfsboatsandships.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A fisherman villager whose daily routine is tied to a waterside port.
 *
 * <p>The vanilla villager brain continues to handle normal village life while
 * the two high-priority goals below take exclusive control for fishing and boat
 * trips. All ship control remains server-authoritative.
 */
public final class WatermanEntity extends Villager {
	private static final int PORT_HOME_RADIUS = 28;

	@Nullable
	private BlockPos portPos;
	private int boatTripCooldown = 80;
	private int fishingCooldown = 20;
	private int atollTradeCooldown = 200;
	private int completedBoatTrips;
	private int caughtFish;
	private int completedAtollTrades;
	private int lastAtollTradeWealth;
	private int lastHomeDepositCount;
	private int wealthDisplayTicks;
	private boolean pendingHomeDeposit;
	@Nullable
	private WatermanBoatTripGoal boatTripGoal;
	@Nullable
	private WatermanStoreWealthGoal storeWealthGoal;
	@Nullable
	private WatermanFishingGoal fishingGoal;
	@Nullable
	private BlockPos nextExcursionTargetForTesting;
	@Nullable
	private BlockPos nextAtollTradeTargetForTesting;

	public WatermanEntity(EntityType<? extends Villager> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.makeFisherman();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Villager.createAttributes();
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.storeWealthGoal = new WatermanStoreWealthGoal(this);
		this.boatTripGoal = new WatermanBoatTripGoal(this);
		this.fishingGoal = new WatermanFishingGoal(this);
		this.goalSelector.addGoal(1, this.storeWealthGoal);
		this.goalSelector.addGoal(2, this.boatTripGoal);
		this.goalSelector.addGoal(3, this.fishingGoal);
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// The villager Brain can issue its own walk targets. Pause it only while a
		// waterman routine owns movement, then resume normal village behaviour.
		if (!this.isWatermanRoutineActive()) {
			super.customServerAiStep(level);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.level().isClientSide()) {
			if (this.boatTripCooldown > 0) {
				this.boatTripCooldown--;
			}
			if (this.fishingCooldown > 0) {
				this.fishingCooldown--;
			}
			if (this.atollTradeCooldown > 0) {
				this.atollTradeCooldown--;
			}
			if (this.wealthDisplayTicks > 0 && --this.wealthDisplayTicks == 0
				&& this.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.EMERALD_BLOCK)) {
				this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		}
	}

	@Override
	@Nullable
	public SpawnGroupData finalizeSpawn(
		ServerLevelAccessor level,
		DifficultyInstance difficulty,
		EntitySpawnReason spawnReason,
		@Nullable SpawnGroupData groupData
	) {
		SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnReason, groupData);
		this.makeFisherman();
		this.setVillagerDataFinalized(true);
		return result;
	}

	private void makeFisherman() {
		this.setVillagerData(this.getVillagerData().withProfession(
			BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FISHERMAN)
		));
	}

	@Override
	protected Component getTypeName() {
		return Component.translatable("entity.peterwolfs_boats_and_ships.waterman");
	}

	@Override
	public float getWalkTargetValue(BlockPos pos, LevelReader level) {
		// Vanilla idle walking samples this score; shore positions therefore win
		// over inland positions even when neither custom routine is running.
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				for (int y = -1; y <= 1; y++) {
					if (level.getFluidState(pos.offset(x, y, z)).is(FluidTags.WATER)) {
						return 10.0F - (Math.abs(x) + Math.abs(z)) * 0.35F;
					}
				}
			}
		}
		return -2.0F;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.portPos != null) {
			output.store("WatermanPort", BlockPos.CODEC, this.portPos);
		}
		output.putInt("WatermanBoatCooldown", this.boatTripCooldown);
		output.putInt("WatermanFishingCooldown", this.fishingCooldown);
		output.putInt("WatermanAtollTradeCooldown", this.atollTradeCooldown);
		output.putInt("WatermanCompletedTrips", this.completedBoatTrips);
		output.putInt("WatermanCaughtFish", this.caughtFish);
		output.putInt("WatermanCompletedAtollTrades", this.completedAtollTrades);
		output.putInt("WatermanLastAtollTradeWealth", this.lastAtollTradeWealth);
		output.putBoolean("WatermanPendingHomeDeposit", this.pendingHomeDeposit);
		output.putInt("WatermanLastHomeDepositCount", this.lastHomeDepositCount);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		Optional<BlockPos> savedPort = input.read("WatermanPort", BlockPos.CODEC);
		savedPort.ifPresent(this::setPortPos);
		this.boatTripCooldown = input.getIntOr("WatermanBoatCooldown", 80);
		this.fishingCooldown = input.getIntOr("WatermanFishingCooldown", 20);
		this.atollTradeCooldown = input.getIntOr("WatermanAtollTradeCooldown", 200);
		this.completedBoatTrips = input.getIntOr("WatermanCompletedTrips", 0);
		this.caughtFish = input.getIntOr("WatermanCaughtFish", 0);
		this.completedAtollTrades = input.getIntOr("WatermanCompletedAtollTrades", 0);
		this.lastAtollTradeWealth = input.getIntOr("WatermanLastAtollTradeWealth", 0);
		this.pendingHomeDeposit = input.getBooleanOr("WatermanPendingHomeDeposit", false);
		this.lastHomeDepositCount = input.getIntOr("WatermanLastHomeDepositCount", 0);
		this.makeFisherman();
	}

	boolean isWatermanRoutineActive() {
		return this.storeWealthGoal != null && this.storeWealthGoal.isActive()
			|| this.boatTripGoal != null && this.boatTripGoal.isActive()
			|| this.fishingGoal != null && this.fishingGoal.isActive();
	}

	@Nullable
	BlockPos getPortPos() {
		return this.portPos;
	}

	void setPortPos(BlockPos portPos) {
		this.portPos = portPos.immutable();
		this.setHomeTo(this.portPos, PORT_HOME_RADIUS);
	}

	int getBoatTripCooldown() {
		return this.boatTripCooldown;
	}

	void setBoatTripCooldown(int ticks) {
		this.boatTripCooldown = Math.max(0, ticks);
	}

	int getFishingCooldown() {
		return this.fishingCooldown;
	}

	void setFishingCooldown(int ticks) {
		this.fishingCooldown = Math.max(0, ticks);
	}

	void markBoatTripCompleted() {
		this.completedBoatTrips++;
	}

	void markFishCaught() {
		this.caughtFish++;
	}

	int getAtollTradeCooldown() {
		return this.atollTradeCooldown;
	}

	void setAtollTradeCooldown(int ticks) {
		this.atollTradeCooldown = Math.max(0, ticks);
	}

	/** Loads valuable, tangible goods into ship cargo first and personal inventory second. */
	void loadAtollTradeWealth(AbstractShipEntity ship) {
		List<ItemStack> wealth = new ArrayList<>();
		wealth.add(new ItemStack(Items.COPPER_INGOT, 32 + this.getRandom().nextInt(33)));
		wealth.add(new ItemStack(Items.IRON_BLOCK, 5 + this.getRandom().nextInt(7)));
		wealth.add(new ItemStack(Items.GOLD_BLOCK, 3 + this.getRandom().nextInt(5)));
		wealth.add(new ItemStack(Items.EMERALD_BLOCK, 4 + this.getRandom().nextInt(7)));
		wealth.add(new ItemStack(Items.DIAMOND, 8 + this.getRandom().nextInt(9)));
		wealth.add(new ItemStack(Items.NETHERITE_SCRAP, 1 + this.getRandom().nextInt(2)));

		Identifier seedId = Identifier.fromNamespaceAndPath(AtollTradeCompat.ATOLL_MOD_ID, "atoll_seed");
		if (BuiltInRegistries.ITEM.containsKey(seedId)) {
			Item seed = BuiltInRegistries.ITEM.getValue(seedId);
			wealth.add(new ItemStack(seed, 1 + this.getRandom().nextInt(3)));
		}

		int itemCount = 0;
		for (ItemStack stack : wealth) {
			itemCount += stack.getCount();
			ItemStack remainder = ship.addTradeCargo(stack);
			if (!remainder.isEmpty()) {
				remainder = this.getInventory().addItem(remainder);
			}
			if (!remainder.isEmpty() && this.level() instanceof ServerLevel level) {
				this.spawnAtLocation(level, remainder);
			}
		}
		this.lastAtollTradeWealth = itemCount;
	}

	void markAtollTradeCompleted() {
		this.completedAtollTrades++;
		this.atollTradeCooldown = 6000 + this.getRandom().nextInt(6001);
		this.fishingCooldown = Math.max(this.fishingCooldown, 600);
		this.wealthDisplayTicks = 200;
		this.pendingHomeDeposit = true;
		this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.EMERALD_BLOCK));
		this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
	}

	boolean isDisplayingAtollWealth() {
		return this.wealthDisplayTicks > 0;
	}

	boolean hasPendingHomeDeposit() {
		return this.pendingHomeDeposit;
	}

	void collectAtollWealthFrom(AbstractShipEntity ship) {
		for (ItemStack stack : ship.takeMatchingCargo(WatermanEntity::isAtollTreasure)) {
			ItemStack remainder = this.getInventory().addItem(stack);
			if (!remainder.isEmpty() && this.level() instanceof ServerLevel level) {
				this.spawnAtLocation(level, remainder);
			}
		}
		this.pendingHomeDeposit = true;
	}

	int depositAtollWealth(Container chest) {
		int deposited = 0;
		ItemStack held = this.getItemBySlot(EquipmentSlot.MAINHAND);
		if (isAtollTreasure(held)) {
			ItemStack remainder = addToContainer(chest, held.copy());
			deposited += held.getCount() - remainder.getCount();
			this.setItemSlot(EquipmentSlot.MAINHAND, remainder);
		}
		for (int slot = 0; slot < this.getInventory().getContainerSize(); slot++) {
			ItemStack stack = this.getInventory().getItem(slot);
			if (!isAtollTreasure(stack)) {
				continue;
			}
			ItemStack remainder = addToContainer(chest, stack.copy());
			deposited += stack.getCount() - remainder.getCount();
			this.getInventory().setItem(slot, remainder);
		}
		this.lastHomeDepositCount = deposited;
		if (this.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
			|| this.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.EMERALD_BLOCK)) {
			this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		this.wealthDisplayTicks = 0;
		return deposited;
	}

	void clearPendingHomeDeposit() {
		this.pendingHomeDeposit = false;
	}

	@Nullable
	BlockPos findHomeBed(ServerLevel level, int radius) {
		Optional<GlobalPos> home = this.getBrain().getMemory(MemoryModuleType.HOME);
		if (home.isPresent() && home.get().dimension() == level.dimension()) {
			BlockPos claimed = home.get().pos();
			if (level.getBlockState(claimed).getBlock() instanceof BedBlock) {
				return claimed.immutable();
			}
		}
		BlockPos center = this.portPos != null ? this.portPos : this.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int y = -4; y <= 4; y++) {
					BlockPos candidate = center.offset(x, y, z);
					BlockState state = level.getBlockState(candidate);
					if (!(state.getBlock() instanceof BedBlock)) {
						continue;
					}
					double distance = candidate.distSqr(this.blockPosition());
					if (distance < bestDistance) {
						bestDistance = distance;
						best = candidate.immutable();
					}
				}
			}
		}
		return best;
	}

	static boolean isAtollTreasure(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.COPPER_INGOT)
			|| stack.is(Items.IRON_BLOCK)
			|| stack.is(Items.GOLD_BLOCK)
			|| stack.is(Items.EMERALD_BLOCK)
			|| stack.is(Items.EMERALD)
			|| stack.is(Items.DIAMOND)
			|| stack.is(Items.NETHERITE_SCRAP)) {
			return true;
		}
		Identifier seedId = Identifier.fromNamespaceAndPath(AtollTradeCompat.ATOLL_MOD_ID, "atoll_seed");
		return BuiltInRegistries.ITEM.containsKey(seedId) && stack.is(BuiltInRegistries.ITEM.getValue(seedId));
	}

	private static ItemStack addToContainer(Container container, ItemStack stack) {
		if (stack.isEmpty()) {
			return stack;
		}
		ItemStack remaining = stack.copy();
		for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
			ItemStack existing = container.getItem(slot);
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
				continue;
			}
			int max = Math.min(container.getMaxStackSize(existing), existing.getMaxStackSize());
			int space = max - existing.getCount();
			if (space <= 0) {
				continue;
			}
			int moved = Math.min(space, remaining.getCount());
			existing.grow(moved);
			remaining.shrink(moved);
			container.setChanged();
		}
		for (int slot = 0; slot < container.getContainerSize() && !remaining.isEmpty(); slot++) {
			if (!container.getItem(slot).isEmpty()) {
				continue;
			}
			int max = Math.min(container.getMaxStackSize(remaining), remaining.getMaxStackSize());
			container.setItem(slot, remaining.split(Math.min(max, remaining.getCount())));
			container.setChanged();
		}
		return remaining;
	}

	/** Selects one deterministic cruise destination for the client game test. */
	void setNextExcursionTargetForTesting(BlockPos target) {
		this.nextExcursionTargetForTesting = target.immutable();
	}

	@Nullable
	BlockPos consumeNextExcursionTargetForTesting() {
		BlockPos target = this.nextExcursionTargetForTesting;
		this.nextExcursionTargetForTesting = null;
		return target;
	}

	/** Selects one deterministic atoll berth for the client game test. */
	void setNextAtollTradeTargetForTesting(BlockPos target) {
		this.nextAtollTradeTargetForTesting = target.immutable();
	}

	@Nullable
	BlockPos consumeNextAtollTradeTargetForTesting() {
		BlockPos target = this.nextAtollTradeTargetForTesting;
		this.nextAtollTradeTargetForTesting = null;
		return target;
	}

	/** Runtime diagnostics used by the deterministic client game test. */
	public int getCompletedBoatTrips() {
		return this.completedBoatTrips;
	}

	/** Runtime diagnostics used by the deterministic client game test. */
	public int getCaughtFish() {
		return this.caughtFish;
	}

	/** Runtime diagnostics used by the deterministic client game test. */
	public int getCompletedAtollTrades() {
		return this.completedAtollTrades;
	}

	/** Number of valuable items brought back by the latest atoll voyage. */
	public int getLastAtollTradeWealth() {
		return this.lastAtollTradeWealth;
	}

	/** Number of treasure items placed in the bed-side chest after the last return. */
	public int getLastHomeDepositCount() {
		return this.lastHomeDepositCount;
	}
}
