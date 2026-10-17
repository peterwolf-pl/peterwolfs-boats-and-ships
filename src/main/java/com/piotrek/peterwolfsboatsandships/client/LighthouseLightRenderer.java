package com.piotrek.peterwolfsboatsandships.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.piotrek.peterwolfsboatsandships.block.LighthouseLightBlock;
import com.piotrek.peterwolfsboatsandships.block.LighthouseLightBlockEntity;
import com.piotrek.peterwolfsboatsandships.block.LighthouseLightMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rotating horizontal lighthouse spotlight for spot mode. Flash mode has no ray —
 * only the block's blinking point light (level 15 on / 0 off).
 * Spot beam alpha is lower by day and full by night.
 */
public final class LighthouseLightRenderer implements BlockEntityRenderer<LighthouseLightBlockEntity, LighthouseLightRenderState> {
	/** Horizontal beam length in blocks (visual cone). */
	private static final int BEAM_LENGTH = 96;
	/** Warm lighthouse gold / white light. */
	private static final int BEAM_COLOR = 0xFFF6D57A;
	private static final int OPPOSITE_BEAM_COLOR = 0xFFE8C060;
	/** Daytime floor / nighttime peak for beam alpha. */
	private static final float DAY_VISIBILITY = 0.14F;
	private static final float NIGHT_VISIBILITY = 1.0F;

	public LighthouseLightRenderer(final BlockEntityRendererProvider.Context context) {
	}

	@Override
	public LighthouseLightRenderState createRenderState() {
		return new LighthouseLightRenderState();
	}

	@Override
	public void extractRenderState(
		final LighthouseLightBlockEntity blockEntity,
		final LighthouseLightRenderState state,
		final float partialTicks,
		final Vec3 cameraPosition,
		@Nullable final ModelFeatureRenderer.CrumblingOverlay breakProgress
	) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		state.showBeam = blockEntity.getBlockState().getValue(LighthouseLightBlock.MODE) == LighthouseLightMode.SPOT;
		// Flash / off: no ray geometry — flash uses block light only; off is dark.
		if (!state.showBeam) {
			state.beamVisibility = 0.0F;
			return;
		}

		state.beamYaw = blockEntity.getBeamYaw(partialTicks);
		state.animationTime = blockEntity.getAnimationTime(partialTicks);

		float distance = (float) cameraPosition.subtract(Vec3.atCenterOf(state.blockPos)).horizontalDistance();
		LocalPlayer player = Minecraft.getInstance().player;
		// Scale beam thickness with distance so the spot stays readable offshore.
		state.beamRadiusScale = player != null && player.isScoping()
			? 1.0F
			: Math.max(1.0F, distance / 80.0F);

		Level level = blockEntity.getLevel();
		state.beamVisibility = dayNightVisibility(level);
	}

	/**
	 * skyDarken is low in daylight and rises toward night (~0..15). Map to a
	 * soft day floor so the ray is faint by day and strong after dusk.
	 */
	private static float dayNightVisibility(@Nullable final Level level) {
		if (level == null) {
			return NIGHT_VISIBILITY;
		}
		float nightFactor = Mth.clamp(level.getSkyDarken() / 11.0F, 0.0F, 1.0F);
		// Soften the transition around dusk/dawn.
		nightFactor = nightFactor * nightFactor * (3.0F - 2.0F * nightFactor);
		return Mth.lerp(nightFactor, DAY_VISIBILITY, NIGHT_VISIBILITY);
	}

	@Override
	public void submit(
		final LighthouseLightRenderState state,
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final CameraRenderState camera
	) {
		// Only spot mode draws rays.
		if (!state.showBeam || state.beamVisibility <= 0.01F) {
			return;
		}
		// Main sweeping beam
		submitHorizontalBeam(poseStack, submitNodeCollector, state, state.beamYaw, BEAM_COLOR, 0.18F, 0.28F);
		// Fainter opposite beam for classic dual-lens lighthouse look
		submitHorizontalBeam(poseStack, submitNodeCollector, state, state.beamYaw + 180.0F, OPPOSITE_BEAM_COLOR, 0.10F, 0.16F);
	}

	private static void submitHorizontalBeam(
		final PoseStack poseStack,
		final SubmitNodeCollector submitNodeCollector,
		final LighthouseLightRenderState state,
		final float yawDegrees,
		final int color,
		final float solidRadius,
		final float glowRadius
	) {
		poseStack.pushPose();
		// Pivot at lamp center (mid-block, slightly above base).
		poseStack.translate(0.5D, 0.62D, 0.5D);
		poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));
		// Point the vertical beacon beam down the local +Z after this pitch.
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		// Nudge so the beam starts just outside the glass housing.
		poseStack.translate(0.0D, 0.35D, 0.0D);

		float scale = state.beamRadiusScale;
		BeaconRenderer.submitBeaconBeam(
			poseStack,
			submitNodeCollector,
			BeaconRenderer.BEAM_LOCATION,
			1.0F,
			state.animationTime,
			0,
			BEAM_LENGTH,
			colorWithVisibility(color, state.beamVisibility),
			solidRadius * scale,
			glowRadius * scale
		);
		poseStack.popPose();
	}

	private static int colorWithVisibility(final int rgb, final float visibility) {
		int alpha = Mth.clamp(Math.round(visibility * 255.0F), 0, 255);
		return ARGB.color(alpha, rgb);
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	/**
	 * Longer than default block entity range so the rotating spot remains
	 * visible from open water beyond ordinary block draw distance.
	 */
	@Override
	public int getViewDistance() {
		return Math.max(256, Minecraft.getInstance().options.getEffectiveRenderDistance() * 24);
	}

	@Override
	public boolean shouldRender(final LighthouseLightBlockEntity blockEntity, final Vec3 cameraPosition) {
		// Flash / off need no long-range ray renderer.
		if (blockEntity.getBlockState().getValue(LighthouseLightBlock.MODE) != LighthouseLightMode.SPOT) {
			return false;
		}
		// Horizontal-only distance (same idea as the vanilla beacon).
		return Vec3.atCenterOf(blockEntity.getBlockPos())
			.multiply(1.0D, 0.0D, 1.0D)
			.closerThan(cameraPosition.multiply(1.0D, 0.0D, 1.0D), this.getViewDistance());
	}
}
