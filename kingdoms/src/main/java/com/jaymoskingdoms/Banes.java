package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.Captains.Meeting;
import com.jaymoskingdoms.nether.Hornkin;
import com.jaymoskingdoms.nether.Nether;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.619) Rivals patch 4, <b>your Bane is born</b> (ideas from Warframe's personal adversaries, a commercial game: our own
 * names, code and lines). One foe in a while carries a faint brand only you can see ({@link #BRAND}). Finish it (downed, or
 * its head off) and it doesn't stay dead: a day or two later it rises as your Bane, a captain on a ladder of one
 * ({@code bane_<kind>@<your id>}, {@link Rivals.Side#bane}) who hunts you alone. You choose whether to wake one by choosing the
 * kill; there's only ever one for each player.
 * <ul>
 * <li><b>Three kinds:</b> a raised corpse-knight (from the dead), an outlaw warlord who climbed on your name (from an outlaw or
 * an illager), a demon-sworn one (from the Nether's own). Each has its own band, arms and lines, and speaks in its kin's
 * voice otherwise ({@link Rivals.Side#voice}).</li>
 * <li><b>Ranks:</b> up a rank, a strength and a better weapon each time it kills or downs you, and a mocking note by your
 * messenger. Rank five at most ({@link #RANK_MAX}).</li>
 * <li><b>It learns how you fight:</b> the kind of harm you deal it most ({@link CaptainWays#cause}) it takes at half
 * ({@link #RESIST}) once it has had enough of it.</li>
 * <li><b>It turns up mid-fight</b> on its ground (round where it was born, {@link #GROUND}), never while you're in a film,
 * with its band, a speaker-shot film and a line about how your last meeting went. It goes when the fight's over or you get
 * away.</li>
 * <li><b>Friends can help:</b> anyone can fight it and question its band; only its owner may finish it. Struck down by
 * anyone else it leaves, laughing, to wait for them.</li>
 * </ul>
 * Beaten, it comes back in a day or two, having learned from it; only its own marks end it ({@link BaneMarks}). (0.7.621) It
 * fights with its own element weapon, whose head is yours when it's ended ({@link BaneArms}), and at rank five it raises a
 * hold to be stormed ({@link BaneHolds}).
 */
public final class Banes {
	// ================================================================================================================
	// Tuning
	/** Each look (every {@link #LOOK} ticks), the chance a fitting foe within {@link #BRAND_NEAR} of a player without a Bane is branded for them. */
	static final float BRAND_CHANCE = 0.015F;
	static final double BRAND_NEAR = 24.0;
	/**
	 * (Andy: "the outlaws should be the most common for this system") An outlaw or illager is this many times as likely to be
	 * picked for the brand as anything else near, and anything else, once picked, keeps it only at {@link #OTHER_KEEPS}.
	 */
	static final int OUTLAW_WEIGHT = 4;
	static final float OTHER_KEEPS = 0.5F;
	static final int LOOK = 100;
	/** Days until a newly born Bane first rises; days until a beaten one is back. */
	static final int RISE_DAYS_MIN = 1, RISE_DAYS_MAX = 2, BACK_DAYS_MIN = 1, BACK_DAYS_MAX = 2;
	/** Its top rank. */
	static final int RANK_MAX = 5;
	/** Its ground: this far (blocks) from where it was born. */
	static final double GROUND = 320.0;
	/** You're in a fight if you've hurt or been hurt by anything this recently (ticks); then, each look, it comes at this chance. */
	static final int FIGHT_TICKS = 200;
	static final float ARRIVE_CHANCE = 0.3F;
	/** After it goes, this long (ticks) before it comes again. */
	static final long ARRIVE_GAP = 12000L;
	/** Its arrival film (ticks); how far off it turns up. */
	static final int ARRIVE_FILM = 110;
	static final double ARRIVE_MIN = 10.0, ARRIVE_MAX = 16.0;
	/** It goes if you're further than this, or nobody has struck a blow for this long (ticks), or this long after it killed you. */
	static final double LEAVE_FAR = 72.0;
	static final long LEAVE_QUIET = 1200L, LEAVE_AFTER_KILL = 200L;
	/** The harm it has learned: taken at this share, once you've dealt this much of that kind. */
	static final float RESIST = 0.5F, RESIST_FROM = 40.0F;
	/** Its band: this many, plus one each two ranks, at most {@link #BAND_MAX}. */
	static final int BAND_BASE = 1, BAND_MAX = 4;
	/** A villager's blow (its brain set aside): reach and how often. */
	static final double REACH = 2.6;
	static final int SWING_TICKS = 20;
	/** Struck down by anyone but its owner, it gets up with this share of its health and goes. */
	static final float NOT_YOURS_HEALTH = 0.3F;
	/** (0.7.620) Away from its ground, or while it heals: each look in a fight, the chance some of its men come (this many), marks and all. */
	static final float MEN_CHANCE = 0.12F;
	static final int MEN = 2;
	/** (0.7.620) Finished with the wrong marks, it's up with this share of its health; sworn, with this. */
	static final float WRONG_HEALTH = 0.6F, SWORN_HEALTH = 0.5F;
	/** (0.7.621) At its hold: its band goes for anyone this near, and back home past this far; warded, a killing blow leaves it this much. */
	static final double HOLD_SEES = 20.0, HOLD_STRAY = 10.0;
	static final float WARDED_HEALTH = 0.25F;
	// ================================================================================================================

	/** On a Bane's body. */
	public static final String BODY_TAG = "jaymoskingdoms.bane";
	/** On one of its band: this and its ladder. */
	static final String BAND_TAG = "jaymoskingdoms.bane_band=";
	static final int LINES = 2, NOTES = 3;

	/** On a foe: the player (id) who sees its brand. Saved, and sent to every client (only that player's draws it). */
	public static final AttachmentType<String> BRAND = AttachmentRegistry.create(JaymosKingdoms.id("bane_brand"),
		builder -> builder.persistent(Codec.STRING).syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	/** A player's Bane: its captain id, the dimension and place of its ground, and when it may next come. */
	record Bane(String id, String dim, BlockPos home, long next) {
		static final Codec<Bane> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("id").forGetter(Bane::id),
				Codec.STRING.fieldOf("dim").forGetter(Bane::dim),
				BlockPos.CODEC.fieldOf("home").forGetter(Bane::home),
				Codec.LONG.optionalFieldOf("next", 0L).forGetter(Bane::next))
			.apply(i, Bane::new));

		Bane next(long when) {
			return new Bane(id, dim, home, when);
		}
	}

	/** On a player: their Bane. Saved, kept through death. */
	static final AttachmentType<Bane> BANE = AttachmentRegistry.create(JaymosKingdoms.id("bane"),
		builder -> builder.persistent(Bane.CODEC).copyOnDeath());
	/** On the Overworld: each Bane's (captain id) tally of the harm its owner has dealt it, by cause. Saved. */
	static final AttachmentType<Map<String, Map<String, Float>>> TALLY = AttachmentRegistry.create(JaymosKingdoms.id("bane_tally"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.FLOAT))));

	/**
	 * A visit: when it came, the last blow struck by or at it or its band, when its owner fell (−1), whether it downed them
	 * yet; (0.7.620) {@code men}: only some of its band, come without it.
	 */
	private static final class Visit {
		final long at;
		final boolean men;
		/** (0.7.621) Where it waits in its hold ({@link BaneHolds}), or null for a visit to a fight. */
		final @Nullable BlockPos hold;
		long lastBlow, ownerDead = -1L;
		boolean downed;

		Visit(long at, boolean men) {
			this(at, men, null);
		}

		Visit(long at, boolean men, @Nullable BlockPos hold) {
			this.at = at;
			this.men = men;
			this.hold = hold;
			this.lastBlow = at + (men ? 0 : ARRIVE_FILM);
		}
	}

	/** (0.7.620) Its owner's finisher with the right marks: these Banes fall for good. In memory, for the moment of the blow. */
	private static final java.util.Set<String> ENDING = new java.util.HashSet<>();

	/** Each Bane here now (its ladder). In memory: a reload sends them off. */
	private static final Map<String, Visit> VISITS = new HashMap<>();
	/** Each visiting Bane's band (its ladder → their ids). */
	private static final Map<String, List<UUID>> BANDS = new HashMap<>();
	/** The foe branded for each player now. */
	private static final Map<UUID, UUID> BRANDS = new HashMap<>();
	/** When each player was last told it shrugged off their blow. */
	private static final Map<UUID, Long> TOLD = new HashMap<>();

	private static final String[] KNIGHT_NAMES = {"Sir Malcolm", "Sir Roland", "Sir Tristram", "Sir Gerent", "Sir Urien", "Lord Edric", "Sir Baudwin",
		"Sir Caradoc", "Lady Ysolde", "Sir Hamon"};
	private static final String[] KNIGHT_BYNAMES = {"the Twice-Dead", "Grave-Risen", "the Unforgiving", "Ash-Knight", "the Hollow Oath", "Tomb-Breaker",
		"the Unrested", "Worm-Mailed"};
	private static final String[] WARLORD_NAMES = {"Garrick", "Wulfric", "Osmund", "Roderick", "Bastian", "Harlan", "Corin", "Aldric", "Mabry", "Joss"};
	private static final String[] WARLORD_BYNAMES = {"Name-Thief", "the Climber", "Turncoat", "the Last Laugh", "Price-Taker", "the Usurper",
		"Grudge-Keeper", "the Pretender"};
	private static final String[] DEMON_NAMES = {"Azhrak", "Vorrul", "Skathe", "Mordrith", "Ghazul", "Nerrok", "Ulthrax", "Brimmog"};
	private static final String[] DEMON_BYNAMES = {"the Hell-Sworn", "Brand-Eater", "the Smouldering", "Ash-Tongue", "the Pact-Bound", "Cinder-Crowned",
		"the Kindled", "Soul-Taker"};

	/** Its weapon at each rank (1 to 5), by kind. */
	private static final Item[] KNIGHT_ARMS = {Items.STONE_SWORD, Items.IRON_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
	private static final Item[] WARLORD_ARMS = {Items.STONE_AXE, Items.IRON_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};
	private static final Item[] DEMON_ARMS = {Items.STONE_AXE, Items.GOLDEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};

	private Banes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % LOOK == 0) {
				look(server);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			if (!VISITS.isEmpty() && level.getGameTime() % 5L == 0L) {
				visits(level);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register(Banes::allowDeath);
		// ((0.7.620) the marks that end it)
		BaneMarks.register();
		// ((0.7.621) its element weapon, and its hold at rank five)
		BaneArms.register();
		BaneHolds.register();
		// ((0.7.625) its cut, its claims and its hounds)
		BaneCut.register();
		// (a band never harms its own: the knight's dead leave his grave-robbers be)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
			String mine = VISITS.isEmpty() || source.getEntity() == null ? null : bandOf(victim);
			return mine == null || !mine.equals(bandOf(source.getEntity()));
		});
		ServerEntityEvents.ENTITY_LOAD.register(Banes::loaded);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			VISITS.clear();
			BANDS.clear();
			BRANDS.clear();
			TOLD.clear();
		});
	}

	// ---- who's who ----------------------------------------------------------------------------------------------------------

	/** {@code ladder} is a Bane's. */
	static boolean bane(@Nullable String ladder) {
		return ladder != null && Rivals.side(ladder).bane();
	}

	/** {@code e} is a Bane's body. */
	static boolean is(Entity e) {
		return e.entityTags().contains(BODY_TAG) && Captains.is(e);
	}

	/** The ladder of the Bane whose band {@code e} is in, or null. */
	static @Nullable String followerOf(Entity e) {
		for (String tag : e.entityTags()) {
			if (tag.startsWith(BAND_TAG)) {
				return tag.substring(BAND_TAG.length());
			}
		}
		return null;
	}

	/** The ladder of the Bane {@code e} is, or is one of the band of; null for anyone else. */
	public static @Nullable String bandOf(Entity e) {
		if (is(e)) {
			Captain c = Captains.of(e);
			return c == null ? null : c.tribe();
		}
		return followerOf(e);
	}

	/** The player whose Bane's ladder {@code ladder} is. */
	static @Nullable UUID owner(String ladder) {
		int at = ladder.indexOf('@');
		try {
			return at < 0 ? null : UUID.fromString(ladder.substring(at + 1));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** {@code player}'s Bane's captain, born and not ended (fallen and due back counts), or null. */
	static @Nullable Captain of(ServerPlayer player) {
		Bane b = player.getAttached(BANE);
		Captain c = b == null ? null : Captains.get(player.level().getServer(), b.id());
		// ((0.7.620) one sworn to you is your Bane no more)
		return c == null || c.fallen() && c.back() < 0 || SwornCaptains.sworn(player.level().getServer(), c.id()) ? null : c;
	}

	static String[] names(Rivals.Side s) {
		return switch (s) {
			case BANE_WARLORD -> WARLORD_NAMES;
			case BANE_DEMON -> DEMON_NAMES;
			default -> KNIGHT_NAMES;
		};
	}

	static String[] bynames(Rivals.Side s) {
		return switch (s) {
			case BANE_WARLORD -> WARLORD_BYNAMES;
			case BANE_DEMON -> DEMON_BYNAMES;
			default -> KNIGHT_BYNAMES;
		};
	}

	/** A kind's short name for its lines: {@code knight}, {@code warlord}, {@code demon}. */
	static String kind(Rivals.Side s) {
		return s.key.substring("bane_".length());
	}

	/** The kind of Bane {@code e} would wake as, or null if it can't carry a brand. */
	static Rivals.@Nullable Side kindOf(Entity e) {
		if (!(e instanceof Mob) || Captains.is(e) || followerOf(e) != null || Thralls.isThrall(e)) {
			return null;
		}
		boolean nether = e.level().dimension() == Level.NETHER;
		if (e instanceof Hornkin h) {
			return h.zombified() || h.kind() == Hornkin.Kind.WARLORD ? null : Rivals.Side.BANE_DEMON;
		}
		if (e instanceof AbstractPiglin) {
			return Rivals.Side.BANE_DEMON;
		}
		if (e instanceof Zombie z && !z.isBaby() || e instanceof AbstractSkeleton) {
			return nether ? Rivals.Side.BANE_DEMON : Rivals.Side.BANE_KNIGHT;
		}
		if (e instanceof AbstractIllager || Outlaws.is(e)) {
			return Rivals.Side.BANE_WARLORD;
		}
		return null;
	}

	// ---- the brand and the birth ----------------------------------------------------------------------------------------

	/** Every look: brands for those without a Bane; the Bane comes for those with one. */
	private static void look(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive() || p.isSpectator() || p.isCreative()) {
				continue;
			}
			Captain c = of(p);
			if (c == null) {
				brand(p);
			} else {
				comes(p, c);
			}
		}
	}

	/** Now and then a fitting foe near {@code p} is branded for them (one at a time). */
	private static void brand(ServerPlayer p) {
		ServerLevel level = (ServerLevel)p.level();
		UUID had = BRANDS.get(p.getUUID());
		if (had != null) {
			Entity e = level.getEntity(had);
			if (e != null && e.isAlive()) {
				return;
			}
			BRANDS.remove(p.getUUID());
		}
		RandomSource random = level.getRandom();
		if (random.nextFloat() >= BRAND_CHANCE) {
			return;
		}
		List<Mob> fit = new ArrayList<>(level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(BRAND_NEAR), m -> m.isAlive() && !m.hasAttached(BRAND) && kindOf(m) != null));
		if (fit.isEmpty()) {
			return;
		}
		int total = 0;
		for (Mob m : fit) {
			total += kindOf(m) == Rivals.Side.BANE_WARLORD ? OUTLAW_WEIGHT : 1;
		}
		Mob foe = fit.getFirst();
		for (int roll = random.nextInt(total); ; ) {
			foe = fit.removeFirst();
			roll -= kindOf(foe) == Rivals.Side.BANE_WARLORD ? OUTLAW_WEIGHT : 1;
			if (roll < 0 || fit.isEmpty()) {
				break;
			}
		}
		if (kindOf(foe) != Rivals.Side.BANE_WARLORD && random.nextFloat() >= OTHER_KEEPS) {
			return;
		}
		foe.setAttached(BRAND, p.getStringUUID());
		foe.setPersistenceRequired();
		BRANDS.put(p.getUUID(), foe.getUUID());
	}

	/** (From {@code Captains.died}) A branded foe is dead: finished by the one who sees its brand, it wakes as their Bane. */
	static void foeDied(ServerLevel level, LivingEntity foe, DamageSource source) {
		String brand = foe.getAttached(BRAND);
		if (brand == null) {
			return;
		}
		BRANDS.values().remove(foe.getUUID());
		if (!(source.getEntity() instanceof ServerPlayer p) || !p.getStringUUID().equals(brand) || of(p) != null) {
			return;
		}
		boolean finished = Downed.isDowned(foe) || foe.entityTags().contains(Captains.MISSING_TAG + "head");
		Rivals.Side kind = kindOf(foe);
		if (!finished || kind == null) {
			return;
		}
		born(level, p, foe, kind);
	}

	/** {@code foe}, finished by {@code p}, will rise as their Bane. */
	private static void born(ServerLevel level, ServerPlayer p, LivingEntity foe, Rivals.Side kind) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		long now = Factions.level(server).getGameTime();
		String ladder = kind.key + "@" + p.getStringUUID();
		String byname = Rivals.byname(ladder, random);
		String name = foe.hasCustomName() || foe instanceof Worker ? foe.getName().getString() + " " + byname : Rivals.rollName(ladder, random);
		Captain c = Captain.of(Captains.newId(random), ladder, name, 1, Captains.roll(server, random), -1, null, false);
		if (kind == Rivals.Side.BANE_KNIGHT) {
			c = Captains.raisedDead(c);
		}
		c = c.met(p.getUUID(), new Meeting(1, "slain", now));
		long rise = now + 24000L * (RISE_DAYS_MIN + random.nextInt(RISE_DAYS_MAX - RISE_DAYS_MIN + 1));
		c = c.fell(rise, List.of());
		Captains.put(server, c);
		p.setAttached(BANE, new Bane(c.id(), level.dimension().identifier().toString(), foe.blockPosition(), 0L));
		List<String> sworn = new ArrayList<>(p.getAttachedOrElse(Captains.SWORN, List.of()));
		if (!sworn.contains(c.id())) {
			sworn.add(c.id());
			p.setAttached(Captains.SWORN, List.copyOf(sworn));
		}
		level.sendParticles(ParticleTypes.SCULK_SOUL, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(), 18, 0.3, 0.4, 0.3, 0.03);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 0.5F, 0.6F);
		p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.flares").withStyle(ChatFormatting.DARK_RED));
		Post.report(p.getUUID(), Component.translatable("bane.jaymoskingdoms." + kind(kind) + ".born", Component.literal(name)));
		JaymosKingdoms.LOGGER.info("{}'s Bane ({}) is born of {} at {}", p.getName().getString(), name, foe.getName().getString(), foe.blockPosition());
	}

	/** When a beaten Bane is back. */
	static long back(long now, RandomSource random) {
		return now + 24000L * (BACK_DAYS_MIN + random.nextInt(BACK_DAYS_MAX - BACK_DAYS_MIN + 1));
	}

	// ---- its arms --------------------------------------------------------------------------------------------------------------

	/** (From {@code Rivals.gear}) Its arms for {@code rank}: a better weapon each rank, and plate for the knight and the warlord. */
	static void gear(Mob mob, int rank, Rivals.Side s) {
		gear(mob, null, rank, s);
	}

	/** (0.7.621) As {@link #gear(Mob, int, Rivals.Side)}, its own element weapon when {@code c} is known ({@link BaneArms}). */
	static void gear(Mob mob, @Nullable Captain c, int rank, Rivals.Side s) {
		int r = Math.max(1, Math.min(RANK_MAX, rank)) - 1;
		Item[] arms = switch (s) {
			case BANE_WARLORD -> WARLORD_ARMS;
			case BANE_DEMON -> DEMON_ARMS;
			default -> KNIGHT_ARMS;
		};
		mob.setItemSlot(EquipmentSlot.MAINHAND, c != null && mob.level() instanceof ServerLevel level ? BaneArms.weapon(level, c, r + 1, arms[r])
			: new ItemStack(arms[r]));
		if (s == Rivals.Side.BANE_KNIGHT) {
			mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(r >= 4 ? Items.NETHERITE_HELMET : Items.IRON_HELMET));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(r >= 4 ? Items.NETHERITE_CHESTPLATE : r >= 2 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
			if (r >= 3) {
				mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
			}
		} else if (s == Rivals.Side.BANE_WARLORD) {
			// (0.7.630) diamond is the crown's ({@link Regalia#plate}): a rank-five warlord wears the Armory's warlord plate
			Item top = Armory.piece("warlord", net.minecraft.world.item.equipment.ArmorType.CHESTPLATE);
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(r >= 4 ? (top != null ? top : Items.IRON_CHESTPLATE) : r >= 2 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
			if (r >= 1) {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(r >= 3 ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET));
			}
			if (r >= 2) {
				mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
	}

	// ---- it comes -----------------------------------------------------------------------------------------------------------

	/** {@code p} has a Bane: it comes for them now, if they're in a fight on its ground and it's been long enough. */
	private static void comes(ServerPlayer p, Captain c) {
		ServerLevel level = (ServerLevel)p.level();
		Bane b = p.getAttached(BANE);
		long now = level.getGameTime();
		if (b != null && c.rank() >= RANK_MAX && b.id().equals(c.id())) {
			// ((0.7.621) at its top rank it has a hold, or will: one that climbed there before holds were, too)
			BaneHolds.raise(level.getServer(), c, b.dim(), b.home());
		}
		if (b == null || VISITS.containsKey(c.tribe()) || now < b.next() || !fighting(p) || Downed.isDowned(p)) {
			return;
		}
		boolean ground = !c.fallen() && level.dimension().identifier().toString().equals(b.dim()) && p.position().distanceTo(Vec3.atCenterOf(b.home())) <= GROUND
			// ((0.7.621) with its hold standing it waits there: only its men come)
			&& BaneHolds.heartOf(level.getServer(), c.tribe()) == null;
		if (!ground) {
			// ((0.7.620) off its ground, or while it heals: now and then some of its men, who carry its marks)
			if (level.getRandom().nextFloat() < MEN_CHANCE) {
				men(level, p, c, MEN);
			}
			return;
		}
		if (Captains.filming(p) || PowersCompat.filming(p) || level.getRandom().nextFloat() >= ARRIVE_CHANCE) {
			// (never into another film)
			return;
		}
		arrive(level, p, c);
	}

	/** {@code p} has hurt something, or been hurt by something, in the last {@link #FIGHT_TICKS}. */
	private static boolean fighting(ServerPlayer p) {
		int hit = p.getLastHurtMobTimestamp();
		return hit > 0 && p.tickCount - hit < FIGHT_TICKS || p.getLastHurtByMob() != null && p.tickCount - p.getLastHurtByMobTimestamp() < FIGHT_TICKS;
	}

	/** It comes, with its band, in a film of its own and a line about last time. */
	private static void arrive(ServerLevel level, ServerPlayer p, Captain c) {
		RandomSource random = level.getRandom();
		Rivals.Side kind = Rivals.side(c.tribe());
		BlockPos spot = approach(level, p, random);
		if (spot == null) {
			return;
		}
		long now = level.getGameTime();
		// (a visit first: its body is known for one when it loads)
		VISITS.put(c.tribe(), new Visit(now, false));
		PathfinderMob body = body(level, kind, spot, random);
		if (body == null) {
			VISITS.remove(c.tribe());
			return;
		}
		String ladder = c.tribe();
		Captains.embodyIn(level, c, body);
		c = Captains.get(level.getServer(), c.id());
		if (c == null) {
			VISITS.remove(ladder);
			vanish(body);
			return;
		}
		Rulers.charge(body, p);
		List<UUID> band = new ArrayList<>();
		int count = Math.min(BAND_MAX, BAND_BASE + c.rank() / 2);
		for (int i = 0; i < count; i++) {
			BlockPos at = spot.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
			// (every band has living men in it, to be questioned: the knight's grave-robbers, the demon's cultists)
			boolean man = kind == Rivals.Side.BANE_WARLORD || i % 2 == 0;
			PathfinderMob m = follower(level, kind, man, c.tribe(), Construction.standable(level, at, 2), random);
			if (m != null && man) {
				BaneMarks.carry(m, c.tribe(), random);
			}
			if (m != null) {
				band.add(m.getUUID());
				Rulers.charge(m, p);
			}
		}
		BANDS.put(c.tribe(), band);
		puff(level, body, kind, 24);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.3F, kind == Rivals.Side.BANE_DEMON ? 0.7F : 1.0F);
		PowersCompat.film(body, p, PowersCompat.FILM_SPEAKER, ARRIVE_FILM, body.getEyePosition(), body, Captains.FILM_WITNESSES);
		Captains.hold(p, body, ARRIVE_FILM);
		Meeting m = c.met().get(p.getStringUUID());
		String last = m == null ? "again" : switch (m.last()) {
			case "killed", "slain" -> m.last();
			default -> "again";
		};
		Speech.say(level, body, Component.translatable("bane.jaymoskingdoms." + kind(kind) + ".arrives." + last + "." + random.nextInt(LINES),
			Recollections.nameTo(p, body), Component.literal(c.name())), Speech.RED, Captains.SAY_RANGE);
		Captains.put(level.getServer(), c.met(p.getUUID(), new Meeting(m == null ? 1 : m.times() + 1, m == null ? "met" : m.last(), now)));
	}

	/** (0.7.620) Some of its men turn up in {@code p}'s fight without it, each with one of its marks; a line from the first. */
	private static boolean men(ServerLevel level, ServerPlayer p, Captain c, int count) {
		RandomSource random = level.getRandom();
		Rivals.Side kind = Rivals.side(c.tribe());
		BlockPos spot = approach(level, p, random);
		if (spot == null) {
			return false;
		}
		VISITS.put(c.tribe(), new Visit(level.getGameTime(), true));
		List<UUID> band = new ArrayList<>();
		PathfinderMob first = null;
		for (int i = 0; i < count; i++) {
			BlockPos at = spot.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
			PathfinderMob m = follower(level, kind, true, c.tribe(), Construction.standable(level, at, 2), random);
			if (m != null) {
				BaneMarks.carry(m, c.tribe(), random);
				Rulers.charge(m, p);
				band.add(m.getUUID());
				first = first == null ? m : first;
			}
		}
		if (first == null) {
			VISITS.remove(c.tribe());
			return false;
		}
		BANDS.put(c.tribe(), band);
		Speech.say(level, first, Component.translatable("bane.jaymoskingdoms." + kind(kind) + ".men." + random.nextInt(LINES), Recollections.nameTo(p, first),
			Component.literal(c.name())), Speech.RED, Captains.SAY_RANGE);
		return true;
	}

	/** (0.7.625, for BaneCut) {@code count} of its men come at {@code p} in a place it holds. True if any came. */
	static boolean claimMen(ServerLevel level, ServerPlayer p, Captain c, int count) {
		if (VISITS.containsKey(c.tribe()) || Captains.filming(p) || PowersCompat.filming(p)) {
			return false;
		}
		return men(level, p, c, count);
	}

	/** (0.7.625, for BaneCut) It, or some of its men, are about just now. */
	static boolean visiting(String ladder) {
		return VISITS.containsKey(ladder);
	}

	/** Where it comes from: on the ground {@link #ARRIVE_MIN}-{@link #ARRIVE_MAX} off, near {@code p}'s height. */
	private static @Nullable BlockPos approach(ServerLevel level, ServerPlayer p, RandomSource random) {
		for (int i = 0; i < 12; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double r = ARRIVE_MIN + random.nextDouble() * (ARRIVE_MAX - ARRIVE_MIN);
			BlockPos near = BlockPos.containing(p.getX() + Math.cos(angle) * r, p.getY(), p.getZ() + Math.sin(angle) * r);
			if (!level.isPositionEntityTicking(near)) {
				continue;
			}
			BlockPos spot = Construction.standable(level, near, 3);
			if (level.getBlockState(spot.below()).isSolid() && Math.abs(spot.getY() - p.getY()) <= 6) {
				return spot;
			}
		}
		return null;
	}

	/** Its body: a wither skeleton for the knight, a villager outlaw (a vindicator without MCA) for the warlord, a Hornkin brute for the demon. */
	private static @Nullable PathfinderMob body(ServerLevel level, Rivals.Side kind, BlockPos at, RandomSource random) {
		PathfinderMob mob = switch (kind) {
			case BANE_WARLORD -> outlaw(level, at, random);
			case BANE_DEMON -> Nether.HORNKIN_BRUTE.create(level, EntitySpawnReason.EVENT);
			default -> EntityTypes.WITHER_SKELETON.create(level, EntitySpawnReason.EVENT);
		};
		if (mob == null) {
			return null;
		}
		mob.addTag(BODY_TAG);
		mob.setPersistenceRequired();
		if (level.getEntity(mob.getUUID()) == null) {
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.addFreshEntity(mob)) {
				return null;
			}
		}
		return mob;
	}

	/**
	 * One of its band, tagged for {@code ladder}: a living outlaw ({@code man}: the warlord's cutthroats, the knight's grave-robbers,
	 * the demon's cultists, all of them villagers who can be questioned), else the dead (zombies, skeletons) or Hornkin warriors.
	 */
	private static @Nullable PathfinderMob follower(ServerLevel level, Rivals.Side kind, boolean man, String ladder, BlockPos at, RandomSource random) {
		PathfinderMob mob = man ? outlaw(level, at, random) : switch (kind) {
			case BANE_DEMON -> Nether.HORNKIN.create(level, EntitySpawnReason.EVENT);
			default -> random.nextBoolean() ? EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT) : EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
		};
		if (mob == null) {
			return null;
		}
		mob.addTag(BAND_TAG + ladder);
		mob.addTag(Rivals.ROLLED_TAG);
		if (man) {
			// (a cutthroat's blade; a grave-robber's shovel; a cultist's dagger-sword and a gold charm)
			Item held = switch (kind) {
				case BANE_KNIGHT -> Items.IRON_SHOVEL;
				case BANE_DEMON -> Items.GOLDEN_SWORD;
				default -> random.nextBoolean() ? Items.IRON_SWORD : Items.STONE_AXE;
			};
			mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(held));
			mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
			if (kind == Rivals.Side.BANE_KNIGHT) {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			}
		} else switch (kind) {
			case BANE_KNIGHT -> {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(mob instanceof AbstractSkeleton ? Items.BOW : Items.IRON_SWORD));
				// (a helmet against the sun)
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
			}
			default -> {
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setDropChance(slot, 0.0F);
		}
		if (level.getEntity(mob.getUUID()) == null) {
			mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.addFreshEntity(mob)) {
				return null;
			}
		}
		return mob;
	}

	/** An outlaw: an MCA villager stranger whose doings are ours (his brain set aside), or a vindicator without MCA. */
	private static @Nullable PathfinderMob outlaw(ServerLevel level, BlockPos at, RandomSource random) {
		PathfinderMob mob = McaCompat.stranger(level, at, random.nextFloat() * 360.0F, Looters.LOOTER, at, 48);
		return mob != null ? mob : EntityTypes.VINDICATOR.create(level, EntitySpawnReason.EVENT);
	}

	private static void puff(ServerLevel level, Entity at, Rivals.Side kind, int count) {
		SimpleParticleType p = switch (kind) {
			case BANE_DEMON -> ParticleTypes.FLAME;
			case BANE_WARLORD -> ParticleTypes.LARGE_SMOKE;
			default -> ParticleTypes.SOUL;
		};
		level.sendParticles(p, at.getX(), at.getY() + at.getBbHeight() * 0.5, at.getZ(), count, 0.4, 0.6, 0.4, 0.03);
	}

	// ---- while it's here ----------------------------------------------------------------------------------------------------

	/** Every few ticks: each Bane here fights its owner (its band too), and goes when it's over. */
	private static void visits(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		for (Map.Entry<String, Visit> e : List.copyOf(VISITS.entrySet())) {
			String ladder = e.getKey();
			Visit v = e.getValue();
			if (v.men) {
				men(level, ladder, v, now);
				continue;
			}
			Captain c = null;
			for (Captain x : Captains.of(server, ladder)) {
				c = x;
			}
			Entity body = c == null || c.body().isEmpty() ? null : level.getEntity(c.body().get());
			if (body == null && v.hold != null && c != null && c.body().isPresent()) {
				// ((0.7.621) its body at its hold unloaded with the land: the waiting's over till someone comes again)
				boolean anywhere = false;
				for (ServerLevel l : server.getAllLevels()) {
					anywhere |= l.getEntity(c.body().get()) != null;
				}
				if (!anywhere) {
					Captains.put(server, c.withBody(null));
					end(level, ladder);
				}
				continue;
			}
			if (body == null) {
				if (c == null || c.fallen() || c.body().isEmpty()) {
					// (it fell, or was sent off)
					end(level, ladder);
				}
				// (else its body is in another world: that world's tick has it)
				continue;
			}
			UUID ownerId = owner(ladder);
			ServerPlayer p = ownerId == null ? null : server.getPlayerList().getPlayer(ownerId);
			if (v.hold != null) {
				// ((0.7.621) at its hold: it waits while anyone's near, after whoever comes; quietly gone when all have left)
				if (!anyNear(level, v.hold, BaneHolds.GARRISON_NEAR + 16.0)) {
					quiet(level, c, (LivingEntity)body);
					continue;
				}
				for (UUID id : BANDS.getOrDefault(ladder, List.of())) {
					if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive() && !(m instanceof Worker) && !BaneHolds.sent(id)) {
						guard(m, p, v.hold);
					}
				}
				if (body instanceof PathfinderMob mob && !(body instanceof Worker) && !Downed.isDowned(mob)) {
					guard(mob, p, v.hold);
				}
				continue;
			}
			if (p != null && !p.isAlive() && v.ownerDead < 0) {
				v.ownerDead = now;
			}
			boolean gone = p == null || p.level() != level || p.isAlive() && p.distanceTo(body) > LEAVE_FAR || now - v.lastBlow > LEAVE_QUIET
				|| v.ownerDead >= 0 && now - v.ownerDead > LEAVE_AFTER_KILL;
			if (gone) {
				leave(level, c, (LivingEntity)body, "leaves");
				continue;
			}
			if (now < v.at + ARRIVE_FILM) {
				continue;
			}
			// (its band, after whoever is nearest of those fighting it, its owner first)
			for (UUID id : BANDS.getOrDefault(ladder, List.of())) {
				if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive() && !(m instanceof Worker)) {
					LivingEntity foe = foe(m, p);
					if (foe != null && m.getTarget() != foe) {
						m.setTarget(foe);
					}
				}
			}
			if (body instanceof PathfinderMob mob && !(body instanceof Worker) && !Downed.isDowned(mob)) {
				LivingEntity foe = foe(mob, p);
				if (foe != null && mob.getTarget() != foe) {
					mob.setTarget(foe);
				}
			}
		}
	}

	/** (0.7.620) Its men without it: after its owner (or whoever's on them), and gone when the fight's over or all are down. */
	private static void men(ServerLevel level, String ladder, Visit v, long now) {
		UUID ownerId = owner(ladder);
		ServerPlayer p = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (p != null && !p.isAlive() && v.ownerDead < 0) {
			v.ownerDead = now;
		}
		double nearest = Double.MAX_VALUE;
		boolean any = false;
		for (UUID id : BANDS.getOrDefault(ladder, List.of())) {
			if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive()) {
				any = true;
				if (p != null && p.level() == level) {
					nearest = Math.min(nearest, p.distanceTo(m));
				}
			}
		}
		if (!any || p == null || p.level() != level || nearest > LEAVE_FAR || now - v.lastBlow > LEAVE_QUIET
			|| v.ownerDead >= 0 && now - v.ownerDead > LEAVE_AFTER_KILL) {
			end(level, ladder);
		}
	}

	/** Who {@code mob} goes for: its owner, or a player who's hurt it lately and is nearer. */
	private static @Nullable LivingEntity foe(PathfinderMob mob, @Nullable ServerPlayer owner) {
		LivingEntity by = mob.getLastHurtByMob();
		boolean ownerUp = owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == mob.level();
		if (by instanceof ServerPlayer other && other.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < 100
			&& (!ownerUp || other.distanceTo(mob) < owner.distanceTo(mob))) {
			return other;
		}
		return ownerUp ? owner : by != null && by.isAlive() ? by : null;
	}

	/** (From {@code Workers.step}) A Bane or one of its band in a villager's body: its own doings in place of its brain. */
	static boolean villagerStep(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		String ladder = bandOf(mob);
		if (ladder == null) {
			return false;
		}
		Visit v = VISITS.get(ladder);
		if (v == null && !is(mob) && !Downed.isDowned(mob)) {
			// ((0.7.620) one left down when the rest went, back on his feet: he slips off too)
			vanish(mob);
			return true;
		}
		if (is(mob)) {
			// (his fears, rage and moves, the players he meets)
			if (Captains.step(level, w)) {
				return true;
			}
		}
		if (v == null || !v.men && level.getGameTime() < v.at + ARRIVE_FILM || Downed.isDowned(mob)) {
			mob.getNavigation().stop();
			return true;
		}
		UUID ownerId = owner(ladder);
		ServerPlayer p = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (v.hold != null) {
			// ((0.7.621) at its hold; one sent to tear a banner down is steered by BaneHolds)
			if (!BaneHolds.sent(mob.getUUID())) {
				LivingEntity foe = holdFoe(mob, p, v.hold);
				if (foe != null) {
					fight(level, mob, foe);
				} else if (mob.position().distanceTo(Vec3.atCenterOf(v.hold)) > HOLD_STRAY && mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(v.hold.getX() + 0.5, v.hold.getY(), v.hold.getZ() + 0.5, 1.0);
				}
			}
			return true;
		}
		LivingEntity foe = foe(mob, p);
		if (foe != null) {
			fight(level, mob, foe);
		}
		return true;
	}

	/** (0.7.621) A mob of its hold: after whoever comes, home when there's none. */
	private static void guard(PathfinderMob mob, @Nullable ServerPlayer owner, BlockPos hold) {
		LivingEntity foe = holdFoe(mob, owner, hold);
		if (foe != null) {
			if (mob.getTarget() != foe) {
				mob.setTarget(foe);
			}
		} else if (mob.position().distanceTo(Vec3.atCenterOf(hold)) > HOLD_STRAY && mob.getNavigation().isDone()) {
			mob.getNavigation().moveTo(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5, 1.0);
		}
	}

	/** (0.7.621) Who a mob of its hold goes for: its owner in its hold, else whoever hurt it lately or comes within {@link #HOLD_SEES}. */
	private static @Nullable LivingEntity holdFoe(PathfinderMob mob, @Nullable ServerPlayer owner, BlockPos hold) {
		Vec3 heart = Vec3.atCenterOf(hold);
		if (owner != null && owner.isAlive() && !owner.isSpectator() && !owner.isCreative() && owner.level() == mob.level()
			&& owner.position().distanceTo(heart) <= BaneHolds.WARD_RANGE) {
			return owner;
		}
		LivingEntity by = mob.getLastHurtByMob();
		if (by != null && by.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < 200) {
			return by;
		}
		ServerPlayer near = null;
		for (ServerPlayer p : ((ServerLevel)mob.level()).players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceTo(mob) <= HOLD_SEES && (near == null || p.distanceTo(mob) < near.distanceTo(mob))) {
				near = p;
			}
		}
		return near;
	}

	private static boolean anyNear(ServerLevel level, BlockPos at, double range) {
		Vec3 v = Vec3.atCenterOf(at);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && p.position().distanceTo(v) <= range) {
				return true;
			}
		}
		return false;
	}

	/**
	 * (0.7.621, from BaneHolds) Someone's near its standing hold: it and its band wait there ({@link BaneHolds#GARRISON}, half
	 * of them its men, with its marks), unless they already do or it's out.
	 */
	static void garrison(ServerLevel level, Captain c, BlockPos heart) {
		String ladder = c.tribe();
		if (VISITS.containsKey(ladder) || c.body().isPresent()) {
			return;
		}
		RandomSource random = level.getRandom();
		Rivals.Side kind = Rivals.side(ladder);
		long now = level.getGameTime();
		VISITS.put(ladder, new Visit(now - ARRIVE_FILM, false, heart));
		PathfinderMob body = body(level, kind, Construction.standable(level, heart.offset(0, 0, -2), 2), random);
		if (body == null) {
			VISITS.remove(ladder);
			return;
		}
		Captains.embodyIn(level, c, body);
		List<UUID> band = new ArrayList<>();
		for (int i = 0; i < BaneHolds.GARRISON; i++) {
			int half = BaneHolds.HALF - 2;
			BlockPos at = heart.offset(random.nextInt(2 * half + 1) - half, 0, random.nextInt(2 * half + 1) - half);
			boolean man = kind == Rivals.Side.BANE_WARLORD || i % 2 == 0;
			PathfinderMob m = follower(level, kind, man, ladder, Construction.standable(level, at, 2), random);
			if (m != null && man) {
				BaneMarks.carry(m, ladder, random);
			}
			if (m != null) {
				band.add(m.getUUID());
			}
		}
		BANDS.put(ladder, band);
	}

	/** (0.7.621, for BaneHolds) The ids of {@code ladder}'s band here now. */
	static List<UUID> bandIds(String ladder) {
		return BANDS.getOrDefault(ladder, List.of());
	}

	/** (0.7.621) Everyone has left its hold: it and its band are quietly gone till someone comes again. */
	private static void quiet(ServerLevel level, Captain c, LivingEntity body) {
		Captain now = Captains.get(level.getServer(), c.id());
		if (now != null) {
			Captains.put(level.getServer(), now.withBody(null));
		}
		vanish(body);
		end(level, c.tribe());
	}

	/** A villager's blade, his own doing. */
	private static void fight(ServerLevel level, PathfinderMob a, LivingEntity foe) {
		a.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		if (a.distanceTo(foe) > REACH) {
			if (a.tickCount % 10 == 0 || a.getNavigation().isDone()) {
				a.getNavigation().moveTo(foe, 1.2);
			}
			return;
		}
		a.getNavigation().stop();
		if (a.tickCount % SWING_TICKS == 0) {
			a.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			a.doHurtTarget(level, foe);
		}
	}

	/** It goes (with a line of {@code what}), and its band with it; next time not for {@link #ARRIVE_GAP}. */
	private static void leave(ServerLevel level, Captain c, LivingEntity body, String what) {
		Rivals.Side kind = Rivals.side(c.tribe());
		Speech.say(level, body, Component.translatable("bane.jaymoskingdoms." + kind(kind) + "." + what + "." + level.getRandom().nextInt(LINES)),
			Speech.RED, Captains.SAY_RANGE);
		puff(level, body, kind, 30);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 0.6F);
		Captain now = Captains.get(level.getServer(), c.id());
		if (now != null) {
			Captains.put(level.getServer(), now.withBody(null));
		}
		vanish(body);
		end(level, c.tribe());
		// ((0.7.621) and from its hold a while)
		BaneHolds.away(c.tribe(), level.getGameTime());
	}

	/** Its visit is over: its band goes, and it won't come again for a while. */
	private static void end(ServerLevel level, String ladder) {
		VISITS.remove(ladder);
		for (UUID id : BANDS.getOrDefault(ladder, List.of())) {
			Entity m = null;
			for (ServerLevel l : level.getServer().getAllLevels()) {
				m = m == null ? l.getEntity(id) : m;
			}
			if (m != null && m.isAlive() && m.level() instanceof ServerLevel there && !(m instanceof LivingEntity l && Downed.isDowned(l))) {
				// ((0.7.620) one lying downed stays, to be questioned)
				puff(there, m, Rivals.side(ladder), 8);
				vanish(m);
			}
		}
		BANDS.remove(ladder);
		UUID ownerId = owner(ladder);
		ServerPlayer p = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		Bane b = p == null ? null : p.getAttached(BANE);
		if (b != null) {
			p.setAttached(BANE, b.next(Factions.level(level.getServer()).getGameTime() + ARRIVE_GAP));
		}
	}

	private static void vanish(Entity e) {
		if (e instanceof Worker) {
			McaCompat.vanish(e);
		} else {
			e.discard();
		}
	}

	/** A body or one of a band that loads with no visit going (left from before a reload): gone. */
	private static void loaded(Entity e, ServerLevel level) {
		String ladder = e.entityTags().contains(BODY_TAG) ? "body" : followerOf(e);
		if (ladder == null) {
			return;
		}
		if (e.entityTags().contains(BODY_TAG)) {
			Captain c = Captains.of(e);
			if (c != null && VISITS.containsKey(c.tribe())) {
				return;
			}
			if (c != null && c.body().map(e.getUUID()::equals).orElse(false)) {
				Captains.put(level.getServer(), c.withBody(null));
			}
		} else if (VISITS.containsKey(ladder)) {
			return;
		}
		vanish(e);
	}

	// ---- blows -------------------------------------------------------------------------------------------------------------------

	/** (From {@code Captains.hurt}) A blow: its owner's tallied (what it learns); its owner downed by it (a rank for it). */
	static void hurt(ServerLevel level, LivingEntity victim, DamageSource source, float taken) {
		if (VISITS.isEmpty() && !is(victim)) {
			return;
		}
		Entity by = source.getEntity();
		String hit = bandOf(victim), striker = by == null ? null : bandOf(by);
		Visit v = hit != null ? VISITS.get(hit) : striker != null ? VISITS.get(striker) : null;
		if (v != null) {
			v.lastBlow = level.getGameTime();
		}
		if (hit != null && is(victim) && by instanceof ServerPlayer p && p.getUUID().equals(owner(hit)) && taken > 0.0F) {
			CaptainWays.Cause cause = CaptainWays.cause(source);
			Captain c = Captains.of(victim);
			if (cause != null && c != null) {
				ServerLevel world = Factions.level(level.getServer());
				Map<String, Map<String, Float>> all = new HashMap<>(world.getAttachedOrElse(TALLY, Map.of()));
				Map<String, Float> mine = new HashMap<>(all.getOrDefault(c.id(), Map.of()));
				mine.merge(cause.name(), taken, Float::sum);
				all.put(c.id(), Map.copyOf(mine));
				world.setAttached(TALLY, Map.copyOf(all));
			}
		}
		if (striker != null && v != null && !v.downed && is(by) && victim instanceof ServerPlayer p && p.getUUID().equals(owner(striker)) && Downed.isDowned(p)) {
			v.downed = true;
			Captain c = Captains.of(by);
			if (c != null && by instanceof LivingEntity body) {
				downed(level, c, body, p);
			}
		}
	}

	/** It downed its owner: up a rank, a strength, better arms, a line and a note. */
	private static void downed(ServerLevel level, Captain c, LivingEntity body, ServerPlayer p) {
		Captain after = Captains.promote(level, c, body);
		Rivals.Side kind = Rivals.side(after.tribe());
		if (body instanceof Mob mob) {
			gear(mob, after, after.rank(), kind);
		}
		Speech.say(level, body, Component.translatable("bane.jaymoskingdoms." + kind(kind) + ".downed." + level.getRandom().nextInt(LINES),
			Recollections.nameTo(p, body)), Speech.RED, Captains.SAY_RANGE);
		climbed(level, c, after, p);
	}

	/** (From {@code Captains.slew}) It killed someone: its better arms; its owner gets its note. */
	static void slew(ServerLevel level, Captain c, LivingEntity killer, ServerPlayer player) {
		if (!bane(c.tribe())) {
			return;
		}
		Rivals.Side kind = Rivals.side(c.tribe());
		if (killer instanceof Mob mob) {
			gear(mob, c, c.rank(), kind);
		}
		if (player.getUUID().equals(owner(c.tribe()))) {
			climbed(level, null, c, player);
		}
	}

	/** Its note to {@code p}, by their messenger, and word on the boards if it climbed. */
	private static void climbed(ServerLevel level, @Nullable Captain before, Captain after, ServerPlayer p) {
		Rivals.Side kind = Rivals.side(after.tribe());
		Post.report(p.getUUID(), Component.translatable("bane.jaymoskingdoms." + kind(kind) + ".note." + level.getRandom().nextInt(NOTES), p.getDisplayName(),
			Component.literal(after.name()), after.rank()));
		if (before == null || after.rank() > before.rank()) {
			Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.bane.climbed", Component.literal(after.name()), p.getDisplayName(), after.rank()));
		}
		// ((0.7.621) at its top rank it raises its hold on its ground)
		Bane b = p.getAttached(BANE);
		if (after.rank() >= RANK_MAX && b != null && b.id().equals(after.id())) {
			BaneHolds.raise(level.getServer(), after, b.dim(), b.home());
		}
	}

	/** (From {@code Captains.died}) It fell: its band goes; its owner knows it isn't over. */
	static void fell(ServerLevel level, Captain c, LivingEntity body, DamageSource source) {
		end(level, c.tribe());
		UUID ownerId = owner(c.tribe());
		ServerPlayer p = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		if (ENDING.remove(c.id())) {
			// ((0.7.620) its own marks: it's over)
			ended(level, c, p);
			puff(level, body, Rivals.side(c.tribe()), 40);
			return;
		}
		if (p != null) {
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.not_over", c.name()).withStyle(ChatFormatting.DARK_RED));
		}
		puff(level, body, Rivals.side(c.tribe()), 30);
	}

	/** (0.7.620) {@code c} is falling for good to its own marks (for {@code Captains.died}). */
	static boolean ending(Captain c) {
		return ENDING.contains(c.id());
	}

	/**
	 * (0.7.620) Its owner's blow that would kill it. A finisher (downed, or its head off) with marks carved in the blade is
	 * judged: the right three end it (killed, or sworn if they sneak); wrong ones and it's up, laughing, a rank higher, and gone.
	 * No marks, or no finisher: it falls as before. True if it dies.
	 */
	private static boolean finished(ServerLevel level, Captain c, LivingEntity body, ServerPlayer p) {
		if (BaneHolds.warded(body)) {
			// ((0.7.621) its walls stand: storm them first)
			body.setHealth(body.getMaxHealth() * WARDED_HEALTH);
			Speech.say(level, body, Component.translatable("bane.jaymoskingdoms." + kind(Rivals.side(c.tribe())) + ".warded." + level.getRandom().nextInt(LINES)),
				Speech.RED, Captains.SAY_RANGE);
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.warded", c.name()).withStyle(ChatFormatting.DARK_RED));
			return false;
		}
		boolean finisher = Downed.isDowned(body) || body.entityTags().contains(Captains.MISSING_TAG + "head");
		if (!finisher) {
			return true;
		}
		Rivals.Side kind = Rivals.side(c.tribe());
		switch (BaneMarks.judge(p, c)) {
			case NONE -> {
				return true;
			}
			case WRONG -> {
				BaneMarks.flash(level, body, false);
				body.setHealth(body.getMaxHealth() * WRONG_HEALTH);
				body.addTag(Downed.REVIVE_TAG);
				Captain after = Captains.promote(level, c, body);
				if (body instanceof Mob mob) {
					gear(mob, after, after.rank(), kind);
				}
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.wrong", Component.literal(after.name())).withStyle(ChatFormatting.DARK_RED));
				climbed(level, c, after, p);
				leave(level, after, body, "laughs");
				return false;
			}
			default -> {
				BaneMarks.flash(level, body, true);
				if (p.isShiftKeyDown()) {
					sworn(level, c, body, p);
					return false;
				}
				ENDING.add(c.id());
				// (its weapon is yours: (0.7.621) its head, element and all, for the Tool Station)
				ItemStack arms = BaneArms.spoils(c, body.getMainHandItem());
				if (!arms.isEmpty() && !p.getInventory().add(arms)) {
					body.spawnAtLocation(level, arms);
				}
				return true;
			}
		}
	}

	/** (0.7.620) Ended by its own marks: no more Bane for {@code p} (a new brand may come), and word on the boards. */
	private static void ended(ServerLevel level, Captain c, @Nullable ServerPlayer p) {
		MinecraftServer server = level.getServer();
		forgetAll(server, c);
		if (p != null) {
			p.removeAttached(BANE);
			List<String> sworn = new ArrayList<>(p.getAttachedOrElse(Captains.SWORN, List.of()));
			if (sworn.remove(c.id())) {
				p.setAttached(Captains.SWORN, List.copyOf(sworn));
			}
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.ended", c.name()).withStyle(ChatFormatting.GOLD));
			Post.news(server, Component.translatable("news.jaymoskingdoms.bane.ended", Component.literal(c.name()), p.getDisplayName()));
		}
	}

	/** (0.7.620) Finished with its own marks while its owner held back: sworn to them, its traits and temper kept. */
	private static void sworn(ServerLevel level, Captain c, LivingEntity body, ServerPlayer p) {
		MinecraftServer server = level.getServer();
		body.setHealth(body.getMaxHealth() * SWORN_HEALTH);
		body.removeTag(BODY_TAG);
		end(level, c.tribe());
		forgetAll(server, c);
		p.removeAttached(BANE);
		SwornCaptains.swearBane(level, p, body, c);
		p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.sworn", c.name()).withStyle(ChatFormatting.GOLD));
		Post.news(server, Component.translatable("news.jaymoskingdoms.bane.sworn", Component.literal(c.name()), p.getDisplayName()));
	}

	/** Its key, what's known of it and its tally. */
	private static void forgetAll(MinecraftServer server, Captain c) {
		BaneMarks.forget(server, c.id());
		BaneHolds.forget(server, c.id());
		ServerLevel world = Factions.level(server);
		Map<String, Map<String, Float>> tally = new HashMap<>(world.getAttachedOrElse(TALLY, Map.of()));
		if (tally.remove(c.id()) != null) {
			world.setAttached(TALLY, Map.copyOf(tally));
		}
	}

	/** (From {@code Captains.dealt}) The harm its owner has dealt it most, once there's been enough of it: taken at {@link #RESIST}. */
	static float resist(LivingEntity self, DamageSource source) {
		if (!is(self) || !(self.level() instanceof ServerLevel level)) {
			return 1.0F;
		}
		Captain c = Captains.of(self);
		CaptainWays.Cause cause = c == null ? null : CaptainWays.cause(source);
		if (cause == null) {
			return 1.0F;
		}
		Map<String, Float> mine = Factions.level(level.getServer()).getAttachedOrElse(TALLY, Map.of()).getOrDefault(c.id(), Map.of());
		String top = null;
		float most = 0.0F;
		for (Map.Entry<String, Float> e : mine.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				top = e.getKey();
			}
		}
		if (top == null || most < RESIST_FROM || !top.equals(cause.name())) {
			return 1.0F;
		}
		if (source.getEntity() instanceof ServerPlayer p) {
			long now = level.getGameTime();
			Long told = TOLD.get(p.getUUID());
			if (told == null || now - told > 200L) {
				TOLD.put(p.getUUID(), now);
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bane.learned." + cause.name().toLowerCase(java.util.Locale.ROOT), c.name())
					.withStyle(ChatFormatting.GRAY));
			}
		}
		return RESIST;
	}

	/** Only its owner finishes it: struck down by anyone (or anything) else, it gets up and goes, to wait for them. */
	private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!is(entity) || !(entity.level() instanceof ServerLevel level)) {
			return true;
		}
		Captain c = Captains.of(entity);
		if (c == null) {
			return true;
		}
		UUID ownerId = owner(c.tribe());
		if (source.getEntity() instanceof ServerPlayer p && p.getUUID().equals(ownerId)) {
			return finished(level, c, entity, p);
		}
		entity.setHealth(entity.getMaxHealth() * NOT_YOURS_HEALTH);
		ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);
		Rivals.Side kind = Rivals.side(c.tribe());
		Speech.say(level, entity, Component.translatable("bane.jaymoskingdoms." + kind(kind) + ".not_yours." + level.getRandom().nextInt(LINES),
			owner == null ? Component.literal("?") : owner.getDisplayName()), Speech.RED, Captains.SAY_RANGE);
		Visit v = VISITS.get(c.tribe());
		if (v == null || v.hold == null) {
			// ((0.7.621) in its hold it stands its ground)
			leave(level, c, entity, "leaves");
		}
		return false;
	}
}
