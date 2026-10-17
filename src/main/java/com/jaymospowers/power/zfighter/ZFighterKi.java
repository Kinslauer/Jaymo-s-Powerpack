package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The ki techniques:
 * <ul>
 * <li>G, Ki Blast: a fast bolt of ki from the palm. Cheap and quick; it hurts and knocks back, and fizzles on stone.</li>
 * <li>V tap, Ki Blast; V held {@link #BARRAGE_WINDUP} ticks, Ki Barrage: bolts from both palms in turn while it's held.</li>
 * <li>Sneak+V (hold), Ki Wave: cup your hands and gather it ({@link #WAVE_FULL} ticks to full), let go and it fires as a beam
 * whose head travels out (at the signature's speed, {@code ZFighterWaveBeam}) through everything in a line, ending in a
 * blast; on its way it can lock in a beam clash. The fuller, the wider, the harder, the dearer. A full wave from a
 * Super breaks blocks (if mob griefing is on).</li>
 * <li>B, Flash Step: gone in a blink, up to {@link #STEP_RANGE} blocks the way you look; aimed at a creature, behind
 * it, facing its back.</li>
 * </ul>
 */
public final class ZFighterKi {
	/** Ki Blast and Ki Wave's damage: ordinary, armour applies. */
	static final ResourceKey<DamageType> KI = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosPowers.id("zfighter_ki"));

	// ---- Ki Blast ----
	static final float BLAST_KI = 8.0F;
	static final int BLAST_COOLDOWN = 6;
	static final float BLAST_DAMAGE = 5.0F;
	static final double BLAST_SPEED = 1.6;
	static final int BLAST_LIFE = 40;
	static final float BLAST_EXHAUSTION = 0.5F;

	// ---- Ki Disc: a flat spinning blade of ki that cuts through every creature and block in its path ----
	static final float DISC_KI = 12.0F;
	static final int DISC_COOLDOWN = 20;
	static final float DISC_DAMAGE = 6.0F;
	static final double DISC_SPEED = 1.3;
	/** How far it flies (blocks) unless something stops it. */
	static final double DISC_RANGE = 60.0;
	/** Half its width (blocks): it cuts creatures this near its path and a flat slice of blocks this far to either side. */
	static final double DISC_HALF_WIDTH = 0.6;
	/** How finely its path is walked for blocks (blocks between samples, along it and across it). */
	static final double DISC_CUT_STEP = 0.25;
	/** It stops on a block this hard or harder (obsidian's destroy time), an unbreakable one, or one holding a block entity. */
	static final float DISC_STOP_HARDNESS = 50.0F;
	/** At most this many blocks cut in one tick (a guard; at its speed and width it cuts far fewer). */
	static final int DISC_MAX_CUTS = 24;
	/** In flight it whirs: a high hum every this many ticks. (The wind-up and throw are {@code ZFighterDisc}.) */
	static final int DISC_WHIR_EVERY = 8;
	static final float DISC_WHIR_PITCH = 2.0F;

	// ---- Homing Volley: small bolts that seek out foes ----
	static final float VOLLEY_KI = 20.0F;
	static final int VOLLEY_COOLDOWN = 40;
	static final int VOLLEY_BOLTS = 5;
	static final float VOLLEY_DAMAGE = 3.0F;
	static final double VOLLEY_SPEED = 0.9;
	static final int VOLLEY_LIFE = 60;
	static final double VOLLEY_SEEK = 16.0;
	/** How hard a seeking bolt turns each tick (0-1). */
	static final double VOLLEY_TURN = 0.25;

	// ---- Ki Bomb (V, held like the wave): a big slow ball that bursts ----
	static final float BOMB_MIN_KI = 30.0F;
	static final float BOMB_MAX_KI = 70.0F;
	static final float BOMB_DAMAGE_MIN = 10.0F;
	static final float BOMB_DAMAGE_MAX = 20.0F;
	static final double BOMB_SIZE_MIN = 0.6;
	static final double BOMB_SIZE_MAX = 1.6;
	static final float BOMB_BLAST_MIN = 1.5F;
	static final float BOMB_BLAST_MAX = 3.5F;
	static final double BOMB_SPEED = 0.6;
	static final int BOMB_LIFE = 80;

	// ---- Ki Wave ----
	static final float WAVE_MIN_KI = 25.0F;
	static final float WAVE_MAX_KI = 60.0F;
	static final int WAVE_FULL = 60;
	static final int WAVE_COOLDOWN = 100;
	static final float WAVE_DAMAGE_MIN = 8.0F;
	static final float WAVE_DAMAGE_MAX = 18.0F;
	static final double WAVE_RANGE = 40.0;
	static final double WAVE_RADIUS_MIN = 0.8;
	static final double WAVE_RADIUS_MAX = 1.8;
	static final float WAVE_BLAST_MIN = 1.0F;
	static final float WAVE_BLAST_MAX = 3.0F;
	static final float WAVE_EXHAUSTION = 2.0F;
	/** How fast the head of each lineage's beam travels (blocks a tick): the Ki Wave, the Crimson Cannon (slow and heavy), the Spiral Lance, the Frost Needle. */
	static final double WAVE_SPEED = 2.5, CANNON_SPEED = 1.2, LANCE_SPEED = 2.0, NEEDLE_SPEED = 4.5;
	/** How long a beam fades once it has stopped (ticks, just to be seen). */
	static final int BEAM_TICKS = 12;
	/** Players this near a beam's middle see it drawn. */
	static final double BEAM_SEEN = 160.0;
	/** How far ahead of its middle (in its sizes) the Spirit Bomb's leading edge meets the ground. */
	static final double SPIRIT_LEAD = 0.6;
	/**
	 * Whatever block it meets (ground, wall, roof), it embeds itself: it grinds in, glowing brighter all the while, this
	 * long (ticks), sinking this share of its size into the blocks (never slower than {@link #SPIRIT_SINK_SPEED}), then bursts.
	 */
	static final double SPIRIT_SINK_SPEED = 0.15;
	static final int SPIRIT_SINK_TICKS = 60;
	static final double SPIRIT_EMBED = 0.7;
	/** Flown this long (ticks) without meeting anything, it drops straight down; this much longer, it bursts where it is. */
	static final int SPIRIT_FALL_AFTER = 200;
	static final int SPIRIT_FALL_LIMIT = 400;
	static final double SPIRIT_FALL_SPEED = 1.2;
	/** The ring of blasts round the main one: how many, how far out (in its sizes) and how strong (of the main one). */
	static final int SPIRIT_RING = 8;
	static final double SPIRIT_RING_AT = 0.7;
	static final float SPIRIT_RING_POWER = 0.55F;
	/** And an outer ring, so the blast tears up a wide area. */
	static final int SPIRIT_OUTER_RING = 12;
	static final double SPIRIT_OUTER_AT = 1.4;
	static final float SPIRIT_OUTER_POWER = 0.4F;

	// ---- Flash Step ----
	static final float STEP_KI = 15.0F;
	static final int STEP_COOLDOWN = 30;
	static final double STEP_RANGE = 10.0;
	static final double STEP_TARGET_RANGE = 16.0;
	static final float STEP_EXHAUSTION = 0.8F;

	// V: a tap is a Ki Blast; held, after a windup, bolts from both palms in turn until it's let go or the ki runs dry
	/** V let go sooner than this (ticks) is a tap: one Ki Blast. Held this long, the barrage starts. */
	static final int BARRAGE_WINDUP = 40;
	/** a bolt every this many ticks, from the right palm, then the left */
	static final int BARRAGE_EVERY = 2;
	static final float BARRAGE_KI = 2.5F;
	static final float BARRAGE_DAMAGE = 1.8F;
	static final double BARRAGE_SPEED = 1.8;
	/** how far each bolt may stray from the aim */
	static final double BARRAGE_SPREAD = 0.08;
	static final int BARRAGE_LIFE = 30;
	static final float BARRAGE_EXHAUSTION = 0.1F;
	/** a barrage bolt's orb, blocks across */
	static final double BARRAGE_ORB = 0.3;
	/** ticks before V fires again after a barrage ends */
	static final int BARRAGE_COOLDOWN = 20;

	private static final int BLAST_COLOR = 0xFFC85A;
	private static final int WAVE_COLOR = 0x8FD3FF;

	// ---- the signature V of each lineage (the Ki Wave's shape; the Ki Bomb is the same for all) ----
	/** How a beam is drawn: a broad wave, a thin lance wound with a spiral, a fine needle. */
	static final int WAVE = 0;
	static final int SPIRAL = 1;
	static final int NEEDLE = 2;

	/**
	 * One lineage's V: ticks to full, times the wave's damage, width, blast and ki; its reach; whether it passes through
	 * blocks; how long it slows what it hits (ticks); its colour (outside Super), how it's drawn, and how fast its head
	 * travels (blocks a tick).
	 */
	record Signature(int full, float damage, double width, float blast, float ki, double range, boolean pierces, int chill, int color,
		int style, double speed) {
	}

	/** By lineage: none and Earthling the Ki Wave; Warrior-born the Crimson Cannon; Verdant the Spiral Lance; Frost-born the Frost Needle. */
	static final Signature[] SIGNATURES = {
		new Signature(WAVE_FULL, 1.0F, 1.0, 1.0F, 1.0F, WAVE_RANGE, false, 0, WAVE_COLOR, WAVE, WAVE_SPEED),
		new Signature(WAVE_FULL, 1.0F, 1.0, 1.0F, 1.0F, WAVE_RANGE, false, 0, WAVE_COLOR, WAVE, WAVE_SPEED),
		new Signature(70, 1.3F, 1.25, 1.6F, 1.15F, WAVE_RANGE, false, 0, 0xB050FF, WAVE, CANNON_SPEED),
		new Signature(70, 1.25F, 0.35, 0.0F, 1.0F, 56.0, true, 0, 0xE8FF70, SPIRAL, LANCE_SPEED),
		new Signature(20, 0.8F, 0.25, 0.0F, 0.6F, 56.0, false, 40, 0xFF70D8, NEEDLE, NEEDLE_SPEED),
	};

	static Signature signature(ZFighterPower.Live live) {
		return SIGNATURES[Mth.clamp(live.lineage, 0, SIGNATURES.length - 1)];
	}

	// ---- the Great Ape's mouth beam (V) ----
	static final double APE_BEAM_RANGE = 28.0;
	static final double APE_BEAM_RADIUS = 1.5;
	static final float APE_BEAM_DAMAGE = 16.0F;
	static final float APE_BEAM_BLAST = 2.5F;
	static final int APE_BEAM_COLOR = 0xFF6A3A;
	/** Its head travels this fast (blocks a tick). */
	static final double APE_BEAM_SPEED = 1.8;

	/** What a bolt is: drawn and spent differently. */
	static final int BOLT = 0;
	static final int DISC = 1;
	static final int HOMING = 2;
	static final int BOMB = 3;
	static final int SPIRIT = 4;

	private static final class Bolt {
		final ServerLevel level;
		final UUID owner;
		Vec3 pos;
		Vec3 velocity;
		final float damage;
		final int color;
		final int kind;
		final int life;
		/** How big it is (blocks): its reach for a hit and how it's drawn. */
		double size;
		/** BOMB and SPIRIT: the blast's power and reach where it lands, and whether it may tear the ground. */
		float burst;
		double radius;
		boolean griefing;
		/** SPIRIT: the thrower's entity id, so the client keeps drawing the same sphere it watched gather. */
		int fxId;
		/** Which orb is this one's on the clients, and how big across (0: by kind). */
		final int orbId = nextOrb++;
		double orbSize;
		/** SPIRIT: ticks it has been grinding into the ground (0 while it flies). */
		int sinking;
		/** DISC: who it has already cut (it passes through), which way is across it (flat, level), how far it's flown. */
		final Set<Integer> struck = new HashSet<>();
		Vec3 side = Vec3.ZERO;
		double flown;
		int age;

		Bolt(ServerLevel level, UUID owner, Vec3 pos, Vec3 velocity, float damage, int color, int kind, int life) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
			this.damage = damage;
			this.color = color;
			this.kind = kind;
			this.life = life;
		}
	}

	/** Explosions of ours only shake the ground: the hurting is done by hand, so the fighter and bystanders are spared. */
	static final ExplosionDamageCalculator GROUND_ONLY = new ExplosionDamageCalculator() {
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return false;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return 0.0F;
		}
	};

	private static final List<Bolt> BOLTS = new ArrayList<>();
	/** This tick's ki orbs, by level: sent to everyone near at the end of the tick ({@code ZFighterOrbs} draws them). */
	private static final java.util.Map<ServerLevel, List<ZFighterNet.Orb>> ORBS = new java.util.HashMap<>();
	/** Players this near an orb see it. */
	static final double ORB_SEEN = 128.0;
	private static int nextOrb = 1;

	// ---- the Spirit Bomb's burst: a star going off ----
	/** Blue motes flung out in every direction at once, and how fast (scaled by the bomb's size). */
	static final int NOVA_MOTES = 500;
	static final double NOVA_SPEED = 1.0;
	/** Cracks of energy running out from the middle: how many, for how many ticks, and how far each tick (a share of the size). */
	static final int NOVA_CRACKS = 22;
	static final int NOVA_TICKS = 14;
	static final double NOVA_STEP = 0.32;
	static final int NOVA_BLUE = 0x5EC8FF, NOVA_PALE = 0xCFF4FF;

	/** A star going off: cracks of energy (each a heading and where its jagged tip is now) running out from {@code at}. */
	private record Nova(ServerLevel level, Vec3 at, double size, Vec3[] headings, Vec3[] tips, int[] age) {
	}

	private static final List<Nova> NOVAS = new ArrayList<>();

	private ZFighterKi() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterKi::tick);
	}

	static void clear() {
		BOLTS.clear();
		ORBS.clear();
	}

	static DamageSource source(ServerPlayer player) {
		return player.damageSources().source(KI, player);
	}

	// ---------------------------------------------------------------- Ki Blast

	static boolean blast(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, BLAST_KI)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		// from the right palm, a little ahead
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
		Vec3 from = player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.3)).add(0.0, -0.25, 0.0);
		float damage = BLAST_DAMAGE * ZFighterPower.kiPower(player, live);
		BOLTS.add(new Bolt(level, player.getUUID(), from, look.scale(BLAST_SPEED), damage, formColor(live), BOLT, BLAST_LIFE));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(BLAST_EXHAUSTION);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.7F, 1.5F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
			Bolt bolt = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(bolt.owner);
			if (owner == null || owner.level() != bolt.level || !flyBolt(owner, bolt)) {
				it.remove();
			}
		}
		sendOrbs();
		NOVAS.removeIf(ZFighterKi::tickNova);
		if (server.getTickCount() % 20 == 0) {
			ZFighterTechniques.sweepLenders(server);
		}
	}

	// ---------------------------------------------------------------- V: Ki Blast (tap) and Ki Barrage (hold)

	/** V down: the hands start to gather. What it becomes is decided by how long it's held. */
	static void pressBarrage(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		if (now < live.barrageReady) {
			return;
		}
		live.barrageFrom = now;
	}

	/** V up: let go during the windup, it was a tap and one Ki Blast flies; let go mid-barrage, it ends. */
	static void releaseBarrage(ServerPlayer player, ZFighterPower.Live live) {
		if (live.barrageFrom < 0) {
			return;
		}
		long now = player.level().getGameTime();
		long held = now - live.barrageFrom;
		live.barrageFrom = -1L;
		if (held < BARRAGE_WINDUP) {
			if (blast(player, live)) {
				live.barrageReady = now + BLAST_COOLDOWN;
			}
		} else {
			live.barrageReady = now + BARRAGE_COOLDOWN;
		}
	}

	/** While V is held: the windup (ki gathers in both palms), then the barrage. */
	static void tickBarrage(ServerPlayer player, ZFighterPower.Live live) {
		if (live.barrageFrom < 0) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (Powers.silenced(player) || ZFighterLineage.ape(player) || !player.isAlive()) {
			live.barrageFrom = -1L;
			return;
		}
		long held = now - live.barrageFrom;
		Vec3 look = player.getViewVector(1.0F);
		int color = formColor(live);
		DustParticleOptions dust = new DustParticleOptions(color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : color, 1.0F);
		if (held < BARRAGE_WINDUP) {
			if (held < 5) {
				// too soon to tell a tap from a hold
				return;
			}
			float wind = held / (float)BARRAGE_WINDUP;
			RandomSource random = level.getRandom();
			for (boolean left : new boolean[] {false, true}) {
				// an orb grows in each palm, a mote of ki drawn into one of them now and then
				Vec3 hand = palm(player, look, left);
				orb(level, -(player.getId() * 4 + (left ? 2 : 1)), hand, 0.1 + 0.25 * wind, color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : color);
				if (random.nextInt(3) == 0) {
					double r = 0.6 * (1.0F - wind) + 0.15;
					double ox = (random.nextDouble() - 0.5) * r * 2;
					double oy = (random.nextDouble() - 0.5) * r * 2;
					double oz = (random.nextDouble() - 0.5) * r * 2;
					level.sendParticles(dust, true, false, hand.x + ox, hand.y + oy, hand.z + oz, 0, -ox, -oy, -oz, 0.1);
				}
			}
			if (held % 10 == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.8F,
					1.0F + wind);
			}
			return;
		}
		long firing = held - BARRAGE_WINDUP;
		if (firing % BARRAGE_EVERY != 0) {
			return;
		}
		if (!ZFighterPower.spend(player, live, BARRAGE_KI)) {
			// out of ki: the barrage is spent; letting go of V does nothing more
			live.barrageFrom = -1L;
			live.barrageReady = now + BARRAGE_COOLDOWN;
			return;
		}
		boolean left = (firing / BARRAGE_EVERY) % 2 == 1;
		RandomSource random = level.getRandom();
		Vec3 aim = look.add((random.nextDouble() - 0.5) * BARRAGE_SPREAD * 2, (random.nextDouble() - 0.5) * BARRAGE_SPREAD * 2,
			(random.nextDouble() - 0.5) * BARRAGE_SPREAD * 2).normalize();
		Bolt bolt = new Bolt(level, player.getUUID(), palm(player, look, left), aim.scale(BARRAGE_SPEED),
			BARRAGE_DAMAGE * ZFighterPower.kiPower(player, live), color, BOLT, BARRAGE_LIFE);
		bolt.orbSize = BARRAGE_ORB;
		BOLTS.add(bolt);
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		player.swing(left == mainRight ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		exhaust(player, BARRAGE_EXHAUSTION);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.45F,
			1.6F + random.nextFloat() * 0.3F);
	}

	/** A ki orb {@code size} blocks across at {@code at} this tick, {@code id} the same tick to tick for the same orb. */
	static void orb(ServerLevel level, int id, Vec3 at, double size, int color) {
		ORBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new ZFighterNet.Orb(id, at.x, at.y, at.z, (float)size, color & 0xFFFFFF));
	}

	/**
	 * A ball of water drawn as a plain orb (Darktide's Drown): slow, wobbling and see-through rather than glowing.
	 * {@code id} must not clash with a ki orb's, so callers use negative ids of their own.
	 */
	public static void waterOrb(ServerLevel level, int id, Vec3 at, double size, int color) {
		ORBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new ZFighterNet.Orb(id, at.x, at.y, at.z, (float)size,
			color & 0xFFFFFF | ZFighterNet.Orb.WATER));
	}

	/** This tick's orbs to everyone near them. */
	private static void sendOrbs() {
		for (java.util.Map.Entry<ServerLevel, List<ZFighterNet.Orb>> entry : ORBS.entrySet()) {
			List<ZFighterNet.Orb> orbs = entry.getValue();
			if (orbs.isEmpty()) {
				continue;
			}
			for (ServerPlayer viewer : entry.getKey().players()) {
				List<ZFighterNet.Orb> seen = new ArrayList<>();
				for (ZFighterNet.Orb orb : orbs) {
					if (viewer.distanceToSqr(orb.x(), orb.y(), orb.z()) <= ORB_SEEN * ORB_SEEN) {
						seen.add(orb);
					}
				}
				if (!seen.isEmpty()) {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, new ZFighterNet.KiOrbs(seen));
				}
			}
			orbs.clear();
		}
	}

	/** One tick of a bolt's flight. False when it's spent. */
	private static boolean flyBolt(ServerPlayer owner, Bolt bolt) {
		ServerLevel level = bolt.level;
		if (bolt.kind == DISC) {
			return flyDisc(owner, bolt);
		}
		if (bolt.kind == HOMING) {
			seek(owner, bolt);
		}
		if (bolt.kind == SPIRIT && bolt.sinking > 0) {
			return sink(owner, bolt);
		}
		Vec3 from = bolt.pos;
		Vec3 to = from.add(bolt.velocity);
		// the Spirit Bomb is huge: it lands when its leading edge meets the ground, not its middle
		Vec3 lead = bolt.kind == SPIRIT ? bolt.velocity.normalize().scale(bolt.size * SPIRIT_LEAD) : Vec3.ZERO;
		BlockHitResult block = level.clip(new ClipContext(from.add(lead), to.add(lead), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation().subtract(lead);
		DustParticleOptions dust = new DustParticleOptions(bolt.color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : bolt.color,
			bolt.kind == BOMB || bolt.kind == SPIRIT ? 3.0F : 1.8F);
		double reach = 1.0 + bolt.size;
		EntityHitResult hit = bolt.kind == SPIRIT ? null : ProjectileUtil.getEntityHitResult(owner, from, end, new AABB(from, end).inflate(reach),
			e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner
				&& !bolt.struck.contains(e.getId()) && (bolt.kind != SPIRIT || ZFighterTechniques.foe(owner, living)),
			from.distanceToSqr(end));
		if (hit == null && bolt.size > 0.0 && bolt.kind != SPIRIT) {
			// a big ball catches what it brushes past, not just what's dead ahead
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(bolt.size),
				e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner && (bolt.kind != SPIRIT || ZFighterTechniques.foe(owner, e)))) {
				if (distanceToSegment(near.getBoundingBox().getCenter(), from, end) <= bolt.size + near.getBbWidth() * 0.5) {
					hit = new EntityHitResult(near, near.getBoundingBox().getCenter());
					break;
				}
			}
		}
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			if (bolt.kind == BOMB || bolt.kind == SPIRIT) {
				burst(owner, bolt, hit.getLocation());
				return false;
			}
			target.setInvulnerableTime(0);
			target.hurtServer(level, source(owner), bolt.damage);
			push(target, bolt.velocity, 0.6, 0.2);
			pop(level, hit.getLocation(), dust);
			return false;
		}
		if (block.getType() != HitResult.Type.MISS) {
			if (bolt.kind == SPIRIT) {
				// it never bursts on touching: it embeds itself in whatever it met first, grinding in
				bolt.sinking = 1;
				bolt.pos = end;
				bolt.velocity = bolt.velocity.normalize().scale(Math.max(SPIRIT_SINK_SPEED, bolt.size * SPIRIT_EMBED / SPIRIT_SINK_TICKS));
				ZFighterTechniques.spiritFx(level, bolt.fxId, end, bolt.size, 0.0F);
				return true;
			}
			if (bolt.kind == BOMB) {
				burst(owner, bolt, block.getLocation());
			} else {
				pop(level, block.getLocation(), dust);
			}
			return false;
		}
		draw(level, bolt, from, dust);
		if (bolt.kind == SPIRIT) {
			ZFighterTechniques.spiritFx(level, bolt.fxId, to, bolt.size, 0.0F);
		}
		bolt.pos = to;
		if (bolt.kind == SPIRIT && ++bolt.age < SPIRIT_FALL_LIMIT) {
			if (bolt.age == SPIRIT_FALL_AFTER) {
				// thrown at the sky: it comes down to earth instead of bursting in the air
				bolt.velocity = new Vec3(0.0, -SPIRIT_FALL_SPEED, 0.0);
			}
			return true;
		}
		if (bolt.kind == SPIRIT || ++bolt.age >= bolt.life) {
			if (bolt.kind == BOMB || bolt.kind == SPIRIT) {
				burst(owner, bolt, to);
			}
			return false;
		}
		return true;
	}

	/**
	 * One tick of a Ki Disc: it cuts every creature it passes and every block in a flat slice along its path (they break
	 * and drop), and stops only at a block it can't cut ({@link #discCuts}) or once it has flown {@link #DISC_RANGE}.
	 * Fluids don't stop it.
	 */
	private static boolean flyDisc(ServerPlayer owner, Bolt bolt) {
		ServerLevel level = bolt.level;
		double speed = bolt.velocity.length();
		if (speed < 1.0E-6) {
			return false;
		}
		Vec3 dir = bolt.velocity.scale(1.0 / speed);
		Vec3 from = bolt.pos;
		double step = Math.min(speed, DISC_RANGE - bolt.flown);
		// the blocks in its slice, nearest first: the first it can't cut stops it there
		double reach = step;
		boolean stopped = false;
		List<BlockPos> cuts = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		int along = Math.max(1, Mth.ceil(step / DISC_CUT_STEP));
		int across = Math.max(1, Mth.ceil(DISC_HALF_WIDTH / DISC_CUT_STEP));
		scan:
		for (int i = 0; i <= along; i++) {
			double d = step * i / along;
			Vec3 middle = from.add(dir.scale(d));
			for (int j = -across; j <= across; j++) {
				BlockPos pos = BlockPos.containing(middle.add(bolt.side.scale(DISC_HALF_WIDTH * j / across)));
				if (!seen.add(pos)) {
					continue;
				}
				if (!level.isLoaded(pos)) {
					reach = d;
					stopped = true;
					break scan;
				}
				BlockState state = level.getBlockState(pos);
				if (state.isAir() || state.liquid() || state.is(Blocks.BUBBLE_COLUMN)) {
					continue;
				}
				if (!discCuts(owner, level, pos, state)) {
					reach = d;
					stopped = true;
					break scan;
				}
				if (cuts.size() >= DISC_MAX_CUTS) {
					// enough for one tick: it goes on from here next tick
					reach = d;
					break scan;
				}
				cuts.add(pos.immutable());
			}
		}
		ZFighterGrowth.cutting = true;
		try {
			for (BlockPos pos : cuts) {
				BlockState state = level.getBlockState(pos);
				// breaks it as the fighter would: the break's particles and sound, and what it drops
				level.destroyBlock(pos, true, owner);
				PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, owner, pos, state, null);
			}
		} finally {
			ZFighterGrowth.cutting = false;
		}
		if (!cuts.isEmpty()) {
			level.playSound(null, from.x, from.y, from.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.8F);
		}
		Vec3 end = from.add(dir.scale(reach));
		DustParticleOptions dust = new DustParticleOptions(bolt.color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : bolt.color, 1.8F);
		// every creature its edge passes through, once each
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(DISC_HALF_WIDTH + 1.0),
			e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != owner && !bolt.struck.contains(e.getId()))) {
			AABB box = target.getBoundingBox().inflate(DISC_HALF_WIDTH, DISC_CUT_STEP, DISC_HALF_WIDTH);
			java.util.Optional<Vec3> at = box.clip(from, end);
			if (at.isEmpty() && !box.contains(from)) {
				continue;
			}
			bolt.struck.add(target.getId());
			target.setInvulnerableTime(0);
			target.hurtServer(level, source(owner), bolt.damage);
			push(target, bolt.velocity, 0.6, 0.2);
			pop(level, at.orElse(from), dust);
		}
		if (stopped) {
			// a block it can't cut: it shatters on it
			pop(level, end, dust);
			level.playSound(null, end.x, end.y, end.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
			return false;
		}
		draw(level, bolt, from, dust);
		bolt.pos = end;
		bolt.flown += reach;
		bolt.age++;
		return bolt.flown < DISC_RANGE - 1.0E-6;
	}

	/**
	 * Whether the disc cuts through this block: not as hard as obsidian, not unbreakable, holding no block entity (chests,
	 * furnaces), and the fighter may break it here (his game mode, spawn protection, claims through Fabric's break event).
	 */
	private static boolean discCuts(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0F && hardness < DISC_STOP_HARDNESS && !state.hasBlockEntity() && player.mayBuild() && player.mayInteract(level, pos)
			&& !player.blockActionRestricted(level, pos, player.gameMode())
			&& PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, null);
	}

	private static void draw(ServerLevel level, Bolt bolt, Vec3 from, DustParticleOptions dust) {
		Vec3 to = from.add(bolt.velocity);
		int orbColor = bolt.color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : bolt.color;
		switch (bolt.kind) {
			case DISC -> {
				// drawn by the clients as a flat disc spinning level (ZFighterOrbs), its cutting width across, sparks off its edge
				discOrb(level, bolt.orbId, to, DISC_HALF_WIDTH * 2.0, orbColor);
				if (bolt.age % DISC_WHIR_EVERY == 0) {
					level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4F, DISC_WHIR_PITCH);
				}
				double spin = bolt.age * 0.9;
				for (int i = 0; i < 2; i++) {
					double a = spin + i * Math.PI;
					level.sendParticles(dust, true, false, to.x + Math.cos(a) * DISC_HALF_WIDTH, to.y, to.z + Math.sin(a) * DISC_HALF_WIDTH, 1, 0.0, 0.0,
						0.0, 0.0);
				}
			}
			case BOMB -> {
				orb(level, bolt.orbId, to, bolt.orbSize > 0 ? bolt.orbSize : bolt.size * 2.0, orbColor);
				if (bolt.age % 2 == 0) {
					level.sendParticles(dust, true, false, from.x, from.y, from.z, 1, bolt.size * 0.3, bolt.size * 0.3, bolt.size * 0.3, 0.0);
				}
				if (bolt.age % 6 == 0) {
					level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F + (float)bolt.size * 0.3F, 0.6F);
				}
			}
			case SPIRIT -> {
				// drawn by the clients as a ball of light (ZFighterSpirit); a few motes shed behind it
				RandomSource random = level.getRandom();
				int count = (int)Math.min(8, 2 + bolt.size);
				for (int i = 0; i < count; i++) {
					Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
					Vec3 at = to.add(dir.scale(bolt.size * (0.6 + random.nextDouble() * 0.4)));
					level.sendParticles(dust, true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
				level.sendParticles(new DustParticleOptions(0xFFFFFF, 3.0F), true, false, to.x, to.y, to.z, 2, bolt.size * 0.2, bolt.size * 0.2,
					bolt.size * 0.2, 0.0);
				if (bolt.age % 6 == 0) {
					level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F + (float)bolt.size * 0.3F, 0.6F);
				}
			}
			default -> {
				// a ki orb, and a thin trail of one mote every other tick
				orb(level, bolt.orbId, to, bolt.orbSize > 0 ? bolt.orbSize : bolt.kind == HOMING ? 0.3 : 0.45, orbColor);
				if (bolt.age % 2 == 0) {
					level.sendParticles(dust, true, false, from.x, from.y, from.z, 1, 0.03, 0.03, 0.03, 0.0);
				}
			}
		}
	}

	/** A seeking bolt turns towards the nearest foe ahead of it. */
	private static void seek(ServerPlayer owner, Bolt bolt) {
		double speed = bolt.velocity.length();
		if (speed < 1.0E-4) {
			return;
		}
		Vec3 heading = bolt.velocity.scale(1.0 / speed);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity near : bolt.level.getEntitiesOfClass(LivingEntity.class, new AABB(bolt.pos, bolt.pos).inflate(VOLLEY_SEEK),
			e -> e.isAlive() && !e.isSpectator() && e != owner && !(e instanceof ArmorStand) && ZFighterTechniques.foe(owner, e))) {
			Vec3 toward = near.getBoundingBox().getCenter().subtract(bolt.pos);
			double distance = toward.lengthSqr();
			if (distance < bestDistance && toward.normalize().dot(heading) > 0.2) {
				best = near;
				bestDistance = distance;
			}
		}
		if (best != null) {
			Vec3 toward = best.getBoundingBox().getCenter().subtract(bolt.pos).normalize();
			bolt.velocity = heading.lerp(toward, VOLLEY_TURN).normalize().scale(speed);
		}
	}

	/** A Ki Bomb or the Spirit Bomb lands: everything near is hurt (the Spirit Bomb hurts only foes and shoves the rest aside). */
	private static void burst(ServerPlayer owner, Bolt bolt, Vec3 at) {
		ServerLevel level = bolt.level;
		DamageSource source = source(owner);
		boolean spirit = bolt.kind == SPIRIT;
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(bolt.radius),
			e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
			Vec3 away = near.getBoundingBox().getCenter().subtract(at);
			double distance = away.length();
			if (distance > bolt.radius) {
				continue;
			}
			double falloff = 1.0 - 0.5 * distance / bolt.radius;
			if (spirit && !ZFighterTechniques.foe(owner, near)) {
				push(near, away, 0.8 * falloff, 0.3);
				continue;
			}
			near.setInvulnerableTime(0);
			near.hurtServer(level, source, (float)(bolt.damage * falloff));
			push(near, away, (spirit ? 2.0 : 1.2) * falloff, 0.5);
		}
		level.explode(owner, source, GROUND_ONLY, at.x, at.y, at.z, bolt.burst, false,
			bolt.griefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
		if (spirit) {
			// and a ring of blasts round it, so the crater is as wide as the bomb was
			for (int i = 0; i < SPIRIT_RING; i++) {
				double a = i * Math.PI * 2.0 / SPIRIT_RING;
				double r = bolt.size * SPIRIT_RING_AT;
				level.explode(owner, source, GROUND_ONLY, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, bolt.burst * SPIRIT_RING_POWER, false,
					bolt.griefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
			}
			for (int i = 0; i < SPIRIT_OUTER_RING; i++) {
				double a = (i + 0.5) * Math.PI * 2.0 / SPIRIT_OUTER_RING;
				double r = bolt.size * SPIRIT_OUTER_AT;
				level.explode(owner, source, GROUND_ONLY, at.x + Math.cos(a) * r, at.y + bolt.size * 0.3, at.z + Math.sin(a) * r,
					bolt.burst * SPIRIT_OUTER_POWER, false, bolt.griefing ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
			}
		}
		DustParticleOptions dust = new DustParticleOptions(bolt.color, 3.0F);
		level.sendParticles(dust, true, false, at.x, at.y, at.z, (int)(30 + bolt.radius * 10), bolt.radius * 0.4, bolt.radius * 0.4,
			bolt.radius * 0.4, 0.3);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, false, at.x, at.y, at.z, spirit ? 3 : 1, bolt.radius * 0.2, 0.2,
			bolt.radius * 0.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, spirit ? 4.0F : 1.5F, spirit ? 0.5F : 0.9F);
		if (spirit) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 3.0F, 0.6F);
			nova(level, at, Math.max(2.0, bolt.size));
		}
	}

	/** The Spirit Bomb bursts like a star: a sea of blue motes flung out, and cracks of energy racing away from it. */
	private static void nova(ServerLevel level, Vec3 at, double size) {
		RandomSource random = level.getRandom();
		DustParticleOptions blue = new DustParticleOptions(NOVA_BLUE, 4.0F);
		DustParticleOptions pale = new DustParticleOptions(NOVA_PALE, 3.0F);
		for (int i = 0; i < NOVA_MOTES; i++) {
			Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
			double speed = NOVA_SPEED * size * (0.4 + random.nextDouble() * 0.6);
			Vec3 from = at.add(dir.scale(size * 0.2));
			level.sendParticles(random.nextInt(3) == 0 ? pale : blue, true, false, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, speed);
			if (i % 5 == 0) {
				level.sendParticles(ParticleTypes.END_ROD, true, false, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z, Math.min(1.2, speed * 0.06));
			}
		}
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | NOVA_PALE), true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		Vec3[] headings = new Vec3[NOVA_CRACKS];
		Vec3[] tips = new Vec3[NOVA_CRACKS];
		for (int i = 0; i < NOVA_CRACKS; i++) {
			// mostly outward and upward, as from a blast in the ground
			headings[i] = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.7, random.nextGaussian()).normalize();
			tips[i] = at;
		}
		NOVAS.add(new Nova(level, at, size, headings, tips, new int[] {0}));
	}

	/** One tick of the cracks racing out: each runs on a jagged step, drawn as a line of pale-blue light. Done: true. */
	private static boolean tickNova(Nova nova) {
		ServerLevel level = nova.level();
		RandomSource random = level.getRandom();
		int age = ++nova.age()[0];
		float width = 2.2F * (1.0F - age / (float)(NOVA_TICKS + 2));
		DustParticleOptions crack = new DustParticleOptions(age % 2 == 0 ? NOVA_PALE : NOVA_BLUE, Math.max(0.6F, width));
		double step = nova.size() * NOVA_STEP;
		for (int i = 0; i < nova.tips().length; i++) {
			Vec3 from = nova.tips()[i];
			Vec3 jag = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.35);
			Vec3 to = from.add(nova.headings()[i].add(jag).normalize().scale(step));
			int dots = Math.max(3, (int)(step * 3.0));
			for (int k = 0; k < dots; k++) {
				Vec3 p = from.lerp(to, k / (double)dots);
				level.sendParticles(crack, true, false, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, to.x, to.y, to.z, 2, 0.1, 0.1, 0.1, 0.15);
			if (random.nextInt(6) == 0) {
				// a crack forks
				nova.headings()[i] = nova.headings()[i].add(jag.scale(1.5)).normalize();
			}
			nova.tips()[i] = to;
		}
		if (age % 4 == 1) {
			level.playSound(null, nova.at().x, nova.at().y, nova.at().z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 2.5F, 0.7F + age * 0.05F);
		}
		return age >= NOVA_TICKS;
	}

	/** The Spirit Bomb grinding into the ground: slow, the earth shaking and spraying up round it, then the blast. */
	private static boolean sink(ServerPlayer owner, Bolt bolt) {
		ServerLevel level = bolt.level;
		bolt.pos = bolt.pos.add(bolt.velocity);
		Vec3 at = bolt.pos;
		// brighter and brighter till it goes
		float glow = Mth.clamp(bolt.sinking / (float)SPIRIT_SINK_TICKS, 0.0F, 1.0F);
		ZFighterTechniques.spiritFx(level, bolt.fxId, at, bolt.size, glow);
		RandomSource random = level.getRandom();
		int flashes = (int)(2 + glow * glow * 16);
		level.sendParticles(ParticleTypes.END_ROD, true, false, at.x, at.y, at.z, flashes, bolt.size * 0.5, bolt.size * 0.5, bolt.size * 0.5,
			0.1 + glow * 0.4);
		if (glow > 0.6F) {
			level.sendParticles(new DustParticleOptions(0xFFFFFF, 4.0F), true, false, at.x, at.y + bolt.size * 0.5, at.z, 1, bolt.size * 0.3, 0.2, bolt.size * 0.3,
				0.0);
		}
		for (int i = 0; i < 12; i++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			double r = bolt.size * (0.5 + random.nextDouble() * 0.6);
			BlockPos ground = BlockPos.containing(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
			ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground).below();
			BlockState state = level.getBlockState(ground);
			if (!state.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, false, ground.getX() + 0.5, ground.getY() + 1.2,
					ground.getZ() + 0.5, 6, 0.4, 0.6, 0.4, 0.4);
			}
		}
		if (bolt.sinking % Math.max(2, 6 - (int)(glow * 4)) == 1) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 4.0F, 0.5F + glow * 0.6F);
			com.jaymospowers.core.Shakes.around(level, at, 32.0 + bolt.size * 4.0, 0.6F + 1.8F * glow);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.4F);
		}
		if (++bolt.sinking >= SPIRIT_SINK_TICKS) {
			burst(owner, bolt, at);
			return false;
		}
		return true;
	}

	private static void pop(ServerLevel level, Vec3 at, DustParticleOptions dust) {
		level.sendParticles(dust, true, false, at.x, at.y, at.z, 5, 0.25, 0.25, 0.25, 0.1);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.3);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35F, 1.9F);
	}

	// ---------------------------------------------------------------- Ki Disc, Homing Volley

	/** Where his palm is: the eye, a little ahead, right and down. */
	private static Vec3 palm(ServerPlayer player, Vec3 look) {
		return palm(player, look, false);
	}

	/** His right palm, or his left. */
	private static Vec3 palm(ServerPlayer player, Vec3 look, boolean left) {
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.ZERO : right.normalize();
		return player.getEyePosition().add(look.scale(0.8)).add(right.scale(left ? -0.3 : 0.3)).add(0.0, -0.25, 0.0);
	}

	/**
	 * The Ki Disc leaves his hand at {@code from}, flying along {@code dir} (the ki was paid when the wind-up began,
	 * {@code ZFighterDisc}).
	 */
	static void throwDisc(ServerPlayer player, ZFighterPower.Live live, Vec3 from, Vec3 dir) {
		ServerLevel level = player.level();
		Bolt bolt = new Bolt(level, player.getUUID(), from, dir.scale(DISC_SPEED), DISC_DAMAGE * ZFighterPower.kiPower(player, live),
			formColor(live), DISC, Mth.ceil(DISC_RANGE / DISC_SPEED));
		// flat and level: across it is his right, whichever way he looks (straight down too)
		bolt.side = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
		BOLTS.add(bolt);
		exhaust(player, BLAST_EXHAUSTION);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.2F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 2.0F);
	}

	/** A Ki Disc {@code size} blocks across at {@code at} this tick, drawn flat and spinning (in the hand or in flight). */
	static void discOrb(ServerLevel level, int id, Vec3 at, double size, int color) {
		ORBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new ZFighterNet.Orb(id, at.x, at.y, at.z, (float)size,
			color & 0xFFFFFF | ZFighterNet.Orb.DISC));
	}

	/** The disc's colour: the blast's gold in base form, the form's hue otherwise. */
	static int discColor(ZFighterPower.Live live) {
		int color = formColor(live);
		return color == ZFighterPower.FORM_COLOR[0] ? BLAST_COLOR : color;
	}

	static boolean volley(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, VOLLEY_KI)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		RandomSource random = level.getRandom();
		float damage = VOLLEY_DAMAGE * ZFighterPower.kiPower(player, live);
		for (int i = 0; i < VOLLEY_BOLTS; i++) {
			Vec3 spread = look.add((random.nextDouble() - 0.5) * 0.7, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.7).normalize();
			BOLTS.add(new Bolt(level, player.getUUID(), palm(player, look), spread.scale(VOLLEY_SPEED), damage, formColor(live),
				HOMING, VOLLEY_LIFE));
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		exhaust(player, BLAST_EXHAUSTION * 2.0F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	/** The Spirit Bomb, thrown from where it gathered. */
	static void throwSpirit(ServerPlayer player, Vec3 from, Vec3 velocity, float damage, double size, double radius, float burst) {
		Bolt bolt = new Bolt(player.level(), player.getUUID(), from, velocity, damage, ZFighterTechniques.SPIRIT_COLOR, SPIRIT, 200);
		bolt.size = size;
		bolt.radius = radius;
		bolt.burst = burst;
		bolt.griefing = true;
		bolt.fxId = player.getId();
		BOLTS.add(bolt);
	}

	private static void exhaust(ServerPlayer player, float amount) {
		if (!player.isCreative()) {
			player.causeFoodExhaustion(amount);
		}
	}

	// ---------------------------------------------------------------- Ki Wave

	static void startWave(ServerPlayer player, ZFighterPower.Live live) {
		if (com.jaymospowers.core.BeamClash.resting(player)) {
			return;
		}
		float min = ZFighterTraining.of(player).charged() == ZFighterTraining.KI_BOMB ? BOMB_MIN_KI : WAVE_MIN_KI * signature(live).ki();
		if (live.ki < min && !player.isCreative() && !live.raging()) {
			ZFighterPower.spend(player, live, min);
			return;
		}
		live.waveFrom = player.level().getGameTime();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.8F);
	}

	/** The charge (0-1) of a wave gathered since {@code from}, held back to what his ki can pay for. */
	private static float charge(ServerPlayer player, ZFighterPower.Live live, long now) {
		boolean bomb = ZFighterTraining.of(player).charged() == ZFighterTraining.KI_BOMB;
		Signature sig = signature(live);
		float charge = Mth.clamp((now - live.waveFrom) / (float)(bomb ? WAVE_FULL : sig.full()), 0.0F, 1.0F);
		if (!player.isCreative() && !live.raging()) {
			float min = bomb ? BOMB_MIN_KI : WAVE_MIN_KI * sig.ki();
			float affordable = (live.ki - min) / ((bomb ? BOMB_MAX_KI : WAVE_MAX_KI * sig.ki()) - min);
			charge = Math.min(charge, Mth.clamp(affordable, 0.0F, 1.0F));
		}
		return charge;
	}

	/** While V is held: ki gathers between the cupped hands. */
	static void tickWave(ServerPlayer player, ZFighterPower.Live live) {
		if (live.waveFrom < 0) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		float charge = charge(player, live, now);
		Vec3 look = player.getViewVector(1.0F);
		Vec3 hands = player.getEyePosition().add(look.scale(0.7)).add(0.0, -0.45, 0.0);
		RandomSource random = level.getRandom();
		Signature sig = signature(live);
		if (sig.style() == NEEDLE && ZFighterTraining.of(player).charged() != ZFighterTraining.KI_BOMB) {
			// one finger, pointed: the ki gathers at its tip
			hands = player.getEyePosition().add(look.scale(0.9)).add(0.0, -0.25, 0.0);
		}
		DustParticleOptions dust = new DustParticleOptions(live.superOn() ? formColor(live) : sig.color(),
			(0.8F + charge * 1.2F) * (sig.style() == NEEDLE ? 0.5F : 1.0F));
		// the ki gathers as an orb between the hands, growing with the charge; now and then a mote drawn in
		boolean needle = sig.style() == NEEDLE && ZFighterTraining.of(player).charged() != ZFighterTraining.KI_BOMB;
		orb(level, -(player.getId() * 4 + 3), hands, needle ? 0.12 + 0.18 * charge : 0.25 + 0.75 * charge,
			live.superOn() ? formColor(live) : sig.color());
		if (random.nextInt(2) == 0) {
			double ox = (random.nextDouble() - 0.5) * 1.6;
			double oy = (random.nextDouble() - 0.5) * 1.6;
			double oz = (random.nextDouble() - 0.5) * 1.6;
			level.sendParticles(dust, true, false, hands.x + ox, hands.y + oy, hands.z + oz, 0, -ox, -oy, -oz, 0.12);
		}
		if (charge >= 1.0F && now % 3 == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, hands.x, hands.y, hands.z, 2, 0.2, 0.2, 0.2, 0.2);
		}
		if ((now - live.waveFrom) % 10 == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.0F,
				0.8F + charge);
		}
	}

	/** V let go: the wave fires (true), or there wasn't one (false). */
	static boolean releaseWave(ServerPlayer player, ZFighterPower.Live live) {
		if (live.waveFrom < 0) {
			return false;
		}
		ServerLevel level = player.level();
		float charge = charge(player, live, level.getGameTime());
		live.waveFrom = -1L;
		boolean bomb = ZFighterTraining.of(player).charged() == ZFighterTraining.KI_BOMB;
		float cost = bomb ? Mth.lerp(charge, BOMB_MIN_KI, BOMB_MAX_KI) : Mth.lerp(charge, WAVE_MIN_KI, WAVE_MAX_KI) * signature(live).ki();
		if (!ZFighterPower.spend(player, live, cost)) {
			return false;
		}
		if (bomb) {
			fireBomb(player, live, charge);
		} else {
			fireWave(player, live, charge);
		}
		return true;
	}

	private static void fireBomb(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		double size = Mth.lerp(charge, BOMB_SIZE_MIN, BOMB_SIZE_MAX);
		Vec3 from = player.getEyePosition().add(look.scale(1.0 + size)).add(0.0, -0.2, 0.0);
		Bolt bolt = new Bolt(level, player.getUUID(), from, look.scale(BOMB_SPEED),
			Mth.lerp(charge, BOMB_DAMAGE_MIN, BOMB_DAMAGE_MAX) * ZFighterPower.kiPower(player, live),
			live.superOn() ? formColor(live) : WAVE_COLOR, BOMB, BOMB_LIFE);
		bolt.size = size;
		bolt.radius = 2.0 + size * 1.5;
		bolt.burst = Mth.lerp(charge, BOMB_BLAST_MIN, BOMB_BLAST_MAX) * (live.superOn() ? 1.3F : 1.0F);
		bolt.griefing = live.superOn() && charge >= 1.0F;
		BOLTS.add(bolt);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
		exhaust(player, WAVE_EXHAUSTION * (1.0F + charge));
	}

	/** Where a wave leaves the cupped hands. */
	static Vec3 waveHands(ServerPlayer player, Vec3 look) {
		return player.getEyePosition().add(look.scale(0.7)).add(0.0, -0.3, 0.0);
	}

	/** Where the Great Ape's beam leaves his mouth. */
	static Vec3 apeMouth(ServerPlayer player, Vec3 look) {
		return player.getEyePosition().add(look.scale(player.getBbWidth())).add(0.0, -0.3 * player.getScale(), 0.0);
	}

	private static void fireWave(ServerPlayer player, ZFighterPower.Live live, float charge) {
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Signature sig = signature(live);
		Vec3 from = waveHands(player, look);
		double radius = Mth.lerp(charge, WAVE_RADIUS_MIN, WAVE_RADIUS_MAX) * sig.width();
		float kiPower = ZFighterPower.kiPower(player, live);
		float damage = Mth.lerp(charge, WAVE_DAMAGE_MIN, WAVE_DAMAGE_MAX) * sig.damage() * kiPower;
		boolean super_ = live.superOn();
		float power = Mth.lerp(charge, WAVE_BLAST_MIN, WAVE_BLAST_MAX) * sig.blast() * (super_ ? 1.3F : 1.0F);
		// a full wave from a Super tears the ground (as a mob would: mob griefing decides); anything less only hurts
		Level.ExplosionInteraction interaction = super_ && charge >= 1.0F ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE;
		// the head travels out from the hands; the Spiral Lance bores through whatever stands in its way (ZFighterWaveBeam)
		float pitch = switch (sig.style()) {
			case NEEDLE -> 1.9F;
			case SPIRAL -> 1.4F;
			default -> sig.blast() > 1.0F ? 0.6F : 1.0F;
		};
		ZFighterWaveBeam.fire(player, from, look, new ZFighterWaveBeam.Spec(sig.range(), sig.speed(), radius, damage,
			(0.8 + charge * 1.2) * (sig.style() == NEEDLE ? 0.3 : 1.0), sig.chill(), sig.pierces(), power, interaction, false,
			super_ ? formColor(live) : sig.color(), sig.style(), ZFighterWaveBeam.strength(charge, kiPower), false, pitch));
		switch (sig.style()) {
			case NEEDLE -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 1.8F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.6F, 2.0F);
			}
			case SPIRAL -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.2F, 1.3F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.8F);
			}
			default -> {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2F,
					sig.blast() > 1.0F ? 1.0F : 1.4F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
			}
		}
		com.jaymospowers.core.Shakes.send(player, 0.5F + 0.9F * charge, from);
		if (!player.onGround()) {
			// the recoil pushes a fighter in the air back
			player.push(look.scale(-0.4 * charge));
			player.syncVelocity = true;
		}
		if (!player.isCreative()) {
			player.causeFoodExhaustion(WAVE_EXHAUSTION * (1.0F + charge));
		}
	}

	/** A bolt's or beam's colour: the base blast's, or the form's in his lineage's hue. */
	private static int formColor(ZFighterPower.Live live) {
		return live.form == 0 ? ZFighterPower.FORM_COLOR[0] : ZFighterLineage.color(live.lineage, live.form);
	}

	/** V as the Great Ape: a beam from the mouth. No ki, no charge: it's the moon's strength. */
	static boolean apeBeam(ServerPlayer player, ZFighterPower.Live live) {
		if (com.jaymospowers.core.BeamClash.resting(player)) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 from = apeMouth(player, look);
		float damage = APE_BEAM_DAMAGE * ZFighterPower.kiPower(player, live);
		ZFighterWaveBeam.fire(player, from, look, new ZFighterWaveBeam.Spec(APE_BEAM_RANGE, APE_BEAM_SPEED, APE_BEAM_RADIUS, damage, 1.2, 0, false,
			APE_BEAM_BLAST, Level.ExplosionInteraction.MOB, true, APE_BEAM_COLOR, WAVE, ZFighterWaveBeam.APE_CLASH_STRENGTH, true, 0.5F));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 2.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.7F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), 24.0, 1.8F);
		return true;
	}

	/** A beam drawn for everyone near and nothing else (the rogue fighters' blasts; they do their own hurting). */
	static void showBeam(ServerLevel level, Vec3 from, Vec3 to, float radius, int color) {
		ZFighterNet.BeamFx fx = new ZFighterNet.BeamFx(from.x, from.y, from.z, to.x, to.y, to.z, radius, color, WAVE, BEAM_TICKS);
		Vec3 middle = from.lerp(to, 0.5);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.position().distanceToSqr(middle) <= BEAM_SEEN * BEAM_SEEN) {
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, fx);
			}
		}
	}

	static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double lengthSqr = ab.lengthSqr();
		double t = lengthSqr < 1.0E-6 ? 0.0 : Mth.clamp(point.subtract(a).dot(ab) / lengthSqr, 0.0, 1.0);
		return point.distanceTo(a.add(ab.scale(t)));
	}

	// ---------------------------------------------------------------- Flash Step

	static boolean step(ServerPlayer player, ZFighterPower.Live live) {
		if (!player.isCreative() && live.ki < STEP_KI) {
			return ZFighterPower.spend(player, live, STEP_KI);
		}
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 start = player.position();
		Vec3 dest = null;
		float yaw = player.getYRot();
		float pitch = player.getXRot();

		Vec3 reach = eye.add(look.scale(STEP_TARGET_RANGE));
		EntityHitResult aimed = ProjectileUtil.getEntityHitResult(player, eye, reach, player.getBoundingBox().expandTowards(look.scale(STEP_TARGET_RANGE))
			.inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, STEP_TARGET_RANGE * STEP_TARGET_RANGE);
		if (aimed != null && aimed.getEntity() instanceof LivingEntity target && player.hasLineOfSight(target)) {
			// behind it, at its back
			float facing = target.getYRot() * Mth.DEG_TO_RAD;
			Vec3 forward = new Vec3(-Mth.sin(facing), 0.0, Mth.cos(facing));
			double gap = target.getBbWidth() * 0.5 + 0.9;
			for (Vec3 candidate : new Vec3[] {target.position().subtract(forward.scale(gap)),
				target.position().add(target.position().subtract(start).multiply(1.0, 0.0, 1.0).normalize().scale(gap))}) {
				if (free(player, candidate)) {
					dest = candidate;
					break;
				}
			}
			if (dest != null) {
				Vec3 toTarget = target.getEyePosition().subtract(dest.add(0.0, player.getEyeHeight(), 0.0));
				yaw = (float)(Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F;
				pitch = (float)(-Mth.atan2(toTarget.y, Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z)) * Mth.RAD_TO_DEG);
			}
		}
		if (dest == null) {
			// the furthest free spot along the look, up to STEP_RANGE
			for (double d = 0.25; d <= STEP_RANGE; d += 0.25) {
				Vec3 candidate = start.add(look.scale(d));
				if (!free(player, candidate)) {
					break;
				}
				dest = candidate;
			}
			if (dest == null || dest.distanceToSqr(start) < 1.0) {
				return false;
			}
		}
		if (!ZFighterPower.spend(player, live, STEP_KI)) {
			return false;
		}
		// the afterimage: a column of fading motes where he stood
		DustParticleOptions image = ZFighterPower.dust(live);
		for (double h = 0.1; h < player.getBbHeight(); h += 0.3) {
			level.sendParticles(image, true, false, start.x, start.y + h, start.z, 3, 0.15, 0.05, 0.15, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, start.x, start.y + 1.0, start.z, 6, 0.2, 0.4, 0.2, 0.02);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.8F);
		live.stepUntil = level.getGameTime() + 10;
		player.teleportTo(level, dest.x, dest.y, dest.z, Set.of(), yaw, pitch, false);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.CLOUD, dest.x, dest.y + 0.5, dest.z, 6, 0.2, 0.2, 0.2, 0.02);
		level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.6F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(STEP_EXHAUSTION);
		}
		return true;
	}

	private static boolean free(ServerPlayer player, Vec3 feet) {
		return player.level().noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())));
	}

	// ---------------------------------------------------------------- shared

	/** Pushes {@code entity} along {@code dir} (horizontal strength, then up), less for knockback-resistant creatures. */
	static void push(LivingEntity entity, Vec3 dir, double horizontal, double up) {
		double resist = 1.0 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		if (resist <= 0.0) {
			return;
		}
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
		flat = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
		entity.push(flat.x * horizontal * resist, up * resist, flat.z * horizontal * resist);
		entity.syncVelocity = true;
	}
}
