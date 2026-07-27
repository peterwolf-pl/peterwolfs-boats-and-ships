package com.piotrek.peterwolfsboatsandships.network;

import com.piotrek.peterwolfsboatsandships.PeterwolfsBoatsAndShipsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client intent only. Position, velocity and rotation stay server-owned. */
public record ShipInputPayload(float thrust, float rudder) implements CustomPacketPayload {
	public static final Type<ShipInputPayload> TYPE = new Type<>(PeterwolfsBoatsAndShipsMod.id("ship_input"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShipInputPayload> CODEC = new StreamCodec<>() {
		@Override
		public ShipInputPayload decode(RegistryFriendlyByteBuf buffer) {
			return new ShipInputPayload(buffer.readFloat(), buffer.readFloat());
		}

		@Override
		public void encode(RegistryFriendlyByteBuf buffer, ShipInputPayload payload) {
			buffer.writeFloat(payload.thrust);
			buffer.writeFloat(payload.rudder);
		}
	};

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
