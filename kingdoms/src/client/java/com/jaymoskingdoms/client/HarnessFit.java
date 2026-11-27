package com.jaymoskingdoms.client;

import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The harness's hoods and over-garments ({@link HeraldryLayer}) drawn close over whatever is worn under them. Andy
 * (2026-11-27), of a villager in an everyday hood and cloak: "insane amount of space on these armor pieces. can you make it
 * conform to the piece underneath?"
 * <p>
 * The hood, the cowl, the mantle, the cloak, the giornea, the tabard, the surcoat, the kaftan and the rest are cut wide
 * enough to go on over a helm and a cuirass. Over a bare head or a shirt they stood a pixel or two off it all round, and
 * the cloak's cape hung far out behind. Now each is drawn a little smaller, about the bone it hangs on, until it sits
 * {@link #HEAD_GAP} or {@link #BODY_GAP} past what is actually under it: the bare body (with MCA's clothes and the suit under
 * the harness, {@link #BARE}), then every harness piece drawn before it, innermost first ({@link Shell}). Never larger
 * than it was made: over a helm or a cuirass it stays as it is cut, or close to it.
 * <p>
 * Armour from anywhere else (vanilla's, the Armory's, other mods') isn't measured: over it a piece is left as it was made.
 */
final class HarnessFit {
	/** How far past its bone the bare body reaches in each direction (pixels): MCA's clothes layer (0.3125) and the mail sheet under the harness (0.5). */
	static final float BARE = 0.5F;
	/** How far a fitted hood stands off what is under it (pixels), and an over-garment or a skirt. */
	static final float HEAD_GAP = 0.3F, BODY_GAP = 0.4F;
	/** How much of the cloak's resting swing back the cape keeps when the cloak is fitted close (it hung 7 degrees out, for plate). */
	static final float CAPE_TILT = 0.4F;

	/** The parts of the body a piece is fitted on. */
	enum Zone {
		HEAD(-4.0F, 4.0F, -8.0F, 0.0F, -4.0F, 4.0F), BODY(-4.0F, 4.0F, 0.0F, 12.0F, -2.0F, 2.0F), LEGS(-2.0F, 2.0F, 0.0F, 12.0F, -2.0F, 2.0F);

		/** The bone's own box (pixels, in its frame): {minX, maxX, minY, maxY, minZ, maxZ}. */
		final float[] bone;

		Zone(float... bone) {
			this.bone = bone;
		}

		/** The zone of the wearer's bone {@code bone} (a {@link HeraldryLayer} skeleton name); null for the arms. */
		static @Nullable Zone of(String bone) {
			return switch (bone) {
				case "head" -> HEAD;
				case "body" -> BODY;
				case "right_leg", "left_leg" -> LEGS;
				default -> null;
			};
		}
	}

	/**
	 * How a piece is drawn smaller: {@code head} its head scaled about the neck (1 for as made), and for the body and each
	 * leg {kx, px, kz, pz} (scaled by kx across about x = px, and by kz front to back about z = pz), or null for as made.
	 */
	record Fit(float head, float @Nullable [] body, float @Nullable [] rightLeg, float @Nullable [] leftLeg) {
		boolean any() {
			return this.head < 1.0F || this.body != null || this.rightLeg != null || this.leftLeg != null;
		}

		/** Draws {@code piece} (posed as the wearer's body) this much smaller. */
		void apply(HumanoidModel<?> piece) {
			if (this.head < 1.0F) {
				piece.head.xScale *= this.head;
				piece.head.yScale *= this.head;
				piece.head.zScale *= this.head;
			}
			scale(piece.body, this.body);
			scale(piece.rightLeg, this.rightLeg);
			scale(piece.leftLeg, this.leftLeg);
		}
	}

	/**
	 * What one wearer has on so far, zone by zone, as the layer draws their pieces innermost first: the box everything worn
	 * reaches to, or null where something from elsewhere is worn (fit nothing over it there).
	 */
	static final class Shell {
		private final float[] @Nullable [] reach = new float[Zone.values().length][];

		/** The bare body; {@code head}, {@code chest}, {@code legs}, {@code feet}: whether armour from elsewhere is worn there. */
		Shell(boolean head, boolean chest, boolean legs, boolean feet) {
			for (Zone zone : Zone.values()) {
				float[] box = zone.bone.clone();
				for (int i = 0; i < 6; i++) {
					box[i] += i % 2 == 0 ? -BARE : BARE;
				}
				this.reach[zone.ordinal()] = box;
			}
			if (head) {
				this.reach[Zone.HEAD.ordinal()] = null;
			}
			if (chest) {
				this.reach[Zone.BODY.ordinal()] = null;
			}
			if (legs || feet) {
				this.reach[Zone.LEGS.ordinal()] = null;
			}
		}

		/**
		 * How a loose piece (a hood, an over-garment) whose boxes, by the wearer's bone, are {@code bounds} is fitted over
		 * what is on so far; null where it is drawn as made.
		 */
		@Nullable Fit fit(Map<String, float[]> bounds) {
			float head = 1.0F;
			float[] headBox = bounds.get("head"), under = this.reach[Zone.HEAD.ordinal()];
			if (headBox != null && under != null) {
				// every side must still clear what is under it: the side with the least room sets the scale
				head = 0.0F;
				for (int i = 0; i < 6; i++) {
					// (the bottom, at the neck, stays where it is)
					if (i == 3) {
						continue;
					}
					float need = under[i] + (i % 2 == 0 ? -HEAD_GAP : HEAD_GAP);
					if (Math.abs(headBox[i]) > 1.0E-3F && Math.signum(need) == Math.signum(headBox[i])) {
						head = Math.max(head, need / headBox[i]);
					}
				}
				head = Math.min(1.0F, head <= 0.0F ? 1.0F : head);
			}
			Fit fit = new Fit(head, fitBox(bounds.get("body"), Zone.BODY), fitBox(bounds.get("right_leg"), Zone.LEGS), fitBox(bounds.get("left_leg"), Zone.LEGS));
			return fit.any() ? fit : null;
		}

		private float @Nullable [] fitBox(float @Nullable [] box, Zone zone) {
			float[] under = this.reach[zone.ordinal()];
			if (box == null || under == null) {
				return null;
			}
			float[] x = squeeze(box[0], box[1], under[0] - BODY_GAP, under[1] + BODY_GAP);
			float[] z = squeeze(box[4], box[5], under[4] - BODY_GAP, under[5] + BODY_GAP);
			return x == null && z == null ? null : new float[] {x == null ? 1.0F : x[0], x == null ? 0.0F : x[1], z == null ? 1.0F : z[0], z == null ? 0.0F : z[1]};
		}

		/**
		 * Adds a piece drawn on this wearer (its boxes by the wearer's bone, as made) to what is on, drawn {@code fit} smaller
		 * (null for as made): what's drawn over it next is fitted over it.
		 */
		void cover(Map<String, float[]> bounds, @Nullable Fit fit) {
			bounds.forEach((bone, made) -> {
				Zone zone = Zone.of(bone);
				if (zone == null || this.reach[zone.ordinal()] == null) {
					return;
				}
				float[] box = made.clone();
				if (fit != null) {
					if (zone == Zone.HEAD) {
						for (int i = 0; i < 6; i++) {
							box[i] *= fit.head();
						}
					} else {
						float[] k = zone == Zone.BODY ? fit.body() : bone.equals("right_leg") ? fit.rightLeg() : fit.leftLeg();
						if (k != null) {
							box[0] = k[1] + k[0] * (box[0] - k[1]);
							box[1] = k[1] + k[0] * (box[1] - k[1]);
							box[4] = k[3] + k[2] * (box[4] - k[3]);
							box[5] = k[3] + k[2] * (box[5] - k[3]);
						}
					}
				}
				float[] reach = this.reach[zone.ordinal()];
				for (int i = 0; i < 6; i++) {
					reach[i] = i % 2 == 0 ? Math.min(reach[i], box[i]) : Math.max(reach[i], box[i]);
				}
			});
		}
	}

	private HarnessFit() {
	}

	/**
	 * The scale and its fixed point that bring the span {@code min} to {@code max} down onto {@code to0} to {@code to1}
	 * ({k, p}); null when the span is no wider than that already (a piece is never drawn larger than it was made).
	 */
	static float @Nullable [] squeeze(float min, float max, float to0, float to1) {
		float k = (to1 - to0) / (max - min);
		if (!(k < 0.999F) || k <= 0.0F) {
			return null;
		}
		// p + k (max - p) = to1
		float p = (to1 - k * max) / (1.0F - k);
		return new float[] {k, p};
	}

	/** {@code part} scaled by {kx, px, kz, pz} (see {@link Fit}); nothing for null. */
	private static void scale(ModelPart part, float @Nullable [] k) {
		if (k != null) {
			scaleAbout(part, k[0], k[2], k[1], k[3]);
		}
	}

	/**
	 * Scales {@code part} by {@code kx} across and {@code kz} front to back, keeping the point ({@code px}, any, {@code pz})
	 * of its own frame where it was (as {@code McaHarnessFit}'s, which only loads with MCA).
	 */
	static void scaleAbout(ModelPart part, float kx, float kz, float px, float pz) {
		float sx = part.xScale, sz = part.zScale;
		Vector3f shift = new Vector3f(sx * px * (1.0F - kx), 0.0F, sz * pz * (1.0F - kz));
		if (part.xRot != 0.0F || part.yRot != 0.0F || part.zRot != 0.0F) {
			new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(shift);
		}
		part.x += shift.x;
		part.y += shift.y;
		part.z += shift.z;
		part.xScale = sx * kx;
		part.zScale = sz * kz;
	}
}
