package com.jaymospowers.client.pets;

import com.jaymospowers.client.PoseBlend;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (Motion patch 6) Pets that act like pets, after Actions &amp; Stuff's dogs and cats (ideas only):
 * <ul>
 * <li><b>Wolves:</b> a tamed wolf whose owner has been away a while greets them coming back, hopping with its tail going;
 * play-bows (chest down, rump and tail up) when its owner crouches close in front of it; left sitting a while it lies
 * down on its side, and later dozes off, eyes shut, waking at a loud sound nearby. A wild wolf standing about a long
 * time lies down and dozes too.</li>
 * <li><b>Cats:</b> a tamed cat left sitting settles into a loaf, paws tucked and tail wrapped round; loafed, now and then
 * it kneads or grooms a paw; getting up from a loaf it stretches.</li>
 * </ul>
 * Worked out from what everyone's client sees (sitting, the owner, movement, the game clock), and while one plays base
 * Fresh Animations steps aside for that animal ({@code PoseTakeover#mobPosed}).
 */
public final class PetPoses {
	// ===== the knobs ================================================================================================
	/** The owner away this long (ticks) before a greeting, greeted from this near (blocks), the greeting this long. */
	static final int AWAY_TICKS = 2400, GREET_TICKS = 70;
	static final double OWNER_NEAR = 16.0;
	/** A play bow: the owner crouching within this many blocks, in front of the wolf. */
	static final double BOW_RANGE = 3.5;
	/** Sitting (tamed) or standing still (wild) this long before lying down, and lying this much longer before dozing. */
	static final int LIE_AFTER = 600, WILD_LIE_AFTER = 900, DOZE_AFTER = 400;
	/** A sound at least this loud wakes dozing animals within this many blocks per unit of volume (at most WAKE_MAX). */
	static final float LOUD = 0.9F, WAKE_RANGE = 8.0F, WAKE_MAX = 24.0F;
	/** Woken, the head stays up this long (ticks). */
	static final int WAKE_TICKS = 60;
	/** A cat sitting this long settles into a loaf; its knead and groom cycle (ticks, plus a little per cat), each's length. */
	static final int LOAF_AFTER = 200, CYCLE = 420, KNEAD_TICKS = 60, GROOM_TICKS = 80, STRETCH_TICKS = 32;
	/** How fast a pose blends in and out (share a tick). */
	static final float BLEND = 0.12F;
	/** Only animals this near the camera are watched (blocks). */
	static final double WATCH = 64.0;
	// ================================================================================================================

	enum Act {
		GREET, BOW, LIE, DOZE, LOAF, KNEAD, GROOM, STRETCH
	}

	/** This frame's pet pose: the act, how far into it (ticks), how much of it shows (0-1), the animal's id, a wolf or a cat. */
	public record Pose(Act act, float age, float in, int id, boolean cat) {
	}

	static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:pet_pose");

	/** What we know of each watched animal (client clock ticks). */
	private static final class Track {
		long seenSit = -1, seenStill = -1, ownerNear = -1, greetFrom = -1, wokeAt = -1, stretchFrom = -1;
		boolean wasLoafed, ownerKnown;
		@Nullable Act act, lastAct;
		long actFrom;
		float in;
		long lastFrame;
	}

	private static final Int2ObjectOpenHashMap<Track> TRACKS = new Int2ObjectOpenHashMap<>();

	private PetPoses() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(PetPoses::tick);
	}

	/** Whether a pet pose is on this body (Fresh Animations steps aside). */
	public static boolean posed(LivingEntityRenderState state) {
		return state.getData(KEY) != null;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			TRACKS.clear();
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		long now = level.getGameTime();
		Entity camera = minecraft.getCameraEntity();
		if (camera == null) {
			return;
		}
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof Wolf) && !(e instanceof Cat) || e.distanceToSqr(camera) > WATCH * WATCH || !e.isAlive()) {
				continue;
			}
			LivingEntity animal = (LivingEntity)e;
			Track t = TRACKS.computeIfAbsent(e.getId(), id -> new Track());
			Act act = e instanceof Wolf wolf ? wolf(wolf, t, now) : cat((Cat)e, t, now);
			if (act != t.act) {
				t.act = act;
				t.actFrom = now;
			}
			if (animal.hurtTime > 0) {
				t.wokeAt = now;
			}
		}
		if (now % 200 == 0) {
			TRACKS.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	private static @Nullable Act wolf(Wolf wolf, Track t, long now) {
		boolean sitting = wolf.isInSittingPose();
		boolean still = wolf.getDeltaMovement().horizontalDistanceSqr() < 1.0E-4 && wolf.onGround();
		t.seenSit = sitting ? (t.seenSit < 0 ? now : t.seenSit) : -1;
		t.seenStill = still && !sitting ? (t.seenStill < 0 ? now : t.seenStill) : -1;
		if (wolf.isAngry() || wolf.isInWater() || wolf.isPassenger()) {
			return null;
		}
		if (wolf.isTame()) {
			LivingEntity owner = wolf.getOwner();
			boolean near = owner != null && owner.distanceToSqr(wolf) < OWNER_NEAR * OWNER_NEAR;
			if (near) {
				// first sight of the owner since we started watching isn't a homecoming
				if (t.ownerKnown && t.ownerNear >= 0 && now - t.ownerNear > AWAY_TICKS) {
					t.greetFrom = now;
				}
				t.ownerNear = now;
			}
			t.ownerKnown = true;
			if (t.greetFrom >= 0 && now - t.greetFrom < GREET_TICKS) {
				return Act.GREET;
			}
			if (owner instanceof Player player && !sitting && still && player.isShiftKeyDown() && player.distanceToSqr(wolf) < BOW_RANGE * BOW_RANGE
				&& inFront(wolf, player)) {
				return Act.BOW;
			}
		}
		long since = t.seenSit >= 0 ? now - t.seenSit : t.seenStill >= 0 && !wolf.isTame() ? now - t.seenStill - (WILD_LIE_AFTER - LIE_AFTER) : -1;
		if (since < LIE_AFTER) {
			return null;
		}
		boolean awake = t.wokeAt >= 0 && now - t.wokeAt < WAKE_TICKS;
		long dozing = since - LIE_AFTER - DOZE_AFTER;
		if (!awake && dozing >= 0 && (t.wokeAt < 0 || now - t.wokeAt > DOZE_AFTER)) {
			return Act.DOZE;
		}
		return Act.LIE;
	}

	private static @Nullable Act cat(Cat cat, Track t, long now) {
		boolean sitting = cat.isInSittingPose() && cat.isTame() && !cat.isLying();
		t.seenSit = sitting ? (t.seenSit < 0 ? now : t.seenSit) : -1;
		boolean loafed = sitting && now - t.seenSit >= LOAF_AFTER;
		if (t.wasLoafed && !loafed && !cat.isInSittingPose()) {
			t.stretchFrom = now;
		}
		t.wasLoafed = loafed;
		if (t.stretchFrom >= 0 && now - t.stretchFrom < STRETCH_TICKS) {
			return Act.STRETCH;
		}
		if (!loafed) {
			return null;
		}
		// the same moment on every client: the game clock, offset by the cat's own id
		int cycle = CYCLE + (cat.getId() % 5) * 60;
		long phase = (now + cat.getId() * 37L) % cycle;
		if (phase < KNEAD_TICKS) {
			return Act.KNEAD;
		}
		if (phase >= cycle / 2 && phase < cycle / 2 + GROOM_TICKS) {
			return Act.GROOM;
		}
		return Act.LOAF;
	}

	private static boolean inFront(LivingEntity animal, LivingEntity other) {
		double dx = other.getX() - animal.getX(), dz = other.getZ() - animal.getZ();
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		return Math.abs(Mth.wrapDegrees(yaw - animal.yBodyRot)) < 60.0F;
	}

	/** (from the SoundManager mixin) A sound played: dozing animals near a loud one wake. */
	public static void heard(double x, double y, double z, float volume) {
		if (volume < LOUD || TRACKS.isEmpty()) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		double range = Math.min(WAKE_MAX, WAKE_RANGE * volume);
		long now = level.getGameTime();
		for (Int2ObjectOpenHashMap.Entry<Track> entry : TRACKS.int2ObjectEntrySet()) {
			Track t = entry.getValue();
			if (t.act != Act.DOZE && t.act != Act.LIE) {
				continue;
			}
			Entity e = level.getEntity(entry.getIntKey());
			if (e != null && e.distanceToSqr(x, y, z) < range * range) {
				t.wokeAt = now;
			}
		}
	}

	public static void extract(LivingEntity entity, LivingEntityRenderState state, float partialTicks) {
		Track t = entity instanceof Wolf || entity instanceof Cat ? TRACKS.get(entity.getId()) : null;
		if (t == null) {
			state.setData(KEY, null);
			return;
		}
		long frame = entity.level().getGameTime();
		if (frame != t.lastFrame) {
			t.lastFrame = frame;
			t.in = Mth.clamp(t.in + (t.act != null ? BLEND : -BLEND * 1.5F), 0.0F, 1.0F);
		}
		if (t.act != null) {
			t.lastAct = t.act;
		}
		Act act = t.lastAct;
		if (act == null || t.act == null && t.in <= 0.0F) {
			state.setData(KEY, null);
			return;
		}
		float age = frame - t.actFrom + partialTicks;
		state.setData(KEY, new Pose(act, age, PoseBlend.smooth(t.in), entity.getId(), entity instanceof Cat));
		if (act == Act.DOZE) {
			com.jaymoskitchen.client.corpse.ClosedEyes.mark(state);
		}
	}

	// ---- drawing ---------------------------------------------------------------------------------------------------
	/** After the body faces its way: lying on its side, tipped into a bow or stretch, hopping. Forward is -z here. */
	public static void rotate(LivingEntityRenderState state, PoseStack poseStack, float entityScale) {
		Pose p = state.getData(KEY);
		if (p == null || state.deathTime > 0.0F) {
			return;
		}
		float in = p.in(), w = state.boundingBoxWidth / Math.max(entityScale, 0.01F);
		switch (p.act()) {
			case LIE, DOZE -> {
				// rolled onto its side about the ground under it, settled down to rest
				poseStack.translate(w * 0.25F * in, 0.0F, 0.0F);
				poseStack.rotateDegrees(Axis.ZP, 82.0F * in);
				poseStack.translate(0.0F, -w * 0.32F * in, 0.0F);
			}
			case BOW, STRETCH -> tipForward(poseStack, w, (p.act() == Act.BOW ? 18.0F : 24.0F) * in);
			case GREET -> poseStack.translate(0.0F, Math.abs(Mth.sin(p.age() * 0.45F)) * 0.28F * in * envelope(p.age(), GREET_TICKS), 0.0F);
			case LOAF, KNEAD, GROOM -> poseStack.translate(0.0F, -0.12F * in, 0.0F);
		}
	}

	/** Tips the front down about the hind feet (half a body width behind the middle). */
	private static void tipForward(PoseStack poseStack, float width, float degrees) {
		float back = width * 0.5F;
		poseStack.translate(0.0F, 0.0F, back);
		poseStack.rotateDegrees(Axis.XP, -degrees);
		poseStack.translate(0.0F, 0.0F, -back);
	}

	/** 0 to 1 over the first few ticks of {@code length}, back to 0 over its last few. */
	private static float envelope(float age, float length) {
		return PoseBlend.bump(age, 0.0F, 6.0F, length - 10.0F, length);
	}

	/** After the model animates: the limbs of each act, blended over whatever the model had. */
	public static void apply(Model<?> model, Object state) {
		if (!(state instanceof LivingEntityRenderState living) || !(model instanceof EntityModel<?>) || living.deathTime > 0.0F) {
			return;
		}
		Pose p = living.getData(KEY);
		if (p == null || !(living instanceof WolfRenderState) && !(living instanceof FelineRenderState)) {
			return;
		}
		ModelPart root = model.root();
		if (!root.hasChild("head") || !root.hasChild("body") || !root.hasChild("right_front_leg")) {
			return;
		}
		Parts now = new Parts(root, p.cat());
		// the act's pose is laid over the model's own standing pose, blended by how far in it is
		float[][] from = now.save();
		now.reset();
		float a = living.ageInTicks + (p.id() & 31);
		now.head.yRot = living.yRot * Mth.DEG_TO_RAD;
		now.head.xRot = living.xRot * Mth.DEG_TO_RAD;
		switch (p.act()) {
			case GREET -> {
				float hop = Mth.sin(p.age() * 0.45F);
				now.legs(-0.35F * Math.max(0.0F, hop), 0.25F * Math.max(0.0F, hop));
				now.head.xRot = -0.35F + 0.1F * Mth.sin(a * 0.9F);
				now.head.zRot = 0.2F * Mth.sin(a * 0.3F);
				now.wag(0.5F, a * 1.8F, 0.8F);
			}
			case BOW -> {
				now.front(-1.25F);
				now.hind(0.15F);
				now.head.xRot = -0.25F;
				now.head.zRot = 0.3F * Mth.sin(a * 0.12F);
				now.wag(0.25F, a * 1.1F, 0.6F);
			}
			case LIE, DOZE -> {
				boolean doze = p.act() == Act.DOZE;
				now.front(-0.35F);
				now.hind(0.35F);
				now.head.xRot = doze ? 0.15F : -0.1F;
				now.head.yRot *= doze ? 0.0F : 0.5F;
				now.head.zRot = doze ? -0.25F : 0.0F;
				// a slow breath while dozing
				now.body.xRot += doze ? 0.03F * Mth.sin(a * 0.08F) : 0.0F;
				now.wag(1.4F, a * 0.2F, doze ? 0.05F : 0.2F);
			}
			case LOAF, KNEAD, GROOM -> {
				// paws tucked under, tail wrapped round the side
				now.front(-1.45F);
				now.hind(1.45F);
				now.tail.xRot = 1.45F;
				now.tail.yRot = 0.9F;
				if (now.tail2 != null) {
					now.tail2.yRot = 0.9F;
				}
				if (p.act() == Act.KNEAD) {
					float k = Mth.sin(a * 0.5F);
					now.rightFront.xRot = -1.2F + 0.35F * Math.max(0.0F, k);
					now.leftFront.xRot = -1.2F + 0.35F * Math.max(0.0F, -k);
					now.head.xRot = 0.1F;
				} else if (p.act() == Act.GROOM) {
					now.rightFront.xRot = -2.3F;
					now.rightFront.yRot = 0.35F;
					now.head.xRot = 0.55F + 0.08F * Mth.sin(a * 1.6F);
					now.head.yRot = -0.3F;
				}
			}
			case STRETCH -> {
				now.front(-1.35F);
				now.hind(0.2F);
				now.head.xRot = -0.4F;
				now.tail.xRot = 0.4F;
			}
		}
		now.blend(from, p.in());
	}

	/** The parts we pose, by their vanilla names (a wolf and a cat share most). */
	private static final class Parts {
		final ModelPart head, body, rightFront, leftFront, rightHind, leftHind, tail;
		final @Nullable ModelPart tail2;
		final ModelPart[] all;

		Parts(ModelPart root, boolean cat) {
			this.head = root.getChild("head");
			this.body = root.getChild("body");
			this.rightFront = root.getChild("right_front_leg");
			this.leftFront = root.getChild("left_front_leg");
			this.rightHind = root.getChild("right_hind_leg");
			this.leftHind = root.getChild("left_hind_leg");
			this.tail = root.getChild(cat ? "tail1" : "tail");
			this.tail2 = cat && root.hasChild("tail2") ? root.getChild("tail2") : null;
			// every part (a wolf's mane, the ears, the tail's tip), so a sitting model's moved parts all come back together
			this.all = root.getAllParts().stream().filter(part -> part != root).toArray(ModelPart[]::new);
		}

		float[][] save() {
			float[][] out = new float[this.all.length][];
			for (int i = 0; i < this.all.length; i++) {
				ModelPart part = this.all[i];
				out[i] = new float[] {part.x, part.y, part.z, part.xRot, part.yRot, part.zRot};
			}
			return out;
		}

		/** Back to standing as built (a sitting model's raised body and folded legs undone). */
		void reset() {
			for (ModelPart part : this.all) {
				part.resetPose();
			}
		}

		void front(float x) {
			this.rightFront.xRot = x;
			this.leftFront.xRot = x;
		}

		void hind(float x) {
			this.rightHind.xRot = x;
			this.leftHind.xRot = x;
		}

		/** The front legs reach forward by {@code front}, the hind legs back by {@code hind}, as in a hop. */
		void legs(float front, float hind) {
			front(front);
			hind(hind);
		}

		/** The tail raised to {@code lift} and swung side to side ({@code phase}, {@code reach} radians). */
		void wag(float lift, float phase, float reach) {
			this.tail.xRot = lift;
			this.tail.yRot = Mth.sin(phase) * reach;
		}

		void blend(float[][] from, float in) {
			for (int i = 0; i < this.all.length; i++) {
				ModelPart part = this.all[i];
				float[] f = from[i];
				part.x = Mth.lerp(in, f[0], part.x);
				part.y = Mth.lerp(in, f[1], part.y);
				part.z = Mth.lerp(in, f[2], part.z);
				part.xRot = Mth.lerp(in, f[3], part.xRot);
				part.yRot = Mth.lerp(in, f[4], part.yRot);
				part.zRot = Mth.lerp(in, f[5], part.zRot);
			}
		}
	}
}
