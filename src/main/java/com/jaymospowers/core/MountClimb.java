package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.mob.RideableAnimal;
import com.jaymospowers.aether.mob.WingedAnimal;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.freerun.Freerun;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
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
import org.jspecify.annotations.Nullable;

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
		OFF_BACK("player_mount_off_back", 0.5F),
		/** Onto a horse that kneels for an armoured rider (Kingdoms): a low step across. */
		ON_KNEEL("player_mount_on_kneel", 0.6F),
		/** Up behind a rider already in the saddle, who reaches back a hand. */
		ON_PILLION("player_mount_on_pillion", 0.7F),
		/** Off the way back down when the mount shied, from wherever the climb had got to (the client's, not the server's). */
		OFF_SLIP("player_mount_off_slip", 0.4F),
		/** A sprinting freerunner leapfrogs a mount's rump straight into the saddle, keeping their pace (Freerunner). */
		ON_VAULT("player_mount_on_vault", 0.35F),
		/** Off a galloping mount sideways with its momentum, tucking for the landing roll (the client's, like the other climbs off; see MountLeap). */
		OFF_LEAP("player_mount_off_leap", 0.6F),
		/** Onto a war elephant or mammoth that kneels: a foot on the folded foreleg, hauled up by the harness. */
		ON_BIG_KNEEL("player_mount_on_big_kneel", 0.9F, POSE_KNEEL, 0),
		/** Onto an elephant that lifts the rider in its trunk: carried up in the curl and set on its back. */
		ON_TRUNK("player_mount_on_trunk", 1.3F, POSE_TRUNK, 0),
		/** Onto a moa that crouches: a hop onto the low back. */
		ON_CROUCH("player_mount_on_crouch", 0.6F, POSE_CROUCH, 0),
		/** Up the lowered wing of a phyg or flying cow, from its left or its right (the side it lowers is the mount's own). */
		ON_WING_LEFT("player_mount_on_wing_left", 0.8F, POSE_WING, -1),
		ON_WING_RIGHT("player_mount_on_wing_right", 0.8F, POSE_WING, 1),
		/** Up a dragon's lowered neck from its head. */
		ON_NECK("player_mount_on_neck", 1.1F, POSE_NECK, 0),
		/** Up a dragon's lowered wing, from its left or its right. */
		ON_DRAGON_WING_LEFT("player_mount_on_dragon_wing_left", 1.1F, POSE_WING, -1),
		ON_DRAGON_WING_RIGHT("player_mount_on_dragon_wing_right", 1.1F, POSE_WING, 1);

		public final String clip;
		public final float seconds;
		/** What the mount does for this climb ({@code POSE_*}; 0 for the climbs that need nothing of it), and which of its sides (-1 left, 1 right, 0 neither). */
		public final int pose, side;

		Kind(String clip, float seconds) {
			this(clip, seconds, 0, 0);
		}

		Kind(String clip, float seconds, int pose, int side) {
			this.clip = clip;
			this.seconds = seconds;
			this.pose = pose;
			this.side = side;
		}

		public boolean on() {
			return this == ON_LEFT || this == ON_RIGHT || this == ON_BACK || this == ON_KNEEL || this == ON_PILLION || this == ON_VAULT || this.pose > 0;
		}

		/** Kinds that don't hold the climber still: they keep moving through it, so their keys aren't held back. */
		public boolean carriesMomentum() {
			return this == ON_VAULT || this == OFF_LEAP;
		}

		public static Kind of(int i) {
			Kind[] all = values();
			return all[Mth.clamp(i, 0, all.length - 1)];
		}
	}

	/** What a mount does while it is climbed ({@link Kind#pose}): kneels, curls its trunk down and up, crouches, lowers a wing, lowers its neck. */
	public static final int POSE_KNEEL = 1, POSE_TRUNK = 2, POSE_CROUCH = 3, POSE_WING = 4, POSE_NECK = 5;
	/** What Kingdoms' {@code Boarding.style} answers for a mount of its own: nothing special, the elephant kneels, lifts in its trunk, a dragon lowers its neck or a wing, or it is in the air (no climb). */
	static final int STYLE_NONE = 0, STYLE_KNEEL = 1, STYLE_TRUNK = 2, STYLE_NECK = 3, STYLE_WING = 4, STYLE_AIR = -1;
	/** A mount this much wider than a horse is climbed from further off: the reach grows by half the extra width. */
	static final float HORSE_WIDTH = 1.4F;
	/** Full armour (20 points) makes a climb this much longer again: cloth about 1x, full plate about 2x. */
	static final float ARMOUR_SLOW = 1.0F;
	/** A climb is called off if the climber strays this far from where it began, or the mount this far from them. */
	static final double STRAY = 0.75, REACH = 4.0;
	/** Armour slows the vault and the leap only this much (against {@link #ARMOUR_SLOW}); a vaulter, who runs on through it, is called off only this far from the mount (blocks). */
	static final float VAULT_ARMOUR_SLOW = 0.25F;
	static final double VAULT_REACH = 6.0;
	/** Behind the mount: within this many degrees of straight back. */
	static final float BEHIND = 50.0F;

	/** The mount shies from a climber (when it does) this far through the climb: from and to, as shares of it. */
	static final float SHY_FROM = 0.35F, SHY_TO = 0.65F;
	/** How far a shy takes the mount sideways (blocks a tick at the start: about two thirds of a block in all), and its little hop. */
	static final double SHY_STEP = 0.3, SHY_HOP = 0.1;
	/** A mount that has shied doesn't again for this many ticks, so the next try goes through. */
	static final long SETTLED_TICKS = 200L;
	/** Without Kingdoms' bond to read, an untamed horse shies from a climber this often; a tamed one never. */
	static final float WILD_SKITTISH = 0.35F;
	/** Why a climb ended early ({@link Stop}): something called it off, or the mount shied. */
	public static final byte CALLED_OFF = 0, SHIED = 1;

	/** {@code shyAt} is the game time the mount shies (negative: it doesn't). */
	private record Climb(Entity mount, Kind kind, long end, Vec3 from, long shyAt) {
	}

	/** Mounts that have shied, by id: until when they're settled again. */
	private static final Map<UUID, Long> SETTLED = new HashMap<>();

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

	/** Server to client: {@code player}'s climb was called off, for {@code reason} ({@link #CALLED_OFF}, {@link #SHIED}). */
	public record Stop(int player, byte reason) implements CustomPacketPayload {
		public static final Type<Stop> TYPE = new Type<>(JaymosPowers.id("mount_climb_stop"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.player);
				buf.writeByte(p.reason);
			},
			buf -> new Stop(buf.readVarInt(), buf.readByte()));

		@Override
		public Type<Stop> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Start.TYPE, Start.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(MountClimb::tick);
		MountLeap.register();
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && CLIMBS.containsKey(player.getUUID())) {
				stop(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CLIMBS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLIMBS.clear();
			SETTLED.clear();
		});
	}

	/**
	 * Mounts that are climbed onto: horses, donkeys, mules and our breeds (war elephants and the wind dragons are
	 * {@link AbstractHorse}s too), pigs, and the Aether's rideable animals (moa, phyg, flying cow). Camels kneel and llamas
	 * aren't steered.
	 */
	public static boolean climbable(Entity mount) {
		return mount instanceof AbstractHorse && !(mount instanceof Camel) && !(mount instanceof Llama) || mount instanceof Pig
			|| mount instanceof RideableAnimal;
	}

	/** A mount that flies: the Aether's rideable animals and Kingdoms' dragons (their entity ids end in {@code _dragon}). */
	public static boolean flier(Entity mount) {
		return mount instanceof RideableAnimal || BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getPath().endsWith("_dragon");
	}

	/** A flier that is off the ground: nobody climbs onto it (they mount as before), and nobody climbs off it (they fall). */
	public static boolean flying(Entity mount) {
		return flier(mount) && !mount.onGround() && !mount.isInWater();
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
		Kind kind = kindOf(player, mount);
		if (kind == null) {
			return false;
		}
		int ticks = Math.max(1, Mth.ceil(seconds(kind, player) * 20.0F));
		long now = player.level().getGameTime();
		long shyAt = -1L;
		if ((kind == Kind.ON_LEFT || kind == Kind.ON_RIGHT || kind == Kind.ON_BACK) && SETTLED.getOrDefault(mount.getUUID(), 0L) <= now
			&& player.getRandom().nextFloat() < skittish(player, mount)) {
			shyAt = now + Math.max(1, Math.round(ticks * Mth.lerp(player.getRandom().nextFloat(), SHY_FROM, SHY_TO)));
		}
		CLIMBS.put(player.getUUID(), new Climb(mount, kind, now + ticks, player.position(), shyAt));
		send(server, new Start(player.getId(), mount.getId(), (byte)kind.ordinal(), (short)ticks));
		return true;
	}

	/** How long {@code kind} takes {@code rider}: longer the more armour they wear (seconds). */
	public static float seconds(Kind kind, LivingEntity rider) {
		return kind.seconds * (1.0F + (kind.carriesMomentum() ? VAULT_ARMOUR_SLOW : ARMOUR_SLOW) * Math.min(rider.getArmorValue(), 20) / 20.0F);
	}

	/**
	 * How {@code player} climbs on: up behind a rider already in the saddle (a hand up), onto a horse that kneels for them
	 * (Kingdoms' kneel, which it lets an armoured rider on with), else {@link #side}. A mount of Kingdoms' own that boards
	 * its riders its own way says how ({@code Boarding.style}): the war elephant kneels or lifts the rider in its trunk, a
	 * dragon lowers its neck or a wing. The Aether's moa crouches and its winged animals lower a wing. Null: it flies,
	 * and is ridden without a climb.
	 */
	static @Nullable Kind kindOf(Player player, Entity mount) {
		if (flying(mount)) {
			return null;
		}
		if (mount instanceof RideableAnimal) {
			return mount instanceof WingedAnimal ? (onLeft(player, mount) ? Kind.ON_WING_LEFT : Kind.ON_WING_RIGHT) : Kind.ON_CROUCH;
		}
		if (mount instanceof AbstractHorse) {
			for (Entity rider : mount.getPassengers()) {
				if (rider instanceof Player && rider != player) {
					return Kind.ON_PILLION;
				}
			}
			if (KingdomsCompat.call("equestrian.Boarding", "style", new Class<?>[] {AbstractHorse.class, Player.class}, STYLE_NONE,
				mount, player) instanceof Integer style && style != STYLE_NONE) {
				switch (style) {
					case STYLE_KNEEL -> {
						return Kind.ON_BIG_KNEEL;
					}
					case STYLE_TRUNK -> {
						return Kind.ON_TRUNK;
					}
					case STYLE_NECK -> {
						return Kind.ON_NECK;
					}
					case STYLE_WING -> {
						return onLeft(player, mount) ? Kind.ON_DRAGON_WING_LEFT : Kind.ON_DRAGON_WING_RIGHT;
					}
					case STYLE_AIR -> {
						return null;
					}
					default -> {
					}
				}
			}
			if (KingdomsCompat.call("equestrian.Bonding", "kneelingFor", new Class<?>[] {AbstractHorse.class, Player.class}, Boolean.FALSE,
				mount, player) == Boolean.TRUE) {
				return Kind.ON_KNEEL;
			}
		}
		Kind side = side(player, mount);
		return side == Kind.ON_BACK && vaulting(player) ? Kind.ON_VAULT : side;
	}

	/** Whether {@code player} sprints at the mount's rump on the ground with Freerunner learnt (Kingdoms): they vault it rather than climb. */
	static boolean vaulting(Player player) {
		return player.isSprinting() && player.onGround() && Freerun.rank(player, Freerun.Perk.FREERUNNER) >= 1;
	}

	/**
	 * How likely {@code mount} is to shy from {@code player} climbing on, 0 to 1. Pigs never. Our horses by Kingdoms'
	 * bond (trust and temper); without it, an untamed horse {@link #WILD_SKITTISH} and a tamed one never.
	 */
	static float skittish(Player player, Entity mount) {
		if (!(mount instanceof AbstractHorse horse)) {
			return 0.0F;
		}
		Object chance = KingdomsCompat.call("equestrian.Bonding", "skittish", new Class<?>[] {AbstractHorse.class, Player.class}, null, horse, player);
		if (chance instanceof Float f && f >= 0.0F) {
			return f;
		}
		return horse.isTamed() ? 0.0F : WILD_SKITTISH;
	}

	/** Whether {@code player} stands on the mount's left (or, straight behind it, nearer its left than its right). */
	static boolean onLeft(Player player, Entity mount) {
		float facing = mount instanceof LivingEntity living ? living.yBodyRot : mount.getYRot();
		float toPlayer = (float)(Mth.atan2(-(player.getX() - mount.getX()), player.getZ() - mount.getZ()) * Mth.RAD_TO_DEG);
		return Mth.wrapDegrees(toPlayer - facing) < 0.0F;
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
			boolean vault = c.kind == Kind.ON_VAULT;
			if (!mount.isAlive() || mount.level() != player.level() || player.isPassenger() || player.isShiftKeyDown()
				|| !vault && player.position().distanceTo(c.from) > STRAY
				|| player.distanceTo(mount) > (vault ? VAULT_REACH : REACH) + Math.max(0.0F, (mount.getBbWidth() - HORSE_WIDTH) * 0.5F)) {
				it.remove();
				send(player, new Stop(player.getId(), CALLED_OFF));
				continue;
			}
			long now = player.level().getGameTime();
			if (c.shyAt >= 0L && now >= c.shyAt) {
				it.remove();
				shy(mount, player, now);
				send(player, new Stop(player.getId(), SHIED));
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
					send(player, new Stop(player.getId(), CALLED_OFF));
				}
			}
		}
	}

	/** {@code mount} takes a step sideways, away from {@code player}'s side of it, with a snort. */
	private static void shy(Entity mount, ServerPlayer player, long now) {
		SETTLED.values().removeIf(until -> until < now);
		SETTLED.put(mount.getUUID(), now + SETTLED_TICKS);
		float facing = (mount instanceof LivingEntity living ? living.yBodyRot : mount.getYRot()) * Mth.DEG_TO_RAD;
		// the mount's right hand (yaw grows turning right: facing +z, its right is -x)
		double rx = -Mth.cos(facing), rz = -Mth.sin(facing);
		double side = (player.getX() - mount.getX()) * rx + (player.getZ() - mount.getZ()) * rz;
		double away = side > 0.0 ? -1.0 : 1.0;
		Vec3 v = mount.getDeltaMovement();
		mount.setDeltaMovement(rx * away * SHY_STEP, mount.onGround() ? SHY_HOP : v.y, rz * away * SHY_STEP);
		mount.playSound(SoundEvents.HORSE_BREATHE, 1.0F, 1.1F + mount.getRandom().nextFloat() * 0.3F);
	}

	private static void stop(ServerPlayer player) {
		if (CLIMBS.remove(player.getUUID()) != null) {
			send(player, new Stop(player.getId(), CALLED_OFF));
		}
	}

	/** To the climber and everyone who can see them. */
	static void send(ServerPlayer player, CustomPacketPayload payload) {
		ServerPlayNetworking.send(player, payload);
		for (ServerPlayer other : PlayerLookup.tracking(player)) {
			if (other != player) {
				ServerPlayNetworking.send(other, payload);
			}
		}
	}
}
