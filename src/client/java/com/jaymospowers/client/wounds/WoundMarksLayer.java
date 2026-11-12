package com.jaymospowers.client.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.wet.ArmourGrimeTextures;
import com.jaymospowers.core.BladeBlood;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.Breaks;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.WoundMarks;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The marks wounds leave ({@link WoundMarks}), drawn into the skin: a cut (a dark line with raw edges), claws (three of
 * them side by side), a puncture (a dark hole with a run of blood under it), a bruise (a purple patch that yellows as it
 * heals), a blast (specks and soot), a scorch (a charred patch with a singed rim: fire, lightning and ki). An open cut on a
 * body that isn't shaped like a person (the dressings layer only draws on those) is painted the same way, as a gash. On a humanoid skin (players, villagers, zombies and the rest of the humanoid layout)
 * each lies on its own part and side; on anything else it lies somewhere on the skin, the same place every time. They fade
 * as they age, in a few steps, so each body's painted picture changes seldom.
 * <p>
 * Only the mark's pixels are painted (the rest of the picture is clear), so the blood coat ({@code BloodCoatLayer}) and the
 * skin show through everywhere else; each mark pixel is the skin's own pixel with the mark mixed in.
 */
public final class WoundMarksLayer {
	static final RenderStateDataKey<Marked> KEY = RenderStateDataKey.create(() -> "jaymospowers:wound_marks");
	/** How many fade steps a mark goes through. */
	static final int STAGES = 4;
	/** At most this many painted pictures are kept; past it they're all thrown away and painted again as needed. */
	static final int MOST_KEPT = 300;
	/** The colours of a fresh bruise and an old one, and a blast's soot. */
	static final int BRUISE_FRESH = 0x4A2C5E, BRUISE_OLD = 0x8C8A3C, SOOT = 0x2A2522;
	/** A scorch: the char at its middle, the singed rim round it, and the pink it heals to. */
	/** A bloodless body's cuts and rakes: chipped bone, cracked stone or bark, not blood. */
	static final int CHIPPED = 0x2B2724;
	static final int SCORCH_CORE = 0x1C1714, SCORCH_RIM = 0x6B3A22, SCORCH_OLD = 0xA8665A;
	/** A healed stump's scar, and the seam it closed along. */
	static final int SCAR = 0xD19A8C, SEAM = 0x8E5A50;

	/** What a body's marks look like this frame: the marks, their stages, and the body's blood colour. */
	record Marked(List<WoundMarks.Mark> marks, int[] stages, int blood, int stubs, int raw, int gashes, int seed) {
		String key() {
			StringBuilder b = new StringBuilder();
			b.append('s').append(this.stubs).append('r').append(this.raw).append('g').append(this.gashes).append('x').append(this.seed).append('|');
			for (int i = 0; i < this.marks.size(); i++) {
				b.append(Long.toHexString(this.marks.get(i).shape())).append(':').append(this.stages[i]).append(',');
			}
			return b.append(Integer.toHexString(this.blood)).toString();
		}
	}

	private static final Map<String, Identifier> MADE = new HashMap<>();
	private static final Set<Identifier> UNREADABLE = new HashSet<>();
	private static int made;

