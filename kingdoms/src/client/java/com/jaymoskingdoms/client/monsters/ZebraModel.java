package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * The zebra, from {@code MoCModelNewHorse} (the horse of DrZhark's Mo' Creatures, GPL-3, see {@code licenses/mocreatures}, whose
 * zebra is the same horse in a striped coat), ported to 26.3: its boxes as they were and its animation as it was (the jointed
 * legs, the neck and head moving together, the mouth, the tail, the rear and the stamping shuffle), without the wings, horn,
 * mule's ears and saddle bags the original horse also had.
 */
final class ZebraModel extends EntityModel<BeastState> {
	private static final float RAD = 57.29578F;
	/** The zebra's type number in the original horse model (60), which it tests for the stamping shuffle. */
	private static final int ZEBRA = 60;
	private final ModelPart Body, TailA, TailB, TailC, Leg1A, Leg1B, Leg1C, Leg2A, Leg2B, Leg2C, Leg3A, Leg3B, Leg3C, Leg4A, Leg4B, Leg4C, Head, UMouth, LMouth, UMouth2, LMouth2, Ear1, Ear2, Neck, Saddle, SaddleB, SaddleC, SaddleL2, SaddleL, SaddleR2, SaddleR, SaddleMouthL, SaddleMouthR, SaddleMouthLine, SaddleMouthLineR, Mane, HeadSaddle;


