package com.piotrek.peterwolfsboatsandships.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Optional bridge to Water World - The Atoll without linking to its Java classes. */
final class AtollTradeCompat {
	static final String ATOLL_MOD_ID = "water_world_atoll";
	private static final int STRUCTURE_SEARCH_RADIUS_CHUNKS = 48;
	private static final long SEARCH_CACHE_TICKS = 6000L;
	private static final double SAFE_BERTH_RADIUS = 56.0D;
	private static final TagKey<Structure> TRADE_ATOLLS = TagKey.create(
		Registries.STRUCTURE,
		Identifier.fromNamespaceAndPath("peterwolfs_boats_and_ships", "waterman_trade_atolls")
	);
	private static final Map<ServerLevel, Map<Long, CachedSearch>> SEARCH_CACHE = new WeakHashMap<>();

	private AtollTradeCompat() {
	}

	static boolean isAvailable() {
		return FabricLoader.getInstance().isModLoaded(ATOLL_MOD_ID);
	}

	/**
	 * Locates the nearest atoll structure only once per broad port region and five
	 * minutes. Structure-set searching is deliberately not repeated by every
	 * waterman on every ordinary harbour trip.
	 */
	@Nullable
	static BlockPos findNearestAtoll(ServerLevel level, BlockPos port) {
		if (!isAvailable()) {
			return null;
		}
		long cacheKey = cacheKey(port);
		Map<Long, CachedSearch> levelCache = SEARCH_CACHE.computeIfAbsent(level, ignored -> new HashMap<>());
		CachedSearch cached = levelCache.get(cacheKey);
		if (cached != null && cached.expiresAt > level.getGameTime()) {
			return cached.center;
		}

		BlockPos center = level.findNearestMapStructure(
			TRADE_ATOLLS,
			port,
			STRUCTURE_SEARCH_RADIUS_CHUNKS,
			false
		);
		if (center != null) {
			center = new BlockPos(center.getX(), port.getY(), center.getZ());
		}
		levelCache.put(cacheKey, new CachedSearch(level.getGameTime() + SEARCH_CACHE_TICKS, center));
		return center;
	}

	/** Selects open ocean on the port-facing side of the atoll's outer wall. */
	static Vec3 initialBerth(BlockPos atollCenter, Vec3 portWater) {
		double dx = portWater.x - (atollCenter.getX() + 0.5D);
		double dz = portWater.z - (atollCenter.getZ() + 0.5D);
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 0.001D) {
			dx = 0.0D;
			dz = -1.0D;
			length = 1.0D;
		}
		return new Vec3(
			atollCenter.getX() + 0.5D + dx / length * SAFE_BERTH_RADIUS,
			portWater.y,
			atollCenter.getZ() + 0.5D + dz / length * SAFE_BERTH_RADIUS
		);
	}

	private static long cacheKey(BlockPos port) {
		int regionX = port.getX() >> 9;
		int regionZ = port.getZ() >> 9;
		return (long)regionX << 32 ^ regionZ & 0xFFFFFFFFL;
	}

	private record CachedSearch(long expiresAt, @Nullable BlockPos center) {
	}
}
