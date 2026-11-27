package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.nether.FortressKnight;
import com.jaymoskingdoms.remnants.AetherGiant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Easter-egg quips (Andy, 2026-11-27): rare lines in the voice of a knight out of an old tale, all thee and thou, from
 * the realms' knights and guards, townsfolk, hermits and a few old foes. Andy loves the Spyder-Knight of the cartoon
 * Ultimate Spider-Man, so a handful of lines nod to his (short ones only); the rest are our own in his voice, and no
 * name from the show is ever said.
 * <p>
 * Each line is a moment ({@link #ODDS}: 1 in that many times the moment comes, it's said), a player hears any one line
 * at most once a day ({@link #DAY}) and any quip at most every {@link #PLAYER_GAP}, and a speaker quips at most every
 * {@link #SPEAKER_GAP}. Words over the speaker's head ({@link Speech}), never chat, in the speaker's tongue.
 * <p>
 * The moments are looked for round each player every {@link #SWEEP} ticks ({@link #sweep}), on blows and deaths
 * ({@link #struck}, {@link #died}), when a spell is cast ({@link #cast}) and when a talk is left ({@link #leftTalk}).
 * Lines: {@code quips.jaymoskingdoms.<id>.<n>} in lang part {@code quips.json}.
 */
public final class Quips {
	/** One in this many times its moment comes, a line is said; lines with more than one wording ({@link #VARIANTS}). */
	static final Map<String, Integer> ODDS = new HashMap<>(), VARIANTS = new HashMap<>();

	static {
		// knights and guards
		odds("town_fool", 40);
		odds("have_at_thee", 15);
		odds("what_monster", 8);
		odds("trickery", 20);
		odds("cleave", 4);
		odds("unarmoured", 30);
		odds("worse_shot", 6);
		odds("look_where", 10);
		odds("fare_well", 5);
		odds("hold_stranger", 25);
		odds("squire_or_fool", 30);
		odds("pain", 6);
		odds("fight_beside", 10);
		odds("all_medieval", 3);
		odds("web", 6);
		odds("justice", 15);
		odds("horse_wit", 8);
		odds("speak_plain", 6);
		odds("goblin_smell", 10);
		odds("crowd", 5);
		// magic and monsters
		odds("witch", 12);
		odds("science", 6);
		odds("thing_on_wall", 15);
		odds("eight_legs", 10);
		odds("beyond_reckoning", 3);
		odds("back_lest", 1);
		odds("alas", 6);
		// townsfolk
		odds("touched", 4);
		odds("upon_us", 2);
		odds("face_fears", 2);
		odds("stranger_day", 10);
		odds("sorcery", 4);
		odds("ceiling_torch", 10);
		VARIANTS.put("ceiling_torch", 2);
		odds("still_hero", 3);
		odds("my_tone", 8);
		// idle talk, rolled every sweep a player is near
		odds("mirror", 900);
		odds("no_fool", 900);
		odds("robes", 60);
	}

	private static void odds(String id, int oneIn) {
		ODDS.put(id, oneIn);
	}

	/** A day of game time (ticks): no line twice to one player inside it. */
	static final long DAY = 24000L;
	/** Ticks between any two quips one player hears; between two from one speaker. */
	static final long PLAYER_GAP = 600L, SPEAKER_GAP = 400L;
	/** How often the moments round each player are looked for (ticks), and how far round (blocks). */
	static final int SWEEP = 20;
	static final double RANGE = 16.0, GREAT_RANGE = 32.0;
	/** How far a quip said aloud is seen (blocks). */
	static final double HEARD = 16.0;
	/** Health at or over which anything is a great foe, whatever it is. */
	static final float GREAT_HEALTH = 200.0F;
	/** Misses in a row that earn the worse-shot line; blocks moved in one sweep that count as a teleport. */
	static final int MISSES = 3;
	static final double JUMP = 10.0;
	/** Leaving a talk with one guard this many times inside {@link #TALK_WINDOW} ticks earns speak-plain. */
	static final int LEAVES = 2;
	static final long TALK_WINDOW = 1200L;

	/** Per player: when they last heard each line, and any line. */
	private static final Map<UUID, Map<String, Long>> HEARD_AT = new HashMap<>();
	private static final Map<UUID, Long> PLAYER_LAST = new HashMap<>();
	/** Per speaker: when they last quipped. */
	private static final Map<UUID, Long> SPEAKER_LAST = new HashMap<>();
	/** What was seen at a player's last sweep. */
	private static final Map<UUID, Seen> SEEN = new HashMap<>();
	/** Whether a fighter had a foe at the last sweep (and who). */
	private static final Map<Mob, LivingEntity> FOE = new WeakHashMap<>();
	/** Arrows already counted as a hit or a miss. */
	private static final Map<Entity, Boolean> COUNTED = new WeakHashMap<>();
	/** Per player: misses in a row with the bow. */
	private static final Map<UUID, Integer> MISSED = new HashMap<>();
	/** Per player: the guard they last walked off from, how often, since when. */
	private static final Map<UUID, Leaving> LEFT = new HashMap<>();

	private record Seen(Vec3 at, ServerLevel level, boolean down) {
	}

	private record Leaving(UUID guard, int times, long since) {
	}

	private Quips() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SWEEP == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					sweep(server, p);
				}
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Quips::struck);
		ServerLivingEntityEvents.AFTER_DEATH.register(Quips::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HEARD_AT.clear();
			PLAYER_LAST.clear();
			SPEAKER_LAST.clear();
			SEEN.clear();
			FOE.clear();
			COUNTED.clear();
			MISSED.clear();
			LEFT.clear();
		});
	}

	// ---- who's who -----------------------------------------------------------------------------------------------------

	/** One of a realm's soldiers or MCA's guards. */
	static boolean guard(Entity e) {
		return e instanceof PathfinderMob m && m.isAlive() && (Ranks.of(m) >= 0 || McaCompat.isGuard(m));
	}

	/** A soldier of knight's rank or over (a veteran, a leader), archers too. */
	static boolean knight(Entity e) {
		if (!(e instanceof LivingEntity l) || !l.isAlive()) {
			return false;
		}
		int rank = Ranks.of(l);
		return rank >= 0 && Ranks.step(rank) >= Ranks.KNIGHT;
	}

	/** Townsfolk who aren't soldiers. */
	static boolean townsfolk(Entity e) {
		return e.isAlive() && (e instanceof Villager || McaCompat.isMca(e)) && !guard(e);
	}

	/** A foe of the songs: an Aether Giant, a monster of legend, or anything as hale as {@link #GREAT_HEALTH}. */
	static boolean great(Entity e) {
		return e instanceof AetherGiant || e instanceof LivingEntity l && (Monsters.legend(e.getType()) || l.getMaxHealth() >= GREAT_HEALTH)
			&& !(e instanceof ServerPlayer);
	}

	private static boolean bare(ServerPlayer p) {
		return worn(p) == 0;
	}

	private static int worn(ServerPlayer p) {
		int n = 0;
		for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!p.getItemBySlot(s).isEmpty()) {
				n++;
			}
		}
		return n;
	}

	private static boolean armed(ServerPlayer p) {
		return p.getMainHandItem().has(DataComponents.WEAPON);
	}

	// ---- saying it -----------------------------------------------------------------------------------------------------

	/** Whether {@code player} may hear line {@code id} now (the dice not rolled yet). */
	private static boolean due(ServerPlayer player, String id, long now) {
		Long last = PLAYER_LAST.get(player.getUUID());
		if (last != null && now - last < PLAYER_GAP) {
			return false;
		}
		Long heard = HEARD_AT.getOrDefault(player.getUUID(), Map.of()).get(id);
		return heard == null || now - heard >= DAY;
	}

	private static boolean free(Entity speaker, long now) {
		Long last = SPEAKER_LAST.get(speaker.getUUID());
		return (last == null || now - last >= SPEAKER_GAP) && !(speaker instanceof PathfinderMob m && McaCompat.busy(m));
	}

	private static boolean roll(Entity speaker, String id) {
		Integer odds = ODDS.get(id);
		return odds != null && (odds <= 1 || speaker.getRandom().nextInt(odds) == 0);
	}

	private static Component words(Entity speaker, String id) {
		int n = VARIANTS.getOrDefault(id, 1);
		return Component.translatable("quips.jaymoskingdoms." + id + "." + (n > 1 ? speaker.getRandom().nextInt(n) : 0));
	}

	private static void heard(ServerPlayer player, String id, long now) {
		HEARD_AT.computeIfAbsent(player.getUUID(), u -> new HashMap<>()).put(id, now);
		PLAYER_LAST.put(player.getUUID(), now);
	}

	/** {@code speaker} says line {@code id} to {@code player} (everyone near sees it). True if it was said. */
	static boolean quip(Entity speaker, ServerPlayer player, String id) {
		return quip(speaker, player, id, false);
	}

	/** As {@link #quip(Entity, ServerPlayer, String)}; {@code plainly}: in words anyone follows, whatever their tongue. */
	static boolean quip(Entity speaker, ServerPlayer player, String id, boolean plainly) {
		if (!(speaker.level() instanceof ServerLevel level) || player.isSpectator() || !speaker.isAlive()) {
			return false;
		}
		long now = level.getGameTime();
		if (!due(player, id, now) || !free(speaker, now) || !roll(speaker, id)) {
			return false;
		}
		Component words = words(speaker, id);
		if (plainly) {
			Speech.sayPlainly(level, speaker, player, words, Speech.GOLD);
		} else {
			Speech.sayTo(level, speaker, player, words, Speech.GOLD);
		}
		heard(player, id, now);
		SPEAKER_LAST.put(speaker.getUUID(), now);
		return true;
	}

	/** {@code speaker} says line {@code id} aloud, to every player near who hasn't heard it today. */
	static boolean aloud(Entity speaker, String id) {
		if (!(speaker.level() instanceof ServerLevel level) || !speaker.isAlive()) {
			return false;
		}
		long now = level.getGameTime();
		List<ServerPlayer> near = level.getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(HEARD),
			p -> !p.isSpectator() && due(p, id, now));
		if (near.isEmpty() || !free(speaker, now) || !roll(speaker, id)) {
			return false;
		}
		Speech.say(level, speaker, words(speaker, id), Speech.GOLD, HEARD);
		for (ServerPlayer p : near) {
			heard(p, id, now);
		}
		SPEAKER_LAST.put(speaker.getUUID(), now);
		return true;
	}

	// ---- the sweep -----------------------------------------------------------------------------------------------------

	private static void sweep(MinecraftServer server, ServerPlayer player) {
		if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		UUID id = player.getUUID();
		boolean down = PowersCompat.downedState(player) > 0;
		Seen before = SEEN.put(id, new Seen(player.position(), level, down));
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(GREAT_RANGE),
			e -> e != player && e.isAlive() && !(e instanceof ServerPlayer));
		boolean onWall = PowersCompat.onWall(player);
		boolean flying = !player.isCreative() && (player.getAbilities().flying || player.isFallFlying());
		// further in one sweep than anyone runs or rides: a pearl, a blink, a teleport
		boolean jumped = before != null && before.level == level && !player.isPassenger() && !flying && !player.isFallFlying()
			&& before.at.distanceToSqr(player.position()) > JUMP * JUMP;
		boolean rose = before != null && before.down && !down;
		int worn = worn(player);
		int villagers = 0;
		LivingEntity greatFoe = null;
		for (LivingEntity e : near) {
			if (townsfolk(e)) {
				villagers++;
			} else if (greatFoe == null && great(e)) {
				greatFoe = e;
			}
		}
		arrows(level, player, near);
		for (LivingEntity e : near) {
			double d = e.distanceTo(player);
			if (d > RANGE && !(townsfolk(e) && greatFoe != null)) {
				continue;
			}
			boolean sees = e instanceof Mob m ? m.getSensing().hasLineOfSight(player) : e.hasLineOfSight(player);
			if (guard(e)) {
				Mob g = (Mob) e;
				LivingEntity foe = g.getTarget();
				LivingEntity had = FOE.put(g, foe);
				boolean knight = knight(g);
				if (foe != null && had == null) {
					// a fight begins
					if (foe == player) {
						quip(g, player, "pain");
					} else if (knight) {
						aloud(g, "have_at_thee");
					}
				}
				if (foe == null && had != null && knight && worn == 0 && d < 10) {
					quip(g, player, "fare_well");
				}
				if (foe != null && foe != player) {
					if (knight && foe instanceof Monster && villagers >= 3 && d < 10 && player.getLastHurtByMob() != foe) {
						quip(g, player, "fight_beside");
					}
					if (fleeing(near, foe) >= 2) {
						quip(g, player, "crowd");
					}
				}
				if (greatFoe != null && knight && d < 10) {
					quip(g, player, "cleave");
				}
				if (!sees) {
					continue;
				}
				if (onWall) {
					quip(g, player, "what_monster");
				}
				if (player.isSprinting() && d < 1.6) {
					quip(g, player, "look_where");
				}
				if (Disguise.wearsDisguise(player) && d < 8) {
					quip(g, player, "goblin_smell");
				}
				if (foe == null && d < 6) {
					boolean known = Introductions.knows(player, g);
					if (knight && armed(player) && !known && d < 5) {
						quip(g, player, "town_fool");
					} else if (!known && d < 4) {
						quip(g, player, "hold_stranger");
					} else if (worn == 0) {
						quip(g, player, "unarmoured");
					} else if (knight && worn < 4) {
						quip(g, player, "squire_or_fool");
					}
				}
				if (knight && g.getVehicle() instanceof AbstractHorse horse && horse.distanceTo(player) < 1.6) {
					quip(g, player, "horse_wit");
				}
				for (LivingEntity s : near) {
					if (s instanceof Spider spider && spider.getTarget() == player && spider.distanceTo(player) < 10) {
						quip(g, player, "web");
						break;
					}
				}
			} else if (townsfolk(e)) {
				if (greatFoe != null && greatFoe.distanceTo(e) < 24) {
					quip(e, player, "upon_us");
				}
				if (d > RANGE) {
					continue;
				}
				if (rose && d < 4) {
					quip(e, player, "still_hero");
				}
				if (sees && onWall) {
					quip(e, player, "thing_on_wall");
				}
				if (sees && flying) {
					quip(e, player, "stranger_day");
				}
				if (jumped && sees) {
					quip(e, player, "sorcery");
				}
				if (!Languages.understands(player, e) && monsterNear(near, e)) {
					quip(e, player, "my_tone", true);
				}
				if (d < 6 && lampOver(level, e.blockPosition())) {
					quip(e, player, "ceiling_torch");
				}
				if (d < 8 && Hermits.is(e)) {
					quip(e, player, "robes");
				} else if (d < 8) {
					quip(e, player, e.getRandom().nextBoolean() ? "mirror" : "no_fool");
				}
			} else if (FortressKnight.is(e) && d < 12 && sees) {
				quip(e, player, "back_lest");
			}
		}
	}

	/** Townsfolk in {@code near} running from {@code foe}. */
	private static int fleeing(List<LivingEntity> near, LivingEntity foe) {
		int n = 0;
		for (LivingEntity e : near) {
			if (townsfolk(e) && e.distanceTo(foe) < 16) {
				Vec3 move = e.getDeltaMovement();
				Vec3 away = e.position().subtract(foe.position());
				if (move.horizontalDistanceSqr() > 0.01 && move.x * away.x + move.z * away.z > 0) {
					n++;
				}
			}
		}
		return n;
	}

	/** A monster near {@code e} hunting someone. */
	private static boolean monsterNear(List<LivingEntity> near, LivingEntity e) {
		for (LivingEntity m : near) {
			if (m instanceof Monster monster && monster.getTarget() != null && monster.distanceTo(e) < 12) {
				return true;
			}
		}
		return false;
	}

	/** A lit redstone lamp or copper bulb just over the head at {@code at}: a light with no flame. */
	private static boolean lampOver(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 2, -1), at.offset(1, 4, 1))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof RedstoneLampBlock && s.getValue(RedstoneLampBlock.LIT)
				|| s.getBlock() instanceof CopperBulbBlock && s.getValue(CopperBulbBlock.LIT)) {
				return true;
			}
		}
		return false;
	}

	/** The player's arrows come to rest without hitting anything: misses in a row; enough, and a guard who saw says so. */
	private static void arrows(ServerLevel level, ServerPlayer player, List<LivingEntity> near) {
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(GREAT_RANGE),
			a -> a.getOwner() == player && !COUNTED.containsKey(a) && a.tickCount > 2 && a.tickCount < 200 && a.getDeltaMovement().lengthSqr() < 1.0E-4)) {
			COUNTED.put(arrow, Boolean.TRUE);
			int missed = MISSED.merge(player.getUUID(), 1, Integer::sum);
			if (missed >= MISSES) {
				for (LivingEntity e : near) {
					if (guard(e) && e.distanceTo(player) < RANGE && ((Mob) e).getSensing().hasLineOfSight(player)) {
						if (quip(e, player, "worse_shot")) {
							MISSED.put(player.getUUID(), 0);
						}
						break;
					}
				}
			}
		}
	}

	// ---- blows and deaths ----------------------------------------------------------------------------------------------

	private static void struck(LivingEntity hit, DamageSource source, float base, float taken, boolean blocked) {
		Entity by = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow arrow && by instanceof ServerPlayer shooter) {
			// a hit: the misses start again
			COUNTED.put(arrow, Boolean.TRUE);
			MISSED.put(shooter.getUUID(), 0);
		}
		if (by instanceof ServerPlayer player) {
			if (knight(hit) && (blocked || taken < base * 0.5F)) {
				quip(hit, player, "trickery");
			}
			if (great(hit)) {
				for (LivingEntity e : hit.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), Quips::townsfolk)) {
					if (quip(e, player, "touched")) {
						break;
					}
				}
			}
		}
		if (direct instanceof AbstractArrow && guard(by) && hit != by) {
			Vec3 away = hit.position().subtract(by.position());
			Vec3 move = hit.getDeltaMovement();
			if (move.x * away.x + move.z * away.z > 0.0 && move.horizontalDistanceSqr() > 0.01) {
				aloud(by, "justice");
			}
		}
		float now = hit.getHealth(), max = hit.getMaxHealth();
		float was = now + taken;
		if (great(hit) && was > max * 0.5F && now <= max * 0.5F && now > 0) {
			for (LivingEntity e : hit.level().getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(GREAT_RANGE), Quips::guard)) {
				if (aloud(e, "beyond_reckoning")) {
					break;
				}
			}
		}
		if (oldFoe(hit) && was > max * 0.25F && now <= max * 0.25F && now > 0) {
			aloud(hit, "alas");
		}
	}

	/** The headless rider, a zombie knight, a fortress knight: the old dead who think they still fight for something. */
	private static boolean oldFoe(Entity e) {
		return e.entityTags().contains(HeadlessRider.TAG) || e instanceof com.jaymoskingdoms.barrow.ZombieKnight || FortressKnight.is(e);
	}

	private static void died(LivingEntity dead, DamageSource source) {
		Entity by = source.getEntity();
		if (dead instanceof Spider && guard(by)) {
			aloud(by, "eight_legs");
		}
		if (!great(dead)) {
			return;
		}
		List<LivingEntity> near = dead.level().getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(GREAT_RANGE),
			e -> e.isAlive() && e != dead);
		for (LivingEntity e : near) {
			if (knight(e) && aloud(e, "all_medieval")) {
				break;
			}
		}
		if (by instanceof ServerPlayer player) {
			int folk = 0;
			LivingEntity first = null;
			for (LivingEntity e : near) {
				if (townsfolk(e)) {
					folk++;
					if (first == null || e.distanceTo(player) < first.distanceTo(player)) {
						first = e;
					}
				}
			}
			if (folk >= 3 && first != null) {
				quip(first, player, "face_fears");
			}
		}
	}

	// ---- from elsewhere ------------------------------------------------------------------------------------------------

	/** (From {@link Arcana#cast}) {@code player} cast a spell: a guard who saw may wonder if they're a witch, a Frostborn scoffs. */
	static void cast(ServerLevel level, @Nullable Entity caster) {
		if (!(caster instanceof ServerPlayer player)) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(12.0), e -> e.isAlive() && e != player)) {
			if (!(e instanceof Mob m) || !m.getSensing().hasLineOfSight(player)) {
				continue;
			}
			if (guard(e) ? quip(e, player, "witch") : townsfolk(e) && FrostTech.raisedHere(e) && quip(e, player, "science")) {
				return;
			}
		}
	}

	/**
	 * (From {@link Conversations#goodbye}) {@code player} took their leave of {@code villager}: a guard left twice in a
	 * short while bids them speak plain. True if he did (the plain goodbye is then left unsaid).
	 */
	static boolean leftTalk(ServerPlayer player, Entity villager) {
		if (!guard(villager)) {
			return false;
		}
		long now = player.level().getGameTime();
		Leaving was = LEFT.get(player.getUUID());
		int times = was != null && was.guard.equals(villager.getUUID()) && now - was.since < TALK_WINDOW ? was.times + 1 : 1;
		LEFT.put(player.getUUID(), new Leaving(villager.getUUID(), times, times == 1 ? now : was.since));
		if (times >= LEAVES && quip(villager, player, "speak_plain")) {
			LEFT.remove(player.getUUID());
			return true;
		}
		return false;
	}
}
