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
 * {@code docs/SaintsDragons-Plan.md}): a low, broad, plated dragon on four pillar legs. A deep body hung low between
 * them, two staggered rows of tall horn plates down its back (the tallest over its hips), a short thick neck, a heavy
 * blunt head with a bony brow and a broad shovel of a jaw, short horns swept back (long, short, or long with a nose horn,
 * by its {@link DragonLook}), and a tapering tail ending in four spikes. Its riders' saddle, girth and pillion pad show
 * when saddled (the two middle plate pairs give way to them). Texture 256x256 ({@code tools/gen_stegonaut.py} prints the
 * body plan below from its own table, so the UVs match the paint).
 * <p>
 * Posed procedurally from its render state: it plods with its diagonal legs together, head bobbing and tail swaying;
 * its moves ({@link StegonautDragon#action}): the chin slam drives the head down and snaps it up, the ground slam rears
 * the forequarters and stamps them down, the ground eating noses into the earth and chews, and the roar throws the head
 * up with the jaw wide and the plates flared. Crashed (worn down) it sinks to its belly; sitting, it settles on its hind legs.
 */
final class StegonautDragonModel extends EntityModel<WindDragonRenderer.State> {
	/** How low it sinks crashed and sitting (model pixels). */
	static final float CRASH_DROP = 4.0F, SIT_DROP = 3.0F;
	/** The plates' lean (radians, outward) and how far the roar flares them. */
	static final float PLATE_LEAN = 0.16F, ROAR_FLARE = 0.22F;

	private final ModelPart body, neck0, neck1, head, jaw, snout;
	private final ModelPart hornL, hornR, shortL, shortR, noseHorn;
	private final ModelPart[] tail = new ModelPart[4];
	private final ModelPart[] platesL = new ModelPart[DragonLook.SPIKES], platesR = new ModelPart[DragonLook.SPIKES];
	private final ModelPart saddle, strap, pad;
	private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], foot = new ModelPart[4];
	private final float bodyY;

	StegonautDragonModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.bodyY = this.body.y;
		this.neck0 = this.body.getChild("neck0");
		this.neck1 = this.neck0.getChild("neck1");
		this.head = this.neck1.getChild("head");
		this.jaw = this.head.getChild("jaw");
		this.snout = this.head.getChild("snout");
		this.hornL = this.head.getChild("horn_l");
		this.hornR = this.head.getChild("horn_r");
		this.shortL = this.head.getChild("short_horn_l");
		this.shortR = this.head.getChild("short_horn_r");
		this.noseHorn = this.snout.getChild("nose_horn");
		ModelPart parent = this.body;
		for (int i = 0; i < 4; i++) {
			this.tail[i] = parent.getChild("tail" + i);
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
		PartDefinition body = root.addOrReplaceChild("body", box(0, 0, -12.0F, -7.0F, -18.0F, 24, 14, 36), PartPose.offset(0.0F, 11.0F, 0.0F));
		body.addOrReplaceChild("belly", box(120, 0, -10.0F, 0.0F, -13.0F, 20, 3, 26), PartPose.offset(0.0F, 7.0F, 0.0F));
		PartDefinition neck0 = body.addOrReplaceChild("neck0", box(56, 70, -7.0F, -5.0F, -7.0F, 14, 10, 7), PartPose.offset(0.0F, -1.0F, -17.0F));
		PartDefinition neck1 = neck0.addOrReplaceChild("neck1", box(32, 88, -6.0F, -4.5F, -6.0F, 12, 9, 6), PartPose.offset(0.0F, 0.5F, -6.0F));
		PartDefinition head = neck1.addOrReplaceChild("head", box(44, 50, -7.0F, -5.0F, -10.0F, 14, 9, 10), PartPose.offset(0.0F, 0.0F, -5.0F));
		PartDefinition snout = head.addOrReplaceChild("snout", box(130, 88, -5.0F, -3.0F, -7.0F, 10, 6, 7), PartPose.offset(0.0F, 0.0F, -10.0F));
		head.addOrReplaceChild("brow", box(152, 104, -7.5F, -1.5F, -2.0F, 15, 3, 4), PartPose.offset(0.0F, -4.5F, -7.0F));
		head.addOrReplaceChild("jaw", box(92, 50, -5.5F, 0.0F, -16.0F, 11, 3, 16), PartPose.offset(0.0F, 4.0F, 0.0F));
		head.addOrReplaceChild("eye_l", box(190, 104, -0.5F, -1.5F, -1.5F, 1, 3, 3), PartPose.offset(7.0F, -2.0F, -6.5F));
		head.addOrReplaceChild("eye_r", box(190, 104, -0.5F, -1.5F, -1.5F, 1, 3, 3), PartPose.offset(-7.0F, -2.0F, -6.5F));
		head.addOrReplaceChild("horn_l", box(70, 104, -1.0F, -1.0F, 0.0F, 2, 2, 8), PartPose.offsetAndRotation(5.5F, -4.5F, -4.0F, 0.45F, 0.35F, 0.0F));
		head.addOrReplaceChild("horn_r", box(70, 104, -1.0F, -1.0F, 0.0F, 2, 2, 8), PartPose.offsetAndRotation(-5.5F, -4.5F, -4.0F, 0.45F, -0.35F, 0.0F));
		head.addOrReplaceChild("short_horn_l", box(198, 104, -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offsetAndRotation(5.5F, -4.5F, -4.0F, 0.5F, 0.35F, 0.0F));
		head.addOrReplaceChild("short_horn_r", box(198, 104, -1.0F, -1.0F, 0.0F, 2, 2, 4), PartPose.offsetAndRotation(-5.5F, -4.5F, -4.0F, 0.5F, -0.35F, 0.0F));
		snout.addOrReplaceChild("nose_horn", box(210, 104, -1.0F, -4.0F, -1.0F, 2, 4, 2), PartPose.offsetAndRotation(0.0F, -3.0F, -4.0F, -0.35F, 0.0F, 0.0F));
		PartDefinition tail0 = body.addOrReplaceChild("tail0", box(0, 50, -6.0F, -5.0F, 0.0F, 12, 10, 10), PartPose.offset(0.0F, -1.0F, 17.0F));
		PartDefinition tail1 = tail0.addOrReplaceChild("tail1", box(186, 50, -5.0F, -4.0F, 0.0F, 10, 8, 10), PartPose.offset(0.0F, 0.5F, 9.0F));
		PartDefinition tail2 = tail1.addOrReplaceChild("tail2", box(134, 70, -4.0F, -3.0F, 0.0F, 8, 6, 10), PartPose.offset(0.0F, 0.5F, 9.0F));
		PartDefinition tail3 = tail2.addOrReplaceChild("tail3", box(68, 88, -3.0F, -2.5F, 0.0F, 6, 5, 9), PartPose.offset(0.0F, 0.5F, 9.0F));
		tail3.addOrReplaceChild("thag0", box(90, 104, -1.0F, -1.0F, 0.0F, 2, 2, 8), PartPose.offsetAndRotation(2.0F, -1.5F, 5.0F, -0.7F, 1.1F, 0.0F));
		tail3.addOrReplaceChild("thag1", box(90, 104, -1.0F, -1.0F, 0.0F, 2, 2, 8), PartPose.offsetAndRotation(-2.0F, -1.5F, 5.0F, -0.7F, -1.1F, 0.0F));
		tail3.addOrReplaceChild("thag2", box(90, 104, -1.0F, -1.0F, 0.0F, 2, 2, 7), PartPose.offsetAndRotation(2.0F, -1.5F, 1.0F, -0.9F, 0.9F, 0.0F));
		tail3.addOrReplaceChild("thag3", box(90, 104, -1.0F, -1.0F, 0.0F, 2, 2, 7), PartPose.offsetAndRotation(-2.0F, -1.5F, 1.0F, -0.9F, -0.9F, 0.0F));
		tail0.addOrReplaceChild("tail_plate0", box(164, 88, -1.0F, -6.0F, -3.0F, 2, 6, 6), PartPose.offset(0.0F, -5.0F, 5.0F));
		tail1.addOrReplaceChild("tail_plate1", box(138, 104, -1.0F, -4.0F, -2.5F, 2, 4, 5), PartPose.offset(0.0F, -4.0F, 5.0F));
		PartDefinition saddle = body.addOrReplaceChild("saddle", box(170, 70, -8.0F, -2.0F, -7.0F, 16, 2, 14), PartPose.offset(0.0F, -7.0F, -1.0F));
		saddle.addOrReplaceChild("pommel", box(218, 104, -2.0F, -3.0F, 0.0F, 4, 3, 2), PartPose.offset(0.0F, -2.0F, -7.0F));
		saddle.addOrReplaceChild("cantle", box(0, 116, -6.0F, -3.0F, 0.0F, 12, 3, 2), PartPose.offset(0.0F, -2.0F, 5.0F));
		body.addOrReplaceChild("strap", box(0, 70, -12.5F, -7.5F, -1.5F, 25, 15, 3), PartPose.offset(0.0F, 0.0F, -1.0F));
		body.addOrReplaceChild("pad", box(180, 88, -7.0F, -2.0F, -5.0F, 14, 2, 10), PartPose.offset(0.0F, -7.0F, 9.0F));
		body.addOrReplaceChild("plate0_l", box(98, 88, -1.0F, -8.0F, -3.0F, 2, 8, 6), PartPose.offsetAndRotation(2.5F, -6.0F, -12.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate0_r", mirrored(98, 88, -1.0F, -8.0F, -3.0F, 2, 8, 6), PartPose.offsetAndRotation(-2.5F, -6.0F, -9.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate1_l", box(146, 50, -1.0F, -11.0F, -4.0F, 2, 11, 8), PartPose.offsetAndRotation(2.5F, -6.0F, -5.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate1_r", mirrored(146, 50, -1.0F, -11.0F, -4.0F, 2, 11, 8), PartPose.offsetAndRotation(-2.5F, -6.0F, -2.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate2_l", box(212, 0, -1.0F, -13.0F, -4.5F, 2, 13, 9), PartPose.offsetAndRotation(2.5F, -6.0F, 2.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate2_r", mirrored(212, 0, -1.0F, -13.0F, -4.5F, 2, 13, 9), PartPose.offsetAndRotation(-2.5F, -6.0F, 4.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate3_l", box(166, 50, -1.0F, -11.0F, -4.0F, 2, 11, 8), PartPose.offsetAndRotation(2.5F, -6.0F, 9.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate3_r", mirrored(166, 50, -1.0F, -11.0F, -4.0F, 2, 11, 8), PartPose.offsetAndRotation(-2.5F, -6.0F, 11.5F, 0.0F, 0.0F, -0.16F));
		body.addOrReplaceChild("plate4_l", box(114, 88, -1.0F, -8.0F, -3.0F, 2, 8, 6), PartPose.offsetAndRotation(2.5F, -6.0F, 15.0F, 0.0F, 0.0F, 0.16F));
		body.addOrReplaceChild("plate4_r", mirrored(114, 88, -1.0F, -8.0F, -3.0F, 2, 8, 6), PartPose.offsetAndRotation(-2.5F, -6.0F, 17.5F, 0.0F, 0.0F, -0.16F));
		PartDefinition leftfrontleg = body.addOrReplaceChild("left_front_leg", box(0, 88, -4.0F, -2.0F, -4.0F, 8, 8, 8), PartPose.offset(9.0F, 3.0F, -12.0F));
		PartDefinition leftfrontshin = leftfrontleg.addOrReplaceChild("left_front_shin", box(110, 104, -3.5F, 0.0F, -3.5F, 7, 3, 7), PartPose.offset(0.0F, 6.0F, 0.0F));
		leftfrontshin.addOrReplaceChild("left_front_foot", box(0, 104, -4.5F, 0.0F, -6.0F, 9, 2, 10), PartPose.offset(0.0F, 3.0F, 0.0F));
		PartDefinition rightfrontleg = body.addOrReplaceChild("right_front_leg", mirrored(0, 88, -4.0F, -2.0F, -4.0F, 8, 8, 8), PartPose.offset(-9.0F, 3.0F, -12.0F));
		PartDefinition rightfrontshin = rightfrontleg.addOrReplaceChild("right_front_shin", mirrored(110, 104, -3.5F, 0.0F, -3.5F, 7, 3, 7), PartPose.offset(0.0F, 6.0F, 0.0F));
		rightfrontshin.addOrReplaceChild("right_front_foot", mirrored(0, 104, -4.5F, 0.0F, -6.0F, 9, 2, 10), PartPose.offset(0.0F, 3.0F, 0.0F));
		PartDefinition lefthindleg = body.addOrReplaceChild("left_hind_leg", box(98, 70, -4.5F, -2.0F, -4.5F, 9, 8, 9), PartPose.offset(9.0F, 3.0F, 12.0F));
		PartDefinition lefthindshin = lefthindleg.addOrReplaceChild("left_hind_shin", box(38, 104, -4.0F, 0.0F, -4.0F, 8, 3, 8), PartPose.offset(0.0F, 6.0F, 0.0F));
		lefthindshin.addOrReplaceChild("left_hind_foot", box(0, 104, -4.5F, 0.0F, -6.0F, 9, 2, 10), PartPose.offset(0.0F, 3.0F, 0.0F));
		PartDefinition righthindleg = body.addOrReplaceChild("right_hind_leg", mirrored(98, 70, -4.5F, -2.0F, -4.5F, 9, 8, 9), PartPose.offset(-9.0F, 3.0F, 12.0F));
		PartDefinition righthindshin = righthindleg.addOrReplaceChild("right_hind_shin", mirrored(38, 104, -4.0F, 0.0F, -4.0F, 8, 3, 8), PartPose.offset(0.0F, 6.0F, 0.0F));
		righthindshin.addOrReplaceChild("right_hind_foot", mirrored(0, 104, -4.5F, 0.0F, -6.0F, 9, 2, 10), PartPose.offset(0.0F, 3.0F, 0.0F));
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
		float bob = Mth.sin(step * 2.0F) * 0.05F * walk;
		this.neck0.xRot = 0.08F + bob + nose * 0.45F + chinDown * 0.3F - chinUp * 0.35F - roar * 0.45F + rear * 0.2F + crash * 0.25F;
		this.neck0.yRot = headYaw * 0.4F;
		this.neck1.xRot = 0.04F + nose * 0.25F + chinDown * 0.15F - chinUp * 0.2F - roar * 0.2F;
		this.neck1.yRot = headYaw * 0.3F;
		this.head.xRot = headPitch * 0.4F + nose * 0.35F + chinDown * 0.25F - chinUp * 0.45F - roar * 0.15F + crash * 0.1F;
		this.head.yRot = headYaw * 0.3F;
		this.jaw.xRot = 0.03F + roar * 0.75F + chinUp * 0.15F + nose * Math.max(0.0F, Mth.sin(t * 0.9F)) * 0.35F + s.breath * 0.4F;

		// the tail: swaying behind, lifted a little as it walks, lashing in the slam
		for (int i = 0; i < 4; i++) {
			this.tail[i].xRot = -0.06F + i * 0.02F - walk * 0.03F + crash * 0.05F + rear * 0.12F;
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
			this.upper[i].zRot = (left ? -1.0F : 1.0F) * crash * 0.25F;
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
