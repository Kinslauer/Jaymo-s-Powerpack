package com.jaymospowers.client.power.darktide;

import com.jaymospowers.power.darktide.DarktideMove;
import com.jaymospowers.power.darktide.DarktideState;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.jaymospowers.power.darktide.DarktideWeapons;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Darktide drawn on bodies (any humanoid model: players, and the zombies or illagers that reel from a riposte): the
 * guard (a fist guard unarmed, chūdan with a katana, both knives up), the four kendo kamae of Gedan (gedan, chūdan,
 * jōdan, hassō; a parry steps from one to the next and the arms travel there), the riposte's slash from the shoulder
 * across the body, Dark Strike's charge (a blade raised overhead, or a fist drawn back) and its blow, Crescent's sweep,
 * Ink Cross, the knife moves, the cast, reeling and the stagger. Everything is set
 * outright from the move's progress, after the model's own animation.
 */
public final class DarktidePose {
	record Pose(int kind, float p, float ticks, int variant, int guardKit, boolean guarding, float stanceBlend, int fromStance) {
	}

	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:darktide_pose");
	static final RenderStateDataKey<Boolean> RISEN = RenderStateDataKey.create(() -> "jaymospowers:darktide_risen");
	/** How long the arms take from one kamae to the next. */
	static final float STANCE_BLEND_TICKS = 4.0F;
	/** A risen shadow's tint (ARGB). */
	public static final int RISEN_TINT = 0xFF262C3C;

	/** entity id → {last kamae, the one before, game time it changed}. */
	private static final Map<Integer, long[]> STANCES = new HashMap<>();

	/** A crippled humanoid mob face down on the ground: how far down (0 standing, 1 flat) and its crawl's stride. */
	record Crawl(float down, float stride, float speed) {
	}

	static final RenderStateDataKey<Crawl> CRAWL = RenderStateDataKey.create(() -> "jaymospowers:darktide_crawl");
	/** Ticks to drop to the ground, and to get back up once the cripple wears off. */
	static final float CRAWL_DROP = 5.0F, CRAWL_GET_UP = 10.0F;
	/** How high the chest rides off the ground when flat (model units, blocks), so it doesn't sink in. */
	static final float CRAWL_LIFT = 0.18F;
	/** entity id → {how far down when last drawn, game time it was last crippled}. */
	private static final Map<Integer, float[]> CRAWLS = new HashMap<>();

	private DarktidePose() {
	}

	/**
	 * A crippled humanoid mob drops face down and drags itself along (players use the vanilla crawl instead): how far
	 * down it is, easing down when the cripple lands and back up when it wears off.
	 */
	public static void extractCrawl(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		boolean crippled = !(entity instanceof Player) && DarktideStatus.crippled(entity) && DarktideStatus.humanoid(entity);
		float[] track = CRAWLS.get(entity.getId());
		if (!crippled && track == null) {
			state.setData(CRAWL, null);
			return;
		}
		float now = entity.level().getGameTime() + partialTicks;
		if (track == null) {
			track = new float[] {0.0F, now, now};
			CRAWLS.put(entity.getId(), track);
		}
		float dt = Math.max(0.0F, now - track[2]);
		track[2] = now;
		if (crippled) {
			track[0] = Math.min(1.0F, track[0] + dt / CRAWL_DROP);
			track[1] = now;
		} else {
			track[0] = Math.max(0.0F, track[0] - dt / CRAWL_GET_UP);
		}
		if (track[0] <= 0.0F && !crippled) {
			CRAWLS.remove(entity.getId());
			state.setData(CRAWL, null);
			return;
		}
		state.setData(CRAWL, new Crawl(smooth(track[0]), state.walkAnimationPos, state.walkAnimationSpeed));
	}

