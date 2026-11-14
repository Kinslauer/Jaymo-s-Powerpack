package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The new kinds of work (docs/StoryQuests-Plan.md), for the town board ({@link BoardWork}) and for a tale's steps
 * ({@link Tales}): a <b>pack</b> to kill where it was seen; an <b>elite</b>, one named foe tougher than its kind; a town
 * to <b>defend</b> through waves after dark; someone to walk overland (<b>trek</b>); an <b>incursion</b> (a nest, a camp,
 * a pit of the dead) to clear; <b>lamps</b> to set in a town's dark corners before nightfall; something to <b>build</b>
 * at a marked spot; a cave-in to <b>blast</b>; goods to <b>stash</b> in a chest out in the wild.
 *
 * <p>Each is an ordinary {@link Quest} (kinds {@link Quest#PACK} to {@link Quest#STASH}) looked at once a second while
 * its player is in the world. Board work is done when its count is made up, and is brought back to whoever posted it;
 * a tale's step tells the tale ({@link Tales#jobDone}, {@link Tales#jobFailed}). How hard it is, one to five stars,
 * comes from the notice ({@code work.<kind>.<stars>}) or the tale's step: more foes, a leader, armour, potions.
 * What a job needs to remember beyond its count is kept in its objective's {@code otherName}.
 */
public final class QuestJobs {
	// ================================================================================================================
	// Tuning
	/** How often (ticks) each player's jobs are looked at; how near (blocks) its place a job is set out, and how far around it its foes are counted. */
	static final int WATCH_TICKS = 20;
	static final double SET_OUT = 56.0, AROUND = 80.0;
	/** A foe's health grows by this share for each star above one; a leader has this many times a follower's. */
	static final double STAR_HEALTH = 0.2, LEADER_HEALTH = 1.8;
	/** From this many stars the pack has a leader and its people wear iron; from this many they've drunk something; at this many, two things. */
	static final int LEADER_STARS = 3, POTION_STARS = 4, TWO_POTION_STARS = 5;
	/** An elite's health: this, and this more for each star. */
	static final double ELITE_HEALTH = 40.0, ELITE_STAR_HEALTH = 30.0;
	/** An elite speaks when its hunter is this near; one who flees it this far, or falls to it, is marked ({@link Nemesis}). */
	static final double ELITE_SPEAKS = 16.0, ELITE_FLED = 72.0;
	/** Defending: the watch begins with the player this near the town after dark and is lost this far from it; waves come from this far out. */
	static final double DEFEND_NEAR = 64.0, DEFEND_LOST = 110.0;
	static final int WAVE_NEAR = 20, WAVE_FAR = 30;
	/** A wave is this many, and one more for each star and each wave before it; it's over when they're dead or after this long (ticks); the next comes this long after. */
	static final int WAVE_BASE = 3, WAVE_TICKS = 20 * 150, WAVE_REST = 20 * 12;
	/** An escort: its charge has arrived this near the end; each second on the open road there's this chance of an ambush (once), of this many and one a star. */
	static final double TREK_ARRIVED = 12.0, AMBUSH_CHANCE = 1.0 / 120.0;
	static final int AMBUSH_BASE = 2, AMBUSH_TICKS = 20 * 120;
	/** A nest is this many webs within this many blocks; it's burnt out when none are left. */
	static final int NEST_WEBS = 8, NEST_RADIUS = 5;
	/** Lamps: dark spots are looked for this far from the town's middle, this far apart; dark is this block light or less, lit this or more. */
	static final int LAMP_RADIUS = 36, LAMP_APART = 8, LAMP_DARK = 3, LAMP_LIT = 8;
	/** Lamps are due by this time of day (ticks into the day): nightfall. */
	static final long NIGHTFALL = 13000L;
	/** Building: blocks placed within this many blocks of the mark count (this far up and down). */
	static final int BUILD_RADIUS = 6, BUILD_HEIGHT = 4;
	/** A cave-in is cleared when this share of its rubble is gone, after a blast within this many blocks of it. */
	static final double BLAST_CLEARED = 0.75, BLAST_RANGE = 8.0;
	/** Board work failed costs this much of the town's good name. */
	static final int FAIL_REPUTATION = 5;

	/** On a job's foe: whose job, which quest, and whether it's the leader (or the elite). */
	record Mark(UUID owner, String quest, boolean leader) {
		static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Mark::owner),
				Codec.STRING.fieldOf("quest").forGetter(Mark::quest),
				Codec.BOOL.optionalFieldOf("leader", false).forGetter(Mark::leader))
			.apply(i, Mark::new));
	}

	static final AttachmentType<Mark> JOB = AttachmentRegistry.create(JaymosKingdoms.id("quest_job"), builder -> builder.persistent(Mark.CODEC));

	/** Defending: each quest's wave under way (the tick it came), or the tick the last one ended (negative). */
	private static final Map<String, Integer> WAVES = new HashMap<>();
	/** Elites that have spoken, and players already marked by one. */
	private static final Set<String> SPOKEN = new HashSet<>(), MET = new HashSet<>();

	private QuestJobs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(QuestJobs::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(QuestJobs::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAVES.clear();
			SPOKEN.clear();
			MET.clear();
		});
	}

	// ---- what a job is called ---------------------------------------------------------------------------------------------

	/** A job kind's name in keys and notices. */
	static String name(int kind) {
		return switch (kind) {
			case Quest.PACK -> "pack";
			case Quest.ELITE -> "elite";
			case Quest.DEFEND -> "defend";
			case Quest.TREK -> "trek";
			case Quest.INCURSION -> "incursion";
			case Quest.LAMPS -> "lamps";
			case Quest.BUILD -> "build";
			case Quest.BLAST -> "blast";
			default -> "stash";
		};
	}

	/** How hard {@code q} is, one to five stars: from its notice, or its tale's step. */
	static int stars(ServerPlayer player, Quest q) {
		if (Tales.ofTale(q)) {
			return Tales.stars(player, q);
		}
		return BoardWork.stars(q.template());
	}

	private static Component thing(String target) {
		if (target.startsWith("#")) {
			Identifier id = Identifier.tryParse(target.substring(1));
			return Component.translatable("work.jaymoskingdoms.tag." + (id == null ? "any" : id.getPath()));
		}
		Identifier id = Identifier.tryParse(target);
		if (id == null) {
			return Component.literal(target);
		}
		Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
		if (type.isPresent()) {
			return type.get().getDescription();
		}
		Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isPresent() && block.get() != Blocks.AIR) {
			return block.get().getName();
		}
		return BuiltInRegistries.ITEM.getOptional(id).map(i -> new ItemStack(i).getHoverName()).orElse(Component.literal(target));
	}

	private static String variant(Quest q) {
		String name = q.objective().otherName();
		int bar = name.indexOf('|');
		return bar < 0 ? name : name.substring(0, bar);
	}

	private static String x(Quest q) {
		return q.objective().pos().map(p -> Integer.toString(p.getX())).orElse("?");
	}

	private static String z(Quest q) {
		return q.objective().pos().map(p -> Integer.toString(p.getZ())).orElse("?");
	}

	static Component title(Quest q) {
		return Component.translatable("work.jaymoskingdoms." + name(q.kind()) + ".title", "★".repeat(BoardWork.stars(q.template())));
	}

	static Component text(Quest q) {
		Quest.Objective o = q.objective();
		String key = "work.jaymoskingdoms." + name(q.kind()) + (q.kind() == Quest.INCURSION || q.kind() == Quest.STASH ? "." + variant(q) : "") + ".text";
		return Component.translatable(key, o.count(), thing(o.target()), o.place(), x(q), z(q), q.kind() == Quest.ELITE || q.kind() == Quest.TREK ? o.otherName() : "");
	}

	static Component objective(ServerLevel level, ServerPlayer player, Quest q) {
		Quest.Objective o = q.objective();
		String key = "work.jaymoskingdoms." + name(q.kind()) + ".objective";
		if (q.kind() == Quest.INCURSION && variant(q).equals("nest") && o.progress() >= o.count() - 1) {
			key = "work.jaymoskingdoms.incursion.burn";
		} else if (q.kind() == Quest.DEFEND && !o.spawned()) {
			key = "work.jaymoskingdoms.defend.wait";
		}
		return Component.translatable(key, o.progress(), o.count(), thing(o.target()), o.place(), x(q), z(q),
			q.kind() == Quest.ELITE || q.kind() == Quest.TREK ? o.otherName() : "");
	}

	// ---- making and ending ----------------------------------------------------------------------------------------------------

	private static Quest with(Quest q, int progress, String otherName, boolean spawned, Optional<BlockPos> pos, Optional<UUID> other) {
		Quest.Objective o = q.objective();
		return q.withObjective(new Quest.Objective(o.target(), o.count(), Math.min(progress, o.count()), pos, other, otherName, o.place(), spawned));
	}

	private static Quest progress(Quest q, int progress) {
		return with(q, progress, q.objective().otherName(), q.objective().spawned(), q.objective().pos(), q.objective().other());
	}

	private static Quest noted(Quest q, String otherName, boolean spawned) {
		return with(q, q.objective().progress(), otherName, spawned, q.objective().pos(), q.objective().other());
	}

	private static void save(ServerPlayer player, Quest q) {
		Quests.put(player, q);
		Quests.sync(player, true);
	}

	/** {@code q}'s count is made up: a tale goes on; board work is to be brought back to whoever posted it. */
	private static void done(ServerLevel level, ServerPlayer player, Quest q) {
		level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.8F);
		if (Tales.ofTale(q)) {
			Tales.jobDone(player, q);
		} else {
			player.sendOverlayMessage(Quests.objective(level, player, q).copy().withStyle(ChatFormatting.GREEN));
		}
	}

	/** {@code q} can't be done any more ({@code why}: a key under {@code work.jaymoskingdoms.failed.}). */
	private static void fail(ServerLevel level, ServerPlayer player, Quest q, String why) {
		player.sendOverlayMessage(Component.translatable("work.jaymoskingdoms.failed." + why).withStyle(ChatFormatting.RED));
		if (Tales.ofTale(q)) {
			over(level, player, q);
			Tales.jobFailed(player, q);
			return;
		}
		over(level, player, q);
		Quests.drop(player, q.id());
		QuestPast.add(player, Quests.title(q), QuestPast.FAILED, Component.translatable("work.jaymoskingdoms.failed." + why));
		Faction f = Factions.get(level.getServer(), q.giver().faction());
		if (f != null) {
			Reputation.change(player, f, -FAIL_REPUTATION);
		}
		Quests.sync(player, true);
	}

	private static List<Mob> foes(ServerLevel level, BlockPos at, String quest) {
		return level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at).inflate(AROUND), m -> {
			Mark mark = m.getAttached(JOB);
			return m.isAlive() && mark != null && mark.quest().equals(quest);
		});
	}

	/** {@code q} is off {@code player}'s log (done, failed or dropped): what it put in the world is cleared away. */
	static void over(ServerLevel level, ServerPlayer player, Quest q) {
		WAVES.remove(q.id());
		SPOKEN.remove(q.id());
		BlockPos at = q.objective().pos().orElse(player.blockPosition());
		for (Mob mob : foes(level, at, q.id())) {
			mob.discard();
		}
		if (q.kind() == Quest.TREK && !Tales.ofTale(q) && q.objective().other().isPresent()
			&& level.getEntity(q.objective().other().get()) instanceof Villager charge && charge.isAlive()) {
			// (a board's traveller goes their own way)
			McaCompat.stopFollowing(charge);
			TaleCast.Cast c = charge.getAttached(TaleCast.CAST);
			if (c != null) {
				charge.setAttached(TaleCast.CAST, c.done(level.getGameTime()));
			}
		}
	}

	// ---- foes -------------------------------------------------------------------------------------------------------------------

	private static void attribute(Mob mob, Holder<Attribute> attribute, double value) {
		AttributeInstance instance = mob.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	private static EntityType<?> type(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? EntityTypes.ZOMBIE : BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(EntityTypes.ZOMBIE);
	}

	private static BlockPos ground(ServerLevel level, BlockPos near, int spread) {
		RandomSource random = level.getRandom();
		BlockPos at = near.offset(spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread, 0, spread == 0 ? 0 : random.nextInt(spread * 2 + 1) - spread);
		return Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at), 3);
	}

	/** One of a job's foes at {@code at}: made tougher by the job's {@code stars}, the {@code leader} more so and named. */
	private static @Nullable Mob foe(ServerLevel level, ServerPlayer owner, Quest q, EntityType<?> type, BlockPos at, int stars, boolean leader, @Nullable Component name) {
		if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
			raider.setPatrolLeader(false);
		}
		double health = q.kind() == Quest.ELITE ? ELITE_HEALTH + ELITE_STAR_HEALTH * stars
			: mob.getMaxHealth() * (1.0 + STAR_HEALTH * (stars - 1)) * (leader ? LEADER_HEALTH : 1.0);
		attribute(mob, Attributes.MAX_HEALTH, health);
		mob.setHealth(mob.getMaxHealth());
		attribute(mob, Attributes.FOLLOW_RANGE, 40.0);
		if (stars >= LEADER_STARS && Temperaments.humanoid(mob)) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			mob.setDropChance(EquipmentSlot.HEAD, 0.0F);
			if (leader || stars >= POTION_STARS) {
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.setDropChance(EquipmentSlot.CHEST, 0.0F);
			}
		}
		if (stars >= POTION_STARS) {
			mob.addEffect(new MobEffectInstance(MobEffects.SPEED, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
		if (stars >= TWO_POTION_STARS || leader && stars >= POTION_STARS) {
			mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, MobEffectInstance.INFINITE_DURATION, 0, false, false));
		}
		if (name != null) {
			mob.setCustomName(name);
			mob.setCustomNameVisible(true);
		}
		mob.setAttached(JOB, new Mark(owner.getUUID(), q.id(), leader));
		level.addFreshEntity(mob);
		return mob;
	}

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Mark mark = entity.getAttached(JOB);
		if (mark != null) {
			ServerPlayer owner = server.getPlayerList().getPlayer(mark.owner());
			Quest q = owner == null ? null : Quests.quest(owner, mark.quest());
			if (q != null && !q.counted() && q.kind() != Quest.DEFEND) {
				// (a nest's last count is its burning, not a kill)
				int most = q.kind() == Quest.INCURSION && variant(q).equals("nest") ? q.objective().count() - 1 : q.objective().count();
				Quest next = progress(q, q.kind() == Quest.ELITE ? most : Math.min(most, q.objective().progress() + 1));
				save(owner, next);
				owner.sendOverlayMessage(Quests.objective(level, owner, next).copy().withStyle(next.counted() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
				if (next.counted()) {
					done(level, owner, next);
				}
			}
		}
		// the one a watch was kept for, or the one being walked somewhere
		UUID id = entity.getUUID();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			for (Quest q : new ArrayList<>(Quests.log(p))) {
				if ((q.kind() == Quest.DEFEND && q.objective().spawned() || q.kind() == Quest.TREK) && !q.counted()
					&& q.objective().other().map(id::equals).orElse(false)) {
					fail(level, p, q, q.kind() == Quest.DEFEND ? "fallen" : "charge");
				}
			}
		}
		if (entity instanceof ServerPlayer fallen) {
			for (Quest q : Quests.log(fallen)) {
				if (q.kind() == Quest.ELITE && q.objective().spawned() && !q.counted() && SPOKEN.contains(q.id())) {
					met(fallen, q);
				}
			}
		}
	}

	/** {@code player} has met {@code q}'s elite and come off worse: it won't forget them ({@link Nemesis}: hunters are sent). */
	private static void met(ServerPlayer player, Quest q) {
		if (MET.add(q.id() + player.getUUID())) {
			Nemesis.mark(player);
		}
	}

	// ---- the watch ----------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		int tick = server.getTickCount();
		if (tick % WATCH_TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (ServerPlayer player : new ArrayList<>(level.players())) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			for (Quest q : new ArrayList<>(Quests.log(player))) {
				if (q.kind() >= Quest.PACK && q.kind() <= Quest.STASH && !q.counted() && q.objective().pos().isPresent()) {
					watch(level, player, q, tick);
				}
			}
		}
	}

	private static double flat(Entity e, BlockPos at) {
		double dx = at.getX() + 0.5 - e.getX(), dz = at.getZ() + 0.5 - e.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static void watch(ServerLevel level, ServerPlayer player, Quest q, int tick) {
		BlockPos at = q.objective().pos().get();
		double far = flat(player, at);
		if (q.kind() == Quest.TREK) {
			trek(level, player, q, at);
			return;
		}
		if (q.kind() == Quest.DEFEND) {
			defend(level, player, q, at, far, tick);
			return;
		}
		if (far > SET_OUT || !level.isLoaded(at) || !level.isPositionEntityTicking(at)) {
			if (q.kind() == Quest.ELITE && q.objective().spawned() && SPOKEN.contains(q.id()) && far > ELITE_FLED) {
				met(player, q);
			}
			return;
		}
		int stars = stars(player, q);
		switch (q.kind()) {
			case Quest.PACK, Quest.INCURSION -> pack(level, player, q, at, stars);
			case Quest.ELITE -> elite(level, player, q, at, stars);
			case Quest.LAMPS -> lamps(level, player, q, at, tick);
			case Quest.BUILD -> build(level, player, q, at, tick);
			case Quest.BLAST -> blast(level, player, q, at);
			case Quest.STASH -> stash(level, player, q, at);
			default -> {
			}
		}
	}

	/** A pack, or an incursion's dwellers: as many as are still to kill are there (set out the first time, with their leader and their den). */
	private static void pack(ServerLevel level, ServerPlayer player, Quest q, BlockPos at, int stars) {
		Quest.Objective o = q.objective();
		boolean nest = q.kind() == Quest.INCURSION && variant(q).equals("nest");
		int kills = nest ? o.count() - 1 : o.count();
		if (!o.spawned() && q.kind() == Quest.INCURSION) {
			den(level, ground(level, at, 0), variant(q));
		}
		if (nest && o.progress() >= kills) {
			// (all dead: the webs have to go, by fire or by hand)
			BlockPos site = ground(level, at, 0);
			boolean webs = false;
			for (BlockPos p : BlockPos.betweenClosed(site.offset(-NEST_RADIUS, -2, -NEST_RADIUS), site.offset(NEST_RADIUS, 4, NEST_RADIUS))) {
				webs |= level.getBlockState(p).is(Blocks.COBWEB);
			}
			if (!webs) {
				Quest next = progress(q, o.count());
				save(player, next);
				done(level, player, next);
			}
			return;
		}
		List<Mob> alive = foes(level, at, q.id());
		int missing = kills - o.progress() - alive.size();
		EntityType<?> type = type(o.target());
		for (int i = 0; i < missing; i++) {
			boolean leader = !o.spawned() && i == 0 && stars >= LEADER_STARS;
			foe(level, player, q, type, ground(level, at, 6), stars, leader, leader ? Component.translatable("work.jaymoskingdoms.leader", type.getDescription()) : null);
		}
		if (!o.spawned()) {
			save(player, noted(q, o.otherName(), true));
		}
		// (beasts that wouldn't start it themselves know who's come for them)
		for (Mob mob : alive) {
			if (!(mob instanceof Enemy) && mob.getTarget() == null && mob.distanceToSqr(player) < 24.0 * 24.0 && !player.isCreative() && !Downed.isDowned(player)) {
				mob.setTarget(player);
			}
		}
	}

	/** An incursion's den: a nest's webs, a camp's fire, a pit's soured ground. Only into air and plants. */
	private static void den(ServerLevel level, BlockPos site, String variant) {
		RandomSource random = level.getRandom();
		if (variant.equals("camp")) {
			if (level.getBlockState(site).canBeReplaced()) {
				level.setBlockAndUpdate(site, Blocks.CAMPFIRE.defaultBlockState());
			}
			return;
		}
		for (int i = 0; i < NEST_WEBS; i++) {
			BlockPos p = Construction.standable(level, site.offset(random.nextInt(NEST_RADIUS * 2 - 1) - NEST_RADIUS + 1, 0, random.nextInt(NEST_RADIUS * 2 - 1) - NEST_RADIUS + 1), 2);
			if (variant.equals("nest")) {
				BlockPos web = random.nextBoolean() ? p : p.above();
				if (level.getBlockState(web).canBeReplaced()) {
					level.setBlockAndUpdate(web, Blocks.COBWEB.defaultBlockState());
				}
			} else if (level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT)) {
				level.setBlockAndUpdate(p.below(), i % 3 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState());
			}
		}
	}

	/** An elite: there when its hunter comes near (again, if it was lost), and it has something to say. */
	private static void elite(ServerLevel level, ServerPlayer player, Quest q, BlockPos at, int stars) {
		Quest.Objective o = q.objective();
		Mob elite = o.other().isPresent() && level.getEntity(o.other().get()) instanceof Mob m && m.isAlive() ? m : null;
		if (elite == null) {
			elite = foe(level, player, q, type(o.target()), ground(level, at, 3), stars, true, Component.literal(o.otherName()));
			if (elite != null) {
				save(player, with(q, o.progress(), o.otherName(), true, o.pos(), Optional.of(elite.getUUID())));
			}
			return;
		}
		if (elite.distanceToSqr(player) < ELITE_SPEAKS * ELITE_SPEAKS && SPOKEN.add(q.id())) {
			Speech.tell(level, elite, player, Component.translatable("work.jaymoskingdoms.elite.taunt." + level.getRandom().nextInt(4), Post.nameOf(player)), Speech.RED);
		} else if (SPOKEN.contains(q.id()) && elite.getHealth() < elite.getMaxHealth() * 0.5F && SPOKEN.add(q.id() + "/half")) {
			Speech.tell(level, elite, player, Component.translatable("work.jaymoskingdoms.elite.hurt." + level.getRandom().nextInt(3)), Speech.RED);
		}
		if (Downed.isDowned(player) && SPOKEN.contains(q.id())) {
			met(player, q);
		}
	}

	/** A town to hold: once it's dark and the player is there, the waves come, one after another. */
	private static void defend(ServerLevel level, ServerPlayer player, Quest q, BlockPos at, double far, int tick) {
		Quest.Objective o = q.objective();
		if (!o.spawned()) {
			if (level.isDarkOutside() && far <= DEFEND_NEAR && level.isLoaded(at)) {
				save(player, noted(q, o.otherName(), true));
				WAVES.put(q.id(), -(tick - WAVE_REST));
			}
			return;
		}
		if (far > DEFEND_LOST) {
			fail(level, player, q, "left");
			return;
		}
		int stars = stars(player, q);
		List<Mob> alive = foes(level, at, q.id());
		int wave = WAVES.getOrDefault(q.id(), -(tick - WAVE_REST));
		if (wave < 0) {
			// between waves (or picked up again after a restart): the next comes once the last are dead and the rest is over
			if (alive.isEmpty() && tick + wave >= WAVE_REST) {
				int n = WAVE_BASE + stars + o.progress();
				EntityType<?> type = type(o.target());
				for (int i = 0; i < n; i++) {
					double a = level.getRandom().nextDouble() * Math.PI * 2.0;
					int d = WAVE_NEAR + level.getRandom().nextInt(WAVE_FAR - WAVE_NEAR + 1);
					Mob mob = foe(level, player, q, type, ground(level, at.offset((int)(Math.cos(a) * d), 0, (int)(Math.sin(a) * d)), 0), stars, false, null);
					if (mob != null) {
						mob.setTarget(player);
						mob.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
					}
				}
				WAVES.put(q.id(), tick);
				level.playSound(null, at, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 3.0F, 0.8F);
				player.sendOverlayMessage(Component.translatable("work.jaymoskingdoms.defend.wave", o.progress() + 1, o.count()).withStyle(ChatFormatting.RED));
			}
			return;
		}
		if (alive.isEmpty() || tick - wave > WAVE_TICKS) {
			for (Mob mob : alive) {
				mob.discard();
			}
			WAVES.put(q.id(), -tick);
			Quest next = progress(q, o.progress() + 1);
			save(player, next);
			if (next.counted()) {
				WAVES.remove(q.id());
				done(level, player, next);
			}
		}
	}

	/** An escort: its charge keeps with the player (into the cart, if they ride one with room), may be waylaid once, and arrives. */
	private static void trek(ServerLevel level, ServerPlayer player, Quest q, BlockPos at) {
		Quest.Objective o = q.objective();
		if (o.other().isEmpty() || !(level.getEntity(o.other().get()) instanceof Mob charge) || !charge.isAlive()) {
			return;
		}
		charge.setPermanentlyInvulnerable(false);
		TaleCast.moved(charge);
		if (flat(charge, at) <= TREK_ARRIVED) {
			if (charge.getVehicle() != null) {
				charge.stopRiding();
			}
			if (charge instanceof Villager v) {
				McaCompat.stopFollowing(v);
			}
			Speech.tell(level, charge, player, Component.translatable("work.jaymoskingdoms.trek.arrived." + level.getRandom().nextInt(3)), Speech.GOLD);
			Quest next = progress(q, o.count());
			save(player, next);
			done(level, player, next);
			return;
		}
		double apart = charge.distanceTo(player);
		Entity ride = player.getVehicle() == null ? null : player.getRootVehicle();
		if (ride != null && charge.getVehicle() == null && apart < 6.0) {
			charge.startRiding(ride);
		} else if (ride == null && charge.getVehicle() != null) {
			charge.stopRiding();
		}
		if (charge.getVehicle() == null && apart > 4.0 && apart < 64.0) {
			if (!(charge instanceof Villager v) || !McaCompat.follow(v, player)) {
				charge.getNavigation().moveTo(player, 1.0);
			}
		}
		// waylaid, once, out on the open road
		if (!o.spawned() && apart < 16.0 && flat(charge, at) > 60.0 && level.getRandom().nextDouble() < AMBUSH_CHANCE
			&& Factions.around(level.getServer(), level, player.blockPosition(), 64.0) == null) {
			int stars = stars(player, q);
			List<Mob> band = Encounters.send(level, player, EntityTypes.PILLAGER, AMBUSH_BASE + stars, AMBUSH_TICKS);
			if (!band.isEmpty()) {
				Speech.tell(level, charge, player, Component.translatable("work.jaymoskingdoms.trek.ambush"), Speech.RED);
			}
			save(player, noted(q, o.otherName(), true));
		}
	}

	// ---- lamps ----------------------------------------------------------------------------------------------------------------------

	private static List<BlockPos> spots(String note) {
		List<BlockPos> out = new ArrayList<>();
		int bar = note.indexOf('|');
		if (bar < 0) {
			return out;
		}
		for (String s : note.substring(bar + 1).split(";")) {
			String[] xyz = s.split(",");
			if (xyz.length == 3) {
				try {
					out.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
				} catch (NumberFormatException ignored) {
					// (not a spot)
				}
			}
		}
		return out;
	}

	private static boolean lit(ServerLevel level, BlockPos spot) {
		return level.getBrightness(LightLayer.BLOCK, spot) >= LAMP_LIT || level.getBrightness(LightLayer.BLOCK, spot.above()) >= LAMP_LIT;
	}

	/** Lamps: the town's dark spots are picked (on its own land, so its people keep the lights after), shown, and counted as they're lit; due by nightfall. */
	private static void lamps(ServerLevel level, ServerPlayer player, Quest q, BlockPos at, int tick) {
		Quest.Objective o = q.objective();
		long clock = level.getOverworldClockTime();
		long day = clock / 24000L, time = clock % 24000L;
		if (!o.spawned()) {
			RandomSource random = level.getRandom();
			List<BlockPos> picked = new ArrayList<>();
			for (int pass = 0; pass < 2 && picked.size() < o.count(); pass++) {
				for (int i = 0; i < 160 && picked.size() < o.count(); i++) {
					BlockPos p = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						at.offset(random.nextInt(LAMP_RADIUS * 2 + 1) - LAMP_RADIUS, 0, random.nextInt(LAMP_RADIUS * 2 + 1) - LAMP_RADIUS)), 2);
					boolean apart = true;
					for (BlockPos other : picked) {
						apart &= other.distSqr(p) >= LAMP_APART * LAMP_APART;
					}
					// (first the town's own ground, where lights are kept; then anywhere about it)
					if (apart && level.isLoaded(p) && level.getBrightness(LightLayer.BLOCK, p) <= LAMP_DARK && level.getFluidState(p).isEmpty()
						&& (pass == 1 || Lamplight.kept(level, p))) {
						picked.add(p.immutable());
					}
				}
			}
			StringBuilder note = new StringBuilder(Long.toString(time < NIGHTFALL - 2000L ? day : day + 1)).append('|');
			for (BlockPos p : picked) {
				note.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append(';');
			}
			Quest next = q.withObjective(new Quest.Objective(o.target(), Math.max(1, picked.size()), picked.isEmpty() ? 1 : 0, o.pos(), o.other(), note.toString(), o.place(), true));
			save(player, next);
			if (next.counted()) {
				done(level, player, next);
			}
			return;
		}
		List<BlockPos> spots = spots(o.otherName());
		int lit = 0;
		for (BlockPos spot : spots) {
			if (lit(level, spot)) {
				lit++;
			} else if (tick % 40 == 0) {
				level.sendParticles(player, ParticleTypes.END_ROD, true, false, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, 3, 0.1, 0.5, 0.1, 0.0);
			}
		}
		if (lit != o.progress()) {
			Quest next = progress(q, lit);
			save(player, next);
			if (next.counted()) {
				done(level, player, next);
				return;
			}
		}
		long due = 0L;
		try {
			due = Long.parseLong(o.otherName().substring(0, Math.max(0, o.otherName().indexOf('|'))));
		} catch (NumberFormatException ignored) {
			// (no day kept: never late)
		}
		if (due > 0L && (day > due || day == due && time >= NIGHTFALL)) {
			fail(level, player, q, "dark");
		}
	}

	// ---- building, blasting, stashing -----------------------------------------------------------------------------------------------------

	private static boolean matches(BlockState state, String target) {
		if (target.startsWith("#")) {
			Identifier id = Identifier.tryParse(target.substring(1));
			return id != null && state.is(TagKey.create(Registries.BLOCK, id));
		}
		return target.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	/** Building: the blocks of the kind asked for standing by the mark, over what stood there when the job was first looked at. */
	private static void build(ServerLevel level, ServerPlayer player, Quest q, BlockPos at, int tick) {
		Quest.Objective o = q.objective();
		BlockPos site = o.spawned() ? at : ground(level, at, 0);
		int there = 0;
		for (BlockPos p : BlockPos.betweenClosed(site.offset(-BUILD_RADIUS, -BUILD_HEIGHT, -BUILD_RADIUS), site.offset(BUILD_RADIUS, BUILD_HEIGHT, BUILD_RADIUS))) {
			if (matches(level.getBlockState(p), o.target())) {
				there++;
			}
		}
		if (!o.spawned()) {
			save(player, with(q, 0, Integer.toString(there), true, Optional.of(site), o.other()));
			return;
		}
		if (tick % 40 == 0) {
			for (int i = 0; i < 4; i++) {
				level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, site.getX() + 0.5 + (i % 2 == 0 ? -BUILD_RADIUS : BUILD_RADIUS), site.getY() + 0.5,
					site.getZ() + 0.5 + (i < 2 ? -BUILD_RADIUS : BUILD_RADIUS), 4, 0.1, 0.6, 0.1, 0.0);
			}
		}
		int base = 0;
		try {
			base = Integer.parseInt(o.otherName());
		} catch (NumberFormatException ignored) {
			// (nothing stood there)
		}
		int built = Math.max(0, there - base);
		if (built != o.progress()) {
			Quest next = progress(q, built);
			save(player, next);
			if (next.counted()) {
				done(level, player, next);
			}
		}
	}

	/** A cave-in's rubble, the same heap for the same spot: three wide, three long, piled toward the middle. */
	private static List<BlockPos> rubble(BlockPos site) {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int high = dx == 0 && dz == 0 ? 3 : dx == 0 || dz == 0 ? 2 : 1;
				for (int dy = 0; dy < high; dy++) {
					out.add(site.offset(dx, dy, dz));
				}
			}
		}
		return out;
	}

	private static boolean rubbleBlock(BlockState state) {
		return state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.GRAVEL);
	}

	/** ({@code ServerExplosionNoBuildMixin}) A blast at {@code centre} is breaking blocks: any cave-in job it reaches has been blasted. */
	public static void blasted(ServerLevel level, BlockPos centre) {
		if (level != Factions.level(level.getServer())) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			for (Quest q : new ArrayList<>(Quests.log(p))) {
				if (q.kind() == Quest.BLAST && q.objective().spawned() && q.objective().otherName().isEmpty()
					&& q.objective().pos().map(at -> at.distSqr(centre) <= BLAST_RANGE * BLAST_RANGE).orElse(false)) {
					Quests.put(p, noted(q, "blasted", true));
				}
			}
		}
	}

	/** A cave-in: the rubble is laid when someone comes; it's cleared once it's been blasted and most of it is gone (dug out by hand, more comes down). */
	private static void blast(ServerLevel level, ServerPlayer player, Quest q, BlockPos at) {
		Quest.Objective o = q.objective();
		BlockPos site = o.spawned() ? at : ground(level, at, 0);
		List<BlockPos> heap = rubble(site);
		if (!o.spawned()) {
			lay(level, heap);
			save(player, with(q, 0, "", true, Optional.of(site), o.other()));
			return;
		}
		int left = 0;
		for (BlockPos p : heap) {
			left += rubbleBlock(level.getBlockState(p)) ? 1 : 0;
		}
		boolean blasted = o.otherName().equals("blasted");
		if (blasted && left <= heap.size() * (1.0 - BLAST_CLEARED)) {
			Quest next = progress(q, o.count());
			save(player, next);
			done(level, player, next);
		} else if (!blasted && left == 0) {
			lay(level, heap);
			player.sendOverlayMessage(Component.translatable("work.jaymoskingdoms.blast.more").withStyle(ChatFormatting.YELLOW));
		}
	}

	private static void lay(ServerLevel level, List<BlockPos> heap) {
		RandomSource random = level.getRandom();
		for (BlockPos p : heap) {
			if (level.getBlockState(p).canBeReplaced()) {
				level.setBlockAndUpdate(p, (random.nextInt(4) == 0 ? Blocks.GRAVEL : random.nextBoolean() ? Blocks.COBBLED_DEEPSLATE : Blocks.COBBLESTONE).defaultBlockState());
			}
		}
	}

	/** A stash: a chest stands at the place (set there when someone comes), and what's asked for is taken out of it once it's all in. */
	private static void stash(ServerLevel level, ServerPlayer player, Quest q, BlockPos at) {
		Quest.Objective o = q.objective();
		BlockPos site = o.spawned() ? at : ground(level, at, 0);
		if (!(level.getBlockEntity(site) instanceof Container)) {
			if (level.getBlockState(site).canBeReplaced()) {
				level.setBlockAndUpdate(site, Blocks.CHEST.defaultBlockState());
			}
			if (!o.spawned()) {
				save(player, with(q, 0, o.otherName(), true, Optional.of(site), o.other()));
			}
			return;
		}
		Container chest = (Container)level.getBlockEntity(site);
		Identifier id = Identifier.tryParse(Quests.itemId(o.target()));
		Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
		if (item == null) {
			return;
		}
		int have = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			have += chest.getItem(i).is(item) ? chest.getItem(i).getCount() : 0;
		}
		if (have < o.count()) {
			if (Math.min(have, o.count() - 1) != o.progress()) {
				save(player, progress(q, Math.min(have, o.count() - 1)));
			}
			return;
		}
		int take = o.count();
		for (int i = 0; i < chest.getContainerSize() && take > 0; i++) {
			ItemStack s = chest.getItem(i);
			if (s.is(item)) {
				int n = Math.min(take, s.getCount());
				s.shrink(n);
				take -= n;
			}
		}
		chest.setChanged();
		Quest next = progress(q, o.count());
		save(player, next);
		done(level, player, next);
	}
}
