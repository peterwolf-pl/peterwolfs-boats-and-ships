package com.piotrek.peterwolfsboatsandships.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Small bounded water-surface pathfinder used by NPC helmsmen.
 *
 * <p>Vanilla ground navigation cannot describe the footprint of a wide ship.
 * Every accepted cell therefore has to contain water below the complete hull,
 * leave its collision box unobstructed and remain reachable without cutting a
 * diagonal across a bank. The resulting grid route is smoothed into long,
 * natural steering legs before it is handed to the ship controls.
 */
final class WaterRoutePlanner {
	private static final int MAX_SEARCH_DISTANCE = 48;
	private static final int MAX_EXPANDED_CELLS = 12000;
	private static final int MAX_SMOOTHING_LEG = 16;
	private static final double SQRT_TWO = Math.sqrt(2.0D);
	private static final Direction[] DIRECTIONS = {
		new Direction(1, 0, 1.0D),
		new Direction(-1, 0, 1.0D),
		new Direction(0, 1, 1.0D),
		new Direction(0, -1, 1.0D),
		new Direction(1, 1, SQRT_TWO),
		new Direction(1, -1, SQRT_TWO),
		new Direction(-1, 1, SQRT_TWO),
		new Direction(-1, -1, SQRT_TWO)
	};

	private WaterRoutePlanner() {
	}

	@Nullable
	static Route plan(ServerLevel level, AbstractShipEntity ship, Vec3 start, BlockPos requestedDestination) {
		int referenceY = ship.blockPosition().getY();
		Cell startCell = new Cell(BlockPos.containing(start).getX(), BlockPos.containing(start).getZ());
		Map<Cell, Boolean> navigationCache = new HashMap<>();
		Cell navigableStart = findClosestNavigable(level, ship, startCell, referenceY, 3, navigationCache);
		Cell requestedCell = new Cell(requestedDestination.getX(), requestedDestination.getZ());
		Cell destination = findClosestNavigable(level, ship, requestedCell, referenceY, 4, navigationCache);
		if (navigableStart == null || destination == null
			|| Math.abs(destination.x - navigableStart.x) > MAX_SEARCH_DISTANCE
			|| Math.abs(destination.z - navigableStart.z) > MAX_SEARCH_DISTANCE) {
			return null;
		}

		PriorityQueue<SearchNode> open = new PriorityQueue<>(Comparator.comparingDouble(SearchNode::estimatedTotalCost));
		Map<Cell, Cell> cameFrom = new HashMap<>();
		Map<Cell, Double> costs = new HashMap<>();
		costs.put(navigableStart, 0.0D);
		open.add(new SearchNode(navigableStart, heuristic(navigableStart, destination)));

		int expanded = 0;
		while (!open.isEmpty() && expanded++ < MAX_EXPANDED_CELLS) {
			SearchNode currentNode = open.poll();
			Cell current = currentNode.cell;
			double currentCost = costs.getOrDefault(current, Double.POSITIVE_INFINITY);
			if (current.equals(destination)) {
				List<Cell> rawCells = reconstruct(cameFrom, navigableStart, destination);
				List<Vec3> waypoints = smooth(level, ship, rawCells, referenceY, navigationCache);
				Vec3 actualDestination = new Vec3(destination.x + 0.5D, ship.getY(), destination.z + 0.5D);
				return new Route(actualDestination, waypoints);
			}

			for (Direction direction : DIRECTIONS) {
				Cell next = new Cell(current.x + direction.dx, current.z + direction.dz);
				if (!insideSearchArea(navigableStart, next)
					|| !isNavigable(level, ship, next, referenceY, navigationCache)) {
					continue;
				}
				if (direction.dx != 0 && direction.dz != 0) {
					Cell acrossX = new Cell(current.x + direction.dx, current.z);
					Cell acrossZ = new Cell(current.x, current.z + direction.dz);
					if (!isNavigable(level, ship, acrossX, referenceY, navigationCache)
						|| !isNavigable(level, ship, acrossZ, referenceY, navigationCache)) {
						continue;
					}
				}

				double tentativeCost = currentCost + direction.cost + shorePenalty(level, ship, next, referenceY);
				if (tentativeCost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
					continue;
				}
				cameFrom.put(next, current);
				costs.put(next, tentativeCost);
				open.add(new SearchNode(next, tentativeCost + heuristic(next, destination)));
			}
		}
		return null;
	}

	static boolean isSegmentNavigable(ServerLevel level, AbstractShipEntity ship, Vec3 from, Vec3 to) {
		int referenceY = ship.blockPosition().getY();
		Map<Cell, Boolean> cache = new HashMap<>();
		double distance = Math.sqrt(horizontalDistanceSqr(from, to));
		int samples = Math.max(1, (int)Math.ceil(distance * 2.0D));
		for (int sample = 0; sample <= samples; sample++) {
			double progress = sample / (double)samples;
			Cell cell = new Cell(
				BlockPos.containing(from.x + (to.x - from.x) * progress, 0.0D, 0.0D).getX(),
				BlockPos.containing(0.0D, 0.0D, from.z + (to.z - from.z) * progress).getZ()
			);
			if (!isNavigable(level, ship, cell, referenceY, cache)) {
				return false;
			}
		}
		return true;
	}

	private static List<Cell> reconstruct(Map<Cell, Cell> cameFrom, Cell start, Cell destination) {
		List<Cell> cells = new ArrayList<>();
		Cell current = destination;
		cells.add(current);
		while (!current.equals(start)) {
			current = cameFrom.get(current);
			if (current == null) {
				return List.of();
			}
			cells.add(current);
		}
		Collections.reverse(cells);
		return cells;
	}

