package com.piotrek.peterwolfsboatsandships.client;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import com.piotrek.peterwolfsboatsandships.network.ShipInputPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = PeterwolfsBoatsAndShipsMod.MOD_ID, dist = Dist.CLIENT)
public final class PeterwolfsBoatsAndShipsClient {
	private static final ModelLayerLocation RIVER_SKIFF_LAYER = layer("river_skiff");
	private static final ModelLayerLocation EXPLORER_SLOOP_LAYER = layer("explorer_sloop");
	private static final ModelLayerLocation MERCHANT_SCHOONER_LAYER = layer("merchant_schooner");

	/** Window (ms) between key release and next press that counts as a double-tap. */
	private static final long DOUBLE_TAP_MS = 300L;
	/** Forward thrust while double-tap W boost is active. */
	private static final float BOOST_THRUST = 1.75F;
	/** Rudder magnitude while double-tap A/D sharp-turn is active. */
	private static final float BOOST_RUDDER = 2.5F;
	/**
	 * Extra third-person camera distance (blocks) at full W speed boost.
	 * Vanilla base {@link Attributes#CAMERA_DISTANCE} is 4.0.
	 */
	private static final float BOOST_CAMERA_EXTRA = 3.75F;
	/** Ease-in/out rate for the boost camera pull each client tick. */
	private static final float CAMERA_ZOOM_LERP = 0.2F;
	private static final Identifier BOOST_CAMERA_MODIFIER_ID = PeterwolfsBoatsAndShipsMod.id("speed_boost_camera");

	private static boolean wasForward;
	private static boolean wasLeft;
	private static boolean wasRight;
	private static long lastForwardReleaseMs;
	private static long lastLeftReleaseMs;
	private static long lastRightReleaseMs;
	private static boolean forwardBoost;
	private static boolean leftBoost;
	private static boolean rightBoost;
	/** Smoothed extra camera distance currently applied (0 … {@link #BOOST_CAMERA_EXTRA}). */
	private static float boostCameraPull;

	/**
	 * 0–1 how strongly the W speed-boost camera effect is applied (distance + FOV).
	 * Used by the FOV mixin for first-person feedback.
	 */
	public static float boostCameraIntensity() {
		return Mth.clamp(boostCameraPull / BOOST_CAMERA_EXTRA, 0.0F, 1.0F);
	}

	public PeterwolfsBoatsAndShipsClient(IEventBus modBus) {
		modBus.addListener(PeterwolfsBoatsAndShipsClient::registerRenderers);
		modBus.addListener(PeterwolfsBoatsAndShipsClient::registerLayerDefinitions);
		NeoForge.EVENT_BUS.addListener(PeterwolfsBoatsAndShipsClient::onClientTick);
	}

