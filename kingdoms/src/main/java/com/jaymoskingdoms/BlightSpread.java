package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * How the Hollow's blight ({@link HollowBlight}) goes from one body to the next, Andy's picks (2026-11-15):
 * <ul>
 * <li><b>Carriers cough it on</b> (Andy: "This happening is why people avoid those that cough"): in the last stage a
 * cough can pass it to anyone close in front of the cougher, players and villagers alike.</li>
 * <li><b>Your blows pass it</b>: a carrier's own melee blows, once it has taken hold, can leave it in whoever they strike.</li>
 * <li><b>Zombie blood</b> (Andy: "Rotten zombie blood should spread infection as well, you must clean yourself and armor,
 * soaking can clear it"): the dead's blood on your skin or armour can give it to you, likelier with an open wound.
 * Scrubbing it off ends the danger, and being Soaked through washes it all away.</li>
 * <li><b>Villagers catch it</b>: from the dead's blows, from carriers and from the dead's blood. They sicken for a day
 * (a cough, slowness, the greying only the knowing see) and rise as one of the dead if nobody cures them; their family
 * grieves, and one who is cured remembers who cured them ({@link Recollections.Kind#CURED_BLIGHT}).</li>
 * <li><b>The blighted dead rise</b>: a villager who dies with it in them gets up the next night unless the body is burned
 * or buried (Jaymo's Kitchen's corpses: a burning body, or one taken away to the grave, never rises).</li>
 * <li><b>Outbreaks</b>: now and then a town's people fall sick, and the sick spread it among their neighbours; while they
 * do, the town board posts work to cure the sick and burn the blighted dead ({@link BoardWork}, {@link QuestJobs}).</li>
 * </ul>
 */
public final class BlightSpread {
	// ---- tuning ----
	/** A last-stage cough can pass it this far in front of the cougher (blocks), within this cone (the dot of the look), this often. */
	static final double COUGH_REACH = 3.5, COUGH_CONE = 0.5;
	static final float COUGH_PASS = 0.2F;
	/** A full mask over the face lets only this share of a cough's breath through. */
	static final float MASK_KEEPS = 0.25F;
	/** A carrier's melee blow passes it this often once it has taken hold (before armour and the wound). */
	static final float BLOW_PASS = 0.06F;
	/** The dead's blood is looked at this often (ticks); each look, a body soaked in it takes the blight this often (before the wound). */
	static final int BLOOD_EVERY = 20 * 5;
	static final float BLOOD_CHANCE = 0.0015F;
	/** A villager's blight runs this long before they rise as one of the dead (ticks: a day), at the stages of a player's. */
	static final long VILLAGER_TURN = 24000L;
	/** A sick villager's bout: one in this many seconds, for this long (ticks). */
	static final int VILLAGER_BOUT_EVERY = 60, VILLAGER_BOUT_TICKS = 20 * 10;
	/** Kin this near a villager who rises turned grieve (blocks); lines for it ({@code blight_spread.jaymoskingdoms.grief.<n>}). */
	static final double GRIEF_RANGE = 32.0;
	static final int GRIEF_LINES = 4, GRIEF_COLOUR = 0xFFB0B8C0;
	/** Outbreaks, looked at this often (ticks) for each town near a player (this far): one of its people falls sick this often... */
	static final int OUTBREAK_EVERY = 20 * 60;
	static final double OUTBREAK_NEAR = 128.0;
	static final float OUTBREAK_SEED = 0.004F;
	/** ...and each sick one gives it to a neighbour this near (blocks) this often. */
	static final double NEIGHBOUR_RANGE = 8.0;
	static final float NEIGHBOUR_PASS = 0.12F;
	/** Hearts a villager gives one who cured them. */
	static final int CURED_HEARTS = 6;

	/** On a Kitchen corpse of one who died blighted; with {@code "." + the night it died in}, when it rises. */
	static final String DEAD_TAG = "jaymoskingdoms.blighted_dead";
	/** A night runs from this time of day (ticks into the day) to the next. */
	static final long NIGHT_FROM = 12500L;
	/** How far round a blighted death to look for the body it left (blocks). */
	static final double BODY_RANGE = 3.0;

	/** Blighted villagers and blighted bodies in loaded chunks. Not kept: they're found again as they load. */
	private static final Set<Villager> SICK = new HashSet<>();
	private static final Set<Entity> DEAD = new HashSet<>();
	/** Deaths to find the bodies of on the next tick: where, and the night. */
	private record Death(ServerLevel level, Vec3 at, long night, Set<UUID> before, int[] left) {
	}
	/** A death's body is looked for this many ticks (it may be laid down a tick or two after). */
	static final int BODY_TICKS = 5;
	/** On the overworld: the blighted bodies that rose (for the burning work), the most remembered. Saved. */
	static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<List<String>> ROSE_SAVED = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
		.create(JaymosKingdoms.id("blighted_risen"), builder -> builder.persistent(com.mojang.serialization.Codec.STRING.listOf()));
	static final int ROSE_KEPT = 128;

	private static final List<Death> DEATHS = new ArrayList<>();
	/** Bodies that rose (for the burning work: one that rose wasn't burned). Not kept. */
	private static final Set<UUID> ROSE = new HashSet<>();

	private BlightSpread() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && !blocked) {
				struck(entity, source);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(BlightSpread::died);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && HollowBlight.blighted(v)) {
				SICK.add(v);
			} else if (entity.entityTags().contains(DEAD_TAG)) {
				DEAD.add(entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				SICK.remove(v);
			}
			DEAD.remove(entity);
		});
		ServerTickEvents.END_SERVER_TICK.register(BlightSpread::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SICK.clear();
			DEAD.clear();
			DEATHS.clear();
			ROSE.clear();
		});
	}

	// ---- catching it ---------------------------------------------------------------------------------------------------

	/** Whether {@code body} can catch it at all: a player who isn't proof against it, or a grown or young villager. */
	static boolean canCatch(LivingEntity body) {
		if (!body.isAlive() || HollowBlight.blighted(body) || BlightCures.proof(body)) {
			return false;
		}
		if (body instanceof ServerPlayer player) {
			return !HollowBlight.immune(player);
		}
		return body instanceof Villager;
	}

	/** {@code body} catches the {@code turn}'s blight (from {@code by}, if anyone): a player as from a wound, a villager sickens. */
	static void catchIt(LivingEntity body, Hollows.Turn turn, @Nullable Entity by) {
		if (!canCatch(body)) {
			return;
		}
		if (body instanceof ServerPlayer player) {
			HollowBlight.take(player, turn, by);
		} else if (body instanceof Villager v) {
			String look = turn.look() == null ? "pallid" : turn.look().key();
			v.setAttached(HollowBlight.BLIGHT, new HollowBlight.Blight(look, turn.line(), v.level().getGameTime(), 0, false, 0L, 0L));
			SICK.add(v);
			JaymosKingdoms.LOGGER.info("A villager ({}) took the Hollow's blight from {}", v.getName().getString(),
				by == null ? "nothing living" : by.getType().getDescriptionId());
		}
	}

	/** The blight in {@code body} as one who meets them would catch it. */
	static Hollows.Turn turnIn(LivingEntity body) {
		HollowBlight.Blight blight = body.getAttached(HollowBlight.BLIGHT);
		return blight == null ? new Hollows.Turn(HeritageVariants.Variant.ZOMBIE, 1.0F, "zombie") : blight.turn();
	}

	/** How far {@code body}'s blight has come: 0 still settling in, 1 taken hold, 2 near the end. */
	static int stage(LivingEntity body) {
		HollowBlight.Blight blight = body.getAttached(HollowBlight.BLIGHT);
		if (blight == null) {
			return -1;
		}
		if (body instanceof ServerPlayer) {
			return blight.told();
		}
		float done = (body.level().getGameTime() - blight.since()) / (float) VILLAGER_TURN;
		return done >= HollowBlight.STAGE_WEAK ? 2 : done >= HollowBlight.STAGE_HUNGRY ? 1 : 0;
	}

	/** How likely a blow or a breath is to get in: armour wards it off, an open wound lets it in. */
	private static float through(LivingEntity body) {
		return (1.0F - HollowBlight.ward(body)) * (PowersCompat.cuts(body) > 0 ? HollowBlight.WOUND_TIMES : HollowBlight.NO_WOUND_TIMES);
	}

	/** A blow landed on {@code body}: a villager struck by one of the dead, or anyone struck by a carrier's own hand. */
	private static void struck(LivingEntity body, DamageSource source) {
		Entity by = source.getEntity();
		if (by == null || by == body || !(body.level() instanceof ServerLevel) || !canCatch(body)) {
			return;
		}
		if (body instanceof Villager) {
			Hollows.Turn turn = Hollows.turnOf(by);
			if (turn != null && !Vampires.isVampire(by)) {
				if (body.getRandom().nextFloat() < turn.chance() * HollowBlight.BLIGHT_SHARE * through(body)) {
					catchIt(body, turn, by);
				}
				return;
			}
		}
		// a carrier's own blow, struck by hand
		if (by instanceof LivingEntity carrier && source.getDirectEntity() == by && stage(carrier) >= 1
			&& body.getRandom().nextFloat() < BLOW_PASS * through(body)) {
			catchIt(body, turnIn(carrier), carrier);
		}
	}

	/** {@code body} coughed: in the last stage it may pass to anyone close in front of them. */
	static void coughed(ServerLevel level, LivingEntity body) {
		if (stage(body) < 2) {
			return;
		}
		Vec3 look = body.getLookAngle();
		Vec3 mouth = body.getEyePosition();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(COUGH_REACH), e -> e != body && canCatch(e))) {
			Vec3 to = near.getEyePosition().subtract(mouth);
			double d = to.length();
			if (d > COUGH_REACH || d < 1.0E-3 || to.scale(1.0 / d).dot(look) < COUGH_CONE || !near.hasLineOfSight(body)) {
				continue;
			}
			// a full mask over the face keeps the breath out, as the greying is kept in
			float chance = COUGH_PASS * (near instanceof ServerPlayer p && Prejudice.masked(p) ? MASK_KEEPS : 1.0F);
			if (near.getRandom().nextFloat() < chance) {
				catchIt(near, turnIn(body), body);
			}
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		DEATHS.removeIf(d -> bodyOf(d) || --d.left()[0] <= 0);
		long tick = server.getTickCount();
		if (tick % 20 == 0) {
			for (Villager v : new ArrayList<>(SICK)) {
				if (v.isRemoved() || !v.isAlive() || !HollowBlight.blighted(v)) {
					SICK.remove(v);
					continue;
				}
				sicken((ServerLevel) v.level(), v);
			}
			for (Entity body : new ArrayList<>(DEAD)) {
				if (body.isRemoved()) {
					DEAD.remove(body);
					continue;
				}
				rise((ServerLevel) body.level(), body);
			}
		}
		if (tick % BLOOD_EVERY == 7) {
			for (ServerLevel level : server.getAllLevels()) {
				for (ServerPlayer player : level.players()) {
					soakedIn(player);
				}
			}
			for (Villager v : villagersNearPlayers(server)) {
				soakedIn(v);
			}
		}
		if (tick % OUTBREAK_EVERY == 11) {
			outbreaks(server);
		}
	}

	/** The dead's blood on {@code body}: soaked through, it washes off; else it may get in. */
	private static void soakedIn(LivingEntity body) {
		if (!body.isAlive()) {
			return;
		}
		if (PowersCompat.soaked(body)) {
			PowersCompat.washZombieBlood(body);
			return;
		}
		if (!canCatch(body)) {
			return;
		}
		float blood = PowersCompat.zombieBlood(body);
		if (blood > 0.0F && body.getRandom().nextFloat() < BLOOD_CHANCE * blood
			* (PowersCompat.cuts(body) > 0 ? HollowBlight.WOUND_TIMES : HollowBlight.NO_WOUND_TIMES)) {
			catchIt(body, new Hollows.Turn(HeritageVariants.Variant.ZOMBIE, 1.0F, "zombie"), null);
		}
	}

	private static List<Villager> villagersNearPlayers(MinecraftServer server) {
		Set<Villager> out = new HashSet<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				out.addAll(level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(OUTBREAK_NEAR), Villager::isAlive));
			}
		}
		return new ArrayList<>(out);
	}

	/** Once a second for a sick villager: a cough, a bout, the motes the knowing see; at the end they rise as one of the dead. */
	private static void sicken(ServerLevel level, Villager v) {
		HollowBlight.Blight blight = v.getAttached(HollowBlight.BLIGHT);
		long now = level.getGameTime();
		float done = (now - blight.since()) / (float) VILLAGER_TURN;
		if (done >= 1.0F) {
			turn(level, v);
			return;
		}
		int stage = stage(v);
		RandomSource random = v.getRandom();
		if (stage > 0 && random.nextInt(BlightSigns.COUGH_EVERY[stage]) == 0) {
			BlightSigns.cough(level, v);
		}
		if (stage > 0 && random.nextInt(VILLAGER_BOUT_EVERY) == 0) {
			v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, VILLAGER_BOUT_TICKS, 0, true, false, false));
			if (stage > 1) {
				v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, VILLAGER_BOUT_TICKS, 0, true, false, false));
			}
		}
		if (now % (20L * BlightSigns.TELL_EVERY) < 20) {
			BlightSigns.tell(level, v, now);
		}
	}

	/** A sick villager nobody cured rises as one of the dead; their kin who see it grieve. */
	private static void turn(ServerLevel level, Villager v) {
		SICK.remove(v);
		KeepingCells.turned(v);
		v.removeAttached(HollowBlight.BLIGHT);
		Zombie raiser = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.CONVERSION);
		if (raiser == null) {
			return;
		}
		raiser.snapTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), 0.0F);
		grieve(level, v);
		level.playSound(null, v.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.NEUTRAL, 1.0F, 0.6F);
		if (!raiser.convertVillagerToZombieVillager(level, v)) {
			JaymosKingdoms.LOGGER.debug("A blighted villager ({}) couldn't be turned", v.getName().getString());
		}
		raiser.discard();
	}

	/** {@code who}'s kin and partner near them see them turn and grieve, in their own words; others back away. */
	private static void grieve(ServerLevel level, LivingEntity who) {
		RandomSource random = who.getRandom();
		for (Villager v : level.getEntitiesOfClass(Villager.class, who.getBoundingBox().inflate(GRIEF_RANGE), x -> x != who && x.isAlive() && !x.isSleeping())) {
			int kin = McaCompat.kinOf(v, who.getUUID());
			if (kin == McaCompat.STRANGERS) {
				if (v.distanceTo(who) < HollowSighting.FLEE_DISTANCE) {
					HollowSighting.flee(v, who);
				}
				continue;
			}
			v.getLookControl().setLookAt(who, 30.0F, 30.0F);
			Speech.say(level, v, Component.translatable("blight_spread.jaymoskingdoms.grief." + random.nextInt(GRIEF_LINES), who.getName()), GRIEF_COLOUR,
				GRIEF_RANGE);
		}
	}

	// ---- the blighted dead -------------------------------------------------------------------------------------------

	/** The night {@code dayTime} falls in (a night is counted from its dusk). */
	static long nightOf(long dayTime) {
		return Math.floorDiv(dayTime - NIGHT_FROM, 24000L);
	}

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Villager) || !HollowBlight.blighted(entity) || !(entity.level() instanceof ServerLevel level) || !KitchenCompat.loaded()) {
			return;
		}
		// (its body is laid down as it dies: found next tick, the ones already about it set aside)
		Set<UUID> before = new HashSet<>();
		for (Entity e : level.getEntities((Entity) null, entity.getBoundingBox().inflate(BODY_RANGE), KitchenCompat::isCorpse)) {
			before.add(e.getUUID());
		}
		DEATHS.add(new Death(level, entity.position(), nightOf(level.getOverworldClockTime()), before, new int[] {BODY_TICKS}));
	}

	/** Looks for the body a blighted death left and marks it; true once found. */
	private static boolean bodyOf(Death d) {
		for (Entity e : d.level().getEntities((Entity) null, new AABB(d.at(), d.at()).inflate(BODY_RANGE), KitchenCompat::isCorpse)) {
			if (!d.before().contains(e.getUUID()) && !e.entityTags().contains(DEAD_TAG)) {
				e.addTag(DEAD_TAG);
				e.addTag(DEAD_TAG + "." + d.night());
				DEAD.add(e);
				return true;
			}
		}
		return false;
	}

	/** The night a blighted body was left in, from its tag. */
	private static long diedIn(Entity body) {
		for (String tag : body.entityTags()) {
			if (tag.startsWith(DEAD_TAG + ".")) {
				try {
					return Long.parseLong(tag.substring(DEAD_TAG.length() + 1));
				} catch (NumberFormatException e) {
					return Long.MIN_VALUE;
				}
			}
		}
		return Long.MIN_VALUE;
	}

	/** Whether {@code body} is a blighted body still lying unburned, that would rise. */
	static boolean unburned(Entity body) {
		return !body.isRemoved() && body.entityTags().contains(DEAD_TAG) && !body.isOnFire();
	}

	/** A blighted body, unburned and lying free, gets up once the next night has come. */
	private static void rise(ServerLevel level, Entity body) {
		if (!level.isDarkOutside() || body.isOnFire() || !KitchenCompat.isFreeCorpse(body) || nightOf(level.getOverworldClockTime()) <= diedIn(body)) {
			return;
		}
		ZombieVillager risen = EntityTypes.ZOMBIE_VILLAGER.create(level, EntitySpawnReason.EVENT);
		if (risen == null) {
			return;
		}
		risen.snapTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), 0.0F);
		risen.finalizeSpawn(level, level.getCurrentDifficultyAt(body.blockPosition()), EntitySpawnReason.EVENT, null);
		if (body.hasCustomName()) {
			risen.setCustomName(body.getCustomName());
		}
		level.addFreshEntity(risen);
		level.playSound(null, body.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.7F);
		level.sendParticles(Fx.miasma(0x9A9E92, 0.8F), body.getX(), body.getY() + 0.4, body.getZ(), 8, 0.3, 0.2, 0.3, 0.01);
		ROSE.add(body.getUUID());
		ServerLevel world = Factions.level(level.getServer());
		List<String> saved = new ArrayList<>(world.getAttachedOrElse(ROSE_SAVED, List.of()));
		saved.add(body.getStringUUID());
		while (saved.size() > ROSE_KEPT) {
			saved.removeFirst();
		}
		world.setAttached(ROSE_SAVED, saved);
		DEAD.remove(body);
		body.discard();
		JaymosKingdoms.LOGGER.info("A blighted body rose unburned at {}", body.blockPosition());
	}

	/** Whether the blighted body {@code id} got up (it wasn't burned). */
	static boolean rose(ServerLevel level, UUID id) {
		return ROSE.contains(id) || Factions.level(level.getServer()).getAttachedOrElse(ROSE_SAVED, List.of()).contains(id.toString());
	}

	// ---- outbreaks ---------------------------------------------------------------------------------------------------

	/** {@code f}'s sick: the blighted villagers on its land. */
	static List<Villager> sickIn(ServerLevel level, Faction f) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : SICK) {
			if (v.level() == level && v.isAlive() && HollowBlight.blighted(v)) {
				Faction theirs = Theft.lawAt(level, v.blockPosition());
				if (theirs != null && theirs.id() == f.id()) {
					out.add(v);
				}
			}
		}
		return out;
	}

	/** The blighted bodies lying unburned on {@code f}'s land. */
	static List<Entity> deadIn(ServerLevel level, Faction f) {
		List<Entity> out = new ArrayList<>();
		for (Entity body : DEAD) {
			if (body.level() == level && unburned(body)) {
				Faction theirs = Theft.lawAt(level, body.blockPosition());
				if (theirs != null && theirs.id() == f.id()) {
					out.add(body);
				}
			}
		}
		return out;
	}

	/** Now and then a town near a player falls sick, and its sick pass it to their neighbours. */
	private static void outbreaks(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			return;
		}
		RandomSource random = level.getRandom();
		Set<Integer> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			Faction f = Factions.around(server, level, player.blockPosition(), OUTBREAK_NEAR);
			if (f == null || f.fallen() || !seen.add(f.id())) {
				continue;
			}
			List<Villager> sick = sickIn(level, f);
			if (sick.isEmpty()) {
				if (random.nextFloat() < OUTBREAK_SEED) {
					List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Villages.VILLAGE_RADIUS), BlightSpread::canCatch);
					if (!folk.isEmpty()) {
						catchIt(folk.get(random.nextInt(folk.size())), new Hollows.Turn(HeritageVariants.Variant.ZOMBIE, 1.0F, "zombie"), null);
						JaymosKingdoms.LOGGER.info("An outbreak of the Hollow's blight began in {}", f.name());
					}
				}
				continue;
			}
			for (Villager v : sick) {
				if (stage(v) < 1 || random.nextFloat() >= NEIGHBOUR_PASS) {
					continue;
				}
				List<Villager> near = level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(NEIGHBOUR_RANGE), BlightSpread::canCatch);
				if (!near.isEmpty()) {
					catchIt(near.get(random.nextInt(near.size())), turnIn(v), v);
				}
			}
		}
	}

	/** A sick villager was cured by {@code player}: they remember it, and it counts for any work curing their town's sick. */
	static void cured(ServerLevel level, Villager v, ServerPlayer player) {
		SICK.remove(v);
		if (!KeepingCells.cured(level, v, player)) {
			// (an old home's smith cured in his sleep never knows who did it)
			Recollections.remember(v, player, Recollections.Kind.CURED_BLIGHT, Component.empty());
			McaCompat.hearts(v, player, CURED_HEARTS);
		}
		QuestJobs.curedSick(level, player, v);
	}
}
