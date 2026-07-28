package com.piotrek.peterwolfsboatsandships.client;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

public final class LighthouseLightRenderState extends BlockEntityRenderState {
	public float beamYaw;
	public float animationTime;
	public float beamRadiusScale = 1.0F;
	/** Combined day/night + flash pulse alpha multiplier (0..1). */
	public float beamVisibility = 1.0F;
	/** Only SPOT mode draws the long-range ray. */
	public boolean showBeam;
}
