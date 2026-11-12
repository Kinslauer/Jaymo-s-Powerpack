package com.jaymospowers.power.xenoform.minds;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.GreatFoes;
import com.jaymospowers.power.xenoform.XenoMinds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Lumen's shapes of light. Lumen keeps a charge of light ({@link #MAX}) that tops itself up, faster in bright light, and that
 * Mend tops up too. Z (a tap) turns the dial of shapes (sneaking, back); sneak + V makes the shape in hand:
 * <ul>
 * <li>Beam: held; it burns what it touches while a heat gauge climbs, and when the gauge is full the beam fails until it cools.</li>
 * <li>Dome: a shell of light round the spot you stand, {@link #DOME_TICKS} ticks: it eats shots, shoves creatures off it and
 * strengthens the friends inside.</li>
 * <li>Hand: a hand of light grips the creature you aim at and drags it to your feet.</li>
 * <li>Missiles: a volley of {@link #VOLLEY} homing bolts.</li>
 * <li>Block: a great block of light thrown in an arc that bursts where it lands.</li>
 * <li>Wall, Bridge, Cage: short-lived blocks of light you can stand on ({@link LightBlocks}).</li>
 * </ul>
 * Every shape is drawn through {@link MagicFx} (beam, orb, ring, blast, sigil, swing).
 */
public final class LumenShapes {
	// ---- tuning ----
	public static final int MAX = 100;
	public static final int COLOR = 0x9ADCF4, HEAT_COLOR = 0xFFE9A0;
	/** The charge that comes back each tick; and extra in bright light (a brightness of {@link #BRIGHT} or more). */
	public static final float REGEN = 0.1F, REGEN_BRIGHT = 0.25F;
	public static final int BRIGHT = 13;
	/** Mend's gift of charge. */
	public static final float MEND_TOPUP = 25.0F;
	public static final int CAST_GAP = 8;
	/** The beam: reach, damage and how often, charge and heat per tick, the heat it fails at and cools back to, and the cooling per tick. */
	public static final double BEAM_REACH = 24.0;
	public static final float BEAM_DAMAGE = 2.5F, BEAM_WIDTH = 0.4F, BEAM_CHARGE = 0.3F, BEAM_HEAT = 1.7F, HEAT_COOL = 1.0F;
	public static final int BEAM_EVERY = 5, HEAT_FULL = 100, HEAT_BACK = 35;
	/** The dome: radius, life, the shell's thickness and the push on what touches it. */
	public static final double DOME_RADIUS = 4.5, DOME_SHELL = 1.1, DOME_PUSH = 0.5;
	public static final int DOME_TICKS = 100;
	/** The hand: reach, ticks it drags for, its pull per tick and the grip's damage and slowness at the end. */
	public static final double HAND_REACH = 20.0, HAND_PULL = 0.35, HAND_MAX = 1.1, HAND_STOP = 2.2;
	public static final int HAND_TICKS = 14;
	public static final float HAND_DAMAGE = 3.0F;
	/** The volley: bolts, ticks between them, reach for a target, speed, how hard they turn (share a tick), life, burst radius and damage. */
	public static final int VOLLEY = 6, VOLLEY_GAP = 2, MISSILE_LIFE = 40;
	public static final double MISSILE_REACH = 32.0, MISSILE_SPEED = 1.1, MISSILE_TURN = 0.25, MISSILE_RADIUS = 1.8;
	public static final float MISSILE_DAMAGE = 5.0F;
	/** The great block: speed, gravity, life, burst radius, damage, shove. */
	public static final double BLOCK_SPEED = 1.1, BLOCK_GRAVITY = 0.025, BLOCK_RADIUS = 4.5, BLOCK_OUT = 1.3, BLOCK_UP = 0.6;
	public static final int BLOCK_LIFE = 70;
	public static final float BLOCK_DAMAGE = 14.0F;
	public static final double CAGE_REACH = 16.0;

	/** The dial of shapes, in order, and what each costs to make (the beam's is paid as it burns). */
	public enum Shape {
		BEAM("beam", 0), DOME("dome", 35), HAND("hand", 30), MISSILES("missiles", 30), BLOCK("block", 55), WALL("wall", 40), BRIDGE("bridge", 25),
		CAGE("cage", 45);

		public final String key;
		public final int cost;

		Shape(String key, int cost) {
			this.key = key;
			this.cost = cost;
		}

		public Component title() {
			return Component.translatable("xenoform_minds.shape." + this.key);
		}
	}

	private static final class Bolt {
		Vec3 pos, vel;
		int target = -1, life;
		boolean block;
	}

	private static final class State {
		float charge = MAX, heat;
		int shape;
		boolean over, beaming;
		long domeUntil;
		@Nullable Vec3 domeAt;
		int handTarget = -1, handTicks;
		int volley, volleyTarget = -1;
		long volleyAt;
		final List<Bolt> bolts = new ArrayList<>();
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private LumenShapes() {
	}

	private static State state(ServerPlayer player) {
		return STATES.computeIfAbsent(player.getUUID(), id -> new State());
	}

	public static void forget(ServerPlayer player) {
		STATES.remove(player.getUUID());
		player.removeAttached(XenoMinds.LUMEN);
	}

	/** Z tapped: the next shape (or, sneaking, the one before). */
	public static void cycle(ServerPlayer player, boolean back) {
		State s = state(player);
		int count = Shape.values().length;
		s.shape = Math.floorMod(s.shape + (back ? -1 : 1), count);
		s.beaming = false;
		Shape shape = Shape.values()[s.shape];
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.lumen.shape", shape.title(), shape.cost).withStyle(ChatFormatting.AQUA));
		MindsKit.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.0F + 0.08F * s.shape);
		sync(player, s);
	}

	/** Mend (and anything else that feeds it) tops up the charge. */
	public static void topUp(ServerPlayer player, float amount) {
		State s = state(player);
		s.charge = Math.min(MAX, s.charge + amount);
	}

	/** Sneak + V let go: the beam goes out. */
	public static void letGo(ServerPlayer player) {
		State s = STATES.get(player.getUUID());
		if (s != null) {
			s.beaming = false;
		}
	}

	/** Sneak + V: make the shape in hand. */
	public static void cast(ServerPlayer player) {
		State s = state(player);
		Shape shape = Shape.values()[s.shape];
		ServerLevel level = player.level();
		if (shape == Shape.BEAM) {
			if (s.over) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.lumen.overheated").withStyle(ChatFormatting.GOLD));
			} else if (s.charge >= 5.0F) {
				s.beaming = true;
			}
			return;
		}
		if (!XenoMinds.ready(player, "lumen_cast")) {
			return;
		}
		if (s.charge < shape.cost) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.lumen.low").withStyle(ChatFormatting.GRAY));
			return;
		}
		boolean made = switch (shape) {
			case DOME -> dome(player, level, s);
			case HAND -> hand(player, s);
			case MISSILES -> missiles(player, s);
			case BLOCK -> block(player, s);
			case WALL -> LightBlocks.wall(player);
			case BRIDGE -> LightBlocks.bridge(player);
			case CAGE -> cage(player);
			default -> false;
		};
		if (made) {
			s.charge -= shape.cost;
			XenoMinds.cool(player, "lumen_cast", CAST_GAP);
			if (shape == Shape.WALL || shape == Shape.BRIDGE || shape == Shape.CAGE) {
				MindsKit.sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.1F);
				MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 2.0F, COLOR);
			}
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.lumen.no_room").withStyle(ChatFormatting.GRAY));
		}
	}

	private static boolean dome(ServerPlayer player, ServerLevel level, State s) {
		s.domeAt = player.position();
		s.domeUntil = level.getGameTime() + DOME_TICKS;
		MagicFx.blast(level, s.domeAt.add(0.0, 1.0, 0.0), (float)DOME_RADIUS * 0.8F, COLOR);
		MagicFx.sigil(level, s.domeAt.add(0.0, 0.05, 0.0), (float)DOME_RADIUS * 0.8F, COLOR, 20);
		MindsKit.sound(player, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.6F);
		return true;
	}

	private static boolean hand(ServerPlayer player, State s) {
		LivingEntity target = MindsKit.aimed(player, HAND_REACH, e -> true);
		if (target == null) {
			return false;
		}
		s.handTarget = target.getId();
		s.handTicks = HAND_TICKS;
		MindsKit.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.7F);
		return true;
	}

	private static boolean missiles(ServerPlayer player, State s) {
		LivingEntity target = MindsKit.aimed(player, MISSILE_REACH, e -> true);
		s.volley = VOLLEY;
		s.volleyAt = player.level().getGameTime();
		s.volleyTarget = target == null ? -1 : target.getId();
		return true;
	}

	private static boolean block(ServerPlayer player, State s) {
		Bolt b = new Bolt();
		Vec3 look = player.getLookAngle();
		b.pos = player.getEyePosition().add(look.scale(1.6));
		b.vel = look.scale(BLOCK_SPEED);
		b.life = BLOCK_LIFE;
		b.block = true;
		s.bolts.add(b);
		MindsKit.sound(player, SoundEvents.BEACON_POWER_SELECT, 1.4F, 0.6F);
		return true;
	}

	private static boolean cage(ServerPlayer player) {
		LivingEntity target = MindsKit.aimed(player, CAGE_REACH, e -> true);
		return target != null && LightBlocks.cage(player, target);
	}

	/** Every tick as Lumen. */
	public static void tick(ServerPlayer player) {
		State s = state(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		// the charge tops itself up, faster in bright light
		float regen = REGEN + (level.getMaxLocalRawBrightness(player.blockPosition()) >= BRIGHT ? REGEN_BRIGHT : 0.0F);
		if (!s.beaming) {
			s.charge = Math.min(MAX, s.charge + regen);
		}
		if (s.beaming) {
			beam(player, level, s);
		} else if (s.heat > 0.0F) {
			s.heat = Math.max(0.0F, s.heat - HEAT_COOL);
			if (s.over && s.heat <= HEAT_BACK) {
				s.over = false;
				MindsKit.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.8F);
			}
		}
		if (s.domeAt != null) {
			dome(player, level, s, now);
		}
		if (s.handTicks > 0) {
			grip(player, level, s);
		}
		if (s.volley > 0 && now >= s.volleyAt) {
			fire(player, level, s);
			s.volley--;
			s.volleyAt = now + VOLLEY_GAP;
		}
		for (Iterator<Bolt> it = s.bolts.iterator(); it.hasNext();) {
			if (step(player, level, s, it.next())) {
				it.remove();
			}
		}
		sync(player, s);
	}

	private static void sync(ServerPlayer player, State s) {
		XenoMinds.put(player, XenoMinds.LUMEN, new XenoMinds.LumenHud(s.shape, Math.round(s.charge), Math.round(s.heat), s.over));
	}

	private static void beam(ServerPlayer player, ServerLevel level, State s) {
		s.charge -= BEAM_CHARGE;
		s.heat += BEAM_HEAT;
		if (s.charge <= 0.0F || player.isSpectator()) {
			s.charge = Math.max(0.0F, s.charge);
			s.beaming = false;
			return;
		}
		if (s.heat >= HEAT_FULL) {
			s.heat = HEAT_FULL;
			s.over = true;
			s.beaming = false;
			MagicFx.blast(level, player.getEyePosition().add(player.getLookAngle().scale(0.8)), 0.8F, HEAT_COLOR);
			MindsKit.sound(player, SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.5F);
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BEAM_REACH));
		var wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		Vec3 stop = end;
		if (player.tickCount % 2 == 0) {
			Vec3 from = eye.add(look.scale(0.7)).add(0.0, -0.3, 0.0);
			float heat = s.heat / HEAT_FULL;
			MagicFx.beam(level, from, end, BEAM_WIDTH * (0.8F + 0.4F * heat), heat > 0.7F ? HEAT_COLOR : COLOR, 3);
		}
		if (player.tickCount % BEAM_EVERY == 0) {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.0),
				e -> MindsKit.fair(player, e) && e.getBoundingBox().inflate(BEAM_WIDTH * 0.5).clip(eye, stop).isPresent())) {
				e.setInvulnerableTime(0);
				e.hurtServer(level, player.damageSources().indirectMagic(player, player), BEAM_DAMAGE);
			}
			MagicFx.blast(level, end, 0.5F, COLOR, 6);
		}
		if (player.tickCount % 12 == 0) {
			MindsKit.sound(player, SoundEvents.BEACON_AMBIENT, 0.6F, 1.8F);
		}
	}

	private static void dome(ServerPlayer player, ServerLevel level, State s, long now) {
		Vec3 c = s.domeAt;
		if (now >= s.domeUntil || player.level().dimension() != level.dimension()) {
			MagicFx.blast(level, c.add(0.0, 1.0, 0.0), (float)DOME_RADIUS, COLOR, 8);
			s.domeAt = null;
			return;
		}
		Vec3 mid = c.add(0.0, 1.0, 0.0);
		if (player.tickCount % 4 == 0) {
			MagicFx.orb(level, mid, (float)DOME_RADIUS, COLOR, 5);
		}
		if (player.tickCount % 10 == 0) {
			MagicFx.sigil(level, c.add(0.0, 0.05, 0.0), (float)DOME_RADIUS * 0.8F, COLOR, 12);
		}
		AABB box = new AABB(mid, mid).inflate(DOME_RADIUS + 2.0);
		for (Projectile shot : level.getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
			Entity owner = shot.getOwner();
			if (owner != null && (owner == player || MindsKit.spared(player, owner))) {
				continue;
			}
			double d = shot.position().distanceTo(mid);
			if (d >= DOME_RADIUS - DOME_SHELL && d <= DOME_RADIUS + DOME_SHELL * 0.5) {
				MagicFx.blast(level, shot.position(), 0.6F, COLOR, 6);
				MindsKit.soundAt(level, shot.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 1.6F);
				shot.discard();
			}
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			double d = e.position().add(0.0, e.getBbHeight() * 0.5, 0.0).distanceTo(mid);
			if (MindsKit.fair(player, e) && !(e instanceof Player) && d >= DOME_RADIUS - DOME_SHELL && d <= DOME_RADIUS + DOME_SHELL * 0.5) {
				MindsKit.shove(e, mid, DOME_PUSH, 0.1);
			} else if ((e == player || e instanceof Player || MindsKit.spared(player, e)) && d < DOME_RADIUS && now % 10 == 0) {
				e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 24, 0, false, false), player);
			}
		}
	}

	private static void grip(ServerPlayer player, ServerLevel level, State s) {
		Entity found = level.getEntity(s.handTarget);
		if (!(found instanceof LivingEntity held) || !held.isAlive()) {
			s.handTicks = 0;
			return;
		}
		s.handTicks--;
		if (!GreatFoes.is(held)) {
			Vec3 want = player.position().add(MindsKit.flat(player).scale(HAND_STOP));
			Vec3 pull = want.subtract(held.position()).scale(HAND_PULL);
			if (pull.lengthSqr() > HAND_MAX * HAND_MAX) {
				pull = pull.normalize().scale(HAND_MAX);
			}
			held.setDeltaMovement(pull.x, Math.max(-0.2, Math.min(0.35, pull.y)), pull.z);
			held.syncVelocity = true;
			held.resetFallDistance();
			if (held instanceof net.minecraft.world.entity.Mob mob) {
				mob.getNavigation().stop();
			}
		}
		Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0.0, -0.4, 0.0);
		Vec3 centre = held.getBoundingBox().getCenter();
		MagicFx.beam(level, from, centre, 0.22F, COLOR, 3);
		MagicFx.orb(level, centre, 0.55F, COLOR, 3);
		if (s.handTicks == 0) {
			held.setInvulnerableTime(0);
			held.hurtServer(level, player.damageSources().indirectMagic(player, player), HAND_DAMAGE);
			held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), player);
			MagicFx.blast(level, centre, 0.9F, COLOR);
			MindsKit.sound(player, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 1.2F);
		}
	}

	private static void fire(ServerPlayer player, ServerLevel level, State s) {
		var rng = player.getRandom();
		Vec3 look = player.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x).normalize();
		Bolt b = new Bolt();
		b.pos = player.getEyePosition().add(look.scale(0.8)).add(side.scale((rng.nextDouble() - 0.5) * 1.8)).add(0.0, rng.nextDouble() * 0.6 - 0.2, 0.0);
		b.vel = look.add((rng.nextDouble() - 0.5) * 0.7, (rng.nextDouble() - 0.2) * 0.5, (rng.nextDouble() - 0.5) * 0.7).normalize().scale(MISSILE_SPEED * 0.8);
		b.target = s.volleyTarget;
		b.life = MISSILE_LIFE;
		s.bolts.add(b);
		MindsKit.soundAt(level, b.pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.4F + rng.nextFloat() * 0.5F);
	}

	/** One tick of a missile or the great block. True: it has burst or flown out. */
	private static boolean step(ServerPlayer player, ServerLevel level, State s, Bolt b) {
		if (b.block) {
			b.vel = b.vel.add(0.0, -BLOCK_GRAVITY, 0.0);
		} else if (b.target >= 0) {
			if (level.getEntity(b.target) instanceof LivingEntity t && t.isAlive()) {
				Vec3 want = t.getBoundingBox().getCenter().subtract(b.pos);
				if (want.lengthSqr() > 1.0E-4) {
					b.vel = b.vel.lerp(want.normalize().scale(MISSILE_SPEED), MISSILE_TURN);
				}
			} else {
				b.target = -1;
			}
		}
		Vec3 end = b.pos.add(b.vel);
		var wall = level.clip(new ClipContext(b.pos, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		boolean blocked = wall.getType() != HitResult.Type.MISS;
		Vec3 stop = blocked ? wall.getLocation() : end;
		double r = b.block ? 1.0 : 0.35;
		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		Vec3 from = b.pos;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, stop).inflate(r + 1.0),
			e -> MindsKit.fair(player, e) && e.getBoundingBox().inflate(r).clip(from, stop).isPresent())) {
			double d = e.distanceToSqr(from);
			if (d < best) {
				best = d;
				hit = e;
			}
		}
		if (hit != null || blocked || --b.life <= 0) {
			Vec3 at = hit != null ? hit.getBoundingBox().getCenter() : stop;
			burst(player, level, at, b.block);
			return true;
		}
		b.pos = end;
		MagicFx.orb(level, b.pos, b.block ? 1.1F : 0.2F, COLOR, 2);
		return false;
	}

	private static void burst(ServerPlayer player, ServerLevel level, Vec3 at, boolean block) {
		double radius = block ? BLOCK_RADIUS : MISSILE_RADIUS;
		float damage = block ? BLOCK_DAMAGE : MISSILE_DAMAGE;
		for (LivingEntity e : MindsKit.near(player, at, radius)) {
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().indirectMagic(player, player), damage);
			if (block) {
				MindsKit.shove(e, at, BLOCK_OUT, BLOCK_UP);
			}
		}
		MagicFx.blast(level, at, (float)radius * 0.8F, COLOR);
		if (block) {
			MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float)radius, COLOR);
			MindsKit.soundAt(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 1.4F);
			Shakes.heavy(player, at, 14.0, 0.8F);
		} else {
			MindsKit.soundAt(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.5F);
		}
	}
}
