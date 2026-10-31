package com.jaymoskingdoms;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.449) Andy: "you should be able to ask villagers where certain things in town are in the talk option, such as if
 * you need a doctor, or a butcher"; "move some of these options to the interact menu". MCA's Interact page has Ask the Way,
 * which opens a page of places (client {@code McaScreensMixin}/{@code McaWayPageMixin}; each sends {@link #COMMAND}):
 * a healer (a cleric), a butcher, a smith (armourer, weaponsmith or toolsmith), a tailor (a grown shepherd), the tavern,
 * the stables, and whoever rules here. The villager finds the nearest in their town and:
 * <ul>
 * <li>turns to face it and tells you who and where ("about 40 paces to the north-east"), and if the one you want is abed;</li>
 * <li>or, if they think well of you ({@link #GUIDE_HEARTS}) or they're a child, and it isn't far ({@link #GUIDE_RANGE}),
 * walks you there, waiting when you fall behind, and says so when you've arrived. A town's guards and office-holders
 * keep their posts and only point.</li>
 * </ul>
 * One who bears you real ill will ({@link #REFUSE_HEARTS}) tells you to find it yourself. Asking needs a shared tongue
 * (a foreigner gets a shrug, {@link Languages}).
 */
public final class Directions {
	/** MCA's command from a place on Interact's Ask the Way page ({@code way.<place>}). */
	public static final String COMMAND = "way.";

	/** How far from the one you ask they know the town's folk and buildings (blocks). */
	static final double FIND_RANGE = 128.0;
	/** Their hearts with you at which they'll walk you over, and below which they won't help at all. */
	static final int GUIDE_HEARTS = 10, REFUSE_HEARTS = -15;
	/** The farthest they'll walk you (blocks); nearer than this they just point at it. */
	static final double GUIDE_RANGE = 96.0, POINT_ONLY = 10.0;
	/** They wait once you're this far behind, give up once you're this far, and you've arrived this close. */
	static final double WAIT_RANGE = 7.0, LOSE_RANGE = 32.0, ARRIVE_RANGE = 4.0;
	/** Walking speed (walk-target speed), and how often the walk target is set again (ticks). */
	static final float LEAD_SPEED = 0.55F;
	static final int RETARGET_TICKS = 10;
	/** They wait this long for you to close the menu before setting off; the longest walk; how often they call you on (ticks). */
	static final int START_TICKS = 30, LEAD_TICKS = 20 * 120, HURRY_TICKS = 20 * 6;
	/** No closer to it for this long and they point you the rest of the way (ticks). */
	static final int STUCK_TICKS = 20 * 12;
	/** How long they stand facing it when they point (ticks). */
	static final int POINT_TICKS = 50;
	/** Distances in their words: a stone's throw under this, a long walk over that (blocks); paces are rounded to this. */
	static final double NEAR = 14.0, FAR = 160.0;
	static final int ROUND_TO = 5;
	/** Lines per kind. */
	static final int LINES = 3;

	/** What can be asked after. */
	enum Place {
		HEALER("healer", VillagerProfession.CLERIC),
		BUTCHER("butcher", VillagerProfession.BUTCHER),
		SMITH("smith", VillagerProfession.ARMORER, VillagerProfession.WEAPONSMITH, VillagerProfession.TOOLSMITH),
		TAILOR("tailor", VillagerProfession.SHEPHERD),
		TAVERN("tavern"),
		STABLES("stables"),
		RULER("ruler");

		final String key;
		final List<ResourceKey<VillagerProfession>> jobs;

		@SafeVarargs
		Place(String key, ResourceKey<VillagerProfession>... jobs) {
			this.key = key;
			this.jobs = List.of(jobs);
		}

		static @Nullable Place of(String answer) {
			for (Place p : values()) {
				if (p.key.equals(answer)) {
					return p;
				}
			}
			return null;
		}
	}

	/** Where they found it: a spot, and the one there (if it's a person), named for the telling. */
	record Found(BlockPos pos, @Nullable Entity who, Component name) {
	}

	private record Point(Vec3 at, long until) {
	}

	private static final class Lead {
		final UUID player;
		final Place place;
		final BlockPos to;
		final Component name;
		final long started, until;
		long hurried = Long.MIN_VALUE / 2, nearer;
		double nearest = Double.MAX_VALUE;
		int retarget;

		Lead(UUID player, Place place, BlockPos to, Component name, long now) {
			this.player = player;
			this.place = place;
			this.to = to;
			this.name = name;
			this.started = now;
			this.nearer = now;
			this.until = now + LEAD_TICKS;
		}
	}

	/** Who is walking whom where, and who is standing pointing. */
	private static final Map<Villager, Lead> LEADS = new WeakHashMap<>();
	private static final Map<Villager, Point> POINTING = new WeakHashMap<>();

	private Directions() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Directions::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LEADS.clear();
			POINTING.clear();
		});
	}

	/** Any villager but a ship's crew can be asked the way. */
	public static boolean offers(Villager v) {
		return v.isAlive() && !Crewmen.tagged(v);
	}

	/** {@code v} is walking someone somewhere or pointing the way (their day's routine leaves them be). */
	static boolean guiding(Villager v) {
		return LEADS.containsKey(v) || POINTING.containsKey(v);
	}

	// ---- the asking ---------------------------------------------------------------------------------------------------

	/**
	 * {@code player} picked {@code answer} from Ask the Way on {@code v}: what they say (in MCA's chat, so it stays to be
	 * read back), or null if it wasn't one of ours.
	 */
	public static @Nullable Component answer(ServerPlayer player, Villager v, String answer) {
		Place place = Place.of(answer);
		if (place == null || !(v.level() instanceof ServerLevel level)) {
			return null;
		}
		RandomSource random = level.getRandom();
		if (McaBridge.heartsOf(v, player) < REFUSE_HEARTS) {
			return Component.translatable(line("refuse", random));
		}
		if (LEADS.containsKey(v)) {
			return Component.translatable(line("already", random));
		}
		Found found = find(level, v, place);
		if (found == null) {
			return Component.translatable("directions.jaymoskingdoms.none." + place.key);
		}
		if (found.who() == v) {
			return Component.translatable(line("self", random));
		}
		double far = Math.sqrt(found.pos().distToCenterSqr(v.position()));
		boolean asleep = found.who() instanceof PathfinderMob m && m.isSleeping();
		if (far > POINT_ONLY && far <= GUIDE_RANGE && !asleep && guides(v, player)) {
			LEADS.put(v, new Lead(player.getUUID(), place, found.pos(), found.name(), level.getGameTime()));
			POINTING.remove(v);
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, v.isBaby() ? 1.4F : 1.0F);
			return Component.translatable(line("guide", random), found.name());
		}
		return point(v, found, random, asleep ? "asleep" : "point");
	}

	/** {@code v} will walk {@code player} over: a child always, a grown one who likes them, never one with a post to keep. */
	private static boolean guides(Villager v, ServerPlayer player) {
		if (v instanceof Worker w && w.role() >= 0 || Rulers.is(v) || v.isSleeping() || v.getTarget() != null || Downed.isDowned(v)) {
			return false;
		}
		String job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		if (job.equals("guard") || job.equals("archer")) {
			return false;
		}
		return v.isBaby() || McaBridge.heartsOf(v, player) >= GUIDE_HEARTS;
	}

	/** They turn to face it; what they say of where it is. */
	private static Component point(Villager v, Found found, RandomSource random, String kind) {
		Vec3 at = Vec3.atCenterOf(found.pos());
		POINTING.put(v, new Point(at, v.level().getGameTime() + POINT_TICKS));
		double far = Math.sqrt(found.pos().distToCenterSqr(v.position()));
		if (far <= POINT_ONLY) {
			return Component.translatable(line("close", random), found.name());
		}
		return Component.translatable(line(kind, random), found.name(), distance(far), heading(v.position(), at));
	}

	// ---- finding it ---------------------------------------------------------------------------------------------------

	/** The nearest {@code place} to {@code v} in their town, or null if there's none. */
	static @Nullable Found find(ServerLevel level, Villager v, Place place) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.around(server, level, v.blockPosition(), FIND_RANGE);
		switch (place) {
			case TAVERN, STABLES -> {
				if (f == null) {
					return null;
				}
				String entry = place == Place.TAVERN ? Planner.TAVERN : "stable";
				Works.Built b = Works.of(server, f.id()).built().stream().filter(x -> x.entry().equals(entry) && x.ruined() == 0)
					.min(Comparator.comparingDouble(x -> middle(x).distSqr(v.blockPosition()))).orElse(null);
				return b == null || middle(b).distSqr(v.blockPosition()) > FIND_RANGE * FIND_RANGE * 4 ? null
					: new Found(middle(b), null, Component.translatable("directions.jaymoskingdoms.the." + place.key));
			}
			case RULER -> {
				if (f == null || f.leader().isEmpty()) {
					return null;
				}
				PathfinderMob ruler = Rulers.of(level, f);
				if (ruler != null && ruler.distanceToSqr(v) <= FIND_RANGE * FIND_RANGE * 4) {
					return new Found(ruler.blockPosition(), ruler, ruler.getName());
				}
				// (away from home: their seat, then)
				return new Found(f.seat(), null, Component.translatable("directions.jaymoskingdoms.the.seat"));
			}
			default -> {
				AABB box = v.getBoundingBox().inflate(FIND_RANGE, 32.0, FIND_RANGE);
				List<Villager> folk = level.getEntitiesOfClass(Villager.class, box,
					o -> o.isAlive() && !o.isBaby() && !Downed.isDowned(o) && place.jobs.stream().anyMatch(j -> o.getVillagerData().profession().is(j)));
				Villager best = null;
				double bestD = Double.MAX_VALUE;
				for (Villager o : folk) {
					double d = o == v ? -1.0 : o.distanceToSqr(v);
					if (d < bestD) {
						best = o;
						bestD = d;
					}
				}
				if (best == null) {
					return null;
				}
				Component name = Component.translatable("directions.jaymoskingdoms.who." + place.key, best.getName());
				return new Found(workplace(level, best), best, name);
			}
		}
	}

	/** Where to find {@code o}: at their workstation while it's near them, else where they stand. */
	private static BlockPos workplace(ServerLevel level, Villager o) {
		Optional<GlobalPos> site = o.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		if (site.isPresent() && site.get().dimension() == level.dimension() && site.get().pos().distSqr(o.blockPosition()) <= 48.0 * 48.0) {
			return site.get().pos();
		}
		return o.blockPosition();
	}

	private static BlockPos middle(Works.Built b) {
		return new BlockPos((b.min().getX() + b.max().getX()) / 2, b.min().getY() + 1, (b.min().getZ() + b.max().getZ()) / 2);
	}

	// ---- their words ----------------------------------------------------------------------------------------------------

	private static String line(String kind, RandomSource random) {
		return "directions.jaymoskingdoms." + kind + "." + random.nextInt(LINES);
	}

	/** A speech bubble over them, for the asker alone. */
	private static void say(Villager v, ServerPlayer player, Component words) {
		if (v.level() instanceof ServerLevel level) {
			Speech.sayTo(level, v, player, words, Speech.WHITE);
		}
	}

	/** "a stone's throw", "about 40 paces", "a fair walk". */
	static Component distance(double blocks) {
		if (blocks < NEAR) {
			return Component.translatable("directions.jaymoskingdoms.dist.near");
		}
		if (blocks > FAR) {
			return Component.translatable("directions.jaymoskingdoms.dist.far");
		}
		long paces = Math.max(ROUND_TO, Math.round(blocks / ROUND_TO) * ROUND_TO);
		return Component.translatable("directions.jaymoskingdoms.dist.paces", paces);
	}

	/** The compass point from {@code from} to {@code to} (north is -z). */
	static Component heading(Vec3 from, Vec3 to) {
		double dx = to.x - from.x, dz = to.z - from.z;
		// (0 = north, clockwise in eighths)
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		int eighth = (int)Math.floorMod(Math.round(angle / 45.0), 8);
		String[] points = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
		return Component.translatable("directions.jaymoskingdoms.dir." + points[eighth]);
	}

	// ---- walking you there -------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!POINTING.isEmpty()) {
			POINTING.entrySet().removeIf(e -> {
				Villager v = e.getKey();
				if (!v.isAlive() || v.level().getGameTime() > e.getValue().until() || v.getTarget() != null) {
					return true;
				}
				v.getNavigation().stop();
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				Vec3 at = e.getValue().at();
				v.getLookControl().setLookAt(at.x, v.getEyeY(), at.z, 30.0F, 30.0F);
				return false;
			});
		}
		if (!LEADS.isEmpty()) {
			LEADS.entrySet().removeIf(e -> !step(server, e.getKey(), e.getValue()));
		}
	}

	/** One tick of {@code v} walking someone over: false once it's over. */
	private static boolean step(MinecraftServer server, Villager v, Lead lead) {
		ServerPlayer player = server.getPlayerList().getPlayer(lead.player);
		if (player == null || !v.isAlive() || player.level() != v.level() || !player.isAlive() || v.getTarget() != null || Downed.isDowned(v)
			|| v.isSleeping()) {
			return false;
		}
		long now = v.level().getGameTime();
		RandomSource random = v.getRandom();
		Vec3 to = Vec3.atCenterOf(lead.to);
		if (now - lead.started < START_TICKS || McaCompat.busy(v)) {
			// (waiting for you to close the menu)
			v.getNavigation().stop();
			v.getLookControl().setLookAt(player, 30.0F, 30.0F);
			return true;
		}
		double there = Math.min(v.position().distanceTo(to), player.position().distanceTo(to));
		if (there <= ARRIVE_RANGE) {
			say(v, player, Component.translatable(line("arrive", random), lead.name));
			POINTING.put(v, new Point(to, now + POINT_TICKS));
			return false;
		}
		double behind = v.distanceTo(player);
		if (behind > LOSE_RANGE) {
			say(v, player, Component.translatable(line("lost", random)));
			return false;
		}
		if (now > lead.until || now - lead.nearer > STUCK_TICKS) {
			// (out of time, or got no nearer: the rest of the way pointed out)
			say(v, player, point(v, new Found(lead.to, null, lead.name), random, "rest"));
			return false;
		}
		if (behind > WAIT_RANGE) {
			v.getNavigation().stop();
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			v.getLookControl().setLookAt(player, 30.0F, 30.0F);
			if (now - lead.hurried >= HURRY_TICKS) {
				lead.hurried = now;
				say(v, player, Component.translatable(line("hurry", random)));
			}
			// (waiting isn't being stuck)
			lead.nearer = now;
			return true;
		}
		double d = v.position().distanceTo(to);
		if (d < lead.nearest - 0.5) {
			lead.nearest = d;
			lead.nearer = now;
		}
		if (--lead.retarget <= 0 || !v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			lead.retarget = RETARGET_TICKS;
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(lead.to, LEAD_SPEED, 2));
		}
		return true;
	}
}
