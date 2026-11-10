package com.jaymospowers.client.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.Future;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * The future's dead are all Warrior-born (Andy: "husks zombies and player zombies all with mangled tails in various
 * conditions (12+ at least) and 30+ different skins"). Each grown zombie there wears one of our civilian skins and each
 * husk one of our dried ones (tools/gen_future_zombies.py; {@code FutureZombieRendererMixin}, {@code FutureHuskRendererMixin}),
 * and every zombie, husk and stranger's zombie player there (Kitchen's, from {@code FutureKitchen}) drags a mangled tail
 * in one of {@link Condition}'s states. Skin and tail are picked by the mob's id, so it keeps them. Back home, and on a
 * dead player who got up again, nothing changes.
 *
 * <p>The tail is drawn like a Warrior-born's own ({@code zfighter/ZFighterTail}): a chain of boxes from the small of the
 * back, each at its own place on the pose stack, from the materials in {@code textures/entity/future/dead_tail.png}
 * (the boxes below must match the patches the generator paints, its {@code TAIL_PARTS}). Dead tails barely sway; a
 * snapped one hangs from its break.
 */
public final class FutureDeadLooks {
	/** Skins of each kind (civilian_n and husk_n). */
	public static final int SKINS = 32;
	private static final Identifier[] CIVILIANS = new Identifier[SKINS];
	private static final Identifier[] HUSKS = new Identifier[SKINS];
	/** On a future zombie's or husk's render state: its skin. */
	static final RenderStateDataKey<Identifier> SKIN = RenderStateDataKey.create(() -> "jaymospowers:future_dead_skin");
	/** On a future dead's render state: its tail. */
	static final RenderStateDataKey<Tail> TAIL = RenderStateDataKey.create(() -> "jaymospowers:future_dead_tail");
	static final Identifier TAIL_TEXTURE = JaymosPowers.id("textures/entity/future/dead_tail.png");
	/** Kitchen's zombie player, which gets a tail too when it's a stranger's. */
	static final Identifier ZOMBIE_PLAYER = Identifier.fromNamespaceAndPath("jaymoskitchen", "zombie_player");

	/** A tail: 5 to 7 segments, each this long (pixels) and thick (scale), the first one's droop and the rest's curl. */
	static final int SEGMENTS_MIN = 5, SEGMENTS_MAX = 7;
	static final float LENGTH_MIN = 2.2F, LENGTH_MAX = 2.8F, THICK_MIN = 0.8F, THICK_MAX = 1.05F;
	static final float DROOP_MIN = -1.0F, DROOP_MAX = -0.6F, CURL_MIN = 0.08F, CURL_MAX = 0.3F;
	/**
	 * A segment's box is baked 3 pixels long and stretched by its length over this, so it overlaps the next a little (no
	 * gaps at the bends); the next segment is this much smaller.
	 */
	static final float STEP = 2.6F, TAPER = 0.92F;
	/** How far a dead tail wanders on its own (radians, before its condition's share) and how slowly. */
	static final float SWAY = 0.12F, SWAY_SPEED = 0.045F;
	/** The swing on the stride while it walks, and the pendulum of a part hanging from a break (radians, speed). */
	static final float WALK_SWING = 0.08F, HANG_SWING = 0.18F, HANG_SPEED = 0.07F;
	/** BENT: the sideways bend; ZIGZAG: up at one break and back down at the next; SPLINTERED: the bend at the break. */
	static final float BEND = 1.0F, ZIG = 0.9F, ZAG = 1.4F, SPLINTER_BEND = 1.1F, SPLINTER_OUT = -0.9F;
	/** LIMP hangs almost straight down; STIFF sticks out behind; KNOTTED coils over itself. */
	static final float LIMP_DROOP = -1.35F, STIFF_DROOP = -0.15F, KNOT_CURL = 0.95F;
	/** BONE_TIP's bare bone is this thin; CRUSHED is flattened to this; FRAYED's two strands spread this far apart. */
	static final float BONE_THIN = 0.6F, FLAT_WIDE = 1.45F, FLAT_THIN = 0.4F, FRAY = 0.35F, FRAY_THIN = 0.55F;
	/** ROOT_STUMP's stub, as a share of a segment. */
	static final float STUB = 0.55F;
	/** Dead fur shades, multiplied over the texture's own matted brown; a husk's are dusted. */
	static final int[] FUR = {0xFFFFFFFF, 0xFFD8CCC0, 0xFFB4A698, 0xFF8E8478, 0xFFE6D2B8, 0xFF7A6A60};
	static final int DUSTY = 0xFFE8DCC0;
	static final int FUR_JITTER = 12;
	/** Rolled once per mob (each frame asks); past this many remembered, start over. */
	static final int CACHE_MAX = 512;

