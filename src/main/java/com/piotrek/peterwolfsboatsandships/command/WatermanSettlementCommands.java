package com.piotrek.peterwolfsboatsandships.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.piotrek.peterwolfsboatsandships.worldgen.WatermanSettlementPiece;
import com.piotrek.peterwolfsboatsandships.worldgen.WatermanSettlementStructure;
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
				.then(Commands.literal("large").executes(ctx -> spawn(ctx, true)))));
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
