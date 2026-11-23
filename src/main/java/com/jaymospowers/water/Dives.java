package com.jaymospowers.water;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.freerun.moves.SkyDive;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.phys.Vec3;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * (Motion patch 2) Going into water from a height, after Actions &amp; Stuff's dives. Fall from {@link #MIN_FALL} blocks or
 * more into water at least {@link #MIN_DEPTH} deep and how you went in shows:
 * <ul>
 * <li><b>Cannonball:</b> Sneak held as you hit the water: a big splash, a boom, and the fish nearby scatter.</li>
 * <li><b>Clean dive:</b> the Dodge key (Alt) pressed over water ({@link #diveFrom}): a small, neat splash and you slip
 * deeper, faster. Andy (2026-11-22): "can we also add the water dive activate upon pressing alt into the water instead of
 * the shift activation, i've never actually gotten that to work ... the animation of a player diving off the gound into
 * the water would be smoother with the dive rather than a jump into the air beforehand. they should still be able to
 * dive from a free fall, with the same button." Standing at an edge over deep water, it dives you straight off it, head
 * first, with no hop first; falling over water, it turns the fall into a head-first dive. Either way it's a clean dive
 * however far the drop ({@link #DIVE_MIN_FALL}), and everyone sees you go in head first ({@link #DIVING}).</li>
 * <li><b>Belly flop:</b> neither. A slap and a splash, and real fall damage scaled by height and impact speed. A
 * head-first entry (looking down past {@link #DIVE_LOOK_DOWN}, or Leap of Faith's dive) counts as a clean dive.</li>
 * </ul>
 * The splash is our own (vanilla splash and bubble particles), sized by how far you fell and how big you are. Water
 * already takes fall damage away; nothing here adds any but the flop's sting.
 */
public final class Dives {
	// ===== the knobs ================================================================================================
	/** Fell at least this far (blocks) into water at least this deep (blocks). */
	static final double MIN_FALL = 4.0;
	static final int MIN_DEPTH = 2;
	/** The Dodge key's dive: from an edge, deep water this far ahead at most (blocks, from the feet) and this far down at most. */
	public static final double DIVE_AHEAD = 2.5, DIVE_DROP = 14.0;
	/** Falling, deep water straight below within this far (blocks). */
	public static final double DIVE_BELOW = 48.0;
	/** Off an edge: the push out and the little lift (blocks a tick), only enough to clear the edge, not a jump. */
	public static final double LAUNCH_OUT = 0.42, LAUNCH_UP = 0.06;
	/** A dive keyed with the Dodge key counts from any height above this (blocks). */
	static final double DIVE_MIN_FALL = 0.8;
	/** (Andy, 2026-11-23) The Dodge key in mid air over land, when no flip or trick fits: a head-first dive, pushed this
	 *  far along the movement keys (blocks a tick) with at least this lift, ending in a landing roll held this many ticks. */
	public static final double AIR_DIVE_PUSH = 0.55, AIR_DIVE_LIFT = 0.12;
	public static final int AIR_DIVE_ROLL_TICKS = 40;
	/** A belly flop from this far (blocks) stings for real: {@link #FLOP_PER_BLOCK} HP per block fallen past
	 *  {@link #MIN_FALL}, plus {@link #FLOP_SPEED} HP per block a tick of downward impact speed, capped at
	 *  {@link #FLOP_MAX}. A cannonball (feet first, tucked) is the middle case. */
	static final float FLOP_PER_BLOCK = 0.5F, FLOP_SPEED = 1.5F, FLOP_MAX = 8.0F;
	static final float CANNONBALL_PER_BLOCK = 0.25F, CANNONBALL_SPEED = 0.6F, CANNONBALL_MAX = 4.0F;
	/** A "dive" only counts head-first: the look direction must point at least this far below horizontal
	 *  ({@code 1.0} is straight down). */
	static final double DIVE_LOOK_DOWN = 0.65;
	/** A clean dive carries you down this much faster (blocks a tick). */
	static final double DIVE_PUSH = 0.6;
	/** The splash: drops per block fallen (times body width), at most this many; how far a cannonball scatters fish. */
	static final int DROPS_PER_BLOCK = 6, MAX_DROPS = 160;
	static final double SCATTER_RANGE = 8.0, SCATTER_PUSH = 0.6;
	// ================================================================================================================

	enum Entry {
		CANNONBALL, DIVE, FLOP
	}

	/** The last water entry and when it began, synced to every client so the entry pose shows on everyone in view:
	 * top byte is 1 flop / 2 dive / 3 cannonball (0 none), the rest is the game time it began. */
	public static final AttachmentType<Long> ENTRY = AttachmentRegistry.create(JaymosPowers.id("water_entry"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));
	/** On a player diving by the Dodge key, from when (game time) till they're in the water: synced, so everyone draws them head first. */
	public static final AttachmentType<Long> DIVING = AttachmentRegistry.create(JaymosPowers.id("water_diving"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** The client's Dodge key over water: a dive ({@link #diveFrom}). */
	public record DiveKey() implements CustomPacketPayload {
		public static final Type<DiveKey> TYPE = new Type<>(JaymosPowers.id("dive_key"));
		public static final StreamCodec<ByteBuf, DiveKey> CODEC = StreamCodec.unit(new DiveKey());

		@Override
		public Type<DiveKey> type() {
			return TYPE;
		}
	}

	/** Where a dive by the Dodge key would go from: {@link #NO_DIVE}, {@link #EDGE} (standing at one) or {@link #FALLING}. */
	public static final int NO_DIVE = 0, EDGE = 1, FALLING = 2;

	private static final class Watch {
		double fall;
		boolean inWater;
		boolean wasSkydiving;
	}

	private static final Map<UUID, Watch> WATCH = new HashMap<>();

	private Dives() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Dives::tick);
		PayloadTypeRegistry.serverboundPlay().register(DiveKey.TYPE, DiveKey.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(DiveKey.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			// (the client launched itself off the edge already: a step behind, it may be just off the ground here)
			if (!player.isSpectator() && !player.isPassenger() && !player.isInWater() && (diveFrom(player) != NO_DIVE || canAirDive(player))) {
				player.setAttached(DIVING, player.level().getGameTime());
			}
		});
	}

	/**
	 * Whether the Dodge key dives {@code player} into water: {@link #EDGE} standing with deep water ahead and below the
	 * edge (within {@link #DIVE_AHEAD} and {@link #DIVE_DROP}), {@link #FALLING} in the air over deep water (within
	 * {@link #DIVE_BELOW}), else {@link #NO_DIVE}. Both sides ask it.
	 */
	public static int diveFrom(Player player) {
		if (player.isInWater() || player.isPassenger() || player.getAbilities().flying || player.isFallFlying()) {
			return NO_DIVE;
		}
		Level level = player.level();
		if (!player.onGround()) {
			return deepBelow(level, player.getX(), player.getY(), player.getZ(), DIVE_BELOW) ? FALLING : NO_DIVE;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		for (double ahead = 0.8; ahead <= DIVE_AHEAD; ahead += 0.4) {
			double x = player.getX() + look.x * ahead, z = player.getZ() + look.z * ahead;
			BlockPos at = BlockPos.containing(x, player.getY(), z);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				return NO_DIVE; // a wall in the way
			}
			if (deepBelow(level, x, player.getY(), z, DIVE_DROP)) {
				return EDGE;
			}
		}
		return NO_DIVE;
	}

	/** The Dodge key's dive over land: in the air, not flying, gliding, riding or swimming. Both sides ask it. */
	public static boolean canAirDive(Player player) {
		return !player.onGround() && !player.isInWater() && !player.isPassenger() && !player.isSpectator()
			&& !player.getAbilities().flying && !player.isFallFlying();
	}

	/** Deep water (at least {@link #MIN_DEPTH}) straight below ({@code x}, {@code y}, {@code z}) within {@code reach}, with only air on the way. */
	private static boolean deepBelow(Level level, double x, double y, double z, double reach) {
		BlockPos.MutableBlockPos pos = BlockPos.containing(x, y, z).mutable();
		for (int i = 0; i <= reach; i++) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				for (int d = 1; d < MIN_DEPTH; d++) {
					if (!level.getFluidState(pos.move(0, -1, 0)).is(FluidTags.WATER)) {
						return false;
					}
				}
				return true;
			}
			if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return false;
			}
			pos.move(0, -1, 0);
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Watch w = WATCH.computeIfAbsent(player.getUUID(), id -> new Watch());
			boolean inWater = player.isInWater();
			boolean diving = player.hasAttached(DIVING);
			if (inWater && !w.inWater && w.fall >= (diving ? DIVE_MIN_FALL : MIN_FALL) && !player.isSpectator() && !player.isPassenger() && deep(player)) {
				Entry entry = diving ? Entry.DIVE : entry(player, w.wasSkydiving);
				enter(player, entry, w.fall);
			}
			if (diving && (inWater || player.onGround() || player.isPassenger())) {
				player.removeAttached(DIVING);
			}
			if ((!inWater || player.onGround()) && player.getAttachedOrElse(ENTRY, 0L) != 0L) {
				player.setAttached(ENTRY, 0L); // only when it changes, so it isn't synced every tick
			}
			w.inWater = inWater;
			w.fall = inWater || player.onGround() ? 0.0 : player.fallDistance;
			w.wasSkydiving = SkyDive.active(player);
		}
		if (server.getTickCount() % 1200 == 0) {
			WATCH.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	/** Water at least {@link #MIN_DEPTH} deep from the feet down. */
	private static boolean deep(ServerPlayer player) {
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		for (int i = 0; i < MIN_DEPTH; i++) {
			if (!player.level().getFluidState(pos).is(FluidTags.WATER)) {
				return false;
			}
			pos.move(0, -1, 0);
		}
		return true;
	}

	/** How the player went in without the Dodge key's dive: Sneak held is a cannonball, head first a dive, else a belly flop. */
	private static Entry entry(ServerPlayer player, boolean wasSkydiving) {
		if (wasSkydiving || SkyDive.active(player)) {
			return Entry.FLOP; // landing from a sky dive (spread flat) is the belly flop, even if SkyDive folded you first
		}
		if (Freerun.dives(player) || player.getLookAngle().y <= -DIVE_LOOK_DOWN && !player.isShiftKeyDown()) {
			return Entry.DIVE; // head first without Sneak (Leap of Faith, a Wind user's dive, or just looking down)
		}
		return player.isShiftKeyDown() ? Entry.CANNONBALL : Entry.FLOP;
	}

	static void enter(ServerPlayer player, Entry entry, double fall) {
		ServerLevel level = player.level();
		player.setAttached(ENTRY, ((long) (entry.ordinal() + 1) << 56) | level.getGameTime() & 0x00FF_FFFF_FFFF_FFFFL);
		Vec3 at = new Vec3(player.getX(), surface(player), player.getZ());
		float size = Math.max(0.6F, player.getBbWidth()) / 0.6F;
		int drops = (int)Math.min(MAX_DROPS, fall * DROPS_PER_BLOCK * size * switch (entry) {
			case CANNONBALL -> 2.0;
			case DIVE -> 0.35;
			case FLOP -> 1.0;
		});
		double spread = player.getBbWidth() * (entry == Entry.CANNONBALL ? 1.2 : entry == Entry.DIVE ? 0.3 : 0.9);
		double lift = entry == Entry.CANNONBALL ? 0.6 : entry == Entry.DIVE ? 0.15 : 0.35;
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.1, at.z, drops, spread, 0.1, spread, lift);
		level.sendParticles(ParticleTypes.BUBBLE, at.x, at.y - 0.6, at.z, drops / 3, spread, 0.4, spread, 0.05);
		switch (entry) {
			case CANNONBALL -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.6F, 0.6F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.25F, 1.8F);
				hurt(player, Entry.CANNONBALL, fall);
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 1.0, at.z, drops / 2, spread * 0.5, 0.6, spread * 0.5, 0.9);
				for (WaterAnimal fish : level.getEntitiesOfClass(WaterAnimal.class, player.getBoundingBox().inflate(SCATTER_RANGE))) {
					Vec3 away = fish.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					if (away.lengthSqr() < 1.0E-4) {
						continue;
					}
					fish.setDeltaMovement(fish.getDeltaMovement().add(away.normalize().scale(SCATTER_PUSH)));
					fish.needsSync = true;
				}
			}
			case DIVE -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.6F, 1.5F);
				Vec3 v = player.getDeltaMovement();
				player.setDeltaMovement(v.x, Math.min(v.y, 0.0) - DIVE_PUSH, v.z);
				player.needsSync = true;
			}
			case FLOP -> {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 0.9F);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.5F);
				hurt(player, Entry.FLOP, fall);
			}
		}
	}

	/** Hurt from a non-dive entry, scaled by fall height and downward impact speed. */
	private static void hurt(ServerPlayer player, Entry entry, double fall) {
		// A server player's delta movement stays near zero; the known movement is how far it really moved last tick.
		double speed = Math.max(0.0, -player.getKnownMovement().y);
		float damage = switch (entry) {
			case FLOP -> Mth.clamp((float) ((fall - MIN_FALL) * FLOP_PER_BLOCK + speed * FLOP_SPEED), 0.0F, FLOP_MAX);
			case CANNONBALL -> Mth.clamp((float) ((fall - MIN_FALL) * CANNONBALL_PER_BLOCK + speed * CANNONBALL_SPEED),
				0.0F, CANNONBALL_MAX);
			case DIVE -> 0.0F;
		};
		if (damage > 0.0F && !player.isCreative() && !player.isSpectator()) {
			player.hurtServer(player.level(), player.level().damageSources().fall(), damage);
		}
	}

	/** The top of the water the player went into. */
	private static double surface(ServerPlayer player) {
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		for (int i = 0; i < 4 && player.level().getFluidState(pos.above()).is(FluidTags.WATER); i++) {
			pos.move(0, 1, 0);
		}
		return pos.getY() + player.level().getFluidState(pos).getHeight(player.level(), pos);
	}
}
