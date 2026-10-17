package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class ExplorerSloopEntity extends AbstractShipEntity {
	public ExplorerSloopEntity(EntityType<? extends ExplorerSloopEntity> type, Level level) { super(type, level, 9); }
	@Override protected int seatCount() { return 4; }
	@Override protected double maxSpeed() { return 0.39D; }
	@Override protected double acceleration() { return 0.021D; }
	@Override protected float turnRate() { return 2.75F; }
	@Override protected boolean hasSails() { return true; }
	@Override protected Item dropItem() { return PeterwolfsBoatsAndShipsMod.EXPLORER_SLOOP_ITEM.get(); }
	@Override protected Component cargoTitle() { return Component.translatable("container.peterwolfs_boats_and_ships.explorer_sloop"); }
	@Override protected double helmSeatY() { return 0.62D; }
	@Override protected double helmSeatZ() { return -1.15D; }
	@Override protected double passengerSeatY() { return 0.50D; }
}
