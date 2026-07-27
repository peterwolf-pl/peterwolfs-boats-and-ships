package com.piotrek.peterwolfsboatsandships.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Three distinct layer definitions for River Skiff, Explorer Sloop, and Merchant Schooner.
 * Sails are anchored directly beneath the wooden yardarms (reje) on the masts.
 */
public final class ShipModel extends EntityModel<ShipRenderState> {
	private final ModelPart hull;
	private final ModelPart sail;
	private final ModelPart sailTwo;
	private final ModelPart leftOar;
	private final ModelPart rightOar;

	public ShipModel(ModelPart root) {
		super(root);
		this.hull = root.getChild("hull");
		this.sail = root.getChild("sail");
		this.sailTwo = root.getChild("sail_two");
		this.leftOar = root.getChild("left_oar");
		this.rightOar = root.getChild("right_oar");
	}

	public static LayerDefinition createRiverSkiffLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-12.0F, -5.0F, -20.0F, 24.0F, 1.0F, 40.0F) // Bottom floor
			.texOffs(130, 0).addBox(-13.0F, -4.0F, -22.0F, 26.0F, 7.0F, 44.0F) // Bottom outer hull
			.texOffs(0, 52).addBox(-15.0F, -11.0F, -22.0F, 2.0F, 7.0F, 44.0F) // Left side wall
			.texOffs(94, 52).addBox(13.0F, -11.0F, -22.0F, 2.0F, 7.0F, 44.0F) // Right side wall
			.texOffs(0, 104).addBox(-14.0F, -11.0F, -24.0F, 28.0F, 8.0F, 3.0F) // Front bow wall
			.texOffs(64, 104).addBox(-14.0F, -11.0F, 21.0F, 28.0F, 8.0F, 3.0F) // Rear stern wall
			.texOffs(0, 116).addBox(-15.5F, -12.0F, -24.0F, 3.0F, 2.0F, 48.0F) // Left gunwale
			.texOffs(104, 116).addBox(12.5F, -12.0F, -24.0F, 3.0F, 2.0F, 48.0F) // Right gunwale
			.texOffs(0, 168).addBox(-13.0F, -7.0F, -10.0F, 26.0F, 2.0F, 5.0F) // Front bench
			.texOffs(64, 168).addBox(-13.0F, -7.0F, 6.0F, 26.0F, 2.0F, 5.0F) // Rear bench
			.texOffs(0, 176).addBox(-16.0F, -13.0F, -2.0F, 2.0F, 3.0F, 4.0F) // Left rowlock
			.texOffs(14, 176).addBox(14.0F, -13.0F, -2.0F, 2.0F, 3.0F, 4.0F), PartPose.offset(0.0F, 24.0F, 0.0F)); // Right rowlock

		// Left Oar extending left through rowlock at Y=11.0F
		root.addOrReplaceChild("left_oar", CubeListBuilder.create()
			.texOffs(0, 184).addBox(-18.0F, -1.0F, -1.0F, 28.0F, 2.0F, 2.0F) // Shaft extending from handle (+10) to left tip (-18)
			.texOffs(62, 184).addBox(-24.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F), PartPose.offset(-15.0F, 11.0F, 0.0F)); // Vertical paddle blade

		// Right Oar extending right through rowlock at Y=11.0F
		root.addOrReplaceChild("right_oar", CubeListBuilder.create()
			.texOffs(0, 216).addBox(-10.0F, -1.0F, -1.0F, 28.0F, 2.0F, 2.0F) // Shaft extending from handle (-10) to right tip (+18)
			.texOffs(62, 216).addBox(18.0F, -3.0F, -0.5F, 6.0F, 6.0F, 1.0F), PartPose.offset(15.0F, 11.0F, 0.0F)); // Vertical paddle blade

		root.addOrReplaceChild("sail", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);
		root.addOrReplaceChild("sail_two", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);

		return LayerDefinition.create(mesh, 512, 512);
	}

	public static LayerDefinition createExplorerSloopLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-14.0F, -5.0F, -26.0F, 28.0F, 1.0F, 52.0F) // Bottom floor
			.texOffs(162, 0).addBox(-15.0F, -4.0F, -28.0F, 30.0F, 8.0F, 56.0F) // Lower outer hull
			.texOffs(0, 66).addBox(-17.0F, -12.0F, -28.0F, 2.0F, 8.0F, 56.0F) // Left side wall
			.texOffs(118, 66).addBox(15.0F, -12.0F, -28.0F, 2.0F, 8.0F, 56.0F) // Right side wall
			.texOffs(0, 132).addBox(-16.0F, -14.0F, -31.0F, 32.0F, 10.0F, 4.0F) // Front bow wall
			.texOffs(74, 132).addBox(-16.0F, -14.0F, 27.0F, 32.0F, 10.0F, 3.0F) // Rear stern wall
			.texOffs(0, 148).addBox(-17.5F, -13.0F, -31.0F, 3.0F, 2.0F, 60.0F) // Left gunwale
			.texOffs(128, 148).addBox(14.5F, -13.0F, -31.0F, 3.0F, 2.0F, 60.0F) // Right gunwale
			.texOffs(0, 212).addBox(-2.0F, -16.0F, -43.0F, 4.0F, 4.0F, 13.0F) // Bowsprit
			.texOffs(36, 212).addBox(-15.0F, -9.0F, 14.0F, 30.0F, 4.0F, 14.0F) // Quarterdeck
			.texOffs(126, 212).addBox(-1.5F, -15.0F, 22.0F, 3.0F, 7.0F, 3.0F) // Helm stand
			.texOffs(140, 212).addBox(-5.0F, -20.0F, 22.0F, 10.0F, 10.0F, 1.0F) // Helm wheel
			.texOffs(164, 212).addBox(-5.0F, -13.0F, -2.0F, 10.0F, 8.0F, 8.0F) // Chest
			.texOffs(202, 212).addBox(-15.0F, -18.0F, -10.0F, 3.0F, 5.0F, 3.0F) // Lantern
			.texOffs(0, 230).addBox(-2.5F, -50.0F, -10.0F, 5.0F, 46.0F, 5.0F) // Mainmast (5x5)
			.texOffs(22, 230).addBox(-1.5F, -60.0F, -9.0F, 3.0F, 10.0F, 3.0F) // Thinner Topmast Extension (3x3)
			.texOffs(36, 230).addBox(-18.0F, -51.0F, -11.0F, 36.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 24.0F, 0.0F)); // Yardarm at top of mainmast (Y=-51..-48 inside hull)

		// Sail pivot aligned at bottom of yardarm (Y = 24 - 48 = -26.0F absolute model Y)
		root.addOrReplaceChild("sail", CubeListBuilder.create()
			.texOffs(116, 230).addBox(-16.0F, 0.0F, 0.0F, 32.0F, 30.0F, 1.0F), PartPose.offset(0.0F, -26.0F, -9.0F)); // Hangs DOWN directly beneath yardarm

		root.addOrReplaceChild("sail_two", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);
		root.addOrReplaceChild("left_oar", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);
		root.addOrReplaceChild("right_oar", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);

		return LayerDefinition.create(mesh, 512, 512);
	}

	public static LayerDefinition createMerchantSchoonerLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();

		PartDefinition hull = root.addOrReplaceChild("hull", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-16.0F, -5.0F, -34.0F, 32.0F, 1.0F, 68.0F) // Bottom floor
			.texOffs(202, 0).addBox(-18.0F, -4.0F, -36.0F, 36.0F, 8.0F, 72.0F) // Lower outer hull
			.texOffs(0, 71).addBox(-19.0F, -13.0F, -36.0F, 2.0F, 9.0F, 72.0F) // Left side wall
			.texOffs(150, 71).addBox(17.0F, -13.0F, -36.0F, 2.0F, 9.0F, 72.0F) // Right side wall
			.texOffs(0, 154).addBox(-18.0F, -16.0F, -39.0F, 36.0F, 12.0F, 4.0F) // Forecastle bow wall
			.texOffs(82, 154).addBox(-17.0F, -16.0F, 14.0F, 34.0F, 12.0F, 22.0F) // Quarterdeck cabin & rear transom wall
			.texOffs(0, 190).addBox(-19.5F, -14.0F, -39.0F, 3.0F, 2.0F, 76.0F) // Left gunwale
			.texOffs(160, 190).addBox(16.5F, -14.0F, -39.0F, 3.0F, 2.0F, 76.0F) // Right gunwale
			.texOffs(0, 270).addBox(-2.5F, -18.0F, -57.0F, 5.0F, 5.0F, 19.0F) // Bowsprit
			.texOffs(50, 270).addBox(-17.0F, -17.0F, 12.0F, 34.0F, 1.0F, 24.0F) // Quarterdeck roof
			.texOffs(168, 270).addBox(-10.0F, -13.0F, -12.0F, 8.0F, 8.0F, 8.0F) // Crates stack
			.texOffs(202, 270).addBox(2.0F, -12.0F, -4.0F, 7.0F, 7.0F, 7.0F) // Barrels stack
			.texOffs(232, 270).addBox(-2.0F, -22.0F, 35.0F, 4.0F, 6.0F, 4.0F) // Stern lantern
			.texOffs(0, 296).addBox(-2.5F, -52.0F, -20.0F, 5.0F, 48.0F, 5.0F) // Foremast (5x5)
			.texOffs(22, 296).addBox(-1.5F, -62.0F, -19.0F, 3.0F, 10.0F, 3.0F) // Thinner Fore-Topmast (3x3)
			.texOffs(36, 296).addBox(-15.0F, -53.0F, -21.0F, 30.0F, 3.0F, 3.0F) // Foremast Yardarm
			.texOffs(0, 356).addBox(-2.5F, -58.0F, 6.0F, 5.0F, 54.0F, 5.0F) // Mainmast (5x5)
			.texOffs(22, 356).addBox(-1.5F, -70.0F, 7.0F, 3.0F, 12.0F, 3.0F) // Thinner Main-Topmast (3x3)
			.texOffs(36, 356).addBox(-17.0F, -59.0F, 5.0F, 34.0F, 3.0F, 3.0F), PartPose.offset(0.0F, 24.0F, 0.0F)); // Mainmast Yardarm

		// Foresail pivot aligned beneath fore-yardarm (Y = 24 - 50 = -26.0F)
		root.addOrReplaceChild("sail", CubeListBuilder.create()
			.texOffs(104, 296).addBox(-15.0F, 0.0F, 0.0F, 30.0F, 32.0F, 1.0F), PartPose.offset(0.0F, -26.0F, -19.0F));

		// Mainsail pivot aligned beneath main-yardarm (Y = 24 - 56 = -32.0F)
		root.addOrReplaceChild("sail_two", CubeListBuilder.create()
			.texOffs(112, 356).addBox(-17.0F, 0.0F, 0.0F, 34.0F, 38.0F, 1.0F), PartPose.offset(0.0F, -32.0F, 7.0F));

		root.addOrReplaceChild("left_oar", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);
		root.addOrReplaceChild("right_oar", CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, 0.0F, 0.1F, 0.1F, 0.1F), PartPose.ZERO);

		return LayerDefinition.create(mesh, 512, 512);
	}

	@Override
	public void setupAnim(ShipRenderState state) {
		super.setupAnim(state);
		this.hull.zRot = (float) Math.toRadians(state.heel);

		// Furled sails logic: when stationary (speed <= 0.015F), sails furl/roll up directly under yardarms
		boolean isMoving = state.speed > 0.015F;
		float sailUnfold = isMoving ? 1.0F : 0.10F;

		this.sail.yScale = sailUnfold;
		this.sailTwo.yScale = sailUnfold;

		if (isMoving) {
			this.sail.zRot = (float) Math.sin(state.sailPhase) * 0.075F;
			this.sailTwo.zRot = (float) Math.sin(state.sailPhase + 0.8F) * 0.065F;
		} else {
			this.sail.zRot = 0.0F;
			this.sailTwo.zRot = 0.0F;
		}

		// Downward oar tilt & rowing motion
		float strokeSwing = (float) Math.sin(state.oarPhase) * 0.38F;
		float strokeDip = (float) Math.cos(state.oarPhase) * 0.18F;

		this.leftOar.zRot = -0.46F - strokeDip;
		this.rightOar.zRot = 0.46F + strokeDip;
		this.leftOar.yRot = strokeSwing;
		this.rightOar.yRot = -strokeSwing;
		this.leftOar.xRot = (float) Math.sin(state.oarPhase) * 0.10F;
		this.rightOar.xRot = (float) -Math.sin(state.oarPhase) * 0.10F;
	}
}
