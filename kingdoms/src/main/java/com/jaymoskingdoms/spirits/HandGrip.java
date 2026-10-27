package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Mana;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.245) The per-tick state of the hands that take hold: what the spirit has seized and is holding in front of its
 * keeper (grab and throw), what it has thrown and is waiting to land, the items it is pulling in (the far hand), and the
 * mobs it holds fast (hold the line). Andy: "manipulating items from way farther away than you could naturally". All of it is
 * kept by server tick count, per keeper; everything lets go the moment the keeper's spirit is no longer out.
 */
final class HandGrip {
	/** How long a seized thing is held at most (ticks), and how far in front of the eyes (blocks). */
	static final int HOLD_TICKS = 60;
	static final double HOLD_DISTANCE = 2.0;
	/** The throw: speed, and extra speed for each tick the key was held. */
	static final double THROW_SPEED = 1.4, THROW_CHARGE = 0.02;
	/** Damage a thrown thing does to what it lands on (and takes itself), before the level's scale. */
	static final float THROW_DAMAGE = 4.0F;
	/** How long a thrown thing is watched for its landing (ticks); the least before it can land; how near a landing hurts (blocks). */
	static final int THROWN_TICKS = 100, THROWN_MIN = 3;
	static final double LAND_RANGE = 1.5;
	/** Nothing taller than this can be seized (blocks). */
	static final double MAX_HEIGHT = 3.0;
	/** An item is pulled this fast (blocks a tick), for at most this long, and is let go this near. */
	static final double PULL_SPEED = 0.6;
	static final int PULL_TICKS = 100;
	static final double PULL_ARRIVES = 1.4;
	/** A pinned mob is held this long (ticks) and slowed to this amplifier (4 is amplifier 3). */
	static final int PIN_TICKS = 60, PIN_SLOW = 3;

	/** Something seized: the keeper holds it ahead of them. */
	private record Held(Entity thing, int since, boolean hadNoGravity) {
	}

	/** Something thrown, waiting to land. */
	private record Thrown(Entity thing, UUID owner, int since, boolean hadNoGravity) {
	}

	/** An item being pulled to its keeper. */
	private record Pull(ItemEntity item, UUID owner, int since) {
	}

	/** A mob held at its spot. */
	private record Pin(Mob mob, UUID owner, Vec3 at, int until) {
	}

	private static final java.util.Map<UUID, Held> HELD = new java.util.HashMap<>();
	private static final List<Thrown> THROWN = new ArrayList<>();
	private static final List<Pull> PULLS = new ArrayList<>();
	private static final List<Pin> PINS = new ArrayList<>();

	/**
	 * On everything the hands hold up (so it saves with no gravity if its keeper leaves or its chunk unloads mid-hold):
	 * {@link #landed} gives it its gravity back when it loads again.
	 */
	static final String HELD_TAG = "jaymoskingdoms_held";

	private HandGrip() {
	}

	/** {@code thing} is held up by the hands: no gravity, and tagged so a save mid-hold is undone on load. */
	static void floatUp(Entity thing) {
		thing.setNoGravity(true);
		thing.addTag(HELD_TAG);
	}

	/** {@code thing} is let go: its gravity back ({@code hadNoGravity}: it never had any). */
	static void settle(Entity thing, boolean hadNoGravity) {
		thing.setNoGravity(hadNoGravity);
		thing.removeTag(HELD_TAG);
	}

	/** An entity loaded: if it was saved mid-hold, its gravity comes back. */
	static void landed(Entity entity) {
		if (entity.entityTags().contains(HELD_TAG)) {
			entity.removeTag(HELD_TAG);
			entity.setNoGravity(false);
		}
	}

	/** Everything {@code player} holds, pulls or pins is let go (the spirit is put away). */
	static void letGo(ServerPlayer player) {
		UUID id = player.getUUID();
		Held held = HELD.remove(id);
		if (held != null) {
			release(held);
		}
		for (Iterator<Pull> it = PULLS.iterator(); it.hasNext();) {
			Pull p = it.next();
			if (p.owner.equals(id)) {
				if (!p.item.isRemoved()) {
					settle(p.item, false);
				}
				it.remove();
			}
		}
		PINS.removeIf(pin -> pin.owner.equals(id));
	}

