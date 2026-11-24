package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Cleric's great prayers (Andy, 2026-11-23), said only on the medallion board ({@link MedallionBoard}), each in five
 * marks and the patron's colour, each paid in turnings ({@link Clerics#channels}):
 * <ul>
 * <li><b>Hallowed Ground</b> (known from the start): a wide circle at the feet for {@link #HALLOWED_TICKS}; the dead in it
 * burn, friends in it mend slowly.</li>
 * <li><b>Litany of the Band</b> (favour tier {@link #LITANY_TIER}): Bless the Band and a mend on every friend near, at once.</li>
 * <li><b>Pillar of Judgement</b> (tier {@link #PILLAR_TIER}): a mark under the creature looked at, and after
 * {@link #PILLAR_TELL} a beam from the sky onto it.</li>
 * <li><b>The Patron's Hand</b> (tier {@link #HAND_TIER}): a great hand of light, drawn in sigils and strokes, comes down before
 * the Cleric and slams the ground.</li>
 * </ul>
 * The dead take more from all of them ({@link #UNDEAD_MULT}).
 */
public final class GreatPrayers {
	public static final String HALLOWED = "hallowed_ground", LITANY = "litany_of_the_band", PILLAR = "pillar_of_judgement", HAND = "patrons_hand";
	/** The favour tier each is granted at (Hallowed Ground: from the start). */
	public static final int LITANY_TIER = 2, PILLAR_TIER = 3, HAND_TIER = 4;
	/** Turnings each costs. */
	static final int HALLOWED_COST = 1, LITANY_COST = 2, PILLAR_COST = 1, HAND_COST = 2;
	/** Hallowed Ground: how long (ticks), how wide (blocks), how often it works (ticks), the burn on the dead and the mend on friends each time. */
	static final int HALLOWED_TICKS = 20 * 20, HALLOWED_EVERY = 20;
	static final double HALLOWED_RADIUS = 5.0;
	static final float HALLOWED_BURN = 3.0F, HALLOWED_MEND = 1.0F;
	/** Litany of the Band: the mend each friend gets. */
	static final float LITANY_MEND = 8.0F;
	/** Pillar of Judgement: how far it reaches (blocks), the aim (cosine to the look), the warning (ticks), the blow, the sky's height (blocks). */
	static final double PILLAR_RANGE = 32.0, PILLAR_AIM = 0.97, PILLAR_SKY = 24.0;
	static final int PILLAR_TELL = 20;
	static final float PILLAR_DAMAGE = 14.0F;
	/** The Patron's Hand: how far before the Cleric it falls, how wide it strikes, how high it starts, the blow and the shove (blocks). */
	static final double HAND_REACH = 5.0, HAND_RADIUS = 4.0, HAND_HEIGHT = 8.0, HAND_SHOVE = 1.2;
	static final int HAND_FALL = 10;
	static final float HAND_DAMAGE = 12.0F;
	/** How much more the dead take. */
	static final float UNDEAD_MULT = 1.5F;

	private record Zone(ServerLevel level, Vec3 at, long until, UUID owner, int colour) {
	}

	private record Later(ServerLevel level, long at, Runnable run) {
	}

	private static final List<Zone> ZONES = new ArrayList<>();
	private static final List<Later> LATER = new ArrayList<>();

	private GreatPrayers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GreatPrayers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ZONES.clear();
			LATER.clear();
		});
	}

	/** Whether {@code player}'s patron has granted the great prayer {@code id}. */
	public static boolean granted(Player player, String id) {
		int tier = Patrons.state(player).tier();
		return switch (id) {
			case HALLOWED -> true;
			case LITANY -> tier >= LITANY_TIER;
			case PILLAR -> tier >= PILLAR_TIER;
			case HAND -> tier >= HAND_TIER;
			default -> false;
		};
	}

	/** Says the great prayer {@code id}; false if it could not be said (nothing spent). */
	static boolean say(ServerPlayer player, ServerLevel level, String id, int colour) {
		LivingEntity target = null;
		if (id.equals(PILLAR)) {
			target = aimed(player, level);
			if (target == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.medallion.no_target"));
				return false;
			}
		}
		int cost = switch (id) {
			case HALLOWED -> HALLOWED_COST;
			case LITANY -> LITANY_COST;
			case PILLAR -> PILLAR_COST;
			default -> HAND_COST;
		};
		if (!Rests.spend(player, Rests.CHANNEL, cost, Clerics.channels(player))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cleric.silent"));
			return false;
		}
		switch (id) {
			case HALLOWED -> hallow(player, level, colour);
			case LITANY -> litany(player, level);
			case PILLAR -> pillar(player, level, target, colour);
			default -> hand(player, level, colour);
		}
		return true;
	}

	private static void hallow(ServerPlayer player, ServerLevel level, int colour) {
		Vec3 at = player.position();
		ZONES.add(new Zone(level, at, level.getGameTime() + HALLOWED_TICKS, player.getUUID(), colour));
		Fx.ring(level, at.add(0.0, 0.1, 0.0), (float) HALLOWED_RADIUS, colour);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.9F);
	}

	private static void litany(ServerPlayer player, ServerLevel level) {
		for (LivingEntity who : Clerics.bless(level, player)) {
			who.heal(LITANY_MEND);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	private static void pillar(ServerPlayer player, ServerLevel level, LivingEntity target, int colour) {
		Fx.sigil(level, target.position().add(0.0, 0.05, 0.0), 1.4F, colour, PILLAR_TELL + 4);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 0.6F);
		later(level, PILLAR_TELL, () -> {
			if (!target.isAlive() || target.level() != level) {
				return;
			}
			Vec3 feet = target.position();
			Fx.beam(level, feet.add(0.0, PILLAR_SKY, 0.0), feet, 0.7F, colour, 12);
			Fx.blast(level, feet.add(0.0, 0.5, 0.0), 1.6F, colour);
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), PILLAR_DAMAGE * (Restoration.undead(target) ? UNDEAD_MULT : 1.0F));
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
		});
	}

	private static void hand(ServerPlayer player, ServerLevel level, int colour) {
		Vec3 forward = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		forward = forward.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
		Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
		Vec3 ground = player.position().add(forward.scale(HAND_REACH));
		Vec3 f = forward, r = right;
		// the hand comes down in three steps, then strikes
		for (int step = 0; step < 3; step++) {
			double height = HAND_HEIGHT * (1.0 - step / 3.0);
			later(level, step * HAND_FALL / 3, () -> drawHand(level, ground.add(0.0, height, 0.0), f, r, colour));
		}
		level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		later(level, HAND_FALL, () -> {
			Fx.blast(level, ground.add(0.0, 0.5, 0.0), 3.0F, colour);
			Fx.ring(level, ground.add(0.0, 0.1, 0.0), (float) HAND_RADIUS, colour);
			level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ground, ground).inflate(HAND_RADIUS, 2.5, HAND_RADIUS),
				e -> e.isAlive() && e != player && !friend(player, e))) {
				if (e.position().distanceToSqr(ground) > HAND_RADIUS * HAND_RADIUS * 1.5) {
					continue;
				}
				e.hurtServer(level, level.damageSources().indirectMagic(player, player), HAND_DAMAGE * (Restoration.undead(e) ? UNDEAD_MULT : 1.0F));
				Vec3 away = e.position().subtract(ground).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? f : away.normalize();
				e.push(away.x * HAND_SHOVE, 0.5, away.z * HAND_SHOVE);
				e.needsSync = true;
			}
		});
	}

	/** The hand at {@code palm}, palm down: the palm a sigil, four fingers and a thumb as strokes. */
	private static void drawHand(ServerLevel level, Vec3 palm, Vec3 forward, Vec3 right, int colour) {
		Fx.shape(level, 4, palm, new Vec3(0.0, -1.0, 0.0), 1.6F, colour, 6);
		for (int i = 0; i < 4; i++) {
			double spread = (i - 1.5) * 0.35;
			Vec3 tip = palm.add(forward.scale(2.4 - Math.abs(spread) * 0.6)).add(right.scale(spread * 2.0)).add(0.0, -0.4, 0.0);
			Fx.stroke(level, palm, tip, 0.25F, colour);
		}
		Fx.stroke(level, palm, palm.add(right.scale(-1.8)).add(forward.scale(0.6)).add(0.0, -0.4, 0.0), 0.25F, colour);
	}

	/** The creature {@code player} looks at, within {@link #PILLAR_RANGE} and in sight, or null. */
	private static @Nullable LivingEntity aimed(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		LivingEntity best = null;
		double bestDot = PILLAR_AIM;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PILLAR_RANGE), e -> e.isAlive() && e != player)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (to.lengthSqr() > PILLAR_RANGE * PILLAR_RANGE) {
				continue;
			}
			double dot = look.dot(to.normalize());
			if (dot > bestDot && player.hasLineOfSight(e)) {
				best = e;
				bestDot = dot;
			}
		}
		return best;
	}

	/** Whether {@code e} is {@code cleric}'s friend: a player, or one of the Cleric's own beasts. */
	private static boolean friend(ServerPlayer cleric, LivingEntity e) {
		return e instanceof Player || e instanceof TamableAnimal pet && pet.isOwnedBy(cleric);
	}

	private static void later(ServerLevel level, int ticks, Runnable run) {
		LATER.add(new Later(level, level.getGameTime() + ticks, run));
	}

	private static void tick(MinecraftServer server) {
		if (!LATER.isEmpty()) {
			List<Later> due = new ArrayList<>();
			Iterator<Later> it = LATER.iterator();
			while (it.hasNext()) {
				Later l = it.next();
				if (l.level().getGameTime() >= l.at()) {
					due.add(l);
					it.remove();
				}
			}
			due.forEach(l -> l.run().run());
		}
		if (ZONES.isEmpty() || server.getTickCount() % HALLOWED_EVERY != 0) {
			return;
		}
		Iterator<Zone> it = ZONES.iterator();
		while (it.hasNext()) {
			Zone z = it.next();
			ServerLevel level = z.level();
			if (level.getGameTime() >= z.until()) {
				it.remove();
				continue;
			}
			Fx.sigil(level, z.at().add(0.0, 0.05, 0.0), (float) HALLOWED_RADIUS, z.colour(), HALLOWED_EVERY + 4);
			ServerPlayer owner = server.getPlayerList().getPlayer(z.owner());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(z.at(), z.at()).inflate(HALLOWED_RADIUS, 2.0, HALLOWED_RADIUS),
				LivingEntity::isAlive)) {
				if (e.position().distanceToSqr(z.at()) > HALLOWED_RADIUS * HALLOWED_RADIUS) {
					continue;
				}
				if (Restoration.undead(e)) {
					e.hurtServer(level, owner != null ? level.damageSources().indirectMagic(owner, owner) : level.damageSources().magic(), HALLOWED_BURN);
					e.igniteForSeconds(2.0F);
				} else if (e instanceof Player || owner != null && e instanceof TamableAnimal pet && pet.isOwnedBy(owner)) {
					e.heal(HALLOWED_MEND);
				}
			}
		}
	}
}