	private static List<Vec3> smooth(
		ServerLevel level,
		AbstractShipEntity ship,
		List<Cell> rawCells,
		int referenceY,
		Map<Cell, Boolean> navigationCache
	) {
		if (rawCells.isEmpty()) {
			return List.of();
		}
		if (rawCells.size() == 1) {
			Cell only = rawCells.get(0);
			return List.of(new Vec3(only.x + 0.5D, ship.getY(), only.z + 0.5D));
		}
		List<Vec3> result = new ArrayList<>();
		int anchor = 0;
		while (anchor < rawCells.size() - 1) {
			int farthest = Math.min(rawCells.size() - 1, anchor + MAX_SMOOTHING_LEG);
			while (farthest > anchor + 1
				&& !isGridSegmentNavigable(level, ship, rawCells.get(anchor), rawCells.get(farthest), referenceY, navigationCache)) {
				farthest--;
			}
			Cell waypoint = rawCells.get(farthest);
			result.add(new Vec3(waypoint.x + 0.5D, ship.getY(), waypoint.z + 0.5D));
			anchor = farthest;
		}
		return List.copyOf(result);
	}

	private static boolean isGridSegmentNavigable(
		ServerLevel level,
		AbstractShipEntity ship,
		Cell from,
		Cell to,
		int referenceY,
		Map<Cell, Boolean> navigationCache
	) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		int samples = Math.max(1, (int)Math.ceil(Math.sqrt(dx * dx + dz * dz) * 2.0D));
		for (int sample = 0; sample <= samples; sample++) {
			double progress = sample / (double)samples;
			Cell cell = new Cell(
				(int)Math.floor(from.x + 0.5D + dx * progress),
				(int)Math.floor(from.z + 0.5D + dz * progress)
			);
			if (!isNavigable(level, ship, cell, referenceY, navigationCache)) {
				return false;
			}
		}
		return true;
	}

	@Nullable
	private static Cell findClosestNavigable(
		ServerLevel level,
		AbstractShipEntity ship,
		Cell center,
		int referenceY,
		int maxRadius,
		Map<Cell, Boolean> navigationCache
	) {
		for (int radius = 0; radius <= maxRadius; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
						continue;
					}
					Cell candidate = new Cell(center.x + dx, center.z + dz);
					if (isNavigable(level, ship, candidate, referenceY, navigationCache)) {
						return candidate;
					}
				}
			}
		}
		return null;
	}

	private static boolean isNavigable(
		ServerLevel level,
		AbstractShipEntity ship,
		Cell cell,
		int referenceY,
		Map<Cell, Boolean> navigationCache
	) {
		return navigationCache.computeIfAbsent(cell, ignored -> isNavigableUncached(level, ship, cell, referenceY));
	}

	private static boolean isNavigableUncached(ServerLevel level, AbstractShipEntity ship, Cell cell, int referenceY) {
		// Unloaded ocean is treated as open water. Voyage tickets only keep a
		// small window loaded, so treating air-in-unloaded-chunks as land made
		// every long atoll leg look blocked and the helmsman spun in place.
		if (!level.hasChunk(cell.x >> 4, cell.z >> 4)) {
			return true;
		}
		double centerX = cell.x + 0.5D;
		double centerZ = cell.z + 0.5D;
		double hullClearance = ship.getBbWidth() * 0.5D + 0.2D;
		int minX = (int)Math.floor(centerX - hullClearance);
		int maxX = (int)Math.floor(centerX + hullClearance);
		int minZ = (int)Math.floor(centerZ - hullClearance);
		int maxZ = (int)Math.floor(centerZ + hullClearance);
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				if (!hasWaterSurface(level, x, z, referenceY)) {
					return false;
				}
			}
		}

		AABB candidateBox = ship.getBoundingBox()
			.move(centerX - ship.getX(), 0.0D, centerZ - ship.getZ())
			.inflate(0.15D, 0.05D, 0.15D);
		return level.noBlockCollision(ship, candidateBox);
	}

	private static boolean hasWaterSurface(ServerLevel level, int x, int z, int referenceY) {
		for (int y = referenceY + 1; y >= referenceY - 2; y--) {
			if (level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	private static double shorePenalty(ServerLevel level, AbstractShipEntity ship, Cell cell, int referenceY) {
		double extraClearance = ship.getBbWidth() * 0.5D + 1.35D;
		int[][] probes = {
			{(int)Math.ceil(extraClearance), 0},
			{-(int)Math.ceil(extraClearance), 0},
			{0, (int)Math.ceil(extraClearance)},
			{0, -(int)Math.ceil(extraClearance)}
		};
		double penalty = 0.0D;
		for (int[] probe : probes) {
			if (!hasWaterSurface(level, cell.x + probe[0], cell.z + probe[1], referenceY)) {
				penalty += 0.22D;
			}
		}
		return penalty;
	}

	private static boolean insideSearchArea(Cell origin, Cell candidate) {
		return Math.abs(candidate.x - origin.x) <= MAX_SEARCH_DISTANCE
			&& Math.abs(candidate.z - origin.z) <= MAX_SEARCH_DISTANCE;
	}

	private static double heuristic(Cell from, Cell to) {
		int dx = Math.abs(from.x - to.x);
		int dz = Math.abs(from.z - to.z);
		return Math.max(dx, dz) + (SQRT_TWO - 1.0D) * Math.min(dx, dz);
	}

	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double dx = first.x - second.x;
		double dz = first.z - second.z;
		return dx * dx + dz * dz;
	}

	record Route(Vec3 destination, List<Vec3> waypoints) {
		Route {
			waypoints = List.copyOf(waypoints);
		}
	}

	private record Cell(int x, int z) {
	}

	private record Direction(int dx, int dz, double cost) {
	}

	private record SearchNode(Cell cell, double estimatedTotalCost) {
	}
}
