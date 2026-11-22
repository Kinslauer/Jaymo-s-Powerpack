package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) <b>Awakened strangers</b>: gifted folk on the road (an MCA villager with a power, Jaymo's Powers' gifted
 * folk, who fights with it) who seek out others who carry a power to test their gift on them. Now and then
 * ({@link #CHANCE} each {@link #LOOK_EVERY} ticks, no oftener than {@link #GAP} for one player) one comes walking up to a
 * player with a power, out in the wild of the Overworld, named with the title of their mastery ("Adept" or, for a master
 * of it, "Master" of their power: masters likelier the higher the world tier), and fights them, by hand and with the
 * power. Hurt to {@link #YIELD_AT} of their health, they yield, say so, and go on their way. Without MCA, a plain villager.
 * <p>
 * Andy (2026-11-19, beamed down by one a few minutes into a new life: "way too strong to encounter that early ... tune that
 * fighter WAY down"; then "they need to introduce themselves first, and are persistent. if you ignore them they will come
 * back. and wait outside if you go to sleep. and mention how long it took if you do"; and of a downed player, "dependent
 * on their nature they may get you up, say it was a good fight, and might come back, or they might fatally wound you
 * anyway"):
 * <ul>
 * <li><b>Not early:</b> none come for a player who hasn't lived {@link #LIVED_FIRST} in this life; no masters in Hearth.</li>
 * <li><b>Introduced, then the challenge:</b> they walk up, give their name ({@link Introductions#learn}: their name shows
 * from then on), call the player out {@link #INTRO_GAP} later, and square up {@link #SQUARE_UP} before the first blow or
 * move. Their power's moves need a target, so nothing is thrown before that.</li>
 * <li><b>Softer by tier:</b> their blows on a player land at {@link #HARM} of their strength for the player's world tier, and
 * never take more than {@link #MOST} of the player's health in one (Hearth's own cap, {@link WorldTiers#hearthHit}, holds
 * for everything).</li>
 * <li><b>Persistent:</b> walked away from, or never answered ({@link #FIGHT_TICKS} without a blow struck back), they say
 * they'll be back and go, and the same one (their body kept, {@link #RIVALS}) comes again {@link #RETURN_TICKS} later,
 * and greets the player as one they've met.</li>
 * <li><b>Sleep:</b> a quarry who lies down is waited for outside ({@link #outside}); when they get up, the stranger says how
 * many hours they stood there, and squares up again.</li>
 * <li><b>Downed:</b> by their nature ({@link Temperaments}): the kind, the cheerful, the devout, the dutiful and the brave
 * get them up ({@link #TEND_TICKS} at their side), call it a good fight, and may come back for another
 * ({@link #REMATCH_CHANCE}); the spiteful, and the hotheaded half the time, finish them (Powers' execution); the rest
 * say they've won and walk off.</li>
 * </ul>
 */
public final class AwakenedStrangers {
	// ---- tuning ----
	/** How often each player is looked at (ticks), the chance a stranger comes, and the least time between two for one player. */
	static final int LOOK_EVERY = 1200;
	static final float CHANCE = 0.05F;
	static final long GAP = 24000L;
	/** How far off they arrive (blocks). */
	static final int ARRIVE_MIN = 22, ARRIVE_MAX = 34;
	/** The chance they're a master of it, and how much likelier each world tier. */
	static final float MASTER_CHANCE = 0.0F, MASTER_PER_TIER = 0.1F;
	/** How long a player must have lived in this life before any stranger comes for them (ticks: two days). */
	static final int LIVED_FIRST = 48000;
	/** From their name to the challenge, and from the challenge to the first blow or move (ticks). */
	static final int INTRO_GAP = 60, SQUARE_UP = 40;
	/**
	 * By the player's world tier (Hearth, Borderlands, Wildlands, Dread, Legend): the share of a stranger's harm that lands
	 * on a player, and the most of the player's full health one blow or move may take.
	 */
	static final float[] HARM = {0.35F, 0.6F, 0.8F, 1.0F, 1.0F};
	static final float[] MOST = {0.2F, 0.3F, 0.45F, 1.0F, 1.0F};
	/** How long a fight may go on (ticks), the share of health they yield at, and how long they take to go once done. */
	static final int FIGHT_TICKS = 6000, LEAVE_TICKS = 300;
	static final float YIELD_AT = 0.25F;
	/** Walked away from this far (blocks), they give it up for now. */
	static final double LOST_AT = 64.0;
	/** Ignored, they come back this long after (ticks: half a day); a kind one after a win, maybe, this long after (two days). */
	static final long RETURN_TICKS = 12000L, REMATCH_TICKS = 48000L;
	/** The chance a kind one comes back for another fight after getting their beaten quarry up. */
	static final float REMATCH_CHANCE = 0.5F;
	/** How long a kind one tends a beaten quarry before they're up (ticks), and how near is at their side. */
	static final int TEND_TICKS = 100;
	static final double TEND_REACH = 1.8;
	/** The chance a hotheaded one finishes a beaten quarry (a spiteful one always does). */
	static final float HOTHEAD_FINISH = 0.5F;
	/** Waiting out a sleeper: how far round the bed an open-sky spot is looked for (blocks), and how near they stand to it. */
	static final int OUTSIDE_REACH = 16;
	static final double OUTSIDE_NEAR = 6.0;
	/** A stranger with no attack of their own (a plain villager) hits for this much. */
	static final float FIST = 3.0F;
	/** Their reach, and the beat of their blows (ticks). */
	static final double REACH = 2.6;
	static final int BLOW_EVERY = 20;
	/** How near before they introduce themselves (blocks), and how many lines of each kind there are. */
	static final double CALL_RANGE = 14.0;
	static final int CALL_LINES = 4, YIELD_LINES = 3, WIN_LINES = 2, INTRO_LINES = 3, BACK_LINES = 3, IGNORED_LINES = 3,
		SLEEP_LINES = 2, WAITED_LINES = 3, GOOD_FIGHT_LINES = 3, FINISH_LINES = 2;
	/** On every stranger. */
	public static final String TAG = "jaymoskingdoms.awakened_stranger";

	/** A stranger to come back: their body as it left, and when (game time). */
	record Rival(CompoundTag body, long back) {
		static final Codec<Rival> CODEC = RecordCodecBuilder.create(i -> i.group(
			CompoundTag.CODEC.fieldOf("body").forGetter(Rival::body),
			Codec.LONG.fieldOf("back").forGetter(Rival::back)).apply(i, Rival::new));
	}

	/** On the Overworld: each player's stranger who'll be back, by the player's id. */
	static final AttachmentType<Map<String, Rival>> RIVALS = AttachmentRegistry.create(JaymosKingdoms.id("awakened_rivals"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Rival.CODEC)));

	private enum Stage {
		/** Walking up. */
		COMING,
		/** Named themselves; the challenge comes next. */
		INTRODUCED,
		/** Challenged; squaring up. */
		CALLED,
		FIGHTING,
		/** Waiting outside while the quarry sleeps. */
		WAITING,
		/** The quarry is down: getting them up, or finishing them. */
		TENDING,
		FINISHING,
		LEAVING
	}

	/** A stranger on the road: whom they've come for, whether they've met before, where they are in it, and since when. */
	private static final class Stranger {
		final UUID quarry;
		final boolean met;
		long since;
		Stage stage = Stage.COMING;
		long stageAt;
		/** The quarry's Overworld clock when they lay down, and where the stranger waits meanwhile. */
		long sleptAt = -1L;
		@Nullable BlockPos waitAt;
		/** Whether the quarry has struck them back, whether they've given their name, and whether an execution was set going. */
		boolean answered, introduced, ordered;

		Stranger(UUID quarry, long since, boolean met) {
			this.quarry = quarry;
			this.since = since;
			this.met = met;
		}

		void to(Stage next, long now) {
			this.stage = next;
			this.stageAt = now;
		}
	}

	private static final Map<UUID, Stranger> STRANGERS = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private AwakenedStrangers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(AwakenedStrangers::tick);
		// one left from before a restart (no longer followed) goes on their way
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (is(entity) && !STRANGERS.containsKey(entity.getUUID())) {
				entity.discard();
			}
		});
		// (one mid-meeting when the world closes comes back soon after it opens)
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel overworld = server.overworld();
			long now = overworld.getGameTime();
			for (Map.Entry<UUID, Stranger> e : STRANGERS.entrySet()) {
				Stranger s = e.getValue();
				if (s.stage != Stage.LEAVING && overworld.getEntity(e.getKey()) instanceof PathfinderMob mob && mob.isAlive()) {
					keep(server, mob, s.quarry, now + LOOK_EVERY);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STRANGERS.clear();
			LAST.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		for (ServerPlayer player : overworld.players()) {
			if ((now + player.getId() * 7L) % LOOK_EVERY != 0 || !player.isAlive() || player.isCreative() || player.isSpectator()
				|| PowersCompat.power(player) == null || Downed.isDowned(player) || player.isSleeping()
				|| Factions.at(server, overworld, player.blockPosition()) != null || coming(player)) {
				continue;
			}
			Rival rival = rivals(server).get(player.getUUID().toString());
			if (rival != null) {
				// the one they walked away from, back again
				if (now >= rival.back() && comeBack(overworld, player, rival)) {
					LAST.put(player.getUUID(), now);
				}
				continue;
			}
			Long last = LAST.get(player.getUUID());
			if (last != null && now - last < GAP || overworld.getRandom().nextFloat() >= CHANCE || PowersCompat.giftable().isEmpty()
				|| player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.TIME_SINCE_DEATH)) < LIVED_FIRST) {
				continue;
			}
			if (come(overworld, player)) {
				LAST.put(player.getUUID(), now);
			}
		}
		if (now % 10 == 0 && !STRANGERS.isEmpty()) {
			STRANGERS.entrySet().removeIf(e -> !step(overworld, e.getKey(), e.getValue(), now));
		}
	}

	/** Whether a stranger is out for {@code player} already. */
	private static boolean coming(ServerPlayer player) {
		for (Stranger s : STRANGERS.values()) {
			if (s.quarry.equals(player.getUUID()) && s.stage != Stage.LEAVING) {
				return true;
			}
		}
		return false;
	}

	/** Where a stranger may arrive for {@code player}: open ground {@link #ARRIVE_MIN} to {@link #ARRIVE_MAX} off; null if none. */
	private static @Nullable Vec3 arrival(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 8; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0, distance = Mth.nextInt(random, ARRIVE_MIN, ARRIVE_MAX);
			BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * distance, 0, player.getZ() + Math.sin(angle) * distance);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (level.getBlockState(top.below()).isSolid() && level.getFluidState(top).isEmpty() && Math.abs(top.getY() - player.getY()) < 16) {
				return Vec3.atBottomCenterOf(top);
			}
		}
		return null;
	}

	/** A stranger comes for {@code player}: true if one did. */
	static boolean come(ServerLevel level, ServerPlayer player) {
		RandomSource random = level.getRandom();
		Vec3 spot = arrival(level, player);
		if (spot == null) {
			return false;
		}
		Villager v = McaCompat.LOADED ? McaCompat.talePerson(level, spot, null, null, "", true) : McaCompat.visitor(level, spot, null, false);
		if (v == null) {
			return false;
		}
		List<String> powers = PowersCompat.giftable();
		String power = powers.get(random.nextInt(powers.size()));
		int tier = WorldTiers.of(player).ordinal();
		boolean master = random.nextFloat() < MASTER_CHANCE + MASTER_PER_TIER * tier;
		if (!PowersCompat.give(v, power, PowersCompat.BOLD, master, true)) {
			v.discard();
			return false;
		}
		v.addTag(TAG);
		v.addTag(GiftedFolk.ROLLED);
		v.setPersistenceRequired();
		Component title = Component.translatable("stranger.jaymoskingdoms.name", v.getName().copy(),
			Component.translatable(master ? "stranger.jaymoskingdoms.master" : "stranger.jaymoskingdoms.adept"),
			Component.translatable("power.jaymospowers." + power));
		v.setCustomName(title);
		v.setCustomNameVisible(true);
		STRANGERS.put(v.getUUID(), new Stranger(player.getUUID(), level.getGameTime(), false));
		return true;
	}

	/** The stranger {@code player} walked away from comes back, the same one: true if they did. */
	private static boolean comeBack(ServerLevel level, ServerPlayer player, Rival rival) {
		Vec3 spot = arrival(level, player);
		if (spot == null) {
			return false;
		}
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), rival.body()), level,
			EntitySpawnReason.LOAD, e -> {
				e.snapTo(spot.x, spot.y, spot.z, e.getYRot(), e.getXRot());
				return e;
			});
		if (!(loaded instanceof PathfinderMob mob)) {
			forget(level.getServer(), player.getUUID());
			return false;
		}
		if (level.getEntity(mob.getUUID()) != null) {
			return false;
		}
		// (followed before it's added, so the load doesn't send them off as a stray)
		STRANGERS.put(mob.getUUID(), new Stranger(player.getUUID(), level.getGameTime(), true));
		if (!level.addFreshEntity(mob)) {
			STRANGERS.remove(mob.getUUID());
			return false;
		}
		forget(level.getServer(), player.getUUID());
		return true;
	}

	/** One stranger's step (every 10 ticks): false once they're gone. */
	private static boolean step(ServerLevel level, UUID id, Stranger s, long now) {
		if (!(level.getEntity(id) instanceof PathfinderMob mob) || !mob.isAlive()) {
			return false;
		}
		if (s.stage == Stage.LEAVING) {
			if (now - s.stageAt >= LEAVE_TICKS) {
				mob.discard();
				return false;
			}
			return true;
		}
		ServerPlayer quarry = level.getServer().getPlayerList().getPlayer(s.quarry);
		RandomSource random = level.getRandom();
		if (quarry != null && mob.getLastHurtByMob() == quarry) {
			s.answered = true;
		}
		if (s.stage == Stage.TENDING || s.stage == Stage.FINISHING) {
			return beaten(level, mob, s, quarry, now);
		}
		if (mob.getHealth() <= mob.getMaxHealth() * YIELD_AT) {
			if (quarry != null) {
				Speech.sayTo(level, mob, quarry, line("yield", YIELD_LINES, random), Speech.GOLD);
			}
			leave(mob, s, now);
			return true;
		}
		if (quarry != null && !quarry.isAlive()) {
			leave(mob, s, now);
			return true;
		}
		if (quarry == null || quarry.level() != level || quarry.distanceTo(mob) > LOST_AT) {
			// walked away from (or gone): they'll be back
			if (quarry != null && s.stage != Stage.COMING) {
				Speech.say(level, mob, line("ignored", IGNORED_LINES, random), Speech.GOLD, 24.0);
			}
			keep(level.getServer(), mob, s.quarry, now + RETURN_TICKS);
			leave(mob, s, now);
			return true;
		}
		if (Downed.isDowned(quarry)) {
			if (s.stage == Stage.FIGHTING) {
				won(level, mob, s, quarry, now);
			} else {
				// (downed by something else before it came to blows: they stand by)
				hold(mob, quarry, false);
			}
			return true;
		}
		if (quarry.isSleeping()) {
			waitOut(level, mob, s, quarry, now);
			return true;
		}
		if (s.stage == Stage.WAITING) {
			// up again: how long they stood out there, then back to it
			long hours = Math.max(1L, Math.round((level.getOverworldClockTime() - s.sleptAt) / 1000.0));
			Speech.sayTo(level, mob, quarry, line("waited", WAITED_LINES, random, hours), Speech.GOLD);
			s.sleptAt = -1L;
			s.waitAt = null;
			s.since = now;
			s.to(s.introduced ? Stage.CALLED : Stage.COMING, now);
			return true;
		}
		if (s.stage == Stage.FIGHTING && now - s.since > FIGHT_TICKS) {
			if (s.answered) {
				leave(mob, s, now);
			} else {
				// never answered: they'll try again
				Speech.sayTo(level, mob, quarry, line("ignored", IGNORED_LINES, random), Speech.GOLD);
				keep(level.getServer(), mob, s.quarry, now + RETURN_TICKS);
				leave(mob, s, now);
			}
			return true;
		}
		switch (s.stage) {
			case COMING -> {
				if (mob.distanceTo(quarry) <= CALL_RANGE) {
					// their name first (from then on it shows over them), or, met before, a word on that
					Introductions.learn(quarry, mob);
					s.introduced = true;
					Speech.sayTo(level, mob, quarry, s.met ? line("back", BACK_LINES, random) : line("intro", INTRO_LINES, random, mob.getName()),
						Speech.GOLD);
					s.to(Stage.INTRODUCED, now);
				}
				hold(mob, quarry, s.stage == Stage.COMING);
				return true;
			}
			case INTRODUCED -> {
				if (now - s.stageAt >= INTRO_GAP) {
					Speech.sayTo(level, mob, quarry, line("call", CALL_LINES, random), Speech.GOLD);
					s.to(Stage.CALLED, now);
				}
				hold(mob, quarry, false);
				return true;
			}
			case CALLED -> {
				if (now - s.stageAt >= SQUARE_UP) {
					s.since = now;
					s.to(Stage.FIGHTING, now);
				}
				hold(mob, quarry, false);
				return true;
			}
			default -> {
			}
		}
		fight(level, mob, quarry);
		return true;
	}

	/** Not fighting yet: no target (so their power throws nothing), looking at the quarry, walking up or standing. */
	private static void hold(PathfinderMob mob, ServerPlayer quarry, boolean walk) {
		calm(mob);
		mob.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
		if (walk) {
			mob.getNavigation().moveTo(quarry, 0.9);
		} else {
			mob.getNavigation().stop();
		}
	}

	/** Set on them: their power's moves go at their target (Powers' gifted folk), and they close in for blows. */
	private static void fight(ServerLevel level, PathfinderMob mob, ServerPlayer quarry) {
		mob.setTarget(quarry);
		if (mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED)) {
			mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, quarry);
		}
		mob.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
		if (mob.distanceTo(quarry) > REACH) {
			mob.getNavigation().moveTo(quarry, 1.1);
		} else if (mob.tickCount % BLOW_EVERY < 10) {
			mob.getNavigation().stop();
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			if (mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null) {
				mob.doHurtTarget(level, quarry);
			} else {
				// a plain villager (no MCA) has no attack of its own
				quarry.hurtServer(level, level.damageSources().mobAttack(mob), FIST);
			}
		}
	}

	/** The quarry lay down: the stranger waits for them outside, where the sky is open. */
	private static void waitOut(ServerLevel level, PathfinderMob mob, Stranger s, ServerPlayer quarry, long now) {
		calm(mob);
		if (s.stage != Stage.WAITING) {
			s.sleptAt = level.getOverworldClockTime();
			s.waitAt = outside(level, quarry.blockPosition());
			if (s.stage != Stage.COMING) {
				Speech.sayTo(level, mob, quarry, line("sleep", SLEEP_LINES, level.getRandom()), Speech.GOLD);
			}
			s.to(Stage.WAITING, now);
		}
		BlockPos at = s.waitAt;
		if (at != null && mob.blockPosition().distSqr(at) > 2.0) {
			mob.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.8);
		} else {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(quarry, 30.0F, 30.0F);
		}
	}

	/**
	 * Where to wait for a sleeper at {@code bed}: the nearest open-sky ground round it, at least {@link #OUTSIDE_NEAR} off
	 * if the bed itself is under the sky; null if there's none (they stand where they are).
	 */
	static @Nullable BlockPos outside(ServerLevel level, BlockPos bed) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -OUTSIDE_REACH; dx <= OUTSIDE_REACH; dx++) {
			for (int dz = -OUTSIDE_REACH; dz <= OUTSIDE_REACH; dz++) {
				double d = dx * dx + dz * dz;
				if (d >= bestDistance || d < OUTSIDE_NEAR * OUTSIDE_NEAR || d > OUTSIDE_REACH * OUTSIDE_REACH) {
					continue;
				}
				BlockPos column = bed.offset(dx, 0, dz);
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
				if (Math.abs(top.getY() - bed.getY()) <= 4 && level.getBlockState(top.below()).isSolid() && level.getFluidState(top).isEmpty()
					&& level.canSeeSky(top)) {
					best = top;
					bestDistance = d;
				}
			}
		}
		return best;
	}

	/** The quarry went down: by the stranger's nature, they get them up, finish them, or say they've won and go. */
	private static void won(ServerLevel level, PathfinderMob mob, Stranger s, ServerPlayer quarry, long now) {
		calm(mob);
		RandomSource random = level.getRandom();
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t == Temperaments.Temperament.SPITEFUL || t == Temperaments.Temperament.HOTHEADED && random.nextFloat() < HOTHEAD_FINISH) {
			Speech.sayTo(level, mob, quarry, line("finish", FINISH_LINES, random), Speech.RED);
			s.to(Stage.FINISHING, now);
			return;
		}
		if (t == Temperaments.Temperament.KINDHEARTED || t == Temperaments.Temperament.CHEERFUL || t == Temperaments.Temperament.DEVOUT
			|| t == Temperaments.Temperament.DUTIFUL || t == Temperaments.Temperament.BRAVE) {
			s.to(Stage.TENDING, now);
			return;
		}
		Speech.say(level, mob, line("won", WIN_LINES, random), Speech.GOLD, 24.0);
		leave(mob, s, now);
	}

	/** Seeing to a beaten quarry: getting them up (a kind one), or finishing them (Powers' execution). */
	private static boolean beaten(ServerLevel level, PathfinderMob mob, Stranger s, @Nullable ServerPlayer quarry, long now) {
		if (quarry == null || !quarry.isAlive() || quarry.level() != level || !Downed.isDowned(quarry) || PowersCompat.isCarried(quarry)) {
			// up already, or carried off by someone else: nothing more for them here
			mob.setShiftKeyDown(false);
			leave(mob, s, now);
			return true;
		}
		if (s.stage == Stage.FINISHING) {
			if (!s.ordered) {
				s.ordered = PowersCompat.orderExecution(mob, quarry);
				if (!s.ordered && now - s.stageAt > 200) {
					// (it can't be done: they leave them lying)
					leave(mob, s, now);
				}
			}
			// (once it's going, Powers walks them over and does it; they go when it's over)
			return true;
		}
		if (mob.distanceTo(quarry) > TEND_REACH) {
			mob.getNavigation().moveTo(quarry, 0.9);
			s.stageAt = now;
			return true;
		}
		// at their side: kneeling, seeing to them
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(quarry.getX(), quarry.getY() + 0.2, quarry.getZ());
		mob.setShiftKeyDown(true);
		mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.HEART, quarry.getX(), quarry.getY() + 0.6, quarry.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		if (now - s.stageAt < TEND_TICKS) {
			return true;
		}
		// (Powers gets them up on their next tick, bandaged)
		quarry.addTag(Downed.REVIVE_TAG);
		quarry.addTag(Downed.BANDAGED_TAG);
		mob.setShiftKeyDown(false);
		Speech.sayTo(level, mob, quarry, line("good_fight", GOOD_FIGHT_LINES, level.getRandom()), Speech.GOLD);
		if (level.getRandom().nextFloat() < REMATCH_CHANCE) {
			keep(level.getServer(), mob, s.quarry, now + REMATCH_TICKS);
		}
		leave(mob, s, now);
		return true;
	}

	/** No target, no fight. */
	private static void calm(PathfinderMob mob) {
		mob.setTarget(null);
		mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
	}

	/** Done (beaten, gone, ignored or won): the stranger stops fighting and walks off, gone a while later. */
	private static void leave(PathfinderMob mob, Stranger s, long now) {
		s.to(Stage.LEAVING, now);
		calm(mob);
		mob.setShiftKeyDown(false);
		mob.getNavigation().stop();
		Vec3 away = mob.position().add((mob.getRandom().nextDouble() - 0.5) * 60.0, 0.0, (mob.getRandom().nextDouble() - 0.5) * 60.0);
		mob.getNavigation().moveTo(away.x, away.y, away.z, 0.8);
	}

	/** {@code mob} will come back for {@code quarry} at {@code back}: their body kept as it is. */
	private static void keep(MinecraftServer server, PathfinderMob mob, UUID quarry, long back) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!mob.saveAsPassenger(out)) {
			return;
		}
		Map<String, Rival> all = new HashMap<>(rivals(server));
		all.put(quarry.toString(), new Rival(out.buildResult(), back));
		Factions.level(server).setAttached(RIVALS, Map.copyOf(all));
	}

	private static void forget(MinecraftServer server, UUID quarry) {
		Map<String, Rival> all = new HashMap<>(rivals(server));
		if (all.remove(quarry.toString()) != null) {
			Factions.level(server).setAttached(RIVALS, Map.copyOf(all));
		}
	}

	private static Map<String, Rival> rivals(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(RIVALS, Map.of());
	}

	/** One of a kind of their lines, at random. */
	private static Component line(String kind, int count, RandomSource random, Object... args) {
		return Component.translatable("speech.jaymoskingdoms.stranger." + kind + "." + random.nextInt(count), args);
	}

	/**
	 * A stranger's blow or move on a player, from the hurt chain ({@code LivingEntityHurtMixin}, last): {@link #HARM} of it
	 * for the player's world tier, and never more than {@link #MOST} of their full health.
	 */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		// (not on the downed: a finishing blow is a finishing blow)
		if (amount <= 0.0F || !(target instanceof Player player) || !is(source.getEntity()) || Downed.isDowned(player)) {
			return amount;
		}
		int tier = Math.clamp(WorldTiers.of(player).ordinal(), 0, HARM.length - 1);
		return Math.min(amount * HARM[tier], player.getMaxHealth() * MOST[tier]);
	}

	/** Whether {@code mob} is a stranger set on finishing {@code body}, their beaten quarry ({@link Downed#holdsFire} lets it land). */
	static boolean finishing(Entity mob, LivingEntity body) {
		Stranger s = STRANGERS.get(mob.getUUID());
		return s != null && s.stage == Stage.FINISHING && s.quarry.equals(body.getUUID());
	}

	/** Whether {@code mob} is an awakened stranger. */
	public static boolean is(net.minecraft.world.entity.@Nullable Entity mob) {
		return mob != null && mob.entityTags().contains(TAG);
	}
}
