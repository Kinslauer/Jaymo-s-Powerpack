package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * Your get (Night's Blood, patch 5): a villager a player's bite turns ({@link Vampires#turn}, the bite's own spread at nightfall)
 * is that vampire's fledgling.
 * <ul>
 * <li>They feel where you are and you feel them ({@link Kinship}): at night an idle fledgling drifts toward its sire from as far as
 * {@link #FEEL}, and the sire hears the nearest as kin, in sight or not ({@link #nearest}). A fledgling's death is felt.</li>
 * <li>They may come when you are hunted ({@link LairHunts}, {@link Hunts}, or any mob after you near you): each rolls once
 * ({@link #COME_NIGHT} by night, {@link #COME_DAY} by day) and goes for one of the hunters.</li>
 * <li>At night they take the four quick orders like a thrall ({@link #heeds}, {@link QuickOrders}). Not by day; not once turned on you.</li>
 * <li>A fledgling grows as the sire does: its blood age is the nights it has fed and its feedings ({@link #score}), set against the
 * sire's own ({@link BloodPage.Blood#score}). One whose age outgrows the sire's by {@link #OUTGROWN} may, on a night, turn on
 * them ({@link #REBEL_CHANCE}): it hunts its sire on sight and heeds no order, and spares them no longer.</li>
 * </ul>
 */
public final class Fledglings {
	/** How far (blocks) a fledgling feels its sire, and drifts toward them by night; when it stops (blocks). */
	static final double FEEL = 150.0, CLOSE = 12.0;
	/** The chance a fledgling comes to a hunted sire's side, by night and by day. */
	static final float COME_NIGHT = 0.7F, COME_DAY = 0.25F;
	/** How far from the sire (blocks) a fledgling answers a hunt, and how near a mob after the sire counts as a hunter. */
	static final double COME_RANGE = 120.0, HUNTER_NEAR = 24.0;
	/** A hunt is answered once per this long (ticks). */
	static final long ANSWER_GAP = 20L * 60 * 5;
	/** How far a fledgling's blood age must outgrow its sire's before it may turn, and the chance on each night. */
	static final int OUTGROWN = 5;
	static final float REBEL_CHANCE = 0.25F;
	/** A rebel goes for a sire this near (blocks). */
	static final double REBEL_RANGE = 64.0;
	/** How often (ticks) fledglings are looked after. */
	static final int EVERY = 20;
	static final double WALK = 0.8;

	/** A fledgling's record: its sire (UUID), the night it turned, the nights it has fed and its feedings, the last night it fed, the last night it weighed turning, and whether it has turned on them. */
	record Get(String sire, long since, int nights, int feedings, long lastNight, long weighed, boolean rebel) {
		static final Codec<Get> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("sire").forGetter(Get::sire),
			Codec.LONG.fieldOf("since").forGetter(Get::since),
			Codec.INT.fieldOf("nights").forGetter(Get::nights),
			Codec.INT.fieldOf("feedings").forGetter(Get::feedings),
			Codec.LONG.fieldOf("last_night").forGetter(Get::lastNight),
			Codec.LONG.fieldOf("weighed").forGetter(Get::weighed),
			Codec.BOOL.fieldOf("rebel").forGetter(Get::rebel)).apply(i, Get::new));

		int score() {
			return nights + feedings;
		}
	}

	/** On a bitten villager not yet turned: who bit them. Kept. */
	static final AttachmentType<String> BITER = AttachmentRegistry.create(JaymosKingdoms.id("fledgling_biter"), b -> b.persistent(Codec.STRING));
	/** On a turned villager: its record as a fledgling. Kept. */
	static final AttachmentType<Get> GET = AttachmentRegistry.create(JaymosKingdoms.id("fledgling"), b -> b.persistent(Get.CODEC));

	private static final Map<UUID, PathfinderMob> LOADED = new HashMap<>();
	private static final Map<UUID, Long> ANSWERED = new HashMap<>();

	private Fledglings() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob m && m.hasAttached(GET)) {
				LOADED.put(m.getUUID(), m);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			LOADED.remove(entity.getUUID());
			ANSWERED.remove(entity.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			ANSWERED.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Fledglings::tick);
		// a fledgling's death is felt by its sire
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Get g = entity.getAttached(GET);
			if (g != null && entity.level().getServer() != null) {
				ServerPlayer sire = entity.level().getServer().getPlayerList().getPlayer(UUID.fromString(g.sire()));
				if (sire != null && sire.isAlive()) {
					sire.level().playSound(null, sire.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.5F);
					sire.level().sendParticles(Fx.wisp(0x5A0A0A, 1.0F), sire.getX(), sire.getY(0.6), sire.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
				}
				LOADED.remove(entity.getUUID());
			}
		});
	}

	// ---- hooks --------------------------------------------------------------------------------------------------------

	/** {@code sire}'s bite has marked {@code mob} to turn at nightfall: if it does, it is theirs. */
	static void sired(PathfinderMob mob, ServerPlayer sire) {
		if (mob instanceof Villager && Vampires.isVampire(sire) && !VampirePlayers.dhampir(sire)) {
			mob.setAttached(BITER, sire.getUUID().toString());
		}
	}

	/** {@code mob} has turned: if a player's bite did it, it is their fledgling. */
	static void turned(ServerLevel level, PathfinderMob mob) {
		String sire = mob.getAttached(BITER);
		mob.removeAttached(BITER);
		if (sire != null && !mob.hasAttached(Servants.MASTER)) {
			mob.setAttached(GET, new Get(sire, Vampires.nightIndex(level.getOverworldClockTime()), 0, 0, Long.MIN_VALUE, Vampires.nightIndex(level.getOverworldClockTime()), false));
			LOADED.put(mob.getUUID(), mob);
		}
	}

	/** {@code mob} is human again: no one's fledgling. */
	static void freed(PathfinderMob mob) {
		mob.removeAttached(BITER);
		mob.removeAttached(GET);
		LOADED.remove(mob.getUUID());
	}

	/** {@code mob} has fed: its blood age grows, a night once and each feeding. */
	static void fed(PathfinderMob mob) {
		Get g = mob.getAttached(GET);
		if (g == null) {
			return;
		}
		long night = Vampires.nightIndex(mob.level().getOverworldClockTime());
		mob.setAttached(GET, new Get(g.sire(), g.since(), g.nights() + (g.lastNight() == night ? 0 : 1), g.feedings() + 1, night, g.weighed(), g.rebel()));
	}

	/** Whether {@code e} is anyone's fledgling. */
	static boolean isGet(Entity e) {
		return e.hasAttached(GET) || e.hasAttached(BITER);
	}

	/** Whether {@code mob} spares {@code e} in its hunt: its sire, its sire's thralls and its fellow get, unless it has turned on its sire. */
	static boolean spares(PathfinderMob mob, LivingEntity e) {
		Get g = mob.getAttached(GET);
		if (g == null || g.rebel()) {
			return false;
		}
		if (e.getUUID().toString().equals(g.sire())) {
			return true;
		}
		Get other = e.getAttached(GET);
		return other != null && other.sire().equals(g.sire()) || g.sire().equals(e.getAttached(Servants.MASTER));
	}

	/** Whether {@code v} takes {@code player}'s orders as their fledgling: a villager, at night, one that has not turned on them. */
	static boolean heeds(Entity v, ServerPlayer player) {
		Get g = v.getAttached(GET);
		return g != null && v instanceof Villager && !g.rebel() && g.sire().equals(player.getUUID().toString())
			&& Vampires.night(v.level().getOverworldClockTime());
	}

	/** The distance (blocks) to {@code sire}'s nearest loaded fledgling within {@code range}, or -1: what the sire feels of them. */
	static double nearest(ServerPlayer sire, double range) {
		double best = -1.0;
		String id = sire.getUUID().toString();
		for (PathfinderMob m : LOADED.values()) {
			Get g = m.getAttached(GET);
			if (g != null && !g.rebel() && m.isAlive() && m.level() == sire.level() && g.sire().equals(id)) {
				double d = m.distanceTo(sire);
				if (d <= range && (best < 0.0 || d < best)) {
					best = d;
				}
			}
		}
		return best;
	}

	/** A fledgling's blood age. */
	static int score(PathfinderMob m) {
		Get g = m.getAttached(GET);
		return g == null ? 0 : g.score();
	}

	// ---- every second -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % EVERY != 3 || LOADED.isEmpty()) {
			return;
		}
		long clock = server.overworld().getOverworldClockTime();
		boolean night = Vampires.night(clock);
		long nightNo = Vampires.nightIndex(clock);
		for (PathfinderMob m : new ArrayList<>(LOADED.values())) {
			Get g = m.getAttached(GET);
			if (g == null || !m.isAlive() || m.isRemoved() || !(m.level() instanceof ServerLevel level)) {
				if (m.isRemoved() || g == null) {
					LOADED.remove(m.getUUID());
				}
				continue;
			}
			ServerPlayer sire;
			try {
				sire = server.getPlayerList().getPlayer(UUID.fromString(g.sire()));
			} catch (IllegalArgumentException e) {
				continue;
			}
			if (sire == null || !sire.isAlive() || sire.level() != level || sire.isSpectator()) {
				continue;
			}
			if (night && !g.rebel() && g.weighed() != nightNo) {
				g = weigh(level, m, g, sire, nightNo);
			}
			if (g.rebel()) {
				if (night && m.distanceTo(sire) <= REBEL_RANGE) {
					Vampires.huntNow(level, m, sire);
				}
				continue;
			}
			if (answer(server, level, m, sire, night, now)) {
				continue;
			}
			if (night && Comrades.target(m) == null && m.getAttached(Followers.ORDER) == null && QuickOrders.stance(m) == null) {
				drift(m, sire);
			}
		}
	}

	/** Once a night: a fledgling grown past its sire may turn on them. */
	private static Get weigh(ServerLevel level, PathfinderMob m, Get g, ServerPlayer sire, long nightNo) {
		BloodPage.Blood theirs = BloodPage.of(sire);
		Get next = new Get(g.sire(), g.since(), g.nights(), g.feedings(), g.lastNight(), nightNo, g.rebel());
		if (theirs != null && g.score() >= theirs.score() + OUTGROWN && level.getRandom().nextFloat() < REBEL_CHANCE) {
			next = new Get(g.sire(), g.since(), g.nights(), g.feedings(), g.lastNight(), nightNo, true);
			Speech.say(level, m, Component.translatable("speech.jaymoskingdoms.get.turn." + level.getRandom().nextInt(2)), Speech.RED, 16.0);
			level.playSound(null, m.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.0F, 0.4F);
		}
		m.setAttached(GET, next);
		return next;
	}

	/** When the sire is hunted a fledgling near enough may go for one of the hunters; true if it did (or has answered lately). */
	private static boolean answer(MinecraftServer server, ServerLevel level, PathfinderMob m, ServerPlayer sire, boolean night, long now) {
		if (m.distanceTo(sire) > COME_RANGE) {
			return false;
		}
		Long last = ANSWERED.get(m.getUUID());
		if (last != null && now - last < ANSWER_GAP) {
			return Comrades.target(m) != null;
		}
		List<Mob> hunters = new ArrayList<>(LairHunts.men(server, sire.getUUID()));
		hunters.addAll(Hunts.men(server, sire.getUUID()));
		for (Mob e : level.getEntitiesOfClass(Mob.class, sire.getBoundingBox().inflate(HUNTER_NEAR),
			e -> e.isAlive() && e != m && e.getTarget() == sire && !hunters.contains(e) && !isGet(e) && !Vampires.isVampire(e))) {
			hunters.add(e);
		}
		hunters.removeIf(h -> !h.isAlive());
		if (hunters.isEmpty()) {
			return false;
		}
		ANSWERED.put(m.getUUID(), now);
		if (level.getRandom().nextFloat() >= (night ? COME_NIGHT : COME_DAY)) {
			return false;
		}
		Mob foe = hunters.stream().min((a, b) -> Double.compare(a.distanceToSqr(sire), b.distanceToSqr(sire))).orElse(null);
		if (foe == null) {
			return false;
		}
		Vampires.huntNow(level, m, foe);
		Speech.say(level, m, Component.translatable("speech.jaymoskingdoms.get.come." + level.getRandom().nextInt(2)), Speech.RED, 16.0);
		return true;
	}

	/** An idle fledgling by night feels where its sire is and drifts toward them. */
	private static void drift(PathfinderMob m, ServerPlayer sire) {
		double d = m.distanceTo(sire);
		if (d <= CLOSE || d > FEEL || !(m instanceof Villager v)) {
			return;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(v, sire.blockPosition(), (float) WALK, (int) CLOSE);
	}
}
