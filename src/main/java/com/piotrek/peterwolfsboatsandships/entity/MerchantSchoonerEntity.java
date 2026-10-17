package com.piotrek.peterwolfsboatsandships.entity;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class MerchantSchoonerEntity extends AbstractShipEntity {
	public MerchantSchoonerEntity(EntityType<? extends MerchantSchoonerEntity> type, Level level) { super(type, level, 27); }
	@Override protected int seatCount() { return 6; }
	@Override protected double maxSpeed() { return 0.31D; }
	@Override protected double acceleration() { return 0.014D; }
	@Override protected float turnRate() { return 1.75F; }
	@Override protected boolean hasSails() { return true; }
	@Override protected Item dropItem() { return PeterwolfsBoatsAndShipsMod.MERCHANT_SCHOONER_ITEM.get(); }
	@Override protected Component cargoTitle() { return Component.translatable("container.peterwolfs_boats_and_ships.merchant_schooner"); }
	@Override protected double helmSeatY() { return 0.55D; }
	@Override protected double helmSeatZ() { return -1.85D; }
	@Override protected double passengerSeatY() { return 0.50D; }
}
