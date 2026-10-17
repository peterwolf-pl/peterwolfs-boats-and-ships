package com.piotrek.peterwolfsboatsandships.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import com.piotrek.peterwolfsboatsandships.entity.ExplorerSloopEntity;
import com.piotrek.peterwolfsboatsandships.entity.MerchantSchoonerEntity;
import com.piotrek.peterwolfsboatsandships.entity.WatermanEntity;
import com.piotrek.peterwolfsboatsandships.worldgen.WatermanSettlementPiece;
import com.piotrek.peterwolfsboatsandships.worldgen.WatermanSettlementStructure;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WatermanSettlementCommands {
	private WatermanSettlementCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
	}

	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("pwboats")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("spawn-settlement")
				.executes(ctx -> spawn(ctx, false))
				.then(Commands.literal("small").executes(ctx -> spawn(ctx, false)))
				.then(Commands.literal("large").executes(ctx -> spawn(ctx, true))))
			.then(Commands.literal("send-home")
				.executes(ctx -> sendHome(ctx, 64))
				.then(Commands.argument("radius", IntegerArgumentType.integer(1, 512))
					.executes(ctx -> sendHome(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
			.then(Commands.literal("scale")
				.executes(ctx -> scaleShips(ctx, 2.0F, 128))
				.then(Commands.literal("double")
					.executes(ctx -> doubleShips(ctx, 128))
					.then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
						.executes(ctx -> doubleShips(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(Commands.literal("reset")
					.executes(ctx -> resetShips(ctx, 128))
					.then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
						.executes(ctx -> resetShips(ctx, IntegerArgumentType.getInteger(ctx, "radius")))))
				.then(Commands.argument("multiplier", FloatArgumentType.floatArg(0.25F, 8.0F))
					.executes(ctx -> scaleShips(ctx, FloatArgumentType.getFloat(ctx, "multiplier"), 128))
					.then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
						.executes(ctx -> scaleShips(ctx, FloatArgumentType.getFloat(ctx, "multiplier"), IntegerArgumentType.getInteger(ctx, "radius"))))))
			.then(Commands.literal("double-size")
				.executes(ctx -> doubleShips(ctx, 128))
				.then(Commands.argument("radius", IntegerArgumentType.integer(1, 1024))
					.executes(ctx -> doubleShips(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
	}

	private static int scaleShips(CommandContext<CommandSourceStack> context, float targetScale, int radius) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Vec3 origin = source.getPosition();
		AABB box = new AABB(origin, origin).inflate(radius);
		List<AbstractShipEntity> ships = new java.util.ArrayList<>(level.getEntitiesOfClass(
			AbstractShipEntity.class,
			box,
			ship -> ship.isAlive() && (ship instanceof ExplorerSloopEntity || ship instanceof MerchantSchoonerEntity)
		));
		if (source.getEntity() instanceof ServerPlayer player
			&& player.getVehicle() instanceof AbstractShipEntity ridden
			&& (ridden instanceof ExplorerSloopEntity || ridden instanceof MerchantSchoonerEntity)
			&& !ships.contains(ridden)) {
			ships.add(ridden);
		}
		AbstractShipEntity.setGlobalSailingShipScaleMultiplier(targetScale / 2.0F);

		if (ships.isEmpty()) {
			source.sendFailure(Component.translatable(
				"command.peterwolfs_boats_and_ships.scale_none",
				radius
			));
			return 0;
		}
		for (AbstractShipEntity ship : ships) {
			ship.setShipScale(targetScale);
		}
		int count = ships.size();
		source.sendSuccess(
			() -> Component.translatable(
				"command.peterwolfs_boats_and_ships.scale_success",
				count,
				String.format(java.util.Locale.ROOT, "%.2f", targetScale),
				radius
			),
			true
		);
		return count;
	}

	private static int doubleShips(CommandContext<CommandSourceStack> context, int radius) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Vec3 origin = source.getPosition();
		AABB box = new AABB(origin, origin).inflate(radius);
		List<AbstractShipEntity> ships = new java.util.ArrayList<>(level.getEntitiesOfClass(
			AbstractShipEntity.class,
			box,
			ship -> ship.isAlive() && (ship instanceof ExplorerSloopEntity || ship instanceof MerchantSchoonerEntity)
		));
		if (source.getEntity() instanceof ServerPlayer player
			&& player.getVehicle() instanceof AbstractShipEntity ridden
			&& (ridden instanceof ExplorerSloopEntity || ridden instanceof MerchantSchoonerEntity)
			&& !ships.contains(ridden)) {
			ships.add(ridden);
		}
		AbstractShipEntity.setGlobalSailingShipScaleMultiplier(AbstractShipEntity.getGlobalSailingShipScaleMultiplier() * 2.0F);

		if (ships.isEmpty()) {
			source.sendFailure(Component.translatable(
				"command.peterwolfs_boats_and_ships.scale_none",
				radius
			));
			return 0;
		}
		for (AbstractShipEntity ship : ships) {
			ship.setShipScale(ship.getShipScale() * 2.0F);
		}
		int count = ships.size();
		source.sendSuccess(
			() -> Component.translatable(
				"command.peterwolfs_boats_and_ships.scale_double_success",
				count,
				radius
			),
			true
		);
		return count;
	}

	private static int resetShips(CommandContext<CommandSourceStack> context, int radius) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Vec3 origin = source.getPosition();
		AABB box = new AABB(origin, origin).inflate(radius);
		List<AbstractShipEntity> ships = new java.util.ArrayList<>(level.getEntitiesOfClass(
			AbstractShipEntity.class,
			box,
			ship -> ship.isAlive() && (ship instanceof ExplorerSloopEntity || ship instanceof MerchantSchoonerEntity)
		));
		if (source.getEntity() instanceof ServerPlayer player
			&& player.getVehicle() instanceof AbstractShipEntity ridden
			&& (ridden instanceof ExplorerSloopEntity || ridden instanceof MerchantSchoonerEntity)
			&& !ships.contains(ridden)) {
			ships.add(ridden);
		}
		AbstractShipEntity.setGlobalSailingShipScaleMultiplier(1.0F);
		for (AbstractShipEntity ship : ships) {
			ship.setShipScale(ship instanceof ExplorerSloopEntity || ship instanceof MerchantSchoonerEntity ? 2.0F : 1.0F);
		}
		int count = ships.size();
		source.sendSuccess(
			() -> Component.translatable(
				"command.peterwolfs_boats_and_ships.scale_reset_success",
				count,
				radius
			),
			true
		);
		return count;
	}

	private static int sendHome(CommandContext<CommandSourceStack> context, int radius) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Vec3 origin = source.getPosition();
		AABB box = new AABB(origin, origin).inflate(radius);
		List<WatermanEntity> watermen = level.getEntitiesOfClass(
			WatermanEntity.class,
			box,
			waterman -> waterman.isAlive() && !waterman.isBaby()
		);
		if (watermen.isEmpty()) {
			source.sendFailure(Component.translatable(
				"command.peterwolfs_boats_and_ships.send_home_none",
				radius
			));
			return 0;
		}
		int sentHome = 0;
		int seeking = 0;
		for (WatermanEntity waterman : watermen) {
			if (waterman.requestReturnHome(level) == WatermanEntity.HomeOrder.RETURNING) {
				sentHome++;
			} else {
				seeking++;
			}
		}
		int sentHomeCount = sentHome;
		int seekingCount = seeking;
		source.sendSuccess(
			() -> Component.translatable(
				"command.peterwolfs_boats_and_ships.send_home_success",
				sentHomeCount,
				seekingCount,
				radius
			),
			true
		);
		return sentHome + seeking;
	}

	private static int spawn(CommandContext<CommandSourceStack> context, boolean large) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = source.getLevel();
		BlockPos pos = player.blockPosition();

		Direction waterDir = findWaterDirection(level, pos);
		if (waterDir == null) {
			source.sendFailure(Component.translatable("command.peterwolfs_boats_and_ships.spawn_settlement_no_water"));
			return 0;
		}

		BlockPos shore = findShoreBlock(level, pos, waterDir);
		int waterY = findWaterSurfaceY(level, shore, waterDir);
		WatermanSettlementStructure.ShoreSite site = new WatermanSettlementStructure.ShoreSite(
			shore.getX(), shore.getZ(), waterY, waterDir
		);
		RandomSource random = level.getRandom();
		WatermanSettlementPiece piece = new WatermanSettlementPiece(random, site, large);
		piece.alignFloorTo(waterY);
		BoundingBox box = piece.getBoundingBox();
		piece.postProcess(
			level,
			level.structureManager(),
			level.getChunkSource().getGenerator(),
			random,
			box,
			new ChunkPos(shore.getX() >> 4, shore.getZ() >> 4),
			shore
		);
		source.sendSuccess(
			() -> Component.translatable(
				"command.peterwolfs_boats_and_ships.spawn_settlement_success",
				shore.getX(), waterY, shore.getZ()
			),
			true
		);
		return 1;
	}

	private static Direction findWaterDirection(ServerLevel level, BlockPos origin) {
		Direction best = null;
		int bestWater = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int water = 0;
			for (int d = 1; d <= 24; d++) {
				BlockPos sample = origin.relative(dir, d);
				if (isWater(level, sample) || isWater(level, sample.below()) || isWater(level, sample.above())) {
					water++;
				}
			}
			if (water > bestWater) {
				bestWater = water;
				best = dir;
			}
		}
		return bestWater >= 8 ? best : null;
	}

	private static BlockPos findShoreBlock(ServerLevel level, BlockPos origin, Direction waterDir) {
		BlockPos cursor = origin.immutable();
		for (int i = 0; i < 16; i++) {
			BlockPos next = cursor.relative(waterDir);
			if (isWater(level, next) || isWater(level, next.below())) {
				return cursor;
			}
			if (isWater(level, cursor) || isWater(level, cursor.below())) {
				return cursor.relative(waterDir.getOpposite());
			}
			cursor = next;
		}
		return origin;
	}

	private static int findWaterSurfaceY(ServerLevel level, BlockPos shore, Direction waterDir) {
		for (int d = 1; d <= 16; d++) {
			BlockPos sample = shore.relative(waterDir, d);
			int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, sample.getX(), sample.getZ());
			for (int y = surface + 2; y >= surface - 12; y--) {
				BlockPos pos = new BlockPos(sample.getX(), y, sample.getZ());
				if (isWater(level, pos) && !isWater(level, pos.above())) {
					return y;
				}
			}
		}
		return level.getSeaLevel() - 1;
	}

	private static boolean isWater(ServerLevel level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER);
	}
}
