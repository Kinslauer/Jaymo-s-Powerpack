package com.jaymospowers.client;

import com.jaymospowers.core.HitReactions;
import com.jaymospowers.core.MobHurtStances;
import com.jaymospowers.core.wounds.BodyPart;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How hits land on bodies, drawn ({@link HitReactions}, {@link MobHurtStances}), on every humanoid and four-legged
 * model (found by their part names), mobs and MCA's villagers included:
 * <ul>
 * <li><b>The flinch:</b> the part that was struck snaps away from the blow and settles back over
 * {@link #LENGTH_MS}, by the blow's strength; the body leans to the side it came from. On the local player it also
 * gives the screen a small kick ({@link #SELF_SHAKE}). Scaled by {@link MotionSettings#reactionNow}; with Reduced
 * motion the screen kick is gone.</li>
 * <li><b>The hurt stances</b> (not for players, who have {@link com.jaymospowers.client.wounds.WoundNursePose}): a body
 * under half hunches, a head under half hangs and sways, a hurt leg limps (dragged, the body tipping off it), a hurt arm
 * hangs limp. Eased in and out with {@link PoseBlend}.</li>
 * </ul>
 * Not on a body one of our other poses has (downed, falling, carried...).
 */
public final class HitReactionsClient {
	/** The flinch: ms it takes to reach full, and the ms it takes to fade to a fifth after; and its whole length (ms). */
	static final long ATTACK_MS = 60L, DECAY_MS = 160L, LENGTH_MS = 520L;
	/** The flinch's angles (radians) at full strength: the part itself, and the lean of the body toward the blow's side. */
	static final float PART_SNAP = 0.75F, BODY_BEND = 0.4F, LEAN = 0.16F, TWIST = 0.14F;
	/** The screen kick (degrees at full strength) for the local player's own flinch. */
	static final float SELF_SHAKE = 1.1F;
	/** The stances' angles (radians): the hunch, the head's droop and sway (its size and its rate), the limp's drag and tip, an arm's hang. */
	static final float HUNCH = 0.32F, DROOP = 0.38F, SWAY = 0.16F, SWAY_RATE = 1.7F, DRAG = 0.65F, TIP = 0.12F, HANG = 0.18F;
	/** How much of the weight track the stance takes at its fullest. */
	static final float STANCE = 1.0F;

	private record Hit(BodyPart part, float strength, int side, long start) {
	}

	/** The parts of a model that the poses move, by what the model has. */
	private record Rig(@Nullable ModelPart head, @Nullable ModelPart body, @Nullable ModelPart rightArm, @Nullable ModelPart leftArm,
		@Nullable ModelPart rightLeg, @Nullable ModelPart leftLeg, ModelPart[] front, ModelPart[] hind) {
	}

	private static final ModelPart[] NONE = new ModelPart[0];
	private static final PoseBlend.Channel STANCES = PoseBlend.channel("hurt_stance");
	private static final Map<Integer, Hit> HITS = new HashMap<>();
	private static final Map<Model<?>, Rig> RIGS = new WeakHashMap<>();

	private HitReactionsClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(HitReactions.Flinch.TYPE, (payload, context) -> flinch(context.client(), payload));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			long now = Util.getMillis();
			for (Iterator<Hit> it = HITS.values().iterator(); it.hasNext();) {
				if (now - it.next().start() >= LENGTH_MS) {
					it.remove();
				}
			}
		});
	}

	private static void flinch(Minecraft minecraft, HitReactions.Flinch flinch) {
		BodyPart[] parts = BodyPart.values();
		if (minecraft.player == null || flinch.part() < 0 || flinch.part() >= parts.length) {
			return;
		}
		float scale = MotionSettings.reactionNow();
		if (scale <= 0.0F) {
			return;
		}
		HITS.put(flinch.entity(), new Hit(parts[flinch.part()], Math.min(1.0F, flinch.strength() * scale), flinch.side(), Util.getMillis()));
		if (flinch.entity() == minecraft.player.getId() && !MotionSettings.reduced()) {
			ScreenShake.add(SELF_SHAKE * flinch.strength(), 6, null);
		}
	}

	/** The flinch's size now, 0 to 1: a quick rise, then an easing away. */
	private static float envelope(long age) {
		if (age < 0L || age >= LENGTH_MS) {
			return 0.0F;
		}
		if (age < ATTACK_MS) {
			return age / (float)ATTACK_MS;
		}
		return (float)Math.exp(-(age - ATTACK_MS) * 1.6F / DECAY_MS) * (1.0F - (age - ATTACK_MS) / (float)(LENGTH_MS - ATTACK_MS));
	}

	/** Right after a model animates: the flinch of a part just struck, and the stance of a body that is hurt. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || living.deathTime > 0.0F) {
			return;
		}
		Integer id = living instanceof AvatarRenderState avatar ? Integer.valueOf(avatar.id) : living.getData(PoseTakeover.MOB_ID);
		if (id == null) {
			return;
		}
		boolean posed = living instanceof AvatarRenderState avatar ? PoseTakeover.posedOtherwise(avatar) : PoseTakeover.mobPosed(living);
		if (posed) {
			return;
		}
		Hit hit = HITS.get(id);
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(id);
		int hurt = entity instanceof Player || entity == null ? 0 : MobHurtStances.hurt((net.minecraft.world.entity.LivingEntity)entity);
		PoseBlend.Track track = STANCES.track(id, hurt != 0 && !MotionSettings.reduced());
		if (hit == null && track == null) {
			return;
		}
		Rig rig = RIGS.computeIfAbsent(model, HitReactionsClient::rig);
		if (rig.body() == null && rig.head() == null) {
			return;
		}
		if (track != null && hurt != 0) {
			stance(rig, hurt, track.weight() * STANCE, living.walkAnimationPos, living.walkAnimationSpeed, living.ageInTicks);
		}
		if (hit != null) {
			float s = hit.strength() * envelope(Util.getMillis() - hit.start());
			if (s > 0.0F) {
				flinch(rig, hit, s);
			}
		}
	}

	private static void flinch(Rig rig, Hit hit, float s) {
		float side = hit.side() == 1 ? 1.0F : hit.side() == 2 ? -1.0F : 0.0F;
		add(rig.body(), 0.0F, TWIST * side * s, LEAN * side * s);
		switch (hit.part()) {
			case HEAD -> add(rig.head(), -PART_SNAP * s, side * 0.3F * s, -side * 0.12F * s);
			case BODY -> {
				add(rig.body(), BODY_BEND * s, 0.0F, 0.0F);
				add(rig.head(), BODY_BEND * 0.5F * s, 0.0F, 0.0F);
				add(rig.rightArm(), -0.35F * s, 0.0F, 0.0F);
				add(rig.leftArm(), -0.35F * s, 0.0F, 0.0F);
			}
			case RIGHT_ARM -> add(rig.rightArm(), PART_SNAP * s, 0.0F, 0.35F * s);
			case LEFT_ARM -> add(rig.leftArm(), PART_SNAP * s, 0.0F, -0.35F * s);
			case RIGHT_LEG, RIGHT_FOOT -> add(rig.rightLeg(), -PART_SNAP * s, 0.0F, 0.0F);
			case LEFT_LEG, LEFT_FOOT -> add(rig.leftLeg(), -PART_SNAP * s, 0.0F, 0.0F);
			case FRONT_LEGS -> addAll(rig.front(), -PART_SNAP * s);
			case HIND_LEGS -> addAll(rig.hind(), PART_SNAP * s);
			default -> add(rig.body(), BODY_BEND * 0.5F * s, 0.0F, 0.0F);
		}
	}

	private static void stance(Rig rig, int hurt, float w, float walkPos, float walkSpeed, float age) {
		if (w <= 0.0F) {
			return;
		}
		boolean twoLegged = rig.front().length == 0;
		if ((hurt & MobHurtStances.bit(BodyPart.BODY)) != 0) {
			// hunched: forward at the body, the head low, the arms hanging in front
			add(rig.body(), HUNCH * w * (twoLegged ? 1.0F : 0.4F), 0.0F, 0.0F);
			add(rig.head(), HUNCH * 0.8F * w, 0.0F, 0.0F);
			add(rig.rightArm(), 0.2F * w, 0.0F, 0.0F);
			add(rig.leftArm(), 0.2F * w, 0.0F, 0.0F);
		}
		if ((hurt & MobHurtStances.bit(BodyPart.HEAD)) != 0) {
			// the head hangs and sways
			add(rig.head(), DROOP * w, 0.0F, Mth.sin(age * 0.05F * SWAY_RATE) * SWAY * w);
		}
		if ((hurt & MobHurtStances.bit(BodyPart.RIGHT_ARM)) != 0) {
			hang(rig.rightArm(), 1.0F, w);
		}
		if ((hurt & MobHurtStances.bit(BodyPart.LEFT_ARM)) != 0) {
			hang(rig.leftArm(), -1.0F, w);
		}
		float moving = Math.min(1.0F, walkSpeed * 3.0F);
		boolean right = (hurt & (MobHurtStances.bit(BodyPart.RIGHT_LEG) | MobHurtStances.bit(BodyPart.RIGHT_FOOT))) != 0;
		boolean left = (hurt & (MobHurtStances.bit(BodyPart.LEFT_LEG) | MobHurtStances.bit(BodyPart.LEFT_FOOT))) != 0;
		if (right || left) {
			// a limp: the bad leg dragged short, the body tipping onto the good one on each step
			ModelPart bad = right ? rig.rightLeg() : rig.leftLeg();
			if (bad != null) {
				bad.xRot *= 1.0F - DRAG * w;
			}
			add(rig.body(), 0.0F, 0.0F, (right ? -1.0F : 1.0F) * TIP * w * (0.5F + 0.5F * Mth.cos(walkPos * 0.6662F * 2.0F)) * moving);
		}
		if ((hurt & MobHurtStances.bit(BodyPart.FRONT_LEGS)) != 0) {
			drag(rig.front(), w);
		}
		if ((hurt & (MobHurtStances.bit(BodyPart.HIND_LEGS) | MobHurtStances.bit(BodyPart.LEGS))) != 0) {
			drag(rig.hind(), w);
		}
	}

	private static void hang(@Nullable ModelPart arm, float side, float w) {
		if (arm != null) {
			arm.xRot = Mth.lerp(w, arm.xRot, 0.1F);
			arm.zRot += HANG * side * w;
		}
	}

	private static void drag(ModelPart[] legs, float w) {
		for (ModelPart leg : legs) {
			leg.xRot *= 1.0F - DRAG * w;
		}
	}

	private static void add(@Nullable ModelPart part, float x, float y, float z) {
		if (part != null) {
			part.xRot += x;
			part.yRot += y;
			part.zRot += z;
		}
	}

	private static void addAll(ModelPart[] parts, float x) {
		for (ModelPart part : parts) {
			part.xRot += x;
		}
	}

	private static Rig rig(Model<?> model) {
		if (model instanceof HumanoidModel<?> h) {
			return new Rig(h.head, h.body, h.rightArm, h.leftArm, h.rightLeg, h.leftLeg, NONE, NONE);
		}
		ModelPart root = model.root();
		ModelPart[] front = found(root, "right_front_leg", "left_front_leg");
		ModelPart[] hind = found(root, "right_hind_leg", "left_hind_leg");
		return new Rig(child(root, "head"), child(root, "body"), child(root, "right_arm"), child(root, "left_arm"), child(root, "right_leg"),
			child(root, "left_leg"), front, hind);
	}

	private static @Nullable ModelPart child(ModelPart root, String name) {
		return root.hasChild(name) ? root.getChild(name) : null;
	}

	private static ModelPart[] found(ModelPart root, String... names) {
		int n = 0;
		ModelPart[] all = new ModelPart[names.length];
		for (String name : names) {
			if (root.hasChild(name)) {
				all[n++] = root.getChild(name);
			}
		}
		return n == 0 ? NONE : java.util.Arrays.copyOf(all, n);
	}
}
