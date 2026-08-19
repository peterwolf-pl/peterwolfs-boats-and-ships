package com.piotrek.peterwolfsboatsandships.worldgen;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * Places a waterman hamlet on the bank of a large water body, built at the waterline.
 */
public final class WatermanSettlementStructure extends Structure {
	public static final MapCodec<WatermanSettlementStructure> CODEC = simpleCodec(WatermanSettlementStructure::new);

	/** Open-water samples required along the water-facing axis. */
	private static final int MIN_WATER_REACH = 20;
	/** Water samples required across the water body at mid-reach. */
	private static final int MIN_WATER_WIDTH = 12;
	/** Land samples required inland from the shore. */
	private static final int MIN_LAND_BACKING = 8;
	/** Max blocks the bank may sit above the water surface. */
	private static final int MAX_BANK_HEIGHT = 4;

	public WatermanSettlementStructure(StructureSettings settings) {
		super(settings);
	}

	@Override
	public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		Optional<ShoreSite> site = findShore(context);
		if (site.isEmpty()) {
			return Optional.empty();
		}
		ShoreSite shore = site.get();
		boolean large = context.random().nextInt(10) == 0;
		BlockPos anchor = new BlockPos(shore.shoreX(), shore.waterY(), shore.shoreZ());
		return Optional.of(new GenerationStub(anchor, builder ->
			builder.addPiece(new WatermanSettlementPiece(context.random(), shore, large))
		));
	}

	@Override
	public StructureType<?> type() {
		return ModStructures.WATERMAN_SETTLEMENT_TYPE;
	}

	static Optional<ShoreSite> findShore(GenerationContext context) {
		int cx = context.chunkPos().getMiddleBlockX();
		int cz = context.chunkPos().getMiddleBlockZ();
		int sea = context.chunkGenerator().getSeaLevel();

		Direction bestDir = null;
		int bestScore = 0;
		int bestX = cx;
		int bestZ = cz;
		int bestWaterY = sea - 1;

		for (int ox = -16; ox <= 16; ox += 4) {
			for (int oz = -16; oz <= 16; oz += 4) {
				int x = cx + ox;
				int z = cz + oz;
				if (!isLand(context, x, z, sea)) {
					continue;
				}
				int landY = surfaceY(context, x, z);
				if (landY < sea - 1 || landY > sea + MAX_BANK_HEIGHT) {
					continue;
				}
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					int score = waterBodyScore(context, x, z, dir, sea);
					if (score > bestScore) {
						bestScore = score;
						bestDir = dir;
						bestX = x;
						bestZ = z;
						bestWaterY = waterSurfaceY(context, x, z, dir, sea);
					}
				}
			}
		}

		if (bestDir == null || bestScore <= 0) {
			return Optional.empty();
		}
		return Optional.of(new ShoreSite(bestX, bestZ, bestWaterY, bestDir));
	}

	/** Top water block facing the shore — hamlets sit on this Y, not the raised bank. */
	static int waterSurfaceY(GenerationContext context, int x, int z, Direction dir, int sea) {
		for (int d = 1; d <= 16; d++) {
			int wx = x + dir.getStepX() * d;
			int wz = z + dir.getStepZ() * d;
			if (isWater(context, wx, wz, sea)) {
				return height(context, wx, wz, Heightmap.Types.WORLD_SURFACE_WG);
			}
		}
		return sea - 1;
	}

	private static int waterBodyScore(GenerationContext context, int x, int z, Direction dir, int sea) {
		int water = 0;
		for (int d = 1; d <= 32; d++) {
			if (isWater(context, x + dir.getStepX() * d, z + dir.getStepZ() * d, sea)) {
				water++;
			}
		}
		if (water < MIN_WATER_REACH) {
			return 0;
		}

		Direction left = dir.getCounterClockWise();
		int midX = x + dir.getStepX() * 16;
		int midZ = z + dir.getStepZ() * 16;
		int width = 0;
		for (int w = -10; w <= 10; w++) {
			if (isWater(context, midX + left.getStepX() * w, midZ + left.getStepZ() * w, sea)) {
				width++;
			}
		}
		if (width < MIN_WATER_WIDTH) {
			return 0;
		}

		int floor = height(context, midX, midZ, Heightmap.Types.OCEAN_FLOOR_WG);
		int surface = height(context, midX, midZ, Heightmap.Types.WORLD_SURFACE_WG);
		if (surface - floor < 3) {
			return 0;
		}

		Direction back = dir.getOpposite();
		int land = 0;
		for (int d = 1; d <= 16; d++) {
			if (isLand(context, x + back.getStepX() * d, z + back.getStepZ() * d, sea)) {
				land++;
			}
		}
		if (land < MIN_LAND_BACKING) {
			return 0;
		}
		return water + width * 2 + land;
	}

	static boolean isWater(GenerationContext context, int x, int z, int sea) {
		int floor = height(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
		int surface = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
		return surface - floor >= 2 && floor < sea;
	}

	static boolean isLand(GenerationContext context, int x, int z, int sea) {
		int floor = height(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG);
		int surface = height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
		return surface - floor < 2 && surface >= sea - 1;
	}

	static int surfaceY(GenerationContext context, int x, int z) {
		return height(context, x, z, Heightmap.Types.WORLD_SURFACE_WG);
	}

	static int height(GenerationContext context, int x, int z, Heightmap.Types type) {
		return context.chunkGenerator().getFirstOccupiedHeight(
			x, z, type, context.heightAccessor(), context.randomState()
		);
	}

	public record ShoreSite(int shoreX, int shoreZ, int waterY, Direction waterDir) {
	}
}
