package com.piotrek.peterwolfsboatsandships.worldgen;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import com.piotrek.peterwolfsboatsandships.entity.ExplorerSloopEntity;
import com.piotrek.peterwolfsboatsandships.entity.MerchantSchoonerEntity;
import com.piotrek.peterwolfsboatsandships.entity.RiverSkiffEntity;
import com.piotrek.peterwolfsboatsandships.entity.WatermanEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.block.Rotation;

/**
 * Shore hamlet: flat-roof oak huts with bamboo doors, crop plots and a port.
 * One in ten settlements is large and includes a lighthouse.
 *
 * Local axes: {@code x} along the shore, {@code z} toward open water, {@code y} up from the bank.
 */
public final class WatermanSettlementPiece extends StructurePiece {
	private static final int SMALL_WIDTH = 28;
	private static final int SMALL_DEPTH = 32;
	private static final int SMALL_LAND = 20;
	private static final int SMALL_HEIGHT = 16;
	private static final int LARGE_WIDTH = 40;
	private static final int LARGE_DEPTH = 40;
	private static final int LARGE_LAND = 24;
	private static final int LARGE_HEIGHT = 22;
	private static final int WATERMAN_SLOTS = 8;

	private final boolean large;
	private final Direction waterDir;
	private final int originX;
	private final int originZ;
	private final int layoutWidth;
	private final int layoutDepth;
	private final int landDepth;
	private final int layoutVariant;
	private int floorY;
	private int watermanSpawnMask;
	private int boatSpawnMask;

	public WatermanSettlementPiece(RandomSource random, WatermanSettlementStructure.ShoreSite shore, boolean large) {
		super(
			ModStructures.WATERMAN_SETTLEMENT_PIECE,
			0,
			boundingBoxFor(large, shore.waterDir(), originX(shore, large), originZ(shore, large), shore.landY())
		);
		this.setOrientation(Direction.SOUTH);
		this.large = large;
		this.waterDir = shore.waterDir().getAxis().isHorizontal() ? shore.waterDir() : Direction.SOUTH;
		this.originX = originX(shore, large);
		this.originZ = originZ(shore, large);
		this.layoutWidth = large ? LARGE_WIDTH : SMALL_WIDTH;
		this.layoutDepth = large ? LARGE_DEPTH : SMALL_DEPTH;
		this.landDepth = large ? LARGE_LAND : SMALL_LAND;
		this.layoutVariant = random.nextInt(3);
		this.floorY = -1;
		this.watermanSpawnMask = 0;
		this.boatSpawnMask = 0;
	}

	public WatermanSettlementPiece(StructurePieceSerializationContext context, CompoundTag tag) {
		super(ModStructures.WATERMAN_SETTLEMENT_PIECE, tag);
		this.large = tag.getBooleanOr("Large", false);
		this.waterDir = Direction.from2DDataValue(tag.getIntOr("WaterDir", Direction.SOUTH.get2DDataValue()));
		this.originX = tag.getIntOr("OriginX", 0);
		this.originZ = tag.getIntOr("OriginZ", 0);
		this.layoutWidth = this.large ? LARGE_WIDTH : SMALL_WIDTH;
		this.layoutDepth = this.large ? LARGE_DEPTH : SMALL_DEPTH;
		this.landDepth = this.large ? LARGE_LAND : SMALL_LAND;
		this.layoutVariant = tag.getIntOr("LayoutVariant", 0);
		this.floorY = tag.getIntOr("FloorY", -1);
		this.watermanSpawnMask = tag.getIntOr("WatermanSpawnMask", 0);
		this.boatSpawnMask = tag.getIntOr("BoatSpawnMask", 0);
	}