	ZebraModel(ModelPart root) {
		super(root);
		this.Body = root.getChild("Body");
		this.TailA = root.getChild("TailA");
		this.TailB = root.getChild("TailB");
		this.TailC = root.getChild("TailC");
		this.Leg1A = root.getChild("Leg1A");
		this.Leg1B = root.getChild("Leg1B");
		this.Leg1C = root.getChild("Leg1C");
		this.Leg2A = root.getChild("Leg2A");
		this.Leg2B = root.getChild("Leg2B");
		this.Leg2C = root.getChild("Leg2C");
		this.Leg3A = root.getChild("Leg3A");
		this.Leg3B = root.getChild("Leg3B");
		this.Leg3C = root.getChild("Leg3C");
		this.Leg4A = root.getChild("Leg4A");
		this.Leg4B = root.getChild("Leg4B");
		this.Leg4C = root.getChild("Leg4C");
		this.Head = root.getChild("Head");
		this.UMouth = root.getChild("UMouth");
		this.LMouth = root.getChild("LMouth");
		this.UMouth2 = root.getChild("UMouth2");
		this.LMouth2 = root.getChild("LMouth2");
		this.Ear1 = root.getChild("Ear1");
		this.Ear2 = root.getChild("Ear2");
		this.Neck = root.getChild("Neck");
		this.Saddle = root.getChild("Saddle");
		this.SaddleB = root.getChild("SaddleB");
		this.SaddleC = root.getChild("SaddleC");
		this.SaddleL2 = root.getChild("SaddleL2");
		this.SaddleL = root.getChild("SaddleL");
		this.SaddleR2 = root.getChild("SaddleR2");
		this.SaddleR = root.getChild("SaddleR");
		this.SaddleMouthL = root.getChild("SaddleMouthL");
		this.SaddleMouthR = root.getChild("SaddleMouthR");
		this.SaddleMouthLine = root.getChild("SaddleMouthLine");
		this.SaddleMouthLineR = root.getChild("SaddleMouthLineR");
		this.Mane = root.getChild("Mane");
		this.HeadSaddle = root.getChild("HeadSaddle");
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_Body = root.addOrReplaceChild("Body", CubeListBuilder.create().texOffs(0, 34).mirror(false).addBox(-5F, -8F, -19F, 10F, 10F, 24F), PartPose.offset(0F, 11F, 9F));
		PartDefinition p_TailA = root.addOrReplaceChild("TailA", CubeListBuilder.create().texOffs(44, 0).mirror(false).addBox(-1F, -1F, 0F, 2F, 2F, 3F), PartPose.offsetAndRotation(0F, 3F, 14F, -1.134464F, 0F, 0F));
		PartDefinition p_TailB = root.addOrReplaceChild("TailB", CubeListBuilder.create().texOffs(38, 7).mirror(false).addBox(-1.5F, -2F, 3F, 3F, 4F, 7F), PartPose.offsetAndRotation(0F, 3F, 14F, -1.134464F, 0F, 0F));
		PartDefinition p_TailC = root.addOrReplaceChild("TailC", CubeListBuilder.create().texOffs(24, 3).mirror(false).addBox(-1.5F, -4.5F, 9F, 3F, 4F, 7F), PartPose.offsetAndRotation(0F, 3F, 14F, -1.40215F, 0F, 0F));
		PartDefinition p_Leg1A = root.addOrReplaceChild("Leg1A", CubeListBuilder.create().texOffs(78, 29).mirror(false).addBox(-2.5F, -2F, -2.5F, 4F, 9F, 5F), PartPose.offset(4F, 9F, 11F));
		PartDefinition p_Leg1B = root.addOrReplaceChild("Leg1B", CubeListBuilder.create().texOffs(78, 43).mirror(false).addBox(-2F, 0F, -1.5F, 3F, 5F, 3F), PartPose.offset(4F, 16F, 11F));
		PartDefinition p_Leg1C = root.addOrReplaceChild("Leg1C", CubeListBuilder.create().texOffs(78, 51).mirror(false).addBox(-2.5F, 5.1F, -2F, 4F, 3F, 4F), PartPose.offset(4F, 16F, 11F));
		PartDefinition p_Leg2A = root.addOrReplaceChild("Leg2A", CubeListBuilder.create().texOffs(96, 29).mirror(false).addBox(-1.5F, -2F, -2.5F, 4F, 9F, 5F), PartPose.offset(-4F, 9F, 11F));
		PartDefinition p_Leg2B = root.addOrReplaceChild("Leg2B", CubeListBuilder.create().texOffs(96, 43).mirror(false).addBox(-1F, 0F, -1.5F, 3F, 5F, 3F), PartPose.offset(-4F, 16F, 11F));
		PartDefinition p_Leg2C = root.addOrReplaceChild("Leg2C", CubeListBuilder.create().texOffs(96, 51).mirror(false).addBox(-1.5F, 5.1F, -2F, 4F, 3F, 4F), PartPose.offset(-4F, 16F, 11F));
		PartDefinition p_Leg3A = root.addOrReplaceChild("Leg3A", CubeListBuilder.create().texOffs(44, 29).mirror(false).addBox(-1.9F, -1F, -2.1F, 3F, 8F, 4F), PartPose.offset(4F, 9F, -8F));
		PartDefinition p_Leg3B = root.addOrReplaceChild("Leg3B", CubeListBuilder.create().texOffs(44, 41).mirror(false).addBox(-1.9F, 0F, -1.6F, 3F, 5F, 3F), PartPose.offset(4F, 16F, -8F));
		PartDefinition p_Leg3C = root.addOrReplaceChild("Leg3C", CubeListBuilder.create().texOffs(44, 51).mirror(false).addBox(-2.4F, 5.1F, -2.1F, 4F, 3F, 4F), PartPose.offset(4F, 16F, -8F));
		PartDefinition p_Leg4A = root.addOrReplaceChild("Leg4A", CubeListBuilder.create().texOffs(60, 29).mirror(false).addBox(-1.1F, -1F, -2.1F, 3F, 8F, 4F), PartPose.offset(-4F, 9F, -8F));
		PartDefinition p_Leg4B = root.addOrReplaceChild("Leg4B", CubeListBuilder.create().texOffs(60, 41).mirror(false).addBox(-1.1F, 0F, -1.6F, 3F, 5F, 3F), PartPose.offset(-4F, 16F, -8F));
		PartDefinition p_Leg4C = root.addOrReplaceChild("Leg4C", CubeListBuilder.create().texOffs(60, 51).mirror(false).addBox(-1.6F, 5.1F, -2.1F, 4F, 3F, 4F), PartPose.offset(-4F, 16F, -8F));
		PartDefinition p_Head = root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-2.5F, -10F, -1.5F, 5F, 5F, 7F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_UMouth = root.addOrReplaceChild("UMouth", CubeListBuilder.create().texOffs(24, 18).mirror(false).addBox(-2F, -10F, -7F, 4F, 3F, 6F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_LMouth = root.addOrReplaceChild("LMouth", CubeListBuilder.create().texOffs(24, 27).mirror(false).addBox(-2F, -7F, -6.5F, 4F, 2F, 5F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_UMouth2 = root.addOrReplaceChild("UMouth2", CubeListBuilder.create().texOffs(24, 18).mirror(false).addBox(-2F, -10F, -8F, 4F, 3F, 6F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.4363323F, 0F, 0F));
		PartDefinition p_LMouth2 = root.addOrReplaceChild("LMouth2", CubeListBuilder.create().texOffs(24, 27).mirror(false).addBox(-2F, -7F, -5.5F, 4F, 2F, 5F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.7853982F, 0F, 0F));
		PartDefinition p_Ear1 = root.addOrReplaceChild("Ear1", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(0.45F, -12F, 4F, 2F, 3F, 1F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_Ear2 = root.addOrReplaceChild("Ear2", CubeListBuilder.create().texOffs(0, 0).mirror(false).addBox(-2.45F, -12F, 4F, 2F, 3F, 1F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_Neck = root.addOrReplaceChild("Neck", CubeListBuilder.create().texOffs(0, 12).mirror(false).addBox(-2.05F, -9.8F, -2F, 4F, 14F, 8F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_Saddle = root.addOrReplaceChild("Saddle", CubeListBuilder.create().texOffs(80, 0).mirror(false).addBox(-5F, 0F, -3F, 10F, 1F, 8F), PartPose.offset(0F, 2F, 2F));
		PartDefinition p_SaddleB = root.addOrReplaceChild("SaddleB", CubeListBuilder.create().texOffs(106, 9).mirror(false).addBox(-1.5F, -1F, -3F, 3F, 1F, 2F), PartPose.offset(0F, 2F, 2F));
		PartDefinition p_SaddleC = root.addOrReplaceChild("SaddleC", CubeListBuilder.create().texOffs(80, 9).mirror(false).addBox(-4F, -1F, 3F, 8F, 1F, 2F), PartPose.offset(0F, 2F, 2F));
		PartDefinition p_SaddleL2 = root.addOrReplaceChild("SaddleL2", CubeListBuilder.create().texOffs(74, 0).mirror(false).addBox(-0.5F, 6F, -1F, 1F, 2F, 2F), PartPose.offset(5F, 3F, 2F));
		PartDefinition p_SaddleL = root.addOrReplaceChild("SaddleL", CubeListBuilder.create().texOffs(70, 0).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offset(5F, 3F, 2F));
		PartDefinition p_SaddleR2 = root.addOrReplaceChild("SaddleR2", CubeListBuilder.create().texOffs(74, 4).mirror(false).addBox(-0.5F, 6F, -1F, 1F, 2F, 2F), PartPose.offset(-5F, 3F, 2F));
		PartDefinition p_SaddleR = root.addOrReplaceChild("SaddleR", CubeListBuilder.create().texOffs(80, 0).mirror(false).addBox(-0.5F, 0F, -0.5F, 1F, 6F, 1F), PartPose.offset(-5F, 3F, 2F));
		PartDefinition p_SaddleMouthL = root.addOrReplaceChild("SaddleMouthL", CubeListBuilder.create().texOffs(74, 13).mirror(false).addBox(1.5F, -8F, -4F, 1F, 2F, 2F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_SaddleMouthR = root.addOrReplaceChild("SaddleMouthR", CubeListBuilder.create().texOffs(74, 13).mirror(false).addBox(-2.5F, -8F, -4F, 1F, 2F, 2F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_SaddleMouthLine = root.addOrReplaceChild("SaddleMouthLine", CubeListBuilder.create().texOffs(44, 10).mirror(false).addBox(2.6F, -6F, -6F, 0F, 3F, 16F), PartPose.offset(0F, 4F, -10F));
		PartDefinition p_SaddleMouthLineR = root.addOrReplaceChild("SaddleMouthLineR", CubeListBuilder.create().texOffs(44, 5).mirror(false).addBox(-2.6F, -6F, -6F, 0F, 3F, 16F), PartPose.offset(0F, 4F, -10F));
		PartDefinition p_Mane = root.addOrReplaceChild("Mane", CubeListBuilder.create().texOffs(58, 0).mirror(false).addBox(-1F, -11.5F, 5F, 2F, 16F, 4F), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		PartDefinition p_HeadSaddle = root.addOrReplaceChild("HeadSaddle", CubeListBuilder.create().texOffs(80, 12).mirror(false).addBox(-2.5F, -10.1F, -7F, 5F, 5F, 12F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0F, 4F, -10F, 0.5235988F, 0F, 0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(BeastState state) {
		super.setupAnim(state);
		boolean saddled = state.saddled;
		this.HeadSaddle.visible = this.Saddle.visible = this.SaddleB.visible = this.SaddleC.visible = this.SaddleL.visible = this.SaddleL2.visible = saddled;
		this.SaddleR.visible = this.SaddleR2.visible = this.SaddleMouthL.visible = this.SaddleMouthR.visible = saddled;
		this.SaddleMouthLine.visible = this.SaddleMouthLineR.visible = saddled && state.ridden;
		this.UMouth.visible = this.LMouth.visible = !state.mouthOpen;
		this.UMouth2.visible = this.LMouth2.visible = state.mouthOpen;
		rig(state.walkAnimationPos, state.walkAnimationSpeed, state.ageInTicks, state.yRot, state.xRot, state.eating, state.ridden, false, state.rearing, state.saddled,
			state.tailMoving, false, false, state.mouthOpen, ZEBRA);
	}

	private void rig(float f, float f1, float f2, float f3, float f4, boolean eating, boolean rider, boolean floating, boolean standing,
		boolean saddled, boolean tail, boolean wings, boolean flapwings, boolean shuffle, int type) {

        float RLegXRot = Mth.cos((f * 0.6662F) + 3.141593F) * 0.8F * f1;
        float LLegXRot = Mth.cos(f * 0.6662F) * 0.8F * f1;
        float HeadXRot = (f4 / 57.29578F);
        if (f3 > 20F) {
            f3 = 20F;
        }
        if (f3 < -20F) {
            f3 = -20F;
        }

        /**
         * f = distance walked f1 = speed 0 - 1 f2 = timer
         */

        if (shuffle) {
            HeadXRot = HeadXRot + (Mth.cos(f2 * 0.4F) * 0.15F);
        } else if (f1 > 0.2F && !floating) {
            HeadXRot = HeadXRot + (Mth.cos(f * 0.4F) * 0.15F * f1);
        }

        this.Head.y = 4.0F;
        this.Head.z = -10F;
        this.Head.xRot = 0.5235988F + (HeadXRot);
        this.Head.yRot = (f3 / 57.29578F);//fixes SMP bug
        this.TailA.y = 3F;
        this.TailB.z = 14F;
        this.Body.xRot = 0F;

        if (standing && !shuffle) //standing on hind legs
        {
            this.Head.y = -6F;
            this.Head.z = -1F;
            this.Head.xRot = (15 / 57.29578F) + (HeadXRot);
            ;//120 degrees
            this.Head.yRot = (f3 / 57.29578F);
            this.TailA.y = 9F;
            this.TailB.z = 18F;
            this.Body.xRot = -45 / 57.29578F;

        } else if (eating && !shuffle)//neck down
        {
            this.Head.y = 11.0F; //new lower position
            this.Head.z = -10F;
            this.Head.xRot = 2.18166F;//120 degrees
            this.Head.yRot = 0.0F;//don't twist your neck if eating

        }

        this.Ear1.y = this.Head.y;
        this.Ear2.y = this.Head.y;
        this.Neck.y = this.Head.y;
        this.UMouth.y = this.Head.y;
        this.UMouth2.y = this.Head.y;
        this.LMouth.y = this.Head.y;
        this.LMouth2.y = this.Head.y;
        this.Mane.y = this.Head.y;

        this.Ear1.z = this.Head.z;
        this.Ear2.z = this.Head.z;
        this.Neck.z = this.Head.z;
        this.UMouth.z = this.Head.z;
        this.UMouth2.z = this.Head.z;
        this.LMouth.z = this.Head.z;
        this.LMouth2.z = this.Head.z;
        this.Mane.z = this.Head.z;

        this.Ear1.xRot = this.Head.xRot;
        this.Ear2.xRot = this.Head.xRot;
        this.Neck.xRot = this.Head.xRot;
        this.UMouth.xRot = this.Head.xRot;
        this.UMouth2.xRot = this.Head.xRot - 0.0872664F;
        this.LMouth.xRot = this.Head.xRot;
        this.LMouth2.xRot = this.Head.xRot + 0.261799F;
        this.Mane.xRot = this.Head.xRot;

        this.Ear1.yRot = this.Head.yRot;
        this.Ear2.yRot = this.Head.yRot;
        this.Neck.yRot = this.Head.yRot;
        this.UMouth.yRot = this.Head.yRot;
        this.LMouth.yRot = this.Head.yRot;
        this.UMouth2.yRot = this.Head.yRot;
        this.LMouth2.yRot = this.Head.yRot;
        this.Mane.yRot = this.Head.yRot;

        //(if chested)

        if (wings) {

            if (standing) {
            } else {
            }

            /**
             * flapping wings or cruising. IF flapping wings, move up and down.
             * if cruising, movement depends of speed
             */
            float WingRot = 0F;
            if (flapwings) {
                WingRot = Mth.cos((f2 * 0.3F) + 3.141593F) * 1.2F;// * f1;
            } else
            //cruising
            {
                //WingRot = Mth.cos((f * 0.6662F) + 3.141593F) * 1.2F * f1;
                WingRot = Mth.cos((f * 0.5F)) * 0.1F;//* 1.2F * f1;
            }

            //float WingRot = Mth.cos((f * 0.6662F) + 3.141593F) * f1 * 1.2F;
            //InnerWing.setRotationPoint(5F, 3F, -6F);
            //setRotation(InnerWing, 0F, -0.3490659F, 0F);
            //X dist = 12
            //OuterWing.setRotationPoint(17F, 3F, -6F);
            //setRotation(OuterWing, 0F, -0.3228859F, 0F);

            if (floating) {

            } else {
                WingRot = 60 / 57.29578F;//0.7854F;
            }


            //OuterWing.rotationPointX = InnerWing.rotationPointX + (Mth.cos(WingRot)*12F);
            //the rotation point X rotates depending of the cos of rotation times the distance of the other block:
            //cos (WingRot) * 12F
            //the rotation PointX of Innerwing = 5
            //the rotation PointX of Outerwing = 17
            //the difference = 12.
            // for the rotation point Y, sin is used instead.
            //OuterWing.rotationPointX = InnerWing.rotationPointX + (Mth.cos(WingRot)*12F);
            //OuterWing.rotationPointY = InnerWing.rotationPointY + (Mth.sin(WingRot)*12F);






            //45deg = 0.7854F
            //1.396 (80degrees folded)
            /*
             * //rear left. -4X(ignored), 9Y, 11Z the distance is 7Y
             * Leg1B.rotationPointY = 9F + (Mth.sin((90/ 57.29578F) +
             * LLegXRot )*7F); Leg1B.rotationPointZ = 11F +
             * (Mth.cos((270/ 57.29578F) + LLegXRot )*7F); //rear right
             * Leg2B.rotationPointY = 9F + (Mth.sin((90/ 57.29578F) +
             * RLegXRot )*7F); Leg2B.rotationPointZ = 11F +
             * (Mth.cos((270/ 57.29578F) + RLegXRot )*7F); //front left
             * 4X(ign), 9Y, -8Z, the distance is again 7Y Leg3B.rotationPointY =
             * 9F + (Mth.sin((90/ 57.29578F) + RLegXRot )*7F);
             * Leg3B.rotationPointZ = -8F + (Mth.cos((270/ 57.29578F) +
             * RLegXRot )*7F); //front right -4X(ign), 9Y, -8Z, the distance is
             * again 7Y Leg4B.rotationPointY = 9F + (Mth.sin((90/
             * 57.29578F) + LLegXRot )*7F); Leg4B.rotationPointZ = -8F +
             * (Mth.cos((270/ 57.29578F) + LLegXRot )*7F);
             */

        }

        if (type > 44 && type < 60 || type == 21) //butterfly horses or ghost horse
        {
            /**
             * buttefly to have two / 3 movs: 1 slow movement when idle on
             * ground has to be random from closing up to horizontal 2 fast wing
             * flapping flying movement, short range close to 0 degree RLegXRot
             * = Mth.cos((f * 0.6662F) + 3.141593F) * 0.8F * f1;
             */

            /**
             * f = distance walked f1 = speed 0 - 1 f2 = timer
             */

            float f2a = f2 % 100F;
            float WingRot = 0F;

            if (type != 21) {
                //for butterfly horses
                if (flapwings) //when user hits space or randomly
                {
                    WingRot = Mth.cos((f2 * 0.9F)) * 0.9F;

                } else
                //default movement
                {
                    if (floating) //cruising
                    {
                        WingRot = Mth.cos((f2 * 0.6662F)) * 0.5F;
                    } else {
                        if (f2a > 40 & f2a < 60) //random movement
                        {
                            WingRot = Mth.cos((f2 * 0.15F)) * 1.20F;
                        }
                    }

                }
            }

            else
            //for ghost horse
            {
                WingRot = Mth.cos((f2 * 0.1F));//* 0.2F;
            }

            //from regular horse
            /*
             * if (flapwings) { WingRot = Mth.cos((f2 * 0.3F) +
             * 3.141593F) * 1.2F;// * f1; }else //cruising { //WingRot =
             * Mth.cos((f * 0.6662F) + 3.141593F) * 1.2F * f1; WingRot =
             * Mth.cos((f * 0.5F)) *0.1F ;//* 1.2F * f1; }
             */

            /**
             * this part is needed for position and angle of the butterfly wings
             * and ghost horse wings
             */
            if (standing) {
            } else {
            }


            //this to be added for the ghost or adjusted
            //ButterflyL.rotateAngleZ = -0.52359F + RLegXRot;
            //ButterflyR.rotateAngleZ = 0.52359F -RLegXRot;

            float baseAngle = 0.52359F;
            if (type == 21) {
                baseAngle = 0F;
            }
        }

        /**
         * knee joints Leg1 and Leg4 use LLegXRot Leg2 and Leg3 use RLegXRot
         */
        //RLegXRot = 45/57.29578F;
        //fMov1 = RLegXRot;
        float RLegXRotB = RLegXRot;
        float LLegXRotB = LLegXRot;
        float RLegXRotC = RLegXRot;
        float LLegXRotC = LLegXRot;

        if (floating) {
            RLegXRot = (15 / 57.29578F);
            LLegXRot = RLegXRot;
            RLegXRotB = (45 / 57.29578F);
            RLegXRotC = RLegXRotB;
            LLegXRotB = RLegXRotB;
            LLegXRotC = RLegXRotB;
        }

        if (standing) {
            this.Leg3A.y = -2F;
            this.Leg3A.z = -2F;
            this.Leg4A.y = this.Leg3A.y;
            this.Leg4A.z = this.Leg3A.z;

            RLegXRot = (-60 / 57.29578F) + Mth.cos((f2 * 0.4F) + 3.141593F);
            LLegXRot = (-60 / 57.29578F) + Mth.cos(f2 * 0.4F);

            RLegXRotB = (45 / 57.29578F);
            LLegXRotB = RLegXRotB;

            RLegXRotC = (-15 / 57.29578F);
            LLegXRotC = (15 / 57.29578F);

            this.Leg3B.y = this.Leg3A.y + (Mth.sin((90 / 57.29578F) + RLegXRot) * 7F);
            this.Leg3B.z = this.Leg3A.z + (Mth.cos((270 / 57.29578F) + RLegXRot) * 7F);

            //front right -4X(ign), 9Y, -8Z, the distance is again 7Y
            this.Leg4B.y = this.Leg4A.y + (Mth.sin((90 / 57.29578F) + LLegXRot) * 7F);
            this.Leg4B.z = this.Leg4A.z + (Mth.cos((270 / 57.29578F) + LLegXRot) * 7F);

            this.Leg1B.y = this.Leg1A.y + (Mth.sin((90 / 57.29578F) + RLegXRotC) * 7F);
            this.Leg1B.z = this.Leg1A.z + (Mth.cos((270 / 57.29578F) + RLegXRotC) * 7F);

            //rear right
            this.Leg2B.y = this.Leg1B.y;// Leg2A.rotationPointY + (Mth.sin((90/ 57.29578F) + RLegXRotC )*7F);
            this.Leg2B.z = this.Leg1B.z;//Leg2A.rotationPointZ + (Mth.cos((270/ 57.29578F) + RLegXRotC )*7F);

            this.Leg1A.xRot = RLegXRotC;
            this.Leg1B.xRot = LLegXRotC;
            this.Leg1C.xRot = this.Leg1B.xRot;

            this.Leg2A.xRot = RLegXRotC;
            this.Leg2B.xRot = LLegXRotC;
            this.Leg2C.xRot = this.Leg2B.xRot;

            this.Leg3A.xRot = RLegXRot;
            this.Leg3B.xRot = RLegXRotB;
            this.Leg3C.xRot = RLegXRotB;

            this.Leg4A.xRot = LLegXRot;
            this.Leg4B.xRot = LLegXRotB;
            this.Leg4C.xRot = LLegXRotB;
        }

        else
        //not standing
        {
            this.Leg3A.y = 9F;
            this.Leg3A.z = -8F;
            this.Leg4A.y = this.Leg3A.y;
            this.Leg4A.z = this.Leg3A.z;

            if (!floating && f1 > 0.2F) {

                float RLegXRot2 = Mth.cos(((f + 0.1F) * 0.6662F) + 3.141593F) * 0.8F * f1;
                float LLegXRot2 = Mth.cos((f + 0.1F) * 0.6662F) * 0.8F * f1;
                if (RLegXRot > RLegXRot2) // - - >
                {
                    RLegXRotB = RLegXRot + (55 / 57.29578F);
                    //LLegXRotB = LLegXRot + (55/57.29578F);

                }
                if (RLegXRot < RLegXRot2) // < - -
                {
                    RLegXRotC = RLegXRot + (15 / 57.29578F);
                    //LLegXRotC = LLegXRot - (15/57.29578F);

                }
                if (LLegXRot > LLegXRot2) // - - >
                {
                    LLegXRotB = LLegXRot + (55 / 57.29578F);
                }
                if (LLegXRot < LLegXRot2) // < - -
                {
                    LLegXRotC = LLegXRot + (15 / 57.29578F);
                }

            }

            this.Leg1B.y = this.Leg1A.y + (Mth.sin((90 / 57.29578F) + LLegXRot) * 7F);
            this.Leg1B.z = this.Leg1A.z + (Mth.cos((270 / 57.29578F) + LLegXRot) * 7F);

            //rear right
            this.Leg2B.y = this.Leg2A.y + (Mth.sin((90 / 57.29578F) + RLegXRot) * 7F);
            this.Leg2B.z = this.Leg2A.z + (Mth.cos((270 / 57.29578F) + RLegXRot) * 7F);

            //front left 4X(ign), 9Y, -8Z, the distance is again 7Y
            this.Leg3B.y = this.Leg3A.y + (Mth.sin((90 / 57.29578F) + RLegXRot) * 7F);
            this.Leg3B.z = this.Leg3A.z + (Mth.cos((270 / 57.29578F) + RLegXRot) * 7F);

            //front right -4X(ign), 9Y, -8Z, the distance is again 7Y
            this.Leg4B.y = this.Leg4A.y + (Mth.sin((90 / 57.29578F) + LLegXRot) * 7F);
            this.Leg4B.z = this.Leg4A.z + (Mth.cos((270 / 57.29578F) + LLegXRot) * 7F);

            this.Leg1A.xRot = LLegXRot;
            this.Leg1B.xRot = LLegXRotC;
            this.Leg1C.xRot = LLegXRotC;

            this.Leg2A.xRot = RLegXRot;
            this.Leg2B.xRot = RLegXRotC;
            this.Leg2C.xRot = RLegXRotC;

            this.Leg3A.xRot = RLegXRot;
            this.Leg3B.xRot = RLegXRotB;
            this.Leg3C.xRot = RLegXRotB;

            this.Leg4A.xRot = LLegXRot;
            this.Leg4B.xRot = LLegXRotB;
            this.Leg4C.xRot = LLegXRotB;

        }

        if (type == 60 && shuffle) {
            this.Leg3A.y = 9F;
            this.Leg3A.z = -8F;
            this.Leg4A.y = this.Leg3A.y;
            this.Leg4A.z = this.Leg3A.z;

            if (!floating)//&& f1 > 0.2F)
            {

                //float RLegXRot2 = Mth.cos(f2 * 0.4F);//Mth.cos(((f+0.1F) * 0.6662F) + 3.141593F) * 1.4F * f1;
                RLegXRot = Mth.cos(f2 * 0.4F);
                if (RLegXRot > 0.1F) {
                    RLegXRot = 0.3F;
                }
                //if (RLegXRot < -0.5F) RLegXRotB = RLegXRot + (45/57.29578F);
                LLegXRot = Mth.cos((f2 * 0.4F) + 3.141593F);
                if (LLegXRot > 0.1F) {
                    LLegXRot = 0.3F;
                }

                /*
                 * if (RLegXRot > RLegXRot2) // - - > { RLegXRotB = RLegXRot +
                 * (55/57.29578F); LLegXRotB = LLegXRot + (55/57.29578F); } if
                 * (RLegXRot < RLegXRot2) // < - - { RLegXRotC = RLegXRot -
                 * (15/57.29578F); LLegXRotC = LLegXRot - (15/57.29578F); }
                 */

                /*
                 * float RLegXRot2 = Mth.cos(((f+0.1F) * 0.6662F) +
                 * 3.141593F) * 1.4F * f1; if (RLegXRot > RLegXRot2) // - - > {
                 * RLegXRotB = RLegXRot + (55/57.29578F); LLegXRotB = LLegXRot +
                 * (55/57.29578F); } if (RLegXRot < RLegXRot2) // < - - {
                 * RLegXRotC = RLegXRot - (15/57.29578F); LLegXRotC = LLegXRot -
                 * (15/57.29578F); }
                 */
            }

            this.Leg1B.y = this.Leg1A.y + (Mth.sin((90 / 57.29578F) + LLegXRot) * 7F);
            this.Leg1B.z = this.Leg1A.z + (Mth.cos((270 / 57.29578F) + LLegXRot) * 7F);

            //rear right
            this.Leg2B.y = this.Leg2A.y + (Mth.sin((90 / 57.29578F) + RLegXRot) * 7F);
            this.Leg2B.z = this.Leg2A.z + (Mth.cos((270 / 57.29578F) + RLegXRot) * 7F);

            //front left 4X(ign), 9Y, -8Z, the distance is again 7Y
            this.Leg3B.y = this.Leg3A.y + (Mth.sin((90 / 57.29578F) + LLegXRot) * 7F);
            this.Leg3B.z = this.Leg3A.z + (Mth.cos((270 / 57.29578F) + LLegXRot) * 7F);

            //front right -4X(ign), 9Y, -8Z, the distance is again 7Y
            this.Leg4B.y = this.Leg4A.y + (Mth.sin((90 / 57.29578F) + RLegXRot) * 7F);
            this.Leg4B.z = this.Leg4A.z + (Mth.cos((270 / 57.29578F) + RLegXRot) * 7F);

            this.Leg1A.xRot = LLegXRot;// + rand.nextFloat();
            this.Leg1B.xRot = LLegXRotB;
            this.Leg1C.xRot = LLegXRotB;

            this.Leg3A.xRot = LLegXRot;
            this.Leg3B.xRot = LLegXRotB;
            this.Leg3C.xRot = LLegXRotB;

            this.Leg2A.xRot = RLegXRot;
            this.Leg2B.xRot = RLegXRotB;
            this.Leg2C.xRot = RLegXRotB;

            this.Leg4A.xRot = RLegXRot;
            this.Leg4B.xRot = RLegXRotB;
            this.Leg4C.xRot = RLegXRotB;

        }

        this.Leg1C.y = this.Leg1B.y;
        this.Leg1C.z = this.Leg1B.z;
        this.Leg2C.y = this.Leg2B.y;
        this.Leg2C.z = this.Leg2B.z;
        this.Leg3C.y = this.Leg3B.y;
        this.Leg3C.z = this.Leg3B.z;
        this.Leg4C.y = this.Leg4B.y;
        this.Leg4C.z = this.Leg4B.z;

        /*
         * //rear left. -4X(ignored), 9Y, 11Z the distance is 7Y
         * Leg1B.rotationPointY = 9F + (Mth.sin((90/ 57.29578F) +
         * LLegXRot )*7F); Leg1B.rotationPointZ = 11F + (Mth.cos((270/
         * 57.29578F) + LLegXRot )*7F); //rear right Leg2B.rotationPointY = 9F +
         * (Mth.sin((90/ 57.29578F) + RLegXRot )*7F);
         * Leg2B.rotationPointZ = 11F + (Mth.cos((270/ 57.29578F) +
         * RLegXRot )*7F); //front left 4X(ign), 9Y, -8Z, the distance is again
         * 7Y Leg3B.rotationPointY = 9F + (Mth.sin((90/ 57.29578F) +
         * RLegXRot )*7F); Leg3B.rotationPointZ = -8F + (Mth.cos((270/
         * 57.29578F) + RLegXRot )*7F); //front right -4X(ign), 9Y, -8Z, the
         * distance is again 7Y Leg4B.rotationPointY = 9F + (Mth.sin((90/
         * 57.29578F) + LLegXRot )*7F); Leg4B.rotationPointZ = -8F +
         * (Mth.cos((270/ 57.29578F) + LLegXRot )*7F);
         */

        if (saddled) {

            if (standing) {
                this.Saddle.y = 0.5F;
                this.Saddle.z = 11F;

            } else {
                this.Saddle.y = 2F;
                this.Saddle.z = 2F;

            }

            this.SaddleB.y = this.Saddle.y;
            this.SaddleC.y = this.Saddle.y;
            this.SaddleL.y = this.Saddle.y;
            this.SaddleR.y = this.Saddle.y;
            this.SaddleL2.y = this.Saddle.y;
            this.SaddleR2.y = this.Saddle.y;

            this.SaddleB.z = this.Saddle.z;
            this.SaddleC.z = this.Saddle.z;
            this.SaddleL.z = this.Saddle.z;
            this.SaddleR.z = this.Saddle.z;
            this.SaddleL2.z = this.Saddle.z;
            this.SaddleR2.z = this.Saddle.z;

            this.Saddle.xRot = this.Body.xRot;
            this.SaddleB.xRot = this.Body.xRot;
            this.SaddleC.xRot = this.Body.xRot;

            this.SaddleMouthLine.y = this.Head.y;
            this.SaddleMouthLineR.y = this.Head.y;
            this.HeadSaddle.y = this.Head.y;
            this.SaddleMouthL.y = this.Head.y;
            this.SaddleMouthR.y = this.Head.y;

            this.SaddleMouthLine.z = this.Head.z;
            this.SaddleMouthLineR.z = this.Head.z;
            this.HeadSaddle.z = this.Head.z;
            this.SaddleMouthL.z = this.Head.z;
            this.SaddleMouthR.z = this.Head.z;

            this.SaddleMouthLine.xRot = HeadXRot;
            this.SaddleMouthLineR.xRot = HeadXRot;
            this.HeadSaddle.xRot = this.Head.xRot;
            this.SaddleMouthL.xRot = this.Head.xRot;
            this.SaddleMouthR.xRot = this.Head.xRot;
            this.HeadSaddle.yRot = this.Head.yRot;
            this.SaddleMouthL.yRot = this.Head.yRot;
            this.SaddleMouthLine.yRot = this.Head.yRot;
            this.SaddleMouthR.yRot = this.Head.yRot;
            this.SaddleMouthLineR.yRot = this.Head.yRot;

            if (rider) {
                this.SaddleL.xRot = -60 / 57.29578F;
                this.SaddleL2.xRot = -60 / 57.29578F;
                this.SaddleR.xRot = -60 / 57.29578F;
                this.SaddleR2.xRot = -60 / 57.29578F;

                this.SaddleL.zRot = 0F;
                this.SaddleL2.zRot = 0F;
                this.SaddleR.zRot = 0F;
                this.SaddleR2.zRot = 0F;
                ;
            } else {
                this.SaddleL.xRot = RLegXRot / 3F;
                this.SaddleL2.xRot = RLegXRot / 3F;
                this.SaddleR.xRot = RLegXRot / 3F;
                this.SaddleR2.xRot = RLegXRot / 3F;

                this.SaddleL.zRot = RLegXRot / 5F;
                this.SaddleL2.zRot = RLegXRot / 5F;
                this.SaddleR.zRot = -RLegXRot / 5F;
                this.SaddleR2.zRot = -RLegXRot / 5F;
            }
        }

        //f1 = movement speed!
        //f2 = timer!

        float tailMov = -1.3089F + (f1 * 1.5F);
        if (tailMov > 0) {
            tailMov = 0;
        }

        if (tail) {
            this.TailA.yRot = Mth.cos(f2 * 0.7F);
            tailMov = 0;
        } else {
            this.TailA.yRot = 0F;
        }
        this.TailB.yRot = this.TailA.yRot;
        this.TailC.yRot = this.TailA.yRot;

        this.TailB.y = this.TailA.y;
        this.TailC.y = this.TailA.y;
        this.TailB.z = this.TailA.z;
        this.TailC.z = this.TailA.z;

        this.TailA.xRot = tailMov;//-1.3089F+(f1*1.5F);
        this.TailB.xRot = tailMov;//-1.3089F+(f1*1.5F);
        this.TailC.xRot = -0.2618F + tailMov;//-1.5707F -tailMov;

    
	}
}
