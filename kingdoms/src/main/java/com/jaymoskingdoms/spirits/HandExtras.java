package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Mana;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.237) The second patch's shared hands, the moves every spirit has after {@link HandMoves#SHARED}. Andy: "a mage hand
 * type of ability that mimics the way stands work".
 * <ul>
 * <li><b>Spirit armour</b> (level 6): the arms wrap round you like plate for six seconds. Blows land for half (the hit is
 * taken, then half of it given back), and you cannot be knocked back.</li>
 * <li><b>Spirit ride</b> (level 4): the hands carry you a short way in a leap along your look, with no fall damage from it.</li>
 * </ul>
 * The knockback of steady hands ({@link Ambrose#steady}) is eased here too, since armour and steadiness work the same way.
 */
public final class HandExtras {
	/** Armour: how long it lasts (ticks), the share of each blow given back, the ticks between re-shows of the pose. */
	static final int ARMOUR_TICKS = 120, ARMOUR_POSE_EVERY = 15;
	static final float ARMOUR_GIVEBACK = 0.5F;
	/** The share of knockback that gets through steady hands. */
	static final double STEADY_KNOCK = 0.5;
	/** Ride: the push along your look (blocks per tick), the lift, and the most ticks it can last. */
	static final double RIDE_FORWARD = 1.6, RIDE_UP = 0.6;
	static final int RIDE_MAX_TICKS = 100, RIDE_MIN_TICKS = 10, RIDE_GRACE_TICKS = 10;
	/** The chance a cast move is remarked on. */
	static final float MOVE_SAY = 0.15F;

	/** The moves every spirit has, after {@link HandMoves#SHARED}. */
	public static final List<Move> SHARED = new ArrayList<>();

	/** A leap under way: when it began and (once it has landed) when. */
	private static final class Riding {
		final long began;
		long landed;

		Riding(long began) {
			this.began = began;
		}
	}

	/** A player's velocity before a blow, to ease the knockback after it. */
	private record Pre(Vec3 delta, long time) {
	}

	/** Game time each player's armour ends. */
	private static final Map<UUID, Long> ARMOUR = new HashMap<>();
	/** Leaps under way. */
	private static final Map<UUID, Riding> RIDING = new HashMap<>();
	/** Velocities before blows landed this tick. */
	private static final Map<UUID, Pre> PRE = new HashMap<>();

	static {
		SHARED.add(new Ambrose.Def("ride", 14.0F, 100, 4, Poses.RIDE, Mana.WILL, HandExtras::ride));
		SHARED.add(new Ambrose.Def("armour", 20.0F, 300, 6, Poses.ARMOUR, Mana.WARD, HandExtras::armour));
	}

	private HandExtras() {
	}

	static void register() {
		for (Move move : SHARED) {
			Moves.register(move);
		}
		HandMoves.SHARED.addAll(SHARED);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(HandExtras::allowDamage);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && player.isAlive()) {
				if (taken > 0.0F && armoured(player)) {
					player.heal(taken * ARMOUR_GIVEBACK);
					((ServerLevel)player.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
				}
				Pre pre = PRE.get(player.getUUID());
				if (pre != null && pre.time() == player.level().getGameTime() && ease(player, pre)) {
					PRE.remove(player.getUUID());
				}
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(HandExtras::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ARMOUR.clear();
			RIDING.clear();
			PRE.clear();
		});
	}

	/** A player left: forget what is kept for them. */
	static void forget(UUID id) {
		ARMOUR.remove(id);
		RIDING.remove(id);
		PRE.remove(id);
	}

	/** Whether the arms are wrapped round {@code player} like plate now. */
	public static boolean armoured(ServerPlayer player) {
		Long until = ARMOUR.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until && Spirits.out(player);
	}

	/** Whether {@code player} is being carried by a leap of their spirit's hands. */
	public static boolean riding(ServerPlayer player) {
		return RIDING.containsKey(player.getUUID());
	}

	// ---- armour -----------------------------------------------------------------------------------------------------

	private static boolean armour(ServerPlayer player, SpiritState state, int charge) {
		ARMOUR.put(player.getUUID(), player.level().getGameTime() + ARMOUR_TICKS);
		Spirits.toggle(player, SpiritState.ARMOUR, true);
		ServerLevel level = (ServerLevel)player.level();
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.9, 0.5, 0.6);
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.8, 0.5, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 0.8F);
		return true;
	}

	/** A blow is about to land: note the velocity it will change, if armour or steady hands will ease the knockback. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (victim instanceof ServerPlayer player && amount > 0.0F && (armoured(player) || Ambrose.steady(player))) {
			PRE.put(player.getUUID(), new Pre(player.getDeltaMovement(), player.level().getGameTime()));
		}
		if (victim instanceof ServerPlayer player && RIDING.containsKey(player.getUUID()) && source.is(DamageTypeTags.IS_FALL)) {
			player.resetFallDistance();
			return false;
		}
		return true;
	}

	/** Eases the knockback a blow gave {@code player}: none in armour, half with steady hands. False if none has been given yet. */
	private static boolean ease(ServerPlayer player, Pre pre) {
		Vec3 push = player.getDeltaMovement().subtract(pre.delta());
		if (push.lengthSqr() < 1.0E-4) {
			return false;
		}
		double keep = armoured(player) ? 0.0 : Ambrose.steady(player) ? STEADY_KNOCK : 1.0;
		player.setDeltaMovement(pre.delta().add(push.scale(keep)));
		player.syncVelocity = true;
		return true;
	}

	// ---- ride -------------------------------------------------------------------------------------------------------

	private static boolean ride(ServerPlayer player, SpiritState state, int charge) {
		if (player.isPassenger() || player.isFallFlying()) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : flat.normalize();
		player.setDeltaMovement(flat.x * RIDE_FORWARD, RIDE_UP, flat.z * RIDE_FORWARD);
		player.syncVelocity = true;
		player.resetFallDistance();
		RIDING.put(player.getUUID(), new Riding(player.level().getGameTime()));
		ServerLevel level = (ServerLevel)player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 16, 0.4, 0.1, 0.4, 0.05);
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 0.5, player.getZ(), 16, 0.5, 0.3, 0.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (ARMOUR.isEmpty() && RIDING.isEmpty() && PRE.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Long>> it = ARMOUR.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive() || owner.level().getGameTime() > entry.getValue() || !Spirits.out(owner)) {
				it.remove();
				if (owner != null) {
					Spirits.toggle(owner, SpiritState.ARMOUR, false);
				}
			} else if (now % ARMOUR_POSE_EVERY == 0) {
				Spirits.show(owner, Poses.ARMOUR);
			}
		}
		for (Iterator<Map.Entry<UUID, Riding>> it = RIDING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Riding> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || !owner.isAlive()) {
				it.remove();
				continue;
			}
			Riding ride = entry.getValue();
			long age = owner.level().getGameTime() - ride.began;
			if (age > RIDE_MAX_TICKS) {
				it.remove();
			} else if (age > RIDE_MIN_TICKS && (owner.onGround() || owner.isInWater())) {
				// (a short grace after landing: the fall damage can come a tick after the ground)
				if (ride.landed == 0L) {
					ride.landed = owner.level().getGameTime();
				} else if (owner.level().getGameTime() - ride.landed > RIDE_GRACE_TICKS) {
					it.remove();
				}
			}
		}
		// (a blow noted but never eased, because it was cancelled or gave no knockback)
		for (Iterator<Map.Entry<UUID, Pre>> it = PRE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Pre> entry = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
			if (owner == null || owner.level().getGameTime() > entry.getValue().time()) {
				it.remove();
			} else if (ease(owner, entry.getValue())) {
				it.remove();
			}
		}
	}
}
