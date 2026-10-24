package com.santiq.kingdomomnitrix.registry;

import com.santiq.kingdomomnitrix.KingdomOmnitrix;
import com.santiq.kingdomomnitrix.world.structure.PetrosapienPiece;
import com.santiq.kingdomomnitrix.world.structure.PetrosapienSettlement;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.gen.structure.StructureType;

/** Eigene Strukturen; Platzierung (Abstand, Biome) steht in data/kingdomomnitrix/worldgen/structure(_set)/. */
public final class ModStructures {
	public static final StructureType<PetrosapienSettlement> PETROSAPIEN_SETTLEMENT = Registry.register(Registries.STRUCTURE_TYPE,
			KingdomOmnitrix.id("petrosapien_settlement"), () -> PetrosapienSettlement.CODEC);
	public static final StructurePieceType PETROSAPIEN_PIECE = Registry.register(Registries.STRUCTURE_PIECE,
			KingdomOmnitrix.id("petrosapien_piece"), (StructurePieceType.Simple) PetrosapienPiece::new);

	private ModStructures() {
	}

	public static void register() {
		KingdomOmnitrix.LOGGER.debug("Strukturen registriert: {}", Registries.STRUCTURE_TYPE.getId(PETROSAPIEN_SETTLEMENT));
	}
}
