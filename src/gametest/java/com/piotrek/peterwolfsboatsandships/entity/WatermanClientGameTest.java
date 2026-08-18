package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

/** Full visible loop: spawn egg, safe cruise, fishing and a wealthy atoll trade. */
@SuppressWarnings("UnstableApiUsage")
public final class WatermanClientGameTest implements FabricClientGameTest {
	private static final double PORT_X = 0.5D;
	private static final double PORT_Z = 2.5D;
	private static final int ATOLL_Z = 44;
	private static final int HOME_CHEST_X = 1;
	private static final int HOME_CHEST_Z = -6;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.getInput().resizeWindow(1100, 760);
		int[] watermanId = {-1};
		int[] shipId = {-1};
		int[] waterY = {0};
		float[] caughtFishCameraYaw = {45.0F};

		try (TestSingleplayerContext singleplayer = context.worldBuilder()
			.setUseConsistentSettings(true)
			.create()) {
			singleplayer.getServer().runCommand("time set noon");
			singleplayer.getServer().runCommand("weather clear");

			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				if (!SpawnEggItem.spawnsEntity(
					new ItemStack(PeterwolfsBoatsAndShipsMod.WATERMAN_SPAWN_EGG),
					PeterwolfsBoatsAndShipsMod.WATERMAN
				)) {
					throw new AssertionError("waterman spawn egg is not bound to the waterman entity type");
				}
				waterY[0] = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
				buildPortScene(level, waterY[0]);

				RiverSkiffEntity ship = new RiverSkiffEntity(PeterwolfsBoatsAndShipsMod.RIVER_SKIFF, level);
				ship.setPos(PORT_X, waterY[0] + 0.72D, PORT_Z);
				ship.setYRot(0.0F);
				level.addFreshEntity(ship);
				shipId[0] = ship.getId();

				WatermanEntity waterman = new WatermanEntity(PeterwolfsBoatsAndShipsMod.WATERMAN, level);
				waterman.setPos(0.5D, waterY[0] + 1.0D, -3.5D);
				waterman.setPortPos(waterman.blockPosition());
				waterman.setBoatTripCooldown(0);
				waterman.setFishingCooldown(2000);
				waterman.setAtollTradeCooldown(24000);
				waterman.setNextExcursionTargetForTesting(new BlockPos(0, waterY[0], 26));
				level.addFreshEntity(waterman);
				watermanId[0] = waterman.getId();

				player.setGameMode(GameType.SPECTATOR);
				placeCamera(player, 9.0D, waterY[0] + 6.0D, -6.0D, 45.0F, 24.0F);
			});

			context.waitFor(client -> client.level != null
				&& client.level.getEntity(watermanId[0]) instanceof WatermanEntity
				&& client.level.getEntity(shipId[0]) instanceof RiverSkiffEntity, 300);
			singleplayer.getClientLevel().waitForChunksRender();
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.getInput().lookAt(45.0F, 24.0F);
			context.waitTicks(10);
			context.takeScreenshot("waterman-at-waterside-port");

			// Verify a visible open-water excursion before waiting for the return.
			context.waitFor(client -> client.level != null
				&& client.level.getEntity(watermanId[0]) instanceof WatermanEntity waterman
				&& client.level.getEntity(shipId[0]) instanceof RiverSkiffEntity ship
				&& waterman.getVehicle() == ship
				&& ship.getZ() > 18.0D, 1200);
			positionCameraBesideShip(singleplayer, shipId[0], 45.0F, 23.0F);
			context.getInput().lookAt(45.0F, 23.0F);
			context.waitTicks(8);
			context.takeScreenshot("waterman-cruising-open-water");

			// A completed excursion ends with the same ship back at its saved port.
			context.waitFor(client -> {
				if (client.level == null
					|| !(client.level.getEntity(watermanId[0]) instanceof WatermanEntity waterman)
					|| !(client.level.getEntity(shipId[0]) instanceof RiverSkiffEntity ship)) {
					return false;
				}
				return !waterman.isPassenger() && horizontalDistanceSqr(ship.getX(), ship.getZ(), PORT_X, PORT_Z) < 12.0D;
			}, 1800);
			positionCameraBesideShip(singleplayer, shipId[0], 45.0F, 23.0F);
			context.getInput().lookAt(45.0F, 23.0F);
			context.waitTicks(8);
			context.takeScreenshot("waterman-returned-to-port");

			// Keep the ship parked and force the next routine to be an actual catch.
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = (ServerLevel)server.getPlayerList().getPlayers().get(0).level();
				WatermanEntity waterman = (WatermanEntity)level.getEntity(watermanId[0]);
				if (waterman == null) {
					throw new AssertionError("waterman disappeared after returning to port");
				}
				waterman.setBoatTripCooldown(2400);
				waterman.setFishingCooldown(0);
			});

			context.waitFor(client -> {
				if (client.level == null || !(client.level.getEntity(watermanId[0]) instanceof WatermanEntity waterman)) {
					return false;
				}
				return !waterman.getMainHandItem().isEmpty() && !waterman.getMainHandItem().is(Items.FISHING_ROD);
			}, 900);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				WatermanEntity waterman = (WatermanEntity)((ServerLevel)player.level()).getEntity(watermanId[0]);
				if (waterman == null || waterman.getCompletedBoatTrips() < 1 || waterman.getCaughtFish() < 1) {
					throw new AssertionError("waterman did not complete both routines");
				}
				float watermanYaw = waterman.getYHeadRot();
				double radians = Math.toRadians(watermanYaw);
				double cameraX = waterman.getX() - Math.sin(radians) * 4.5D;
				double cameraZ = waterman.getZ() + Math.cos(radians) * 4.5D;
				caughtFishCameraYaw[0] = Mth.wrapDegrees(watermanYaw + 180.0F);
				placeCamera(player, cameraX, waterman.getY() + 2.3D, cameraZ, caughtFishCameraYaw[0], 10.0F);
			});
			context.getInput().lookAt(caughtFishCameraYaw[0], 10.0F);
			context.waitTicks(5);
			context.takeScreenshot("waterman-showing-caught-fish");

			// A second voyage uses a deterministic stand-in for an Atoll trading
			// outpost. The production destination discovery remains optional and is
			// driven by the Water World structure tag.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				WatermanEntity waterman = (WatermanEntity)level.getEntity(watermanId[0]);
				if (waterman == null) {
					throw new AssertionError("waterman disappeared before atoll voyage");
				}
				buildAtollTradePost(level, waterY[0]);
				waterman.setFishingCooldown(2400);
				waterman.setBoatTripCooldown(0);
				waterman.setAtollTradeCooldown(0);
				waterman.setNextAtollTradeTargetForTesting(new BlockPos(0, waterY[0], ATOLL_Z));
			});
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				placeCamera(player, 14.0D, waterY[0] + 7.0D, ATOLL_Z - 10.0D, 35.0F, 22.0F);
			});
			context.getInput().lookAt(35.0F, 22.0F);

			context.waitFor(client -> client.level != null
				&& client.level.getEntity(watermanId[0]) instanceof WatermanEntity waterman
				&& client.level.getEntity(shipId[0]) instanceof RiverSkiffEntity ship
				&& waterman.getVehicle() == ship
				&& waterman.getMainHandItem().is(Items.EMERALD)
				&& ship.getZ() > ATOLL_Z - 6.0D, 3000);
			context.waitTicks(8);
			context.takeScreenshot("waterman-trading-at-atoll");
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				placeCamera(player, 9.0D, waterY[0] + 6.0D, -6.0D, 45.0F, 24.0F);
			});
			context.getInput().lookAt(45.0F, 24.0F);

			context.waitFor(client -> {
				if (client.level == null
					|| !(client.level.getEntity(watermanId[0]) instanceof WatermanEntity waterman)
					|| !(client.level.getEntity(shipId[0]) instanceof RiverSkiffEntity ship)) {
					return false;
				}
				return !waterman.isPassenger()
					&& waterman.getMainHandItem().is(Items.EMERALD_BLOCK)
					&& horizontalDistanceSqr(ship.getX(), ship.getZ(), PORT_X, PORT_Z) < 12.0D;
			}, 3600);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				WatermanEntity waterman = (WatermanEntity)((ServerLevel)player.level()).getEntity(watermanId[0]);
				if (waterman == null
					|| waterman.getCompletedAtollTrades() < 1
					|| waterman.getLastAtollTradeWealth() < 50) {
					throw new AssertionError("waterman did not return from the atoll with substantial wealth");
				}
			});
			positionCameraBesideShip(singleplayer, shipId[0], 45.0F, 23.0F);
			context.getInput().lookAt(45.0F, 23.0F);
			context.waitTicks(8);
			context.takeScreenshot("waterman-returned-rich-from-atoll");

			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				placeCamera(player, 6.0D, waterY[0] + 4.5D, -2.0D, 200.0F, 18.0F);
			});
			context.getInput().lookAt(200.0F, 18.0F);
			context.waitFor(client -> {
				if (client.level == null || !(client.level.getEntity(watermanId[0]) instanceof WatermanEntity waterman)) {
					return false;
				}
				return !waterman.isPassenger()
					&& horizontalDistanceSqr(waterman.getX(), waterman.getZ(), HOME_CHEST_X + 0.5D, HOME_CHEST_Z + 0.5D) < 8.0D;
			}, 1200);
			context.waitTicks(12);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ServerLevel level = (ServerLevel)player.level();
				WatermanEntity waterman = (WatermanEntity)level.getEntity(watermanId[0]);
				BlockEntity blockEntity = level.getBlockEntity(new BlockPos(HOME_CHEST_X, waterY[0] + 1, HOME_CHEST_Z));
				int chestTreasure = countTreasure(blockEntity);
				if (waterman == null || chestTreasure < 50 || waterman.getLastHomeDepositCount() < 50) {
					throw new AssertionError(
						"waterman did not store atoll treasure in the chest beside his bed (chest="
							+ chestTreasure + ", deposited=" + (waterman == null ? -1 : waterman.getLastHomeDepositCount()) + ")"
					);
				}
			});
			context.takeScreenshot("waterman-storing-atoll-treasure");
		}
	}

	private static void buildPortScene(ServerLevel level, int waterY) {
		for (int x = -20; x <= 20; x++) {
			for (int z = -8; z <= 110; z++) {
				level.setBlockAndUpdate(new BlockPos(x, waterY - 1, z), Blocks.STONE.defaultBlockState());
				level.setBlockAndUpdate(new BlockPos(x, waterY, z), Blocks.WATER.defaultBlockState());
				for (int y = 1; y <= 6; y++) {
					level.setBlockAndUpdate(new BlockPos(x, waterY + y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		for (int x = -2; x <= 2; x++) {
			for (int z = -7; z <= 0; z++) {
				level.setBlockAndUpdate(new BlockPos(x, waterY, z), Blocks.OAK_PLANKS.defaultBlockState());
			}
		}
		// Small waterside bedroom: red bed with a chest immediately beside it.
		level.setBlockAndUpdate(
			new BlockPos(-1, waterY + 1, HOME_CHEST_Z),
			Blocks.BED.red().defaultBlockState()
				.setValue(BedBlock.FACING, Direction.EAST)
				.setValue(BedBlock.PART, BedPart.FOOT)
		);
		level.setBlockAndUpdate(
			new BlockPos(0, waterY + 1, HOME_CHEST_Z),
			Blocks.BED.red().defaultBlockState()
				.setValue(BedBlock.FACING, Direction.EAST)
				.setValue(BedBlock.PART, BedPart.HEAD)
		);
		level.setBlockAndUpdate(new BlockPos(HOME_CHEST_X, waterY + 1, HOME_CHEST_Z), Blocks.CHEST.defaultBlockState());
		for (int x = -2; x <= 2; x++) {
			level.setBlockAndUpdate(new BlockPos(x, waterY + 1, -7), Blocks.OAK_PLANKS.defaultBlockState());
			level.setBlockAndUpdate(new BlockPos(x, waterY + 2, -7), Blocks.OAK_PLANKS.defaultBlockState());
		}
	}

	private static int countTreasure(BlockEntity blockEntity) {
		if (!(blockEntity instanceof ChestBlockEntity chest)) {
			return 0;
		}
		int total = 0;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			ItemStack stack = chest.getItem(slot);
			if (WatermanEntity.isAtollTreasure(stack)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static void buildAtollTradePost(ServerLevel level, int waterY) {
		for (int x = 5; x <= 10; x++) {
			for (int z = ATOLL_Z - 3; z <= ATOLL_Z + 3; z++) {
				if ((x + z) % 5 != 0) {
					level.setBlockAndUpdate(new BlockPos(x, waterY, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
				}
			}
		}
		for (int y = 0; y <= 6; y++) {
			level.setBlockAndUpdate(new BlockPos(8, waterY + y, ATOLL_Z), Blocks.RAW_IRON_BLOCK.defaultBlockState());
		}
		level.setBlockAndUpdate(new BlockPos(6, waterY + 1, ATOLL_Z - 1), Blocks.BARREL.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(6, waterY + 1, ATOLL_Z + 1), Blocks.CHEST.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(9, waterY + 1, ATOLL_Z - 2), Blocks.EMERALD_BLOCK.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(9, waterY + 1, ATOLL_Z + 2), Blocks.GOLD_BLOCK.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(8, waterY + 6, ATOLL_Z - 1), Blocks.LANTERN.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(8, waterY + 6, ATOLL_Z + 1), Blocks.LANTERN.defaultBlockState());
	}

	private static void positionCameraBesideShip(TestSingleplayerContext singleplayer, int shipId, float yaw, float pitch) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().get(0);
			RiverSkiffEntity ship = (RiverSkiffEntity)((ServerLevel)player.level()).getEntity(shipId);
			if (ship == null) {
				throw new AssertionError("river skiff disappeared during waterman test");
			}
			placeCamera(player, ship.getX() + 8.0D, ship.getY() + 5.0D, ship.getZ() - 8.0D, yaw, pitch);
		});
	}

	private static void placeCamera(ServerPlayer player, double x, double y, double z, float yaw, float pitch) {
		player.teleportTo(x, y, z);
		player.setYRot(yaw);
		player.setXRot(pitch);
	}

	private static double horizontalDistanceSqr(double x1, double z1, double x2, double z2) {
		double dx = x1 - x2;
		double dz = z1 - z2;
		return dx * dx + dz * dz;
	}
}
