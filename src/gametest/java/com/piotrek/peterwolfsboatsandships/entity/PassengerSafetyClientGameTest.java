package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Visible multi-rider and dock-only dismount acceptance test. */
@SuppressWarnings("UnstableApiUsage")
public final class PassengerSafetyClientGameTest implements FabricClientGameTest {
	private static final double SHIP_X = 0.5D;
	private static final double DOCK_Z = 2.5D;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1100, 760);
		int[] waterY = {0};
		int[] shipId = {-1};
		int[] captainId = {-1};
		int[] passengerId = {-1};

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.setUseConsistentSettings(true)
			.create()) {
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");

			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				waterY[0] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
				buildPassengerDock(level, waterY[0]);

				ExplorerSloopEntity ship = new ExplorerSloopEntity(PeterwolfsBoatsAndShipsMod.EXPLORER_SLOOP, level);
				ship.setPos(SHIP_X, waterY[0] + 0.72D, DOCK_Z);
				ship.setYRot(0.0F);
				level.addFreshEntity(ship);
				shipId[0] = ship.getId();

				Villager captain = createVillager(level, SHIP_X, waterY[0] + 1.0D, DOCK_Z);
				Villager passenger = createVillager(level, SHIP_X + 1.0D, waterY[0] + 1.0D, DOCK_Z);
				captainId[0] = captain.getId();
				passengerId[0] = passenger.getId();

				if (!ship.tryClaimHelm(captain)) {
					throw new AssertionError("first villager did not claim the helm");
				}
				if (!ship.canPassengerDismount(captain)) {
					throw new AssertionError("initial stopped sloop did not detect the adjacent pier");
				}
				player.setGameMode(GameType.SURVIVAL);
				player.teleportTo(SHIP_X + 1.5D, waterY[0] + 1.0D, DOCK_Z);
				ship.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
				if (player.getVehicle() != ship || ship.isHelmsman(player)) {
					throw new AssertionError("second right-click rider did not become a passenger");
				}
				if (!ship.tryBoardPassenger(passenger) || ship.getPassengers().size() != 3) {
					throw new AssertionError("multiple villagers and player did not fit in the sloop");
				}
				if (ship.getControllingPassenger() != captain) {
					throw new AssertionError("a later passenger stole control from the first rider");
				}

				// Put the fully occupied vessel in open water with real horizontal speed.
				ship.setPos(SHIP_X, waterY[0] + 0.72D, 12.5D);
				ship.setDeltaMovement(new Vec3(0.0D, 0.0D, 0.22D));
				ship.setControl(1.0F, 0.0F);
				// Exercise the exact player and villager dismount calls while the
				// authoritative server speed is non-zero and no shore is nearby.
				player.stopRiding();
				passenger.stopRiding();
				if (player.getVehicle() != ship || passenger.getVehicle() != ship || ship.getControllingPassenger() != captain) {
					throw new AssertionError("a rider escaped from a moving ship");
				}
			});

			context.waitTicks(12);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ExplorerSloopEntity ship = (ExplorerSloopEntity)((ServerLevel)player.level()).getEntity(shipId[0]);
				if (ship == null || ship.getPassengers().size() != 3 || player.getVehicle() != ship) {
					throw new AssertionError("server did not retain all three moving-ship riders");
				}
			});

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(8);
			context.takeScreenshot("three-riders-kept-aboard-while-moving");

			// Stop at the pier. The same dismount requests must now succeed onto solid blocks.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				ExplorerSloopEntity ship = (ExplorerSloopEntity)level.getEntity(shipId[0]);
				Villager passenger = (Villager)level.getEntity(passengerId[0]);
				Villager captain = (Villager)level.getEntity(captainId[0]);
				if (ship == null || passenger == null || captain == null) {
					throw new AssertionError("passenger test entities disappeared before docking");
				}
				ship.setControl(0.0F, 0.0F);
				ship.setDeltaMovement(Vec3.ZERO);
				ship.setPos(SHIP_X, waterY[0] + 0.72D, DOCK_Z);
				player.stopRiding();
				passenger.stopRiding();
				if (player.isPassenger() || passenger.isPassenger()) {
					throw new AssertionError("passengers could not leave a stopped ship at the pier");
				}
				if (!ship.releaseHelm(captain) || captain.isPassenger()) {
					throw new AssertionError("captain could not leave after docking");
				}

			});

			// Vanilla applies a 60-tick boarding cooldown after every dismount.
			context.waitTicks(65);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				ExplorerSloopEntity ship = (ExplorerSloopEntity)level.getEntity(shipId[0]);
				Villager passenger = (Villager)level.getEntity(passengerId[0]);
				Villager captain = (Villager)level.getEntity(captainId[0]);
				if (ship == null || passenger == null || captain == null) {
					throw new AssertionError("passenger test entities disappeared before reboarding");
				}
				// With an empty helm, the first player right-click takes control.
				player.teleportTo(SHIP_X + 1.5D, waterY[0] + 1.0D, DOCK_Z);
				ship.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
				if (!ship.isHelmsman(player) || ship.getControllingPassenger() != player) {
					throw new AssertionError("first right-click did not assign the player as captain");
				}
				if (!ship.tryBoardPassenger(captain) || !ship.tryBoardPassenger(passenger)
					|| ship.getPassengers().size() != 3 || ship.getControllingPassenger() != player) {
					throw new AssertionError("later villagers were not retained as non-controlling passengers");
				}
			});

			context.waitTicks(12);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ExplorerSloopEntity ship = (ExplorerSloopEntity)((ServerLevel)player.level()).getEntity(shipId[0]);
				if (ship == null || ship.getPassengers().size() != 3 || ship.getControllingPassenger() != player) {
					throw new AssertionError("server did not keep the first-click player as captain");
				}
			});
			context.takeScreenshot("first-player-captain-with-villager-passengers");
		}
	}

	private static Villager createVillager(ServerLevel level, double x, double y, double z) {
		Villager villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (villager == null) {
			throw new AssertionError("could not create test villager");
		}
		villager.setPos(x, y, z);
		level.addFreshEntity(villager);
		return villager;
	}

	private static void buildPassengerDock(ServerLevel level, int waterY) {
		for (int x = -12; x <= 12; x++) {
			for (int z = -8; z <= 28; z++) {
				level.setBlockAndUpdate(new BlockPos(x, waterY - 1, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, waterY, z), Blocks.WATER.defaultBlockState());
				for (int y = 1; y <= 5; y++) {
					level.setBlockAndUpdate(new BlockPos(x, waterY + y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = -5; x <= 5; x++) {
			for (int z = -6; z <= 0; z++) {
				level.setBlockAndUpdate(new BlockPos(x, waterY, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
	}
}
