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
 * <b>The visits</b> (needs Jaymo's Kingdoms and MCA): every {@link #VISIT_DAYS} days or so a patrolman's pod comes down out of
 * the sky by a town a player is near ({@link #tick}). The patrolman ({@link #TAG}, an MCA villager through Kingdoms'
 * {@code ZFighterFolk}, in the patrol's uniform) steps out with a Z Fighter's gift ({@link Gifted}) and roams the town, going for
 * any monster or rogue fighter that comes near ({@link #patrol}); after {@link #STAY_TICKS} he walks back to his pod and lifts
 * off. His pod is only a sight (a block display): nobody can take it.
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
	/** How often a patrolman visits (days, and a spread on top), how far from the town's seat he lands, and how long he stays. */
	static final long VISIT_DAYS = 3L, VISIT_SPREAD = 24000L;
	static final int LAND_MIN = 14, LAND_MAX = 30;
	static final int STAY_TICKS = 30000;
	/** A town is visited only with a player this near its seat. */
	static final double NEAR = 160.0;
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
	/** How many ways each line is said, and how many travel stories there are. */
	static final int GREET_LINES = 3, STORIES = 8, NOT_YET_LINES = 3, OFFER_LINES = 2, REST_LINES = 2, NAMES = 10;
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

	/** A patrolman's visit: where his pod stands and when he leaves. */
	public record Visit(BlockPos pod, long leave) {
		public static final Codec<Visit> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("pod").forGetter(Visit::pod),
			Codec.LONG.fieldOf("leave").forGetter(Visit::leave)).apply(i, Visit::new));
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
	/** When the next patrolman may come (overworld game time). */
	public static final AttachmentType<Long> NEXT = AttachmentRegistry.create(JaymosPowers.id("patrol_next"),
		builder -> builder.persistent(Codec.LONG));

	public static Item LICENSE;

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
		if (ticks % 600 == 0) {
			visits(server);
		}
	}

	/** Now and then, by day, a patrolman comes down by a town someone is near. */
	private static void visits(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!ZFighterNpcs.villagers() || !ZFighterKingdoms.present() || !level.isBrightOutside()) {
			return;
		}
		long now = level.getGameTime();
		Long next = level.getAttached(NEXT);
		RandomSource random = level.getRandom();
		if (next == null) {
			level.setAttached(NEXT, now + DAY + random.nextInt((int)VISIT_SPREAD));
			return;
		}
		if (now < next) {
			return;
		}
		List<ZFighterKingdoms.Settlement> near = new ArrayList<>();
		for (ZFighterKingdoms.Settlement s : ZFighterKingdoms.settlements(server)) {
			BlockPos seat = s.seat();
			if (!"CAMP".equals(s.type()) && level.getNearestPlayer(seat.getX(), seat.getY(), seat.getZ(), NEAR, p -> !p.isSpectator()) != null) {
				near.add(s);
			}
		}
		if (near.isEmpty()) {
			return;
		}
		ZFighterKingdoms.Settlement s = near.get(random.nextInt(near.size()));
		if (arrive(level, s.seat(), random.nextInt(NAMES))) {
			level.setAttached(NEXT, now + VISIT_DAYS * DAY + random.nextInt((int)VISIT_SPREAD));
		}
	}

	/** A patrolman's pod starts down over open ground by {@code seat}; true if a spot was found. */
	static boolean arrive(ServerLevel level, BlockPos seat, int name) {
		RandomSource random = level.getRandom();
		for (int tries = 0; tries < 12; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = LAND_MIN + random.nextDouble() * (LAND_MAX - LAND_MIN);
			int x = seat.getX() + (int)Math.round(Math.cos(angle) * distance), z = seat.getZ() + (int)Math.round(Math.sin(angle) * distance);
			level.getChunk(x >> 4, z >> 4);
			BlockPos ground = ZFighterSpace.surface(level, x, z);
			if (ground == null) {
				continue;
			}
			land(level, ground, name, f -> stepOut((ServerLevel)f.pod.level(), f));
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

	/** A pod nobody can take (0.18.12: a complete pod entity, locked by {@link #POD_TAG}). */
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
	private static void stepOut(ServerLevel level, Flier f) {
		BlockPos ground = f.ground;
		Vec3 feet = new Vec3(ground.getX() + 2.5, ground.getY(), ground.getZ() + 0.5);
		LivingEntity man = ZFighterNpcs.villager(level, feet, level.getRandom().nextFloat() * 360.0F, TAG, "entity.jaymospowers.patrolman_" + f.name,
			f.name % 4 == 1);
		if (man == null) {
			f.pod.discard();
			return;
		}
		man.setAttached(VISIT, new Visit(ground, level.getGameTime() + STAY_TICKS));
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
		if (visit == null) {
			// one that came some other way: his visit starts where he stands
			visit = new Visit(man.blockPosition(), now + STAY_TICKS);
			man.setAttached(VISIT, visit);
		}
		Vec3 pod = Vec3.atBottomCenterOf(visit.pod());
		if (now >= visit.leave()) {
			if (man.distanceToSqr(pod) < 9.0 || now > visit.leave() + 1200) {
				depart(level, man, visit);
			} else {
				man.getNavigation().moveTo(pod.x, pod.y, pod.z, 0.8);
			}
			return;
		}
		LivingEntity foe = foe(level, man);
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
		if (man.getNavigation().isDone() && man.getRandom().nextInt(ROAM_EVERY / THINK_EVERY) == 0) {
			RandomSource random = man.getRandom();
			double x = pod.x + (random.nextDouble() * 2.0 - 1.0) * ROAM, z = pod.z + (random.nextDouble() * 2.0 - 1.0) * ROAM;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
			man.getNavigation().moveTo(x, y, z, 0.6);
		}
	}

	/** The nearest monster or rogue fighter within reach of him, or null. */
	private static @Nullable LivingEntity foe(ServerLevel level, PathfinderMob man) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		AABB box = man.getBoundingBox().inflate(FIGHT_RANGE);
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
		String greet = member != null ? "greet.officer" : ZFighterScouter.powerLevel(player) >= RECRUIT_POWER ? "greet.strong" : "greet";
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
				ZFighterNpcs.ask(player, man, line("offer", OFFER_LINES, man), List.of(
					ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.swear", true, () -> {
						join(player);
						ZFighterNpcs.ask(player, man, Component.translatable("talk.jaymospowers.patrol.sworn"),
							List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)));
					}),
					ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.later", true, back)));
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
			options.add(ZFighterNpcs.Option.of("talk.jaymospowers.patrol.opt.report", true, () -> ZFighterNpcs.ask(player, man,
				Component.translatable("talk.jaymospowers.patrol.report", rankName(rank(member.arrests())), member.arrests()),
				List.of(ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.back", true, back)))));
		}
		ZFighterNpcs.ask(player, man, words, options);
	}

	/** The next of his travel stories, or that he has told them all. */
	private static Component story(LivingEntity man) {
		int told = man.getAttachedOrElse(TOLD, 0);
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
		// (0.18.12) a pod is no longer an item: a complete one, full of fuel, is set down beside the new officer
		PodEntity.spawn(level, ZFighterPodTrip.roomFor(level, player.blockPosition()), true, ZFighterSpace.MAX_FUEL);
		ZFighterSagas.give(player, new ItemStack(LICENSE));
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
		int rank = rank(player.getAttached(MEMBER).arrests());
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
		int rank = rank(player.getAttached(MEMBER).arrests());
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
		int was = rank(member.arrests());
		Member now = new Member(member.arrests() + 1, member.fuelled());
		owner.setAttached(MEMBER, now);
		ZFighterSagas.give(owner, new ItemStack(Items.EMERALD, bounty(was)));
		int tp = (int)Math.round(BOUNTY_TP * (1.0 + RANK_PAYS * was));
		if (Powers.has(owner, ZFighterPower.INSTANCE)) {
			ZFighterTraining.grant(owner, tp);
		}
		owner.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.arrest", dead.getDisplayName(), bounty(was), now.arrests())
			.withStyle(ChatFormatting.AQUA));
		int rank = rank(now.arrests());
		if (rank > was) {
			owner.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.promoted", rankName(rank)).withStyle(ChatFormatting.GOLD));
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
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
				server.sendSystemMessage(Component.translatable("message.jaymospowers.patrol.license", rankName(rank(member.arrests())), member.arrests(), held)
					.withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.SUCCESS;
		}
	}
}