	/** How a dead tail is mangled. {@code sway}: its share of a tail's own wander. */
	public enum Condition {
		/** Bent hard to one side at an old break. */
		BENT(0.6F),
		/** Snapped: everything past the break hangs straight down and swings. */
		SNAPPED(0.3F),
		/** Cut through halfway: a raw end. */
		HALF_CUT(0.7F),
		/** Gone at the root: a stub. */
		ROOT_STUMP(0.0F),
		/** Hangs dead from the root, dragging. */
		LIMP(0.25F),
		/** Broken twice, up and back down. */
		ZIGZAG(0.4F),
		/** The fur torn away in patches, flesh beneath. */
		MANGY(0.8F),
		/** The tip rotted down to bare bone. */
		BONE_TIP(0.7F),
		/** Burnt black and short. */
		CHARRED(0.5F),
		/** Crushed flat past a kink. */
		CRUSHED(0.4F),
		/** Split at the end into two strands. */
		FRAYED(0.8F),
		/** Coiled tight over itself. */
		KNOTTED(0.3F),
		/** Gone rigid, sticking straight out behind. */
		STIFF(0.0F),
		/** Torn nearly through at the root: hangs by a strand. */
		BY_A_THREAD(0.2F),
		/** Cut short, an old rag knotted round the end. */
		RAGGED(0.6F),
		/** Broken with the bone through the fur. */
		SPLINTERED(0.5F);

		final float sway;

		Condition(float sway) {
			this.sway = sway;
		}
	}

	/** A dead tail: its condition, segments, length, thickness, droop, curl, phase, fur tint, damaged segment and side. */
	record Tail(Condition condition, int segments, float length, float thickness, float droop, float curl, float phase, int fur, int at,
		float side, boolean dusty) {
	}

	private enum Part {
		FUR(0, 0, 2, 2, 3), MANGY(12, 0, 2, 2, 3), FLESH(24, 0, 2, 2, 3), BONE(36, 0, 2, 2, 3), CHAR(48, 0, 2, 2, 3),
		RAG(0, 8, 3, 3, 2), CUT(12, 8, 2, 2, 3), STRAND(24, 8, 1, 1, 3), SPLINTER(36, 8, 1, 1, 2);

		final int u, v, w, h, d;
		private @Nullable ModelPart part;

		Part(int u, int v, int w, int h, int d) {
			this.u = u;
			this.v = v;
			this.w = w;
			this.h = h;
			this.d = d;
		}

		/** Its box, centred on the chain and running back from the joint. */
		ModelPart part() {
			if (this.part == null) {
				MeshDefinition mesh = new MeshDefinition();
				mesh.getRoot().addOrReplaceChild("part", CubeListBuilder.create().texOffs(this.u, this.v)
					.addBox(-this.w / 2.0F, -this.h / 2.0F, 0.0F, this.w, this.h, this.d), PartPose.ZERO);
				this.part = LayerDefinition.create(mesh, 64, 32).bakeRoot().getChild("part");
			}
			return this.part;
		}
	}

	private static final Map<UUID, Tail> TAILS = new HashMap<>();

	static {
		for (int i = 0; i < SKINS; i++) {
			CIVILIANS[i] = JaymosPowers.id("textures/entity/future/civilian_" + i + ".png");
			HUSKS[i] = JaymosPowers.id("textures/entity/future/husk_" + i + ".png");
		}
	}

	private FutureDeadLooks() {
	}

	// ---------------------------------------------------------------- skins

	/** From the zombie renderers' extractRenderState: a grown zombie or husk in the future gets its skin. */
	public static void extractSkin(LivingEntity zombie, LivingEntityRenderState state) {
		if (zombie.isBaby() || zombie.level().dimension() != Future.FUTURE) {
			return;
		}
		int skin = Math.floorMod(zombie.getUUID().hashCode(), SKINS);
		EntityType<?> type = zombie.getType();
		if (type == EntityTypes.ZOMBIE) {
			state.setData(SKIN, CIVILIANS[skin]);
		} else if (type == EntityTypes.HUSK) {
			state.setData(SKIN, HUSKS[skin]);
		}
	}

	/** Its skin, if it's one of the future's dead. */
	public static @Nullable Identifier skin(LivingEntityRenderState state) {
		return state.getData(SKIN);
	}

	// ---------------------------------------------------------------- tails

	/** Draw the tails on zombies, husks and Kitchen's zombie players. */
	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (dead(entityType) && renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
	}

