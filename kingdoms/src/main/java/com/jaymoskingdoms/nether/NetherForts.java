package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Kingdoms 0.7.142) The Nether faction, stage 2: war forts and their warlords. Near players in the Nether, now and then
 * a pair of rivals rises out of sight: a Hornkin war fort (in the wastes or a crimson forest) and, a little way off, a
 * piglin war camp, gates facing ({@link FortBuildings}). The fort holds the <b>Hornkin Warlord</b> ({@link
 * Hornkin.Kind#WARLORD}: huge, a red boss bar, a horn charge that breaks shields, a ground stamp, a rally roar that calls
 * more Hornkin); the camp the <b>Piglin Warboss</b> (a piglin brute made big and strong, a gold boss bar, a leaping slam
 * and a war horn that rallies his piglins and calls two more). Each side sends war hosts at the other's walls every few
 * minutes while a player is near, and they fight wherever they meet; both turn on any player close by. Each fort
 * recruits slowly. Kill a warlord and his side breaks: every one of them flees from players and is gone, the walls and
 * the hoard left standing. Ops: {@code /kingdom netherfort found | host | info}.
 */
public final class NetherForts {
	/** The two sides. */
	public static final int HORNKIN = 0, PIGLIN = 1;
	/** What a fort's member is to it. */
	public static final int GARRISON = 0, HOST = 1, WARLORD = 2;

	/** How often a new pair may rise near each player in the Nether (ticks), and the chance it does. */
	static final int FOUND_EVERY = 20 * 60 * 2;
	static final float FOUND_CHANCE = 0.3F;
	/** No new pair while a standing fort is this near the player; every fort keeps this far from any other pair's (blocks). */
	static final double FORT_AREA = 320.0, FORT_SPACING = 240.0;
	/** A Hornkin fort rises this far from the player (out of sight: no player within {@link #HIDE_RANGE}), its rival this far from it. */
	static final int SITE_MIN = 72, SITE_MAX = 140, SITE_TRIES = 12, PAIR_MIN = 56, PAIR_MAX = 88;
	static final double HIDE_RANGE = 48.0;
	/** The most the ground may rise and fall across a site; it's looked for between these heights. */
	static final int MAX_SPREAD = 4, LOW = 33, HIGH = 116, HEADROOM = 6;
	/** How far around a chosen spot to look for fit ground (the Nether is rarely level), in steps of SEARCH_STEP. */
	static final int SEARCH_RADIUS = 32, SEARCH_STEP = 4;
	/** A war host goes out about this often from each standing fort (ticks), while a player is this near either fort. */
	static final int HOST_EVERY = 20 * 60 * 6;
	static final double HOST_RANGE = 160.0;
	/** A host: this many, and no new one while this many of the last are still out. */
	static final int HOST_MIN = 4, HOST_MAX = 6, HOSTS_OUT_MOST = 8;
	/** A fort takes in a new member about this often (ticks), up to this many. */
	static final int RECRUIT_EVERY = 20 * 60 * 5, GARRISON_MOST = 10;
	/** Its people keep within this of its centre; a host that's arrived holds within this of the walls it came for. */
	static final int HOME_RADIUS = 10, HOLD_RADIUS = 12;
	/** The warboss: size, health, blow, how hard to shove. */
	static final double WARBOSS_SCALE = 1.45, WARBOSS_HEALTH = 220.0, WARBOSS_DAMAGE = 12.0, WARBOSS_KNOCKBACK_RESIST = 1.0;
	/** His leap: from how far, how often (ticks); his landing: reach, damage, throw. */
	static final double LEAP_FROM = 4.0, LEAP_TO = 12.0, SLAM_RADIUS = 4.0, SLAM_THROW = 1.2;
	static final int LEAP_EVERY = 20 * 10;
	static final float SLAM_DAMAGE = 8.0F;
	/** His war horn: below this share of his health, at most this often (ticks), its reach, how many it calls. */
	static final float HORN_BELOW = 0.5F;
	static final int HORN_EVERY = 20 * 40, HORN_TICKS = 20 * 15, HORN_CALLS = 2;
	static final double HORN_RANGE = 24.0;
	/** A routed piglin runs this long, then is gone once no player is this near. */
	static final int ROUT_TICKS = 20 * 60;
	static final double ROUT_GONE_RANGE = 24.0;
	/** Who hears that a warlord fell, or a host marched out. */
	static final double NEWS_RANGE = 96.0;

	/** A fort: its side, centre (floor), gate, rival, name, its warlord's name, when it last sent a host and took a recruit, and whether it's fallen. */
	public record Fort(int id, int side, BlockPos center, Direction gate, int rival, String name, String lord, long lastHost, long lastRecruit,
		boolean fallen) {
		public static final Codec<Fort> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Fort::id),
				Codec.INT.fieldOf("side").forGetter(Fort::side),
				BlockPos.CODEC.fieldOf("center").forGetter(Fort::center),
				Direction.CODEC.fieldOf("gate").forGetter(Fort::gate),
				Codec.INT.fieldOf("rival").forGetter(Fort::rival),
				Codec.STRING.fieldOf("name").forGetter(Fort::name),
				Codec.STRING.fieldOf("lord").forGetter(Fort::lord),
				Codec.LONG.fieldOf("last_host").forGetter(Fort::lastHost),
				Codec.LONG.fieldOf("last_recruit").forGetter(Fort::lastRecruit),
				Codec.BOOL.fieldOf("fallen").forGetter(Fort::fallen))
			.apply(i, Fort::new));

		Fort withHost(long t) {
			return new Fort(id, side, center, gate, rival, name, lord, t, lastRecruit, fallen);
		}

		Fort withRecruit(long t) {
			return new Fort(id, side, center, gate, rival, name, lord, lastHost, t, fallen);
		}

		Fort fall() {
			return new Fort(id, side, center, gate, rival, name, lord, lastHost, lastRecruit, true);
		}
	}

	/** On every fort's member, Hornkin or piglin (saved with it). */
	public record Tag(int fort, int role) {
		public static final Codec<Tag> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("fort").forGetter(Tag::fort),
				Codec.INT.fieldOf("role").forGetter(Tag::role))
			.apply(i, Tag::new));
	}

	/** Every fort there's been (on the Nether). */
	public static final AttachmentType<List<Fort>> FORTS = AttachmentRegistry.create(JaymosKingdoms.id("nether_forts"),
		builder -> builder.persistent(Fort.CODEC.listOf()));
	public static final AttachmentType<Tag> TAG = AttachmentRegistry.create(JaymosKingdoms.id("nether_fort"), builder -> builder.persistent(Tag.CODEC));

	private static final String[] FORT_FIRST = {"Ash", "Cinder", "Gilt", "Horn", "Brimstone", "Ember", "Gold", "Crag", "Blood", "Soot", "Slag", "Char"};
	private static final String[] FORT_SECOND = {"horn", "hold", "maw", "gate", "keep", "spire", "hearth", "crag", "wall", "tusk"};
	private static final String[] CAMP_FIRST = {"Snout", "Tusk", "Bristle", "Grunt", "Hog", "Gold", "Crimson", "Ash", "Wart", "Sow"};
	private static final String[] CAMP_SECOND = {"camp", "pen", "stand", "fire", "ring", "stockade", "rest", "muster"};
	private static final String[] WARLORDS = {"Bhorrak", "Tauvros", "Gorhuun", "Mhurrad", "Kraveth", "Durghal", "Oskaur", "Rhumbar", "Vardhorn", "Thaurok"};
	private static final String[] WARLORD_BYNAMES = {"the Gilded", "Gold-Horn", "the Unbowed", "Ash-Hoof", "the Hoarder", "Iron-Brow", "Pyre-Born",
		"the Gorer"};
	private static final String[] WARBOSSES = {"Grubnak", "Snortha", "Hoglar", "Bristlok", "Gruntor", "Tuskra", "Swillgar", "Rootmaw", "Gildsnout"};

	/** Fort members loaded now. */
	private static final Set<Mob> LOADED = new HashSet<>();
	/** Warbosses' boss bars. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	/** When each warboss may leap and blow his horn again; the ones in the air (and when they leapt). */
	private static final Map<UUID, Long> LEAP_READY = new HashMap<>(), HORN_READY = new HashMap<>(), LEAPING = new HashMap<>();
	/** Routed piglins, and when they broke (kept in memory: after a restart they're plain piglins). */
	private static final Map<Mob, Long> ROUTED = new HashMap<>();

	private NetherForts() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && tag(mob) != null) {
				LOADED.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			LOADED.remove(entity);
			ROUTED.remove(entity);
			ServerBossEvent bar = BARS.remove(entity.getUUID());
			if (bar != null) {
				bar.removeAllPlayers();
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(NetherForts::died);
		ServerTickEvents.END_SERVER_TICK.register(NetherForts::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			ROUTED.clear();
			LEAP_READY.clear();
			HORN_READY.clear();
			LEAPING.clear();
			BARS.values().forEach(ServerBossEvent::removeAllPlayers);
			BARS.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("netherfort").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("found").executes(NetherForts::foundHere))
				.then(Commands.literal("host").executes(NetherForts::hostNow))
				.then(Commands.literal("info").executes(NetherForts::info)))));
	}

	// ---- who's who -----------------------------------------------------------------------------------------------------

	public static @Nullable Tag tag(Entity entity) {
		return entity.getAttached(TAG);
	}

	/** A fort's warlord or warboss (for {@link BossBars}). */
	public static boolean warlord(Entity entity) {
		Tag tag = tag(entity);
		return tag != null && tag.role() == WARLORD;
	}

	/** Makes {@code mob} one of {@code fort}'s, as {@code role}. */
	static void enlist(Mob mob, int fort, int role) {
		mob.setAttached(TAG, new Tag(fort, role));
		mob.setPersistenceRequired();
		LOADED.add(mob);
	}

	private static @Nullable ServerLevel nether(MinecraftServer server) {
		return server.getLevel(Level.NETHER);
	}

	static List<Fort> forts(ServerLevel nether) {
		List<Fort> all = nether.getAttached(FORTS);
		return all == null ? List.of() : all;
	}

	static @Nullable Fort fort(ServerLevel nether, int id) {
		for (Fort f : forts(nether)) {
			if (f.id() == id) {
				return f;
			}
		}
		return null;
	}

	private static void put(ServerLevel nether, Fort fort) {
		List<Fort> all = new ArrayList<>(forts(nether));
		all.removeIf(f -> f.id() == fort.id());
		all.add(fort);
		nether.setAttached(FORTS, List.copyOf(all));
	}

	// ---- each tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel nether = nether(server);
		if (nether == null) {
			return;
		}
		long now = nether.getGameTime();
		landings(nether, now);
		if (now % 10 == 0) {
			for (Mob mob : List.copyOf(LOADED)) {
				if (!mob.isAlive() || mob.level() != nether) {
					continue;
				}
				Tag tag = tag(mob);
				if (tag != null && mob instanceof PiglinBrute brute && tag.role() == WARLORD) {
					warboss(nether, brute, tag, now);
				}
			}
		}
		if (now % 40 == 0) {
			for (Mob mob : List.copyOf(LOADED)) {
				Tag tag = tag(mob);
				if (tag != null && mob.isAlive() && mob instanceof AbstractPiglin piglin && mob.level() == nether) {
					keepPiglin(nether, piglin, tag);
				}
			}
			routs(nether, now);
		}
		if (now % 200 == 0) {
			for (Fort fort : forts(nether)) {
				if (!fort.fallen()) {
					tend(nether, fort, now);
				}
			}
		}
		if (now % FOUND_EVERY == 0) {
			for (ServerPlayer player : nether.players()) {
				if (!player.isSpectator() && nether.getRandom().nextFloat() < FOUND_CHANCE) {
					tryFound(nether, player);
				}
			}
		}
	}

	/** A piglin of a fort: one marching walks on towards the rival's walls; one at home comes back when it's strayed. */
	private static void keepPiglin(ServerLevel nether, AbstractPiglin piglin, Tag tag) {
		if (piglin.getTarget() != null) {
			return;
		}
		Fort own = fort(nether, tag.fort());
		if (own == null) {
			return;
		}
		BlockPos goal = own.center();
		int within = HOME_RADIUS;
		if (tag.role() == HOST) {
			Fort rival = fort(nether, own.rival());
			if (rival != null) {
				goal = rival.center();
				within = HOLD_RADIUS;
			}
		}
		Vec3 to = Vec3.atBottomCenterOf(goal).subtract(piglin.position());
		if (to.horizontalDistance() <= within) {
			return;
		}
		Vec3 step = to.horizontalDistance() > Hornkin.MARCH_STRIDE ? piglin.position().add(to.normalize().scale(Hornkin.MARCH_STRIDE))
			: Vec3.atBottomCenterOf(goal);
		piglin.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(step, 1.0F, 2));
	}

	/** Looks after a standing fort while it's loaded: a new recruit now and then, and a war host when one's due. */
	private static void tend(ServerLevel nether, Fort fort, long now) {
		if (!nether.isLoaded(fort.center())) {
			return;
		}
		Fort rival = fort(nether, fort.rival());
		if (now - fort.lastRecruit() >= RECRUIT_EVERY) {
			put(nether, fort = fort.withRecruit(now));
			if (count(fort.id(), GARRISON) < GARRISON_MOST) {
				spawnMember(nether, fort, gateInside(fort), GARRISON);
			}
		}
		if (rival != null && !rival.fallen() && now - fort.lastHost() >= HOST_EVERY + (fort.id() % 4) * 20 * 30 && playerNear(nether, fort, rival)) {
			sendHost(nether, fort, rival, now);
		}
	}

	private static boolean playerNear(ServerLevel nether, Fort a, Fort b) {
		for (ServerPlayer p : nether.players()) {
			if (!p.isSpectator() && (p.blockPosition().closerThan(a.center(), HOST_RANGE) || p.blockPosition().closerThan(b.center(), HOST_RANGE))) {
				return true;
			}
		}
		return false;
	}

	private static int count(int fort, int role) {
		int n = 0;
		for (Mob mob : LOADED) {
			Tag tag = tag(mob);
			if (tag != null && tag.fort() == fort && tag.role() == role && mob.isAlive()) {
				n++;
			}
		}
		return n;
	}

	/** A war host marches out of {@code fort}'s gate at {@code rival}'s walls. */
	static void sendHost(ServerLevel nether, Fort fort, Fort rival, long now) {
		put(nether, fort.withHost(now));
		if (count(fort.id(), HOST) >= HOSTS_OUT_MOST) {
			return;
		}
		RandomSource random = nether.getRandom();
		int n = HOST_MIN + random.nextInt(HOST_MAX - HOST_MIN + 1);
		BlockPos out = fort.center().relative(fort.gate(), FortBuildings.FORT_RADIUS + 2).above();
		for (int i = 0; i < n; i++) {
			Mob mob = spawnMember(nether, fort, out.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1), HOST);
			if (mob instanceof Hornkin hornkin) {
				hornkin.clearHome();
				hornkin.march(rival.center());
			}
		}
		nether.playSound(null, out, fort.side() == HORNKIN ? SoundEvents.RAVAGER_ROAR : SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 0.8F);
		news(nether, fort.center(), Component.translatable("message.jaymoskingdoms.nether_host", fort.name(), rival.name()).withStyle(ChatFormatting.GOLD));
	}

	// ---- the warboss ---------------------------------------------------------------------------------------------------

	private static void warboss(ServerLevel nether, PiglinBrute brute, Tag tag, long now) {
		ServerBossEvent bar = BARS.computeIfAbsent(brute.getUUID(), id -> new ServerBossEvent(id, brute.getDisplayName(), BossEvent.BossBarColor.YELLOW,
			BossEvent.BossBarOverlay.NOTCHED_10));
		bar.setProgress(brute.getHealth() / brute.getMaxHealth());
		BossBars.show(nether, brute, bar);
		LivingEntity foe = brute.getTarget();
		if (foe == null || !foe.isAlive()) {
			return;
		}
		if (brute.getHealth() < brute.getMaxHealth() * HORN_BELOW && now >= HORN_READY.getOrDefault(brute.getUUID(), 0L)) {
			HORN_READY.put(brute.getUUID(), now + HORN_EVERY);
			warHorn(nether, brute, tag, foe);
		}
		double d = brute.distanceTo(foe);
		if (brute.onGround() && d > LEAP_FROM && d < LEAP_TO && now >= LEAP_READY.getOrDefault(brute.getUUID(), 0L) && brute.hasLineOfSight(foe)) {
			LEAP_READY.put(brute.getUUID(), now + LEAP_EVERY);
			Vec3 to = foe.position().subtract(brute.position());
			double flat = Math.sqrt(to.x * to.x + to.z * to.z);
			Vec3 dir = new Vec3(to.x / flat, 0.0, to.z / flat);
			brute.setDeltaMovement(dir.scale(0.2 + flat * 0.09).add(0.0, 0.7, 0.0));
			brute.syncVelocity = true;
			LEAPING.put(brute.getUUID(), now);
			nether.playSound(null, brute.getX(), brute.getY(), brute.getZ(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 2.0F, 0.7F);
		}
	}

	/** A leaping warboss lands: everyone near (but piglins) is hurt and thrown. */
	private static void landings(ServerLevel nether, long now) {
		if (LEAPING.isEmpty()) {
			return;
		}
		LEAPING.entrySet().removeIf(e -> {
			if (now - e.getValue() < 5) {
				return false;
			}
			Entity entity = nether.getEntity(e.getKey());
			if (!(entity instanceof PiglinBrute brute) || !brute.isAlive() || now - e.getValue() > 60) {
				return true;
			}
			if (!brute.onGround()) {
				return false;
			}
			DamageSource source = nether.damageSources().mobAttack(brute);
			for (LivingEntity hit : nether.getEntitiesOfClass(LivingEntity.class, brute.getBoundingBox().inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS),
				h -> h != brute && h.isAlive() && !(h instanceof AbstractPiglin) && !(h instanceof Player p && (p.isCreative() || p.isSpectator())))) {
				hit.hurtServer(nether, source, SLAM_DAMAGE);
				Vec3 away = hit.position().subtract(brute.position()).multiply(1.0, 0.0, 1.0);
				Vec3 push = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(SLAM_THROW);
				hit.setDeltaMovement(push.x, 0.5, push.z);
				hit.syncVelocity = true;
			}
			nether.sendParticles(ParticleTypes.EXPLOSION, brute.getX(), brute.getY() + 0.3, brute.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
			nether.sendParticles(ParticleTypes.LAVA, brute.getX(), brute.getY() + 0.1, brute.getZ(), 10, 1.5, 0.1, 1.5, 0.0);
			nether.playSound(null, brute.getX(), brute.getY(), brute.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
			return true;
		});
	}

	/** The warboss's war horn: his piglins near fight harder and turn on his foe, and two more come running. */
	private static void warHorn(ServerLevel nether, PiglinBrute brute, Tag tag, LivingEntity foe) {
		for (AbstractPiglin piglin : nether.getEntitiesOfClass(AbstractPiglin.class, brute.getBoundingBox().inflate(HORN_RANGE), p -> p.isAlive() && !p.isBaby())) {
			piglin.addEffect(new MobEffectInstance(MobEffects.STRENGTH, HORN_TICKS, 0));
			piglin.addEffect(new MobEffectInstance(MobEffects.SPEED, HORN_TICKS, 0));
			if (piglin != brute) {
				piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, foe.getUUID(), 600L);
			}
		}
		Fort fort = fort(nether, tag.fort());
		if (fort != null) {
			for (int i = 0; i < HORN_CALLS; i++) {
				Mob called = spawnMember(nether, fort, brute.blockPosition().offset(nether.getRandom().nextInt(5) - 2, 0, nether.getRandom().nextInt(5) - 2), GARRISON);
				if (called instanceof AbstractPiglin piglin) {
					piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, foe.getUUID(), 600L);
				}
			}
		}
		nether.playSound(null, brute.getX(), brute.getY(), brute.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 4.0F, 1.1F);
		for (ServerPlayer player : nether.getEntitiesOfClass(ServerPlayer.class, brute.getBoundingBox().inflate(32.0))) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.warboss_horn", brute.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
	}

	// ---- a warlord falls -----------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !warlord(entity)) {
			return;
		}
		ServerBossEvent bar = BARS.remove(entity.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		Tag tag = tag(entity);
		Fort fort = tag == null ? null : fort(level, tag.fort());
		if (fort == null || fort.fallen()) {
			return;
		}
		put(level, fort.fall());
		long now = level.getGameTime();
		for (Mob mob : List.copyOf(LOADED)) {
			Tag t = tag(mob);
			if (t == null || t.fort() != fort.id() || !mob.isAlive() || mob == entity) {
				continue;
			}
			mob.removeAttached(TAG);
			LOADED.remove(mob);
			if (mob instanceof Hornkin hornkin) {
				hornkin.rout();
			} else {
				ROUTED.put(mob, now);
			}
		}
		news(level, fort.center(), Component.translatable("message.jaymoskingdoms.nether_fort_fallen", entity.getDisplayName(), fort.name())
			.withStyle(ChatFormatting.GOLD));
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1.0F, 1.4F);
		JaymosKingdoms.LOGGER.info("The Nether fort {} fell", fort.name());
	}

	/** Routed piglins run from the nearest player; once the rout is over and nobody's near, they're gone. */
	private static void routs(ServerLevel nether, long now) {
		ROUTED.entrySet().removeIf(e -> {
			Mob mob = e.getKey();
			if (!mob.isAlive() || mob.level() != nether) {
				return true;
			}
			Player near = nether.getNearestPlayer(mob, ROUT_GONE_RANGE);
			if (near == null && now - e.getValue() > ROUT_TICKS) {
				mob.discard();
				return true;
			}
			if (mob instanceof AbstractPiglin piglin) {
				piglin.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
				piglin.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
				if (near != null) {
					Vec3 away = mob.position().subtract(near.position()).multiply(1.0, 0.0, 1.0);
					Vec3 to = mob.position().add(away.lengthSqr() < 1.0E-4 ? new Vec3(8.0, 0.0, 0.0) : away.normalize().scale(12.0));
					piglin.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 1.2F, 1));
				}
			}
			return false;
		});
	}

	private static void news(ServerLevel level, BlockPos at, Component message) {
		for (ServerPlayer player : level.players()) {
			if (player.blockPosition().closerThan(at, NEWS_RANGE)) {
				player.sendSystemMessage(message);
			}
		}
	}

	// ---- founding ------------------------------------------------------------------------------------------------------

	/** Now and then a rival pair rises near {@code player}, out of sight, when no standing fort is near. */
	private static void tryFound(ServerLevel nether, ServerPlayer player) {
		for (Fort f : forts(nether)) {
			if (!f.fallen() && f.center().closerThan(player.blockPosition(), FORT_AREA)) {
				return;
			}
		}
		RandomSource random = nether.getRandom();
		for (int i = 0; i < SITE_TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = SITE_MIN + random.nextInt(SITE_MAX - SITE_MIN);
			BlockPos at = player.blockPosition().offset((int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
			if (foundPair(nether, at, player.getBlockY(), false) != null) {
				return;
			}
		}
	}

	/**
	 * A Hornkin fort near {@code at} and a piglin camp facing it: both sites found and checked first, then both built and
	 * manned. Null (nothing built) if either has no fit ground. {@code anywhere}: an op's, so not hidden from players.
	 */
	static @Nullable Fort foundPair(ServerLevel nether, BlockPos at, int nearY, boolean anywhere) {
		BlockPos fortSite = nearSite(nether, at.getX(), at.getZ(), nearY, FortBuildings.FORT_RADIUS, anywhere, true);
		if (fortSite == null) {
			return null;
		}
		RandomSource random = nether.getRandom();
		BlockPos campSite = null;
		for (int i = 0; i < SITE_TRIES && campSite == null; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = PAIR_MIN + random.nextInt(PAIR_MAX - PAIR_MIN);
			campSite = nearSite(nether, fortSite.getX() + (int) (Math.cos(angle) * dist), fortSite.getZ() + (int) (Math.sin(angle) * dist), fortSite.getY(),
				FortBuildings.CAMP_RADIUS, anywhere, false);
			if (campSite != null && campSite.closerThan(fortSite, PAIR_MIN - SEARCH_RADIUS / 2)) {
				campSite = null;
			}
		}
		if (campSite == null) {
			return null;
		}
		int id = forts(nether).stream().mapToInt(Fort::id).max().orElse(0) + 1;
		Direction fortGate = Direction.getApproximateNearest(campSite.getX() - fortSite.getX(), 0, campSite.getZ() - fortSite.getZ());
		Direction campGate = fortGate.getOpposite();
		Fort fort = new Fort(id, HORNKIN, fortSite, fortGate, id + 1, name(random, FORT_FIRST, FORT_SECOND),
			WARLORDS[random.nextInt(WARLORDS.length)] + " " + WARLORD_BYNAMES[random.nextInt(WARLORD_BYNAMES.length)], 0L, nether.getGameTime(), false);
		Fort camp = new Fort(id + 1, PIGLIN, campSite, campGate, id, name(random, CAMP_FIRST, CAMP_SECOND),
			"Warboss " + WARBOSSES[random.nextInt(WARBOSSES.length)], 0L, nether.getGameTime(), false);
		put(nether, fort);
		put(nether, camp);
		man(nether, fort, FortBuildings.hornkinFort(nether, fortSite, fortGate, random));
		man(nether, camp, FortBuildings.piglinCamp(nether, campSite, campGate, random));
		JaymosKingdoms.LOGGER.info("A Hornkin war fort, {}, rose at {}, and a piglin war camp, {}, at {}", fort.name(), fortSite, camp.name(), campSite);
		return fort;
	}

	private static String name(RandomSource random, String[] first, String[] second) {
		return first[random.nextInt(first.length)] + second[random.nextInt(second.length)];
	}

	/** The fit {@link #site} nearest column ({@code x}, {@code z}), looking out to SEARCH_RADIUS. Null if none. */
	private static @Nullable BlockPos nearSite(ServerLevel nether, int x, int z, int nearY, int radius, boolean anywhere, boolean hornkin) {
		for (int ring = 0; ring <= SEARCH_RADIUS; ring += SEARCH_STEP) {
			for (int dx = -ring; dx <= ring; dx += SEARCH_STEP) {
				for (int dz = -ring; dz <= ring; dz += SEARCH_STEP) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					BlockPos found = site(nether, x + dx, z + dz, nearY, radius, anywhere, hornkin);
					if (found != null) {
						return found;
					}
				}
			}
		}
		return null;
	}

	/**
	 * A floor at column ({@code x}, {@code z}) for a square of {@code radius}: ground with headroom over it, level enough
	 * across, loaded, not over lava; out of every player's sight unless {@code anywhere}; clear of every other fort. Null if none.
	 */
	private static @Nullable BlockPos site(ServerLevel nether, int x, int z, int nearY, int radius, boolean anywhere, boolean hornkin) {
		BlockPos probe = new BlockPos(x, nearY, z);
		for (int dx : new int[] {-radius, radius}) {
			for (int dz : new int[] {-radius, radius}) {
				if (!nether.isLoaded(probe.offset(dx, 0, dz))) {
					return null;
				}
			}
		}
		for (Fort f : forts(nether)) {
			if (f.center().closerThan(probe, FORT_SPACING)) {
				return null;
			}
		}
		if (!anywhere) {
			for (ServerPlayer p : nether.players()) {
				if (p.blockPosition().closerThan(probe, HIDE_RANGE)) {
					return null;
				}
			}
		}
		int floor = ground(nether, x, z, Math.min(HIGH, nearY + 24), LOW);
		if (floor == Integer.MIN_VALUE) {
			return null;
		}
		BlockPos center = new BlockPos(x, floor, z);
		if (hornkin && !anywhere && !(nether.getBiome(center).is(Biomes.NETHER_WASTES) || nether.getBiome(center).is(Biomes.CRIMSON_FOREST))) {
			return null;
		}
		int[] steps = {-radius, 0, radius};
		for (int dx : steps) {
			for (int dz : steps) {
				int y = ground(nether, x + dx, z + dz, floor + MAX_SPREAD, floor - MAX_SPREAD);
				if (y == Integer.MIN_VALUE) {
					return null;
				}
			}
		}
		return center;
	}

	/** The highest solid ground in column ({@code x}, {@code z}) between {@code top} and {@code bottom} with headroom over it, or MIN_VALUE. */
	private static int ground(ServerLevel nether, int x, int z, int top, int bottom) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = top; y >= bottom; y--) {
			BlockState state = nether.getBlockState(pos.set(x, y, z));
			if (!state.getFluidState().isEmpty()) {
				return Integer.MIN_VALUE;
			}
			if (state.isAir() || !state.isSolid()) {
				continue;
			}
			boolean room = true;
			for (int h = 1; h <= HEADROOM && room; h++) {
				BlockState above = nether.getBlockState(pos.set(x, y + h, z));
				room = !above.isSolid() && above.getFluidState().isEmpty();
			}
			if (room) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	/** Seats a fort's warlord in his keep and fills its yard. */
	private static void man(ServerLevel nether, Fort fort, FortBuildings.Layout layout) {
		RandomSource random = nether.getRandom();
		Mob lord = spawnMember(nether, fort, layout.keep(), WARLORD);
		if (lord != null) {
			lord.setCustomName(Component.literal(fort.lord()));
		}
		int n = fort.side() == HORNKIN ? 7 : 6;
		for (int i = 0; i < n; i++) {
			BlockPos spot = fort.center().above().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4);
			spawnMember(nether, fort, spot, GARRISON);
		}
	}

	private static BlockPos gateInside(Fort fort) {
		return fort.center().relative(fort.gate(), (fort.side() == HORNKIN ? FortBuildings.FORT_RADIUS : FortBuildings.CAMP_RADIUS) - 2).above();
	}

	/** One of {@code fort}'s people at {@code at}: its side's kind for {@code role} (a warlord, or a mix for the rest). */
	static @Nullable Mob spawnMember(ServerLevel nether, Fort fort, BlockPos at, int role) {
		RandomSource random = nether.getRandom();
		EntityType<? extends Mob> type;
		if (fort.side() == HORNKIN) {
			type = role == WARLORD ? Nether.HORNKIN_WARLORD : pick(random, Nether.HORNKIN, 5, Nether.HORNKIN_HURLER, 3, Nether.HORNKIN_BRUTE, 1);
		} else {
			type = role == WARLORD ? EntityTypes.PIGLIN_BRUTE : pick(random, EntityTypes.PIGLIN, 7, EntityTypes.PIGLIN_BRUTE, 2, EntityTypes.PIGLIN, 0);
		}
		Mob mob = type.create(nether, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(nether, nether.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (mob instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true);
		}
		if (role == WARLORD && mob instanceof PiglinBrute brute) {
			makeWarboss(brute, fort);
		}
		if (role != HOST) {
			mob.setHomeTo(fort.center(), HOME_RADIUS);
		}
		enlist(mob, fort.id(), role);
		nether.addFreshEntity(mob);
		return mob;
	}

	private static EntityType<? extends Mob> pick(RandomSource random, Object... weighted) {
		int total = 0;
		for (int i = 1; i < weighted.length; i += 2) {
			total += (Integer) weighted[i];
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < weighted.length; i += 2) {
			roll -= (Integer) weighted[i + 1];
			if (roll < 0) {
				@SuppressWarnings("unchecked")
				EntityType<? extends Mob> type = (EntityType<? extends Mob>) weighted[i];
				return type;
			}
		}
		@SuppressWarnings("unchecked")
		EntityType<? extends Mob> first = (EntityType<? extends Mob>) weighted[0];
		return first;
	}

	/** A piglin brute made a warboss: half again as big, far tougher, in gold, his name over him. */
	private static void makeWarboss(PiglinBrute brute, Fort fort) {
		brute.getAttribute(Attributes.SCALE).setBaseValue(WARBOSS_SCALE);
		brute.getAttribute(Attributes.MAX_HEALTH).setBaseValue(WARBOSS_HEALTH);
		brute.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(WARBOSS_DAMAGE);
		brute.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(WARBOSS_KNOCKBACK_RESIST);
		brute.setHealth(brute.getMaxHealth());
		brute.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		brute.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
		brute.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
		brute.setCustomName(Component.literal(fort.lord()));
	}

	// ---- commands (operators; debug: plain text) -----------------------------------------------------------------------

	/** Founds a pair just ahead of whoever runs it (a player, or {@code execute in the_nether positioned ...}). */
	private static int foundHere(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		if (level.dimension() != Level.NETHER) {
			c.getSource().sendFailure(Component.literal("Nether forts are only in the Nether"));
			return 0;
		}
		BlockPos here = BlockPos.containing(c.getSource().getPosition());
		Entity entity = c.getSource().getEntity();
		BlockPos ahead = entity == null ? here : here.relative(entity.getDirection(), FortBuildings.FORT_RADIUS + 4);
		Fort fort = foundPair(level, ahead, here.getY(), true);
		if (fort == null) {
			c.getSource().sendFailure(Component.literal("No fit ground for a fort ahead of you and a camp near it (needs level, loaded ground and headroom)"));
			return 0;
		}
		Fort camp = fort(level, fort.rival());
		c.getSource().sendSuccess(() -> Component.literal("Founded " + fort.name() + " at " + fort.center().toShortString()
			+ (camp == null ? "" : " and " + camp.name() + " at " + camp.center().toShortString())), true);
		return 1;
	}

	private static int hostNow(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getLevel();
		if (level.dimension() != Level.NETHER) {
			c.getSource().sendFailure(Component.literal("Nether forts are only in the Nether"));
			return 0;
		}
		BlockPos here = BlockPos.containing(c.getSource().getPosition());
		Fort nearest = null;
		for (Fort f : forts(level)) {
			if (!f.fallen() && (nearest == null || f.center().distSqr(here) < nearest.center().distSqr(here))) {
				nearest = f;
			}
		}
		Fort rival = nearest == null ? null : fort(level, nearest.rival());
		if (nearest == null || rival == null || rival.fallen()) {
			c.getSource().sendFailure(Component.literal("No standing fort with a standing rival"));
			return 0;
		}
		sendHost(level, nearest, rival, level.getGameTime());
		Fort from = nearest;
		c.getSource().sendSuccess(() -> Component.literal("A host marches from " + from.name() + " on " + rival.name()), true);
		return 1;
	}

	private static int info(CommandContext<CommandSourceStack> c) {
		ServerLevel level = nether(c.getSource().getServer());
		if (level == null) {
			return 0;
		}
		List<Fort> all = forts(level);
		StringBuilder out = new StringBuilder(all.size() + " Nether forts");
		for (Fort f : all) {
			out.append("\n").append(f.id()).append(" ").append(f.side() == HORNKIN ? "Hornkin" : "piglin").append(" ").append(f.name())
				.append(" at ").append(f.center().toShortString()).append(f.fallen() ? " (fallen)" : "").append(", ").append(f.lord())
				.append(", garrison loaded ").append(count(f.id(), GARRISON)).append(", host out ").append(count(f.id(), HOST));
		}
		c.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
		return 1;
	}
}
