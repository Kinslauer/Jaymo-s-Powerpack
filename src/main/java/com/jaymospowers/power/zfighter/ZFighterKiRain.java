package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.46, Andy's NOEA picks) Ki Rain, a quick technique (G): a spray of ki shoots up from your hand, and
 * {@link #DELAY_TICKS} later (a second) it comes down as small bolts over a {@link #RADIUS}-block circle round the locked
 * target (as it stands when the rain begins), or round where you look. The bolts fall for {@link #RAIN_TICKS} and hurt
 * whatever one lands on; they break no blocks. Bolts and damage are scaled by Ki Power; the bolts are the same ki orbs as a
 * Ki Blast's ({@link ZFighterKi#orb}).
 */
final class ZFighterKiRain {
	static final float KI = 35.0F;
	static final int COOLDOWN = 300;
	/** How far away the circle may be set when you look at nothing, and how far a locked target may be. */
	static final double RANGE = 40.0;
	static final double RADIUS = 6.0;
	/** The spray goes up this fast and this long, then the rain begins {@link #DELAY_TICKS} after the cast. */
	static final int DELAY_TICKS = 20;
	static final int RAIN_TICKS = 50;
	/** New bolts every this many ticks, this many at a time. */
	static final int EVERY = 2, PER_VOLLEY = 3;
	/** Where they start above the circle (blocks), how fast they fall (blocks a tick), and how big each is. */
	static final double HEIGHT = 14.0, FALL = 1.5, BOLT_SIZE = 0.35;
	/** A bolt hurts what comes this near its middle, for this much (times Ki Power). */
	static final double HIT_REACH = 1.3;
	static final float DAMAGE = 3.5F;
	static final float POISE_WEAR = 2.0F;

	private static final class Drop {
		Vec3 pos;
		final int id;

		Drop(Vec3 pos, int id) {
			this.pos = pos;
			this.id = id;
		}
	}

	private static final class Rain {
		final ServerLevel level;
		final UUID owner;
		final UUID locked;
		Vec3 centre;
		final float damage;
		final int color;
		final long begin;
		final List<Drop> drops = new ArrayList<>();
		int serial;
		boolean begun;

		Rain(ServerLevel level, UUID owner, UUID locked, Vec3 centre, float damage, int color, long begin) {
			this.level = level;
			this.owner = owner;
			this.locked = locked;
			this.centre = centre;
			this.damage = damage;
			this.color = color;
			this.begin = begin;
		}
	}

	private static final List<Rain> RAINS = new ArrayList<>();
	private static int nextId;

	private ZFighterKiRain() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterKiRain::tick);
	}

	static void clear() {
		RAINS.clear();
	}

	/** G: the spray goes up. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		ServerLevel level = player.level();
		LivingEntity locked = LockOn.target(player);
		UUID lockedId = null;
		Vec3 centre;
		if (locked != null && player.distanceTo(locked) <= RANGE) {
			lockedId = locked.getUUID();
			centre = locked.position();
		} else {
			Vec3 eye = player.getEyePosition();
			Vec3 look = player.getViewVector(1.0F);
			var hit = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			centre = hit.getLocation();
		}
		int color = ZFighterArts.color(live);
		float damage = DAMAGE * ZFighterPower.kiPower(player, live);
		RAINS.add(new Rain(level, player.getUUID(), lockedId, centre, damage, color, level.getGameTime() + DELAY_TICKS));
		// the spray: motes shot upward from the hand, fanning a little
		Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.8)).add(0.0, -0.3, 0.0);
		DustParticleOptions dust = new DustParticleOptions(color, 1.4F);
		RandomSource random = level.getRandom();
		for (int i = 0; i < 30; i++) {
			level.sendParticles(dust, true, false, hand.x, hand.y, hand.z, 0, (random.nextDouble() - 0.5) * 0.3, 1.0, (random.nextDouble() - 0.5) * 0.3,
				0.6 + random.nextDouble() * 0.5);
		}
		level.sendParticles(ParticleTypes.END_ROD, true, false, hand.x, hand.y, hand.z, 8, 0.1, 0.1, 0.1, 0.4);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.8F, 1.3F);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(1.0F);
		}
		return COOLDOWN;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Rain> it = RAINS.iterator(); it.hasNext(); ) {
			Rain rain = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(rain.owner);
			if (owner == null || !owner.isAlive() || owner.level() != rain.level || !advance(owner, rain)) {
				it.remove();
			}
		}
	}

	/** One tick: the rain waits, begins, falls, and is spent when its bolts are all down. */
	private static boolean advance(ServerPlayer owner, Rain rain) {
		ServerLevel level = rain.level;
		long now = level.getGameTime();
		if (now < rain.begin) {
			return true;
		}
		long into = now - rain.begin;
		if (!rain.begun) {
			rain.begun = true;
			if (rain.locked != null && level.getEntity(rain.locked) instanceof LivingEntity foe && foe.isAlive()) {
				rain.centre = foe.position();
			}
			level.playSound(null, rain.centre.x, rain.centre.y, rain.centre.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.8F);
		}
		RandomSource random = level.getRandom();
		if (into < RAIN_TICKS && into % EVERY == 0) {
			for (int i = 0; i < PER_VOLLEY; i++) {
				double angle = random.nextDouble() * Math.PI * 2.0;
				double r = Math.sqrt(random.nextDouble()) * RADIUS;
				Vec3 start = new Vec3(rain.centre.x + Math.cos(angle) * r, rain.centre.y + HEIGHT, rain.centre.z + Math.sin(angle) * r);
				rain.drops.add(new Drop(start, 0x58000000 | (nextId++ & 0xFFFFFF)));
			}
		}
		DustParticleOptions dust = new DustParticleOptions(rain.color, 1.2F);
		var source = ZFighterKi.source(owner);
		for (Iterator<Drop> it = rain.drops.iterator(); it.hasNext(); ) {
			Drop drop = it.next();
			Vec3 from = drop.pos;
			Vec3 to = from.add(0.0, -FALL, 0.0);
			var block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
			Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
			boolean landed = block.getType() != HitResult.Type.MISS;
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(HIT_REACH),
				e -> e != owner && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand))) {
				if (ZFighterKi.distanceToSegment(target.getBoundingBox().getCenter(), from, end) > HIT_REACH + target.getBbWidth() * 0.4) {
					continue;
				}
				target.setInvulnerableTime(0);
				target.hurtServer(level, source, rain.damage);
				ZFighterPoise.wear(target, POISE_WEAR);
				landed = true;
				end = target.getBoundingBox().getCenter();
				break;
			}
			if (landed) {
				level.sendParticles(dust, true, false, end.x, end.y, end.z, 5, 0.25, 0.25, 0.25, 0.1);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, end.x, end.y, end.z, 3, 0.2, 0.2, 0.2, 0.3);
				if (random.nextInt(3) == 0) {
					level.playSound(null, end.x, end.y, end.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3F, 1.9F);
				}
				it.remove();
				continue;
			}
			ZFighterKi.orb(level, drop.id, to, BOLT_SIZE, rain.color);
			level.sendParticles(dust, true, false, from.x, from.y, from.z, 1, 0.03, 0.03, 0.03, 0.0);
			drop.pos = to;
			if (to.y < level.getMinY()) {
				it.remove();
			}
		}
		return into < RAIN_TICKS || !rain.drops.isEmpty();
	}
}
