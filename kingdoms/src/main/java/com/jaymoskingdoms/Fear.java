package com.jaymoskingdoms;

import com.jaymoskingdoms.dragons.WindDragon;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Frightened you can see (fifth edition's frightened, Andy's pick B1). Players and everyone else alike: while something
 * scares you your feet won't carry you toward it (forward slows to a crawl), and while it is in view your aim shakes and
 * your blows are bad footing ({@link UpperHand}). It ends when the scare is out of sight a while, when a Grit save
 * ({@link Saves.Kind#FEAR}) comes good (rolled every {@link #SAVE_EVERY} ticks), or when it times out.
 * <p>
 * What scares: the great and dreadful ({@link #FRIGHTFUL}: wild dragons, the Wither, the Warden, wraiths, and whatever
 * liches the pack adds), a hag's curse (a witch's potion landing), a brutal kill seen up close ({@link Dread}'s witnesses,
 * and a monster finishing a downed body in front of you). Those who see the dreadful thing for the first time get a Grit
 * save at once; one who shrugs it off isn't tried again for {@link #SHRUGGED} ticks.
 * <p>
 * The client's half (the slowed feet and the shaking aim) is in {@code FifthClient}, from {@link Afflictions}.
 */
public final class Fear {
	/** The longest a fright lasts (ticks), and the fright a dreadful sight or a hag's curse gives. */
	static final int MOST = 400, SIGHT_TICKS = 200, CURSE_TICKS = 160;
	/** A Grit save comes round this often (ticks). */
	static final int SAVE_EVERY = 60;
	/** The scare out of sight this long (ticks) ends it. */
	static final int OUT_OF_SIGHT = 60;
	/** One who saved isn't frightened again for this long (ticks). */
	static final int SHRUGGED = 400;
	/** The dreadful frighten those within this far (blocks), looked for every this many ticks. */
	static final double AURA = 20.0;
	static final int LOOK_EVERY = 20;
	/** A hag's curse takes this often on a hit. */
	static final float CURSE_CHANCE = 0.35F;
	/** A brutal kill by a monster frightens a witness within this far (blocks) this often. */
	static final double WITNESS = 14.0;
	static final float WITNESS_CHANCE = 0.5F;
	/** Forward motion toward the scare keeps this share (the rest is taken away). */
	static final double CRAWL = 0.15;

	/** The dreadful: wild dragons come in by the class check, the rest by this tag. */
	public static final TagKey<EntityType<?>> FRIGHTFUL = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("frightful"));

	/** A body's fright: what scares it (entity id), until when, when its next save is, when it last saw it. */
	public record Scare(int source, long until) {
	}

	private static final class State {
		int source;
		long until, nextSave, lastSeen;
	}

	private static final Map<LivingEntity, State> SCARES = new WeakHashMap<>();
	/** Until when each has shrugged off a fright. */
	private static final Map<LivingEntity, Long> SHRUG = new WeakHashMap<>();

	private Fear() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Fear::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SCARES.clear();
			SHRUG.clear();
		});
		UpperHand.add((attacker, target) -> {
			State s = SCARES.get(attacker);
			return s != null && attacker.level().getGameTime() < s.until && sees(attacker, s) ? -1 : 0;
		});
		// a hag's curse: a witch's potion landing
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof Witch witch && victim != witch && victim.isAlive() && !(victim instanceof Witch)
				&& victim.getRandom().nextFloat() < CURSE_CHANCE) {
				expose(victim, witch, CURSE_TICKS);
			}
		});
		// a monster finishing a downed body in front of others
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getEntity() instanceof Mob killer && killer instanceof Enemy && killer.isAlive() && Downed.isDowned(victim)
				&& victim.level() instanceof ServerLevel level) {
				for (LivingEntity w : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(WITNESS),
					e -> e != victim && e != killer && e.isAlive() && fearsAtAll(e) && e.hasLineOfSight(killer))) {
					if (w.getRandom().nextFloat() < WITNESS_CHANCE) {
						expose(w, killer, SIGHT_TICKS);
					}
				}
			}
		});
	}

	/** The fright on {@code who}, or null. */
	public static @Nullable Scare of(LivingEntity who) {
		State s = SCARES.get(who);
		return s == null || who.level().getGameTime() >= s.until ? null : new Scare(s.source, s.until);
	}

	/** Whether {@code who} is frightened just now. */
	public static boolean frightened(LivingEntity who) {
		return of(who) != null;
	}

	/** One who can be frightened at all: a player in play, or a person or beast that isn't a monster, boss or the dead. */
	public static boolean fearsAtAll(LivingEntity who) {
		if (who instanceof Player p) {
			return !p.isSpectator() && !p.isCreative();
		}
		return who instanceof Villager || who instanceof WanderingTrader || who instanceof Folk || Temperaments.humanoid(who) && !(who instanceof Enemy);
	}

	/** Whether {@code who} can be frightened at all: not a player out of play (a boss shrugs off the first frights, {@link Legendary}). */
	static boolean can(LivingEntity who) {
		return (!(who instanceof Player p) || !p.isSpectator() && !p.isCreative()) && !HeroesFeast.fed(who) && !Paladins.fearless(who);
	}

	/** Whether {@code e} is something dreadful that frightens by being seen. */
	static boolean frightful(Entity e) {
		return e instanceof Mob m && m.isAlive() && !m.isSleeping() && (m.is(FRIGHTFUL) || m instanceof WindDragon d && !d.isTamed());
	}

	/**
	 * {@code source} frightens {@code who} for {@code ticks}, after a Grit save first (a save that holds also keeps them
	 * from being tried again for a while). Returns whether they are frightened now.
	 */
	public static boolean expose(LivingEntity who, Entity source, int ticks) {
		if (!can(who) || who == source || !(who.level() instanceof ServerLevel level)) {
			return false;
		}
		long now = level.getGameTime();
		State s = SCARES.get(who);
		if (s != null && now < s.until && s.source == source.getId()) {
			// already afraid of it: the fright holds
			s.until = Math.min(now + MOST, Math.max(s.until, now + ticks));
			return true;
		}
		Long shrugged = SHRUG.get(who);
		if (shrugged != null && now < shrugged) {
			return false;
		}
		if (Saves.resist(who, Saves.Kind.FEAR) || who instanceof ServerPlayer player && Sparks.rescue(player)) {
			SHRUG.put(who, now + SHRUGGED);
			return false;
		}
		frighten(who, source, ticks);
		return frightened(who);
	}

	/** {@code source} frightens {@code who} for {@code ticks}, with no save to turn it first. */
	public static void frighten(LivingEntity who, Entity source, int ticks) {
		if (!can(who) || who == source || !(who.level() instanceof ServerLevel level) || Legendary.shrugs(who)) {
			return;
		}
		long now = level.getGameTime();
		State s = new State();
		s.source = source.getId();
		s.until = now + Math.min(ticks, MOST);
		s.nextSave = now + SAVE_EVERY;
		s.lastSeen = now;
		SCARES.put(who, s);
		level.playSound(null, who.getX(), who.getEyeY(), who.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.5F, 1.8F);
		Fx.burst(level, who.getX(), who.getY(1.0), who.getZ(), 0x4A3A70, 0.5F, 5, 0.25, 0.3, 0.25);
	}

	/** Ends {@code who}'s fright (a cure, a rally). */
	public static void calm(LivingEntity who) {
		SCARES.remove(who);
	}

	private static boolean sees(LivingEntity who, State s) {
		Entity source = who.level().getEntity(s.source);
		return source != null && source.isAlive() && who.hasLineOfSight(source);
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now % LOOK_EVERY == 0) {
			dread(server);
		}
		if (SCARES.isEmpty()) {
			return;
		}
		for (Map.Entry<LivingEntity, State> e : new ArrayList<>(SCARES.entrySet())) {
			LivingEntity who = e.getKey();
			State s = e.getValue();
			if (who == null || !who.isAlive() || who.isRemoved() || !(who.level() instanceof ServerLevel level)) {
				SCARES.remove(who);
				continue;
			}
			long time = level.getGameTime();
			Entity source = level.getEntity(s.source);
			if (time >= s.until || source == null || !source.isAlive()) {
				SCARES.remove(who);
				continue;
			}
			if (who.hasLineOfSight(source)) {
				s.lastSeen = time;
			} else if (time - s.lastSeen >= OUT_OF_SIGHT) {
				SCARES.remove(who);
				continue;
			}
			if (time >= s.nextSave) {
				s.nextSave = time + SAVE_EVERY;
				if (Saves.resist(who, Saves.Kind.FEAR)) {
					SCARES.remove(who);
					SHRUG.put(who, time + SHRUGGED);
					continue;
				}
			}
			if (who instanceof Mob) {
				crawl(who, source);
			}
		}
	}

	/** Takes most of the way a body is moving toward {@code source}: its feet won't carry it closer. */
	private static void crawl(LivingEntity who, Entity source) {
		Vec3 to = source.position().subtract(who.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 0.01) {
			return;
		}
		Vec3 dir = to.normalize();
		Vec3 v = who.getDeltaMovement();
		double along = v.x * dir.x + v.z * dir.z;
		if (along > 0.0) {
			double cut = along * (1.0 - CRAWL);
			who.setDeltaMovement(v.x - dir.x * cut, v.y, v.z - dir.z * cut);
		}
	}

	/** The dreadful frighten those who see them. */
	private static void dread(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
				continue;
			}
			List<Mob> dreadful = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AURA * 1.5), Fear::frightful);
			for (Mob f : dreadful) {
				for (LivingEntity w : level.getEntitiesOfClass(LivingEntity.class, f.getBoundingBox().inflate(AURA),
					e -> e != f && e.isAlive() && fearsAtAll(e) && e.hasLineOfSight(f))) {
					expose(w, f, SIGHT_TICKS);
				}
			}
		}
	}
}
