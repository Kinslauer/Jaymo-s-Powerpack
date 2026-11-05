package com.jaymospowers.client.sound;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.56) After Create Big Cannons' flying shells (ideas only; our own code and sound): a cannonball, a bomb, a
 * meteor or anything shell-like flying fast whines as it goes by ({@code blast.whistle}). The whine trails a little
 * behind it (the air it tore), grows with its speed, rises in pitch while it comes at you and drops once it's past, and
 * fades out when it lands or flies off.
 */
public final class ShellWhistle {
	/** Heard this far (blocks); looked for every this many ticks; at most this many at once. */
	static final double REACH = 56.0;
	static final int LOOK_EVERY = 4, MAX = 6;
	/** It must be going at least this fast (blocks a tick) to whistle; it's loudest from FULL_SPEED. */
	static final double MIN_SPEED = 0.7, FULL_SPEED = 2.0;
	/** The whine trails this many ticks of flight behind it (at most TRAIL_MAX blocks). */
	static final double TRAIL_TICKS = 2.0, TRAIL_MAX = 6.0;
	/** Pitch: its base (plus up to BASE_SPREAD at random), and how far it bends coming at you or going away. */
	static final float BASE_PITCH = 0.9F, BASE_SPREAD = 0.25F, BEND = 0.35F;
	/** Fades out over this many ticks once it stops, lands or is gone. */
	static final int FADE_OUT = 12;

	/** These whistle; and so does anything whose id says it's a cannonball, shell, mortar, bomb, meteor or boulder. */
	static final Set<Identifier> SHELLS = Set.of(
		Identifier.fromNamespaceAndPath("jaymoskingdoms", "cannonball"),
		Identifier.fromNamespaceAndPath("jaymospowers", "arsenal_cannonball"),
		Identifier.fromNamespaceAndPath("jaymospowers", "demolition_bomb"));
	static final String[] SHELL_WORDS = {"cannonball", "cannon_ball", "shell", "mortar", "bomb", "meteor", "boulder"};
	/** ...unless it's one of these (ridden, or not a shell at all). */
	static final String[] NOT_SHELLS = {"glider", "smoke", "seed"};

	private static final Map<Integer, Whine> PLAYING = new HashMap<>();
	private static final Map<net.minecraft.world.entity.EntityType<?>, Boolean> KINDS = new HashMap<>();
	private static int clock;

	private ShellWhistle() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ShellWhistle::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			PLAYING.clear();
			return;
		}
		PLAYING.values().removeIf(Whine::isStopped);
		if (minecraft.isPaused() || ++clock % LOOK_EVERY != 0 || PLAYING.size() >= MAX) {
			return;
		}
		for (Entity e : level.getEntities((Entity)null, new AABB(player.position(), player.position()).inflate(REACH), ShellWhistle::shell)) {
			if (PLAYING.size() >= MAX) {
				break;
			}
			if (!PLAYING.containsKey(e.getId()) && e.getDeltaMovement().lengthSqr() > MIN_SPEED * MIN_SPEED) {
				Whine whine = new Whine(e, level.getRandom());
				PLAYING.put(e.getId(), whine);
				minecraft.getSoundManager().play(whine);
			}
		}
	}

	private static boolean shell(Entity e) {
		if (e.onGround() || e.isInWater()) {
			return false;
		}
		return KINDS.computeIfAbsent(e.getType(), type -> {
			Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
			if (SHELLS.contains(id)) {
				return true;
			}
			String path = id.getPath().toLowerCase(Locale.ROOT);
			for (String word : NOT_SHELLS) {
				if (path.contains(word)) {
					return false;
				}
			}
			for (String word : SHELL_WORDS) {
				if (path.contains(word)) {
					return true;
				}
			}
			return false;
		});
	}

	/** One shell's whine, following it. */
	static final class Whine extends AbstractTickableSoundInstance {
		private final Entity shell;
		private final float basePitch;
		private int fading;

		Whine(Entity shell, RandomSource random) {
			super(SoundEvent.createVariableRangeEvent(Blasts.WHISTLE), SoundSource.NEUTRAL, RandomSource.create(random.nextLong()));
			this.shell = shell;
			this.looping = true;
			this.delay = 0;
			this.attenuation = Attenuation.LINEAR;
			this.basePitch = BASE_PITCH + random.nextFloat() * BASE_SPREAD;
			this.pitch = this.basePitch;
			// the volume it starts at sets how far it's heard (16 blocks a unit); after that it only sets how loud
			this.volume = (float)(REACH / 16.0);
			this.follow();
		}

		/** Where the whine is: a little behind the shell, along its flight. */
		private Vec3 follow() {
			Vec3 v = this.shell.getDeltaMovement();
			Vec3 at = this.shell.position();
			double speed = v.length();
			if (speed > 1.0E-3) {
				at = at.subtract(v.scale(Math.min(TRAIL_MAX, speed * TRAIL_TICKS) / speed));
			}
			this.x = at.x;
			this.y = at.y;
			this.z = at.z;
			return at;
		}

		@Override
		public void tick() {
			LocalPlayer player = Minecraft.getInstance().player;
			boolean flying = player != null && !this.shell.isRemoved() && !this.shell.onGround() && !this.shell.isInWater()
				&& this.shell.getDeltaMovement().lengthSqr() > MIN_SPEED * MIN_SPEED;
			if (!flying) {
				this.fading++;
				this.volume *= 0.75F;
				if (this.fading >= FADE_OUT) {
					this.stop();
				}
				return;
			}
			Vec3 at = this.follow();
			Vec3 v = this.shell.getDeltaMovement();
			double speed = v.length();
			Vec3 toEar = player.getEyePosition().subtract(at);
			double d = toEar.length();
			// +1 coming straight at you, -1 going straight away
			float coming = d < 1.0E-3 ? 0.0F : (float)(v.dot(toEar) / (speed * d));
			float loud = (float)Mth.clamp((speed - MIN_SPEED) / (FULL_SPEED - MIN_SPEED), 0.15, 1.0);
			this.volume = loud;
			this.pitch = Mth.clamp(this.basePitch + BEND * coming * loud, 0.5F, 2.0F);
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}
	}
}