	private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
		event.registerEntityRenderer(
			PeterwolfsBoatsAndShipsMod.RIVER_SKIFF.get(),
			context -> new ShipRenderer<>(context, RIVER_SKIFF_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/river_skiff.png"), 0.9F)
		);
		event.registerEntityRenderer(
			PeterwolfsBoatsAndShipsMod.EXPLORER_SLOOP.get(),
			context -> new ShipRenderer<>(context, EXPLORER_SLOOP_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/explorer_sloop.png"), 1.25F)
		);
		event.registerEntityRenderer(
			PeterwolfsBoatsAndShipsMod.MERCHANT_SCHOONER.get(),
			context -> new ShipRenderer<>(context, MERCHANT_SCHOONER_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/merchant_schooner.png"), 1.75F)
		);
		event.registerEntityRenderer(PeterwolfsBoatsAndShipsMod.WATERMAN.get(), VillagerRenderer::new);
		event.registerBlockEntityRenderer(PeterwolfsBoatsAndShipsMod.LIGHTHOUSE_LIGHT_BLOCK_ENTITY.get(), LighthouseLightRenderer::new);
	}

	private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
		event.registerLayerDefinition(RIVER_SKIFF_LAYER, ShipModel::createRiverSkiffLayer);
		event.registerLayerDefinition(EXPLORER_SLOOP_LAYER, ShipModel::createExplorerSloopLayer);
		event.registerLayerDefinition(MERCHANT_SCHOONER_LAYER, ShipModel::createMerchantSchoonerLayer);
	}

	private static void onClientTick(ClientTickEvent.Post event) {
		Minecraft client = Minecraft.getInstance();
		// Only the helmsman (right-click claim) may send steering input.
		if (client.player != null
			&& client.player.getVehicle() instanceof AbstractShipEntity ship
			&& ship.isHelmsman(client.player)) {
			long now = System.currentTimeMillis();
			boolean forward = client.options.keyUp.isDown();
			boolean back = client.options.keyDown.isDown();
			boolean left = client.options.keyLeft.isDown();
			boolean right = client.options.keyRight.isDown();

			forwardBoost = updateDoubleTapBoost(forward, wasForward, forwardBoost, lastForwardReleaseMs, now);
			if (!forward && wasForward) {
				lastForwardReleaseMs = now;
			}
			leftBoost = updateDoubleTapBoost(left, wasLeft, leftBoost, lastLeftReleaseMs, now);
			if (!left && wasLeft) {
				lastLeftReleaseMs = now;
			}
			rightBoost = updateDoubleTapBoost(right, wasRight, rightBoost, lastRightReleaseMs, now);
			if (!right && wasRight) {
				lastRightReleaseMs = now;
			}

			wasForward = forward;
			wasLeft = left;
			wasRight = right;

			float thrust = forward ? (forwardBoost ? BOOST_THRUST : 1.0F) : back ? -0.55F : 0.0F;
			float rudder = 0.0F;
			if (left && !right) {
				rudder = leftBoost ? -BOOST_RUDDER : -1.0F;
			} else if (right && !left) {
				rudder = rightBoost ? BOOST_RUDDER : 1.0F;
			}
			// Send every client tick. This avoids a stale input state after a
			// mount/dismount sync and preserves server authority over movement.
			ClientPacketDistributor.sendToServer(new ShipInputPayload(thrust, rudder));

			// Double-tap W: ease the third-person camera back for a speed feel.
			tickBoostCamera(client.player, forward && forwardBoost);
		} else {
			// Reset double-tap state when not captaining a ship.
			wasForward = wasLeft = wasRight = false;
			forwardBoost = leftBoost = rightBoost = false;
			if (client.player != null) {
				tickBoostCamera(client.player, false);
			} else {
				boostCameraPull = 0.0F;
			}
		}
	}

	/**
	 * Activates boost when the key is pressed again within {@link #DOUBLE_TAP_MS}
	 * of the previous release. Boost lasts only while the key stays held.
	 */
	private static boolean updateDoubleTapBoost(boolean down, boolean wasDown, boolean currentlyBoosted, long lastReleaseMs, long now) {
		if (!down) {
			return false;
		}
		if (!wasDown && now - lastReleaseMs <= DOUBLE_TAP_MS) {
			return true;
		}
		return currentlyBoosted;
	}

	/**
	 * Smoothly pulls the third-person camera farther out while the W speed boost
	 * is held, using a transient {@link Attributes#CAMERA_DISTANCE} modifier.
	 */
	private static void tickBoostCamera(LocalPlayer player, boolean boosting) {
		float target = boosting ? BOOST_CAMERA_EXTRA : 0.0F;
		boostCameraPull = Mth.lerp(CAMERA_ZOOM_LERP, boostCameraPull, target);
		if (boostCameraPull < 0.02F) {
			boostCameraPull = 0.0F;
		}

		AttributeInstance cameraDistance = player.getAttribute(Attributes.CAMERA_DISTANCE);
		if (cameraDistance == null) {
			return;
		}
		if (boostCameraPull > 0.0F) {
			cameraDistance.addOrUpdateTransientModifier(new AttributeModifier(
				BOOST_CAMERA_MODIFIER_ID,
				boostCameraPull,
				AttributeModifier.Operation.ADD_VALUE
			));
		} else {
			cameraDistance.removeModifier(BOOST_CAMERA_MODIFIER_ID);
		}
	}

	private static ModelLayerLocation layer(String path) {
		return new ModelLayerLocation(Identifier.fromNamespaceAndPath(PeterwolfsBoatsAndShipsMod.MOD_ID, path), "main");
	}
}