	/** Before the world saves: everything held, pulled or pinned is let go, so nothing is saved floating. */
	static void letGo() {
		for (Held held : HELD.values()) {
			release(held);
		}
		for (Pull p : PULLS) {
			if (!p.item.isRemoved()) {
				settle(p.item, false);
			}
		}
		clear();
	}

	static void clear() {
		HELD.clear();
		THROWN.clear();
		PULLS.clear();
		PINS.clear();
	}

	// ---- grab and throw ---------------------------------------------------------------------------------------------

	static boolean holding(ServerPlayer player) {
		return HELD.containsKey(player.getUUID());
	}

	/** The arms seize {@code thing} and hold it ahead of {@code player}. */
	static void seize(ServerPlayer player, Entity thing) {
		thing.stopRiding();
		HELD.put(player.getUUID(), new Held(thing, tick(player), thing.isNoGravity()));
		floatUp(thing);
		thing.setDeltaMovement(Vec3.ZERO);
		if (thing instanceof Mob mob) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		}
		player.level().playSound(null, thing.getX(), thing.getY(), thing.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** The arms hurl what they hold along {@code player}'s look; false if they hold nothing. */
	static boolean hurl(ServerPlayer player, int charge) {
		Held held = HELD.remove(player.getUUID());
		if (held == null || held.thing.isRemoved()) {
			return false;
		}
		Entity thing = held.thing;
		Vec3 look = player.getLookAngle();
		settle(thing, held.hadNoGravity);
		thing.setDeltaMovement(look.scale(THROW_SPEED + charge * THROW_CHARGE));
		thing.syncVelocity = true;
		thing.resetFallDistance();
		THROWN.add(new Thrown(thing, player.getUUID(), tick(player), held.hadNoGravity));
		player.level().playSound(null, thing.getX(), thing.getY(), thing.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	private static void release(Held held) {
		if (!held.thing.isRemoved()) {
			settle(held.thing, held.hadNoGravity);
			held.thing.resetFallDistance();
		}
	}

	// ---- the far hand's pull ----------------------------------------------------------------------------------------

	static void pull(ServerPlayer player, ItemEntity item) {
		for (Pull p : PULLS) {
			if (p.item == item) {
				return;
			}
		}
		PULLS.add(new Pull(item, player.getUUID(), tick(player)));
		floatUp(item);
	}

	// ---- hold the line ----------------------------------------------------------------------------------------------

	static boolean pinned(Mob mob) {
		for (Pin p : PINS) {
			if (p.mob == mob) {
				return true;
			}
		}
		return false;
	}

	static void pin(ServerPlayer player, Mob mob) {
		mob.stopRiding();
		PINS.add(new Pin(mob, player.getUUID(), mob.position(), tick(player) + PIN_TICKS));
		mob.setTarget(null);
		mob.getNavigation().stop();
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static int tick(Entity e) {
		MinecraftServer server = e.level().getServer();
		return server == null ? 0 : server.getTickCount();
	}

	private static @Nullable ServerPlayer keeper(MinecraftServer server, UUID id) {
		ServerPlayer p = server.getPlayerList().getPlayer(id);
		return p != null && p.isAlive() && !p.hasDisconnected() && Spirits.out(p) ? p : null;
	}

	static void tick(MinecraftServer server) {
		if (HELD.isEmpty() && THROWN.isEmpty() && PULLS.isEmpty() && PINS.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		tickHeld(server, now);
		tickThrown(server, now);
		tickPulls(server, now);
		tickPins(server, now);
	}

	private static void tickHeld(MinecraftServer server, int now) {
		for (Iterator<java.util.Map.Entry<UUID, Held>> it = HELD.entrySet().iterator(); it.hasNext();) {
			java.util.Map.Entry<UUID, Held> entry = it.next();
			Held held = entry.getValue();
			ServerPlayer owner = keeper(server, entry.getKey());
			boolean over = owner == null || held.thing.isRemoved() || !held.thing.isAlive() || now - held.since >= HOLD_TICKS
				|| held.thing.level() != owner.level();
			if (over) {
				release(held);
				it.remove();
				if (owner != null) {
					Move grab = Moves.of("grab");
					if (grab != null) {
						Moves.start(owner, grab, owner.level().getGameTime());
					}
				}
				continue;
			}
			Entity thing = held.thing;
			Vec3 at = owner.getEyePosition().add(owner.getLookAngle().scale(HOLD_DISTANCE)).subtract(0.0, thing.getBbHeight() * 0.5, 0.0);
			if (thing instanceof ServerPlayer sp) {
				sp.teleportTo(at.x, at.y, at.z);
			} else {
				thing.setPos(at);
			}
			thing.setDeltaMovement(Vec3.ZERO);
			thing.resetFallDistance();
			thing.syncVelocity = true;
			if (thing instanceof Mob mob) {
				mob.setTarget(null);
				mob.getNavigation().stop();
			}
			if (now % 6 == 0 && owner.level() instanceof ServerLevel level) {
				Mana.sendSeen(level, Mana.MOTE, thing.getX(), thing.getY() + thing.getBbHeight() * 0.5, thing.getZ(), 3, 0.3, 0.3, 0.3, 0.01);
			}
		}
	}

	private static void tickThrown(MinecraftServer server, int now) {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext();) {
			Thrown t = it.next();
			Entity thing = t.thing;
			if (thing.isRemoved() || now - t.since > THROWN_TICKS || !(thing.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (now - t.since < THROWN_MIN || !(thing.onGround() || thing.horizontalCollision || thing.isInWater())) {
				continue;
			}
			it.remove();
			ServerPlayer owner = server.getPlayerList().getPlayer(t.owner);
			if (owner == null) {
				continue;
			}
			SpiritState state = Spirits.state(owner);
			float damage = THROW_DAMAGE * (state == null ? 1.0F : Spirits.damageScale(state));
			DamageSource source = level.damageSources().indirectMagic(owner, owner);
			if (thing instanceof LivingEntity living && !(living instanceof Player)) {
				living.hurtServer(level, source, damage);
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, thing.getBoundingBox().inflate(LAND_RANGE),
				e -> e != thing && HandMoves.foe(owner, e))) {
				e.hurtServer(level, source, damage);
			}
			level.sendParticles(ParticleTypes.POOF, thing.getX(), thing.getY() + 0.2, thing.getZ(), 8, 0.3, 0.1, 0.3, 0.02);
			level.playSound(null, thing.getX(), thing.getY(), thing.getZ(), SoundEvents.GENERIC_BIG_FALL, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
	}

	private static void tickPulls(MinecraftServer server, int now) {
		for (Iterator<Pull> it = PULLS.iterator(); it.hasNext();) {
			Pull pull = it.next();
			ServerPlayer owner = keeper(server, pull.owner);
			ItemEntity item = pull.item;
			if (owner == null || item.isRemoved() || now - pull.since > PULL_TICKS || item.level() != owner.level()) {
				if (!item.isRemoved()) {
					settle(item, false);
				}
				it.remove();
				continue;
			}
			Vec3 to = owner.position().add(0.0, 0.5, 0.0).subtract(item.position());
			if (to.length() <= PULL_ARRIVES) {
				settle(item, false);
				item.setNoPickUpDelay();
				item.setDeltaMovement(to.scale(0.1));
				it.remove();
				continue;
			}
			item.setDeltaMovement(to.normalize().scale(PULL_SPEED));
			item.syncVelocity = true;
		}
	}

	private static void tickPins(MinecraftServer server, int now) {
		for (Iterator<Pin> it = PINS.iterator(); it.hasNext();) {
			Pin pin = it.next();
			Mob mob = pin.mob;
			if (mob.isRemoved() || !mob.isAlive() || now >= pin.until || keeper(server, pin.owner) == null) {
				it.remove();
				continue;
			}
			mob.setPos(pin.at);
			mob.setDeltaMovement(Vec3.ZERO);
			mob.setTarget(null);
			mob.getNavigation().stop();
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4, PIN_SLOW, false, false));
			if (now % 8 == 0 && mob.level() instanceof ServerLevel level) {
				Mana.sendSeen(level, Mana.MOTE, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(), 4, 0.3, mob.getBbHeight() * 0.4, 0.3, 0.01);
			}
		}
	}
}
