package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Climbing into the saddle instead of appearing in it (after SmoothMount, LGPL, an idea only: none of its code). Andy
 * ticked every row of the SmoothMount pick list, 2026-11-19.
 * <p>
 * A player who mounts a horse, donkey, mule or saddled pig climbs on first: from the horse's left (left foot in the
 * stirrup, the right leg swung over), its right (the mirror), or from behind (a vault onto the rump and a slide
 * forward), by where they stand. The ride starts only when the climb ends ({@link #begin}): the mount stands still for
 * it, and a hit, sneaking, or stepping away calls it off. Armour makes it slower ({@link #seconds}). Everyone in sight
 * sees the climb ({@link Start}); getting off is the client's alone (client/MountClimbPose), since vanilla has already
 * set the rider down where they land.
 */
public final class MountClimb {
	/** The climbs: the clip each plays (geckolib/animations/entity), and how long it takes unarmoured (seconds). */
	public enum Kind {
		ON_LEFT("player_mount_on_left", 0.7F),
		ON_RIGHT("player_mount_on_right", 0.7F),
		ON_BACK("player_mount_on_back", 0.5F),
		OFF_FORWARD("player_mount_off_forward", 0.55F),
		OFF_BACK("player_mount_off_back", 0.5F);

		public final String clip;
		public final float seconds;

		Kind(String clip, float seconds) {
			this.clip = clip;
			this.seconds = seconds;
		}

		public boolean on() {
			return this == ON_LEFT || this == ON_RIGHT || this == ON_BACK;
		}

		public static Kind of(int i) {
			Kind[] all = values();
			return all[Mth.clamp(i, 0, all.length - 1)];
		}
	}

	/** Full armour (20 points) makes a climb this much longer again: cloth about 1x, full plate about 2x. */
	static final float ARMOUR_SLOW = 1.0F;
	/** A climb is called off if the climber strays this far from where it began, or the mount this far from them. */
	static final double STRAY = 0.75, REACH = 4.0;
	/** Behind the mount: within this many degrees of straight back. */
	static final float BEHIND = 50.0F;

	private record Climb(Entity mount, Kind kind, long end, Vec3 from) {
	}

	private static final Map<UUID, Climb> CLIMBS = new HashMap<>();

	private MountClimb() {
	}

	/** Server to client: {@code player} climbs on {@code mount}, taking {@code ticks}. */
	public record Start(int player, int mount, byte kind, short ticks) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosPowers.id("mount_climb_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.player);
				buf.writeVarInt(p.mount);
				buf.writeByte(p.kind);
				buf.writeShort(p.ticks);
			},
			buf -> new Start(buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readShort()));

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Server to client: {@code player}'s climb was called off. */
	public record Stop(int player) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(JaymosPowers.id("mount_climb_stop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeVarInt(p.player), buf -> new Stop(buf.readVarInt()));

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Start.TYPE, Start.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(MountClimb::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && CLIMBS.containsKey(player.getUUID())) {
				stop(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CLIMBS.remove(handler.player.getUUID()));
	}

	/** Mounts that are climbed onto: horses, donkeys, mules and our breeds, and pigs. Camels kneel and llamas aren't steered. */
	public static boolean climbable(Entity mount) {
		return mount instanceof AbstractHorse && !(mount instanceof Camel) && !(mount instanceof Llama) || mount instanceof Pig;
	}

	/** Whether {@code player} is partway up onto a mount (server side). */
	public static boolean climbing(Player player) {
		return CLIMBS.containsKey(player.getUUID());
	}

	/**
	 * Instead of starting to ride {@code mount} straight away, {@code player} climbs on. True if the ride is put off
	 * (the caller then mustn't start it): a climb began, or one is already under way. False on the client, for anyone
	 * who isn't a player in survival or creative, and for mounts that aren't climbed.
	 */
	public static boolean begin(Player player, Entity mount) {
		if (!(player instanceof ServerPlayer server) || player.isSpectator() || !climbable(mount)) {
			return false;
		}
		if (CLIMBS.containsKey(player.getUUID())) {
			return true;
		}
		Kind kind = side(player, mount);
		int ticks = Math.max(1, Mth.ceil(seconds(kind, player) * 20.0F));
		CLIMBS.put(player.getUUID(), new Climb(mount, kind, player.level().getGameTime() + ticks, player.position()));
		send(server, new Start(player.getId(), mount.getId(), (byte)kind.ordinal(), (short)ticks));
		return true;
	}

	/** How long {@code kind} takes {@code rider}: longer the more armour they wear (seconds). */
	public static float seconds(Kind kind, LivingEntity rider) {
		return kind.seconds * (1.0F + ARMOUR_SLOW * Math.min(rider.getArmorValue(), 20) / 20.0F);
	}

	/** Which way {@code player} climbs on: from behind, or from whichever side of the mount they stand. */
	static Kind side(Player player, Entity mount) {
		float facing = mount instanceof LivingEntity living ? living.yBodyRot : mount.getYRot();
		double dx = player.getX() - mount.getX(), dz = player.getZ() - mount.getZ();
		// the way from the mount to the player, as a yaw (0 faces +z, like the mount's own)
		float toPlayer = (float)(Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		float off = Mth.wrapDegrees(toPlayer - facing);
		if (Math.abs(off) > 180.0F - BEHIND) {
			return Kind.ON_BACK;
		}
		// yaw grows turning right (facing +z, 90 faces -x, the right hand), so the left side is the negative one
		return off < 0.0F ? Kind.ON_LEFT : Kind.ON_RIGHT;
	}

	private static void tick(MinecraftServer server) {
		Iterator<Map.Entry<UUID, Climb>> it = CLIMBS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Climb> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Climb c = e.getValue();
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			Entity mount = c.mount;
			if (!mount.isAlive() || mount.level() != player.level() || player.isPassenger() || player.isShiftKeyDown()
				|| player.position().distanceTo(c.from) > STRAY || player.distanceTo(mount) > REACH) {
				it.remove();
				send(player, new Stop(player.getId()));
				continue;
			}
			// the mount stands still for it
			if (mount instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			Vec3 v = mount.getDeltaMovement();
			mount.setDeltaMovement(0.0, v.y, 0.0);
			if (player.level().getGameTime() >= c.end) {
				it.remove();
				if (mount instanceof AbstractHorse) {
					// as vanilla's own mounting does
					player.setYRot(mount.getYRot());
					player.setXRot(mount.getXRot());
				}
				if (!player.startRiding(mount)) {
					send(player, new Stop(player.getId()));
				}
			}
		}
	}

	private static void stop(ServerPlayer player) {
		if (CLIMBS.remove(player.getUUID()) != null) {
			send(player, new Stop(player.getId()));
		}
	}

	/** To the climber and everyone who can see them. */
	private static void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}