	/** Lays a crawling mob's body on the ground: tipped forward about its feet, centred over where it stands. */
	public static void rotateCrawl(LivingEntityRenderState state, PoseStack poseStack) {
		Crawl crawl = state.getData(CRAWL);
		if (crawl == null || state.deathTime > 0.0F) {
			return;
		}
		float height = state.boundingBoxHeight / Math.max(0.01F, state.scale);
		float d = crawl.down();
		poseStack.rotateDegrees(Axis.XP, -90.0F * d);
		poseStack.translate(0.0F, -height * 0.5F * d, CRAWL_LIFT * d);
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		extractCrawl(entity, state, partialTicks);
		// risen shadows and a user walking as their own shade are drawn the same dark
		state.setData(RISEN, entity.getAttached(DarktideState.RISEN) != null || entity.getAttached(DarktideState.SHADE) != null ? Boolean.TRUE : null);
		DarktideState.Move move = entity.getAttached(DarktideState.MOVE);
		boolean guarding = entity.getAttached(DarktideState.GUARD) != null;
		if (move == null && !guarding) {
			state.setData(KEY, null);
			return;
		}
		int kit = 0;
		if (entity instanceof Player player) {
			kit = DarktideWeapons.kit(player).ordinal();
		}
		if (move != null) {
			float now = DarktideFx.animTime(entity, partialTicks);
			float ticks = now - move.start();
			if (ticks >= 0.0F && ticks < move.duration()) {
				float blend = 1.0F;
				int from = move.variant() & 3;
				if (move.kind() == DarktideMove.STANCE) {
					long[] seen = STANCES.computeIfAbsent(entity.getId(), id -> new long[] {move.variant() & 3, move.variant() & 3, 0L});
					int stance = move.variant() & 3;
					if (seen[0] != stance) {
						seen[1] = seen[0];
						seen[0] = stance;
						seen[2] = entity.level().getGameTime();
					}
					from = (int) seen[1];
					blend = Mth.clamp((entity.level().getGameTime() + partialTicks - seen[2]) / STANCE_BLEND_TICKS, 0.0F, 1.0F);
				}
				state.setData(KEY, new Pose(move.kind(), ticks / move.duration(), ticks, move.variant(), kit, guarding, blend, from));
				return;
			}
		}
		STANCES.remove(entity.getId());
		state.setData(KEY, guarding ? new Pose(0, 0.0F, 0.0F, 0, kit, true, 1.0F, 0) : null);
	}

	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = living.getData(KEY);
		if (pose != null) {
			applyPose(h, pose);
		}
		Crawl crawl = living.getData(CRAWL);
		if (crawl != null && living.deathTime <= 0.0F) {
			crawl(h, crawl);
		}
	}

	/**
	 * Face down, dragging itself: the arms reach out past the head and pull in turn, the legs trail behind with a weak
	 * kick, the head lifted to look ahead. Blended in by how far down the body is.
	 */
	private static void crawl(HumanoidModel<?> h, Crawl crawl) {
		float d = crawl.down();
		float pull = Mth.sin(crawl.stride() * 0.6662F) * Math.min(1.0F, 0.35F + crawl.speed() * 2.0F);
		set(h.rightArm, -2.75F + 0.45F * pull, -0.15F, 0.25F, d);
		set(h.leftArm, -2.75F - 0.45F * pull, 0.15F, -0.25F, d);
		set(h.rightLeg, 0.1F * pull, 0.0F, 0.12F, d);
		set(h.leftLeg, -0.1F * pull, 0.0F, -0.12F, d);
		h.body.xRot = Mth.lerp(d, h.body.xRot, 0.0F);
		h.body.yRot = Mth.lerp(d, h.body.yRot, 0.08F * pull);
		h.head.xRot = Mth.lerp(d, h.head.xRot, -1.1F);
	}

	private static void applyPose(HumanoidModel<?> h, Pose pose) {
		if (pose.kind == 0) {
			guard(h, pose.guardKit);
			return;
		}
		float p = pose.p;
		float e = p < 0.3F ? p / 0.3F : 1.0F - (p - 0.3F) / 0.7F;
		e = smooth(Mth.clamp(e, 0.0F, 1.0F));
		switch (pose.kind) {
			case DarktideMove.STRIKE -> {
				if ((pose.variant & 2) != 0) {
					// the blade brought down from overhead, both hands on it
					float cut = smooth(seg(p, 0.0F, 0.35F));
					float hold = 1.0F - seg(p, 0.6F, 1.0F);
					set(h.rightArm, Mth.lerp(cut, -2.95F, -0.7F), -0.15F, 0.0F, hold);
					set(h.leftArm, Mth.lerp(cut, -2.85F, -0.75F), 0.35F, 0.0F, hold);
					h.body.xRot = 0.25F * cut * hold;
					set(h.rightLeg, -0.4F, 0.0F, 0.0F, hold);
					set(h.leftLeg, 0.35F, 0.0F, 0.0F, hold);
					break;
				}
				set(h.rightArm, -Mth.HALF_PI - 0.1F, -0.1F, 0.0F, e);
				set(h.leftArm, -1.2F, 0.4F, 0.2F, Math.max(e, 0.5F));
				h.body.yRot = -0.4F * e;
				set(h.rightLeg, -0.35F, 0.0F, 0.0F, e);
				set(h.leftLeg, 0.3F, 0.0F, 0.0F, e);
			}
			case DarktideMove.RIPOSTE -> {
				// from over the right shoulder, down across the body to the left hip, both hands on the hilt
				float cut = smooth(seg(p, 0.0F, 0.35F));
				float hold = 1.0F - seg(p, 0.7F, 1.0F);
				set(h.rightArm, Mth.lerp(cut, -2.8F, -0.55F), Mth.lerp(cut, -0.3F, 0.75F), Mth.lerp(cut, 0.25F, 0.0F), hold);
				set(h.leftArm, Mth.lerp(cut, -2.6F, -0.65F), Mth.lerp(cut, 0.5F, 1.0F), 0.0F, hold);
				h.body.yRot = Mth.lerp(cut, -0.35F, 0.55F) * hold;
				h.body.xRot = 0.2F * cut * hold;
				set(h.rightLeg, -0.45F, 0.0F, 0.0F, hold);
				set(h.leftLeg, 0.4F, 0.0F, 0.0F, hold);
			}
			case DarktideMove.CHARGE -> {
				float up = smooth(seg(pose.ticks, 0.0F, 5.0F));
				float c = Mth.clamp(pose.ticks / DarktideMove.CHARGE_FULL_TICKS, 0.0F, 1.0F);
				// at full charge the arms shake with it
				float shake = c >= 1.0F ? 0.03F * Mth.sin(pose.ticks * 2.5F) : 0.0F;
				if (pose.variant == DarktideMove.CHARGE_FIST) {
					// the right fist drawn back by the hip, the left held out before you, the body wound up
					set(h.rightArm, 0.55F + 0.35F * c + shake, 0.25F, 0.25F, up);
					set(h.leftArm, -1.3F, 0.45F, 0.0F, up);
					h.body.yRot = 0.5F * c * up;
					set(h.rightLeg, 0.35F, 0.0F, 0.0F, up);
					set(h.leftLeg, -0.35F, 0.0F, 0.0F, up);
				} else {
					// the blade raised high overhead in both hands, leaning back into it as the charge builds
					set(h.rightArm, -2.85F - 0.25F * c + shake, -0.15F, 0.1F, up);
					set(h.leftArm, -2.75F - 0.25F * c + shake, 0.35F, -0.1F, up);
					h.body.xRot = -0.12F * c * up;
					set(h.rightLeg, -0.3F, 0.0F, 0.0F, up);
					set(h.leftLeg, 0.3F, 0.0F, 0.0F, up);
				}
			}
			case DarktideMove.CRESCENT -> {
				float sweep = smooth(seg(p, 0.05F, 0.6F));
				float hold = 1.0F - seg(p, 0.75F, 1.0F);
				set(h.rightArm, -1.45F, Mth.lerp(sweep, -1.2F, 1.1F), 0.0F, hold);
				set(h.leftArm, -1.35F, Mth.lerp(sweep, -0.8F, 1.4F), 0.0F, hold);
				h.body.yRot = Mth.lerp(sweep, -0.7F, 0.7F) * hold;
				set(h.rightLeg, -0.3F, 0.0F, 0.0F, hold);
				set(h.leftLeg, 0.3F, 0.0F, 0.0F, hold);
			}
			case DarktideMove.INK_CROSS -> {
				float cut = smooth(seg(p, 0.0F, 0.45F));
				float hold = 1.0F - seg(p, 0.7F, 1.0F);
				set(h.rightArm, Mth.lerp(cut, -2.5F, -0.6F), Mth.lerp(cut, -0.4F, 0.9F), 0.0F, hold);
				set(h.leftArm, Mth.lerp(cut, -2.5F, -0.6F), Mth.lerp(cut, 0.4F, -0.9F), 0.0F, hold);
				h.body.xRot = 0.15F * cut * hold;
			}
			case DarktideMove.STANCE -> {
				stance(h, pose.fromStance, 1.0F);
				stance(h, pose.variant & 3, pose.stanceBlend);
			}
			case DarktideMove.STILLWATER, DarktideMove.SINK -> {
				// sinking: crouched low, blade (or hands) held down
				h.body.xRot = 0.55F;
				set(h.rightArm, -0.35F, -0.3F, 0.0F, 1.0F);
				set(h.leftArm, -0.35F, 0.45F, 0.0F, 1.0F);
				set(h.rightLeg, -0.7F, 0.0F, 0.1F, 1.0F);
				set(h.leftLeg, -0.7F, 0.0F, -0.1F, 1.0F);
				h.head.xRot = 0.3F;
			}
			case DarktideMove.POUNCE_AIM -> {
				// both knives raised to stab, the body coiled forward
				float up = smooth(seg(pose.ticks, 0.0F, 4.0F));
				set(h.rightArm, -2.75F, -0.15F, 0.35F, up);
				set(h.leftArm, -2.75F, 0.15F, -0.35F, up);
				h.body.xRot = 0.3F * up;
				set(h.rightLeg, -0.5F, 0.0F, 0.0F, up);
				set(h.leftLeg, 0.45F, 0.0F, 0.0F, up);
			}
			case DarktideMove.POUNCE_STAB -> {
				int stabs = Math.max(1, pose.variant);
				float beat = p * stabs;
				float q = beat - (int) Math.min(stabs - 1, beat);
				float down = smooth(seg(q, 0.0F, 0.4F)) * (1.0F - seg(q, 0.7F, 1.0F) * (beat < stabs - 1 ? 1.0F : 0.0F));
				set(h.rightArm, Mth.lerp(down, -2.75F, -0.5F), -0.1F, 0.2F, 1.0F);
				set(h.leftArm, Mth.lerp(down, -2.75F, -0.5F), 0.1F, -0.2F, 1.0F);
				h.body.xRot = Mth.lerp(down, 0.1F, 0.6F);
				set(h.rightLeg, -1.2F, 0.0F, 0.2F, 1.0F);
				set(h.leftLeg, -1.2F, 0.0F, -0.2F, 1.0F);
			}
			case DarktideMove.BARRAGE, DarktideMove.FLURRY -> {
				// slashes as fast as the eye can follow, knives alternating
				float rate = pose.kind == DarktideMove.BARRAGE ? 0.5F : 0.5F;
				float beat = pose.ticks * rate;
				int n = (int) beat;
				float q = beat - n;
				int s = n % 2 == 0 ? 1 : -1;
				ModelPart cutting = s > 0 ? h.rightArm : h.leftArm;
				ModelPart drawn = s > 0 ? h.leftArm : h.rightArm;
				set(cutting, Mth.lerp(q, -2.3F, -0.8F), Mth.lerp(q, -s * 0.6F, s * 0.9F), 0.0F, 1.0F);
				set(drawn, -1.4F, s * 0.4F, 0.0F, 1.0F);
				h.body.yRot = s * Mth.lerp(q, -0.3F, 0.3F);
				set(h.rightLeg, -0.3F, 0.0F, 0.0F, 1.0F);
				set(h.leftLeg, 0.3F, 0.0F, 0.0F, 1.0F);
			}
			case DarktideMove.DASH -> {
				// low and forward, blades trailing behind
				h.body.xRot = 0.55F;
				set(h.rightArm, 0.9F, 0.0F, 0.25F, 1.0F);
				set(h.leftArm, 0.9F, 0.0F, -0.25F, 1.0F);
				set(h.rightLeg, -0.8F, 0.0F, 0.0F, 1.0F);
				set(h.leftLeg, 0.6F, 0.0F, 0.0F, 1.0F);
			}
			case DarktideMove.CAST -> {
				// the hand thrown out (Black Tide, Drown, Tide Arms), or held out palm-up (Necromancy, a rise)
				boolean held = pose.variant == 1;
				float out = held ? smooth(seg(pose.ticks, 0.0F, 4.0F)) : e;
				set(h.rightArm, held ? -1.25F : -1.65F, -0.1F, held ? 0.0F : -0.1F, out);
				if (pose.variant == 0) {
					set(h.leftArm, -1.55F, 0.15F, 0.1F, out);
				}
				h.body.xRot = held ? 0.0F : 0.1F * out;
			}
			case DarktideMove.REEL -> {
				// thrown back by the blow: leaning away, arms flung out
				float r = 1.0F - smooth(seg(p, 0.25F, 1.0F));
				h.body.xRot = -0.4F * r;
				h.head.xRot = Mth.lerp(r, h.head.xRot, -0.5F);
				set(h.rightArm, -0.6F, 0.0F, 0.9F, r);
				set(h.leftArm, -0.6F, 0.0F, -0.9F, r);
				set(h.rightLeg, 0.35F, 0.0F, 0.0F, r);
				set(h.leftLeg, -0.25F, 0.0F, 0.0F, r);
			}
			case DarktideMove.STAGGER -> {
				float r = 1.0F - smooth(seg(p, 0.5F, 1.0F));
				h.body.xRot = -0.25F * r;
				set(h.rightArm, -0.2F, 0.0F, 0.7F, r);
				set(h.leftArm, -0.2F, 0.0F, -0.7F, r);
				h.head.xRot = Mth.lerp(r, h.head.xRot, 0.35F);
			}
			default -> {
			}
		}
	}

	/** The guard: unarmed, forearms up before the face; a katana, chūdan; knives, both blades raised before the chest. */
	private static void guard(HumanoidModel<?> h, int kit) {
		switch (kit) {
			case 1 -> stance(h, DarktideMove.CHUDAN, 1.0F);
			case 2 -> {
				set(h.rightArm, -1.7F, -0.35F, 0.0F, 1.0F);
				set(h.leftArm, -1.7F, 0.35F, 0.0F, 1.0F);
			}
			default -> {
				set(h.rightArm, -2.0F, -0.7F, 0.0F, 1.0F);
				set(h.leftArm, -1.45F, 0.45F, 0.0F, 1.0F);
				h.body.xRot = 0.1F;
			}
		}
	}

	/** The four kamae with a two-handed grip (the left hand reaches across to the hilt below the right). */
	private static void stance(HumanoidModel<?> h, int kamae, float amount) {
		switch (kamae) {
			case DarktideMove.GEDAN -> {
				// blade low, tip toward the opponent's knees
				set(h.rightArm, -0.45F, -0.25F, 0.0F, amount);
				set(h.leftArm, -0.5F, 0.55F, 0.0F, amount);
				h.body.xRot = Mth.lerp(amount, h.body.xRot, 0.12F);
				set(h.rightLeg, -0.25F, 0.0F, 0.0F, amount);
				set(h.leftLeg, 0.25F, 0.0F, 0.0F, amount);
			}
			case DarktideMove.CHUDAN -> {
				// the middle guard: tip at the throat
				set(h.rightArm, -1.1F, -0.3F, 0.0F, amount);
				set(h.leftArm, -1.0F, 0.65F, 0.0F, amount);
				h.body.xRot = Mth.lerp(amount, h.body.xRot, 0.05F);
				set(h.rightLeg, -0.2F, 0.0F, 0.0F, amount);
				set(h.leftLeg, 0.2F, 0.0F, 0.0F, amount);
			}
			case DarktideMove.JODAN -> {
				// overhead, ready to cut down
				set(h.rightArm, -2.85F, -0.2F, 0.1F, amount);
				set(h.leftArm, -2.75F, 0.35F, -0.1F, amount);
				h.body.xRot = Mth.lerp(amount, h.body.xRot, -0.05F);
				set(h.rightLeg, -0.3F, 0.0F, 0.0F, amount);
				set(h.leftLeg, 0.25F, 0.0F, 0.0F, amount);
			}
			default -> {
				// hassō: hilt by the right shoulder, the blade standing upright
				set(h.rightArm, -2.1F, -0.2F, 0.35F, amount);
				set(h.leftArm, -1.85F, 0.75F, 0.0F, amount);
				h.body.yRot = Mth.lerp(amount, h.body.yRot, 0.25F);
				set(h.rightLeg, 0.2F, 0.0F, 0.0F, amount);
				set(h.leftLeg, -0.3F, 0.0F, 0.0F, amount);
			}
		}
	}

	/** A risen shadow (or a shade) is drawn dark. */
	public static int tint(LivingEntityRenderState state, int tint) {
		return state.getData(RISEN) != null ? RISEN_TINT : tint;
	}

	private static float seg(float p, float a, float b) {
		return Mth.clamp((p - a) / (b - a), 0.0F, 1.0F);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}

	private static void set(ModelPart part, float x, float y, float z, float amount) {
		part.xRot = Mth.lerp(amount, part.xRot, x);
		part.yRot = Mth.lerp(amount, part.yRot, y);
		part.zRot = Mth.lerp(amount, part.zRot, z);
	}

	static @Nullable Pose of(LivingEntityRenderState state) {
		return state.getData(KEY);
	}
}
