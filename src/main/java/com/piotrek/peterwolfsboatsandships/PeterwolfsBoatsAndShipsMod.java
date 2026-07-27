package com.piotrek.peterwolfsboatsandships;

import com.piotrek.peterwolfsboatsandships.entity.ExplorerSloopEntity;
import com.piotrek.peterwolfsboatsandships.entity.MerchantSchoonerEntity;
import com.piotrek.peterwolfsboatsandships.entity.RiverSkiffEntity;
import com.piotrek.peterwolfsboatsandships.item.ShipItem;
import com.piotrek.peterwolfsboatsandships.network.ShipInputPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PeterwolfsBoatsAndShipsMod implements ModInitializer {
	public static final String MOD_ID = "peterwolfs_boats_and_ships";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final ResourceKey<EntityType<?>> RIVER_SKIFF_KEY = entityKey("river_skiff");
	public static final EntityType<RiverSkiffEntity> RIVER_SKIFF = Registry.register(BuiltInRegistries.ENTITY_TYPE, RIVER_SKIFF_KEY,
		EntityType.Builder.of(RiverSkiffEntity::new, MobCategory.MISC).sized(2.2F, 1.0F).clientTrackingRange(10).build(RIVER_SKIFF_KEY));
	public static final ResourceKey<EntityType<?>> EXPLORER_SLOOP_KEY = entityKey("explorer_sloop");
	public static final EntityType<ExplorerSloopEntity> EXPLORER_SLOOP = Registry.register(BuiltInRegistries.ENTITY_TYPE, EXPLORER_SLOOP_KEY,
		EntityType.Builder.of(ExplorerSloopEntity::new, MobCategory.MISC).sized(3.4F, 2.9F).clientTrackingRange(12).build(EXPLORER_SLOOP_KEY));
	public static final ResourceKey<EntityType<?>> MERCHANT_SCHOONER_KEY = entityKey("merchant_schooner");
	public static final EntityType<MerchantSchoonerEntity> MERCHANT_SCHOONER = Registry.register(BuiltInRegistries.ENTITY_TYPE, MERCHANT_SCHOONER_KEY,
		EntityType.Builder.of(MerchantSchoonerEntity::new, MobCategory.MISC).sized(4.8F, 3.8F).clientTrackingRange(14).build(MERCHANT_SCHOONER_KEY));

	public static final ShipItem RIVER_SKIFF_ITEM = registerShipItem("river_skiff", level -> new RiverSkiffEntity(RIVER_SKIFF, level));
	public static final ShipItem EXPLORER_SLOOP_ITEM = registerShipItem("explorer_sloop", level -> new ExplorerSloopEntity(EXPLORER_SLOOP, level));
	public static final ShipItem MERCHANT_SCHOONER_ITEM = registerShipItem("merchant_schooner", level -> new MerchantSchoonerEntity(MERCHANT_SCHOONER, level));

	public static final CreativeModeTab SHIPS_TAB = FabricCreativeModeTab.builder()
		.title(Component.translatable("itemGroup.peterwolfs_boats_and_ships.group"))
		.icon(RIVER_SKIFF_ITEM::getDefaultInstance)
		.displayItems((parameters, output) -> { output.accept(RIVER_SKIFF_ITEM); output.accept(EXPLORER_SLOOP_ITEM); output.accept(MERCHANT_SCHOONER_ITEM); })
		.build();

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("group"), SHIPS_TAB);
		// A discoverable custom tab is useful, but these must also appear in a
		// vanilla tab so players can obtain them from the normal Creative search.
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.insertAfter(Items.OAK_BOAT, RIVER_SKIFF_ITEM);
			output.insertAfter(RIVER_SKIFF_ITEM, EXPLORER_SLOOP_ITEM);
			output.insertAfter(EXPLORER_SLOOP_ITEM, MERCHANT_SCHOONER_ITEM);
		});
		PayloadTypeRegistry.serverboundPlay().register(ShipInputPayload.TYPE, ShipInputPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShipInputPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			if (player.getVehicle() instanceof com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity ship && ship.getFirstPassenger() == player) ship.setControl(payload.thrust(), payload.rudder());
		}));
		LOGGER.info("Peterwolf's Minecraft Boats and Ships is ready to sail.");
	}

	private static ResourceKey<EntityType<?>> entityKey(String path) { return ResourceKey.create(Registries.ENTITY_TYPE, id(path)); }
	private static ShipItem registerShipItem(String path, java.util.function.Function<net.minecraft.world.level.Level, ? extends com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new ShipItem(new Item.Properties().setId(key).stacksTo(1), factory));
	}
	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