	@Override
	protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
		tag.putBoolean("Large", this.large);
		tag.putInt("WaterDir", this.waterDir.get2DDataValue());
		tag.putInt("OriginX", this.originX);
		tag.putInt("OriginZ", this.originZ);
		tag.putInt("LayoutVariant", this.layoutVariant);
		tag.putInt("FloorY", this.floorY);
		tag.putInt("WatermanSpawnMask", this.watermanSpawnMask);
		tag.putInt("BoatSpawnMask", this.boatSpawnMask);
	}

	public void alignFloorTo(int worldY) {
		int dy = worldY - this.boundingBox.minY();
		this.move(0, dy, 0);
		this.floorY = worldY;
	}

	@Override
	public void postProcess(
		WorldGenLevel level,
		StructureManager structureManager,
		ChunkGenerator generator,
		RandomSource random,
		BoundingBox chunkBB,
		ChunkPos chunkPos,
		BlockPos referencePos
	) {
		if (!this.fitToLand(level)) {
			return;
		}

		RandomSource local = RandomSource.create(
			(long) this.originX * 341873128712L
				+ (long) this.originZ * 132897987541L
				+ this.layoutVariant * 31L
				+ (this.large ? 17L : 0L)
		);

		prepareGround(level, chunkBB);
		buildPaths(level, chunkBB);
		buildFarms(level, chunkBB, local);
		buildHuts(level, chunkBB, local);
		BlockPos port = buildPort(level, chunkBB);
		if (this.large) {
			buildLighthouse(level, chunkBB);
		}
		spawnWatermen(level, chunkBB, port);
		spawnBoats(level, chunkBB);
	}

	private boolean fitToLand(WorldGenLevel level) {
		if (this.floorY >= 0) {
			return true;
		}
		long total = 0L;
		int count = 0;
		for (int z = 2; z < this.landDepth - 1; z++) {
			for (int x = 2; x < this.layoutWidth - 2; x++) {
				BlockPos world = this.localToWorld(x, 0, z);
				int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, world.getX(), world.getZ());
				BlockPos surface = new BlockPos(world.getX(), y, world.getZ());
				if (level.getFluidState(surface).is(FluidTags.WATER) || level.getFluidState(surface.below()).is(FluidTags.WATER)) {
					continue;
				}
				total += y;
				count++;
			}
		}
		if (count == 0) {
			this.floorY = this.boundingBox.minY() + 4;
			return true;
		}
		int avg = (int) (total / count);
		this.alignFloorTo(avg);
		return true;
	}

	private void prepareGround(WorldGenLevel level, BoundingBox chunkBB) {
		for (int z = 0; z < this.landDepth; z++) {
			for (int x = 0; x < this.layoutWidth; x++) {
				if (this.isWaterColumn(level, x, z)) {
					continue;
				}
				this.set(level, chunkBB, x, 0, z, Blocks.GRASS_BLOCK.defaultBlockState());
				this.fillDown(level, chunkBB, x, -1, z, Blocks.DIRT.defaultBlockState());
				for (int y = 1; y < (this.large ? LARGE_HEIGHT : SMALL_HEIGHT) - 2; y++) {
					this.set(level, chunkBB, x, y, z, Blocks.AIR.defaultBlockState());
				}
			}
		}
	}

	private void buildPaths(WorldGenLevel level, BoundingBox chunkBB) {
		int pathX = this.layoutWidth / 2;
		for (int z = 4; z < this.landDepth; z++) {
			this.set(level, chunkBB, pathX, 0, z, Blocks.DIRT_PATH.defaultBlockState());
			this.set(level, chunkBB, pathX - 1, 0, z, Blocks.DIRT_PATH.defaultBlockState());
			this.set(level, chunkBB, pathX + 1, 0, z, Blocks.DIRT_PATH.defaultBlockState());
		}
		int hutRow = 11 + (this.layoutVariant % 2);
		for (int x = 3; x < this.layoutWidth - 3; x++) {
			this.set(level, chunkBB, x, 0, hutRow, Blocks.DIRT_PATH.defaultBlockState());
		}
	}

	private void buildFarms(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
		buildCropPlot(level, chunkBB, random, 2, 4, 7, 6);
		buildCropPlot(level, chunkBB, random, this.layoutWidth - 10, 4, 7, 6);
		if (this.large) {
			buildCropPlot(level, chunkBB, random, this.layoutWidth / 2 - 4, 3, 8, 5);
		}
	}

	private void buildCropPlot(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int ox, int oz, int w, int d) {
		for (int z = oz; z < oz + d; z++) {
			for (int x = ox; x < ox + w; x++) {
				boolean edge = x == ox || x == ox + w - 1 || z == oz || z == oz + d - 1;
				if (edge) {
					this.set(level, chunkBB, x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
					continue;
				}
				boolean water = (x - ox) == w / 2 && (z - oz) == d / 2;
				if (water) {
					this.set(level, chunkBB, x, 0, z, Blocks.WATER.defaultBlockState());
					continue;
				}
				this.set(level, chunkBB, x, 0, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7));
				this.set(level, chunkBB, x, 1, z, cropFor(x + z, random));
			}
		}
		this.set(level, chunkBB, ox, 1, oz + d / 2, Blocks.OAK_FENCE_GATE.defaultBlockState()
			.setValue(FenceGateBlock.FACING, Direction.WEST));
		this.set(level, chunkBB, ox + w - 1, 1, oz + 1, Blocks.HAY_BLOCK.defaultBlockState());
		this.set(level, chunkBB, ox + 1, 1, oz, Blocks.COMPOSTER.defaultBlockState());
	}

	private static BlockState cropFor(int key, RandomSource random) {
		int age = 3 + random.nextInt(5);
		return switch (Math.floorMod(key, 3)) {
			case 0 -> ((CropBlock) Blocks.WHEAT).getStateForAge(Math.min(age, CropBlock.MAX_AGE));
			case 1 -> ((CropBlock) Blocks.CARROTS).getStateForAge(Math.min(age, CropBlock.MAX_AGE));
			default -> ((CropBlock) Blocks.POTATOES).getStateForAge(Math.min(age, CropBlock.MAX_AGE));
		};
	}

	private void buildHuts(WorldGenLevel level, BoundingBox chunkBB, RandomSource random) {
		int[][] huts = this.large
			? new int[][]{{3, 10}, {11, 8}, {19, 10}, {27, 8}, {7, 16}, {23, 16}}
			: new int[][]{{3, 10}, {11, 8}, {19, 10}};
		int shift = this.layoutVariant == 1 ? 1 : (this.layoutVariant == 2 ? -1 : 0);
		for (int i = 0; i < huts.length; i++) {
			int style = Math.floorMod(i + this.layoutVariant, 6);
			int w = hutWidth(style);
			int d = hutDepth(style);
			int x = Math.max(1, Math.min(this.layoutWidth - w - 2, huts[i][0] + shift));
			int z = Math.max(5, Math.min(this.landDepth - d - 3, huts[i][1] + shift));
			buildHut(level, chunkBB, random, x, z, style);
		}
	}

	private static int hutWidth(int style) {
		return switch (style) {
			case 1, 4 -> 6;
			case 5 -> 4;
			default -> 5;
		};
	}

	private static int hutDepth(int style) {
		return switch (style) {
			case 2, 4 -> 6;
			default -> 5;
		};
	}

	private void buildHut(WorldGenLevel level, BoundingBox chunkBB, RandomSource random, int ox, int oz, int style) {
		int w = hutWidth(style);
		int d = hutDepth(style);
		int wallH = (style == 1 || style == 4) ? 3 : 2;
		HutKit kit = hutKit(style);
		boolean onWater = hutSitsOnWater(level, ox, oz, w, d);

		for (int z = oz; z <= oz + d; z++) {
			for (int x = ox; x <= ox + w; x++) {
				boolean waterCol = this.isWaterColumn(level, x, z);
				this.set(level, chunkBB, x, 0, z, kit.floor);
				if (waterCol) {
					this.fillDown(level, chunkBB, x, -1, z, kit.log);
				}
				boolean edge = x == ox || x == ox + w || z == oz || z == oz + d;
				boolean corner = (x == ox || x == ox + w) && (z == oz || z == oz + d);
				for (int y = 1; y <= wallH; y++) {
					if (edge) {
						this.set(level, chunkBB, x, y, z, corner ? kit.log : kit.wall);
					} else {
						this.set(level, chunkBB, x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
		placeHutRoof(level, chunkBB, ox, oz, w, d, wallH, style, kit);

		Direction doorFacing = onWater ? Direction.NORTH : Direction.SOUTH;
		int doorX = ox + w / 2;
		int doorZ = onWater ? oz : oz + d;
		placeBambooDoor(level, chunkBB, doorX, 1, doorZ, doorFacing);
		placeHutWindows(level, chunkBB, ox, oz, w, d, wallH, style, kit, doorX, doorZ);

		DyeColor bedColor = switch (style) {
			case 1 -> DyeColor.BROWN;
			case 2 -> DyeColor.ORANGE;
			case 3 -> DyeColor.WHITE;
			case 4 -> DyeColor.GREEN;
			case 5 -> DyeColor.YELLOW;
			default -> DyeColor.LIGHT_BLUE;
		};
		int bedZ = onWater ? oz + d - 2 : oz + 1;
		placeBed(level, chunkBB, ox + 1, 1, bedZ, Direction.SOUTH, bedColor);
		placeStockedChest(level, chunkBB, ox + w - 1, 1, onWater ? oz + d - 1 : oz + 1, Direction.WEST, random);
		this.set(level, chunkBB, ox + 1, 1, oz + d / 2, Blocks.BARREL.defaultBlockState());
		this.set(level, chunkBB, ox + w / 2, wallH, oz + d / 2, Blocks.LANTERN.defaultBlockState());
		if (style == 1 || style == 4) {
			this.set(level, chunkBB, ox + 1, wallH + 1, oz + 1, Blocks.COBBLESTONE.defaultBlockState());
			this.set(level, chunkBB, ox + 1, wallH + 2, oz + 1, Blocks.COBBLESTONE_WALL.defaultBlockState());
		}
		if (style == 3) {
			this.set(level, chunkBB, ox + w - 1, 1, oz + d / 2, Blocks.POTTED_FERN.defaultBlockState());
		} else if (style == 5) {
			this.set(level, chunkBB, ox + w - 1, 1, oz + 2, Blocks.CRAFTING_TABLE.defaultBlockState());
		}

		if (onWater) {
			buildBoardwalk(level, chunkBB, doorX, doorZ, Direction.NORTH);
		} else {
			int stepZ = doorZ + 1;
			this.set(level, chunkBB, doorX, 0, stepZ, Blocks.DIRT_PATH.defaultBlockState());
			if (style % 2 == 0) {
				this.set(level, chunkBB, doorX, 1, stepZ, kit.stair.setValue(StairBlock.FACING, Direction.NORTH));
			}
		}
	}

	private boolean hutSitsOnWater(WorldGenLevel level, int ox, int oz, int w, int d) {
		for (int z = oz; z <= oz + d; z++) {
			for (int x = ox; x <= ox + w; x++) {
				if (this.isWaterColumn(level, x, z)) {
					return true;
				}
			}
		}
		return false;
	}

	private void placeHutRoof(
		WorldGenLevel level,
		BoundingBox chunkBB,
		int ox,
		int oz,
		int w,
		int d,
		int wallH,
		int style,
		HutKit kit
	) {
		int roofY = wallH + 1;
		int pad = (style == 1 || style == 5) ? 1 : 0;
		for (int z = oz - pad; z <= oz + d + pad; z++) {
			for (int x = ox - pad; x <= ox + w + pad; x++) {
				boolean overhang = x < ox || x > ox + w || z < oz || z > oz + d;
				BlockState roof = switch (style) {
					case 2 -> kit.wall;
					case 4 -> overhang ? kit.slab : kit.wall;
					default -> kit.slab;
				};
				this.set(level, chunkBB, x, roofY, z, roof);
				if (style == 4 && !overhang && ((x + z) & 1) == 0) {
					this.set(level, chunkBB, x, roofY + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
				}
			}
		}
		if (style == 3 || style == 5) {
			for (int x = ox; x <= ox + w; x++) {
				this.set(level, chunkBB, x, roofY, oz - 1, kit.trap.setValue(TrapDoorBlock.FACING, Direction.NORTH));
				this.set(level, chunkBB, x, roofY, oz + d + 1, kit.trap.setValue(TrapDoorBlock.FACING, Direction.SOUTH));
			}
		}
	}

	private void placeHutWindows(
		WorldGenLevel level,
		BoundingBox chunkBB,
		int ox,
		int oz,
		int w,
		int d,
		int wallH,
		int style,
		HutKit kit,
		int doorX,
		int doorZ
	) {
		int wy = Math.min(2, wallH);
		BlockState window = switch (style) {
			case 2 -> Blocks.OAK_FENCE.defaultBlockState();
			case 5 -> kit.trap.setValue(TrapDoorBlock.FACING, Direction.NORTH);
			default -> Blocks.GLASS_PANE.defaultBlockState();
		};
		if (doorZ != oz) {
			this.set(level, chunkBB, ox + 1, wy, oz, window);
			if (w > 4) {
				this.set(level, chunkBB, ox + w - 1, wy, oz, window);
			}
		}
		if (doorZ != oz + d) {
			this.set(level, chunkBB, ox + 1, wy, oz + d, window);
			if (w > 4) {
				this.set(level, chunkBB, ox + w - 1, wy, oz + d, window);
			}
		}
		this.set(level, chunkBB, ox, wy, oz + d / 2, window);
		this.set(level, chunkBB, ox + w, wy, oz + d / 2, window);
	}

	/** Spruce plank walk from a water-side door inland until the bank or village path. */
	private void buildBoardwalk(WorldGenLevel level, BoundingBox chunkBB, int doorX, int doorZ, Direction inland) {
		int x = doorX;
		int z = doorZ;
		int pathX = this.layoutWidth / 2;
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
		BlockState piling = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		boolean reachedLand = false;
		for (int i = 0; i < 14; i++) {
			x += inland.getStepX();
			z += inland.getStepZ();
			if (x < 1 || x >= this.layoutWidth - 2 || z < 1 || z >= this.layoutDepth - 1) {
				break;
			}
			boolean water = this.isWaterColumn(level, x, z);
			this.set(level, chunkBB, x, 0, z, water ? deck : Blocks.DIRT_PATH.defaultBlockState());
			this.set(level, chunkBB, x + 1, 0, z, water ? slab : Blocks.DIRT_PATH.defaultBlockState());
			if (water) {
				this.fillDown(level, chunkBB, x, -1, z, piling);
				if ((i & 1) == 0) {
					this.set(level, chunkBB, x - 1, 0, z, piling);
					this.fillDown(level, chunkBB, x - 1, -1, z, piling);
					this.set(level, chunkBB, x - 1, 1, z, Blocks.OAK_FENCE.defaultBlockState());
					this.set(level, chunkBB, x + 2, 0, z, piling);
					this.fillDown(level, chunkBB, x + 2, -1, z, piling);
					this.set(level, chunkBB, x + 2, 1, z, Blocks.OAK_FENCE.defaultBlockState());
				}
			} else {
				reachedLand = true;
				break;
			}
			if (i > 3 && x != pathX && this.isWaterColumn(level, x + Integer.compare(pathX, x), z)) {
				x += Integer.compare(pathX, x);
			}
		}
		if (!reachedLand && x != pathX) {
			int dir = Integer.compare(pathX, x);
			for (int i = 0; i < 10 && x != pathX; i++) {
				x += dir;
				if (x < 1 || x >= this.layoutWidth - 1) {
					break;
				}
				boolean water = this.isWaterColumn(level, x, z);
				this.set(level, chunkBB, x, 0, z, water ? deck : Blocks.DIRT_PATH.defaultBlockState());
				if (water) {
					this.fillDown(level, chunkBB, x, -1, z, piling);
				} else {
					break;
				}
			}
		}
	}

	private static HutKit hutKit(int style) {
		return switch (style) {
			case 1 -> new HutKit(
				Blocks.SPRUCE_PLANKS.defaultBlockState(),
				axisY(Blocks.SPRUCE_LOG.defaultBlockState()),
				Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				Blocks.SPRUCE_PLANKS.defaultBlockState(),
				Blocks.SPRUCE_STAIRS.defaultBlockState(),
				Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
			);
			case 2 -> new HutKit(
				Blocks.DARK_OAK_PLANKS.defaultBlockState(),
				axisY(Blocks.DARK_OAK_LOG.defaultBlockState()),
				Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				Blocks.DARK_OAK_PLANKS.defaultBlockState(),
				Blocks.DARK_OAK_STAIRS.defaultBlockState(),
				Blocks.DARK_OAK_TRAPDOOR.defaultBlockState()
			);
			case 3 -> new HutKit(
				Blocks.BIRCH_PLANKS.defaultBlockState(),
				axisY(Blocks.STRIPPED_BIRCH_LOG.defaultBlockState()),
				Blocks.BIRCH_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				Blocks.BIRCH_PLANKS.defaultBlockState(),
				Blocks.BIRCH_STAIRS.defaultBlockState(),
				Blocks.BIRCH_TRAPDOOR.defaultBlockState()
			);
			case 4 -> new HutKit(
				Blocks.MANGROVE_PLANKS.defaultBlockState(),
				axisY(Blocks.MANGROVE_LOG.defaultBlockState()),
				Blocks.MANGROVE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				Blocks.MANGROVE_PLANKS.defaultBlockState(),
				Blocks.MANGROVE_STAIRS.defaultBlockState(),
				Blocks.MANGROVE_TRAPDOOR.defaultBlockState()
			);
			case 5 -> new HutKit(
				Blocks.BAMBOO_PLANKS.defaultBlockState(),
				axisY(Blocks.BAMBOO_BLOCK.defaultBlockState()),
				Blocks.BAMBOO_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				Blocks.BAMBOO_PLANKS.defaultBlockState(),
				Blocks.BAMBOO_STAIRS.defaultBlockState(),
				Blocks.BAMBOO_TRAPDOOR.defaultBlockState()
			);
			default -> new HutKit(
				Blocks.OAK_PLANKS.defaultBlockState(),
				axisY(Blocks.OAK_LOG.defaultBlockState()),
				Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				Blocks.OAK_PLANKS.defaultBlockState(),
				Blocks.OAK_STAIRS.defaultBlockState(),
				Blocks.OAK_TRAPDOOR.defaultBlockState()
			);
		};
	}

	private static BlockState axisY(BlockState log) {
		return log.hasProperty(RotatedPillarBlock.AXIS)
			? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
			: log;
	}

	private record HutKit(BlockState wall, BlockState log, BlockState slab, BlockState floor, BlockState stair, BlockState trap) {
	}

	private BlockPos buildPort(WorldGenLevel level, BoundingBox chunkBB) {
		int pierX0 = this.layoutWidth / 2 - (this.large ? 3 : 2);
		int pierX1 = this.layoutWidth / 2 + (this.large ? 3 : 2);
		int landEdge = this.landDepth - 2;
		int pierEnd = this.layoutDepth - 3;
		BlockState deck = Blocks.SPRUCE_PLANKS.defaultBlockState();
		BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState();
		BlockState piling = Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);

		for (int z = landEdge - 1; z <= pierEnd; z++) {
			for (int x = pierX0; x <= pierX1; x++) {
				boolean edge = x == pierX0 || x == pierX1;
				this.set(level, chunkBB, x, 0, z, edge ? piling : deck);
				if (edge) {
					this.fillDown(level, chunkBB, x, -1, z, piling);
					if (z > landEdge) {
						this.set(level, chunkBB, x, 1, z, Blocks.OAK_FENCE.defaultBlockState());
					}
				} else {
					this.set(level, chunkBB, x, 0, z, z % 2 == 0 ? deck : slab);
				}
			}
		}
		this.set(level, chunkBB, pierX0, 2, pierEnd, Blocks.LANTERN.defaultBlockState());
		this.set(level, chunkBB, pierX1, 2, pierEnd, Blocks.LANTERN.defaultBlockState());
		this.set(level, chunkBB, this.layoutWidth / 2, 1, landEdge, Blocks.BARREL.defaultBlockState());
		this.set(level, chunkBB, this.layoutWidth / 2 + 1, 1, landEdge, Blocks.CRAFTING_TABLE.defaultBlockState());
		placeStockedChest(level, chunkBB, this.layoutWidth / 2 - 1, 1, landEdge, Direction.SOUTH, RandomSource.create(this.originX * 31L + this.originZ));
		return this.localToWorld(this.layoutWidth / 2, 1, landEdge);
	}

	private void buildLighthouse(WorldGenLevel level, BoundingBox chunkBB) {
		int ox = 2;
		int oz = this.landDepth - 4;
		int h = 12;
		BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
		BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
		for (int y = 0; y <= h; y++) {
			for (int z = oz; z <= oz + 3; z++) {
				for (int x = ox; x <= ox + 3; x++) {
					boolean edge = x == ox || x == ox + 3 || z == oz || z == oz + 3;
					if (y == 0) {
						this.set(level, chunkBB, x, y, z, cobble);
						this.fillDown(level, chunkBB, x, -1, z, cobble);
					} else if (edge) {
						this.set(level, chunkBB, x, y, z, y % 3 == 0 ? cobble : brick);
					} else {
						this.set(level, chunkBB, x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
			this.set(level, chunkBB, ox + 1, y, oz + 1, Blocks.LADDER.defaultBlockState()
				.setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		for (int z = oz; z <= oz + 3; z++) {
			for (int x = ox; x <= ox + 3; x++) {
				this.set(level, chunkBB, x, h + 1, z, Blocks.STONE_BRICK_SLAB.defaultBlockState());
			}
		}
		this.set(level, chunkBB, ox + 1, h + 1, oz + 1, Blocks.AIR.defaultBlockState());
		this.set(level, chunkBB, ox + 2, h + 1, oz + 1, Blocks.AIR.defaultBlockState());
		this.set(level, chunkBB, ox + 1, h + 2, oz + 1, PeterwolfsBoatsAndShipsMod.LIGHTHOUSE_LIGHT.defaultBlockState());
		for (int[] c : new int[][]{{ox, oz}, {ox + 3, oz}, {ox, oz + 3}, {ox + 3, oz + 3}}) {
			this.set(level, chunkBB, c[0], h + 2, c[1], Blocks.COBBLESTONE_WALL.defaultBlockState());
		}
	}

	private void spawnWatermen(WorldGenLevel level, BoundingBox chunkBB, BlockPos port) {
		int[][] slots = this.large
			? new int[][]{{5, 12}, {13, 10}, {21, 12}, {29, 10}, {9, 18}, {25, 18}}
			: new int[][]{{5, 12}, {13, 10}, {21, 12}};
		for (int i = 0; i < slots.length && i < WATERMAN_SLOTS; i++) {
			trySpawnWaterman(level, chunkBB, i, slots[i][0], 1, slots[i][1], port);
		}
	}

	private void trySpawnWaterman(WorldGenLevel level, BoundingBox chunkBB, int slot, int x, int y, int z, BlockPos port) {
		if ((this.watermanSpawnMask & (1 << slot)) != 0) {
			return;
		}
		BlockPos pos = this.localToWorld(x, y, z);
		if (!chunkBB.isInside(pos)) {
			return;
		}
		WatermanEntity waterman = PeterwolfsBoatsAndShipsMod.WATERMAN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
		if (waterman == null) {
			return;
		}
		this.watermanSpawnMask |= 1 << slot;
		this.set(level, chunkBB, x, y, z, Blocks.AIR.defaultBlockState());
		this.set(level, chunkBB, x, y + 1, z, Blocks.AIR.defaultBlockState());
		waterman.setPersistenceRequired();
		waterman.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.waterDir.toYRot(), 0.0F);
		waterman.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
		waterman.setPortPos(port);
		level.addFreshEntityWithPassengers(waterman);
	}

	private void spawnBoats(WorldGenLevel level, BoundingBox chunkBB) {
		int pierEnd = this.layoutDepth - 5;
		int mid = this.layoutWidth / 2;
		trySpawnBoat(level, chunkBB, 0, mid - 5, pierEnd, () ->
			new RiverSkiffEntity(PeterwolfsBoatsAndShipsMod.RIVER_SKIFF, level.getLevel()));
		trySpawnBoat(level, chunkBB, 1, mid + 5, pierEnd - 1, () ->
			new ExplorerSloopEntity(PeterwolfsBoatsAndShipsMod.EXPLORER_SLOOP, level.getLevel()));
		if (this.large) {
			trySpawnBoat(level, chunkBB, 2, mid + 8, pierEnd + 1, () ->
				new MerchantSchoonerEntity(PeterwolfsBoatsAndShipsMod.MERCHANT_SCHOONER, level.getLevel()));
		}
	}

	private void trySpawnBoat(WorldGenLevel level, BoundingBox chunkBB, int slot, int x, int z, java.util.function.Supplier<AbstractShipEntity> factory) {
		if ((this.boatSpawnMask & (1 << slot)) != 0) {
			return;
		}
		BlockPos pos = this.localToWorld(x, 0, z);
		if (!chunkBB.isInside(pos)) {
			return;
		}
		int waterY = findWaterSurface(level, pos.getX(), pos.getZ());
		if (waterY == Integer.MIN_VALUE) {
			return;
		}
		this.boatSpawnMask |= 1 << slot;
		AbstractShipEntity ship = factory.get();
		ship.setPos(pos.getX() + 0.5D, waterY + 0.72D, pos.getZ() + 0.5D);
		ship.setYRot(this.waterDir.toYRot());
		level.addFreshEntity(ship);
	}

	private int findWaterSurface(WorldGenLevel level, int x, int z) {
		int top = this.boundingBox.maxY();
		int bottom = Math.max(level.getMinY() + 1, this.boundingBox.minY() - 8);
		for (int y = top; y >= bottom; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (level.getFluidState(pos).is(FluidTags.WATER) && !level.getFluidState(pos.above()).is(FluidTags.WATER)) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	private void placeBambooDoor(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, Direction facing) {
		BlockState lower = Blocks.BAMBOO_DOOR.defaultBlockState()
			.setValue(DoorBlock.FACING, facing)
			.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
			.setValue(DoorBlock.HINGE, DoorHingeSide.LEFT)
			.setValue(DoorBlock.OPEN, false);
		this.set(level, chunkBB, x, y, z, lower);
		this.set(level, chunkBB, x, y + 1, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
	}

	private void placeBed(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, Direction facing, DyeColor color) {
		BlockState foot = Blocks.BED.pick(color).defaultBlockState()
			.setValue(BedBlock.FACING, facing)
			.setValue(BedBlock.PART, BedPart.FOOT);
		BlockState head = Blocks.BED.pick(color).defaultBlockState()
			.setValue(BedBlock.FACING, facing)
			.setValue(BedBlock.PART, BedPart.HEAD);
		this.set(level, chunkBB, x, y, z, foot);
		this.set(level, chunkBB, x + facing.getStepX(), y, z + facing.getStepZ(), head);
	}

	private void placeStockedChest(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, Direction facing, RandomSource random) {
		this.set(level, chunkBB, x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
		BlockPos pos = this.localToWorld(x, y, z);
		if (!chunkBB.isInside(pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
			return;
		}
		ItemStack[] loot = {
			new ItemStack(Items.COD, 4 + random.nextInt(5)),
			new ItemStack(Items.SALMON, 2 + random.nextInt(4)),
			new ItemStack(Items.FISHING_ROD),
			new ItemStack(Items.STRING, 3 + random.nextInt(4)),
			new ItemStack(Items.BAMBOO, 4 + random.nextInt(8)),
			new ItemStack(Items.OAK_PLANKS, 4 + random.nextInt(8)),
			new ItemStack(Items.EMERALD, 1 + random.nextInt(3)),
			new ItemStack(Items.KELP, 3 + random.nextInt(6))
		};
		int stacks = 3 + random.nextInt(4);
		for (int i = 0; i < stacks; i++) {
			chest.setItem(random.nextInt(chest.getContainerSize()), loot[random.nextInt(loot.length)].copy());
		}
	}

	private boolean isWaterColumn(WorldGenLevel level, int x, int z) {
		BlockPos atFloor = this.localToWorld(x, 0, z);
		for (int dy = -4; dy <= 1; dy++) {
			if (level.getFluidState(atFloor.offset(0, dy, 0)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private void set(WorldGenLevel level, BoundingBox chunkBB, int x, int y, int z, BlockState state) {
		BlockPos pos = this.localToWorld(x, y, z);
		if (chunkBB.isInside(pos)) {
			level.setBlock(pos, this.reorient(state), 2);
		}
	}

	private void fillDown(WorldGenLevel level, BoundingBox chunkBB, int x, int startY, int z, BlockState state) {
		BlockPos.MutableBlockPos pos = this.localToWorld(x, startY, z).mutable();
		if (!chunkBB.isInside(pos)) {
			return;
		}
		while (pos.getY() > level.getMinY() + 1) {
			BlockState existing = level.getBlockState(pos);
			if (!existing.isAir() && !existing.liquid()) {
				break;
			}
			level.setBlock(pos, state, 2);
			pos.move(Direction.DOWN);
		}
	}

	private BlockState reorient(BlockState state) {
		Rotation rot = switch (this.waterDir) {
			case EAST -> Rotation.CLOCKWISE_90;
			case NORTH -> Rotation.CLOCKWISE_180;
			case WEST -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		return rot == Rotation.NONE ? state : state.rotate(rot);
	}

	private BlockPos localToWorld(int x, int y, int z) {
		int worldY = this.boundingBox.minY() + y;
		return switch (this.waterDir) {
			case NORTH -> new BlockPos(this.originX + x, worldY, this.originZ - z);
			case EAST -> new BlockPos(this.originX + z, worldY, this.originZ + x);
			case WEST -> new BlockPos(this.originX - z, worldY, this.originZ + x);
			default -> new BlockPos(this.originX + x, worldY, this.originZ + z);
		};
	}

	private static int originX(WatermanSettlementStructure.ShoreSite shore, boolean large) {
		int width = large ? LARGE_WIDTH : SMALL_WIDTH;
		int landDepth = large ? LARGE_LAND : SMALL_LAND;
		return switch (shore.waterDir()) {
			case EAST -> shore.shoreX() - landDepth + 1;
			case WEST -> shore.shoreX() + landDepth - 1;
			default -> shore.shoreX() - width / 2;
		};
	}

	private static int originZ(WatermanSettlementStructure.ShoreSite shore, boolean large) {
		int width = large ? LARGE_WIDTH : SMALL_WIDTH;
		int landDepth = large ? LARGE_LAND : SMALL_LAND;
		return switch (shore.waterDir()) {
			case SOUTH -> shore.shoreZ() - landDepth + 1;
			case NORTH -> shore.shoreZ() + landDepth - 1;
			default -> shore.shoreZ() - width / 2;
		};
	}

	private static BoundingBox boundingBoxFor(boolean large, Direction waterDir, int originX, int originZ, int floorY) {
		int width = large ? LARGE_WIDTH : SMALL_WIDTH;
		int depth = large ? LARGE_DEPTH : SMALL_DEPTH;
		int height = large ? LARGE_HEIGHT : SMALL_HEIGHT;
		int minX;
		int maxX;
		int minZ;
		int maxZ;
		switch (waterDir) {
			case NORTH -> {
				minX = originX;
				maxX = originX + width - 1;
				minZ = originZ - depth + 1;
				maxZ = originZ;
			}
			case EAST -> {
				minX = originX;
				maxX = originX + depth - 1;
				minZ = originZ;
				maxZ = originZ + width - 1;
			}
			case WEST -> {
				minX = originX - depth + 1;
				maxX = originX;
				minZ = originZ;
				maxZ = originZ + width - 1;
			}
			default -> {
				minX = originX;
				maxX = originX + width - 1;
				minZ = originZ;
				maxZ = originZ + depth - 1;
			}
		}
		return new BoundingBox(minX, floorY - 4, minZ, maxX, floorY + height, maxZ);
	}
}
