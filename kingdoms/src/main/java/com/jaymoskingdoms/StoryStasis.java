package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.304) Andy: "players should be held in stasis high above anything, while creating a character, because right now
 * it has your character sitting in the overworld, hearing entities around and receiving damage". While a player makes
 * their character (the life story, MCA's creator, and the start being made ready) they hang far over the world where
 * they stood: nothing can hurt them, nothing hunts them, nothing is near enough to hear. When it's done they're let
 * down where they were taken from, and the story puts them at its start from there.
 */
public final class StoryStasis {
	/** How far over the world's build limit the held hang (blocks): far out of earshot of anything on the ground. */
	static final int ABOVE_BUILD_LIMIT = 48;
	/** How far a held player may drift from their spot before they're put back (blocks). */
	static final double DRIFT = 1.0;
	/** Mobs this near that go for a held player anyway are called off, this often (blocks, ticks). */
	static final int SHOO_RANGE = 48, SHOO_EVERY = 10;

	/** Where a held player was taken from, and what they could do before (put back as it was when they're let down). */
	private record Held(ResourceKey<Level> dimension, Vec3 from, float yRot, float xRot, Vec3 spot,
		boolean mayfly, boolean flying, boolean abilityInvulnerable, boolean invulnerable) {
	}

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private StoryStasis() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StoryStasis::tick);
		// let down before they're saved, so no one is left hanging in the sky (or able to fly) next time
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> release(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				release(player);
			}
			HELD.clear();
		});
		// a new body after a death: held afresh from where it woke (the old one's abilities went with it)
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> HELD.remove(newPlayer.getUUID()));
	}

	/** Whether {@code player} is still making their character, and so should be held. */
	static boolean owed(ServerPlayer player) {
		return LifeStory.owed(player) || LifeStory.departing(player) || McaCompat.destinyPending(player) || StoryGround.waiting(player);
	}

	/** Whether {@code player} hangs in stasis now. */
	public static boolean holding(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		boolean shoo = server.getTickCount() % SHOO_EVERY == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			// (the story set out once MCA's creator was done, however it was finished)
			if (LifeStory.departing(player) && !LifeStory.owed(player) && !McaCompat.destinyPending(player)) {
				LifeStory.depart(player);
			}
			Held held = HELD.get(player.getUUID());
			if (!owed(player)) {
				if (held != null) {
					release(player);
				}
				continue;
			}
			if (held == null) {
				held = hold(player);
			}
			keep(player, held);
			if (shoo) {
				for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SHOO_RANGE), m -> m.getTarget() == player)) {
					mob.setTarget(null);
				}
			}
		}
	}

	/** Takes {@code player} up out of the world, out of harm's way. */
	private static Held hold(ServerPlayer player) {
		ServerLevel level = player.level();
		Abilities abilities = player.getAbilities();
		Vec3 spot = new Vec3(player.getX(), level.getMaxY() + ABOVE_BUILD_LIMIT, player.getZ());
		Held held = new Held(level.dimension(), player.position(), player.getYRot(), player.getXRot(), spot,
			abilities.mayfly, abilities.flying, abilities.invulnerable, player.isPermanentlyInvulnerable());
		HELD.put(player.getUUID(), held);
		// flying, so their own client doesn't let them fall (and the server doesn't take them for a cheat)
		abilities.mayfly = true;
		abilities.flying = true;
		abilities.invulnerable = true;
		player.setPermanentlyInvulnerable(true);
		player.onUpdateAbilities();
		player.teleportTo(level, spot.x, spot.y, spot.z, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
		return held;
	}

	/** Each tick: still where they were put, still and unharmed. */
	private static void keep(ServerPlayer player, Held held) {
		if (player.level().dimension() != held.dimension() || player.position().distanceToSqr(held.spot()) > DRIFT * DRIFT) {
			player.teleportTo(player.level(), held.spot().x, held.spot().y, held.spot().z, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
		}
		if (!player.getAbilities().flying || !player.getAbilities().invulnerable) {
			player.getAbilities().flying = true;
			player.getAbilities().invulnerable = true;
			player.onUpdateAbilities();
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
		player.clearFire();
	}

	/** Lets {@code player} down where they were taken from, as they were (nothing if they weren't held). */
	static void release(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		Abilities abilities = player.getAbilities();
		abilities.mayfly = held.mayfly();
		abilities.flying = held.flying();
		abilities.invulnerable = held.abilityInvulnerable();
		player.setPermanentlyInvulnerable(held.invulnerable());
		player.onUpdateAbilities();
		ServerLevel level = player.level().getServer().getLevel(held.dimension());
		if (level == null) {
			level = player.level();
		}
		player.teleportTo(level, held.from().x, held.from().y, held.from().z, Set.<Relative>of(), held.yRot(), held.xRot(), false);
		player.setDeltaMovement(Vec3.ZERO);
		player.resetFallDistance();
	}
}
