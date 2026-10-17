package com.piotrek.peterwolfsboatsandships;

import com.piotrek.peterwolfsboatsandships.block.LighthouseLightBlock;
import com.piotrek.peterwolfsboatsandships.block.LighthouseLightBlockEntity;
import com.piotrek.peterwolfsboatsandships.command.WatermanSettlementCommands;
import com.piotrek.peterwolfsboatsandships.entity.ExplorerSloopEntity;
import com.piotrek.peterwolfsboatsandships.entity.MerchantSchoonerEntity;
import com.piotrek.peterwolfsboatsandships.entity.RiverSkiffEntity;
import com.piotrek.peterwolfsboatsandships.entity.WatermanEntity;
import com.piotrek.peterwolfsboatsandships.item.ShipItem;
import com.piotrek.peterwolfsboatsandships.network.ShipInputPayload;
import com.piotrek.peterwolfsboatsandships.worldgen.ModStructures;
import java.util.Set;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PeterwolfsBoatsAndShipsMod.MOD_ID)
public final class PeterwolfsBoatsAndShipsMod {
	public static final String MOD_ID = "peterwolfs_boats_and_ships";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final DeferredRegister.Entities ENTITY_TYPES = DeferredRegister.createEntities(MOD_ID);
	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
	public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
	public static final DeferredRegister<TicketType> TICKET_TYPES = DeferredRegister.create(Registries.TICKET_TYPE, MOD_ID);
	public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

	/** Short-lived moving ticket used only while an autonomous atoll voyage is active. */
	public static final DeferredHolder<TicketType, TicketType> WATERMAN_VOYAGE_TICKET = TICKET_TYPES.register(
		"waterman_voyage",
		() -> new TicketType(
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
	public static final DeferredHolder<EntityType<?>, EntityType<RiverSkiffEntity>> RIVER_SKIFF = ENTITY_TYPES.register(
		"river_skiff",
		() -> EntityType.Builder.of(RiverSkiffEntity::new, MobCategory.MISC)
			.sized(2.2F, 0.6F)
			.clientTrackingRange(10)
			.build(RIVER_SKIFF_KEY)
	);
	public static final ResourceKey<EntityType<?>> EXPLORER_SLOOP_KEY = entityKey("explorer_sloop");
	public static final DeferredHolder<EntityType<?>, EntityType<ExplorerSloopEntity>> EXPLORER_SLOOP = ENTITY_TYPES.register(
		"explorer_sloop",
		() -> EntityType.Builder.of(ExplorerSloopEntity::new, MobCategory.MISC)
			.sized(3.4F, 0.75F)
			.clientTrackingRange(12)
			.build(EXPLORER_SLOOP_KEY)
	);
	public static final ResourceKey<EntityType<?>> MERCHANT_SCHOONER_KEY = entityKey("merchant_schooner");
	public static final DeferredHolder<EntityType<?>, EntityType<MerchantSchoonerEntity>> MERCHANT_SCHOONER = ENTITY_TYPES.register(
		"merchant_schooner",
		() -> EntityType.Builder.of(MerchantSchoonerEntity::new, MobCategory.MISC)
			.sized(4.8F, 0.85F)
			.clientTrackingRange(14)
			.build(MERCHANT_SCHOONER_KEY)
	);
	public static final ResourceKey<EntityType<?>> WATERMAN_KEY = entityKey("waterman");
	public static final DeferredHolder<EntityType<?>, EntityType<WatermanEntity>> WATERMAN = ENTITY_TYPES.register(
		"waterman",
		() -> EntityType.Builder.<WatermanEntity>of(WatermanEntity::new, MobCategory.CREATURE)
			.sized(0.6F, 1.95F)
			.clientTrackingRange(10)
			.build(WATERMAN_KEY)
	);

	public static final DeferredItem<ShipItem> RIVER_SKIFF_ITEM = registerShipItem(
		"river_skiff",
		level -> new RiverSkiffEntity(RIVER_SKIFF.get(), level)
	);
	public static final DeferredItem<ShipItem> EXPLORER_SLOOP_ITEM = registerShipItem(
		"explorer_sloop",
		level -> new ExplorerSloopEntity(EXPLORER_SLOOP.get(), level)
	);
	public static final DeferredItem<ShipItem> MERCHANT_SCHOONER_ITEM = registerShipItem(
		"merchant_schooner",
		level -> new MerchantSchoonerEntity(MERCHANT_SCHOONER.get(), level)
	);
	public static final ResourceKey<Item> WATERMAN_SPAWN_EGG_KEY = ResourceKey.create(Registries.ITEM, id("waterman_spawn_egg"));
	public static final DeferredItem<SpawnEggItem> WATERMAN_SPAWN_EGG = ITEMS.register(
		"waterman_spawn_egg",
		() -> new SpawnEggItem(new Item.Properties().spawnEgg(WATERMAN.get()).setId(WATERMAN_SPAWN_EGG_KEY))
	);

	public static final DeferredBlock<LighthouseLightBlock> LIGHTHOUSE_LIGHT = BLOCKS.registerBlock(
		"lighthouse_light",
		LighthouseLightBlock::new,
		() -> BlockBehaviour.Properties.of()
			.strength(1.5F, 6.0F)
			.sound(SoundType.LANTERN)
			.lightLevel(LighthouseLightBlock::lightEmission)
			.noOcclusion()
			.isRedstoneConductor((state, level, pos) -> false)
			.isSuffocating((state, level, pos) -> false)
			.isViewBlocking((state, level, pos) -> false)
	);
	public static final ResourceKey<Item> LIGHTHOUSE_LIGHT_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("lighthouse_light"));
	public static final DeferredItem<BlockItem> LIGHTHOUSE_LIGHT_ITEM = ITEMS.register(
		"lighthouse_light",
		() -> {
			BlockItem item = new BlockItem(
				LIGHTHOUSE_LIGHT.get(),
				new Item.Properties().setId(LIGHTHOUSE_LIGHT_ITEM_KEY).useBlockDescriptionPrefix()
			);
			item.registerBlocks(Item.BY_BLOCK, item);
			return item;
		}
	);
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LighthouseLightBlockEntity>> LIGHTHOUSE_LIGHT_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
		"lighthouse_light",
		() -> new BlockEntityType<>(LighthouseLightBlockEntity::new, Set.of(LIGHTHOUSE_LIGHT.get()))
	);

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SHIPS_TAB = CREATIVE_TABS.register(
		"group",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.peterwolfs_boats_and_ships.group"))
			.icon(() -> RIVER_SKIFF_ITEM.get().getDefaultInstance())
			.displayItems((parameters, output) -> {
				output.accept(RIVER_SKIFF_ITEM.get());
				output.accept(EXPLORER_SLOOP_ITEM.get());
				output.accept(MERCHANT_SCHOONER_ITEM.get());
				output.accept(WATERMAN_SPAWN_EGG.get());
				output.accept(LIGHTHOUSE_LIGHT_ITEM.get());
			})
			.build()
	);

