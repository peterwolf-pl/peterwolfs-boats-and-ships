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
	@Override protected Item dropItem() { return PeterwolfsBoatsAndShipsMod.MERCHANT_SCHOONER_ITEM; }
	@Override protected Component cargoTitle() { return Component.translatable("container.peterwolfs_boats_and_ships.merchant_schooner"); }
	@Override protected net.minecraft.world.phys.Vec3 seatOffset(int index) {
		if (index == 0) return new net.minecraft.world.phys.Vec3(0.0D, 0.44D, -1.55D);
		return super.seatOffset(index);
	}
}
