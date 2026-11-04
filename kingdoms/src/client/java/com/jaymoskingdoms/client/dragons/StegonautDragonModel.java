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
 * (0.7.522) Stegonaut, our own model (blocks, as the Kingdoms beasts are; nothing of Saint's Dragons' art is used, see
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
 * (0.7.531) Every move after the Bastionodon set's (wind-up, strike, settle): the ground eating as scoop, chew and spit, a
 * bite of its own, grazing chomps, three idle grumbles, a flinch when struck, a doze when it has sat a while, a gallop at
 * a run, a paddling swim, and stiff legs as it dies.
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

	/** Smoothstep, clamped: eases a 0..1 share in and out. */
	static float ease(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}

	/** A hump: 0 at {@code from}, 1 halfway, 0 again at {@code to} (eased both ways); 0 outside. */
	static float bell(float x, float from, float to) {
		return x <= from || x >= to ? 0.0F : Mth.sin(ease((x - from) / (to - from)) * Mth.PI);
	}

	/** Eased up over {@code from..peak}, held, then eased down over {@code fall..to}; 0 outside. */
	static float hold(float x, float from, float peak, float fall, float to) {
		if (x <= from || x >= to) {
			return 0.0F;
		}
		return x < peak ? ease((x - from) / (peak - from)) : x < fall ? 1.0F : 1.0F - ease((x - fall) / (to - fall));
	}

	@Override
	public void setupAnim(WindDragonRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		float dead = ease(s.deathTime / 20.0F);
		float crash = ease(s.crash), swim = ease(s.swim) * (1.0F - crash);
		float sit = ease(s.sit) * (1.0F - crash) * (1.0F - swim);
		float walk = Math.min(s.walkAnimationSpeed, 1.0F) * (1.0F - crash) * (1.0F - sit) * (1.0F - dead);
		// (0.7.531) at a run it gallops: the pairs fall out of step, the strides lengthen and it rocks fore and aft
		float run = ease((s.walkAnimationSpeed - 0.55F) / 0.35F) * walk;
		float step = s.walkAnimationPos * 0.55F;
		float headYaw = Mth.clamp(s.yRot, -50.0F, 50.0F) * Mth.DEG_TO_RAD;
		float headPitch = Mth.clamp(s.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD;
		float share = StegonautDragon.actionShare(s.action, s.actionAge);

		// the moves, each a 0..1 envelope over its length
		// (0.7.522, Andy: "all the joints should move and blend smoothly") every envelope eases in and out (smoothstep) and
		// ends where it began, so a move flows out of the walk and back into it with no snap
		// (0.7.531, Andy: "map these animations to our stegonaut") the moves follow the Bastionodon set's keys: a wind-up, a
		// strike and a settle for each, the ground eating in its three parts (scoop, chew, spit), and the bite, the
		// grazing, the grumbles, the flinch, the gallop and the swim stroke added; all drawn here, none of its files used
		float chin = s.action == StegonautDragon.CHIN_SLAM ? share : 0.0F;
		float slam = s.action == StegonautDragon.GROUND_SLAM ? share : 0.0F;
		float chew = s.action == StegonautDragon.CHEW ? share : 0.0F;
		float roarShare = s.action == StegonautDragon.ROAR ? share : 0.0F;
		float bite = s.action == StegonautDragon.BITE_MOVE ? share : 0.0F;
		float graze = s.action == StegonautDragon.GRAZE ? share : 0.0F;
		// the slam: rear up through the wind-up (to 0.6), then come down hard (but eased, over a quarter of it), the body
		// pitching forward onto its forefeet as they land and rocking back
		float rear = slam <= 0.0F ? 0.0F : slam < 0.6F ? ease(slam / 0.6F) : 1.0F - ease((slam - 0.6F) / 0.25F);
		float impact = bell(slam, 0.68F, 1.0F);
		// the chin slam: crouched, head drawn down and back (to 0.36), then flung up through whoever's there
		float chinDown = hold(chin, 0.0F, 0.36F, 0.36F, 0.62F);
		float chinUp = bell(chin, 0.36F, 1.0F);
		// the ground eating: the scoop (head down to the earth, jaw wide, to 0.3), the chew (head raised, cheeks full,
		// jaw working), then the spit at 0.75 (head thrown back and flung forward, jaw wide)
		float scoop = hold(chew, 0.0F, 0.3F, 0.3F, 0.5F);
		float cheeks = hold(chew, 0.3F, 0.42F, 0.68F, 0.78F);
		float throwBack = hold(chew, 0.64F, 0.74F, 0.74F, 0.84F);
		float fling = bell(chew, 0.74F, 1.0F);
		// the roar (a show of strength): the head lifted and swung to one side then the other, jaw held wide, tail lashing
		float roar = hold(roarShare, 0.0F, 0.2F, 0.66F, 1.0F);
		float roarSweep = roar * Mth.sin(roarShare * Mth.PI * 2.0F);
		// the bite: drawn back, jaws wide (to 0.34), then snapped in and shut
		float biteBack = hold(bite, 0.0F, 0.34F, 0.34F, 0.6F);
		float biteIn = bell(bite, 0.34F, 1.0F);
		// grazing: nose to the ground, two chomps, back up
		float graze0 = hold(graze, 0.0F, 0.22F, 0.8F, 1.0F);
		float chomp = graze0 * (0.5F - 0.5F * Mth.cos(graze * Mth.PI * 4.0F));
		// the flinch: a blow rocks it back and over, jaw opening, then it rights itself
		float flinch = s.hurt <= 0.0F ? 0.0F : hold(1.0F - s.hurt, 0.0F, 0.2F, 0.2F, 1.0F);
		// (idle) now and then it grumbles: one of three, a head toss, a head tilt, or the neck lifted, each with a low growl's
		// open jaw; only stood still and doing nothing else
		float idle = (1.0F - Math.min(s.walkAnimationSpeed * 4.0F, 1.0F)) * (1.0F - sit) * (1.0F - crash) * (1.0F - swim) * (1.0F - dead)
			* (s.action == StegonautDragon.NONE ? 1.0F : 0.0F);
		float cycle = (t + 100.0F) / 260.0F;
		float grumble = idle * bell((cycle - Mth.floor(cycle)) * 260.0F, 0.0F, 44.0F);
		int kind = Math.floorMod(Mth.floor(cycle), 3);
		float toss = kind == 0 ? grumble : 0.0F, tilt = kind == 1 ? grumble : 0.0F, raise = kind == 2 ? grumble : 0.0F;
		float grumbleTurn = toss * Mth.sin(((cycle - Mth.floor(cycle)) * 260.0F / 44.0F) * Mth.PI * 2.0F);
		// sitting long it dozes: head down and turned aside, slow breaths
		float doze = sit * sit;

		// the body: a heavy plod (a bounding rock at a run), sunk crashed, settled back sitting, rearing for the slam
		this.body.y = this.bodyY + crash * CRASH_DROP + sit * SIT_DROP + Math.abs(Mth.sin(step)) * (0.6F + run * 1.0F) * walk
			+ Mth.sin(t * (0.08F - doze * 0.04F)) * (0.15F + doze * 0.15F)
			- rear * 3.0F + impact * 1.6F + chinDown * 1.0F - chinUp * 1.2F;
		this.body.xRot = -rear * 0.38F + impact * 0.2F + sit * -0.12F + swim * 0.04F + Mth.cos(step * 2.0F) * 0.05F * run - flinch * 0.12F;
		this.body.zRot = Mth.sin(step) * 0.035F * walk * (1.0F - run) + flinch * 0.16F;

		// the neck and head: low and forward, bobbing with the plod
		// (the neck is held high: lowering it to the ground for the ground eating or the chin slam takes the base down most)
		float[] share4 = {0.4F, 0.25F, 0.2F, 0.15F};
		float neckDown = scoop * 1.5F - cheeks * 0.15F - throwBack * 0.45F + fling * 0.55F
			+ chinDown * 1.0F - chinUp * 0.5F - roar * 0.35F + rear * 0.15F + crash * 1.1F + sit * 0.2F + doze * 0.25F
			- biteBack * 0.3F + biteIn * 0.45F + graze0 * 1.45F - raise * 0.3F + dead * 0.6F;
		for (int i = 0; i < 4; i++) {
			// (each segment a beat behind the one before it, so the bob and the sway roll up the neck)
			this.neck[i].xRot = this.neckRest[i] + neckDown * share4[i] + Mth.sin(step * 2.0F - i * 0.6F) * 0.04F * walk
				+ Mth.sin(t * 0.05F - i * 0.5F) * 0.015F;
			this.neck[i].yRot = headYaw * 0.22F * (1.0F - doze) + Mth.sin(step - i * 0.6F) * 0.03F * walk + roarSweep * 0.05F;
		}
		this.head.xRot = this.headRest + headPitch * 0.4F * (1.0F - doze) - neckDown * 0.55F + scoop * 0.45F - throwBack * 0.3F + fling * 0.3F
			+ chinDown * 0.25F - chinUp * 0.35F - roar * 0.3F - biteBack * 0.3F + biteIn * 0.25F + graze0 * 0.5F
			- toss * 0.17F - tilt * 0.24F + doze * 0.28F;
		this.head.yRot = headYaw * 0.12F * (1.0F - doze) + roarSweep * 0.25F + grumbleTurn * 0.14F + doze * 0.14F;
		this.head.zRot = tilt * Mth.sin(((cycle - Mth.floor(cycle)) * 260.0F / 44.0F) * Mth.PI * 2.0F) * 0.17F;
		this.jaw.xRot = 0.03F + roar * 0.6F + chinUp * 0.15F + s.breath * 0.4F
			+ scoop * 0.6F + cheeks * (0.21F + Mth.sin(t * 0.9F) * 0.05F) + fling * 0.7F
			+ biteBack * 0.66F - biteIn * 0.05F + chomp * 0.5F + flinch * 0.5F + grumble * 0.44F + dead * 0.5F;

		// the tail: swaying behind, lifted a little as it walks, lashing in the slam and the roar, sweeping as it swims
		for (int i = 0; i < 5; i++) {
			this.tail[i].xRot = this.tailRest[i] - walk * 0.03F + crash * (i == 0 ? 0.25F : 0.0F) + rear * 0.12F
				- (i == 0 ? impact * 0.3F : 0.0F) + Mth.sin(step * 2.0F - i * 0.5F) * 0.05F * run;
			this.tail[i].yRot = Mth.sin(t * 0.06F - i * 0.6F) * (0.05F + walk * 0.08F) + Mth.sin(step - i * 0.7F) * 0.06F * walk * (1.0F - run)
				+ Mth.sin(slam * Mth.PI * 2.0F) * 0.15F * rear + Mth.sin(t * 0.5F - i * 0.6F) * 0.12F * roar
				+ Mth.cos(t * 0.2F - i * 0.6F) * 0.12F * swim;
		}

		// the plates: leaning out, flared by the roar
		for (int i = 0; i < DragonLook.SPIKES; i++) {
			float lean = PLATE_LEAN + roar * ROAR_FLARE;
			this.platesL[i].zRot = lean;
			this.platesR[i].zRot = -lean;
		}

		// legs: pillars that swing from the shoulder, the diagonal pairs together (at a run the right pair an eighth of a
		// stride behind the left, as a gallop); folded crashed and sitting, splayed for the slam, paddling when it swims,
		// stiff and out when it dies
		for (int i = 0; i < 4; i++) {
			boolean front = i < 2, left = i % 2 == 0;
			float trot = front == left ? 0.0F : Mth.PI;
			float gallop = (front ? 0.0F : Mth.PI) + (left ? 0.0F : Mth.PI * 0.25F);
			float phase = Mth.lerp(run, trot, gallop);
			float swing = Mth.cos(step + phase) * (0.45F + run * 0.15F) * walk;
			// (the knee folds as the leg swings forward and straightens as it plants: a smooth curve, no kink at the turn)
			float lift = (1.0F - Mth.sin(step + phase)) * 0.5F;
			float knee = lift * lift * (0.45F + run * 0.2F) * walk;
			float up = swing, low = knee;
			if (front) {
				up = Mth.lerp(rear, up, -0.7F) + impact * 0.28F;
				low = Mth.lerp(rear, low, 0.9F);
			} else {
				up = Mth.lerp(sit, up, -0.9F);
				low = Mth.lerp(sit, low, 1.3F);
			}
			up = Mth.lerp(crash, up, front ? -0.9F : 0.9F);
			low = Mth.lerp(crash, low, front ? 1.2F : -0.4F);
			up += swim * Mth.sin(t * 0.25F + phase) * (front ? 0.5F : 0.3F);
			low = Mth.lerp(dead, low, 0.0F);
			this.upper[i].xRot = up;
			this.upper[i].zRot = (left ? -1.0F : 1.0F) * (crash * 0.6F + (front ? rear * 0.24F + swim * 0.24F : 0.0F) + dead * 0.45F);
			this.lower[i].xRot = low;
			// the foot stays flat on the ground
			this.foot[i].xRot = -(up + low) * (1.0F - swim) * (1.0F - dead);
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
