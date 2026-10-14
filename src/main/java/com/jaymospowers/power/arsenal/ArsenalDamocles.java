package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Debris;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Arsenal's B, <b>Sword of Damocles</b>: a great rift tears open in the sky over you, and three seconds later
 * a sword {@link #LENGTH} blocks long pushes out of it and falls, smashing through whatever stands in its way (trees,
 * roofs, the floor above you) and driving its point into the ground, where it stands for a while. Then the rift opens
 * again and the sword is hauled back up into it, hilt first, before the rift closes behind it. Whatever it falls on is crushed; around the point, everything is thrown back.
 *
 * <p>Only the timing and the place are synced ({@link #FALL}, to everyone); every client works out where the sword is
 * from the same formulas as the server ({@link #tipY}, {@link #emerged}). Drawn by {@code ArsenalDamoclesRenderer}.
 */
public final class ArsenalDamocles {
	// tuning
	public static final int COOLDOWN = 900;
	/** The rift opens this high over your feet (lower under the world's ceiling). */
	public static final double PORTAL_HEIGHT = 30.0;
	public static final double PORTAL_RADIUS = 4.5;
	/** The sword's length, point to pommel (blocks). */
	public static final double LENGTH = 16.0;
	/** How deep the point goes into the ground. */
	public static final double EMBED_DEPTH = 4.5;
	/** How far down it looks for ground under you. */
	static final int GROUND_SEARCH = 64;
	/** The blade breaks a column this many blocks either side of its middle; the cross-guard this far out along it. */
	static final int BLADE_HALF_WIDTH = 1;
	static final double GUARD_HALF_WIDTH = 3.5;
	/** Where the cross-guard sits, as a share of the length from the point. */
	public static final double GUARD_AT = 0.68;
	/** It breaks blocks up to this hard (deepslate 3, ores up to 4.5; obsidian 50 holds it). */
	static final float MAX_HARDNESS = 10.0F;
	/** Chance a broken block drops itself (like an explosion's). */
	static final float DROP_CHANCE = 0.25F;
	/** Falling on a creature. */
	static final float FALL_DAMAGE = 30.0F;
	/** The point striking the ground: damage at the centre, falling off to nothing at {@link #IMPACT_RADIUS}. */
	static final float IMPACT_DAMAGE = 16.0F;
	static final double IMPACT_RADIUS = 6.0;
	static final double KNOCKBACK = 1.6;
	static final double KNOCKBACK_UP = 0.6;
	/** Soft ground (dirt, sand, grass) around the point cracks open out to this far. */
	static final double CRATER_RADIUS = 3.0;
	static final float CRATER_HARDNESS = 1.0F;

	// timeline (ticks since the cast)
	public static final int OPEN_END = 14;
	/** Three seconds after the rift opens, the sword comes through. */
	public static final int DROP = 60;
	/** It pushes out of the rift over this many ticks (hilt held at the rift), then falls. */
	public static final int EMERGE = 6;
	/** Falling: starting speed and the pull (blocks/tick, blocks/tick²). */
	public static final double FALL_SPEED = 0.4;
	public static final double FALL_ACCEL = 0.14;
	/** How long it stands in the ground before the rift opens again to take it back. */
	public static final int STAND = 240;
	/** Hauled back up: starting speed and the pull (blocks/tick, blocks/tick²), once the rift is open again. */
	public static final double RISE_SPEED = 0.05;
	public static final double RISE_ACCEL = 0.06;
	/** The rift closes over this many ticks once the sword has landed. */
	public static final int PORTAL_CLOSE = 12;

	/**
	 * A sword falling: when, the column it falls down (x, z), the rift's height, where the point stops, and which way the
	 * blade faces (radians about the vertical).
	 */
	public record Fall(long start, double x, double z, double portalY, double tipEnd, float angle) {
		public static final StreamCodec<ByteBuf, Fall> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Fall::start,
			ByteBufCodecs.DOUBLE, Fall::x,
			ByteBufCodecs.DOUBLE, Fall::z,
			ByteBufCodecs.DOUBLE, Fall::portalY,
			ByteBufCodecs.DOUBLE, Fall::tipEnd,
			ByteBufCodecs.FLOAT, Fall::angle,
			Fall::new);

		/** The hilt's height while it's still pushing out of the rift. */
		public double hiltStart() {
			return portalY - 0.4;
		}

		/** Ticks after the cast when the point reaches {@link #tipEnd}. */
		public float landsAt() {
			double d = hiltStart() - LENGTH - tipEnd;
			double f = d <= 0.0 ? 0.0 : (-FALL_SPEED + Math.sqrt(FALL_SPEED * FALL_SPEED + 2.0 * FALL_ACCEL * d)) / FALL_ACCEL;
			return (float) (DROP + EMERGE + f);
		}

		/** Ticks after the cast when the rift starts opening again to take the sword back. */
		public float reopensAt() {
			return landsAt() + STAND;
		}

		/** Ticks after the cast when the sword starts rising (the rift is open again by then). */
		public float risesAt() {
			return reopensAt() + PORTAL_CLOSE;
		}

		/** Ticks after the cast when the point has gone back through the rift. */
		public float sunkAt() {
			double d = hiltStart() - tipEnd;
			return risesAt() + (float) ((-RISE_SPEED + Math.sqrt(RISE_SPEED * RISE_SPEED + 2.0 * RISE_ACCEL * d)) / RISE_ACCEL);
		}

		/** Ticks after the cast when the rift has closed behind it. */
		public float gone() {
			return sunkAt() + PORTAL_CLOSE;
		}

		/** Along the blade, horizontally: the way the cross-guard runs. */
		public Vec3 across() {
			return new Vec3(Math.cos(angle), 0.0, -Math.sin(angle));
		}
	}

	public static final AttachmentType<Fall> FALL = AttachmentRegistry.create(JaymosPowers.id("arsenal_damocles"),
		builder -> builder.syncWith(Fall.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** The server's side of a fall in progress: where the point and guard were last tick, and who's been hit. */
	private static final class Live {
		double tip;
		double guard;
		boolean landed;
		final Set<Integer> hit = new HashSet<>();
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private ArsenalDamocles() {
	}

	static void init() {
		// (the attachment registers itself when this class loads)
	}

	/** How much of the sword is out of the rift (0-1) at {@code t}. */
	public static float emerged(float t) {
		if (t < DROP) {
			return 0.0F;
		}
		float x = Math.min(1.0F, (t - DROP) / EMERGE);
		return 1.0F - (1.0F - x) * (1.0F - x);
	}

	/** The point's height at {@code t}. */
	public static double tipY(Fall fall, float t) {
		if (t >= fall.risesAt()) {
			float f = t - fall.risesAt();
			return Math.min(fall.hiltStart(), fall.tipEnd() + RISE_SPEED * f + 0.5 * RISE_ACCEL * f * f);
		}
		if (t < DROP + EMERGE) {
			return fall.hiltStart() - LENGTH * emerged(t);
		}
		float f = t - DROP - EMERGE;
		return Math.max(fall.tipEnd(), fall.hiltStart() - LENGTH - (FALL_SPEED * f + 0.5 * FALL_ACCEL * f * f));
	}

	/** How open the rift is (0-1) at {@code t}. */
	public static float openness(Fall fall, float t) {
		if (t < OPEN_END) {
			float x = t / OPEN_END;
			return (float) Math.min(1.1, 1.0 - Math.pow(1.0 - x, 3.0) + Math.sin(x * Math.PI) * 0.12);
		}
		if (t >= fall.sunkAt()) {
			return Math.max(0.0F, 1.0F - (t - fall.sunkAt()) / PORTAL_CLOSE);
		}
		if (t >= fall.reopensAt()) {
			return Math.min(1.0F, (t - fall.reopensAt()) / PORTAL_CLOSE);
		}
		float closing = t - fall.landsAt();
		return closing <= 0.0F ? 1.0F : Math.max(0.0F, 1.0F - closing / PORTAL_CLOSE);
	}

	/**
	 * How much of the sword is out of the rift (blocks, point to wherever the rift cuts it): growing as it pushes out,
	 * all of it while it falls and stands, shrinking again as it's hauled back in.
	 */
	public static double shownLength(Fall fall, float t) {
		if (t >= fall.risesAt()) {
			return Mth.clamp(fall.hiltStart() - tipY(fall, t), 0.0, LENGTH);
		}
		return LENGTH * emerged(t);
	}

	/** B, while corrupted. */
	static void cast(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (player.getAttached(FALL) != null) {
			return;
		}
		ArsenalPower.Cooldowns cd = ArsenalPower.cooldowns(player);
		if (now < cd.damocles()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.damocles_recharging",
				String.format("%.1f", (cd.damocles() - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
			return;
		}
		// the ground under you: the first solid block below your feet
		BlockPos feet = player.blockPosition();
		BlockPos ground = null;
		for (int dy = 0; dy <= GROUND_SEARCH && feet.getY() - dy > level.getMinY(); dy++) {
			BlockPos pos = feet.below(dy);
			if (level.getBlockState(pos).isSolid()) {
				ground = pos;
				break;
			}
		}
		if (ground == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.damocles_nowhere").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(ArsenalPower.COOLDOWNS, cd.withDamocles(now + COOLDOWN));
		double surface = ground.getY() + 1.0;
		double portalY = Math.min(player.getY() + PORTAL_HEIGHT, level.getMaxY() - 1.0);
		portalY = Math.max(portalY, surface + LENGTH + 2.0);
		Fall fall = new Fall(now, player.getX(), player.getZ(), portalY, surface - EMBED_DEPTH, player.getRandom().nextFloat() * Mth.TWO_PI);
		player.setAttached(FALL, fall);
		Live live = new Live();
		live.tip = fall.hiltStart();
		live.guard = fall.hiltStart();
		LIVE.put(player.getUUID(), live);

		level.playSound(null, fall.x(), portalY, fall.z(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 3.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** Every tick for the caster. */
	static void tick(ServerPlayer player) {
		Fall fall = player.getAttached(FALL);
		if (fall == null) {
			LIVE.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - fall.start();
		Live live = LIVE.get(player.getUUID());
		if (t < 0 || t >= fall.gone() || live == null) {
			onLost(player);
			return;
		}
		Vec3 portal = new Vec3(fall.x(), fall.portalY(), fall.z());
		if (t < DROP) {
			// the rift above, gathering: motes falling from it, and a rumble as it's about to give
			if (t % 2 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, portal.x, portal.y - 0.3, portal.z, 6, PORTAL_RADIUS * 0.5, 0.1, PORTAL_RADIUS * 0.5, 0.02);
				level.sendParticles(ParticleTypes.SQUID_INK, portal.x, portal.y - 0.4, portal.z, 3, PORTAL_RADIUS * 0.45, 0.1, PORTAL_RADIUS * 0.45, 0.02);
			}
			if (t == DROP - 30 || t == DROP - 12) {
				level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 3.0F, 0.5F);
			}
			return;
		}
		if (t == DROP) {
			level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0F, 0.6F);
			level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 0.5F);
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, portal.x, portal.y - 1.0, portal.z, 120, 1.5, 1.0, 1.5, 0.3);
		}
		if (live.landed) {
			if (t == (long) Math.ceil(fall.reopensAt())) {
				// the rift opens again to take it back
				level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 2.0F, 0.8F);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, portal.x, portal.y - 0.3, portal.z, 60, PORTAL_RADIUS * 0.5, 0.2, PORTAL_RADIUS * 0.5, 0.05);
			}
			if (t == (long) Math.ceil(fall.risesAt())) {
				// it tears out of the ground and is hauled up
				double surface = fall.tipEnd() + EMBED_DEPTH;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, fall.x(), surface + 1.0, fall.z(), 120, 1.0, 0.5, 1.0, 0.15);
				level.playSound(null, fall.x(), surface, fall.z(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.5F, 0.5F);
				level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.6F, 0.7F);
			}
			if (t > fall.risesAt() && t < fall.sunkAt() && t % 2 == 0) {
				double tip = tipY(fall, t);
				double shown = shownLength(fall, t);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, fall.x(), tip + shown * 0.5, fall.z(), 8, 0.4, shown * 0.25, 0.4, 0.02);
			}
			if (t == (long) Math.ceil(fall.sunkAt())) {
				level.playSound(null, portal.x, portal.y, portal.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 2.0F, 0.4F);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, portal.x, portal.y - 0.5, portal.z, 120, 1.5, 0.5, 1.5, 0.2);
			}
			return;
		}
		double tip = tipY(fall, t);
		double hilt = tip + LENGTH * emerged(t);
		double guard = tip + LENGTH * GUARD_AT * emerged(t);
		smashThrough(player, level, fall, tip, live.tip, guard, live.guard);
		crush(player, level, fall, tip, hilt, live);
		live.tip = tip;
		live.guard = guard;
		if (t % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, fall.x(), (tip + hilt) * 0.5, fall.z(), 10, 0.5, LENGTH * 0.25, 0.5, 0.02);
		}
		if (tip <= fall.tipEnd() + 1.0E-3) {
			live.landed = true;
			impact(player, level, fall, live);
		}
	}

	/** Breaks what the blade (point first) and the cross-guard passed through since last tick. */
	private static void smashThrough(ServerPlayer player, ServerLevel level, Fall fall, double tip, double tipWas, double guard, double guardWas) {
		double surface = fall.tipEnd() + EMBED_DEPTH;
		// the blade: a column under the point, down to (not into) the ground it stops in
		int top = Mth.floor(tipWas);
		int bottom = Mth.floor(Math.max(tip, surface));
		int cx = Mth.floor(fall.x()), cz = Mth.floor(fall.z());
		for (int y = top; y >= bottom; y--) {
			for (int dx = -BLADE_HALF_WIDTH; dx <= BLADE_HALF_WIDTH; dx++) {
				for (int dz = -BLADE_HALF_WIDTH; dz <= BLADE_HALF_WIDTH; dz++) {
					smash(player, level, new BlockPos(cx + dx, y, cz + dz), MAX_HARDNESS, (dx + dz + y) % 2 == 0);
				}
			}
		}
		// the cross-guard, sweeping down the wider slot
		Vec3 across = fall.across();
		int gTop = Mth.floor(guardWas);
		int gBottom = Mth.floor(Math.max(guard, surface));
		for (int y = gTop; y >= gBottom; y--) {
			for (double s = -GUARD_HALF_WIDTH; s <= GUARD_HALF_WIDTH; s += 0.5) {
				BlockPos pos = BlockPos.containing(fall.x() + across.x * s, y + 0.5, fall.z() + across.z * s);
				smash(player, level, pos, MAX_HARDNESS, false);
			}
		}
	}

	private static void smash(ServerPlayer player, ServerLevel level, BlockPos pos, float maxHardness, boolean puff) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) {
			return;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness > maxHardness || !mayBreak(player, level, pos, state)) {
			return;
		}
		level.destroyBlock(pos, level.getRandom().nextFloat() < DROP_CHANCE, player);
		PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
		if (puff) {
			Debris.puff(level, pos, state, 1.2F);
		}
	}

	/**
	 * Whether {@code player} may break the block at {@code pos}: they can build, the spot isn't spawn-protected or
	 * adventure-restricted, and no claim mod (through Fabric's break event) objects.
	 */
	public static boolean mayBreak(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
		return player.mayBuild() && player.mayInteract(level, pos) && !player.blockActionRestricted(level, pos, player.gameMode())
			&& PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, level.getBlockEntity(pos));
	}

	/** Whatever the falling blade comes down on. */
	private static void crush(ServerPlayer player, ServerLevel level, Fall fall, double tip, double hilt, Live live) {
		double r = BLADE_HALF_WIDTH + 0.6;
		AABB box = new AABB(fall.x() - r, tip - 0.5, fall.z() - r, fall.x() + r, hilt, fall.z() + r);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
			if (live.hit.add(victim.getId())) {
				victim.hurtServer(level, level.damageSources().playerAttack(player), FALL_DAMAGE);
				ArsenalVoid.voidHit(victim, player, true);
			}
		}
	}

	/** The point strikes the ground. */
	private static void impact(ServerPlayer player, ServerLevel level, Fall fall, Live live) {
		double surface = fall.tipEnd() + EMBED_DEPTH;
		Vec3 at = new Vec3(fall.x(), surface, fall.z());
		// the ground around the point splits open (soft ground only)
		int r = Mth.ceil(CRATER_RADIUS);
		BlockPos center = BlockPos.containing(at);
		var random = level.getRandom();
		for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -2, -r), center.offset(r, 0, r))) {
			double d = Math.sqrt(pos.distToCenterSqr(at.x, pos.getY() + 0.5, at.z));
			if (d <= CRATER_RADIUS && random.nextFloat() < 0.75F * (1.0F - (float) (d / (CRATER_RADIUS + 1.0)))) {
				smash(player, level, pos.immutable(), CRATER_HARDNESS, true);
			}
		}
		Debris.ring(level, center.below(), Mth.ceil(IMPACT_RADIUS), 1.6F);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 200, 2.5, 0.5, 2.5, 0.25);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.5, at.z, 60, 3.0, 0.3, 3.0, 0.08);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 3.0F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 2.0F, 0.6F);

		// everything near the point is struck and thrown back
		AABB box = new AABB(at.x - IMPACT_RADIUS, at.y - 2.0, at.z - IMPACT_RADIUS, at.x + IMPACT_RADIUS, at.y + 4.0, at.z + IMPACT_RADIUS);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 away = victim.position().subtract(at);
			double d = Math.sqrt(away.x * away.x + away.z * away.z);
			if (d > IMPACT_RADIUS) {
				continue;
			}
			float share = (float) (1.0 - d / IMPACT_RADIUS);
			if (!live.hit.contains(victim.getId())) {
				victim.hurtServer(level, level.damageSources().playerAttack(player), IMPACT_DAMAGE * share);
				ArsenalVoid.voidHit(victim, player, false);
			}
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			if (resist > 0.0) {
				Vec3 flat = d < 1.0E-3 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(away.x / d, 0.0, away.z / d);
				victim.push(flat.x * KNOCKBACK * share * resist, KNOCKBACK_UP * share * resist, flat.z * KNOCKBACK * share * resist);
				victim.syncVelocity = true;
			}
		}
		// the ground shakes for everyone near
		com.jaymospowers.core.Shakes.around(level, at, IMPACT_RADIUS * 5.0, 2.5F);
	}

	/**
	 * G, while the sword stands in the ground: the rift opens again now instead of waiting out {@link #STAND}, and it's
	 * hauled back up as usual. Shifts the fall's start so every client's timeline jumps along with it. False (G does
	 * what it otherwise does) when no sword is standing.
	 */
	static boolean recall(ServerPlayer player) {
		Fall fall = player.getAttached(FALL);
		Live live = LIVE.get(player.getUUID());
		if (fall == null || live == null || !live.landed) {
			return false;
		}
		long now = player.level().getGameTime();
		long reopen = (long) Math.ceil(fall.reopensAt());
		if (now - fall.start() >= reopen) {
			// already on its way back
			return false;
		}
		// the next tick lands on the reopening, so its sound and motes still play
		player.setAttached(FALL, new Fall(now - reopen + 1, fall.x(), fall.z(), fall.portalY(), fall.tipEnd(), fall.angle()));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.damocles_recalled").withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	/** The power went away: the rift and the sword go with it. */
	static void onLost(ServerPlayer player) {
		player.removeAttached(FALL);
		LIVE.remove(player.getUUID());
	}
}