	public PeterwolfsBoatsAndShipsMod(IEventBus modBus) {
		ENTITY_TYPES.register(modBus);
		BLOCKS.register(modBus);
		ITEMS.register(modBus);
		BLOCK_ENTITY_TYPES.register(modBus);
		TICKET_TYPES.register(modBus);
		CREATIVE_TABS.register(modBus);
		ModStructures.register(modBus);
		modBus.addListener(PeterwolfsBoatsAndShipsMod::addCreative);
		modBus.addListener(PeterwolfsBoatsAndShipsMod::registerAttributes);
		modBus.addListener(PeterwolfsBoatsAndShipsMod::registerPayloads);
		NeoForge.EVENT_BUS.register(ServerEvents.class);
		LOGGER.info("Peterwolf's Minecraft Boats and Ships is ready to sail.");
	}

	private static void addCreative(BuildCreativeModeTabContentsEvent event) {
		if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
			event.insertAfter(
				Items.OAK_BOAT.getDefaultInstance(),
				RIVER_SKIFF_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.insertAfter(
				RIVER_SKIFF_ITEM.get().getDefaultInstance(),
				EXPLORER_SLOOP_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
			event.insertAfter(
				EXPLORER_SLOOP_ITEM.get().getDefaultInstance(),
				MERCHANT_SCHOONER_ITEM.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
		} else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
			event.accept(LIGHTHOUSE_LIGHT_ITEM.get());
		} else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
			event.insertAfter(
				Items.VILLAGER_SPAWN_EGG.getDefaultInstance(),
				WATERMAN_SPAWN_EGG.get().getDefaultInstance(),
				CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS
			);
		}
	}

	private static void registerAttributes(EntityAttributeCreationEvent event) {
		event.put(WATERMAN.get(), WatermanEntity.createAttributes().build());
	}

	private static void registerPayloads(RegisterPayloadHandlersEvent event) {
		PayloadRegistrar registrar = event.registrar("1");
		registrar.playToServer(ShipInputPayload.TYPE, ShipInputPayload.CODEC, (payload, context) -> {
			if (context.player() instanceof ServerPlayer player
				&& player.getVehicle() instanceof com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity ship
				&& ship.isHelmsman(player)) {
				ship.setControl(payload.thrust(), payload.rudder());
			}
		});
	}

	private static ResourceKey<EntityType<?>> entityKey(String path) {
		return ResourceKey.create(Registries.ENTITY_TYPE, id(path));
	}

	private static DeferredItem<ShipItem> registerShipItem(
		String path,
		java.util.function.Function<net.minecraft.world.level.Level, ? extends com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity> factory
	) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(path));
		return ITEMS.register(path, () -> new ShipItem(new Item.Properties().setId(key).stacksTo(1), factory));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static final class ServerEvents {
		private ServerEvents() {
		}

		@SubscribeEvent
		public static void onRegisterCommands(RegisterCommandsEvent event) {
			WatermanSettlementCommands.register(event.getDispatcher());
		}
	}
}