	private static boolean dead(EntityType<?> type) {
		return type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || ZOMBIE_PLAYER.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent)renderer);
	}

	/** From every living renderer's extractRenderState: a grown zombie, husk or stranger's zombie player in the future gets its tail. */
	public static void extractTail(LivingEntity entity, LivingEntityRenderState state) {
		if (entity.level().dimension() != Future.FUTURE || entity.isBaby()) {
			return;
		}
		EntityType<?> type = entity.getType();
		boolean husk = type == EntityTypes.HUSK;
		if (husk || type == EntityTypes.ZOMBIE || Kitchen.LOADED && Kitchen.stranger(entity)) {
			state.setData(TAIL, tail(entity, husk));
		}
	}

	static Tail tail(Entity entity, boolean dusty) {
		Tail known = TAILS.get(entity.getUUID());
		if (known != null && known.dusty() == dusty) {
			return known;
		}
		if (TAILS.size() > CACHE_MAX) {
			TAILS.clear();
		}
		Tail tail = roll(entity.getUUID(), dusty);
		TAILS.put(entity.getUUID(), tail);
		return tail;
	}

	private static Tail roll(UUID id, boolean dusty) {
		RandomSource r = RandomSource.create(id.getMostSignificantBits() * 31L ^ id.getLeastSignificantBits() ^ 0xDEADL);
		Condition condition = Condition.values()[r.nextInt(Condition.values().length)];
		int segments = SEGMENTS_MIN + r.nextInt(SEGMENTS_MAX - SEGMENTS_MIN + 1);
		int at = 1 + r.nextInt(segments - 2);
		switch (condition) {
			case HALF_CUT -> segments = Math.max(2, segments / 2);
			case ROOT_STUMP -> segments = 1;
			case RAGGED -> segments = Math.max(3, segments * 3 / 5);
			case CHARRED -> segments -= 2;
			case BY_A_THREAD -> at = 1;
			default -> {
			}
		}
		int shade = FUR[r.nextInt(FUR.length)];
		int fur = ARGB.color(255, jitter(ARGB.red(shade), r), jitter(ARGB.green(shade), r), jitter(ARGB.blue(shade), r));
		if (dusty) {
			fur = ARGB.multiply(fur, DUSTY);
		}
		return new Tail(condition, segments, Mth.randomBetween(r, LENGTH_MIN, LENGTH_MAX), Mth.randomBetween(r, THICK_MIN, THICK_MAX),
			Mth.randomBetween(r, DROOP_MIN, DROOP_MAX), Mth.randomBetween(r, CURL_MIN, CURL_MAX), r.nextFloat() * Mth.TWO_PI, fur, at,
			r.nextBoolean() ? 1.0F : -1.0F, dusty);
	}

	private static int jitter(int channel, RandomSource random) {
		return Mth.clamp(channel + random.nextIntBetweenInclusive(-FUR_JITTER, FUR_JITTER), 0, 255);
	}

	/** Kitchen's zombie players, only if Kitchen is loaded (its classes are touched nowhere else). */
	private static final class Kitchen {
		static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

		/** A stranger's zombie player (the future's horde: FutureKitchen gives them no name), not a dead player who got up. */
		static boolean stranger(Entity entity) {
			return entity instanceof com.jaymoskitchen.corpse.ZombiePlayer zombie && zombie.ownerName().isEmpty();
		}
	}

	static final class Layer<S extends LivingEntityRenderState> extends RenderLayer<S, EntityModel<S>> {
		Layer(RenderLayerParent<S, EntityModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Tail tail = state.getData(TAIL);
			if (tail == null || state.isInvisible || !(this.getParentModel() instanceof HumanoidModel<?> model)) {
				return;
			}
			poseStack.pushPose();
			model.body.translateAndRotate(poseStack);
			// from the small of the back (the body's box is -4..4 x, 0..12 y, -2..2 z; +z is behind)
			poseStack.translate(0.0F, 10.5F / 16.0F, 1.6F / 16.0F);
			draw(poseStack, collector, lightCoords, LivingEntityRenderer.getOverlayCoords(state, 0.0F), tail, state.ageInTicks,
				Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F), state.walkAnimationPos);
			poseStack.popPose();
		}
	}

	/**
	 * The chain, segment by segment. Each rotates from the last one's end: pitch (+ up, - down) then yaw. {@code bent} adds up
	 * the pitch so far, so a part that hangs can point straight down whatever came before it.
	 */
	static void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, Tail tail, float time, float run, float walkPos) {
		Condition c = tail.condition();
		int n = tail.segments(), at = tail.at();
		float bent = 0.0F;
		float swing = HANG_SWING * Mth.sin(time * HANG_SPEED + tail.phase());
		for (int i = 0; i < n; i++) {
			boolean last = i == n - 1;
			Part part = Part.FUR;
			int tint = tail.fur();
			float sx = tail.thickness(), sy = tail.thickness(), length = tail.length();
			float pitch = (i == 0 ? tail.droop() - 0.15F * run : tail.curl()) + 0.03F * Mth.sin(time * 0.05F + i);
			float wander = 0.6F * Mth.sin(time * SWAY_SPEED + tail.phase() - i * 0.5F) + 0.4F * Mth.sin(time * SWAY_SPEED * 0.4F + tail.phase() * 1.7F - i * 0.3F);
			float yaw = SWAY * c.sway * wander + WALK_SWING * c.sway * run * Mth.sin(walkPos * 0.6662F - i * 0.5F);
			switch (c) {
				case BENT -> {
					if (i == at) {
						yaw += tail.side() * BEND;
					}
				}
				case SNAPPED -> {
					if (i == at) {
						pitch = -Mth.HALF_PI - bent + swing;
						yaw = 0.3F * swing * tail.side();
					} else if (i > at) {
						pitch = 0.04F;
						yaw = 0.0F;
					}
				}
				case HALF_CUT, RAGGED -> {
					if (last) {
						part = Part.CUT;
					}
				}
				case ROOT_STUMP -> {
					part = Part.CUT;
					length *= STUB;
				}
				case LIMP -> pitch = i == 0 ? LIMP_DROOP : 0.04F;
				case ZIGZAG -> {
					if (i == at) {
						pitch += ZIG;
						yaw += 0.4F * tail.side();
					} else if (i == at + 1) {
						pitch -= ZAG;
					}
				}
				case MANGY -> {
					if (i > 0) {
						part = (i + at) % 3 == 0 ? Part.FLESH : Part.MANGY;
					}
				}
				case BONE_TIP -> {
					if (i >= n / 2) {
						part = Part.BONE;
						tint = -1;
						sx *= BONE_THIN;
						sy *= BONE_THIN;
					}
				}
				case CHARRED -> {
					part = Part.CHAR;
					tint = -1;
				}
				case CRUSHED -> {
					if (i >= at) {
						sx *= FLAT_WIDE;
						sy *= FLAT_THIN;
					}
					if (i == at) {
						pitch += 0.5F;
					}
				}
				case KNOTTED -> pitch = i == 0 ? tail.droop() * 0.6F : KNOT_CURL;
				case STIFF -> {
					pitch = i == 0 ? STIFF_DROOP : 0.0F;
					yaw = 0.0F;
				}
				case BY_A_THREAD -> {
					if (i == at) {
						part = Part.STRAND;
						tint = -1;
						pitch = 0.1F;
					} else if (i == at + 1) {
						pitch = -Mth.HALF_PI - bent + swing;
						yaw = 0.0F;
					} else if (i > at + 1) {
						pitch = 0.04F;
						yaw = 0.0F;
					}
				}
				case SPLINTERED -> {
					if (i == at) {
						pitch += SPLINTER_BEND;
					}
				}
				default -> {
				}
			}
			bent += pitch;
			poseStack.rotate(Axis.XP, pitch);
			poseStack.rotate(Axis.YP, yaw);
			if (c == Condition.FRAYED && last) {
				for (int side = -1; side <= 1; side += 2) {
					poseStack.pushPose();
					poseStack.rotate(Axis.YP, side * FRAY);
					submit(poseStack, collector, Part.MANGY, light, overlay, tint, sx * FRAY_THIN, sy * FRAY_THIN, length);
					poseStack.popPose();
				}
			} else {
				submit(poseStack, collector, part, light, overlay, tint, sx, sy, length);
			}
			if (c == Condition.RAGGED && i == n - 2) {
				poseStack.pushPose();
				poseStack.translate(0.0F, 0.0F, length * 0.3F / 16.0F);
				poseStack.scale(tail.thickness(), tail.thickness(), 1.0F);
				collector.submitModelPart(Part.RAG.part(), poseStack, RenderTypes.entityCutout(TAIL_TEXTURE), light, overlay, null, -1);
				poseStack.popPose();
			}
			if (c == Condition.SPLINTERED && i == at) {
				// the bone through the fur at the break, pointing back the way the tail came
				poseStack.pushPose();
				poseStack.rotate(Axis.XP, SPLINTER_OUT);
				collector.submitModelPart(Part.SPLINTER.part(), poseStack, RenderTypes.entityCutout(TAIL_TEXTURE), light, overlay, null, -1);
				poseStack.popPose();
			}
			poseStack.translate(0.0F, 0.0F, length / 16.0F);
			poseStack.scale(TAPER, TAPER, TAPER);
		}
	}

	/** One segment's box, stretched to its length (see {@link #STEP}) and thickness. */
	private static void submit(PoseStack poseStack, SubmitNodeCollector collector, Part part, int light, int overlay, int tint, float sx, float sy,
		float length) {
		poseStack.pushPose();
		poseStack.scale(sx, sy, length / STEP);
		collector.submitModelPart(part.part(), poseStack, RenderTypes.entityCutout(TAIL_TEXTURE), light, overlay, null, tint);
		poseStack.popPose();
	}
}
