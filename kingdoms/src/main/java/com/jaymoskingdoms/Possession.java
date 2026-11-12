package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turned for a while (docs/Pawn-Plan.md row H1; after the possessed pawns of Dragon's Dogma, a commercial game: ideas
 * only). Something takes hold of a fighter: its eyes go red ({@link #EYES}) and it fights for whatever took it, at you.
 * Knock it down, or pick it up and carry it, and the hold breaks; or it wears off. A close bond holds out against it
 * ({@link Bonds#resists}).
 * <ul>
 * <li><b>Sorcerers</b>: a camp's necromancer or blood witch, or an evoker (the Grue's shades are evokers too), in a
 * fight reaches for a companion near it, now and then
 * ({@link #SORCERER_EVERY}, {@link #SORCERER_CHANCE}), and turns it to the camp's side.</li>
 * <li><b>A thrall-bite</b>: a vampire's own blow on someone else's companion may turn it to the vampire's side
 * ({@link #BITE_CHANCE}).</li>
 * <li><b>The Grue</b>: once it has a hard hold on a spirit's keeper ({@link com.jaymoskingdoms.spirits.Grue#ASSAULT}),
 * it reaches into one of their companions now and then and sets it on them.</li>
 * <li><b>Your own dark arts</b> (the foe side): the death spell Dominion turns a foe you look at to your side for a
 * while, its own band its first foe, at the forbidden arts' usual price (corruption, the law, the Grue drawing nearer:
 * {@link ForbiddenArts}).</li>
 * </ul>
 * The same hold is what the Murmur does at its worst ({@link Murmur}) and what a masterless companion does to its new
 * master ({@link Masterless}): those last until the turned one is knocked down.
 */
public final class Possession {
	/** How long each hold lasts (ticks); the spell's, times its power. */
	static final int SORCERER_TICKS = 1200, BITE_TICKS = 1200, GRUE_TICKS = 900, SPELL_TICKS = 600;
	/** A sorcerer reaches for a companion at most this often (ticks), at this chance, this far (blocks). */
	static final int SORCERER_EVERY = 400;
	static final float SORCERER_CHANCE = 0.35F, BITE_CHANCE = 0.2F, GRUE_CHANCE = 0.3F;
	static final double REACH = 16.0, PREY_NEAR = 32.0;
	/** The Grue reaches into a companion at most this often (ticks). */
	static final int GRUE_EVERY = 2400;
	/** The turned are steered this often (ticks). */
	static final int EVERY = 20;
	/** The most a turned one can have and still be held by the spell (anything bigger shrugs it off). */
	static final float SPELL_MOST_HEALTH = 60.0F;
	static final int RED = 0xB0141A;

	/** Eyes drawn on a body: {@link #EYES_RED} red and steady (held), {@link #EYES_FLICKER} a red flicker (the Murmur). Synced, not saved. */
	public static final int EYES_RED = 1, EYES_FLICKER = 2;
	public static final AttachmentType<Integer> EYES = AttachmentRegistry.create(JaymosKingdoms.id("dark_eyes"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	/** A hold: the side it fights for, the side it was on, until when (game time; {@code Long.MAX_VALUE} until knocked down), what did it. */
	public record Held(String side, String was, long until, String cause) {
		static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("side").forGetter(Held::side),
			Codec.STRING.optionalFieldOf("was", "").forGetter(Held::was),
			Codec.LONG.fieldOf("until").forGetter(Held::until),
			Codec.STRING.optionalFieldOf("cause", "").forGetter(Held::cause)).apply(i, Held::new));
	}

	/** On someone held. Saved. */
	public static final AttachmentType<Held> HELD = AttachmentRegistry.create(JaymosKingdoms.id("possessed"),
		builder -> builder.persistent(Held.CODEC));

	private static final Set<Mob> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());
	/** A caster's last reach, a Grue host's last reach. */
	private static final Map<UUID, Long> REACHED = new HashMap<>();

	private Possession() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Possession::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob m && m.hasAttached(HELD)) {
				LOADED.add(m);
				m.setAttached(EYES, EYES_RED);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob m) {
				LOADED.remove(m);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> bitten(victim, source, taken));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			REACHED.clear();
		});
	}

	/** (Comrades.side) the side a held one fights for, or null. */
	static @Nullable String side(Entity e) {
		Held h = e.getAttached(HELD);
		return h == null ? null : h.side();
	}

	public static boolean held(Entity e) {
		return e.hasAttached(HELD);
	}

	/** The player a held one was a companion of (its old side), or null. */
	static @Nullable UUID lordOf(Held h) {
		if (!h.was().startsWith("p:")) {
			return null;
		}
		try {
			return UUID.fromString(h.was().substring(2));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	static @Nullable String cause(Entity e) {
		Held h = e.getAttached(HELD);
		return h == null ? null : h.cause();
	}

	/**
	 * {@code m} is taken hold of: it fights for {@code side} for {@code ticks} (negative: until it's knocked down).
	 * {@code by} is what did it (for the words). False if it held out, or can't be held.
	 */
	static boolean seize(Mob m, String side, long ticks, String cause, @Nullable LivingEntity by) {
		if (!(m.level() instanceof ServerLevel level) || !m.isAlive() || Downed.isDowned(m) || held(m) || !Temperaments.humanoid(m)) {
			return false;
		}
		if (Bonds.lordOf(m) != null && Bonds.resists(m, m.getRandom())) {
			Callouts.say(m, "turn.resist", by, true);
			return false;
		}
		long now = level.getGameTime();
		String was = Comrades.side(m);
		m.setAttached(HELD, new Held(side, was, ticks < 0 ? Long.MAX_VALUE : now + ticks, cause));
		m.setAttached(EYES, EYES_RED);
		LOADED.add(m);
		if (m instanceof Villager v) {
			McaCompat.stopFollowing(v);
			v.removeAttached(Followers.ORDER);
		}
		Comrades.stand(m);
		level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.EVOKER_PREPARE_WOLOLO, SoundSource.HOSTILE, 0.8F, 0.6F);
		Fx.sigil(level, m.position().add(0.0, 0.05, 0.0), 0.9F, RED, 30);
		Callouts.say(m, "turn.seized", null, true);
		return true;
	}

	/** The hold on {@code m} breaks ({@code knocked}: it was knocked down or carried). */
	static void release(Mob m, boolean knocked) {
		Held h = m.getAttached(HELD);
		m.removeAttached(HELD);
		m.removeAttached(EYES);
		LOADED.remove(m);
		Comrades.stand(m);
		if (h == null || !(m.level() instanceof ServerLevel level)) {
			return;
		}
		// (the Murmur itself is cured once a downed one comes round: Murmur.step)
		if (h.cause().equals("lost")) {
			Masterless.freed(m, knocked);
			return;
		}
		UUID lord = lordOf(h);
		ServerPlayer p = lord == null ? null : level.getServer().getPlayerList().getPlayer(lord);
		if (p != null && p.level() == level && p.distanceTo(m) < PREY_NEAR && m instanceof Villager v && McaCompat.LOADED) {
			Followers.take(v, p, Followers.FOLLOW);
		}
		Callouts.say(m, "turn.free", p, true);
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Mob m : new ArrayList<>(LOADED)) {
			Held h = m.getAttached(HELD);
			if (h == null || !m.isAlive() || m.isRemoved()) {
				LOADED.remove(m);
				continue;
			}
			if (Downed.isDowned(m) || PowersCompat.carried(m)) {
				release(m, true);
				continue;
			}
			if (now > h.until()) {
				release(m, false);
				continue;
			}
			LivingEntity prey = prey(m, h);
			if (prey != null && Comrades.target(m) == null) {
				Comrades.aim(m, prey);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) {
				continue;
			}
			sorcerers(player, now);
			grue(player, now);
		}
	}

	/** Whom a held one goes for: what it was turned against. */
	private static @Nullable LivingEntity prey(Mob m, Held h) {
		if (!(m.level() instanceof ServerLevel level)) {
			return null;
		}
		if (h.cause().equals("murmur")) {
			return Murmur.prey(level, m);
		}
		// turned against its own: its lord first, then whoever was on its side
		UUID lord = lordOf(h);
		if (lord != null && level.getPlayerByUUID(lord) instanceof ServerPlayer p && p.isAlive() && !p.isSpectator() && !Downed.isDowned(p)
			&& p.distanceTo(m) < PREY_NEAR) {
			return p;
		}
		LivingEntity best = null;
		double bestD = PREY_NEAR * PREY_NEAR;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(PREY_NEAR), e -> e != m && e.isAlive()
			&& !Downed.isDowned(e) && !e.isSpectator() && Comrades.side(e).equals(h.was()))) {
			double d = e.distanceToSqr(m);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** A camp's sorcerer in a fight near {@code player} reaches for one of their companions. */
	private static void sorcerers(ServerPlayer player, long now) {
		ServerLevel level = player.level();
		for (Mob caster : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(PREY_NEAR), e -> e.isAlive() && !Downed.isDowned(e)
			&& sorcerer(e) && Comrades.target(e) != null)) {
			if (now - REACHED.getOrDefault(caster.getUUID(), -SORCERER_EVERY * 2L) < SORCERER_EVERY) {
				continue;
			}
			REACHED.put(caster.getUUID(), now);
			if (caster.getRandom().nextFloat() >= SORCERER_CHANCE) {
				continue;
			}
			List<Mob> near = Bonds.companions(player, PREY_NEAR).stream().filter(c -> c.distanceTo(caster) < REACH && caster.hasLineOfSight(c)
				&& !held(c)).toList();
			if (near.isEmpty()) {
				continue;
			}
			Mob c = near.get(caster.getRandom().nextInt(near.size()));
			Fx.beam(level, caster.getEyePosition(), c.getEyePosition(), 0.12F, RED, 12);
			Callouts.say(caster, "turn.cast", c, true, c.getDisplayName());
			seize(c, Comrades.side(caster), SORCERER_TICKS, "sorcerer", caster);
		}
	}

	/** A dark caster: a camp's necromancer or blood witch, or an evoker (the illagers' sorcerers, and the Grue's shades). */
	static boolean sorcerer(Mob e) {
		return e instanceof Worker w && (w.role() == Sorcery.NECROMANCER || w.role() == Sorcery.BLOOD_WITCH)
			|| e instanceof net.minecraft.world.entity.monster.illager.Evoker;
	}

	/** The Grue, hard on its host, sets one of their companions on them. */
	private static void grue(ServerPlayer player, long now) {
		if (com.jaymoskingdoms.spirits.Grue.hold(player) < com.jaymoskingdoms.spirits.Grue.ASSAULT
			|| now - REACHED.getOrDefault(player.getUUID(), -GRUE_EVERY * 2L) < GRUE_EVERY) {
			return;
		}
		REACHED.put(player.getUUID(), now);
		if (player.getRandom().nextFloat() >= GRUE_CHANCE) {
			return;
		}
		List<Mob> near = Bonds.companions(player, REACH).stream().filter(c -> !held(c)).toList();
		if (!near.isEmpty()) {
			Mob c = near.get(player.getRandom().nextInt(near.size()));
			seize(c, "grue:" + player.getUUID(), GRUE_TICKS, "grue", player);
		}
	}

	/** A vampire's own blow on someone else's companion: a thrall-bite. */
	private static void bitten(LivingEntity victim, DamageSource source, float taken) {
		if (taken <= 0.0F || !(victim instanceof Mob m) || !(source.getEntity() instanceof LivingEntity biter) || source.getDirectEntity() != biter
			|| held(m)) {
			return;
		}
		UUID lord = Bonds.lordOf(m);
		boolean vampire = Vampires.isVampire(biter) || biter instanceof Player p && VampirePlayers.vampire(p);
		if (!vampire || lord == null || lord.equals(biter.getUUID()) || m.getRandom().nextFloat() >= BITE_CHANCE) {
			return;
		}
		seize(m, Comrades.side(biter), BITE_TICKS, "bite", biter);
	}

	// ---- the spell -----------------------------------------------------------------------------------------------------

	/** Dominion (a death spell, {@link Arcana#cast}): the foe you look at fights for you a while, at its own band first. */
	static boolean dominion(ServerLevel level, Player player, float power) {
		LivingEntity target = Arcana.sighted(player);
		if (!(target instanceof Mob m) || held(m) || Downed.isDowned(m) || !Temperaments.humanoid(m) || m.getMaxHealth() > SPELL_MOST_HEALTH
			|| player.getUUID().equals(Bonds.lordOf(m))) {
			return false;
		}
		Vec3 to = m.getEyePosition();
		Sorcery.beam(level, player, to, Necromancy.VIOLET);
		if (!seize(m, Comrades.side(player), (long) (SPELL_TICKS * power), "dominion", player)) {
			return true;
		}
		Comrades.stand(m);
		return true;
	}
}
