package com.jaymospowers.client.sound;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Stance;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How bodies sound as they move (Andy's ImmersiveFX picks, built as our own: footsteps by material, armour by material,
 * wading and swimming, beds):
 * <ul>
 * <li><b>Footsteps.</b> Every step sound the game plays (yours, other players', any mob's) gets a second layer from
 * {@link SoundKinds#ground the ground underfoot}: grass, stone, wood, sand, gravel, snow, metal, carpet or mud, a harder
 * one when running, a push-off when jumping and a drop as heavy as the fall when landing. It is hung on the game's own
 * step, so it lands on the same beat and costs nothing while nobody walks.</li>
 * <li><b>Quieter feet.</b> Sneaking takes the step (the game's and ours) down to {@link #SNEAK}, the stance wheel's low
 * crouch to {@link #LOW}, and crawling to {@link #PRONE}. Who else can hear them is Kingdoms' side ({@code Footsteps}).</li>
 * <li><b>Armour.</b> On each step the armour worn sounds like what it is ({@link SoundKinds#armour}): cloth rustles,
 * leather creaks, chain rattles, scale and plate clank, the heaviest sets thud, bone clacks, crystal rings. The more of a
 * set worn, the louder. (This replaces Ambience's old clink, which was one equip sound for everything.)</li>
 * <li><b>Water.</b> Wading sloshes, swimming strokes, and dropping in fast is a proper dive.</li>
 * <li><b>Beds.</b> A rustle as anyone lies down and as they get up (sleep or Kingdoms' bed rest), and now and then as a
 * sleeper turns over, more often while the night runs by as a timelapse.</li>
 * </ul>
 * All of it plays on this side only, through {@link ImmersiveSounds#play}, within its reach and channel room.
 */
public final class Footfalls {
	/** The step's own volume while sneaking, in the low crouch and crawling. */
	static final float SNEAK = 0.5F, LOW = 0.35F, PRONE = 0.25F;
	/** Our ground layer's volume on a walking and a running step (the game plays its own at 0.15). */
	static final float STEP_VOLUME = 0.13F, RUN_VOLUME = 0.19F, JUMP_VOLUME = 0.1F;
	/** A landing this fast (blocks a tick, down) is heard; louder up to this, at most this loud. */
	static final double LAND_MIN_SPEED = 0.3, LAND_FULL_SPEED = 1.2;
	static final float LAND_VOLUME = 0.55F;
	/** Armour: the volume at a full set, and how much each worn piece counts toward a full set. */
	static final float ARMOUR_VOLUME = 0.2F;
	static final float HEAD_SHARE = 0.15F, CHEST_SHARE = 0.5F, LEGS_SHARE = 0.25F, FEET_SHARE = 0.1F;
	/** Water: a slosh every this many blocks waded, a stroke every this many swum (sprint swimming: the shorter). */
	static final double WADE_STRIDE = 1.5, STROKE_STRIDE = 2.0, SPRINT_STROKE_STRIDE = 1.4;
	static final float WADE_VOLUME = 0.25F, STROKE_VOLUME = 0.22F;
	/** Hitting water this fast (blocks a tick, down) is a dive. */
	static final double DIVE_SPEED = 0.45;
	/** Beds: volumes, and the chance a tick a sleeper turns over (a timelapse night: the faster one). */
	static final float BED_VOLUME = 0.3F, TURN_VOLUME = 0.18F;
	static final float TURN_CHANCE = 1.0F / 900.0F, TURN_CHANCE_FAST = 1.0F / 120.0F;
	/** Moving and sleeping bodies are listened to this near (blocks). */
	static final double NEAR = 16.0;

	private static final class Body {
		boolean ground = true, wet, sleeping;
		double lastVy, waded, swum;
		int steps;
	}

	private static final Map<Entity, Body> BODIES = new WeakHashMap<>();
	private static long lastClock = Long.MIN_VALUE;
	private static boolean fastNight;

	private Footfalls() {
	}

	// ---- the game's own step sounds ----------------------------------------------------------------------------------

	/**
	 * (ClientLevel mixin) A sound is about to play at a spot: if it's a step, lay our ground and armour layers on it, and
	 * hand back how loud the step itself should be (quieter for a sneaking, crouching or crawling walker).
	 */
	public static float heard(ClientLevel level, SoundEvent sound, SoundSource source, float volume, double x, double y, double z) {
		var id = sound.location();
		if (!id.getPath().endsWith(".step") || id.getNamespace().equals(JaymosPowers.MOD_ID)) {
			return volume;
		}
		LivingEntity walker = walkerAt(level, x, y, z);
		if (walker == null) {
			return volume;
		}
		float quiet = quiet(walker);
		RandomSource random = level.getRandom();
		if (biped(walker)) {
			SoundKinds.Ground ground = SoundKinds.ground(level, walker);
			boolean running = walker.isSprinting();
			ImmersiveSounds.play(level, x, y, z, "step." + ground.id + (running ? ".run" : ".walk"), source,
				(running ? RUN_VOLUME : STEP_VOLUME) * quiet, 0.95F + random.nextFloat() * 0.1F);
		}
		armour(level, walker, x, y, z, quiet, random);
		return volume * quiet;
	}

	private static @Nullable LivingEntity walkerAt(ClientLevel level, double x, double y, double z) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(x - 0.5, y - 0.6, z - 0.5, x + 0.5, y + 0.6, z + 0.5),
			e -> !e.isSpectator())) {
			double d = e.distanceToSqr(x, y, z);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Two legs, about a person's size: the bodies whose feet we layer (a horse or a spider keeps its own steps). */
	static boolean biped(LivingEntity e) {
		return e instanceof Player || e.getBbHeight() >= 1.2F && e.getBbWidth() <= 1.0F;
	}

	/** How loud {@code walker}'s feet are: 1, less sneaking, less again in the low crouch or crawling. */
	public static float quiet(LivingEntity walker) {
		if (walker instanceof Player p) {
			if (p.getPose() == Pose.SWIMMING && !p.isInWater()) {
				return PRONE;
			}
			if (p.isCrouching()) {
				return Stance.low(p) ? LOW : SNEAK;
			}
			return 1.0F;
		}
		return walker.isShiftKeyDown() ? SNEAK : 1.0F;
	}

	/** The armour worn, sounding as it's made, as loud as how much of it there is. */
	private static void armour(ClientLevel level, LivingEntity walker, double x, double y, double z, float quiet, RandomSource random) {
		float share = 0.0F;
		SoundKinds.Armour kind = null;
		float kindShare = 0.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD}) {
			ItemStack stack = walker.getItemBySlot(slot);
			Equippable equippable = stack.isEmpty() ? null : stack.get(DataComponents.EQUIPPABLE);
			if (equippable == null || equippable.slot() != slot) {
				continue;
			}
			float part = switch (slot) {
				case HEAD -> HEAD_SHARE;
				case CHEST -> CHEST_SHARE;
				case LEGS -> LEGS_SHARE;
				default -> FEET_SHARE;
			};
			share += part;
			if (part > kindShare) {
				kindShare = part;
				kind = SoundKinds.armour(stack);
			}
		}
		if (kind == null) {
			return;
		}
		Body body = BODIES.computeIfAbsent(walker, e -> new Body());
		body.steps++;
		// soft armour speaks every other step, hard armour every step
		boolean soft = kind == SoundKinds.Armour.CLOTH || kind == SoundKinds.Armour.LEATHER;
		if (soft && (body.steps & 1) == 1) {
			return;
		}
		float gait = walker.isSprinting() ? 1.3F : 1.0F;
		ImmersiveSounds.play(level, x, y + 1.0, z, "armour." + kind.id, walker.getSoundSource(),
			ARMOUR_VOLUME * kind.loud * share * gait * quiet, 0.93F + random.nextFloat() * 0.14F);
	}

	// ---- moving bodies (from Ambience's walk over the bodies near you) -----------------------------------------------

	/** Once a tick, before the bodies: is the night running by as a timelapse (the clock jumping)? */
	public static void tick(ClientLevel level) {
		long clock = level.getOverworldClockTime();
		fastNight = lastClock != Long.MIN_VALUE && clock - lastClock > 3;
		lastClock = clock;
	}

	/** {@code entity} moved {@code moved} since last tick: jumps, landings, wading, swimming, dives and beds. */
	public static void moved(ClientLevel level, Entity viewer, LivingEntity entity, Vec3 moved, RandomSource random) {
		Body body = BODIES.computeIfAbsent(entity, e -> new Body());
		boolean ground = entity.onGround(), wet = entity.isInWater(), sleeping = entity.isSleeping();
		if (entity.distanceToSqr(viewer) < NEAR * NEAR && !entity.isPassenger()) {
			sounds(level, entity, body, moved, ground, wet, sleeping, random);
		}
		body.ground = ground;
		body.wet = wet;
		body.sleeping = sleeping;
		body.lastVy = moved.y;
	}

	private static void sounds(ClientLevel level, LivingEntity entity, Body body, Vec3 moved, boolean ground, boolean wet, boolean sleeping,
		RandomSource random) {
		double x = entity.getX(), y = entity.getY(), z = entity.getZ();
		SoundSource source = entity.getSoundSource();
		float pitch = 0.95F + random.nextFloat() * 0.1F;
		// beds
		if (sleeping != body.sleeping) {
			ImmersiveSounds.play(level, x, y + 0.3, z, sleeping ? "bed.lie" : "bed.rise", source, BED_VOLUME, pitch);
		} else if (sleeping && random.nextFloat() < (fastNight ? TURN_CHANCE_FAST : TURN_CHANCE)) {
			ImmersiveSounds.play(level, x, y + 0.3, z, "bed.turn", source, TURN_VOLUME, pitch);
		}
		if (sleeping) {
			return;
		}
		double horizontal = Math.sqrt(moved.x * moved.x + moved.z * moved.z);
		float quiet = quiet(entity);
		// jumping and landing, on land
		if (!wet && biped(entity)) {
			if (body.ground && !ground && moved.y > 0.1) {
				SoundKinds.Ground from = SoundKinds.ground(level.getBlockState(BlockPos.containing(x - moved.x, y - moved.y - 0.2, z - moved.z)));
				ImmersiveSounds.play(level, x, y, z, "step." + from.id + ".jump", source, JUMP_VOLUME * quiet, pitch);
			} else if (!body.ground && ground && -body.lastVy >= LAND_MIN_SPEED) {
				float hard = (float)Mth.clamp((-body.lastVy - LAND_MIN_SPEED) / (LAND_FULL_SPEED - LAND_MIN_SPEED), 0.15, 1.0);
				ImmersiveSounds.play(level, x, y, z, "step." + SoundKinds.ground(level, entity).id + ".land", source,
					LAND_VOLUME * hard * Math.max(quiet, 0.6F), pitch - hard * 0.08F);
			}
		}
		// water
		if (wet && !body.wet && -moved.y >= DIVE_SPEED) {
			float hard = (float)Mth.clamp(-moved.y, 0.45, 1.5);
			ImmersiveSounds.play(level, x, y, z, "water.dive", source, 0.35F * hard, pitch - 0.05F * hard);
			body.swum = 0.0;
		} else if (wet && !entity.isUnderWater() && !entity.isSwimming() && (ground || moved.y > -0.1)) {
			body.waded += horizontal;
			if (body.waded >= WADE_STRIDE) {
				body.waded = 0.0;
				ImmersiveSounds.play(level, x, y + 0.3, z, "water.wade", source, WADE_VOLUME * Math.max(quiet, 0.5F)
					* (float)Mth.clamp(horizontal * 5.0, 0.5, 1.3), pitch);
			}
		} else if (wet && horizontal > 0.03) {
			body.swum += horizontal;
			double stride = entity.isSwimming() ? SPRINT_STROKE_STRIDE : STROKE_STRIDE;
			if (body.swum >= stride) {
				body.swum = 0.0;
				ImmersiveSounds.play(level, x, y + 0.5, z, "water.stroke", source, STROKE_VOLUME * (entity.isSwimming() ? 1.3F : 1.0F), pitch);
			}
		}
		if (!wet) {
			body.waded = 0.0;
			body.swum = 0.0;
		}
	}

	/** Forgets everyone (a new level). */
	public static void clear() {
		BODIES.clear();
		lastClock = Long.MIN_VALUE;
	}
}
