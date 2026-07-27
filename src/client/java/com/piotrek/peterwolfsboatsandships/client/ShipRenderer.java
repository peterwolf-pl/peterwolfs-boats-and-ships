package com.piotrek.peterwolfsboatsandships.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.peterwolfsboatsandships.entity.AbstractShipEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

public final class ShipRenderer<T extends AbstractShipEntity> extends EntityRenderer<T, ShipRenderState> {
	private final ShipModel model;
	private final Identifier texture;

	public ShipRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture, float shadowRadius) {
		super(context);
		this.model = new ShipModel(context.bakeLayer(layer));
		this.texture = texture;
		this.shadowRadius = shadowRadius;
	}

	@Override public ShipRenderState createRenderState() { return new ShipRenderState(); }
	@Override public void extractRenderState(T entity, ShipRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.yaw = entity.getYRot();
		state.heel = entity.getVisualHeel(partialTick);
		state.sailPhase = entity.getSailPhase(partialTick);
		state.oarPhase = entity.getOarPhase(partialTick);
		state.speed = (float) entity.getHorizontalSpeed();
	}
	@Override public void submit(ShipRenderState state, PoseStack stack, SubmitNodeCollector collector, CameraRenderState camera) {
		stack.pushPose();
		stack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
		stack.scale(-1.0F, -1.0F, 1.0F);
		stack.translate(0.0F, -1.5F, 0.0F);
		this.model.setupAnim(state);
		collector.submitModel(this.model, state, stack, this.texture, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		stack.popPose();
	}
}
