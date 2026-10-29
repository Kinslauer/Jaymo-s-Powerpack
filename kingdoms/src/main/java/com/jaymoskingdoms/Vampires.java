package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.0) Vampires, stage 1 of the 0.10.0 drop ({@code docs/Vampires-Plan.md}): the Elder, and vampirism spreading
 * through the world's people. Our own; nothing of any vampire mod's is used.
 *
 * <p><b>The Elder.</b> One per world. From day {@link #ELDER_FIRST_DAY}, by day, an ordinary grown villager in a
 * settlement near a player quietly becomes him ({@link #arrive}). By day he's a villager like any other (his own name,
 * his job, his family). At nightfall he hunts the nearest person he can reach, whoever they are (villagers, guards, lords,
 * kings; never children), holds them and drinks until they're dead, rests a little, and goes after the next. Slain, he's
 * gone; another may come once {@link #ELDER_RETURN_DAYS} days have passed.
 *
 * <p><b>The bite.</b> Anyone bitten who lives (they broke free, the guards drove him off, the vampire let go) turns at
 * the next nightfall ({@link #BITTEN}). Killed outright, they stay dead. What they become is rolled as they turn:
 * {@link Kind#FEEDER feeders} hunt every night until they've fed, to the kill; {@link Kind#ABSTAINER abstainers} fight the
 * hunger and sleep the night through, until {@link #THIRST_NIGHTS} nights unfed break them and they bite someone once and
 * let go; {@link Kind#HUNTER hunters} hunt their own kind.
 *
 * <p><b>Being seen.</b> A vampire hunting or fighting at night shows itself ({@link #exposed}: red eyes, the blood as it
 * feeds) till dawn: guards go for it ({@link Folk}'s targets, {@link MobCombat#foe}), villagers run ({@link Frights}).
 * Whoever it attacks fights back. Fire burns a vampire twice over; by day they're weak, and pass for the living.
 *
 * <p>Kept on the person ({@link #VAMPIRE}, {@link #BITTEN}) and on the Overworld ({@link #ELDER}). Players aren't
 * turned yet (stage 3): a vampire's bite only weakens one. Operators: {@code /kingdom vampire elder|turn <kind>|cure|where}.
 */
public final class Vampires {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The Elder comes no sooner than this day of the world; slain, the next no sooner than this many days later. */
	static final long ELDER_FIRST_DAY = 6, ELDER_RETURN_DAYS = 30;
	/** How often (ticks) the world looks for its Elder, and how far round a player he may be found (not nearer than the second). */
	static final int ARRIVE_EVERY = 600;
	static final double ARRIVE_RANGE = 64.0, ARRIVE_UNSEEN = 20.0;
	/** Extra health: the Elder's, any other vampire's. */
	static final double ELDER_HEALTH = 20.0, VAMPIRE_HEALTH = 8.0;
	/** At night: how much faster they move (times), and how far they look for prey (blocks). */
	static final double NIGHT_SPEED = 0.35, PREY_RANGE = 24.0;
	/** A bite: how near (blocks), how often (ticks), how much it takes (the Elder, the others), how much of it heals the biter. */
	static final double BITE_REACH = 2.2;
	static final int BITE_EVERY = 20;
	static final float ELDER_BITE = 5.0F, BITE = 3.0F, HEAL_SHARE = 0.75F;
	/** The bitten are held (Slowness level, ticks), the Elder's grip tighter. */
	static final int HOLD = 3, ELDER_HOLD = 5, HOLD_TICKS = 40;
	/** A player bitten (they don't turn till stage 3): Weakness and Hunger this long. */
	static final int PLAYER_BITE_TICKS = 200;
	/** Claws, for whoever fights one: the Elder's, the others'. */
	static final float ELDER_CLAW = 6.0F, CLAW = 4.0F;
	/** The Elder rests this long after a kill before he hunts again (ticks). */
	static final int ELDER_REST = 2400;
	/** An abstainer holds out this many nights unfed. */
	static final int THIRST_NIGHTS = 3;
	/** Chances of each kind as someone turns: feeder, abstainer (the rest are hunters). */
	static final float FEEDER_CHANCE = 0.5F, ABSTAINER_CHANCE = 0.3F;
	/** A chase given up after this long without a bite (ticks), or when the prey's this far off. */
	static final int GIVE_UP_TICKS = 20 * 40;
	static final double LOSE_RANGE = 40.0;
	/** Someone who hurt it is fought for this long after (ticks), within this range; under this share of health it runs instead. */
	static final int GRUDGE_TICKS = 200;
	static final double GRUDGE_RANGE = 16.0;
	static final float FLEE_AT = 0.3F;
	/** At night: health back every second while not fighting. */
	static final float NIGHT_REGEN = 0.5F;
	/** Fire's damage to a vampire (times). */
	static final float FIRE_FACTOR = 2.0F;
	/** Walk speeds (a villager's brain, a folk's navigation). */
	static final float VILLAGER_HUNT_SPEED = 0.9F;
	static final double FOLK_HUNT_SPEED = 1.2;
	/** A night, by the time of day: from this till {@link #NIGHT_TO}. */
	static final long NIGHT_FROM = 12800L, NIGHT_TO = 23200L;
	private static final long DAY = 24000L;
	private static final int BLOOD = 0x8A0303, EYES = 0xFF1A1A;
	/** The Elder's bat form: prey further than this he flies to (and he looks this far for it), how fast he flies (blocks a tick), how high over the higher end, how long at most (ticks), how far he flees, how long before he can change again (ticks). */
	static final double BAT_MIN = 12.0, ELDER_PREY_RANGE = 48.0, BAT_SPEED = 0.7, BAT_CRUISE = 5.0, BAT_FLEE = 32.0;
	static final int BAT_MAX_TICKS = 20 * 20, BAT_COOLDOWN = 20 * 30;
	/** A blow to the bat: the Elder takes this many times it, and he's knocked out of the form. */
	static final float BAT_HURT_FACTOR = 2.0F;
	static final String BAT_TAG = "jaymoskingdoms_elder_bat";

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** What a vampire is. */
	public enum Kind {
		ELDER, FEEDER, ABSTAINER, HUNTER,
		/** (stage 3) a player ({@link VampirePlayers}). */
		PLAYER,
		/** (0.7.66) a player half turned ({@link VampirePlayers#ateGold}): none of the weaknesses. */
		DHAMPIR;

		String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Kind parse(String id) {
			for (Kind k : values()) {
				if (k.id().equals(id)) {
					return k;
				}
			}
			return null;
		}
	}

	/** A vampire: its kind, the last night it fed (see {@link #nightIndex}), and the game time it last killed. */
	record Vampire(String kind, long fedNight, long fedAt) {
		static final Codec<Vampire> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Vampire::kind),
			Codec.LONG.fieldOf("fed_night").forGetter(Vampire::fedNight),
			Codec.LONG.fieldOf("fed_at").forGetter(Vampire::fedAt)).apply(i, Vampire::new));
		/** (0.7.24) A vampire player is sent to every client (blood vision for them, the looks for everyone); villagers never are. */
		static final StreamCodec<ByteBuf, Vampire> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Vampire::kind, ByteBufCodecs.VAR_LONG, Vampire::fedNight, ByteBufCodecs.VAR_LONG, Vampire::fedAt, Vampire::new);

		Kind type() {
			Kind k = Kind.parse(this.kind);
			return k == null ? Kind.FEEDER : k;
		}
	}

	/** The world's Elder: who he is ("" for none yet or none now), and the day the last one was slain (-1 if never). */
	record Elder(String uuid, long slainDay) {
		static final Codec<Elder> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("uuid").forGetter(Elder::uuid),
			Codec.LONG.fieldOf("slain_day").forGetter(Elder::slainDay)).apply(i, Elder::new));
	}

	static final AttachmentType<Vampire> VAMPIRE = AttachmentRegistry.create(JaymosKingdoms.id("vampire"),
		builder -> builder.persistent(Vampire.CODEC).copyOnDeath().syncWith(Vampire.STREAM_CODEC.cast(), (target, viewer) -> target instanceof Player));
	/** On the bitten who aren't turned yet: the Overworld clock when they were bitten. */
	static final AttachmentType<Long> BITTEN = AttachmentRegistry.create(JaymosKingdoms.id("vampire_bitten"),
		builder -> builder.persistent(Codec.LONG));
	static final AttachmentType<Elder> ELDER = AttachmentRegistry.create(JaymosKingdoms.id("elder_vampire"),
		builder -> builder.persistent(Elder.CODEC));

	private static final net.minecraft.resources.Identifier HEALTH = JaymosKingdoms.id("vampire_health");
	private static final net.minecraft.resources.Identifier SPEED = JaymosKingdoms.id("vampire_night_speed");

	/** A chase under way. */
	private static final class Hunt {
		final UUID prey;
		final long since;
		long lastBite;
		boolean bit;

		Hunt(UUID prey, long since) {
			this.prey = prey;
			this.since = since;
			this.lastBite = since;
		}
	}

	/** Loaded vampires and bitten, by UUID; chases; who has shown itself (the night it did). */
	private static final Map<UUID, PathfinderMob> LOADED = new HashMap<>();
	private static final Map<UUID, Hunt> HUNTS = new HashMap<>();
	private static final Map<UUID, Long> EXPOSED = new HashMap<>();
	/** Elders in flight (by their UUID), and when each may change again (game time). */
	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	private static final Map<UUID, Long> BAT_READY = new HashMap<>();

	/** The Elder flying as a bat: the bat, and where he's going (his prey, or a place to hide). */
	private static final class Flight {
		final PathfinderMob elder;
		final Bat bat;
		final @Nullable LivingEntity prey;
		Vec3 to;
		double cruise;
		int ticks;

		Flight(PathfinderMob elder, Bat bat, @Nullable LivingEntity prey, Vec3 to) {
			this.elder = elder;
			this.bat = bat;
			this.prey = prey;
			this.to = to;
			this.cruise = Math.max(elder.getY(), to.y) + BAT_CRUISE;
		}
	}

	private Vampires() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(BAT_TAG) && FLIGHTS.values().stream().noneMatch(f -> f.bat == entity)) {
				// (a bat left over from a flight cut short by a restart)
				entity.discard();
				return;
			}
			if (entity instanceof PathfinderMob mob && (mob.hasAttached(VAMPIRE) || mob.hasAttached(BITTEN))) {
				LOADED.put(mob.getUUID(), mob);
				if (kind(mob) == Kind.ELDER && !FLIGHTS.containsKey(mob.getUUID()) && mob.isNoGravity()) {
					human(mob);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Vampires::allowDamage);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> new ArrayList<>(FLIGHTS.values()).forEach(f -> land(f, false)));
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(Vampires::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Vampires::died);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
			if (taken > 0.0F && source.is(DamageTypeTags.IS_FIRE) && isVampire(entity) && kind(entity) != Kind.DHAMPIR && entity.isAlive()) {
				entity.setHealth(Math.max(0.5F, entity.getHealth() - taken * (FIRE_FACTOR - 1.0F)));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			HUNTS.clear();
			FLIGHTS.clear();
			BAT_READY.clear();
			EXPOSED.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("vampire").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("elder").executes(c -> makeElder(c.getSource())))
				.then(Commands.literal("turn").then(Commands.argument("kind", StringArgumentType.word())
					.suggests((c, b) -> {
						for (Kind k : Kind.values()) {
							if (k != Kind.ELDER && k != Kind.PLAYER && k != Kind.DHAMPIR) {
								b.suggest(k.id());
							}
						}
						return b.buildFuture();
					})
					.executes(c -> turnNearest(c.getSource(), StringArgumentType.getString(c, "kind")))))
				.then(Commands.literal("cure").executes(c -> cureNearest(c.getSource())))
				.then(Commands.literal("where").executes(c -> where(c.getSource()))))));
	}

	// ---- who's what ----------------------------------------------------------------------------------------------------

	public static boolean isVampire(Entity e) {
		return e.hasAttached(VAMPIRE);
	}

	static @Nullable Kind kind(Entity e) {
		Vampire v = e.getAttached(VAMPIRE);
		return v == null ? null : v.type();
	}

	/** (0.7.332) How long (ticks) since the last bite the pallor starts to show, and the time without one at which it's full. */
	static final long PALLOR_FROM = 6000L, PALLOR_FULL = 48000L;

	/**
	 * (0.7.332) How ashen a vampire player's skin shows (0-1): barely when they fed within the day, fully when starving. Read
	 * from the synced {@link Vampire#fedAt()}, so it works on either side; 0 for anyone who isn't one.
	 */
	public static float pallor(Entity e) {
		Vampire v = e.getAttached(VAMPIRE);
		if (v == null) {
			return 0.0F;
		}
		long since = Math.max(0L, e.level().getGameTime() - v.fedAt());
		return net.minecraft.util.Mth.clamp((since - PALLOR_FROM) / (float)(PALLOR_FULL - PALLOR_FROM), 0.0F, 1.0F);
	}

	/** A vampire that has shown itself tonight: guards go for it, villagers run. */
	public static boolean exposed(Entity e) {
		Long night = EXPOSED.get(e.getUUID());
		// (0.7.332) or named to the realm by a priest ({@link Accusations})
		return night != null && e.isAlive() && isVampire(e) && night == nightIndex(e.level().getOverworldClockTime()) && night(e.level().getOverworldClockTime())
			|| isVampire(e) && Accusations.exposed(e);
	}

	/** One of the world's people a vampire may feed on: a grown villager (MCA's too), or a realm's folk; not another vampire. */
	static boolean person(LivingEntity e) {
		return e.isAlive() && !e.isBaby() && !isVampire(e) && (e instanceof Villager || e instanceof Folk) && !Lycans.changed(e);
	}

	/** (stage 3) A player a vampire would drink from: in survival, not one of them, not a beast. */
	static boolean mortal(LivingEntity e) {
		return e instanceof ServerPlayer p && p.isAlive() && !p.isCreative() && !p.isSpectator() && !isVampire(p) && !WerewolfPlayers.beast(p);
	}

	public static boolean night(long clock) {
		long t = Math.floorMod(clock, DAY);
		return t >= NIGHT_FROM && t < NIGHT_TO;
	}

	/** The night {@code clock} belongs to: a morning counts with the night before it. */
	public static long nightIndex(long clock) {
		return Math.floorDiv(clock - NIGHT_FROM, DAY);
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		long now = overworld.getGameTime();
		boolean night = night(clock);
		if (now % ARRIVE_EVERY == 0 && !night) {
			arrive(overworld, clock);
		}
		if (!FLIGHTS.isEmpty()) {
			for (Flight f : new ArrayList<>(FLIGHTS.values())) {
				fly(f, night);
			}
		}
		if (LOADED.isEmpty()) {
			return;
		}
		for (Iterator<PathfinderMob> it = new ArrayList<>(LOADED.values()).iterator(); it.hasNext(); ) {
			PathfinderMob mob = it.next();
			if (!mob.isAlive() || mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
				LOADED.remove(mob.getUUID());
				continue;
			}
			if ((now + mob.getId()) % 5 != 0 || FLIGHTS.containsKey(mob.getUUID())) {
				continue;
			}
			Long bitten = mob.getAttached(BITTEN);
			if (bitten != null && night && nightIndex(clock) > nightIndex(bitten)) {
				turn(level, mob, roll(level.getRandom()));
			}
			Vampire v = mob.getAttached(VAMPIRE);
			if (v == null) {
				continue;
			}
			if (night) {
				nightStep(level, mob, v, clock, now);
			} else {
				dayStep(mob);
			}
		}
	}

	private static Kind roll(RandomSource random) {
		float r = random.nextFloat();
		return r < FEEDER_CHANCE ? Kind.FEEDER : r < FEEDER_CHANCE + ABSTAINER_CHANCE ? Kind.ABSTAINER : Kind.HUNTER;
	}

	/** By day: just one of the living, if a weak one. */
	private static void dayStep(PathfinderMob mob) {
		if (HUNTS.remove(mob.getUUID()) != null) {
			release(mob);
		}
		speed(mob, false);
		if (mob.tickCount % 100 < 5) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 140, 0, true, false, false));
		}
	}

	private static void nightStep(ServerLevel level, PathfinderMob mob, Vampire v, long clock, long now) {
		Kind kind = v.type();
		long nightNo = nightIndex(clock);
		// someone hurt it: it runs if it's badly hurt, else it fights them
		LivingEntity foe = mob.getLastHurtByMob();
		if (foe != null && (!foe.isAlive() || mob.tickCount - mob.getLastHurtByMobTimestamp() > GRUDGE_TICKS || foe.distanceTo(mob) > GRUDGE_RANGE
			|| foe instanceof ServerPlayer p && (p.isCreative() || p.isSpectator()))) {
			foe = null;
		}
		if (foe != null && mob.getHealth() < mob.getMaxHealth() * FLEE_AT) {
			expose(mob, nightNo);
			if (kind == Kind.ELDER && batReady(mob, now)) {
				Vec3 away = mob.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0).normalize().scale(BAT_FLEE).add(mob.position());
				takeOff(level, mob, null, ground(level, away));
				return;
			}
			flee(mob, foe);
			eyes(level, mob);
			return;
		}
		Hunt hunt = HUNTS.get(mob.getUUID());
		LivingEntity prey = hunt == null ? null : level.getEntity(hunt.prey) instanceof LivingEntity l ? l : null;
		if (foe != null && foe != prey) {
			hunt = new Hunt(foe.getUUID(), now);
			HUNTS.put(mob.getUUID(), hunt);
			prey = foe;
		}
		if (prey != null && (!prey.isAlive() || prey.distanceTo(mob) > LOSE_RANGE || now - hunt.lastBite > GIVE_UP_TICKS)) {
			HUNTS.remove(mob.getUUID());
			release(mob);
			hunt = null;
			prey = null;
		}
		if (prey == null && kind == Kind.ELDER && NightbornBlood.court(level, mob, now)) {
			// (0.7.88) one of the Nightborn near: he'd sooner have them as kin
			speed(mob, false);
			return;
		}
		if (prey == null && hungry(v, kind, nightNo, now)) {
			prey = findPrey(level, mob, kind);
			if (prey != null) {
				hunt = new Hunt(prey.getUUID(), now);
				HUNTS.put(mob.getUUID(), hunt);
			}
		}
		if (prey == null || hunt == null) {
			speed(mob, false);
			if (mob.tickCount % 20 < 5 && mob.getHealth() < mob.getMaxHealth() && mob.getLastHurtByMob() == null) {
				mob.heal(NIGHT_REGEN);
			}
			return;
		}
		// on the hunt: it shows itself
		if (mob.isSleeping()) {
			mob.stopSleeping();
		}
		expose(mob, nightNo);
		speed(mob, true);
		eyes(level, mob);
		if (kind == Kind.ELDER && foe == null && mob.distanceTo(prey) > BAT_MIN && batReady(mob, now)) {
			takeOff(level, mob, prey, prey.position());
			return;
		}
		chase(mob, prey);
		if (mob.distanceTo(prey) <= BITE_REACH && now - hunt.lastBite >= BITE_EVERY && mob.hasLineOfSight(prey)) {
			hunt.lastBite = now;
			strike(level, mob, v, kind, prey, nightNo, now, foe == prey);
			if (kind == Kind.ABSTAINER && hunt.bit) {
				// (it let go after one bite: it's fed, ashamed, and it goes)
				mob.setAttached(VAMPIRE, new Vampire(v.kind(), nightNo, now));
				HUNTS.remove(mob.getUUID());
				release(mob);
			}
		}
	}

	private static boolean hungry(Vampire v, Kind kind, long nightNo, long now) {
		return switch (kind) {
			case ELDER -> now - v.fedAt() >= ELDER_REST || now < v.fedAt();
			case FEEDER, HUNTER -> v.fedNight() < nightNo;
			case ABSTAINER -> nightNo - v.fedNight() >= THIRST_NIGHTS;
			case PLAYER, DHAMPIR -> false;
		};
	}

	/** The nearest one it'd go for: anyone (a hunter: a vampire that's shown itself). */
	private static @Nullable LivingEntity findPrey(ServerLevel level, PathfinderMob mob, Kind kind) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(kind == Kind.ELDER ? ELDER_PREY_RANGE : PREY_RANGE),
			e -> e != mob && (kind == Kind.HUNTER ? e.isAlive() && exposed(e) : person(e) || mortal(e) && !(kind == Kind.ELDER && NightbornBlood.kin(e))))) {
			double d = e.distanceToSqr(mob);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** A bite (or, at a vampire or a player who fights it, claws). */
	private static void strike(ServerLevel level, PathfinderMob mob, Vampire v, Kind kind, LivingEntity prey, long nightNo, long now, boolean fighting) {
		boolean elder = kind == Kind.ELDER;
		DamageSource source = level.damageSources().mobAttack(mob);
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		mob.getLookControl().setLookAt(prey, 30.0F, 30.0F);
		if (isVampire(prey) || fighting && !person(prey) && !mortal(prey)) {
			// claws
			prey.hurtServer(level, source, elder ? ELDER_CLAW : CLAW);
			level.playSound(null, prey.blockPosition(), SoundEvents.VEX_HURT, SoundSource.HOSTILE, 0.8F, 0.6F);
			if (prey instanceof ServerPlayer player) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PLAYER_BITE_TICKS, 0));
			}
			if (!prey.isAlive()) {
				fed(mob, v, nightNo, now);
			}
			return;
		}
		if (prey.isSleeping()) {
			prey.stopSleeping();
		}
		float bite = NightbornBlood.bite(prey, elder ? ELDER_BITE : BITE);
		if (bite <= 0.0F) {
			// (0.7.88) Old Blood: a Nightborn's blood won't let it take them; it's had its fill
			fed(mob, v, nightNo, now);
			return;
		}
		prey.hurtServer(level, source, bite);
		prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HOLD_TICKS, elder ? ELDER_HOLD : HOLD, false, false, false));
		mob.heal(bite * HEAL_SHARE);
		HUNTS.computeIfPresent(mob.getUUID(), (id, h) -> {
			h.bit = true;
			return h;
		});
		level.sendParticles(PowersCompat.blood(BLOOD, 1.0F), prey.getX(), prey.getEyeY() - 0.25, prey.getZ(), 8, 0.15, 0.15, 0.15, 0.0);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, prey.getX(), prey.getEyeY() - 0.2, prey.getZ(), 2, 0.1, 0.1, 0.1, 0.1);
		level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.HOSTILE, 0.9F, 0.55F);
		if (!prey.isAlive()) {
			fed(mob, v, nightNo, now);
			return;
		}
		if (prey instanceof ServerPlayer player) {
			// (a player bitten turns at the next nightfall, VampirePlayers)
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PLAYER_BITE_TICKS, 0));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, PLAYER_BITE_TICKS, 1));
			VampirePlayers.bitten(player);
			return;
		}
		// (the wolf's curse in the blood: it won't take)
		if (!prey.hasAttached(BITTEN) && !Lycans.cursed(prey) && prey instanceof PathfinderMob p) {
			bite(p, level.getOverworldClockTime());
		}
	}

	/** {@code p} was bitten (at {@code clock}): they turn at the next nightfall. */
	static void bite(PathfinderMob p, long clock) {
		p.setAttached(BITTEN, clock);
		LOADED.put(p.getUUID(), p);
	}

	private static void fed(PathfinderMob mob, Vampire v, long nightNo, long now) {
		mob.setAttached(VAMPIRE, new Vampire(v.kind(), nightNo, now));
		HUNTS.remove(mob.getUUID());
		release(mob);
	}

	/** (Nightfall) A vampire fed raw beef by hand: the hunger's quieted for tonight, as if it had fed. */
	public static boolean feedBeef(PathfinderMob mob) {
		Vampire v = mob.getAttached(VAMPIRE);
		if (v == null) {
			return false;
		}
		long clock = mob.level().getOverworldClockTime();
		fed(mob, v, nightIndex(clock), mob.level().getGameTime());
		return true;
	}

	/** (Nightfall) A half-turned player: none of a vampire's weaknesses. */
	public static boolean dhampir(Entity e) {
		return kind(e) == Kind.DHAMPIR;
	}

	/** (0.7.88) The Elder drank from someone who offered: he's fed, and rests. */
	static void fedElder(PathfinderMob elder, long clock, long now) {
		Vampire v = elder.getAttached(VAMPIRE);
		if (v != null) {
			fed(elder, v, nightIndex(clock), now);
		}
	}

	static void expose(LivingEntity mob, long nightNo) {
		EXPOSED.put(mob.getUUID(), nightNo);
	}

	/** Red eyes in the dark. */
	private static void eyes(ServerLevel level, PathfinderMob mob) {
		Vec3 look = Vec3.directionFromRotation(0.0F, mob.getYHeadRot());
		Vec3 side = new Vec3(-look.z, 0.0, look.x).scale(0.11);
		Vec3 at = mob.getEyePosition().add(look.scale(0.3));
		DustParticleOptions eye = new DustParticleOptions(EYES, 0.45F);
		level.sendParticles(eye, at.x + side.x, at.y, at.z + side.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(eye, at.x - side.x, at.y, at.z - side.z, 1, 0.0, 0.0, 0.0, 0.0);
	}

	static void chase(PathfinderMob mob, LivingEntity prey) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(prey, true), VILLAGER_HUNT_SPEED, 1);
		} else {
			mob.getNavigation().moveTo(prey, FOLK_HUNT_SPEED);
			if (mob instanceof Folk folk && folk.combatant()) {
				// (its own blows too, and it won't be put to bed)
				folk.setTarget(prey);
			}
		}
	}

	private static void flee(PathfinderMob mob, LivingEntity from) {
		Vec3 away = mob.position().subtract(from.position()).normalize().scale(12.0).add(mob.position());
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new BlockPosTracker(net.minecraft.core.BlockPos.containing(away)), VILLAGER_HUNT_SPEED, 1);
		} else {
			mob.getNavigation().moveTo(away.x, away.y, away.z, FOLK_HUNT_SPEED);
		}
	}

	static void release(PathfinderMob mob) {
		if (mob instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		} else if (mob instanceof Folk folk) {
			folk.setTarget(null);
		}
		mob.getNavigation().stop();
	}

	private static void speed(PathfinderMob mob, boolean hunting) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (hunting) {
			if (speed.getModifier(SPEED) == null) {
				speed.addTransientModifier(new AttributeModifier(SPEED, NIGHT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else if (speed.getModifier(SPEED) != null) {
			speed.removeModifier(SPEED);
		}
	}

	// ---- the Elder's bat form ------------------------------------------------------------------------------------------

	private static boolean batReady(PathfinderMob mob, long now) {
		return now >= BAT_READY.getOrDefault(mob.getUUID(), 0L) && !mob.isPassenger() && !mob.isVehicle();
	}

	private static Vec3 ground(ServerLevel level, Vec3 at) {
		BlockPos column = BlockPos.containing(at);
		return Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column));
	}

	/** He bursts into a bat (in a puff of smoke) and flies for {@code to}, or after {@code prey}. */
	private static void takeOff(ServerLevel level, PathfinderMob elder, @Nullable LivingEntity prey, Vec3 to) {
		Bat bat = EntityTypes.BAT.create(level, EntitySpawnReason.TRIGGERED);
		if (bat == null) {
			return;
		}
		bat.snapTo(elder.getX(), elder.getEyeY() - 0.4, elder.getZ(), elder.getYRot(), 0.0F);
		bat.setNoAi(true);
		bat.setNoGravity(true);
		bat.setResting(false);
		bat.addTag(BAT_TAG);
		bat.setCustomName(elder.getName());
		bat.setCustomNameVisible(false);
		// (known before it's in the world, or the load hook takes it for a leftover)
		FLIGHTS.put(elder.getUUID(), new Flight(elder, bat, prey, to));
		level.addFreshEntity(bat);
		release(elder);
		elder.setInvisible(true);
		elder.setNoGravity(true);
		elder.noPhysics = true;
		level.sendParticles(ParticleTypes.LARGE_SMOKE, elder.getX(), elder.getY(0.5), elder.getZ(), 24, 0.3, 0.6, 0.3, 0.03);
		level.playSound(null, elder.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/** A tick of flight: up over the roofs, across, and down on his prey (or wherever he was going); at dawn he lands. */
	private static void fly(Flight f, boolean night) {
		PathfinderMob elder = f.elder;
		Bat bat = f.bat;
		if (!elder.isAlive() || elder.isRemoved() || !bat.isAlive() || bat.isRemoved() || !night || ++f.ticks > BAT_MAX_TICKS
			|| f.prey != null && !f.prey.isAlive()) {
			land(f, true);
			return;
		}
		if (f.prey != null) {
			f.to = f.prey.position();
		}
		Vec3 at = bat.position();
		Vec3 flat = new Vec3(f.to.x - at.x, 0.0, f.to.z - at.z);
		double across = flat.length();
		f.cruise = Math.max(f.cruise, f.to.y + BAT_CRUISE);
		// (over the roofs while far off, dropping as he nears)
		double wantY = across > 12.0 ? f.cruise : across > 4.0 ? f.to.y + 1.0 + (f.cruise - f.to.y - 1.0) * (across - 4.0) / 8.0 : f.to.y + 1.0;
		Vec3 target = across > 1.0 ? new Vec3(f.to.x, wantY, f.to.z) : f.to.add(0.0, 1.0, 0.0);
		Vec3 step = target.subtract(at);
		if (step.length() < 1.2 && across < 1.5) {
			land(f, true);
			return;
		}
		step = step.normalize().scale(BAT_SPEED);
		// (a little flutter)
		step = step.add(0.0, Math.sin(f.ticks * 0.9) * 0.08, 0.0);
		Vec3 next = at.add(step);
		bat.setDeltaMovement(step);
		bat.setPos(next.x, next.y, next.z);
		bat.setYRot((float)(Math.toDegrees(Math.atan2(-step.x, step.z))));
		bat.setYHeadRot(bat.getYRot());
		elder.setPos(next.x, next.y - 0.5, next.z);
		elder.setDeltaMovement(Vec3.ZERO);
		elder.fallDistance = 0.0;
		if (f.ticks % 4 == 0 && bat.level() instanceof ServerLevel level) {
			level.sendParticles(new DustParticleOptions(EYES, 0.4F), next.x, next.y + 0.2, next.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	/** He takes his own shape again where the bat is (on the ground under it, if it's flying free). */
	private static void land(Flight f, boolean cooldown) {
		FLIGHTS.remove(f.elder.getUUID());
		PathfinderMob elder = f.elder;
		Vec3 at = f.bat.isRemoved() ? elder.position() : f.bat.position();
		f.bat.discard();
		if (!elder.isRemoved() && elder.level() instanceof ServerLevel level) {
			Vec3 feet = f.prey != null && f.prey.isAlive() && f.prey.distanceToSqr(at) < 9.0 ? f.prey.position() : ground(level, at);
			if (feet.y > at.y + 1.0) {
				// (under a roof: wherever the bat is)
				feet = at;
			}
			Vec3 down = feet;
			// (0.7.281) his own shape where the bat is, whoever spoke to him last
			Attention.carry(() -> elder.teleportTo(down.x, down.y, down.z));
			human(elder);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, feet.x, feet.y + 1.0, feet.z, 24, 0.3, 0.6, 0.3, 0.03);
			level.playSound(null, elder.blockPosition(), SoundEvents.BAT_TAKEOFF, SoundSource.HOSTILE, 1.0F, 0.5F);
			if (cooldown) {
				BAT_READY.put(elder.getUUID(), level.getGameTime() + BAT_COOLDOWN);
			}
		}
	}

	private static void human(PathfinderMob elder) {
		elder.setInvisible(false);
		elder.setNoGravity(false);
		elder.noPhysics = false;
		elder.fallDistance = 0.0;
	}

	/** In flight the Elder can't be hurt; a blow to his bat knocks him out of it, and he takes it twice over. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (FLIGHTS.containsKey(entity.getUUID())) {
			return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		}
		if (!entity.entityTags().contains(BAT_TAG)) {
			return true;
		}
		for (Flight f : new ArrayList<>(FLIGHTS.values())) {
			if (f.bat == entity) {
				if (source.getEntity() != null && entity.level() instanceof ServerLevel level) {
					land(f, true);
					f.elder.hurtServer(level, source, amount * BAT_HURT_FACTOR);
				}
				return false;
			}
		}
		return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
	}

	// ---- turning -------------------------------------------------------------------------------------------------------

	/** {@code mob} becomes a vampire of {@code kind} (never fed). */
	static void turn(ServerLevel level, PathfinderMob mob, Kind kind) {
		mob.removeAttached(BITTEN);
		mob.setAttached(VAMPIRE, new Vampire(kind.id(), nightIndex(level.getOverworldClockTime()) - (kind == Kind.ABSTAINER ? 0 : 1), 0L));
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(HEALTH);
			health.addPermanentModifier(new AttributeModifier(HEALTH, kind == Kind.ELDER ? ELDER_HEALTH : VAMPIRE_HEALTH, AttributeModifier.Operation.ADD_VALUE));
			mob.setHealth(mob.getMaxHealth());
		}
		LOADED.put(mob.getUUID(), mob);
		level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY(1.0), mob.getZ(), 10, 0.25, 0.2, 0.25, 0.01);
		JaymosKingdoms.LOGGER.debug("{} turned: {}", mob.getName().getString(), kind.id());
	}

	/** {@code mob} is human again. */
	static void cure(PathfinderMob mob) {
		mob.removeAttached(VAMPIRE);
		mob.removeAttached(BITTEN);
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.removeModifier(HEALTH);
			mob.setHealth(Math.min(mob.getHealth(), mob.getMaxHealth()));
		}
		speed(mob, false);
		if (HUNTS.remove(mob.getUUID()) != null) {
			release(mob);
		}
		EXPOSED.remove(mob.getUUID());
		LOADED.remove(mob.getUUID());
		if (mob.level() instanceof ServerLevel level) {
			Elder e = level.getServer().overworld().getAttached(ELDER);
			if (e != null && e.uuid().equals(mob.getUUID().toString())) {
				level.getServer().overworld().setAttached(ELDER, new Elder("", Math.floorDiv(level.getOverworldClockTime(), DAY)));
			}
		}
	}

	// ---- the Elder -----------------------------------------------------------------------------------------------------

	/** By day, with no Elder in the world: one comes, a grown villager near a player but out of their sight. */
	private static void arrive(ServerLevel overworld, long clock) {
		long day = Math.floorDiv(clock, DAY);
		Elder e = overworld.getAttached(ELDER);
		if (day < ELDER_FIRST_DAY || e != null && (!e.uuid().isEmpty() || e.slainDay() >= 0 && day - e.slainDay() < ELDER_RETURN_DAYS)) {
			return;
		}
		List<ServerPlayer> players = overworld.players().stream().filter(p -> !p.isSpectator()).toList();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayer near = players.get(overworld.getRandom().nextInt(players.size()));
		List<Villager> candidates = overworld.getEntitiesOfClass(Villager.class, near.getBoundingBox().inflate(ARRIVE_RANGE),
			v -> person(v) && !v.hasAttached(BITTEN) && players.stream().noneMatch(p -> p.distanceTo(v) < ARRIVE_UNSEEN));
		if (candidates.isEmpty()) {
			return;
		}
		Villager chosen = candidates.get(overworld.getRandom().nextInt(candidates.size()));
		becomeElder(overworld, chosen);
	}

	private static void becomeElder(ServerLevel overworld, PathfinderMob mob) {
		Elder e = overworld.getAttached(ELDER);
		overworld.setAttached(ELDER, new Elder(mob.getUUID().toString(), e == null ? -1 : e.slainDay()));
		turn((ServerLevel)mob.level(), mob, Kind.ELDER);
		JaymosKingdoms.LOGGER.info("The Elder Vampire walks among the living: {} at {}", mob.getName().getString(), mob.blockPosition().toShortString());
	}

	private static void died(LivingEntity entity, DamageSource source) {
		UUID id = entity.getUUID();
		HUNTS.remove(id);
		EXPOSED.remove(id);
		LOADED.remove(id);
		if (kind(entity) != Kind.ELDER || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerLevel overworld = level.getServer().overworld();
		Elder e = overworld.getAttached(ELDER);
		if (e != null && e.uuid().equals(id.toString())) {
			overworld.setAttached(ELDER, new Elder("", Math.floorDiv(overworld.getOverworldClockTime(), DAY)));
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY(0.5), entity.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, entity.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2F, 0.5F);
		JaymosKingdoms.LOGGER.info("The Elder Vampire {} was slain", entity.getName().getString());
	}

	// ---- operators -----------------------------------------------------------------------------------------------------

	private static @Nullable PathfinderMob nearest(CommandSourceStack source, boolean vampire) {
		ServerLevel level = source.getLevel();
		Vec3 at = source.getPosition();
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(at, at).inflate(16.0),
			m -> vampire ? isVampire(m) || m.hasAttached(BITTEN) : person(m))) {
			double d = m.distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		return best;
	}

	private static int makeElder(CommandSourceStack source) {
		ServerLevel overworld = source.getServer().overworld();
		Elder e = overworld.getAttached(ELDER);
		if (e != null && !e.uuid().isEmpty()) {
			Entity old = overworld.getEntity(UUID.fromString(e.uuid()));
			if (old instanceof PathfinderMob m && m.isAlive()) {
				source.sendFailure(Component.literal("The Elder is already " + m.getName().getString() + ", at " + m.blockPosition().toShortString()));
				return 0;
			}
		}
		PathfinderMob mob = nearest(source, false);
		if (mob == null) {
			source.sendFailure(Component.literal("No grown villager or folk within 16 blocks"));
			return 0;
		}
		becomeElder(overworld, mob);
		source.sendSuccess(() -> Component.literal(mob.getName().getString() + " is the Elder Vampire"), true);
		return 1;
	}

	private static int turnNearest(CommandSourceStack source, String id) {
		Kind kind = Kind.parse(id);
		if (kind == null || kind == Kind.ELDER || kind == Kind.PLAYER || kind == Kind.DHAMPIR) {
			source.sendFailure(Component.literal("feeder, abstainer or hunter"));
			return 0;
		}
		PathfinderMob mob = nearest(source, false);
		if (mob == null) {
			source.sendFailure(Component.literal("No grown villager or folk within 16 blocks"));
			return 0;
		}
		turn(source.getLevel(), mob, kind);
		source.sendSuccess(() -> Component.literal(mob.getName().getString() + " is a vampire (" + kind.id() + ")"), true);
		return 1;
	}

	private static int cureNearest(CommandSourceStack source) {
		PathfinderMob mob = nearest(source, true);
		if (mob == null) {
			source.sendFailure(Component.literal("No vampire or bitten within 16 blocks"));
			return 0;
		}
		cure(mob);
		source.sendSuccess(() -> Component.literal(mob.getName().getString() + " is human again"), true);
		return 1;
	}

	private static int where(CommandSourceStack source) {
		ServerLevel overworld = source.getServer().overworld();
		Elder e = overworld.getAttached(ELDER);
		if (e == null || e.uuid().isEmpty()) {
			long day = Math.floorDiv(overworld.getOverworldClockTime(), DAY);
			long due = e == null || e.slainDay() < 0 ? ELDER_FIRST_DAY : e.slainDay() + ELDER_RETURN_DAYS;
			source.sendSuccess(() -> Component.literal("No Elder walks the world" + (day < due ? " (one may come from day " + due + ")" : " (one comes soon, near a player)")), false);
			return 1;
		}
		UUID id = UUID.fromString(e.uuid());
		for (ServerLevel level : source.getServer().getAllLevels()) {
			if (level.getEntity(id) instanceof PathfinderMob m) {
				source.sendSuccess(() -> Component.literal("The Elder is " + m.getName().getString() + ", at " + m.blockPosition().toShortString()), false);
				return 1;
			}
		}
		source.sendSuccess(() -> Component.literal("The Elder is out of the loaded world"), false);
		return 1;
	}
}
