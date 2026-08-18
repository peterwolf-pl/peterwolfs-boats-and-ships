package com.piotrek.peterwolfsboatsandships;

import com.piotrek.peterwolfsboatsandships.block.LighthouseLightBlock;
import com.piotrek.peterwolfsboatsandships.block.LighthouseLightBlockEntity;
import com.piotrek.peterwolfsboatsandships.entity.ExplorerSloopEntity;
import com.piotrek.peterwolfsboatsandships.entity.MerchantSchoonerEntity;
import com.piotrek.peterwolfsboatsandships.entity.RiverSkiffEntity;
import com.piotrek.peterwolfsboatsandships.entity.WatermanEntity;
import com.piotrek.peterwolfsboatsandships.item.ShipItem;
import com.piotrek.peterwolfsboatsandships.network.ShipInputPayload;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PeterwolfsBoatsAndShipsMod implements ModInitializer {
	public static final String MOD_ID = "peterwolfs_boats_and_ships";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	/** Short-lived moving ticket used only while an autonomous atoll voyage is active. */
	public static final TicketType WATERMAN_VOYAGE_TICKET = Registry.register(
		BuiltInRegistries.TICKET_TYPE,
		id("waterman_voyage"),
		new TicketType(
			100L,
			TicketType.FLAG_LOADING
				| TicketType.FLAG_SIMULATION
				| TicketType.FLAG_KEEP_DIMENSION_ACTIVE
				| TicketType.FLAG_CAN_EXPIRE_IF_UNLOADED
		)
	);

	// Collision heights match the walkable deck only (not masts/sails), so players can
	// step on from a pier like normal blocks in water. Visual models stay tall.
	// Keep height ≤ ~0.85 so vanilla step-up (0.6) reaches the deck from water-level piers.
	public static final ResourceKey<EntityType<?>> RIVER_SKIFF_KEY = entityKey("river_skiff");
	public static final EntityType<RiverSkiffEntity> RIVER_SKIFF = Registry.register(BuiltInRegistries.ENTITY_TYPE, RIVER_SKIFF_KEY,
		EntityType.Builder.of(RiverSkiffEntity::new, MobCategory.MISC).sized(2.2F, 0.6F).clientTrackingRange(10).build(RIVER_SKIFF_KEY));
	public static final ResourceKey<EntityType<?>> EXPLORER_SLOOP_KEY = entityKey("explorer_sloop");
	public static final EntityType<ExplorerSloopEntity> EXPLORER_SLOOP = Registry.register(BuiltInRegistries.ENTITY_TYPE, EXPLORER_SLOOP_KEY,
		EntityType.Builder.of(ExplorerSloopEntity::new, MobCategory.MISC).sized(3.4F, 0.75F).clientTrackingRange(12).build(EXPLORER_SLOOP_KEY));
	public static final ResourceKey<EntityType<?>> MERCHANT_SCHOONER_KEY = entityKey("merchant_schooner");
	public static final EntityType<MerchantSchoonerEntity> MERCHANT_SCHOONER = Registry.register(BuiltInRegistries.ENTITY_TYPE, MERCHANT_SCHOONER_KEY,
		EntityType.Builder.of(MerchantSchoonerEntity::new, MobCategory.MISC).sized(4.8F, 0.85F).clientTrackingRange(14).build(MERCHANT_SCHOONER_KEY));
	public static final ResourceKey<EntityType<?>> WATERMAN_KEY = entityKey("waterman");
	public static final EntityType<WatermanEntity> WATERMAN = Registry.register(BuiltInRegistries.ENTITY_TYPE, WATERMAN_KEY,
		EntityType.Builder.<WatermanEntity>of(WatermanEntity::new, MobCategory.CREATURE).sized(0.6F, 1.95F).clientTrackingRange(10).build(WATERMAN_KEY));

	public static final ShipItem RIVER_SKIFF_ITEM = registerShipItem("river_skiff", level -> new RiverSkiffEntity(RIVER_SKIFF, level));
	public static final ShipItem EXPLORER_SLOOP_ITEM = registerShipItem("explorer_sloop", level -> new ExplorerSloopEntity(EXPLORER_SLOOP, level));
	public static final ShipItem MERCHANT_SCHOONER_ITEM = registerShipItem("merchant_schooner", level -> new MerchantSchoonerEntity(MERCHANT_SCHOONER, level));
	public static final ResourceKey<Item> WATERMAN_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, id("waterman_spawn_egg"));
	public static final SpawnEggItem WATERMAN_SPAWN_EGG = Registry.register(
		BuiltInRegistries.ITEM,
		WATERMAN_SPAWN_EGG_KEY,
		new SpawnEggItem(new Item.Properties().spawnEgg(WATERMAN).setId(WATERMAN_SPAWN_EGG_KEY))
	);

	public static final LighthouseLightBlock LIGHTHOUSE_LIGHT = registerBlock(
		"lighthouse_light",
		key -> new LighthouseLightBlock(BlockBehaviour.Properties.of()
			.strength(1.5F, 6.0F)
			.sound(SoundType.LANTERN)
			.lightLevel(LighthouseLightBlock::lightEmission)
			.noOcclusion()
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos) -> false)
			.setId(key))
	);
	public static final BlockItem LIGHTHOUSE_LIGHT_ITEM = registerBlockItem("lighthouse_light", LIGHTHOUSE_LIGHT);
	public static final BlockEntityType<LighthouseLightBlockEntity> LIGHTHOUSE_LIGHT_BLOCK_ENTITY = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		id("lighthouse_light"),
		new BlockEntityType<>(LighthouseLightBlockEntity::new, Set.of(LIGHTHOUSE_LIGHT))
	);

	public static final CreativeModeTab SHIPS_TAB = FabricCreativeModeTab.builder()
		.title(Component.translatable("itemGroup.peterwolfs_boats_and_ships.group"))
		.icon(RIVER_SKIFF_ITEM::getDefaultInstance)
		.displayItems((parameters, output) -> {
			output.accept(RIVER_SKIFF_ITEM);
			output.accept(EXPLORER_SLOOP_ITEM);
			output.accept(MERCHANT_SCHOONER_ITEM);
			output.accept(WATERMAN_SPAWN_EGG);
			output.accept(LIGHTHOUSE_LIGHT_ITEM);
		})
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
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(LIGHTHOUSE_LIGHT_ITEM);
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> {
			output.insertAfter(Items.VILLAGER_SPAWN_EGG, WATERMAN_SPAWN_EGG);
		});
		FabricDefaultAttributeRegistry.register(WATERMAN, WatermanEntity.createAttributes());
		PayloadTypeRegistry.serverboundPlay().register(ShipInputPayload.TYPE, ShipInputPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ShipInputPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			if (player.getVehicle() instanceof com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity ship && ship.isHelmsman(player)) {
				ship.setControl(payload.thrust(), payload.rudder());
			}
		}));
		LOGGER.info("Peterwolf's Minecraft Boats and Ships is ready to sail.");
	}

	private static ResourceKey<EntityType<?>> entityKey(String path) { return ResourceKey.create(Registries.ENTITY_TYPE, id(path)); }

	private static ShipItem registerShipItem(String path, java.util.function.Function<net.minecraft.world.level.Level, ? extends com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new ShipItem(new Item.Properties().setId(key).stacksTo(1), factory));
	}

	private static <T extends Block> T registerBlock(final String path, final Function<ResourceKey<Block>, T> factory) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(key));
	}

	private static BlockItem registerBlockItem(final String path, final Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
}