	private WoundMarksLayer() {
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> helper.register((RenderLayer) new Layer(renderer)));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> MADE.clear());
	}

	/** From every living renderer's extractRenderState. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		List<WoundMarks.Mark> marks = WoundMarks.marks(entity);
		// the lost arms and legs drawn as stumps (Dismembered), and which of them still bleed
		int stubs = Dismember.missing(entity) & ~Dismember.HEAD & ~com.jaymospowers.core.Prosthetics.fitted(entity);
		// an open cut on a body the dressings layer can't draw on (anything not built like a person) is painted into its skin
		// like the other marks: the bleeding parts that nothing is wrapped over (a person's are drawn by the dressings layer)
		int wrapped = Bleeding.wrapped(entity);
		int gashes = Bleeding.bleeding(entity) & ~(wrapped | wrapped >>> Bleeding.RAG_SHIFT) & (1 << Bleeding.RAG_SHIFT) - 1;
		if (marks.isEmpty() && stubs == 0 && gashes == 0) {
			state.setData(KEY, null);
			return;
		}
		int raw = 0;
		int bleeding = Bleeding.bleeding(entity);
		for (int part : Dismember.PARTS) {
			BodyPart lost = (stubs & part) != 0 ? Breaks.part(part) : null;
			if (lost != null && (bleeding & 1 << lost.ordinal()) != 0) {
				raw |= part;
			}
		}
		long now = entity.level().getGameTime();
		int[] stages = new int[marks.size()];
		for (int i = 0; i < stages.length; i++) {
			// stage STAGES is fresh, 1 nearly gone
			stages[i] = Mth.clamp(STAGES - (int) (marks.get(i).age(now) * STAGES), 1, STAGES);
		}
		int blood = BladeBlood.color(entity);
		state.setData(KEY, new Marked(marks, stages, blood == BladeBlood.NO_BLOOD ? CHIPPED : blood, stubs, raw, gashes, entity.getId() * 31));
	}

	static final class Layer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
		private final LivingEntityRenderer<?, S, M> renderer;

		Layer(LivingEntityRenderer<?, S, M> renderer) {
			super(renderer);
			this.renderer = renderer;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Marked marked = state.getData(KEY);
			if (marked == null || state.isInvisible) {
				return;
			}
			boolean person = this.getParentModel() instanceof HumanoidModel;
			if (person && marked.marks().isEmpty() && marked.stubs() == 0) {
				// (a person's open cut is the dressings layer's)
				return;
			}
			Identifier skin;
			try {
				skin = this.renderer.getTextureLocation(state);
			} catch (RuntimeException e) {
				return;
			}
			Identifier painted = skin == null ? null : paint(skin, marked, person);
			if (painted != null) {
				renderColoredCutoutModel(this.getParentModel(), painted, poseStack, collector, lightCoords, state, -1, 1);
			}
		}
	}

	private static @Nullable Identifier paint(Identifier skin, Marked marked, boolean humanoid) {
		if (UNREADABLE.contains(skin)) {
			return null;
		}
		String key = skin + "|" + humanoid + "|" + (humanoid ? marked.key().replaceFirst("g\\d+x-?\\d+", "") : marked.key());
		Identifier done = MADE.get(key);
		if (done != null) {
			return done;
		}
		NativeImage base = ArmourGrimeTextures.copy(skin);
		if (base == null) {
			UNREADABLE.add(skin);
			return null;
		}
		if (MADE.size() >= MOST_KEPT) {
			Minecraft minecraft = Minecraft.getInstance();
			for (Identifier id : MADE.values()) {
				minecraft.getTextureManager().release(id);
			}
			MADE.clear();
		}
		int w = base.getWidth(), h = base.getHeight();
		NativeImage out = new NativeImage(w, h, true);
		try (base) {
			boolean layout = humanoid && (w == h || w == h * 2) && w >= 64;
			// (stumps are only drawn on the humanoid layout; elsewhere there is nothing to paint for them)
			Painter painter = new Painter(base, out, layout);
			for (int i = 0; i < marked.marks().size(); i++) {
				painter.mark(marked.marks().get(i), marked.stages()[i] / (float) STAGES, marked.blood());
			}
			if (!humanoid) {
				// each open cut: a fresh gash, somewhere on the skin that is the same every time for this body and part
				BodyPart[] parts = BodyPart.values();
				for (int i = 0; i < parts.length; i++) {
					if ((marked.gashes() & 1 << i) != 0) {
						int seed = (marked.seed() * 17 + i * 40503) & 0xFFFF;
						painter.mark(WoundMarks.Mark.of(parts[i], WoundMarks.Kind.CUT, WoundMarks.FRONT, 128, 128, (seed >> 3) & 0xFF, seed, 0L), 1.0F, marked.blood());
					}
				}
			}
			if (layout) {
				for (int part : Dismember.PARTS) {
					if ((marked.stubs() & part) != 0) {
						painter.stump(part, (marked.raw() & part) != 0, marked.blood());
					}
				}
			}
		}
		Identifier id = JaymosPowers.id("wound_marks/" + made++);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(id::toString, out));
		MADE.put(key, id);
		return id;
	}

	/** Paints marks from a skin ({@code base}) onto a clear picture ({@code out}) the same size. */
	static final class Painter {
		private final NativeImage base, out;
		private final int w, h;
		/** One skin pixel per this many picture pixels across (a 64-wide skin is 1). */
		private final int unit;
		private final boolean layout;
		/** Whether the skin is the old 64 by 32 layout (no separate left limbs). */
		private final boolean old;

		Painter(NativeImage base, NativeImage out, boolean layout) {
			this.base = base;
			this.out = out;
			this.w = base.getWidth();
			this.h = base.getHeight();
			this.unit = Math.max(1, this.w / 64);
			this.layout = layout;
			this.old = this.w == this.h * 2;
		}

		void mark(WoundMarks.Mark mark, float strength, int blood) {
			int[] rect = this.layout ? rect(mark.part(), mark.side()) : null;
			double cx, cy, size;
			if (rect != null) {
				cx = (rect[0] + rect[2] * mark.across()) * this.unit;
				cy = (rect[1] + rect[3] * mark.down()) * this.unit;
				size = Math.min(rect[2], rect[3]) * this.unit;
			} else {
				int[] at = somewhere(mark.seed());
				if (at == null) {
					return;
				}
				cx = at[0];
				cy = at[1];
				size = 6.0 * this.unit;
			}
			java.util.Random random = new java.util.Random(mark.seed());
			int core = scale(blood, 0.45F), raw = scale(blood, 0.85F);
			switch (mark.kind()) {
				case CUT -> this.cut(cx, cy, mark.angle(), Math.max(3.0, size * 0.75), core, raw, strength, rect);
				case CLAW -> {
					double nx = -Math.sin(mark.angle()) * 1.6 * this.unit, ny = Math.cos(mark.angle()) * 1.6 * this.unit;
					for (int k = -1; k <= 1; k++) {
						this.cut(cx + nx * k, cy + ny * k, mark.angle(), Math.max(2.5, size * 0.55), core, raw, strength * 0.9F, rect);
					}
				}
				case STAB -> {
					this.blot(cx, cy, 0.6 * this.unit, scale(blood, 0.25F), strength, rect);
					this.ring(cx, cy, 1.4 * this.unit, raw, strength * 0.6F, rect);
					for (int k = 1; k <= 2 + random.nextInt(2); k++) {
						this.dot(cx, cy + k * this.unit, raw, strength * (0.7F - k * 0.15F), rect);
					}
				}
				case BRUISE -> {
					int colour = lerp(1.0F - strength, BRUISE_FRESH, BRUISE_OLD);
					this.patch(cx, cy, Math.max(1.5 * this.unit, size * 0.35), colour, 0.5F * (0.4F + 0.6F * strength), random, rect);
				}
				case SCORCH -> {
					// a charred patch with a singed rim, cracked in places; it lightens as it heals
					double r = Math.max(2.0 * this.unit, size * 0.4);
					this.patch(cx, cy, r + this.unit, lerp(1.0F - strength, SCORCH_RIM, SCORCH_OLD), 0.35F * strength, random, rect);
					this.patch(cx, cy, r, lerp(1.0F - strength, SCORCH_CORE, SCORCH_OLD), 0.8F * (0.4F + 0.6F * strength), random, rect);
					for (int k = 0; k < 3; k++) {
						double a = random.nextDouble() * Math.PI * 2.0;
						this.dot(cx + Math.cos(a) * r * 0.5, cy + Math.sin(a) * r * 0.5, SCORCH_CORE, strength, rect);
					}
				}
				case BLAST -> {
					this.patch(cx, cy, Math.max(2.0 * this.unit, size * 0.45), SOOT, 0.3F * strength, random, rect);
					int specks = 4 + random.nextInt(3);
					double r = Math.max(2.0 * this.unit, size * 0.4);
					for (int k = 0; k < specks; k++) {
						double a = random.nextDouble() * Math.PI * 2.0, d = random.nextDouble() * r;
						this.dot(cx + Math.cos(a) * d, cy + Math.sin(a) * d, core, strength, rect);
					}
				}
			}
		}

		/**
		 * A lost limb's stump end ({@code part}, a {@link Dismember} bit): the limb's bottom face (what a shortened limb
		 * shows at its end) and the last row of each side. Raw while it bleeds; capped once it has closed: the skin drawn
		 * in over it, pink and puckered to a seam.
		 */
		void stump(int part, boolean raw, int blood) {
			int x, y;
			switch (part) {
				case Dismember.RIGHT_ARM -> {
					x = 40; y = 16;
				}
				case Dismember.LEFT_ARM -> {
					x = this.old ? 40 : 32; y = this.old ? 16 : 48;
				}
				case Dismember.RIGHT_LEG -> {
					x = 0; y = 16;
				}
				case Dismember.LEFT_LEG -> {
					x = this.old ? 0 : 16; y = this.old ? 16 : 48;
				}
				default -> {
					return;
				}
			}
			// a limb box is 4 by 4 by 12: its bottom face lies right of its top face, at the top of its strip
			int[] end = {x + 8, y, 4, 4};
			double cx = (end[0] + 2) * this.unit, cy = (end[1] + 2) * this.unit;
			for (int py = end[1] * this.unit; py < (end[1] + end[3]) * this.unit; py++) {
				for (int px = end[0] * this.unit; px < (end[0] + end[2]) * this.unit; px++) {
					double d = Math.hypot(px + 0.5 - cx, py + 0.5 - cy) / (2.0 * this.unit);
					if (raw) {
						this.dot(px, py, d < 0.55 ? scale(blood, 0.5F) : scale(blood, 0.9F), 0.9F, end);
					} else {
						// healed over: scar pink, a darker seam across the middle
						boolean seam = Math.abs(py + 0.5 - cy) < 0.5 * this.unit && d < 0.8;
						this.dot(px, py, seam ? SEAM : SCAR, seam ? 0.55F : 0.4F, end);
					}
				}
			}
			// the last row of each side, where the stump ends
			int[] rim = {x, y + 4 + 11, 16, 1};
			for (int py = rim[1] * this.unit; py < (rim[1] + rim[3]) * this.unit; py++) {
				for (int px = rim[0] * this.unit; px < (rim[0] + rim[2]) * this.unit; px++) {
					this.dot(px, py, raw ? scale(blood, 0.8F) : SCAR, raw ? 0.7F : 0.35F, rim);
				}
			}
		}

		/**
		 * The rectangle (64ths: x, y, width, height) of {@code side} of {@code part} on a humanoid skin, or null for a part
		 * the layout doesn't have. A box's sides lie along its strip in the order right, front, left, back.
		 */
		@Nullable int[] rect(BodyPart part, int side) {
			int x, y, wide, deep, tall;
			switch (part) {
				case HEAD -> {
					x = 0; y = 0; wide = 8; deep = 8; tall = 8;
				}
				case BODY -> {
					x = 16; y = 16; wide = 8; deep = 4; tall = 12;
				}
				case RIGHT_ARM -> {
					x = 40; y = 16; wide = 4; deep = 4; tall = 12;
				}
				case LEFT_ARM -> {
					x = this.old ? 40 : 32; y = this.old ? 16 : 48; wide = 4; deep = 4; tall = 12;
				}
				case RIGHT_LEG, RIGHT_FOOT -> {
					x = 0; y = 16; wide = 4; deep = 4; tall = 12;
				}
				case LEFT_LEG, LEFT_FOOT -> {
					x = this.old ? 0 : 16; y = this.old ? 16 : 48; wide = 4; deep = 4; tall = 12;
				}
				default -> {
					return null;
				}
			}
			int top = y + deep;
			int[] r = switch (side) {
				case WoundMarks.RIGHT -> new int[] {x, top, deep, tall};
				case WoundMarks.LEFT -> new int[] {x + deep + wide, top, deep, tall};
				case WoundMarks.BACK -> new int[] {x + deep + wide + deep, top, wide, tall};
				default -> new int[] {x + deep, top, wide, tall};
			};
			if (part == BodyPart.LEFT_FOOT || part == BodyPart.RIGHT_FOOT) {
				// the foot is the bottom of the leg
				r[1] += tall * 2 / 3;
				r[3] = tall - tall * 2 / 3;
			}
			return r;
		}

		/** A skin pixel picked by {@code seed} that the skin has (not a clear one), or null if none turned up. */
		int @Nullable [] somewhere(int seed) {
			java.util.Random random = new java.util.Random(seed * 31L + 7L);
			for (int tries = 0; tries < 60; tries++) {
				int x = random.nextInt(this.w), y = random.nextInt(this.h);
				if ((this.base.getPixel(x, y) >>> 24) > 0) {
					return new int[] {x, y};
				}
			}
			return null;
		}

		private void cut(double cx, double cy, float angle, double length, int core, int raw, float strength, int @Nullable [] rect) {
			double dx = Math.cos(angle), dy = Math.sin(angle);
			double nx = -dy, ny = dx;
			int steps = (int) Math.ceil(length * 2.0);
			for (int i = 0; i <= steps; i++) {
				double t = i / (double) steps - 0.5;
				double px = cx + dx * length * t, py = cy + dy * length * t;
				// thinner at the ends
				float taper = 1.0F - (float) Math.abs(t) * 1.2F;
				if (taper <= 0.0F) {
					continue;
				}
				this.dot(px, py, core, strength * (0.6F + 0.4F * taper), rect);
				this.dot(px + nx * this.unit, py + ny * this.unit, raw, strength * 0.45F * taper, rect);
				this.dot(px - nx * this.unit, py - ny * this.unit, raw, strength * 0.45F * taper, rect);
			}
		}

		private void blot(double cx, double cy, double r, int colour, float strength, int @Nullable [] rect) {
			int reach = (int) Math.ceil(r);
			for (int y = -reach; y <= reach; y++) {
				for (int x = -reach; x <= reach; x++) {
					if (x * x + y * y <= r * r + 0.5) {
						this.dot(cx + x, cy + y, colour, strength, rect);
					}
				}
			}
		}

		private void ring(double cx, double cy, double r, int colour, float strength, int @Nullable [] rect) {
			int reach = (int) Math.ceil(r);
			for (int y = -reach; y <= reach; y++) {
				for (int x = -reach; x <= reach; x++) {
					double d = Math.sqrt(x * x + y * y);
					if (d > r - 1.0 && d <= r) {
						this.dot(cx + x, cy + y, colour, strength, rect);
					}
				}
			}
		}

		/** A soft, ragged patch: strongest in the middle, uneven at its edge. */
		private void patch(double cx, double cy, double r, int colour, float strength, java.util.Random random, int @Nullable [] rect) {
			int reach = (int) Math.ceil(r);
			for (int y = -reach; y <= reach; y++) {
				for (int x = -reach; x <= reach; x++) {
					double d = Math.sqrt(x * x + y * y) / r;
					double edge = 0.75 + random.nextDouble() * 0.35;
					if (d <= edge) {
						this.dot(cx + x, cy + y, colour, strength * (float) (1.0 - 0.6 * d), rect);
					}
				}
			}
		}

		/** One picture pixel at ({@code x}, {@code y}): the skin's own pixel with {@code colour} mixed in. */
		private void dot(double x, double y, int colour, float strength, int @Nullable [] rect) {
			int px = (int) Math.floor(x), py = (int) Math.floor(y);
			if (px < 0 || py < 0 || px >= this.w || py >= this.h || strength < 0.12F) {
				return;
			}
			if (rect != null && (px < rect[0] * this.unit || py < rect[1] * this.unit || px >= (rect[0] + rect[2]) * this.unit
				|| py >= (rect[1] + rect[3]) * this.unit)) {
				return;
			}
			int argb = this.base.getPixel(px, py);
			if ((argb >>> 24) == 0) {
				return;
			}
			int was = this.out.getPixel(px, py);
			// a pixel already marked mixes on from that
			int from = (was >>> 24) != 0 ? was : argb;
			float k = Math.min(1.0F, strength);
			int r = mix(from >> 16 & 0xFF, colour >> 16 & 0xFF, k), g = mix(from >> 8 & 0xFF, colour >> 8 & 0xFF, k), b = mix(from & 0xFF, colour & 0xFF, k);
			this.out.setPixel(px, py, 0xFF000000 | r << 16 | g << 8 | b);
		}
	}

	private static int mix(int from, int to, float t) {
		return Mth.clamp(Math.round(from + (to - from) * t), 0, 255);
	}

	private static int lerp(float t, int from, int to) {
		return mix(from >> 16 & 0xFF, to >> 16 & 0xFF, t) << 16 | mix(from >> 8 & 0xFF, to >> 8 & 0xFF, t) << 8 | mix(from & 0xFF, to & 0xFF, t);
	}

	private static int scale(int rgb, float k) {
		return Mth.clamp(Math.round((rgb >> 16 & 0xFF) * k), 0, 255) << 16 | Mth.clamp(Math.round((rgb >> 8 & 0xFF) * k), 0, 255) << 8
			| Mth.clamp(Math.round((rgb & 0xFF) * k), 0, 255);
	}
}
