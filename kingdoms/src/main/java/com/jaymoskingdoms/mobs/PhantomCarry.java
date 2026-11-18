package com.jaymoskingdoms.mobs;

import com.jaymoskingdoms.Downed;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.phys.Vec3;

/**
 * Phantoms that carry (the Clouds and Bits plan's Mobs patch; lore: "Phantoms that carry"). The real night-sky phantoms
 * (not the living nightmare's hallucinations, which are never entities): when one's swoop lands on a player standing
 * in the open, it may take hold ({@link #GRAB_CHANCE}) and climb with them hanging from it, the player riding it so
 * their view is never touched. After {@link #CARRY_MIN} to {@link #CARRY_MAX} ticks, or once it has lost a third of its
 * health since taking hold, it lets go; the carried player's blows on it count double, and sneaking wriggles them free
 * (the ordinary dismount). Fall damage is the ordinary kind: letting go is just letting go. Never a player in a vehicle,
 * downed, asleep, in creative or spectator; a phantom that has let go, or a player who got free, waits out
 * {@link #COOLDOWN} before it happens again.
 */
public final class PhantomCarry {
	/** The chance a swoop that lands on a player in the open takes hold (1 in 5). */
	static final float GRAB_CHANCE = 0.2F;
	/** How long it carries (ticks): 3 to 6 seconds. */
	static final int CARRY_MIN = 60, CARRY_MAX = 120;
	/** It lets go once it has lost this share of its maximum health since taking hold. */
	static final float HEALTH_SHARE = 1.0F / 3.0F;
	/** How much more its carried player's blows hurt it. */
	static final float DAMAGE_FACTOR = 2.0F;
	/** Its climb while it carries: added to its upward speed each tick (blocks per tick), up to this. */
	static final double CLIMB = 0.06, CLIMB_MAX = 0.4;
	/** Ticks before the same phantom, or the same player, may be taken hold of again. */
	static final int COOLDOWN = 400;

	/** What a phantom is doing: whom it holds, when it lets go, and the health below which it does. */
	private record Hold(UUID player, long until, float lowest) {
	}

	/** The phantoms carrying someone, by their id. */
	private static final Map<UUID, Hold> HOLDING = new HashMap<>();
	/** The game time before which a phantom or a player (by id) isn't taken hold of or carried again. */
	private static final Map<UUID, Long> AFTER = new HashMap<>();

	private PhantomCarry() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && source.getEntity() instanceof Phantom phantom) {
				swooped(phantom, player);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(PhantomCarry::tick);
		// a carried player who logged out comes back on the phantom (vanilla saves the ride) with no hold left: let go
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.player.getVehicle() instanceof Phantom) {
				handler.player.stopRiding();
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDING.clear();
			AFTER.clear();
		});
	}

	/** A phantom's swoop landed on {@code player}: now and then it takes hold. */
	private static void swooped(Phantom phantom, ServerPlayer player) {
		if (!(phantom.level() instanceof ServerLevel level) || HOLDING.containsKey(phantom.getUUID()) || phantom.isVehicle()
			|| phantom.isPassenger() || !phantom.isAlive() || level.getRandom().nextFloat() >= GRAB_CHANCE) {
			return;
		}
		long now = level.getGameTime();
		if (player.isPassenger() || player.isSpectator() || player.isCreative() || player.isSleeping() || !player.isAlive()
			|| player.entityTags().contains(Downed.DOWNED_TAG) || !level.canSeeSky(player.blockPosition().above())
			|| waiting(phantom.getUUID(), now) || waiting(player.getUUID(), now)) {
			return;
		}
		if (!player.startRiding(phantom, true, true)) {
			return;
		}
		long until = now + CARRY_MIN + level.getRandom().nextInt(CARRY_MAX - CARRY_MIN + 1);
		float lowest = phantom.getHealth() - phantom.getMaxHealth() * HEALTH_SHARE;
		HOLDING.put(phantom.getUUID(), new Hold(player.getUUID(), until, lowest));
		level.playSound(null, phantom.getX(), phantom.getY(), phantom.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.5F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.phantom.grabbed", phantom.getDisplayName())
			.withStyle(ChatFormatting.RED));
	}

	private static boolean waiting(UUID id, long now) {
		Long after = AFTER.get(id);
		return after != null && now < after;
	}

	private static void tick(MinecraftServer server) {
		if (!AFTER.isEmpty() && server.getTickCount() % 200 == 0) {
			long now = server.overworld().getGameTime();
			AFTER.values().removeIf(after -> now >= after);
		}
		if (HOLDING.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Hold>> each = HOLDING.entrySet().iterator();
		while (each.hasNext()) {
			Map.Entry<UUID, Hold> entry = each.next();
			Hold hold = entry.getValue();
			Phantom phantom = null;
			ServerPlayer player = server.getPlayerList().getPlayer(hold.player());
			if (player != null && player.level() instanceof ServerLevel level) {
				phantom = level.getEntity(entry.getKey()) instanceof Phantom found ? found : null;
			}
			if (phantom == null || !phantom.isAlive() || player.getVehicle() != phantom || !player.isAlive()) {
				// it died, or they were shaken off, hauled off or took themselves out of its grip
				finish(entry.getKey(), hold, phantom, player, false);
				each.remove();
				continue;
			}
			ServerLevel level = (ServerLevel)phantom.level();
			if (level.getGameTime() >= hold.until() || phantom.getHealth() <= hold.lowest()) {
				finish(entry.getKey(), hold, phantom, player, true);
				each.remove();
				continue;
			}
			// it climbs with its catch, and the catch's fall is counted only from where it lets go
			Vec3 motion = phantom.getDeltaMovement();
			phantom.setDeltaMovement(motion.x, Math.min(motion.y + CLIMB, CLIMB_MAX), motion.z);
			player.resetFallDistance();
		}
	}

	/** It lets go (or is let go of): both wait out the cooldown. */
	private static void finish(UUID phantomId, Hold hold, Phantom phantom, ServerPlayer player, boolean letGo) {
		long now = phantom != null ? phantom.level().getGameTime() : player != null ? player.level().getGameTime() : 0L;
		AFTER.put(phantomId, now + COOLDOWN);
		AFTER.put(hold.player(), now + COOLDOWN);
		if (letGo && player != null && player.getVehicle() == phantom) {
			player.stopRiding();
			player.resetFallDistance();
		}
	}

	/** (From the hurt mixin) How hard {@code source} hurts {@code victim}: double when a carried player hits their carrier. */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (!HOLDING.isEmpty() && victim instanceof Phantom phantom) {
			Hold hold = HOLDING.get(phantom.getUUID());
			Entity attacker = source.getEntity();
			if (hold != null && attacker != null && attacker.getUUID().equals(hold.player())) {
				return amount * DAMAGE_FACTOR;
			}
		}
		return amount;
	}
}
