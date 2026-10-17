package com.piotrek.peterwolfsboatsandships.worldgen;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
	public static final ResourceKey<Structure> WATERMAN_SETTLEMENT = ResourceKey.create(
		Registries.STRUCTURE,
		PeterwolfsBoatsAndShipsMod.id("waterman_settlement")
	);

	public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, PeterwolfsBoatsAndShipsMod.MOD_ID);
	public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, PeterwolfsBoatsAndShipsMod.MOD_ID);

	public static final DeferredHolder<StructureType<?>, StructureType<WatermanSettlementStructure>> WATERMAN_SETTLEMENT_TYPE = STRUCTURE_TYPES.register(
		"waterman_settlement",
		() -> () -> WatermanSettlementStructure.CODEC
	);
	public static final DeferredHolder<StructurePieceType, StructurePieceType> WATERMAN_SETTLEMENT_PIECE = STRUCTURE_PIECES.register(
		"waterman_settlement_piece",
		() -> WatermanSettlementPiece::new
	);

	private ModStructures() {
	}

	public static void register(IEventBus modBus) {
		STRUCTURE_TYPES.register(modBus);
		STRUCTURE_PIECES.register(modBus);
	}
}
