package com.piotrek.peterwolfsboatsandships.client;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import com.piotrek.peterwolfsboatsandships.network.ShipInputPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.Identifier;

public final class PeterwolfsBoatsAndShipsClient implements ClientModInitializer {
	private static final ModelLayerLocation RIVER_SKIFF_LAYER = layer("river_skiff");
	private static final ModelLayerLocation EXPLORER_SLOOP_LAYER = layer("explorer_sloop");
	private static final ModelLayerLocation MERCHANT_SCHOONER_LAYER = layer("merchant_schooner");

	/** Window (ms) between key release and next press that counts as a double-tap. */
	private static final long DOUBLE_TAP_MS = 300L;
	/** Forward thrust while double-tap W boost is active. */
	private static final float BOOST_THRUST = 1.75F;
	/** Rudder magnitude while double-tap A/D sharp-turn is active. */
	private static final float BOOST_RUDDER = 2.5F;

	private static boolean wasForward;
	private static boolean wasLeft;
	private static boolean wasRight;
	private static long lastForwardReleaseMs;
	private static long lastLeftReleaseMs;
	private static long lastRightReleaseMs;
	private static boolean forwardBoost;
	private static boolean leftBoost;
	private static boolean rightBoost;

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(PeterwolfsBoatsAndShipsMod.RIVER_SKIFF, context -> new ShipRenderer<>(context, RIVER_SKIFF_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/river_skiff.png"), 0.9F));
		EntityRendererRegistry.register(PeterwolfsBoatsAndShipsMod.EXPLORER_SLOOP, context -> new ShipRenderer<>(context, EXPLORER_SLOOP_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/explorer_sloop.png"), 1.25F));
		EntityRendererRegistry.register(PeterwolfsBoatsAndShipsMod.MERCHANT_SCHOONER, context -> new ShipRenderer<>(context, MERCHANT_SCHOONER_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/merchant_schooner.png"), 1.75F));
		ModelLayerRegistry.registerModelLayer(RIVER_SKIFF_LAYER, ShipModel::createRiverSkiffLayer);
		ModelLayerRegistry.registerModelLayer(EXPLORER_SLOOP_LAYER, ShipModel::createExplorerSloopLayer);
		ModelLayerRegistry.registerModelLayer(MERCHANT_SCHOONER_LAYER, ShipModel::createMerchantSchoonerLayer);
		BlockEntityRenderers.register(PeterwolfsBoatsAndShipsMod.LIGHTHOUSE_LIGHT_BLOCK_ENTITY, LighthouseLightRenderer::new);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && client.player.getVehicle() instanceof AbstractShipEntity) {
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
				ClientPlayNetworking.send(new ShipInputPayload(thrust, rudder));
			} else {
				// Reset double-tap state when not captaining a ship.
				wasForward = wasLeft = wasRight = false;
				forwardBoost = leftBoost = rightBoost = false;
			}
		});
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

	private static ModelLayerLocation layer(String path) { return new ModelLayerLocation(Identifier.fromNamespaceAndPath(PeterwolfsBoatsAndShipsMod.MOD_ID, path), "main"); }
}
