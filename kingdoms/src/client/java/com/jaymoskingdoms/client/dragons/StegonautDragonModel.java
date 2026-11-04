package com.jaymoskingdoms.client.dragons;

import com.jaymoskingdoms.dragons.DragonLook;
import com.jaymoskingdoms.dragons.StegonautDragon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * (0.7.511) Stegonaut, our own model (blocks, as the Kingdoms beasts are; nothing of Saint's Dragons' art is used, see
 * {@code docs/SaintsDragons-Plan.md}): a tall, plated dragon high on four long pillar legs with long shins (Andy: "longer
 * limbs and necks, and be taller, to be imposing", "longer cannons"). A deep body, two staggered rows of tall horn
 * plates down its back (the tallest over its hips), a long neck raised up and forward in four segments, a heavy blunt
 * head with a bony brow and a broad shovel of a jaw, horns swept back (long, short, or long with a nose horn, by its
 * {@link DragonLook}), and a long drooping tail ending in four spikes. Its riders' saddle, girth and pillion pad show
 * when saddled (the middle three plate pairs give way to them). Texture 256x256 ({@code tools/gen_stegonaut.py} prints the
 * body plan below from its own table, so the UVs match the paint).
 * <p>
 * Posed procedurally from its render state: it plods with its diagonal legs together, head bobbing and tail swaying;
 * its moves ({@link StegonautDragon#action}): the chin slam drives the head down and snaps it up, the ground slam rears
 * the forequarters and stamps them down, the ground eating noses into the earth and chews, and the roar throws the head
 * up with the jaw wide and the plates flared. Crashed (worn down) it sinks to its belly, legs splayed and neck down; sitting, it settles on its hind legs.
 */
final class StegonautDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How low it sinks crashed and sitting (model pixels). */
	static final float CRASH_DROP = 16.0F, SIT_DROP = 8.0F;
	/** The plates' lean (radians, outward) and how far the roar flares them. */
	static final float PLATE_LEAN = 0.16F, ROAR_FLARE = 0.22F;

	private final ModelPart body, head, jaw, snout;
	private final ModelPart[] neck = new ModelPart[4];
	private final ModelPart hornL, hornR, shortL, shortR, noseHorn;
	private final ModelPart[] tail = new ModelPart[5];
	/** The neck's and the tail's rest angles (as built), which the poses below add to. */
	private final float[] neckRest = new float[4], tailRest = new float[5];
	private final float headRest;
	private final ModelPart[] platesL = new ModelPart[DragonLook.SPIKES], platesR = new ModelPart[DragonLook.SPIKES];
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final float bodyY;

	StegonautDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		ModelPart up = this.body;
		for (int i = 0; i < 4; i++) {
			this.neck[i] = up.getChild("neck" + i);
			this.neckRest[i] = this.neck[i].xRot;
			up = this.neck[i];
		}
		this.head = up.getChild("head");
		this.headRest = this.head.xRot;
		this.jaw = this.head.getChild("jaw");
		this.snout = this.head.getChild("snout");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.shortL = this.head.getChild("short_horn_l");
		this.shortR = this.head.getChild("short_horn_r");
		this.noseHorn = this.snout.getChild("nose_horn");
		ModelPart parent = this.body;
		for (int i = 0; i < 5; i++) {
			this.tail[i] = parent.getChild("tail" + i);
			this.tailRest[i] = this.tail[i].xRot;
			parent = this.tail[i];
		}
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			this.platesL[i] = this.body.getChild("plate" + i + "_l");
			this.platesR[i] = this.body.getChild("plate" + i + "_r");
		}
		this.saddle = this.body.getChild("saddle");
		this.strap = this.body.getChild("strap");
		this.pad = this.body.getChild("pad");
		String[] legs = {"left_front", "right_front", "left_hind", "right_hind"};
		for (int i = 0; i < 4; i++) {
			this.upper[i] = this.body.getChild(legs[i] + "_leg");
			this.lower[i] = this.upper[i].getChild(legs[i] + "_shin");
			this.foot[i] = this.lower[i].getChild(legs[i] + "_foot");
		}
	}

	private static CubeListBuilder box(int u, int v, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
	}

	private static CubeListBuilder mirrored(int u, int v, float x, float y, float z, float w, float h, float d) {
		return CubeListBuilder.create().texOffs(u, v).mirror().addBox(x, y, z, w, h, d);
	}

	static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		// (printed by tools/gen_stegonaut.py)
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -12.0F, -8.0F, -18.0F, 24, 16, 36), PartPose.offset(0.0F, -4.0F, 0.0F));
		body.addOrReplaceChild("belly", box(120, 0, -10.0F, 0.0F, -13.0F, 20, 3, 26), PartPose.offset(0.0F, 8.0F, 0.0F));
		PartDefinition neck0 = body.addOrReplaceChild("neck0", box(56, 75, -6.5F, -5.5F, -8.0F, 13, 11, 8), PartPose.offsetAndRotation(0.0F, -3.0F, -17.0F, -0.55F, 0.0F, 0.0F));
		PartDefinition neck1 = neck0.addOrReplaceChild("neck1", box(0, 95, -6.0F, -5.0F, -7.0F, 12, 10, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -7.0F, -0.25F, 0.0F, 0.0F));
		PartDefinition neck2 = neck1.addOrReplaceChild("neck2", box(110, 95, -5.5F, -4.5F, -7.0F, 11, 9, 7), PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, -0.1F, 0.0F, 0.0F));
		PartDefinition neck3 = neck2.addOrReplaceChild("neck3", box(206, 95, -5.0F, -4.5F, -6.0F, 10, 9, 6), PartPose.offsetAndRotation(0.0F, 0.0F, -6.0F, 0.1F, 0.0F, 0.0F));
		PartDefinition head = neck3.addOrReplaceChild("head", box(98, 75, -7.0F, -5.0F, -10.0F, 14, 9, 10), PartPose.offsetAndRotation(0.0F, 0.0F, -5.0F, 0.75F, 0.0F, 0.0F));
		PartDefinition snout = head.addOrReplaceChild("snout", box(76, 112, -5.0F, -3.0F, -7.0F, 10, 6, 7), PartPose.offset(0.0F, 0.0F, -10.0F));
		head.addOrReplaceChild("brow", box(14, 127, -7.5F, -1.5F, -2.0F, 15, 3, 4), PartPose.offset(0.0F, -4.5F, -7.0F));
		head.addOrReplaceChild("jaw", box(146, 75, -5.5F, 0.0F, -16.0F, 11, 3, 16), PartPose.offset(0.0F, 4.0F, 0.0F));
		head.addOrReplaceChild("eye_l", box(52, 127, -0.5F, -1.5F, -1.5F, 1, 3, 3), PartPose.offset(7.0F, -2.0F, -6.5F));
		head.addOrReplaceChild("eye_r", box(52, 127, -0.5F, -1.5F, -1.5F, 1, 3, 3), PartPose.offset(-7.0F, -2.0F, -6.5F));
		head.addOrReplaceChild("horn_l", box(200, 112, -1.0F, -1.0F, 0.0F, 2, 2, 9), PartPose.offsetAndRotation(5.5F, -4.5F, -4.0F, 0.45F, 0.35F, 0.0F));
		head.addOrReplaceChild("horn_r", box(200, 112, -1.0F, -1.0F, 0.0F, 2, 2, 9), PartPose.offsetAndRotation(-5.5F, -4.5F, -4.0F, 0.45F, -0.35F, 0.0F));
		head.addOrReplaceChild("short_horn_l", box(60, 127, -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offsetAndRotation(5.5F, -4.5F, -4.0F, 0.5F, 0.35F, 0.0F));
		head.addOrReplaceChild("short_horn_r", box(60, 127, -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offsetAndRotation(-5.5F, -4.5F, -4.0F, 0.5F, -0.35F, 0.0F));
		snout.addOrReplaceChild("nose_horn", box(72, 127, -1.0F, -4.0F, -1.0F, 2, 4, 2), PartPose.offsetAndRotation(0.0F, -3.0F, -4.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition tail0 = body.addOrReplaceChild("tail0", box(152, 52, -6.0F, -5.5F, 0.0F, 12, 11, 10), PartPose.offsetAndRotation(0.0F, -2.0F, 17.0F, 0.18F, 0.0F, 0.0F));
		PartDefinition tail1 = tail0.addOrReplaceChild("tail1", box(200, 75, -5.0F, -4.5F, 0.0F, 10, 9, 10), PartPose.offsetAndRotation(0.0F, 0.5F, 9.0F, 0.08F, 0.0F, 0.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", box(38, 95, -4.0F, -3.5F, 0.0F, 8, 7, 10), PartPose.offset(0.0F, 0.5F, 9.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", box(0, 112, -3.0F, -3.0F, 0.0F, 6, 6, 9), PartPose.offsetAndRotation(0.0F, 0.5F, 9.0F, -0.04F, 0.0F, 0.0F));
		PartDefinition tail4 = tail3.addOrReplaceChild("tail4", box(30, 112, -2.5F, -2.5F, 0.0F, 5, 5, 9), PartPose.offsetAndRotation(0.0F, 0.5F, 8.0F, -0.06F, 0.0F, 0.0F));
		tail4.addOrReplaceChild("thag0", box(222, 112, -1.0F, -1.0F, 0.0F, 2, 2, 9), PartPose.offsetAndRotation(2.0F, -1.5F, 5.0F, -0.7F, 1.1F, 0.0F));
		tail4.addOrReplaceChild("thag1", box(222, 112, -1.0F, -1.0F, 0.0F, 2, 2, 9), PartPose.offsetAndRotation(-2.0F, -1.5F, 5.0F, -0.7F, -1.1F, 0.0F));
		tail4.addOrReplaceChild("thag2", box(222, 112, -1.0F, -1.0F, 0.0F, 2, 2, 8), PartPose.offsetAndRotation(2.0F, -1.5F, 1.0F, -0.9F, 0.9F, 0.0F));
		tail4.addOrReplaceChild("thag3", box(222, 112, -1.0F, -1.0F, 0.0F, 2, 2, 8), PartPose.offsetAndRotation(-2.0F, -1.5F, 1.0F, -0.9F, -0.9F, 0.0F));
		tail0.addOrReplaceChild("tail_plate0", box(58, 112, -1.0F, -7.0F, -3.5F, 2, 7, 7), PartPose.offset(0.0F, -5.5F, 5.0F));
		tail1.addOrReplaceChild("tail_plate1", box(0, 127, -1.0F, -5.0F, -2.5F, 2, 5, 5), PartPose.offset(0.0F, -4.5F, 5.0F));
		PartDefinition saddle = body.addOrReplaceChild("saddle", box(146, 95, -8.0F, -2.0F, -7.0F, 16, 2, 14), PartPose.offset(0.0F, -8.0F, -1.0F));
		saddle.addOrReplaceChild("pommel", box(80, 127, -2.0F, -3.0F, 0.0F, 4, 3, 2), PartPose.offset(0.0F, -2.0F, -7.0F));
		saddle.addOrReplaceChild("cantle", box(92, 127, -6.0F, -3.0F, 0.0F, 12, 3, 2), PartPose.offset(0.0F, -2.0F, 5.0F));
		body.addOrReplaceChild("strap", box(0, 75, -12.5F, -8.5F, -1.5F, 25, 17, 3), PartPose.offset(0.0F, 0.0F, -1.0F));
		body.addOrReplaceChild("pad", box(152, 112, -7.0F, -2.0F, -5.0F, 14, 2, 10), PartPose.offset(0.0F, -8.0F, 9.0F));
		body.addOrReplaceChild("plate0_l", box(74, 95, -1.0F, -10.0F, -3.5F, 2, 10, 7), PartPose.offsetAndRotation(2.5F, -7.0F, -12.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate0_r", mirrored(74, 95, -1.0F, -10.0F, -3.5F, 2, 10, 7), PartPose.offsetAndRotation(-2.5F, -7.0F, -9.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate1_l", box(0, 52, -1.0F, -14.0F, -4.5F, 2, 14, 9), PartPose.offsetAndRotation(2.5F, -7.0F, -5.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate1_r", mirrored(0, 52, -1.0F, -14.0F, -4.5F, 2, 14, 9), PartPose.offsetAndRotation(-2.5F, -7.0F, -2.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate2_l", box(212, 0, -1.0F, -17.0F, -5.5F, 2, 17, 11), PartPose.offsetAndRotation(2.5F, -7.0F, 2.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate2_r", mirrored(212, 0, -1.0F, -17.0F, -5.5F, 2, 17, 11), PartPose.offsetAndRotation(-2.5F, -7.0F, 4.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate3_l", box(22, 52, -1.0F, -14.0F, -4.5F, 2, 14, 9), PartPose.offsetAndRotation(2.5F, -7.0F, 9.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate3_r", mirrored(22, 52, -1.0F, -14.0F, -4.5F, 2, 14, 9), PartPose.offsetAndRotation(-2.5F, -7.0F, 11.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate4_l", box(92, 95, -1.0F, -10.0F, -3.5F, 2, 10, 7), PartPose.offsetAndRotation(2.5F, -7.0F, 15.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate4_r", mirrored(92, 95, -1.0F, -10.0F, -3.5F, 2, 10, 7), PartPose.offsetAndRotation(-2.5F, -7.0F, 17.5F, 0.0F, 0.0F, -0.16F));
		PartDefinition leftfrontleg = body.addOrReplaceChild("left_front_leg", box(84, 52, -4.5F, -3.0F, -4.5F, 9, 13, 9), PartPose.offset(9.0F, 2.0F, -11.0F));
		PartDefinition leftfrontshin = leftfrontleg.addOrReplaceChild("left_front_shin", box(196, 52, -3.5F, 0.0F, -3.5F, 7, 14, 7), PartPose.offset(0.0F, 10.0F, 0.0F));
		leftfrontshin.addOrReplaceChild("left_front_foot", box(110, 112, -5.0F, 0.0F, -6.5F, 10, 2, 11), PartPose.offset(0.0F, 14.0F, 0.0F));
		PartDefinition rightfrontleg = body.addOrReplaceChild("right_front_leg", mirrored(84, 52, -4.5F, -3.0F, -4.5F, 9, 13, 9), PartPose.offset(-9.0F, 2.0F, -11.0F));
		PartDefinition rightfrontshin = rightfrontleg.addOrReplaceChild("right_front_shin", mirrored(196, 52, -3.5F, 0.0F, -3.5F, 7, 14, 7), PartPose.offset(0.0F, 10.0F, 0.0F));
		rightfrontshin.addOrReplaceChild("right_front_foot", mirrored(110, 112, -5.0F, 0.0F, -6.5F, 10, 2, 11), PartPose.offset(0.0F, 14.0F, 0.0F));
		PartDefinition lefthindleg = body.addOrReplaceChild("left_hind_leg", box(44, 52, -5.0F, -3.0F, -5.0F, 10, 13, 10), PartPose.offset(9.5F, 2.0F, 12.0F));
		PartDefinition lefthindshin = lefthindleg.addOrReplaceChild("left_hind_shin", box(120, 52, -4.0F, 0.0F, -4.0F, 8, 14, 8), PartPose.offset(0.0F, 10.0F, 0.0F));
		lefthindshin.addOrReplaceChild("left_hind_foot", box(110, 112, -5.0F, 0.0F, -6.5F, 10, 2, 11), PartPose.offset(0.0F, 14.0F, 0.0F));
		PartDefinition righthindleg = body.addOrReplaceChild("right_hind_leg", mirrored(44, 52, -5.0F, -3.0F, -5.0F, 10, 13, 10), PartPose.offset(-9.5F, 2.0F, 12.0F));
		PartDefinition righthindshin = righthindleg.addOrReplaceChild("right_hind_shin", mirrored(120, 52, -4.0F, 0.0F, -4.0F, 8, 14, 8), PartPose.offset(0.0F, 10.0F, 0.0F));
		righthindshin.addOrReplaceChild("right_hind_foot", mirrored(110, 112, -5.0F, 0.0F, -6.5F, 10, 2, 11), PartPose.offset(0.0F, 14.0F, 0.0F));

		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float crash = s.crash, swim = s.swim * (1.0F - crash);
		float sit = s.sit * (1.0F - crash) * (1.0F - swim);
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * (1.0F - crash) * (1.0F - sit);
		float step = s.walkAnimationPos * 0.55F;
		float headYaw = Mth.clamp(s.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD;
		float share = StegonautDragon.actionShare(s.action, s.actionAge);

		// the moves, each a 0..1 envelope over its length
		float chin = s.action == StegonautDragon.CHIN_SLAM ? share : 0.0F;
		float slam = s.action == StegonautDragon.GROUND_SLAM ? share : 0.0F;
		float chew = s.action == StegonautDragon.CHEW ? share : 0.0F;
		float roar = s.action == StegonautDragon.ROAR ? Mth.sin(share * Mth.PI) : 0.0F;
		// the slam: rear up through the wind-up (to 0.6), then crash down
		float rear = slam <= 0.0F ? 0.0F : slam < 0.6F ? Mth.sin(slam / 0.6F * Mth.HALF_PI) : Math.max(0.0F, 1.0F - (slam - 0.6F) * 6.0F);
		// the chin slam: down, then snapped up
		float chinDown = chin <= 0.0F ? 0.0F : chin < 0.45F ? chin / 0.45F : Math.max(0.0F, 1.0F - (chin - 0.45F) * 4.0F);
		float chinUp = chin < 0.45F ? 0.0F : Mth.sin((chin - 0.45F) / 0.55F * Mth.PI);
		// the ground eating: head down to the earth, then chewing
		float nose = chew <= 0.0F ? 0.0F : Mth.sin(Math.min(1.0F, chew * 1.4F) * Mth.HALF_PI) * (1.0F - Math.max(0.0F, chew - 0.8F) * 5.0F);

		// the body: a heavy plod, sunk crashed, settled back sitting, rearing for the slam
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP + Math.abs(Mth.sin(step)) * 0.6F * walk + Mth.sin(t * 0.08F) * 0.15F
			- rear * 3.0F;
		this.body.xRot = -rear * 0.38F + sit * -0.12F + swim * 0.04F;
		this.body.zRot = Mth.sin(step) * 0.035F * walk;

		// the neck and head: low and forward, bobbing with the plod
		// (the neck is held high: lowering it to the ground for the ground eating or the chin slam takes the base down most)
		float bob = Mth.sin(step * 2.0F) * 0.04F * walk;
		float[] share4 = {0.4F, 0.25F, 0.2F, 0.15F};
		float neckDown = nose * 1.5F + chinDown * 1.0F - chinUp * 0.5F - roar * 0.35F + rear * 0.15F + crash * 1.1F + sit * 0.2F;
		for (int i = 0; i < 4; i++) {
			this.neck[i].xRot = this.neckRest[i] + neckDown * share4[i] + bob + Mth.sin(t * 0.05F - i * 0.5F) * 0.015F;
			this.neck[i].yRot = headYaw * 0.22F;
		}
		this.head.xRot = this.headRest + headPitch * 0.4F - neckDown * 0.55F + nose * 0.5F + chinDown * 0.25F - chinUp * 0.35F - roar * 0.25F;
		this.head.yRot = headYaw * 0.12F;
		this.jaw.xRot = 0.03F + roar * 0.75F + chinUp * 0.15F + nose * Math.max(0.0F, Mth.sin(t * 0.9F)) * 0.35F + s.breath * 0.4F;

		// the tail: swaying behind, lifted a little as it walks, lashing in the slam
		for (int i = 0; i < 5; i++) {
			this.tail[i].xRot = this.tailRest[i] - walk * 0.03F + crash * (i == 0 ? 0.25F : 0.0F) + rear * 0.12F;
			this.tail[i].yRot = Mth.sin(t * 0.06F - i * 0.6F) * (0.05F + walk * 0.08F) + Mth.sin(step - i * 0.7F) * 0.06F * walk
				+ Mth.sin(slam * Mth.PI * 2.0F) * 0.15F * rear;
		}

		// the plates: leaning out, flared by the roar
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			float lean = PLATE_LEAN + roar * ROAR_FLARE;
			this.platesL[i].zRot = lean;
			this.platesR[i].zRot = -lean;
		}

		// legs: pillars that swing from the shoulder, the diagonal pairs together; folded crashed and sitting
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float phase = front == left ? 0.0F : Mth.PI;
			float swing = Mth.cos(step + phase) * 0.45F * walk;
			float knee = Math.max(0.0F, -Mth.sin(step + phase)) * 0.35F * walk;
			float up = swing, low = knee;
			if (front) {
				up = Mth.lerp(rear, up, -0.7F);
				low = Mth.lerp(rear, low, 0.9F);
			} else {
				up = Mth.lerp(sit, up, -0.9F);
				low = Mth.lerp(sit, low, 1.3F);
			}
			up = Mth.lerp(crash, up, front ? -0.9F : 0.9F);
			low = Mth.lerp(crash, low, front ? 1.2F : -0.4F);
			up += swim * Mth.sin(t * 0.25F + phase) * 0.4F;
			this.upper[i].xRot = up;
			this.upper[i].zRot = (left ? -1.0F : 1.0F) * crash * 0.6F;
			this.lower[i].xRot = low;
			// the foot stays flat on the ground
			this.foot[i].xRot = -(up + low) * (1.0F - swim);
		}

		// its look: horns, a broken plate; its tack
		DragonLook look = s.look;
		boolean shortHorns = look.horns() == DragonLook.SHORT_HORNS;
		this.hornL.visible = this.hornR.visible = !shortHorns;
		this.shortL.visible = this.shortR.visible = shortHorns;
		this.noseHorn.visible = look.horns() == DragonLook.TWIN_HORNS;
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			// the saddle and the pillion pad sit where the middle plates would
			boolean tack = s.saddled && (i == 1 || i == 2 || i == 3);
			this.platesL[i].visible = look.spike(i) && !tack;
			this.platesR[i].visible = !tack;
		}
		this.saddle.visible = this.strap.visible = this.pad.visible = s.saddled;
	}
}
