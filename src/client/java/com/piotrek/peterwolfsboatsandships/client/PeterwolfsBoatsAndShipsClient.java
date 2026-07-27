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
import net.minecraft.resources.Identifier;

public final class PeterwolfsBoatsAndShipsClient implements ClientModInitializer {
	private static final ModelLayerLocation RIVER_SKIFF_LAYER = layer("river_skiff");
	private static final ModelLayerLocation EXPLORER_SLOOP_LAYER = layer("explorer_sloop");
	private static final ModelLayerLocation MERCHANT_SCHOONER_LAYER = layer("merchant_schooner");

	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(PeterwolfsBoatsAndShipsMod.RIVER_SKIFF, context -> new ShipRenderer<>(context, RIVER_SKIFF_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/river_skiff.png"), 0.9F));
		EntityRendererRegistry.register(PeterwolfsBoatsAndShipsMod.EXPLORER_SLOOP, context -> new ShipRenderer<>(context, EXPLORER_SLOOP_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/explorer_sloop.png"), 1.25F));
		EntityRendererRegistry.register(PeterwolfsBoatsAndShipsMod.MERCHANT_SCHOONER, context -> new ShipRenderer<>(context, MERCHANT_SCHOONER_LAYER, PeterwolfsBoatsAndShipsMod.id("textures/entity/merchant_schooner.png"), 1.75F));
		ModelLayerRegistry.registerModelLayer(RIVER_SKIFF_LAYER, ShipModel::createRiverSkiffLayer);
		ModelLayerRegistry.registerModelLayer(EXPLORER_SLOOP_LAYER, ShipModel::createExplorerSloopLayer);
		ModelLayerRegistry.registerModelLayer(MERCHANT_SCHOONER_LAYER, ShipModel::createMerchantSchoonerLayer);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player != null && client.player.getVehicle() instanceof AbstractShipEntity) {
				float thrust = client.options.keyUp.isDown() ? 1.0F : client.options.keyDown.isDown() ? -0.55F : 0.0F;
				float rudder = client.options.keyLeft.isDown() ? -1.0F : client.options.keyRight.isDown() ? 1.0F : 0.0F;
				// Send every client tick. This avoids a stale input state after a
				// mount/dismount sync and preserves server authority over movement.
				ClientPlayNetworking.send(new ShipInputPayload(thrust, rudder));
			}
		});
	}

	private static ModelLayerLocation layer(String path) { return new ModelLayerLocation(Identifier.fromNamespaceAndPath(PeterwolfsBoatsAndShipsMod.MOD_ID, path), "main"); }
}
