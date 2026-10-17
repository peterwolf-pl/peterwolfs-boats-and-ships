package com.piotrek.peterwolfsboatsandships.entity;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import org.jspecify.annotations.Nullable;

/** Connected surface water must span at least 10×10 before watermen treat it as a fishing lake. */
public final class WaterBodies {
	public static final int MIN_SPAN = 10;
	private static final int MAX_VISIT = 256;
	private static final ConcurrentHashMap<Long, Boolean> CACHE = new ConcurrentHashMap<>();

	private WaterBodies() {
	}

	public static boolean isLargeEnough(LevelReader level, BlockPos pos) {
		BlockPos water = resolveWater(level, pos);
		if (water == null) {
			return false;
		}
		long key = water.asLong();
		Boolean cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		boolean large = measure(level, water);
		if (CACHE.size() > 4096) {
			CACHE.clear();
		}
		CACHE.put(key, large);
		return large;
	}

	private static @Nullable BlockPos resolveWater(LevelReader level, BlockPos pos) {
		if (isWater(level, pos)) {
			return pos;
		}
		if (isWater(level, pos.below())) {
			return pos.below();
		}
		if (isWater(level, pos.above())) {
			return pos.above();
		}
		return null;
	}

	private static boolean measure(LevelReader level, BlockPos start) {
		Set<Long> seen = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start.asLong());
		int minX = start.getX();
		int maxX = start.getX();
		int minZ = start.getZ();
		int maxZ = start.getZ();
		int visited = 0;
		while (!queue.isEmpty() && visited < MAX_VISIT) {
			BlockPos cur = queue.removeFirst();
			visited++;
			minX = Math.min(minX, cur.getX());
			maxX = Math.max(maxX, cur.getX());
			minZ = Math.min(minZ, cur.getZ());
			maxZ = Math.max(maxZ, cur.getZ());
			if (maxX - minX + 1 >= MIN_SPAN && maxZ - minZ + 1 >= MIN_SPAN) {
				return true;
			}
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos next = cur.relative(dir);
				if (!level.hasChunkAt(next) || !seen.add(next.asLong()) || !isWater(level, next)) {
					continue;
				}
				queue.addLast(next);
			}
		}
		return maxX - minX + 1 >= MIN_SPAN && maxZ - minZ + 1 >= MIN_SPAN;
	}

	private static boolean isWater(LevelReader level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.WATER);
	}
}
