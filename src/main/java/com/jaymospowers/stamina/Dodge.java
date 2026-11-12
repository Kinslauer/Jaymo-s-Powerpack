package com.jaymospowers.stamina;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.Armed;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.Ledge;
import com.jaymospowers.core.wounds.Breaks;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.5) The dodge, replacing Combat Roll: our own after Elenai Dodge 2's ideas (see {@code docs/dodge.md}). Left
 * Alt, or Sneak tapped twice, hops you {@link #SPEED} along the way you're holding (back if you hold nothing), on the
 * ground only, for {@link #COST} of your {@link Feathers}. For the first {@link #EVADE_TICKS} of it a melee blow or a
 * projectile misses you. Hunger: {@link #EXHAUSTION}.
 * <p>Movement is the client's: it hops at once and tells the server ({@link Start}); the server checks the rules
 * ({@link #canDodge}, the cooldown, the feathers), spends, opens the evasion window, plays the whoosh for everyone and
 * tells the powers ({@link #onDodge}: Darktide's evasions, the Corrupted blink).
 * <p>(0.15.74) The roll: {@link #ROLLING} is synced to everyone so they see the body tumble the way you went
 * ({@code DodgeRollPose}); your own client draws it at once.
 */
public final class Dodge {
	// ===== the knobs ================================================================================================
	/** Feathers a dodge costs (half-feathers: 6 is three icons). */
	public static final int COST = 6;
	/** The hop: speed along the ground and up (blocks/tick). */
	public static final double SPEED = 0.85, UP = 0.2;
	/** Ticks between dodges. */
	public static final int COOLDOWN = 10;
	/** Ticks from the start in which a blow or projectile misses. */
	public static final int EVADE_TICKS = 5;
	/** Hunger (exhaustion; 4 is a hunger point) per dodge. */
	public static final float EXHAUSTION = 0.4F;
	/** The server lets a dodge through this many ticks early (client and server clocks differ a little). */
	static final int COOLDOWN_SLACK = 3;
	/** How long the drawn roll takes (ticks): one full tumble. */
	public static final int ROLL_TICKS = 10;
	/** The flip: feathers it costs, the push along the way you hold and up (blocks/tick), how much of your own speed is kept, and how long the turn takes (ticks). */
	public static final int FLIP_COST = 4;
	public static final double FLIP_SPEED = 0.42, FLIP_UP = 0.3, FLIP_KEEP = 0.6;
	public static final int FLIP_TICKS = 10;
	/** The powers whose holders get the flip's lift ({@link #flipLift}): they fly, or are stronger, or bend their own weight. */
	public static final java.util.Set<String> LIFT_POWERS = java.util.Set.of("web", "gravity", "darktide", "wind", "zfighter", "smoke");
	// ================================================================================================================

	/** Client → server: a dodge started along {@code (x, z)} (a unit vector on the ground). */
	public record Start(float x, float z) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosPowers.id("dodge"));
		public static final StreamCodec<ByteBuf, Start> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Start::x, ByteBufCodecs.FLOAT, Start::z, Start::new);

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Client → server: a flip started in the air along {@code (x, z)} (a unit vector on the ground). */
	public record Flip(float x, float z) implements CustomPacketPayload {
		public static final Type<Flip> TYPE = new Type<>(JaymosPowers.id("dodge_flip"));
		public static final StreamCodec<ByteBuf, Flip> CODEC = StreamCodec.composite(ByteBufCodecs.FLOAT, Flip::x, ByteBufCodecs.FLOAT, Flip::z, Flip::new);

		@Override
		public Type<Flip> type() {
			return TYPE;
		}
	}

	/** A dodge's roll: when it started, which way it went (a unit vector on the ground), and whether it's the flip in the air. */
	public record Rolling(long start, float x, float z, boolean flip) {
		public static final StreamCodec<ByteBuf, Rolling> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Rolling::start,
			ByteBufCodecs.FLOAT, Rolling::x,
			ByteBufCodecs.FLOAT, Rolling::z,
			ByteBufCodecs.BOOL, Rolling::flip,
			Rolling::new);

		/** How long this one's turn takes (ticks). */
		public int ticks() {
			return this.flip ? FLIP_TICKS : ROLL_TICKS;
		}
	}

	/** (0.15.74) On a player who's dodging (or last did): synced to everyone, who draw the roll. */
	public static final AttachmentType<Rolling> ROLLING = AttachmentRegistry.create(JaymosPowers.id("dodge_roll"),
		builder -> builder.syncWith(Rolling.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Told of every dodge the server lets through. */
	@FunctionalInterface
	public interface Listener {
		void dodged(ServerPlayer player, Vec3 direction);
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	/** Server: game time of each player's last dodge. */
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private Dodge() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Start.TYPE, Start.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Start.TYPE, (payload, ctx) -> start(ctx.player(), payload));
		PayloadTypeRegistry.serverboundPlay().register(Flip.TYPE, Flip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Flip.TYPE, (payload, ctx) -> flip(ctx.player(), payload));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && evades(player, source)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	/** Tells {@code listener} of every dodge from now on. */
	public static void onDodge(Listener listener) {
		LISTENERS.add(listener);
	}

	/**
	 * Whether {@code player} may dodge now (both sides): on their own feet on the ground, in none of the powers' own
	 * moves, and not on a broken leg out of a splint (Wounds' {@link Breaks#legsUsable}, synced).
	 */
	public static boolean canDodge(Player player) {
		return player.onGround() && Ledge.canHang(player) && !Ledge.onLedge(player) && !player.isUsingItem() && Breaks.legsUsable(player);
	}

	/**
	 * Andy: "its tied to weapon forms but activated by dodging in midair, after a jump". Whether {@code player} may turn
	 * the flip now (both sides): in the air on their own legs, a weapon in hand ({@link Armed#kind}), and Weapon Forms
	 * learnt ({@link KingdomsCompat#knowsForms}). That it follows a jump is the client's to know ({@code DodgeClient}).
	 */
	public static boolean canFlip(Player player) {
		return !player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.getAbilities().flying && !player.isPassenger()
			&& !player.isUsingItem() && Ledge.canHang(player) && !Ledge.onLedge(player) && Breaks.legsUsable(player)
			&& Armed.kind(player.getMainHandItem()) != null && KingdomsCompat.knowsForms(player);
	}

	/**
	 * Andy: the flip "gives a lift if you have a flight ability. or increased strength / weight manipulation (web gravity,
	 * darktide, wind, zfighter, smoke, etc)". The lift {@code player}'s flip gets (both sides): {@link #FLIP_UP} for a
	 * {@link #LIFT_POWERS} holder, anyone who may fly, anyone under Strength and anyone with brawn (Might, size); none
	 * for the rest, whose flip only carries the jump they made.
	 */
	public static double flipLift(Player player) {
		net.minecraft.resources.Identifier power = player.getAttached(com.jaymospowers.core.Powers.POWER);
		boolean lifted = power != null && LIFT_POWERS.contains(power.getPath()) || player.getAbilities().mayfly
			|| player.hasEffect(net.minecraft.world.effect.MobEffects.STRENGTH) || com.jaymospowers.power.web.KitchenCompat.brawn(player) > 0;
		return lifted ? FLIP_UP : 0.0;
	}

	private static void flip(ServerPlayer player, Flip flip) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		// (the client checked it was in the air as it turned; by now the server may already have it landed)
		if (last != null && now - last < COOLDOWN - COOLDOWN_SLACK || !(player.onGround() || canFlip(player))
			|| Armed.kind(player.getMainHandItem()) == null || !KingdomsCompat.knowsForms(player)) {
			return;
		}
		if (!Feathers.spend(player, FLIP_COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		player.causeFoodExhaustion(EXHAUSTION);
		Vec3 dir = new Vec3(flip.x(), 0.0, flip.z());
		dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : dir.normalize();
		player.setAttached(ROLLING, new Rolling(now, (float)dir.x, (float)dir.z, true));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.5F, 1.1F);
		for (Listener listener : LISTENERS) {
			listener.dodged(player, dir);
		}
	}

	private static void start(ServerPlayer player, Start start) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		// the client checked the ground as it hopped; by now the server may already have it in the air
		if (last != null && now - last < COOLDOWN - COOLDOWN_SLACK || !(Ledge.canHang(player) && !Ledge.onLedge(player) && Breaks.legsUsable(player))) {
			return;
		}
		if (!Feathers.spend(player, COST)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		player.causeFoodExhaustion(EXHAUSTION);
		Vec3 dir = new Vec3(start.x(), 0.0, start.z());
		dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()).reverse() : dir.normalize();
		player.setAttached(ROLLING, new Rolling(now, (float)dir.x, (float)dir.z, false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_STEP, SoundSource.PLAYERS, 0.6F, 0.8F);
		level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(), 4, 0.25, 0.02, 0.25, 0.02);
		for (Listener listener : LISTENERS) {
			listener.dodged(player, dir);
		}
	}

	/** Whether {@code player} is in a dodge's first ticks. */
	public static boolean evading(ServerPlayer player) {
		Long last = LAST.get(player.getUUID());
		return last != null && player.level().getGameTime() - last <= EVADE_TICKS;
	}

	/** A melee blow from something living, or a projectile, landing in the evasion window misses. */
	private static boolean evades(ServerPlayer player, DamageSource source) {
		if (!evading(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return false;
		}
		if (!(source.getDirectEntity() instanceof Projectile) && !(source.getDirectEntity() instanceof LivingEntity)) {
			return false;
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.6F, 1.6F);
		return true;
	}
}
