package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class RiverSkiffEntity extends AbstractShipEntity {
	public RiverSkiffEntity(EntityType<? extends RiverSkiffEntity> type, Level level) { super(type, level, 0); }
	@Override protected int seatCount() { return 2; }
	@Override protected double maxSpeed() { return 0.52D; }
	@Override protected double acceleration() { return 0.034D; }
	@Override protected float turnRate() { return 4.7F; }
	@Override protected boolean hasSails() { return false; }
	@Override protected Item dropItem() { return PeterwolfsBoatsAndShipsMod.RIVER_SKIFF_ITEM; }
	@Override protected Component cargoTitle() { return Component.empty(); }
}
