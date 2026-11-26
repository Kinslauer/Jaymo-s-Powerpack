package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Loaded;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Thunderhead's electrokinesis. Andy (2026-11-25), on the Hand of God mod's (ideas only; it is All Rights Reserved):
 * "electrokinesis is what i really want from stormborn thunderhead". So:
 * <ul>
 * <li><b>The summoning</b>: the Thunderhead turned on under the open sky calls the storm down: {@link #SUMMON_STRIKES}
 * bolts fall round you one after another, and the last comes down into your hand.</li>
 * <li><b>Charged</b>: while it's on, arcs leap from your body every so often to the blocks round you (leaving small burns)
 * or to a foe close by ({@link #ARC_DAMAGE}).</li>
 * <li><b>Real-looking lightning</b>: every strike and zap of the storm side is a branching bolt ({@link MagicFx#bolt}), and
 * where one comes down out of the sky it leaves a burn on the ground ({@link MagicFx#scorch}) that glows and cools to char.</li>
 * <li><b>Charcoal statues</b>: whatever a Thunderhead's lightning kills (not players) stands where it died, black as
 * charcoal, for {@link #STATUE_TICKS}, then crumbles to ash. It leaves no body (its drops fall as usual).</li>
 * </ul>
 */
public final class WindElectro {
	// ---- tuning ----
	/** The summoning: bolts round you, ticks between them, the wait before the last one comes to your hand. */
	static final int SUMMON_STRIKES = 4, SUMMON_GAP = 6, SUMMON_HAND_DELAY = 12;
	/** How far from you they fall (blocks), and how high their bolts start above the ground. */
	static final double SUMMON_NEAR = 4.0, SUMMON_FAR = 11.0, SKY_HEIGHT = 32.0;
	/** A sky strike: the bolt's width and life (ticks), the burn it leaves (radius, ticks). */
	static final float STRIKE_WIDTH = 0.5F, STRIKE_MARK = 1.2F;
	static final int STRIKE_TICKS = 8, MARK_TICKS = 600;
	/** Arcs off the body: ticks between them (least, most), reach (blocks), width, the damage one does to a foe, a small burn's chance. */
	static final int ARC_MIN = 5, ARC_MAX = 16, ARC_TICKS = 4;
	static final double ARC_REACH = 2.8;
	static final float ARC_WIDTH = 0.08F, ARC_DAMAGE = 1.0F, ARC_MARK_CHANCE = 0.35F, ARC_MARK = 0.22F;
	/** A zap thrown between two points (the storm side's moves): width and life. */
	static final float ZAP_WIDTH = 0.18F;
	static final int ZAP_TICKS = 6;
	/** A charcoal statue stands this long (ticks) before it crumbles. */
	public static final int STATUE_TICKS = 120;
	/** The lightning's colour, its glow, and the char it cools to. */
	static final int BOLT = WindBolts.BOLT, GLOW = WindBolts.GLOW, CHAR = 0x17151A;
	/** Jaymo's Kitchen leaves no body for a creature with this tag (Corpses.NO_CORPSE_TAG). */
	static final String NO_CORPSE_TAG = "jaymoskitchen.no_corpse";

	/** On a creature a Thunderhead's lightning killed: it stands as a charcoal statue (every client draws it black). */
	public static final AttachmentType<Boolean> CHARRED = AttachmentRegistry.create(JaymosPowers.id("charred"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** When each player's summoning began (game time). */
	private static final Map<UUID, Long> SUMMONS = new HashMap<>();
	/** Ticks to each player's next arc. */
	private static final Map<UUID, Integer> NEXT_ARC = new HashMap<>();

	private WindElectro() {
	}

	static void init() {
		// in Wounds' killing phase, after anything that saves the dying (the downed): only real deaths are charred
		ServerLivingEntityEvents.ALLOW_DEATH.register(JaymosPowers.id("wounds_killing_blow"), (entity, source, amount) -> {
			if (!(entity instanceof Player) && source.is(DamageTypes.LIGHTNING_BOLT) && source.getEntity() instanceof ServerPlayer caster
				&& WindModes.mode(caster) == WindModes.BOTH) {
				entity.addTag(NO_CORPSE_TAG);
				entity.setAttached(CHARRED, Boolean.TRUE);
			}
			return true;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SUMMONS.remove(handler.player.getUUID());
			NEXT_ARC.remove(handler.player.getUUID());
		});
	}

	/** The Thunderhead has just been turned on: under the open sky, the storm comes down (see {@link #tick}). */
	static void summon(ServerPlayer player) {
		if (player.level().canSeeSky(BlockPos.containing(player.getEyePosition()))) {
			SUMMONS.put(player.getUUID(), player.level().getGameTime());
		}
	}

	/** Every tick while the Thunderhead is on: the summoning's strikes, and the arcs off the body. */
	static void tick(ServerPlayer player, ServerLevel level) {
		Long start = SUMMONS.get(player.getUUID());
		if (start != null) {
			long age = level.getGameTime() - start;
			RandomSource random = player.getRandom();
			if (age >= 0 && age < SUMMON_STRIKES * SUMMON_GAP && age % SUMMON_GAP == 0) {
				double angle = random.nextDouble() * Mth.TWO_PI;
				double distance = Mth.lerp(random.nextDouble(), SUMMON_NEAR, SUMMON_FAR);
				Vec3 at = ground(level, player.getX() + Math.cos(angle) * distance, player.getZ() + Math.sin(angle) * distance);
				if (at != null) {
					skyStrike(level, at);
				}
			} else if (age >= SUMMON_STRIKES * SUMMON_GAP + SUMMON_HAND_DELAY || age < 0) {
				SUMMONS.remove(player.getUUID());
				intoHand(player, level);
			}
		}
		int left = NEXT_ARC.getOrDefault(player.getUUID(), ARC_MAX) - 1;
		if (left > 0) {
			NEXT_ARC.put(player.getUUID(), left);
			return;
		}
		NEXT_ARC.put(player.getUUID(), Mth.nextInt(player.getRandom(), ARC_MIN, ARC_MAX));
		arc(player, level);
	}

	/** The Thunderhead is off (or the player gone): no summoning left half done, no arcs. */
	static void stop(ServerPlayer player) {
		SUMMONS.remove(player.getUUID());
		NEXT_ARC.remove(player.getUUID());
	}

	/** The last bolt of the summoning, down into the right hand: you are charged. */
	private static void intoHand(ServerPlayer player, ServerLevel level) {
		Vec3 look = player.getViewVector(1.0F);
		Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0E-6 ? Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F) : right.normalize();
		Vec3 hand = player.getEyePosition().add(look.scale(0.4)).add(right.scale(0.35)).add(0.0, -0.45, 0.0);
		Vec3 sky = hand.add((player.getRandom().nextDouble() - 0.5) * 6.0, SKY_HEIGHT, (player.getRandom().nextDouble() - 0.5) * 6.0);
		MagicFx.bolt(level, sky, hand, STRIKE_WIDTH, BOLT, STRIKE_TICKS + 2);
		MagicFx.blast(level, hand, 1.4F, GLOW);
		level.sendParticles(MagicFx.spark(BOLT, 1.2F), hand.x, hand.y, hand.z, 14, 0.3, 0.3, 0.3, 0.0);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.4F, 0.8F);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.5F, 0.9F);
		Shakes.heavy(player, hand, 24.0, 1.4F);
	}

	/** An arc off the body: to a foe close by if there is one, else to the nearest surface in a random direction. */
	private static void arc(ServerPlayer player, ServerLevel level) {
		RandomSource random = player.getRandom();
		Vec3 from = player.position().add((random.nextDouble() - 0.5) * player.getBbWidth(), random.nextDouble() * player.getBbHeight(),
			(random.nextDouble() - 0.5) * player.getBbWidth());
		AABB near = player.getBoundingBox().inflate(ARC_REACH);
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, near, e -> e != player && e.isAlive() && PickKit.foe(player, e))) {
			if (random.nextBoolean()) {
				MagicFx.bolt(level, from, foe.getBoundingBox().getCenter(), ARC_WIDTH * 1.5F, BOLT, ARC_TICKS);
				foe.hurtServer(level, player.damageSources().source(DamageTypes.LIGHTNING_BOLT, player), ARC_DAMAGE);
				level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.4F, 1.9F);
				return;
			}
		}
		Vec3 way = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6 - 0.4, random.nextGaussian());
		if (way.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 to = from.add(way.normalize().scale(ARC_REACH));
		BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			// nothing to ground on: a short crackle in the air
			MagicFx.bolt(level, from, from.add(way.normalize().scale(ARC_REACH * 0.4)), ARC_WIDTH, BOLT, ARC_TICKS - 1);
			return;
		}
		MagicFx.bolt(level, from, hit.getLocation(), ARC_WIDTH, BOLT, ARC_TICKS);
		level.sendParticles(MagicFx.spark(BOLT, 0.7F), hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 3, 0.1, 0.1, 0.1, 0.0);
		if (random.nextFloat() < ARC_MARK_CHANCE) {
			MagicFx.scorch(level, hit.getLocation(), Vec3.atLowerCornerOf(hit.getDirection().getUnitVec3i()), ARC_MARK, GLOW, CHAR, MARK_TICKS / 3);
		}
		level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.25F, 1.8F + random.nextFloat() * 0.2F);
	}

	/**
	 * A bolt out of the sky onto {@code at} (the top of the ground there): the branching bolt, a flash, the burn it leaves
	 * and the thunder. Only the look and the sound: callers deal their own damage.
	 */
	public static void skyStrike(ServerLevel level, Vec3 at) {
		RandomSource random = level.getRandom();
		Vec3 sky = at.add((random.nextDouble() - 0.5) * 8.0, SKY_HEIGHT, (random.nextDouble() - 0.5) * 8.0);
		MagicFx.bolt(level, sky, at, STRIKE_WIDTH, BOLT, STRIKE_TICKS);
		MagicFx.blast(level, at.add(0.0, 0.3, 0.0), 1.6F, GLOW);
		MagicFx.scorch(level, at, null, STRIKE_MARK, GLOW, CHAR, MARK_TICKS);
		level.sendParticles(MagicFx.spark(BOLT, 1.1F), at.x, at.y + 0.2, at.z, 10, 0.4, 0.2, 0.4, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 0.9F + random.nextFloat() * 0.2F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0F, 0.7F + random.nextFloat() * 0.3F);
		Shakes.around(level, at, 16.0, 0.7F);
	}

	/** The top of the ground at ({@code x}, {@code z}), or null if that part of the world isn't there. */
	static Vec3 ground(ServerLevel level, double x, double z) {
		if (!Loaded.made(level, Mth.floor(x), Mth.floor(z))) {
			return null;
		}
		return new Vec3(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)), z);
	}

	/** A zap thrown from {@code from} to {@code to}: a branching bolt and a crackle along it. */
	static void zap(ServerLevel level, Vec3 from, Vec3 to) {
		MagicFx.bolt(level, from, to, ZAP_WIDTH, BOLT, ZAP_TICKS);
		Vec3 mid = from.add(to).scale(0.5);
		level.sendParticles(MagicFx.spark(BOLT, 0.9F), mid.x, mid.y, mid.z, 4, 0.3, 0.3, 0.3, 0.0);
	}

	/**
	 * One tick of a charcoal statue's death, in place of the topple and puff ({@code LivingEntity#tickDeath}, both
	 * sides): it stands, shedding ash, then crumbles. False if the creature isn't charred.
	 */
	public static boolean statueTick(LivingEntity body) {
		if (!body.hasAttached(CHARRED)) {
			return false;
		}
		body.deathTime++;
		if (!(body.level() instanceof ServerLevel level)) {
			return true;
		}
		if (body.deathTime % 6 == 0) {
			level.sendParticles(ParticleTypes.ASH, body.getX(), body.getY() + body.getBbHeight() * 0.6, body.getZ(), 3, body.getBbWidth() * 0.4,
				body.getBbHeight() * 0.3, body.getBbWidth() * 0.4, 0.0);
		}
		if (body.deathTime >= STATUE_TICKS) {
			Vec3 c = body.getBoundingBox().getCenter();
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COAL_BLOCK.defaultBlockState()), c.x, c.y, c.z,
				Mth.clamp((int)(body.getBbWidth() * body.getBbHeight() * 30), 12, 80), body.getBbWidth() * 0.35, body.getBbHeight() * 0.35,
				body.getBbWidth() * 0.35, 0.1);
			level.sendParticles(MagicFx.miasma(0x2A2626, 1.8F), c.x, c.y, c.z, 8, body.getBbWidth() * 0.3, body.getBbHeight() * 0.3,
				body.getBbWidth() * 0.3, 0.01);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.BASALT_BREAK, SoundSource.NEUTRAL, 1.0F, 0.7F);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.SAND_BREAK, SoundSource.NEUTRAL, 0.8F, 0.6F);
			body.remove(Entity.RemovalReason.KILLED);
		}
		return true;
	}
}
