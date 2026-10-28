package com.jaymoskingdoms.client.equestrian;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jaymoskingdoms.JaymosKingdoms;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.305) Our horses, skeleton horses and zombie horses: one rig, a build per breed (the destrier's crested neck and
 * deep chest, the courser's fine legs, the draft's feathered hooves and Roman nose, the steppe pony's standing mane, the
 * desert horse's dished face, the long manes falling over the neck, and the bat horse with its wings), read from
 * {@code assets/jaymoskingdoms/horse/builds.json} ({@code tools/gen_horses.py}). Each build is a group under the root
 * and only the horse's own is shown. Its bones keep the names and pivots of the Mo' Creatures horse we drew before, and
 * move as that one did: the jointed legs (each lower leg swings from the end of the one above, the cannon bending as the
 * hoof comes forward), the head, neck and mane together, the mouth opening to eat, the tail lifting with speed and
 * swishing, vanilla's rear and graze as blends, the saddle, stirrups, headstall and reins of a vanilla saddle; so the
 * tack ({@link TackLayer}) hangs on any build's bones. The pieces hung from the bones (forelock, mane, feathering...)
 * follow them. Texture 128x192 (coats drawn at twice that). (0.7.309) Its barding is a model of its own on the same
 * bones ({@link #createArmorLayer}): chanfron, crinet and crest, peytral, flanchards and crupper, shaped to each build
 * (texture 128x64); while it's worn the mane and forelock lie hidden under it.
 */
class BreedHorseModel extends EntityModel<BreedHorseRenderer.State> {
	private static final float DEG = Mth.DEG_TO_RAD;
	/** The head's pitch at rest, reared and grazing. */
	private static final float HEAD_TILT = 30.0F * DEG, HEAD_TILT_REARED = 15.0F * DEG, HEAD_TILT_GRAZING = 2.18166F;
	/**
	 * (0.7.309, a taller horse after SWEM's look) the legs are this much longer (the body, saddle, tail and wings sit that
	 * much higher) and the neck this much longer (the head set out along it); as {@code LIFT} and {@code NECK} in
	 * tools/horse_rig.py. Everything is posed as a change from each build's own rest pose (builds.json), so a build
	 * brought in from Blockbench with other proportions moves the same way.
	 */
	static final float LIFT = 3.0F, NECK = 3.0F;
	/** Where the head's pivot is at rest, looking level (the head is posed as a change from there). */
	private static final float HEAD_REST_Y = 4.0F - LIFT - NECK * Mth.cos(HEAD_TILT), HEAD_REST_Z = -10.0F - NECK * Mth.sin(HEAD_TILT);
	/** The mouth opens past this much of vanilla's feeding animation. */
	private static final float MOUTH_OPEN = 0.3F;
	/** How far a mane that falls over the neck swings out at a gallop (radians), and how fast it ripples. */
	private static final float MANE_SWAY = 0.12F, MANE_RIPPLE = 0.35F;
	/** (0.7.305) Ridden by you in first person, the head dips this far (degrees, and px down) to clear your view. */
	private static final float HEAD_DIP = 25.0F * DEG, HEAD_DIP_DROP = 3.0F;
	/** The build the tack is hung on (every build has the same bones in the same places). */
	static final String TACK_BUILD = "palfrey";

	private record Box(int u, int v, float[] pos, float[] size, float grow) {
	}

	private record Piece(String name, String parent, float[] pose, List<Box> boxes) {
	}

	/** The builds, by name, as read from builds.json; and its texture size. */
	private static final Map<String, List<Piece>> BUILDS = new LinkedHashMap<>();
	private static int textureWidth = 128, textureHeight = 192;
	/** (0.7.309) The barding's pieces on each build's bones, and their texture's size. */
	private static final Map<String, List<Piece>> ARMOR = new LinkedHashMap<>();
	private static int armorWidth = 128, armorHeight = 128;
	/** (0.7.309) How far out each build's stirrups hang under barding: clear of its side plates. */
	private static final Map<String, Float> STIRRUP_OUT = new HashMap<>();
	private static final float STIRRUP_CLEAR = 0.6F;

	private final Map<String, Rig> rigs = new HashMap<>();
	private final @Nullable Rig fallback;

	BreedHorseModel(ModelPart root) {
		super(root);
		Rig first = null;
		for (String build : BUILDS.keySet()) {
			if (root.hasChild(build)) {
				Rig rig = new Rig(root.getChild(build));
				this.rigs.put(build, rig);
				if (first == null) {
					first = rig;
				}
			}
		}
		this.fallback = first;
	}

	/** Reads the builds (at start-up, before the horse renderers are made). */
	static void register() {
		String path = "/assets/" + JaymosKingdoms.MOD_ID + "/horse/builds.json";
		try (InputStream in = BreedHorseModel.class.getResourceAsStream(path)) {
			if (in == null) {
				JaymosKingdoms.LOGGER.error("Equestrian: no horse builds ({})", path);
				return;
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			JsonArray size = json.getAsJsonArray("texture_size");
			textureWidth = size.get(0).getAsInt();
			textureHeight = size.get(1).getAsInt();
			if (json.has("armor_texture_size")) {
				JsonArray armor = json.getAsJsonArray("armor_texture_size");
				armorWidth = armor.get(0).getAsInt();
				armorHeight = armor.get(1).getAsInt();
			}
			BUILDS.clear();
			ARMOR.clear();
			STIRRUP_OUT.clear();
			for (Map.Entry<String, JsonElement> build : json.getAsJsonObject("builds").entrySet()) {
				JsonObject b = build.getValue().getAsJsonObject();
				BUILDS.put(build.getKey(), pieces(b.getAsJsonArray("parts")));
				if (b.has("armor")) {
					List<Piece> armor = pieces(b.getAsJsonArray("armor"));
					ARMOR.put(build.getKey(), armor);
					for (Piece p : armor) {
						if (p.name().equals("armor_flank_left") && !p.boxes().isEmpty()) {
							Box box = p.boxes().get(0);
							STIRRUP_OUT.put(build.getKey(), box.pos()[0] + box.size()[0] + STIRRUP_CLEAR);
						}
					}
				}
			}
		} catch (Exception e) {
			JaymosKingdoms.LOGGER.error("Equestrian: couldn't read {}", path, e);
		}
	}

	private static List<Piece> pieces(JsonArray parts) {
		List<Piece> pieces = new ArrayList<>();
		for (JsonElement e : parts) {
			JsonObject p = e.getAsJsonObject();
			List<Box> boxes = new ArrayList<>();
			for (JsonElement b : p.getAsJsonArray("cubes")) {
				JsonObject c = b.getAsJsonObject();
				JsonArray uv = c.getAsJsonArray("uv");
				boxes.add(new Box(uv.get(0).getAsInt(), uv.get(1).getAsInt(), floats(c.getAsJsonArray("pos")), floats(c.getAsJsonArray("size")),
					c.has("grow") ? c.get("grow").getAsFloat() : 0.0F));
			}
			pieces.add(new Piece(p.get("name").getAsString(), p.get("parent").getAsString(), floats(p.getAsJsonArray("pose")), boxes));
		}
		return pieces;
	}

	private static float[] floats(JsonArray array) {
		float[] out = new float[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsFloat();
		}
		return out;
	}

	/** The horse itself: every build, each a group under the root. */
	static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(mesh(BUILDS.keySet(), true, 0.0F), textureWidth, textureHeight);
	}

	/** (0.7.309) Its barding: each build's bones carrying the barding's own pieces (until 0.7.305 a larger copy of the horse). */
	static LayerDefinition createArmorLayer() {
		return LayerDefinition.create(mesh(BUILDS.keySet(), false, 0.0F, true), armorWidth, armorHeight);
	}

	/** One build's bones alone (no boxes), for {@link TackLayer} to hang its pieces on. */
	static MeshDefinition bones() {
		return mesh(List.of(TACK_BUILD), false, 0.0F, false);
	}

	private static MeshDefinition mesh(Iterable<String> builds, boolean boxes, float grow) {
		return mesh(builds, boxes, grow, false);
	}

	/** The builds' parts (with their boxes or bare), and with {@code armor} the barding's pieces hung on them. */
	private static MeshDefinition mesh(Iterable<String> builds, boolean boxes, float grow, boolean armor) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (String build : builds) {
			List<Piece> pieces = BUILDS.get(build);
			if (pieces == null) {
				continue;
			}
			PartDefinition group = root.addOrReplaceChild(build, CubeListBuilder.create(), PartPose.ZERO);
			Map<String, PartDefinition> parts = new HashMap<>();
			List<Piece> all = new ArrayList<>(pieces);
			if (armor) {
				all.addAll(ARMOR.getOrDefault(build, List.of()));
			}
			for (Piece p : all) {
				boolean own = boxes || armor && p.name().startsWith("armor_");
				PartDefinition parent = p.parent().isEmpty() ? group : parts.getOrDefault(p.parent(), group);
				parts.put(p.name(), parent.addOrReplaceChild(p.name(), own ? cubes(p, grow) : CubeListBuilder.create(), pose(p)));
			}
		}
		return mesh;
	}

	private static CubeListBuilder cubes(Piece p, float grow) {
		CubeListBuilder cubes = CubeListBuilder.create();
		for (Box b : p.boxes()) {
			cubes.texOffs(b.u(), b.v()).addBox(b.pos()[0], b.pos()[1], b.pos()[2], b.size()[0], b.size()[1], b.size()[2],
				new CubeDeformation(b.grow() + grow));
		}
		return cubes;
	}

	private static PartPose pose(Piece p) {
		float[] o = p.pose();
		return PartPose.offsetAndRotation(o[0], o[1], o[2], o[3] * DEG, o[4] * DEG, o[5] * DEG);
	}

	@Override
	public void setupAnim(BreedHorseRenderer.State state) {
		super.setupAnim(state);
		Rig shown = this.rigs.getOrDefault(state.build, this.fallback);
		for (Rig rig : this.rigs.values()) {
			rig.group.visible = rig == shown;
		}
		if (shown != null) {
			shown.pose(state);
		}
	}

	/** A build's parts, posed each frame. */
	private static final class Rig {
		final ModelPart group, body, tailA, tailB, tailC, head, upperMouth, lowerMouth, upperMouthOpen, lowerMouthOpen;
		final ModelPart leftEar, rightEar, neck, mane;
		final ModelPart leg1A, leg1B, leg1C, leg2A, leg2B, leg2C, leg3A, leg3B, leg3C, leg4A, leg4B, leg4C;
		final ModelPart headSaddle, saddle, saddleB, saddleC, stirrupLeft, stirrupLeftIron, stirrupRight, stirrupRightIron;
		final ModelPart bitLeft, bitRight, reinLeft, reinRight;
		final ModelPart[] headParts, saddleParts;
		/** (0.7.309) The forelock's pieces (hidden under a chanfron). */
		final List<ModelPart> forelock = new ArrayList<>();
		/** The mane that falls over the neck, on the long-maned builds. */
		final @Nullable ModelPart maneFlow;
		/** The bat horse's wings. */
		final @Nullable ModelPart innerWing, midWing, outerWing, innerWingR, midWingR, outerWingR;

		Rig(ModelPart g) {
			this.group = g;
			this.body = g.getChild("body");
			this.tailA = g.getChild("tail_a");
			this.tailB = g.getChild("tail_b");
			this.tailC = g.getChild("tail_c");
			this.leg1A = g.getChild("leg1_a");
			this.leg1B = g.getChild("leg1_b");
			this.leg1C = g.getChild("leg1_c");
			this.leg2A = g.getChild("leg2_a");
			this.leg2B = g.getChild("leg2_b");
			this.leg2C = g.getChild("leg2_c");
			this.leg3A = g.getChild("leg3_a");
			this.leg3B = g.getChild("leg3_b");
			this.leg3C = g.getChild("leg3_c");
			this.leg4A = g.getChild("leg4_a");
			this.leg4B = g.getChild("leg4_b");
			this.leg4C = g.getChild("leg4_c");
			this.head = g.getChild("head");
			this.upperMouth = g.getChild("upper_mouth");
			this.lowerMouth = g.getChild("lower_mouth");
			this.upperMouthOpen = g.getChild("upper_mouth_open");
			this.lowerMouthOpen = g.getChild("lower_mouth_open");
			this.leftEar = g.getChild("left_ear");
			this.rightEar = g.getChild("right_ear");
			this.neck = g.getChild("neck");
			this.mane = g.getChild("mane");
			this.headSaddle = g.getChild("head_saddle");
			this.saddle = g.getChild("saddle");
			this.saddleB = g.getChild("saddle_b");
			this.saddleC = g.getChild("saddle_c");
			this.stirrupLeft = g.getChild("stirrup_left");
			this.stirrupLeftIron = g.getChild("stirrup_left_iron");
			this.stirrupRight = g.getChild("stirrup_right");
			this.stirrupRightIron = g.getChild("stirrup_right_iron");
			this.bitLeft = g.getChild("bit_left");
			this.bitRight = g.getChild("bit_right");
			this.reinLeft = g.getChild("rein_left");
			this.reinRight = g.getChild("rein_right");
			this.maneFlow = this.mane.hasChild("mane_flow") ? this.mane.getChild("mane_flow") : null;
			for (String name : new String[] {"forelock", "forelock_tip"}) {
				if (this.head.hasChild(name)) {
					this.forelock.add(this.head.getChild(name));
				}
			}
			boolean wings = g.hasChild("inner_wing");
			this.innerWing = wings ? g.getChild("inner_wing") : null;
			this.midWing = wings ? g.getChild("mid_wing") : null;
			this.outerWing = wings ? g.getChild("outer_wing") : null;
			this.innerWingR = wings ? g.getChild("inner_wing_r") : null;
			this.midWingR = wings ? g.getChild("mid_wing_r") : null;
			this.outerWingR = wings ? g.getChild("outer_wing_r") : null;
			this.headParts = new ModelPart[] {this.head, this.leftEar, this.rightEar, this.neck, this.mane, this.upperMouth, this.lowerMouth,
				this.upperMouthOpen, this.lowerMouthOpen, this.headSaddle, this.bitLeft, this.bitRight};
			this.saddleParts = new ModelPart[] {this.saddle, this.saddleB, this.saddleC, this.stirrupLeft, this.stirrupLeftIron, this.stirrupRight,
				this.stirrupRightIron};
		}

		void pose(BreedHorseRenderer.State state) {
			float walked = state.walkAnimationPos;
			float speed = state.walkAnimationSpeed;
			float ticks = state.ageInTicks;
			float stand = state.standAnimation;
			float graze = state.eatAnimation * (1.0F - stand);
			float rest = 1.0F - stand - graze;
			float yaw = Mth.clamp(state.yRot, -20.0F, 20.0F) * DEG;
			float rLeg = Mth.cos(walked * 0.6662F + Mth.PI) * 0.8F * speed;
			float lLeg = Mth.cos(walked * 0.6662F) * 0.8F * speed;
			float pitch = state.xRot * DEG;
			if (speed > 0.2F) {
				pitch += Mth.cos(walked * 0.4F) * 0.15F * speed;
			}

			// the head, neck, mane and ears: up when reared, down to the grass when grazing
			// (where the neck meets the chest: the head's pivot is set out NECK along the neck from there; grazing, the
			// taller horse reaches down as far as the old one did)
			float headX = (HEAD_TILT + pitch) * rest + (HEAD_TILT_REARED + pitch) * stand + HEAD_TILT_GRAZING * graze;
			headX += HEAD_DIP * state.headDip * rest;
			float headY = (4.0F - LIFT) * rest + (-6.0F - LIFT) * stand + 11.0F * graze - NECK * Mth.cos(headX);
			float headZ = -10.0F * rest + -1.0F * stand + -10.0F * graze - NECK * Mth.sin(headX);
			float headYaw = yaw * (rest + stand);
			headY += HEAD_DIP_DROP * state.headDip * rest;
			// (as changes from where the head rests: at rest and level it's where builds.json puts it)
			float dY = headY - HEAD_REST_Y, dZ = headZ - HEAD_REST_Z, dX = headX - HEAD_TILT;
			boolean open = state.feedingAnimation > MOUTH_OPEN;
			for (ModelPart part : this.headParts) {
				PartPose o = part.getInitialPose();
				pose(part, o.y() + dY, o.z() + dZ, o.xRot() + dX, o.yRot() + headYaw);
			}
			this.upperMouth.visible = this.lowerMouth.visible = !open;
			this.upperMouthOpen.visible = this.lowerMouthOpen.visible = open;
			for (ModelPart rein : new ModelPart[] {this.reinLeft, this.reinRight}) {
				PartPose o = rein.getInitialPose();
				pose(rein, o.y() + dY, o.z() + dZ, o.xRot() + pitch, o.yRot() + headYaw);
			}
			if (this.maneFlow != null) {
				// the fallen mane lifts off the neck with speed, rippling
				this.maneFlow.zRot = -MANE_SWAY * Math.min(1.0F, speed) * (0.6F + 0.4F * Mth.sin(ticks * MANE_RIPPLE));
			}

			// rearing: up on the hind legs
			this.body.xRot = -45.0F * DEG * stand;
			float tailUp = 6.0F * stand;
			this.tailA.y = this.tailA.getInitialPose().y() + tailUp;

			// the legs: walking, and reared (the forelegs paw the air, the hind legs fold under)
			float rB = rLeg, lB = lLeg, rC = rLeg, lC = lLeg;
			if (speed > 0.2F) {
				float rNext = Mth.cos((walked + 0.1F) * 0.6662F + Mth.PI) * 0.8F * speed;
				float lNext = Mth.cos((walked + 0.1F) * 0.6662F) * 0.8F * speed;
				if (rLeg > rNext) {
					rB = rLeg + 55.0F * DEG;
				} else if (rLeg < rNext) {
					rC = rLeg + 15.0F * DEG;
				}
				if (lLeg > lNext) {
					lB = lLeg + 55.0F * DEG;
				} else if (lLeg < lNext) {
					lC = lLeg + 15.0F * DEG;
				}
			}
			float rPaw = -60.0F * DEG + Mth.cos(ticks * 0.4F + Mth.PI);
			float lPaw = -60.0F * DEG + Mth.cos(ticks * 0.4F);
			float pawBend = 45.0F * DEG, hindUpper = -15.0F * DEG, hindLower = 15.0F * DEG;
			// reared, the forelegs come up and back under the chest
			float frontY = -11.0F * stand, frontZ = 6.0F * stand;
			for (ModelPart leg : new ModelPart[] {this.leg3A, this.leg4A}) {
				leg.y = leg.getInitialPose().y() + frontY;
				leg.z = leg.getInitialPose().z() + frontZ;
			}
			leg(this.leg1A, this.leg1B, this.leg1C, Mth.lerp(stand, lLeg, hindUpper), Mth.lerp(stand, lC, hindLower), lLeg, hindUpper, stand);
			leg(this.leg2A, this.leg2B, this.leg2C, Mth.lerp(stand, rLeg, hindUpper), Mth.lerp(stand, rC, hindLower), rLeg, hindUpper, stand);
			leg(this.leg3A, this.leg3B, this.leg3C, Mth.lerp(stand, rLeg, rPaw), Mth.lerp(stand, rB, pawBend), rLeg, rPaw, stand);
			leg(this.leg4A, this.leg4B, this.leg4C, Mth.lerp(stand, lLeg, lPaw), Mth.lerp(stand, lB, pawBend), lLeg, lPaw, stand);

			// the saddle sits back along the reared body; the stirrups swing unless someone's feet are in them
			float saddleY = -1.5F * stand, saddleZ = 9.0F * stand;
			for (ModelPart part : this.saddleParts) {
				part.y = part.getInitialPose().y() + saddleY;
				part.z = part.getInitialPose().z() + saddleZ;
			}
			this.saddle.xRot = this.saddleB.xRot = this.saddleC.xRot = this.body.xRot;
			boolean ridden = state.isRidden;
			float swingX = ridden ? -60.0F * DEG : rLeg / 3.0F;
			float swingZ = ridden ? 0.0F : rLeg / 5.0F;
			this.stirrupLeft.xRot = this.stirrupLeftIron.xRot = this.stirrupRight.xRot = this.stirrupRightIron.xRot = swingX;
			this.stirrupLeft.zRot = this.stirrupLeftIron.zRot = swingZ;
			this.stirrupRight.zRot = this.stirrupRightIron.zRot = -swingZ;
			// (0.7.309) under barding they hang outside the side plates
			Float out = state.armor != null ? STIRRUP_OUT.get(state.build) : null;
			for (ModelPart part : new ModelPart[] {this.stirrupLeft, this.stirrupLeftIron, this.stirrupRight, this.stirrupRightIron}) {
				float x = part.getInitialPose().x();
				part.x = out == null ? x : Math.copySign(Math.max(Math.abs(x), out), x);
			}

			// the tail: hangs at rest, lifts as it goes faster, swishes now and then
			float tailLift = Math.min(0.0F, -1.3089F + speed * 1.5F);
			float tailYaw = 0.0F;
			if (state.animateTail) {
				tailYaw = Mth.cos(ticks * 0.7F);
				tailLift = 0.0F;
			}
			this.tailB.y = this.tailB.getInitialPose().y() + tailUp;
			this.tailC.y = this.tailC.getInitialPose().y() + tailUp;
			this.tailA.yRot = this.tailB.yRot = this.tailC.yRot = tailYaw;
			this.tailA.xRot = this.tailB.xRot = tailLift;
			this.tailC.xRot = -0.2618F + tailLift;

			// what it wears (a vanilla saddle as the old horse's; ours and the bridle are TackLayer's)
			boolean saddled = state.vanillaSaddle;
			for (ModelPart part : this.saddleParts) {
				part.visible = saddled;
			}
			this.headSaddle.visible = this.bitLeft.visible = this.bitRight.visible = state.headstall;
			// (0.7.309) under barding the mane and forelock lie hidden beneath the crinet and chanfron
			boolean barded = state.armor != null;
			this.mane.visible = !barded;
			for (ModelPart part : this.forelock) {
				part.visible = !barded;
			}
			this.reinLeft.visible = this.reinRight.visible = state.headstall && ridden;
			this.wings(state, walked, ticks, stand);
		}

		/** The bat horse's wings: folded along its sides on the ground, spread in the air, beating while it climbs. */
		private void wings(BreedHorseRenderer.State state, float walked, float ticks, float stand) {
			if (this.innerWing == null || this.midWing == null || this.outerWing == null || this.innerWingR == null || this.midWingR == null
				|| this.outerWingR == null) {
				return;
			}
			ModelPart[] all = {this.innerWing, this.midWing, this.outerWing, this.innerWingR, this.midWingR, this.outerWingR};
			for (ModelPart part : all) {
				part.visible = state.wings;
				part.xRot = this.body.xRot;
			}
			if (!state.wings) {
				return;
			}
			this.innerWing.y = this.innerWing.getInitialPose().y() + (stand > 0.5F ? -8.0F : 0.0F);
			this.innerWing.z = this.innerWing.getInitialPose().z() + (stand > 0.5F ? 10.0F : 0.0F);
			float wingRot = state.flapping ? Mth.cos(ticks * 0.3F + Mth.PI) * 1.2F : Mth.cos(walked * 0.5F) * 0.1F;
			if (state.airborne) {
				this.outerWing.yRot = -0.3228859F + wingRot / 2.0F;
				this.outerWingR.yRot = 0.3228859F - wingRot / 2.0F;
			} else {
				wingRot = 60.0F * DEG;
				this.outerWing.yRot = -90.0F * DEG;
				this.outerWingR.yRot = 90.0F * DEG;
			}
			this.innerWingR.y = this.innerWing.y;
			this.innerWingR.z = this.innerWing.z;
			this.outerWing.x = this.innerWing.x + Mth.cos(wingRot) * 12.0F;
			this.outerWingR.x = this.innerWingR.x - Mth.cos(wingRot) * 12.0F;
			this.midWing.y = this.midWingR.y = this.innerWing.y;
			this.outerWing.y = this.innerWing.y + Mth.sin(wingRot) * 12.0F;
			this.outerWingR.y = this.innerWingR.y + Mth.sin(wingRot) * 12.0F;
			this.midWing.z = this.midWingR.z = this.outerWing.z = this.outerWingR.z = this.innerWing.z;
			this.midWing.zRot = this.innerWing.zRot = this.outerWing.zRot = wingRot;
			this.innerWingR.zRot = this.midWingR.zRot = this.outerWingR.zRot = -wingRot;
		}

		private static void pose(ModelPart part, float y, float z, float xRot, float yRot) {
			part.y = y;
			part.z = z;
			part.xRot = xRot;
			part.yRot = yRot;
		}

		/**
		 * One leg: the upper leg swung by {@code upper}, the cannon and hoof hung from its end and swung by {@code lower}.
		 * The cannon is placed by the upper leg's angle while walking but by the hind thigh's fixed angle reared; both are
		 * blended by {@code stand}.
		 */
		private static void leg(ModelPart a, ModelPart b, ModelPart c, float upper, float lower, float walkAngle, float standAngle, float stand) {
			a.xRot = upper;
			float hang = Mth.lerp(stand, walkAngle, standAngle);
			// the upper leg's length: from its pivot down to the cannon's, at rest
			PartPose a0 = a.getInitialPose(), b0 = b.getInitialPose(), c0 = c.getInitialPose();
			float thigh = b0.y() - a0.y();
			b.y = a.y + Mth.sin(90.0F * DEG + hang) * thigh;
			b.z = a.z + Mth.cos(270.0F * DEG + hang) * thigh + (b0.z() - a0.z());
			c.y = b.y + (c0.y() - b0.y());
			c.z = b.z + (c0.z() - b0.z());
			b.xRot = c.xRot = lower;
		}
	}
}
