package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.gifted.Gifted;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.6) Andy (2026-10-29): "galactic patrolmen will now appear as well, they are villager z fighters who are constantly
 * battling with evil forces, and also use a pod to get around. they will tell you of their travels, after an introduction,
 * and judging on your power level will recruit you to be one as well. giving you a galactic patrol job, a pod and a license
 * to fight crime on various planets."
 * <p>
 * <b>The visits</b> (needs Jaymo's Kingdoms and MCA). (0.19.1, Andy: "they shouldnt just appear. they always need a reason.")
 * A patrolman never drops in at random any more; every pod that comes down has a {@link Cause}:
 * <ul>
 * <li>{@link Cause#PURSUIT}: a raider band ({@link ZFighterRaiders}) came down by a town and the Patrol is on its tail
 * ({@link #PURSUIT_CHANCE}): his pod lands {@link #PURSUIT_DELAY} after theirs, he runs the band's raiders down wherever they
 * are, and once the band is beaten or gone he lingers {@link #PURSUIT_LINGER} and leaves.</li>
 * <li>{@link Cause#RECRUIT}: word of a strong fighter (power {@link #RECRUIT_POWER}, Borderlands open, no officer yet) has
 * reached the Patrol: a patrolman comes down by them, walks to them to make his offer, and leaves once they have answered,
 * gone or {@link #RECRUIT_STAY} has passed. A refusal is asked again after {@link #RECRUIT_GAP}.</li>
 * </ul>
 * The patrolman ({@link #TAG}, an MCA villager through Kingdoms' {@code ZFighterFolk}, in the patrol's uniform) steps out with a
 * Z Fighter's gift ({@link Gifted}). One with no cause (from an older save, or come some other way) goes straight home. Nobody can
 * take his pod.
 * <p>
 * <b>Talking</b> ("Talk about work" in MCA's menu, which a stranger only gets once introduced): he tells of his travels, a
 * piece a talk ({@link #STORIES}), and weighs your power level ({@link ZFighterScouter#powerLevel}): at {@link #RECRUIT_POWER}
 * or more he offers to take you on. Joining ({@link #join}) makes you a patrol officer ({@link #MEMBER}) and gives you a pod
 * full of fuel and a {@link #LICENSE}. A patrolman you meet after that will fuel you once a visit ({@link #FUEL_GIFT}).
 * <p>
 * <b>Warrants</b>: with the license on you, every far world your pod lands on has a wanted criminal to bring down
 * ({@link #arrived}), at most once a world in {@link #WARRANT_GAP}: a rogue fighter that glows so you can find him, tougher the
 * higher your rank. Bring him down for the bounty (emeralds, and TP for a Z Fighter); every arrest counts toward your rank
 * ({@link #RANKS}). Leave the world and he gets away. The license, used, shows your rank and any warrant you hold.
 */
public final class ZFighterPatrol {
	// ---- tuning ----
	/** How far from where he is headed his pod lands. */
	static final int LAND_MIN = 14, LAND_MAX = 30;
	/** (0.19.1) Pursuit: the chance the Patrol follows a raider band down, how long after them (ticks), how long he stays at most,
	 * how long he lingers once the band is beaten or gone, and how far he will go after one of its raiders. */
	static final float PURSUIT_CHANCE = 0.6F;
	static final int PURSUIT_DELAY = 600, PURSUIT_STAY = 14000, PURSUIT_LINGER = 1200;
	static final double PURSUIT_RANGE = 96.0;
	/** (0.19.1) Recruiting: how long he stays at most, how long after the answer he leaves, the wait before a refusal is asked
	 * again (ticks), how near he walks to them, and how far off they may get before he gives up. */
	static final int RECRUIT_STAY = 6000, ANSWERED_STAY = 300;
	static final long RECRUIT_GAP = 7 * 24000L;
	static final double RECRUIT_CLOSE = 3.0, RECRUIT_LOST = 128.0;
	/** A patrolman with something to do near a player only fights monsters this near him on the way. */
	static final double SIDE_FIGHT = 8.0;
	/** How far he goes for a foe, how far he roams from his pod, how hard his fists land and how often. */
	static final double FIGHT_RANGE = 24.0, ROAM = 22.0, REACH = 2.6;
	static final float PUNCH = 7.0F;
	static final int PUNCH_EVERY = 16, ROAM_EVERY = 240, THINK_EVERY = 10;
	/** The power level he wants to see before he offers to take you on. */
	public static final int RECRUIT_POWER = 1500;
	/** The fuel a patrolman gives an officer once a visit (blaze powder: two fuel each). */
	static final int FUEL_GIFT = 4;
	/** A warrant: the wait before the same world has another (ticks), how far off he hides, his health and blows (shares of a tyrant's). */
	static final long WARRANT_GAP = 24000L;
	static final int HIDE_MIN = 40, HIDE_MAX = 70;
	static final double WARRANT_HEALTH = 0.35, WARRANT_DAMAGE = 0.7, RANK_TOUGHER = 0.25;
	/** The bounty: emeralds and TP, more for each rank. */
	static final int BOUNTY_EMERALDS = 12, BOUNTY_TP = 300;
	static final double RANK_PAYS = 0.5;
	/** The arrests each rank needs: Rookie, Officer, Sergeant, Inspector, Commander. */
	static final int[] RANKS = {0, 3, 8, 15, 25};
	/**
	 * (0.18.54, "Ranks Follow Rungs") The world tier (0 Hearth ... 4 Legend) each rank needs open through Kingdoms' deeds as well as
	 * its arrests: Rookie Borderlands, Officer (the Patrolman) Wildlands, Sergeant and Inspector Dread, Commander Legend. Without
	 * Kingdoms the Patrol goes by power and arrests alone.
	 */
	static final int[] RANK_TIER = {1, 2, 3, 3, 4};
	/** A travel story about the landing yard, told once: its bit in {@link #TOLD}. */
	static final int YARD_STORY_BIT = 1 << 30;
	/** How many ways each line is said, and how many travel stories there are. */
	static final int GREET_LINES = 3, STORIES = 9, NOT_YET_LINES = 3, OFFER_LINES = 2, REST_LINES = 2, NAMES = 10;
	/** The pod's arrival and departure: how high it starts, how fast it comes down or goes up (blocks a tick). */
	static final int ARRIVE_HEIGHT = 60;
	static final double ARRIVE_SPEED = 0.7, DEPART_SPEED = 1.2;
	static final int DEPART_TICKS = 90;

	public static final String TAG = "jaymospowers.patrolman";
	public static final String POD_TAG = "jaymospowers.patrol_pod";
	static final String WANTED_TAG = "jaymospowers.wanted";
	/** On a patrol pod while it flies (not while it stands parked). */
	public static final String FLYING_TAG = "jaymospowers.patrol_pod_flying";
	static final long DAY = 24000L;

	/** (0.19.1) Why a patrolman came down: there is always a reason. NONE is only an older save's, and he goes home. */
	public enum Cause {
		NONE, PURSUIT, RECRUIT;

		static Cause of(String name) {
			for (Cause c : values()) {
				if (c.name().equals(name)) {
					return c;
				}
			}
			return NONE;
		}
	}

	/** A patrolman's visit: where his pod stands, when he leaves, why he came, and who for (the band he chases, the fighter he recruits). */
	public record Visit(BlockPos pod, long leave, Cause cause, java.util.Optional<UUID> target) {
		public static final Codec<Visit> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pod").forGetter(Visit::pod),
			Codec.LONG.fieldOf("leave").forGetter(Visit::leave),
			Codec.STRING.optionalFieldOf("cause", "NONE").xmap(Cause::of, Cause::name).forGetter(Visit::cause),
			net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("target").forGetter(Visit::target)).apply(i, Visit::new));

		Visit leaving(long at) {
			return new Visit(pod, Math.min(leave, at), cause, target);
		}
	}

	/** An officer of the patrol: arrests made, and the patrolman visit (his leave time) that last fuelled him. */
	public record Member(int arrests, long fuelled) {
		public static final Codec<Member> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("arrests").forGetter(Member::arrests),
			Codec.LONG.fieldOf("fuelled").forGetter(Member::fuelled)).apply(i, Member::new));
	}

	public static final AttachmentType<Visit> VISIT = AttachmentRegistry.create(JaymosPowers.id("patrol_visit"),
		builder -> builder.persistent(Visit.CODEC));
	/** The travel stories a patrolman has told (bits). */
	public static final AttachmentType<Integer> TOLD = AttachmentRegistry.create(JaymosPowers.id("patrol_told"),
		builder -> builder.persistent(Codec.INT));
	public static final AttachmentType<Member> MEMBER = AttachmentRegistry.create(JaymosPowers.id("patrol_member"),
		builder -> builder.persistent(Member.CODEC).copyOnDeath());
	/** (0.19.1) No longer used: patrolmen stopped coming on a timer. Kept registered so older saves load cleanly. */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosPowers.id("patrol_next"),
		builder -> builder.persistent(Codec.LONG));
	/** (0.19.1) On a player: when the Patrol may next come to recruit them (game time). */
	public static final AttachmentType<Long> RECRUIT_NEXT = AttachmentRegistry.create(JaymosPowers.id("patrol_recruit_next"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	public static Item LICENSE;

	/** (0.19.1) A pursuit on its way down: the band, where it came down, and when the patrolman's pod follows. */
	private record Pursuit(ServerLevel level, UUID band, BlockPos where, long at) {
	}

	private static final List<Pursuit> PURSUITS = new ArrayList<>();

	/** A warrant out: whose, the criminal, his world, and when it was written. */
	private record Warrant(UUID owner, UUID criminal, ResourceKey<Level> level, int planet) {
	}

	/** A pod coming down (and what steps out when it lands), or going up with whoever flew it gone. */
	static final class Flier {
		final PodEntity pod;
		final BlockPos ground;
		final boolean arriving;
		final int name;
		final java.util.function.@Nullable Consumer<Flier> landed;
		int ticks;

		Flier(PodEntity pod, BlockPos ground, boolean arriving, int name, java.util.function.@Nullable Consumer<Flier> landed) {
			this.pod = pod;
			this.ground = ground;
			this.arriving = arriving;
			this.name = name;
			this.landed = landed;
		}
	}

	private static final List<Flier> FLIERS = new ArrayList<>();
	private static final List<Warrant> WARRANTS = new ArrayList<>();
	/** When each officer may next get a warrant on each world ("uuid|planet"). */
	private static final Map<String, Long> NEXT_WARRANT = new HashMap<>();

	private ZFighterPatrol() {
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("patrol_license"));
		LICENSE = Registry.register(BuiltInRegistries.ITEM, key, new LicenseItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(LICENSE));
		ServerTickEvents.END_SERVER_TICK.register(ZFighterPatrol::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(ZFighterPatrol::afterDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLIERS.clear();
			PURSUITS.clear();
			WARRANTS.clear();
			NEXT_WARRANT.clear();
		});
		// a flying pod or a criminal left over from before the server stopped goes
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(WANTED_TAG) && !warranted(entity) || entity.entityTags().contains(FLYING_TAG) && !flying(entity)) {
				entity.discard();
			}
		});
	}

	/** Whether {@code e} is a galactic patrolman. */
	static boolean patrolman(Entity e) {
		return e instanceof LivingEntity && e.entityTags().contains(TAG);
	}

	public static boolean member(Player player) {
		return player.getAttached(MEMBER) != null;
	}

	/** The rank (0: Rookie) an officer with {@code arrests} holds. */
	static int rank(int arrests) {
		int rank = 0;
		for (int i = 0; i < RANKS.length; i++) {
			if (arrests >= RANKS[i]) {
				rank = i;
			}
		}
		return rank;
	}

	/** The rank an officer with {@code arrests} holds, held back to what the world tiers open to {@code player} allow. */
	static int rank(Player player, int arrests) {
		int rank = rank(arrests);
		int tier = ZFighterKingdoms.openTier(player);
		while (tier >= 0 && rank > 0 && RANK_TIER[rank] > tier) {
			rank--;
		}
		return rank;
	}

	static Component rankName(int rank) {
		return Component.translatable("patrol.jaymospowers.rank." + rank);
	}

	// ---------------------------------------------------------------- every tick

	private static void tick(MinecraftServer server) {
		if (!FLIERS.isEmpty()) {
			fliers();
		}
		long ticks = server.getTickCount();
		if (ticks % THINK_EVERY == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.players().isEmpty()) {
					patrolmen(level);
				}
			}
		}
		if (ticks % 100 == 0 && !WARRANTS.isEmpty()) {
			warrants(server);
		}
		if (ticks % 20 == 0 && !PURSUITS.isEmpty()) {
			pursuits(server);
		}
		if (ticks % 600 == 0) {
			recruiters(server);
		}
	}

	/** (0.19.1) A raider band came down by {@code where}: the Patrol may be on its tail, a little behind it. */
	static void pursue(ServerLevel level, UUID band, BlockPos where) {
		if (!ZFighterNpcs.villagers() || !ZFighterKingdoms.present() || level.getRandom().nextFloat() >= PURSUIT_CHANCE) {
			return;
		}
		PURSUITS.add(new Pursuit(level, band, where, level.getGameTime() + PURSUIT_DELAY));
	}

	/** Pursuits whose time has come: the patrolman's pod comes down by where the band landed, if the band is still there. */
	private static void pursuits(MinecraftServer server) {
		for (Iterator<Pursuit> it = PURSUITS.iterator(); it.hasNext();) {
			Pursuit p = it.next();
			if (p.level().getGameTime() < p.at()) {
				continue;
			}
			it.remove();
			if (!ZFighterRaiders.raiders(p.band()).isEmpty()) {
				arrive(p.level(), p.where(), p.level().getRandom().nextInt(NAMES), Cause.PURSUIT, p.band(), PURSUIT_STAY);
			}
		}
	}

	/** (0.19.1) Now and then, by day: a strong fighter out under the sky whom the Patrol has heard of gets a recruiter. */
	private static void recruiters(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!ZFighterNpcs.villagers() || !ZFighterKingdoms.present() || !level.isBrightOutside()) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || member(player) || now < player.getAttachedOrElse(RECRUIT_NEXT, 0L)
				|| ZFighterScouter.powerLevel(player) < RECRUIT_POWER || !level.canSeeSky(player.blockPosition().above())) {
				continue;
			}
			int tier = ZFighterKingdoms.openTier(player);
			if (tier >= 0 && tier < RANK_TIER[0] || recruiterFor(level, player)) {
				continue;
			}
			if (arrive(level, player.blockPosition(), level.getRandom().nextInt(NAMES), Cause.RECRUIT, player.getUUID(), RECRUIT_STAY)) {
				player.setAttached(RECRUIT_NEXT, now + RECRUIT_GAP);
				return;
			}
		}
	}

	/** Whether a recruiter is already down (or coming down) for {@code player}. */
	private static boolean recruiterFor(ServerLevel level, ServerPlayer player) {
		for (PathfinderMob man : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(RECRUIT_LOST), ZFighterPatrol::patrolman)) {
			Visit v = man.getAttached(VISIT);
			if (v != null && v.cause() == Cause.RECRUIT && v.target().filter(player.getUUID()::equals).isPresent()) {
				return true;
			}
		}
		return false;
	}

	/** A patrolman's pod starts down over open ground by {@code seat}, for {@code cause}; true if a spot was found. */
	static boolean arrive(ServerLevel level, BlockPos seat, int name, Cause cause, UUID target, int stay) {
		RandomSource random = level.getRandom();
		// (0.18.54) a landing yard within reach: down on a free pad, and the town named for it
		BlockPos pad = ZFighterYard.freePad(level, seat);
		if (pad != null) {
			land(level, pad, name, f -> {
				stepOut((ServerLevel)f.pod.level(), f, cause, target, stay);
				ZFighterKingdoms.yardTouched((ServerLevel)f.pod.level(), f.ground);
			});
			level.playSound(null, pad.getX(), pad.getY() + 20, pad.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3.0F, 0.6F);
			return true;
		}
		for (int tries = 0; tries < 12; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = LAND_MIN + random.nextDouble() * (LAND_MAX - LAND_MIN);
			int x = seat.getX() + (int)Math.round(Math.cos(angle) * distance), z = seat.getZ() + (int)Math.round(Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			BlockPos ground = ZFighterSpace.surface(level, x, z);
			if (ground == null) {
				continue;
			}
			land(level, ground, name, f -> stepOut((ServerLevel)f.pod.level(), f, cause, target, stay));
			level.playSound(null, ground.getX(), ground.getY() + 20, ground.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3.0F, 0.6F);
			return true;
		}
		return false;
	}

	/** A pod starts down out of the sky over {@code ground}; {@code landed} runs when it sets down. */
	static Flier land(ServerLevel level, BlockPos ground, int name, java.util.function.Consumer<Flier> landed) {
		PodEntity pod = pod(level, new Vec3(ground.getX() + 0.5, ground.getY() + ARRIVE_HEIGHT, ground.getZ() + 0.5));
		Flier flier = new Flier(pod, ground, true, name, landed);
		FLIERS.add(flier);
		pod.addTag(FLYING_TAG);
		pod.setHovering(true);
		return flier;
	}

	/** A parked pod at {@code at} goes up and away. */
	static void takeOff(ServerLevel level, PodEntity pod) {
		FLIERS.add(new Flier(pod, pod.blockPosition(), false, 0, null));
		pod.addTag(FLYING_TAG);
		pod.setHovering(true);
		level.playSound(null, pod.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3.0F, 0.5F);
	}

	/** A pod nobody can take (0.18.49: a complete pod entity, locked by {@link #POD_TAG}). */
	static PodEntity pod(ServerLevel level, Vec3 at) {
		PodEntity pod = PodEntity.spawn(level, at, true, ZFighterSpace.MAX_FUEL);
		pod.addTag(POD_TAG);
		return pod;
	}

	/** Pods coming down and going up. */
	private static void fliers() {
		for (Iterator<Flier> it = FLIERS.iterator(); it.hasNext();) {
			Flier f = it.next();
			if (f.pod.isRemoved() || !(f.pod.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			Vec3 at = f.pod.position();
			f.ticks++;
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.1, at.z, 6, 0.3, 0.1, 0.3, 0.05);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y - 0.4, at.z, 2, 0.4, 0.15, 0.4, 0.02);
			if (f.arriving) {
				double left = at.y - f.ground.getY();
				double speed = Mth.clamp(left * 0.05, 0.12, ARRIVE_SPEED);
				if (left <= speed || f.ticks > 600) {
					f.pod.setPos(at.x, f.ground.getY(), at.z);
					level.sendParticles(ParticleTypes.CLOUD, at.x, f.ground.getY() + 0.2, at.z, 30, 1.4, 0.1, 1.4, 0.1);
					level.playSound(null, f.ground, SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 1.0F, 0.7F);
					f.pod.removeTag(FLYING_TAG);
					f.pod.setHovering(false);
					if (f.landed != null) {
						f.landed.accept(f);
					}
					it.remove();
					continue;
				}
				f.pod.setPos(at.x, at.y - speed, at.z);
			} else {
				f.pod.setPos(at.x, at.y + Math.min(DEPART_SPEED, 0.1 + f.ticks * 0.03), at.z);
				if (f.ticks >= DEPART_TICKS) {
					f.pod.discard();
					it.remove();
				}
			}
		}
	}

	/** Down: the patrolman steps out beside his pod, with his gift, for a stay. */
	private static void stepOut(ServerLevel level, Flier f, Cause cause, UUID target, int stay) {
		BlockPos ground = f.ground;
		Vec3 feet = new Vec3(ground.getX() + 2.5, ground.getY(), ground.getZ() + 0.5);
		LivingEntity man = ZFighterNpcs.villager(level, feet, level.getRandom().nextFloat() * 360.0F, TAG, "entity.jaymospowers.patrolman_" + f.name,
			f.name % 4 == 1);
		if (man == null) {
			f.pod.discard();
			return;
		}
		man.setAttached(VISIT, new Visit(ground, level.getGameTime() + stay, cause, java.util.Optional.of(target)));
		Gifted.give(man, "zfighter", Gifted.BOLD, true, true);
	}

	/** Every half second: each patrolman fights what is near, roams, or, his stay over, goes home. */
	private static void patrolmen(ServerLevel level) {
		List<PathfinderMob> men = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			for (PathfinderMob man : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(96.0), e -> e.isAlive() && patrolman(e))) {
				if (!men.contains(man)) {
					men.add(man);
				}
			}
		}
		long now = level.getGameTime();
		for (PathfinderMob man : men) {
			patrol(level, man, now);
		}
	}

	private static void patrol(ServerLevel level, PathfinderMob man, long now) {
		Visit visit = man.getAttached(VISIT);
		if (visit == null || visit.cause() == Cause.NONE) {
			// (0.19.1) one with no reason to be here (an older save's, or come some other way): he goes home now
			visit = new Visit(visit == null ? man.blockPosition() : visit.pod(), now, Cause.NONE, java.util.Optional.empty());
			man.setAttached(VISIT, visit);
		}
		Vec3 pod = Vec3.atBottomCenterOf(visit.pod());
		if (now >= visit.leave()) {
			if (man.distanceToSqr(pod) < 9.0 || now > visit.leave() + 1200) {
				depart(level, man, visit);
			} else {
				man.setTarget(null);
				man.getNavigation().moveTo(pod.x, pod.y, pod.z, 0.8);
			}
			return;
		}
		LivingEntity foe = null;
		@Nullable ServerPlayer recruit = null;
		if (visit.cause() == Cause.PURSUIT) {
			// the band he came after, wherever its raiders are; once it is beaten or gone he lingers a little and leaves
			UUID band = visit.target().orElse(null);
			List<? extends LivingEntity> raiders = band == null ? List.of() : ZFighterRaiders.raiders(band);
			if (raiders.isEmpty()) {
				if (visit.leave() > now + PURSUIT_LINGER) {
					visit = visit.leaving(now + PURSUIT_LINGER);
					man.setAttached(VISIT, visit);
				}
			} else {
				foe = nearest(man, raiders, PURSUIT_RANGE);
			}
		} else if (visit.cause() == Cause.RECRUIT) {
			UUID id = visit.target().orElse(null);
			recruit = id != null && level.getPlayerByUUID(id) instanceof ServerPlayer p && p.isAlive() && man.distanceTo(p) < RECRUIT_LOST ? p : null;
			if (recruit == null || member(recruit)) {
				// they are gone, or already sworn in: nothing left for him here
				man.setAttached(VISIT, visit.leaving(now + ANSWERED_STAY));
			}
		}
		if (foe == null) {
			foe = foe(level, man, visit.cause() == Cause.RECRUIT ? SIDE_FIGHT : FIGHT_RANGE);
		}
		if (foe != null) {
			man.setTarget(foe);
			man.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			if (man.distanceTo(foe) > REACH) {
				man.getNavigation().moveTo(foe, 1.0);
			} else {
				man.getNavigation().stop();
				if (man.tickCount % PUNCH_EVERY < THINK_EVERY) {
					man.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					DamageSource source = level.damageSources().mobAttack(man);
					if (foe.hurtServer(level, source, PUNCH)) {
						Vec3 push = foe.position().subtract(man.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.6);
						foe.push(push.x, 0.25, push.z);
						level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 0.8F, 0.9F);
					}
				}
			}
			return;
		}
		man.setTarget(null);
		if (recruit != null && !member(recruit)) {
			// he came for them: he walks up and waits by them for their answer
			man.getLookControl().setLookAt(recruit, 30.0F, 30.0F);
			if (man.distanceTo(recruit) > RECRUIT_CLOSE) {
				man.getNavigation().moveTo(recruit, 0.8);
			} else {
				man.getNavigation().stop();
			}
			return;
		}
		if (man.getNavigation().isDone() && man.getRandom().nextInt(ROAM_EVERY / THINK_EVERY) == 0) {
			// the band beaten, he looks the ground over by his pod before he goes
			RandomSource random = man.getRandom();
			double x = pod.x + (random.nextDouble() * 2.0 - 1.0) * ROAM, z = pod.z + (random.nextDouble() * 2.0 - 1.0) * ROAM;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			man.getNavigation().moveTo(x, y, z, 0.6);
		}
	}

	/** The nearest of {@code from} within {@code range} of him, or null. */
	private static @Nullable LivingEntity nearest(PathfinderMob man, List<? extends LivingEntity> from, double range) {
		LivingEntity best = null;
		double bestD = range * range;
		for (LivingEntity e : from) {
			double d = man.distanceToSqr(e);
			if (e.level() == man.level() && d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The nearest monster or rogue fighter within reach of him, or null. */
	private static @Nullable LivingEntity foe(ServerLevel level, PathfinderMob man, double range) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB box = man.getBoundingBox().inflate(range);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e instanceof Enemy && !(e instanceof Player))) {
			double d = man.distanceToSqr(e);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** His stay is over: he climbs in and the pod goes up. */
	private static void depart(ServerLevel level, PathfinderMob man, Visit visit) {
		Vec3 at = Vec3.atBottomCenterOf(visit.pod());
		PodEntity pod = null;
		for (PodEntity d : level.getEntitiesOfClass(PodEntity.class, new AABB(visit.pod()).inflate(3.0), d -> d.entityTags().contains(POD_TAG))) {
			pod = d;
		}
		if (pod == null) {
			pod = pod(level, at);
		}
		man.discard();
		takeOff(level, pod);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 30, 1.4, 0.2, 1.4, 0.1);
	}

	// ---------------------------------------------------------------- talking

	/** "Talk about work" with a patrolman ({@code ZFighterNpcs.talk}): his greeting and what to ask. */
	static void talk(ServerPlayer player, LivingEntity man) {
		if (man instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
		Member member = player.getAttached(MEMBER);
		// (0.19.1) he says why he is here: the band he chases, or the fighter he came to find
		Visit visit = man.getAttached(VISIT);
		Cause cause = visit == null ? Cause.NONE : visit.cause();
		boolean forThem = cause == Cause.RECRUIT && visit.target().filter(player.getUUID()::equals).isPresent();
		String greet = member != null ? "greet.officer" : forThem ? "greet.recruit" : cause == Cause.PURSUIT ? "greet.pursuit"
			: ZFighterScouter.powerLevel(player) >= RECRUIT_POWER ? "greet.strong" : "greet";
		menu(player, man, line(greet, GREET_LINES, man), member);
	}

	private static void menu(ServerPlayer player, LivingEntity man, Component words, @Nullable Member member) {
		Runnable back = () -> menu(player, man, line("again", GREET_LINES, man), player.getAttached(MEMBER));
		List<ZFighterNpcs.Option> options = new ArrayList<>();
		options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.travels", true, () -> ZFighterNpcs.ask(player, man, story(man),
			List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)))));
		if (member == null) {
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.join", true, () -> {
				if (ZFighterScouter.powerLevel(player) < RECRUIT_POWER) {
					ZFighterNpcs.ask(player, man, line("not_yet", NOT_YET_LINES, man), List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)));
					return;
				}
				// (0.18.54) power is not enough: a Rookie has the first deed (Borderlands) behind them
				int tier = ZFighterKingdoms.openTier(player);
				if (tier >= 0 && tier < RANK_TIER[0]) {
					ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.deeds"),
						List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)));
					return;
				}
				ZFighterNpcs.ask(player, man, line("offer", OFFER_LINES, man), List.of(
					ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.swear", true, () -> {
						join(player);
						ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.sworn"),
							List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)));
					}),
					ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.later", true, () -> {
						// (0.19.1) a recruiter has his answer: he heads home, and asks again another time
						answered(man, player);
						back.run();
					})));
			}));
		} else {
			Visit visit = man.getAttached(VISIT);
			long stay = visit == null ? 0L : visit.leave();
			boolean fuelled = member.fuelled() == stay;
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.fuel", !fuelled, () -> {
				player.setAttached(MEMBER, new Member(member.arrests(), stay));
				ZFighterSagas.give(player, new ItemStack(Items.BLAZE_POWDER, FUEL_GIFT));
				ZFighterNpcs.ask(player, man, line("fuel", REST_LINES, man), List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)));
			}));
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.writ", officerRank(player) >= WRIT_RANK,
				() -> com.jaymospowers.power.zfighter.farworld.PatrolWrit.ask(player, man, back)));
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.report", true, () -> ZFighterNpcs.ask(player, man,
				Component.translatable("talk.jaymospowers.patrol.report", rankName(rank(player, member.arrests())), member.arrests()),
				List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)))));
		}
		ZFighterNpcs.ask(player, man, words, options);
	}

	/** A recruiter who came for {@code player} has had their answer: he leaves soon. */
	private static void answered(LivingEntity man, ServerPlayer player) {
		Visit visit = man.getAttached(VISIT);
		if (visit != null && visit.cause() == Cause.RECRUIT && visit.target().filter(player.getUUID()::equals).isPresent()) {
			man.setAttached(VISIT, visit.leaving(man.level().getGameTime() + ANSWERED_STAY));
		}
	}

	/** The next of his travel stories, or that he has told them all. */
	private static Component story(LivingEntity man) {
		int told = man.getAttachedOrElse(TOLD, 0);
		if ((told & YARD_STORY_BIT) == 0 && man.level() instanceof ServerLevel here) {
			// (0.18.54) a landing yard near: the town is a stop he names
			String town = ZFighterKingdoms.yardName(here, man.blockPosition(), ZFighterYard.RANGE);
			if (!town.isEmpty()) {
				man.setAttached(TOLD, told | YARD_STORY_BIT);
				return Component.translatable("talk.jaymospowers.patrol.yard", town);
			}
		}
		List<Integer> left = new ArrayList<>();
		for (int i = 0; i < STORIES; i++) {
			if ((told & (1 << i)) == 0) {
				left.add(i);
			}
		}
		if (left.isEmpty()) {
			return line("told", REST_LINES, man);
		}
		int next = left.get(man.getRandom().nextInt(left.size()));
		man.setAttached(TOLD, told | 1 << next);
		return Component.translatable("talk.jaymospowers.patrol.story." + next);
	}

	private static Component line(String name, int count, LivingEntity man) {
		return Component.translatable("talk.jaymospowers.patrol." + name + "." + (1 + man.getRandom().nextInt(count)));
	}

	/** Sworn in: an officer, with a pod full of fuel and the license. */
	static void join(ServerPlayer player) {
		player.setAttached(MEMBER, new Member(0, 0L));
		ServerLevel level = player.level();
		// (0.18.49) a pod is no longer an item: a complete one, full of fuel, is set down beside the new officer
		PodEntity.spawn(level, ZFighterPodTrip.roomFor(level, player.blockPosition()), true, ZFighterSpace.MAX_FUEL);
		ZFighterSagas.give(player, new ItemStack(LICENSE));
		// (0.18.53) and the chart of Rustfall, where the Patrol's work is
		ZFighterSagas.give(player, com.jaymospowers.power.zfighter.sky.Charts.stack(ZFighterSpace.RUSTFALL));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ---------------------------------------------------------------- warrants

	private static boolean licensed(ServerPlayer player) {
		if (!member(player)) {
			return false;
		}
		for (ItemStack stack : player.getInventory()) {
			if (stack.is(LICENSE)) {
				return true;
			}
		}
		return false;
	}

	private static boolean flying(Entity e) {
		for (Flier f : FLIERS) {
			if (f.pod == e) {
				return true;
			}
		}
		return false;
	}

	private static boolean warranted(Entity e) {
		for (Warrant w : WARRANTS) {
			if (w.criminal().equals(e.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** Down on a far world ({@code ZFighterSpace.arrived}): a licensed officer may find a warrant out there. */
	static void arrived(ServerPlayer player, ZFighterSpace.@Nullable Dest dest) {
		if (dest == null || !licensed(player)) {
			return;
		}
		int planet = -1;
		for (int i = 0; i < ZFighterSpace.DESTS.length; i++) {
			if (ZFighterSpace.DESTS[i] == dest) {
				planet = i;
			}
		}
		if (planet < 0 || planet == ZFighterSpace.FUTURE) {
			return;
		}
		for (Warrant w : WARRANTS) {
			if (w.owner().equals(player.getUUID())) {
				return;
			}
		}
		ServerLevel level = player.level();
		String key = player.getUUID() + "|" + planet;
		long now = level.getGameTime();
		if (now < NEXT_WARRANT.getOrDefault(key, 0L)) {
			return;
		}
		ZFighterRogue criminal = criminal(level, player, dest);
		if (criminal == null) {
			return;
		}
		NEXT_WARRANT.put(key, now + WARRANT_GAP);
		WARRANTS.add(new Warrant(player.getUUID(), criminal.getUUID(), level.dimension(), planet));
		int rank = rank(player, player.getAttached(MEMBER).arrests());
		player.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.warrant", criminal.getDisplayName(),
			Component.translatable("space.jaymospowers.dest." + dest.key()), bounty(rank)).withStyle(ChatFormatting.GOLD));
	}

	private static int bounty(int rank) {
		return (int)Math.round(BOUNTY_EMERALDS * (1.0 + RANK_PAYS * rank));
	}

	/** A wanted rogue fighter, hidden somewhere off from where {@code player} came down, glowing so he can be found. */
	private static @Nullable ZFighterRogue criminal(ServerLevel level, ServerPlayer player, ZFighterSpace.Dest dest) {
		RandomSource random = level.getRandom();
		BlockPos at = null;
		if (dest.kind() == ZFighterSpace.Kind.VOID) {
			List<BlockPos> decks = ZFighterSpace.mawDecks();
			at = decks.get(random.nextInt(decks.size()));
		}
		for (int tries = 0; tries < 10 && at == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = HIDE_MIN + random.nextDouble() * (HIDE_MAX - HIDE_MIN);
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance), z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			at = dest.kind() == ZFighterSpace.Kind.FORGE ? ZFighterSpace.forgeFloor(level, x, z) : ZFighterSpace.surface(level, x, z);
		}
		if (at == null) {
			return null;
		}
		ZFighterRogue fighter = ZFighterRaids.ROGUE.create(level, EntitySpawnReason.EVENT);
		if (fighter == null) {
			return null;
		}
		int rank = rank(player, player.getAttached(MEMBER).arrests());
		double tougher = 1.0 + RANK_TOUGHER * rank;
		fighter.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		fighter.become(ZFighterRogue.TYRANT, Component.translatable("entity.jaymospowers.wanted." + random.nextInt(NAMES)));
		double health = ZFighterRogue.TYRANT_HEALTH * WARRANT_HEALTH * tougher;
		fighter.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		fighter.setHealth((float)health);
		fighter.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(ZFighterRogue.TYRANT_DAMAGE * WARRANT_DAMAGE * tougher);
		fighter.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 60, 0, false, false));
		fighter.addTag(WANTED_TAG);
		fighter.setPersistenceRequired();
		level.addFreshEntity(fighter);
		return fighter;
	}

	/** Every few seconds: a criminal whose officer has left his world (or the game) gets away. */
	private static void warrants(MinecraftServer server) {
		for (Iterator<Warrant> it = WARRANTS.iterator(); it.hasNext();) {
			Warrant w = it.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(w.owner());
			ServerLevel level = server.getLevel(w.level());
			Entity criminal = level == null ? null : level.getEntity(w.criminal());
			if (criminal == null || !criminal.isAlive()) {
				it.remove();
				continue;
			}
			if (owner == null || owner.level() != level) {
				criminal.discard();
				it.remove();
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.escaped", criminal.getDisplayName()).withStyle(ChatFormatting.GRAY));
				}
			}
		}
	}

	/** A criminal down: his officer is paid and the arrest counted. */
	private static void afterDeath(LivingEntity dead, DamageSource source) {
		if (!dead.entityTags().contains(WANTED_TAG) || !(dead.level() instanceof ServerLevel level)) {
			return;
		}
		Warrant warrant = null;
		for (Warrant w : WARRANTS) {
			if (w.criminal().equals(dead.getUUID())) {
				warrant = w;
			}
		}
		if (warrant == null) {
			return;
		}
		WARRANTS.remove(warrant);
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(warrant.owner());
		if (owner == null || owner.level() != level) {
			return;
		}
		Member member = owner.getAttachedOrElse(MEMBER, new Member(0, 0L));
		int was = rank(owner, member.arrests());
		Member now = new Member(member.arrests() + 1, member.fuelled());
		owner.setAttached(MEMBER, now);
		ZFighterSagas.give(owner, new ItemStack(Items.EMERALD, bounty(was)));
		int tp = (int)Math.round(BOUNTY_TP * (1.0 + RANK_PAYS * was));
		if (Powers.has(owner, ZFighterPower.INSTANCE)) {
			ZFighterTraining.grant(owner, tp);
		}
		owner.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.arrest", dead.getDisplayName(), bounty(was), now.arrests())
			.withStyle(ChatFormatting.AQUA));
		int rank = rank(owner, now.arrests());
		if (rank(now.arrests()) > rank(member.arrests()) && rank <= was) {
			// (0.18.54) the arrests are enough for the next rank; the deeds are not
			owner.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.held_back", rankName(rank(now.arrests()))).withStyle(ChatFormatting.GRAY));
		}
		if (rank > was) {
			owner.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.promoted", rankName(rank)).withStyle(ChatFormatting.GOLD));
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	// ---------------------------------------------------------------- (0.18.55) the skip writ (farworld/PatrolWrit)

	/** The rank an officer needs to be given a skip writ: Sergeant (the index into {@link #RANKS}). */
	public static final int WRIT_RANK = 2;

	/** The rank (0 Rookie to 4 Commander) of {@code player}, or -1 if he is no officer. */
	public static int officerRank(Player player) {
		Member member = player.getAttached(MEMBER);
		return member == null ? -1 : rank(member.arrests());
	}

	/** Whether a patrolman can be called down at all (they are MCA villagers, through Kingdoms). */
	public static boolean writCanLand() {
		return ZFighterNpcs.villagers() && ZFighterKingdoms.present();
	}

	/** Open ground for a called patrolman's pod near {@code player} on the far world he stands on, or null. */
	public static @Nullable BlockPos writGround(ServerLevel level, ServerPlayer player, ZFighterSpace.Kind kind) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 16; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = 12.0 + random.nextDouble() * 10.0;
			int x = Mth.floor(player.getX() + Math.cos(angle) * distance), z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			BlockPos ground = kind == ZFighterSpace.Kind.FORGE ? ZFighterSpace.forgeFloor(level, x, z) : ZFighterSpace.surface(level, x, z);
			if (ground != null) {
				return ground;
			}
		}
		return null;
	}

	/** A pod (nobody can take it) comes down over {@code ground}; {@code landed} runs when it sets down. */
	public static void writLand(ServerLevel level, BlockPos ground, java.util.function.Consumer<PodEntity> landed) {
		land(level, ground, 0, f -> landed.accept(f.pod));
		level.playSound(null, ground.getX(), ground.getY() + 20, ground.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 3.0F, 0.6F);
	}

	/** The called patrolman's pod goes up and away. */
	public static void writLeave(ServerLevel level, PodEntity pod) {
		takeOff(level, pod);
	}

	/** The called patrolman as an MCA villager (null without MCA); {@code tag} keeps him out of the visiting patrolmen's roaming. */
	public static @Nullable LivingEntity writMan(ServerLevel level, Vec3 feet, String tag, String nameKey, boolean female) {
		LivingEntity man = ZFighterNpcs.villager(level, feet, level.getRandom().nextFloat() * 360.0F, tag, nameKey, female);
		if (man != null) {
			Gifted.give(man, "zfighter", Gifted.BOLD, true, true);
		}
		return man;
	}

	// ---------------------------------------------------------------- the license

	/** The Galactic Patrol License: use it to see your rank, your arrests and any warrant you hold. */
	static final class LicenseItem extends Item {
		LicenseItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer server) {
				Member member = server.getAttached(MEMBER);
				if (member == null) {
					server.sendOverlayMessage(Component.translatable("message.jaymospowers.patrol.not_yours").withStyle(ChatFormatting.GRAY));
					return InteractionResult.SUCCESS;
				}
				Component held = Component.translatable("message.jaymospowers.patrol.no_warrant");
				for (Warrant w : WARRANTS) {
					Entity criminal = w.owner().equals(server.getUUID()) && server.level().dimension() == w.level() ? server.level().getEntity(w.criminal()) : null;
					if (criminal != null) {
						held = Component.translatable("message.jaymospowers.patrol.holding", criminal.getDisplayName(), (int)Math.round(server.distanceTo(criminal)));
					}
				}
				server.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.license", rankName(rank(server, member.arrests())), member.arrests(), held)
					.withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
