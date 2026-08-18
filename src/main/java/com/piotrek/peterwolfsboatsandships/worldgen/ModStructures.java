package com.piotrek.peterwolfsboatsandships.worldgen;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class ModStructures {
	public static final ResourceKey<Structure> WATERMAN_SETTLEMENT = ResourceKey.create(
		Registries.STRUCTURE,
		PeterwolfsBoatsAndShipsMod.id("waterman_settlement")
	);

	public static StructureType<WatermanSettlementStructure> WATERMAN_SETTLEMENT_TYPE;
	public static StructurePieceType WATERMAN_SETTLEMENT_PIECE;

	private ModStructures() {
	}

	public static void register() {
		WATERMAN_SETTLEMENT_TYPE = Registry.register(
			BuiltInRegistries.STRUCTURE_TYPE,
			PeterwolfsBoatsAndShipsMod.id("waterman_settlement"),
			() -> WatermanSettlementStructure.CODEC
		);
		WATERMAN_SETTLEMENT_PIECE = Registry.register(
			BuiltInRegistries.STRUCTURE_PIECE,
			PeterwolfsBoatsAndShipsMod.id("waterman_settlement_piece"),
			WatermanSettlementPiece::new
		);
	}
}
