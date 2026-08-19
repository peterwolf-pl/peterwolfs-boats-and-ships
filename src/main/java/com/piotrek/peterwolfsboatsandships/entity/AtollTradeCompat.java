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

	/**
	 * Spreads a shared port-facing berth along the atoll so several watermen do
	 * not all aim at the same block. {@code salt} is stable per villager.
	 */
	static Vec3 spreadBerth(Vec3 baseBerth, BlockPos atollCenter, int salt) {
		double centerX = atollCenter.getX() + 0.5D;
		double centerZ = atollCenter.getZ() + 0.5D;
		double dx = baseBerth.x - centerX;
		double dz = baseBerth.z - centerZ;
		double radius = Math.sqrt(dx * dx + dz * dz);
		if (radius < 0.001D) {
			return baseBerth;
		}
		double angle = Math.atan2(dz, dx);
		int slot = Math.floorMod(salt, 11) - 5;
		int ring = Math.floorMod(salt / 11, 3) - 1;
		double spreadRadius = Math.max(32.0D, radius + ring * 7.0D);
		double spreadAngle = angle + Math.toRadians(slot * 11.0D);
		return new Vec3(
			centerX + Math.cos(spreadAngle) * spreadRadius,
			baseBerth.y,
			centerZ + Math.sin(spreadAngle) * spreadRadius
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
