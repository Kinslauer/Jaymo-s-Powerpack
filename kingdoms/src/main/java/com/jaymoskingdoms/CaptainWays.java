package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.Captains.Sort;
import com.jaymoskingdoms.Captains.Trait;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.616) Rivals patch 1, <b>more to be</b> (ideas from Shadow of War, a commercial game: our own code, names and lines).
 * What {@link Captains} leaves to this class:
 * <ul>
 * <li><b>The new traits' doings</b>: a parry reader's blows go past your guard once you've parried him twice; a blood
 * tracker comes for anyone bleeding near; a pack leader calls wolves; a watchful one sees you sneaking or unseen; a witch
 * hunter shrugs off spells and ki; an executioner hits the downed twice as hard and goes for them; a prayerful one gets a
 * second wind once; the iron-clad are armoured and slow; a rock thrower throws; a shield bearer carries a shield that
 * turns blows from the front; an iron jaw takes blunt blows and fists; the heavy-footed are slow; a sot misses; a
 * bargainer buys his life with coin and runs; the gun-shy run from shots.</li>
 * <li><b>Learning from how they lost</b>: every blow a player (or their beast) lands on a captain is tallied by kind;
 * one who comes back swaps the trait that failed him for its answer ({@link #ANSWERS}) and carries a mark of it.</li>
 * <li><b>Marks you can see</b>: the {@link #MARKS} he carries, sent to every client ({@code CaptainMarks} draws them).</li>
 * <li><b>Signature moves</b>: from rank {@link #MOVE_RANK} a captain has one move of his own ({@link Sort#MOVE}), each
 * with a red glint before it, which nobody parries.</li>
 * </ul>
 */
public final class CaptainWays {
	// ================================================================================================================
	// Tuning
	/** A parry reader: after this many of his blows are parried, his blows go past a guard for this long (ticks). */
	static final int READ_AFTER = 2, READ_TICKS = 200;
	/** A blood tracker smells a bleeding player this far; a watchful one sees a sneaking or unseen one this far. */
	static final double TRACK_RANGE = 32.0, WATCH_RANGE = 14.0;
	/** A pack leader's wolves: how many, how far they spawn, how long they stay (ticks). */
	static final int PACK_SIZE = 2, PACK_TICKS = 1200;
	static final double PACK_SPREAD = 4.0;
	/** A witch hunter's share of a spell's or ki's blow; an iron jaw's of a blunt blow or a fist; a shield's from the front. */
	static final float WITCH_HUNTER = 0.5F, IRON_JAW = 0.5F, SHIELD_FRONT = 0.4F;
	/** An executioner on someone downed: times as hard; how far he goes for the downed. */
	static final float EXECUTIONER = 2.0F;
	static final double EXECUTE_RANGE = 10.0;
	/** A prayerful one's second wind: below this share of his health, once a fight, for this long (ticks). */
	static final float PRAY_AT = 0.3F;
	static final int PRAY_TICKS = 160;
	/** Iron-clad: armour more, speed times; heavy-footed: speed times. */
	static final double IRON_ARMOUR = 6.0, IRON_SPEED = -0.15, HEAVY_SPEED = -0.2;
	/** A rock thrower throws between these ranges, this often (ticks); a rock and a thrown axe hit this hard. */
	static final double THROW_MIN = 5.0, THROW_MAX = 18.0;
	static final int THROW_EVERY = 70;
	static final float ROCK_DAMAGE = 4.0F, AXE_DAMAGE = 9.0F;
	/** A sot's blows miss at this chance. */
	static final float SOT_MISS = 0.25F;
	/** A bargainer buys his life below this share of his health, with this much bent worth, and runs this long (ticks). */
	static final float BARGAIN_AT = 0.25F;
	static final int BARGAIN_WORTH = 6, BARGAIN_RUN = 200;
	/** The gun-shy run from a shot at them or this near them, within this long of it (ticks), for this long. */
	static final double GUN_NEAR = 8.0;
	static final int GUN_FRESH = 40, GUN_RUN = 120;
	/** A blow counts toward what he learns from only if it's at least this much. */
	static final float TALLY_MIN = 0.5F;
	/** Signature moves from this rank; how often (ticks, and a random extra); the glint's lead (ticks) for each. */
	static final int MOVE_RANK = 4, MOVE_EVERY = 160, MOVE_JITTER = 80;
	static final int CHARGE_LEAD = 14, SLAM_LEAD = 16, AXE_LEAD = 12, GRAB_LEAD = 12;
	/** Shield charge: from this range to this, how fast he goes, how long he keeps going (ticks), how hard it hits (times his blow). */
	static final double CHARGE_MIN = 4.0, CHARGE_MAX = 12.0, CHARGE_SPEED = 1.1;
	static final int CHARGE_TICKS = 12;
	static final float CHARGE_HIT = 1.3F;
	/** Ground slam: in this range, its reach, how hard (times his blow); anyone in the air when it lands is spared. */
	static final double SLAM_RANGE = 3.5, SLAM_REACH = 4.5;
	static final float SLAM_HIT = 1.4F;
	/** Grab: in this range, how hard (times his blow), how far up and out he throws you. */
	static final double GRAB_RANGE = 2.6, GRAB_UP = 0.7, GRAB_OUT = 1.3;
	static final float GRAB_HIT = 0.9F;
	/** Lines for each move and each new trait's doing. */
	static final int LINES = 2;
	// ================================================================================================================

	/** What a captain learns when he comes back from a beating, by what beat him: the trait he gains, those he loses, his mark. */
	record Answer(Trait gains, List<Trait> loses, String mark) {
	}

	enum Cause {
		FIRE, SHOT, BLUNT, BLADE, MAGIC, BEAST
	}

	static final Map<Cause, Answer> ANSWERS = new EnumMap<>(Map.of(
		Cause.FIRE, new Answer(Trait.FIRE_BORN, List.of(Trait.BURNS_EASILY, Trait.FEARS_FIRE), "burned"),
		Cause.SHOT, new Answer(Trait.SHIELD_BEARER, List.of(Trait.SOFT_TO_ARROWS, Trait.GUN_SHY), "eye_patch"),
		Cause.BLUNT, new Answer(Trait.IRON_JAW, List.of(Trait.GLASS_JAW), "iron_jaw"),
		Cause.BLADE, new Answer(Trait.THICK_HIDE, List.of(), "stitched"),
		Cause.MAGIC, new Answer(Trait.WITCH_HUNTER, List.of(), "pale_eye"),
		Cause.BEAST, new Answer(Trait.PACK_LEADER, List.of(Trait.FEARS_BEASTS), "claws")));

	/** The marks a captain's body can show, in bit order ({@link #SHOWN}); a scar entry is {@code mark:<name>}. */
	public static final List<String> MARKS = List.of("burned", "eye_patch", "iron_jaw", "stitched", "pale_eye", "claws");
	static final String MARK = "mark:", LEARNED = "learned:";

	/** On a captain's body: the bits of the {@link #MARKS} he shows. Saved, and sent to every client. */
	public static final AttachmentType<Integer> SHOWN = AttachmentRegistry.create(JaymosKingdoms.id("captain_marks"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	private static final Identifier IRON_ARMOUR_ID = JaymosKingdoms.id("captain_iron_clad"), IRON_SPEED_ID = JaymosKingdoms.id("captain_iron_slow"),
		HEAVY_ID = JaymosKingdoms.id("captain_heavy_footed");

	/** A move on its way: which, at whom, when it goes. */
	private record Pending(Trait move, LivingEntity target, long at) {
	}

	private static final Map<UUID, Integer> PARRIED = new HashMap<>();
	private static final Map<UUID, Long> READING = new HashMap<>(), NEXT_MOVE = new HashMap<>(), NEXT_THROW = new HashMap<>(), SHOT_AT = new HashMap<>();
	private static final Map<UUID, Pending> PENDING = new HashMap<>();
	private static final Map<UUID, Long> CHARGING = new HashMap<>();
	private static final Map<UUID, LivingEntity> CHARGE_AT = new HashMap<>();
	private static final Set<UUID> PACKED = new HashSet<>(), PRAYED = new HashSet<>(), BARGAINED = new HashSet<>();
	/** Captain id → what's hurt him most since he last stood up. */
	private static final Map<String, EnumMap<Cause, Float>> TALLY = new HashMap<>();
	/** His thrown rocks and axes, and their weight. */
	private static final Map<Projectile, Float> THROWN = Collections.synchronizedMap(new WeakHashMap<>());
	/** Thrown blades heated red: they set what they hit alight (the Cinder-Shade's Ember Knives). */
	private static final Set<Projectile> HOT = Collections.synchronizedSet(java.util.Collections.newSetFromMap(new WeakHashMap<>()));
	static final int HOT_BURN_SECONDS = 3;
	/** His wolves, and when they go. */
	private static final Map<Wolf, Long> PACK = new WeakHashMap<>();
	private static boolean redealing;

	private CaptainWays() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(CaptainWays::allow);
		PowersCompat.onParry(CaptainWays::parried);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PARRIED.clear();
			READING.clear();
			NEXT_MOVE.clear();
			NEXT_THROW.clear();
			SHOT_AT.clear();
			PENDING.clear();
			CHARGING.clear();
			CHARGE_AT.clear();
			PACKED.clear();
			PRAYED.clear();
			BARGAINED.clear();
			TALLY.clear();
			THROWN.clear();
			HOT.clear();
			PACK.clear();
		});
	}

	// ---- his body ------------------------------------------------------------------------------------------------------

	/** (From {@link Captains#bind}) {@code mob} as {@code c}: the gear and build his traits give, and his marks shown. */
	static void dress(Captain c, LivingEntity mob) {
		AttributeInstance armour = mob.getAttribute(Attributes.ARMOR), speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (armour != null) {
			armour.removeModifier(IRON_ARMOUR_ID);
			if (c.has(Trait.IRON_CLAD)) {
				armour.addPermanentModifier(new AttributeModifier(IRON_ARMOUR_ID, IRON_ARMOUR, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		if (speed != null) {
			speed.removeModifier(IRON_SPEED_ID);
			speed.removeModifier(HEAVY_ID);
			if (c.has(Trait.IRON_CLAD)) {
				speed.addPermanentModifier(new AttributeModifier(IRON_SPEED_ID, IRON_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (c.has(Trait.HEAVY_FOOTED)) {
				speed.addPermanentModifier(new AttributeModifier(HEAVY_ID, HEAVY_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (c.has(Trait.SHIELD_BEARER) && mob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty()) {
			mob.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
			if (mob instanceof Mob m) {
				m.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
			}
		}
		int bits = 0;
		for (String s : c.scars()) {
			int i = s.startsWith(MARK) ? MARKS.indexOf(s.substring(MARK.length())) : -1;
			if (i >= 0) {
				bits |= 1 << i;
			}
		}
		if (bits == 0) {
			mob.removeAttached(SHOWN);
		} else {
			mob.setAttached(SHOWN, bits);
		}
	}

	/** {@code c} with a signature move if he's high enough to have one and hasn't yet. */
	static Captain withMove(Captain c, RandomSource random) {
		if (c.rank() < MOVE_RANK || Captains.traits(c).stream().anyMatch(t -> t.sort == Sort.MOVE)) {
			return c;
		}
		List<Trait> moves = new ArrayList<>();
		for (Trait t : Trait.values()) {
			if (t.sort == Sort.MOVE) {
				moves.add(t);
			}
		}
		List<String> traits = new ArrayList<>(c.traits());
		traits.add(moves.get(random.nextInt(moves.size())).key());
		return c.climbed(c.rank(), List.copyOf(traits), c.trophy(), c.slain());
	}

	// ---- learning from how he lost -------------------------------------------------------------------------------------------

	/** What kind of blow {@code source} is, as a captain learns from it; null if it's none he'd learn from. */
	static @Nullable Cause cause(DamageSource source) {
		Entity by = source.getEntity(), direct = source.getDirectEntity();
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return Cause.FIRE;
		}
		if (by != null && by != direct && direct instanceof LivingEntity || by instanceof OwnableEntity) {
			return Cause.BEAST;
		}
		if (magic(source)) {
			return Cause.MAGIC;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return Cause.SHOT;
		}
		if (by instanceof LivingEntity living && direct == by) {
			ItemStack held = living.getMainHandItem();
			return held.isEmpty() || PowersCompat.blunt(held) ? Cause.BLUNT : Cause.BLADE;
		}
		return null;
	}

	/** A spell's blow, or ki's, or any power's (Powers' and Kingdoms' own kinds of harm), or plain magic. */
	static boolean magic(DamageSource source) {
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.SONIC_BOOM)) {
			return true;
		}
		String space = source.typeHolder().unwrapKey().map(k -> k.identifier().getNamespace()).orElse("");
		return "jaymospowers".equals(space) || "jaymoskingdoms".equals(space);
	}

	/** A gun's ball or bullet (tagged by whoever fired it, or any of TaCZ's), as Powers' guard tells it. */
	static boolean gun(DamageSource source) {
		Entity direct = source.getDirectEntity();
		return direct instanceof Projectile && (direct.entityTags().contains(MobCombat.UNPARRYABLE_TAG)
			|| "tacz".equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType()).getNamespace()));
	}

	/** Whether {@code by} is a player, or a player's beast. */
	private static boolean players(@Nullable Entity by) {
		return by instanceof Player || by instanceof OwnableEntity own && own.getOwner() instanceof Player;
	}

	/** {@code c} fell and comes back: the trait that failed him swapped for its answer, and its mark on him. */
	static Captain adapt(Captain c, MinecraftServer server) {
		EnumMap<Cause, Float> tally = TALLY.remove(c.id());
		if (tally == null || tally.isEmpty()) {
			return c;
		}
		Cause most = null;
		for (Map.Entry<Cause, Float> e : tally.entrySet()) {
			if (most == null || e.getValue() > tally.get(most)) {
				most = e.getKey();
			}
		}
		Answer a = ANSWERS.get(most);
		List<String> traits = new ArrayList<>(c.traits());
		for (Trait lost : a.loses()) {
			traits.remove(lost.key());
		}
		Trait gains = a.gains();
		if (gains == Trait.SHIELD_BEARER && traits.contains(gains.key())) {
			// (he had a shield already: now he's arrow-proof too)
			gains = Trait.ARROW_PROOF;
		}
		Trait g = gains;
		traits.removeIf(k -> {
			Trait t = Trait.parse(k);
			return t != null && t.clashes(g);
		});
		if (!traits.contains(g.key())) {
			traits.add(g.key());
		}
		List<String> scars = new ArrayList<>(c.scars());
		if (!scars.contains(MARK + a.mark())) {
			scars.add(MARK + a.mark());
		}
		scars.removeIf(s -> s.startsWith(LEARNED));
		scars.add(LEARNED + most.name().toLowerCase(java.util.Locale.ROOT));
		return c.climbed(c.rank(), List.copyOf(traits), c.trophy(), c.slain()).scarred(List.copyOf(scars));
	}

	/** (From {@link Captains#returns}) the news of what {@code c} came back having learned, once; and {@code c} without the note of it. */
	static Captain learnedNews(MinecraftServer server, Captain c) {
		String learned = c.scars().stream().filter(s -> s.startsWith(LEARNED)).findFirst().orElse(null);
		if (learned == null) {
			return c;
		}
		Post.news(server, net.minecraft.network.chat.Component.translatable("news.jaymoskingdoms.captain.learned." + learned.substring(LEARNED.length()),
			c.name(), Rivals.name(c.tribe())));
		List<String> scars = new ArrayList<>(c.scars());
		scars.remove(learned);
		return c.scarred(List.copyOf(scars));
	}

	/** (From {@link Captains#died}) {@code killer} knows what {@code c} learned from it. */
	static void killerLearns(ServerPlayer killer, Captain before, Captain after) {
		for (Trait t : Captains.traits(after)) {
			if (!before.has(t)) {
				Captains.learn(killer, after.id(), t);
			}
		}
	}

	// ---- blows -----------------------------------------------------------------------------------------------------------

	/** (From {@link Captains#dealt}) a blow on {@code self}, a captain, as his new traits take it. */
	static float takes(LivingEntity self, DamageSource source, float amount) {
		float out = amount;
		if (Captains.has(self, Trait.WITCH_HUNTER) && magic(source)) {
			out *= WITCH_HUNTER;
		}
		if (source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by) {
			ItemStack held = by.getMainHandItem();
			if (Captains.has(self, Trait.IRON_JAW) && (held.isEmpty() || PowersCompat.blunt(held))) {
				out *= IRON_JAW;
			}
			if (Captains.has(self, Trait.SHIELD_BEARER) && self.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD) && front(self, by)) {
				out *= SHIELD_FRONT;
				self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 1.0F);
			}
		}
		return out;
	}

	/** (From {@link Captains#dealt}) a blow by {@code by}, a captain, on {@code victim}. */
	static float gives(LivingEntity by, LivingEntity victim, DamageSource source, float amount) {
		if (Captains.has(by, Trait.EXECUTIONER) && Downed.isDowned(victim)) {
			return amount * EXECUTIONER;
		}
		return amount;
	}

	/** {@code by} stands before {@code self}, within his shield's half. */
	private static boolean front(LivingEntity self, LivingEntity by) {
		Vec3 look = Vec3.directionFromRotation(0.0F, self.getYHeadRot());
		Vec3 to = by.position().subtract(self.position());
		return to.horizontalDistanceSqr() > 1.0E-4 && look.dot(new Vec3(to.x, 0.0, to.z).normalize()) > 0.0;
	}

	/** A sot's miss; a thrown rock's or axe's weight. */
	private static boolean allow(LivingEntity victim, DamageSource source, float amount) {
		if (source.getDirectEntity() instanceof Projectile p && !redealing) {
			Float weight = THROWN.get(p);
			if (weight != null && victim.level() instanceof ServerLevel level) {
				if (source.getEntity() instanceof Mob thrower && !MobCombat.foe(thrower, victim)) {
					return false;
				}
				if (HOT.remove(p)) {
					victim.igniteForSeconds(HOT_BURN_SECONDS);
				}
				if (amount < weight) {
					redealing = true;
					try {
						victim.hurtServer(level, source, weight);
					} finally {
						redealing = false;
					}
					return false;
				}
			}
		}
		Entity by = source.getEntity();
		if (by instanceof LivingEntity attacker && source.getDirectEntity() == attacker && Captains.has(attacker, Trait.SOT)
			&& attacker.getRandom().nextFloat() < SOT_MISS && victim.level() instanceof ServerLevel level) {
			if (attacker.getRandom().nextInt(3) == 0) {
				Speech.say(level, attacker, Dialects.tr(attacker, "talk.jaymoskingdoms.captain.ways.sot." + attacker.getRandom().nextInt(LINES)), Speech.RED, Captains.SAY_RANGE);
			}
			level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.HOSTILE, 1.0F, 0.8F);
			return false;
		}
		return true;
	}

	/** (From {@link Captains#hurt}) a blow landed on {@code victim}: tallied if he's a captain; and his second wind, his bargain. */
	static void hurt(ServerLevel level, LivingEntity victim, DamageSource source, float taken) {
		if (gun(source)) {
			// (a shot at him or near him, for the gun-shy)
			for (Mob near : level.getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(GUN_NEAR), m -> Captains.has(m, Trait.GUN_SHY))) {
				SHOT_AT.put(near.getUUID(), level.getGameTime());
			}
		}
		Captain c = Captains.is(victim) ? Captains.of(victim) : null;
		if (c == null) {
			return;
		}
		Cause cause = taken >= TALLY_MIN && players(source.getEntity()) ? cause(source) : null;
		if (cause != null) {
			TALLY.computeIfAbsent(c.id(), k -> new EnumMap<>(Cause.class)).merge(cause, taken, Float::sum);
		}
		if (!victim.isAlive() || !(victim instanceof PathfinderMob mob)) {
			return;
		}
		float share = victim.getHealth() / victim.getMaxHealth();
		if (Captains.has(victim, Trait.PRAYERFUL) && share < PRAY_AT && PRAYED.add(victim.getUUID())) {
			victim.addEffect(new MobEffectInstance(MobEffects.REGENERATION, PRAY_TICKS, 1));
			victim.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, PRAY_TICKS, 0));
			level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getY(1.0), victim.getZ(), 12, 0.4, 0.3, 0.4, 0.02);
			Speech.say(level, victim, Dialects.tr(victim, "talk.jaymoskingdoms.captain.ways.prayerful." + victim.getRandom().nextInt(LINES)), Speech.RED, Captains.SAY_RANGE);
		}
		if (Captains.has(victim, Trait.BARGAINER) && share < BARGAIN_AT && source.getEntity() instanceof ServerPlayer player && BARGAINED.add(victim.getUUID())) {
			victim.spawnAtLocation(level, BentIngots.coin(BARGAIN_WORTH));
			Speech.say(level, victim, Dialects.tr(victim, "talk.jaymoskingdoms.captain.ways.bargainer." + victim.getRandom().nextInt(LINES),
				Recollections.nameTo(player, victim)), Speech.RED, Captains.SAY_RANGE);
			Captains.scare(mob, player.position(), BARGAIN_RUN);
		}
	}

	/** A player parried {@code attacker}: a parry reader learns it, and after {@link #READ_AFTER} his blows go past a guard. */
	private static void parried(ServerPlayer player, @Nullable LivingEntity attacker) {
		if (attacker == null || !Captains.has(attacker, Trait.PARRY_READER) || !(attacker.level() instanceof ServerLevel level)) {
			return;
		}
		int n = PARRIED.merge(attacker.getUUID(), 1, Integer::sum);
		if (n >= READ_AFTER) {
			PARRIED.remove(attacker.getUUID());
			READING.put(attacker.getUUID(), level.getGameTime() + READ_TICKS);
			attacker.addTag(MobCombat.UNPARRYABLE_TAG);
			Speech.say(level, attacker, Dialects.tr(attacker, "talk.jaymoskingdoms.captain.ways.parry_reader." + attacker.getRandom().nextInt(LINES),
				Recollections.nameTo(player, attacker)), Speech.RED, Captains.SAY_RANGE);
		}
	}

	/** (From {@link Captains#afraid}) whether a gun-shy captain was shot at, or near, just now. */
	static boolean gunShy(Mob mob, long now) {
		Long at = SHOT_AT.get(mob.getUUID());
		return at != null && now - at <= GUN_FRESH;
	}

	// ---- his step ----------------------------------------------------------------------------------------------------------

	/** (From {@link Captains#stepMob}) every tick: what his new traits do, and his move. True while his move holds him. */
	static boolean step(ServerLevel level, PathfinderMob mob, Captain c, @Nullable LivingEntity target) {
		long now = level.getGameTime();
		UUID id = mob.getUUID();
		Long reading = READING.get(id);
		if (reading != null && now >= reading) {
			READING.remove(id);
			mob.removeTag(MobCombat.UNPARRYABLE_TAG);
		}
		if (target == null) {
			if (mob.tickCount % 20 == 0) {
				seek(level, mob);
			}
			if (mob.tickCount % 200 == 0) {
				PACKED.remove(id);
				PRAYED.remove(id);
				PARRIED.remove(id);
			}
			if (mob.tickCount % 100 == 0) {
				packGone(now);
			}
			PENDING.remove(id);
			CHARGING.remove(id);
			CHARGE_AT.remove(id);
			return false;
		}
		if (Captains.has(mob, Trait.PACK_LEADER) && PACKED.add(id)) {
			pack(level, mob, target, now);
		}
		if (Captains.has(mob, Trait.EXECUTIONER) && mob.tickCount % 20 == 0 && !Downed.isDowned(target)) {
			for (Player p : level.getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(EXECUTE_RANGE), p -> Downed.isDowned(p) && MobCombat.foe(mob, p))) {
				mob.setTarget(p);
				target = p;
				break;
			}
		}
		if (Captains.has(mob, Trait.ROCK_THROWER) && now >= NEXT_THROW.getOrDefault(id, 0L) && !PENDING.containsKey(id)) {
			double d = mob.distanceTo(target);
			if (d >= THROW_MIN && d <= THROW_MAX && mob.hasLineOfSight(target) && MobAmmo.spend(mob, MobAmmo.Kind.ROCKS)) {
				// a rock from the few he carries; it lands as a cobblestone he can pick up again
				NEXT_THROW.put(id, now + THROW_EVERY + mob.getRandom().nextInt(THROW_EVERY / 2));
				Snowball rock = hurl(level, mob, target, new ItemStack(Items.COBBLESTONE), ROCK_DAMAGE, false);
				MobAmmo.leaves(rock, new ItemStack(Items.COBBLESTONE));
			}
		}
		return moves(level, mob, c, target, now);
	}

	/** With no foe: a blood tracker smells out a bleeding player; a watchful one sees one sneaking or unseen. */
	private static void seek(ServerLevel level, PathfinderMob mob) {
		boolean tracker = Captains.has(mob, Trait.BLOOD_TRACKER), watchful = Captains.has(mob, Trait.WATCHFUL);
		if (!tracker && !watchful) {
			return;
		}
		double reach = tracker ? TRACK_RANGE : WATCH_RANGE;
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(reach), p -> MobCombat.foe(mob, p))) {
			boolean smelt = tracker && PowersCompat.cuts(p) > 0;
			boolean seen = watchful && mob.distanceTo(p) <= WATCH_RANGE && (p.isShiftKeyDown() || p.isInvisible());
			if (smelt || seen) {
				mob.setTarget(p);
				Speech.say(level, mob, Dialects.tr(mob, "talk.jaymoskingdoms.captain.ways." + (smelt ? "blood_tracker." : "watchful.") + mob.getRandom().nextInt(LINES),
					Recollections.nameTo(p, mob)), Speech.RED, Captains.SAY_RANGE);
				return;
			}
		}
	}

	/** A pack leader's wolves, after his foe. */
	private static void pack(ServerLevel level, PathfinderMob mob, LivingEntity target, long now) {
		for (int i = 0; i < PACK_SIZE; i++) {
			Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.EVENT);
			if (wolf == null) {
				return;
			}
			double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
			wolf.snapTo(mob.getX() + Math.cos(angle) * PACK_SPREAD, mob.getY(), mob.getZ() + Math.sin(angle) * PACK_SPREAD, (float)Math.toDegrees(angle), 0.0F);
			level.addFreshEntity(wolf);
			wolf.setTarget(target);
			wolf.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.<LivingEntity>of(target));
			wolf.startPersistentAngerTimer();
			PACK.put(wolf, now + PACK_TICKS);
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
			SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.ANGRY).adultSounds().growlSound().value(), SoundSource.HOSTILE, 1.4F, 0.9F);
		Speech.say(level, mob, Dialects.tr(mob, "talk.jaymoskingdoms.captain.ways.pack_leader." + mob.getRandom().nextInt(LINES)), Speech.RED, Captains.SAY_RANGE);
	}

	/** His wolves whose time is up go back to the wild (gone). */
	private static void packGone(long now) {
		PACK.entrySet().removeIf(e -> {
			if (now < e.getValue() && e.getKey().isAlive()) {
				return false;
			}
			if (e.getKey().isAlive()) {
				e.getKey().discard();
			}
			return true;
		});
	}

	/** A rock or an axe thrown at {@code target}, weighing {@code weight}; {@code glinted}: nobody parries it. */
	private static Snowball hurl(ServerLevel level, Mob mob, LivingEntity target, ItemStack what, float weight, boolean glinted) {
		Snowball thrown = new Snowball(level, mob, what);
		THROWN.put(thrown, weight);
		if (glinted) {
			MobCombat.unparryable(thrown);
		}
		double dx = target.getX() - mob.getX(), dz = target.getZ() - mob.getZ();
		double dy = target.getY(0.3333) - thrown.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Projectile.spawnProjectileUsingShoot(thrown, level, what, dx, dy + flat * 0.2, dz, 1.4F, 2.0F);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.HOSTILE, 1.0F, 0.7F);
		return thrown;
	}

	/**
	 * {@code mob} throws a real blade ({@code blade}, one of its own) at {@code target}, turned {@code yaw} degrees off the
	 * straight line (for a fan); {@code hot}: heated, it sets what it hits alight. It lands as the blade it was
	 * ({@link MobAmmo#leaves}). The Cinder-Shade's Ember Knives.
	 */
	public static Snowball throwBlade(ServerLevel level, Mob mob, LivingEntity target, ItemStack blade, float damage, float yaw, boolean hot) {
		Snowball thrown = new Snowball(level, mob, blade);
		THROWN.put(thrown, damage);
		if (hot) {
			HOT.add(thrown);
			thrown.igniteForSeconds(5.0F);
		}
		Vec3 to = new Vec3(target.getX() - mob.getX(), target.getY(0.5) - thrown.getY(), target.getZ() - mob.getZ()).yRot((float)Math.toRadians(yaw));
		Projectile.spawnProjectileUsingShoot(thrown, level, blade, to.x, to.y + to.horizontalDistance() * 0.1, to.z, 1.6F, 1.0F);
		MobAmmo.leaves(thrown, blade);
		return thrown;
	}

	// ---- signature moves -----------------------------------------------------------------------------------------------------

	private static @Nullable Trait move(Captain c) {
		for (Trait t : Captains.traits(c)) {
			if (t.sort == Sort.MOVE) {
				return t;
			}
		}
		return null;
	}

	private static boolean moves(ServerLevel level, PathfinderMob mob, Captain c, LivingEntity target, long now) {
		UUID id = mob.getUUID();
		Long charging = CHARGING.get(id);
		if (charging != null) {
			return charge(level, mob, now, charging);
		}
		Pending p = PENDING.get(id);
		if (p != null) {
			mob.getNavigation().stop();
			mob.getLookControl().setLookAt(p.target(), 30.0F, 30.0F);
			if (now < p.at()) {
				return true;
			}
			PENDING.remove(id);
			if (p.target().isAlive() && p.target().level() == level) {
				strike(level, mob, p);
			}
			return CHARGING.containsKey(id);
		}
		Trait move = move(c);
		if (move == null || c.rank() < MOVE_RANK || now < NEXT_MOVE.getOrDefault(id, 0L) || Downed.isDowned(mob)) {
			return false;
		}
		double d = mob.distanceTo(target);
		boolean inRange = switch (move) {
			case SHIELD_CHARGE -> d >= CHARGE_MIN && d <= CHARGE_MAX && mob.hasLineOfSight(target);
			case GROUND_SLAM -> d <= SLAM_RANGE;
			// (MobAmmo) only with an axe in hand: the one he throws, and then he hasn't got it
			case AXE_THROW -> d >= THROW_MIN && d <= THROW_MAX && mob.hasLineOfSight(target)
				&& mob.getMainHandItem().is(net.minecraft.tags.ItemTags.AXES);
			case CRUSHING_GRAB -> d <= GRAB_RANGE;
			default -> false;
		};
		if (!inRange) {
			return false;
		}
		int lead = switch (move) {
			case SHIELD_CHARGE -> CHARGE_LEAD;
			case GROUND_SLAM -> SLAM_LEAD;
			case AXE_THROW -> AXE_LEAD;
			default -> GRAB_LEAD;
		};
		NEXT_MOVE.put(id, now + lead + MOVE_EVERY + mob.getRandom().nextInt(MOVE_JITTER));
		PENDING.put(id, new Pending(move, target, now + lead));
		// (the red glint: a guard only blocks it)
		MobCombat.tell(level, mob, true);
		PowersCompat.tellIncoming(mob, target, lead, true);
		Speech.say(level, mob, Dialects.tr(mob, "talk.jaymoskingdoms.captain.move." + move.key() + "." + mob.getRandom().nextInt(LINES)), Speech.RED, Captains.SAY_RANGE);
		mob.getNavigation().stop();
		return true;
	}

	private static void strike(ServerLevel level, PathfinderMob mob, Pending p) {
		LivingEntity target = p.target();
		float blow = (float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
		mob.addTag(MobCombat.UNPARRYABLE_TAG);
		try {
			switch (p.move()) {
				case SHIELD_CHARGE -> {
					CHARGING.put(mob.getUUID(), level.getGameTime() + CHARGE_TICKS);
					CHARGE_AT.put(mob.getUUID(), target);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.3F);
				}
				case GROUND_SLAM -> {
					mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
					level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), mob.getX(), mob.getY() + 0.1, mob.getZ(),
						40, SLAM_REACH * 0.4, 0.1, SLAM_REACH * 0.4, 0.2);
					level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 0.2, mob.getZ(), 2, 0.6, 0.1, 0.6, 0.0);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.2F, 0.6F);
					for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(SLAM_REACH), e -> e != mob && e.onGround() && MobCombat.foe(mob, e))) {
						e.hurtServer(level, level.damageSources().mobAttack(mob), blow * SLAM_HIT);
						Vec3 out = e.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
						out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(0.6);
						e.setDeltaMovement(e.getDeltaMovement().add(out.x, 0.5, out.z));
						e.needsSync = true;
					}
				}
				case AXE_THROW -> {
					// his own axe: out of his hand, and left lying where it lands till he (or anyone) picks it up
					ItemStack axe = mob.getMainHandItem().copyWithCount(1);
					if (!axe.is(net.minecraft.tags.ItemTags.AXES)) {
						break;
					}
					MobAmmo.lost(mob, axe.getItem());
					MobAmmo.leaves(hurl(level, mob, target, axe, AXE_DAMAGE, true), axe);
				}
				case CRUSHING_GRAB -> {
					if (mob.distanceTo(target) <= GRAB_RANGE + 0.8) {
						mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
						target.hurtServer(level, level.damageSources().mobAttack(mob), blow * GRAB_HIT);
						Vec3 away = Vec3.directionFromRotation(0.0F, mob.getYHeadRot()).scale(GRAB_OUT);
						target.setDeltaMovement(away.x, GRAB_UP, away.z);
						target.needsSync = true;
						target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), mob);
						level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 1.2F, 0.7F);
					}
				}
				default -> {
				}
			}
		} finally {
			if (!READING.containsKey(mob.getUUID())) {
				mob.removeTag(MobCombat.UNPARRYABLE_TAG);
			}
		}
	}

	/** The shield charge under way: on at his foe, and the blow when he reaches them. */
	private static boolean charge(ServerLevel level, PathfinderMob mob, long now, long until) {
		UUID id = mob.getUUID();
		LivingEntity target = CHARGE_AT.get(id);
		if (target == null || !target.isAlive() || now >= until) {
			CHARGING.remove(id);
			CHARGE_AT.remove(id);
			return false;
		}
		Vec3 to = target.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() > 1.0E-4) {
			Vec3 dir = to.normalize();
			mob.setDeltaMovement(dir.x * CHARGE_SPEED, mob.getDeltaMovement().y, dir.z * CHARGE_SPEED);
			mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
		}
		if (mob.distanceTo(target) <= 1.8) {
			CHARGING.remove(id);
			CHARGE_AT.remove(id);
			mob.addTag(MobCombat.UNPARRYABLE_TAG);
			try {
				target.hurtServer(level, level.damageSources().mobAttack(mob), (float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * CHARGE_HIT);
			} finally {
				if (!READING.containsKey(id)) {
					mob.removeTag(MobCombat.UNPARRYABLE_TAG);
				}
			}
			Vec3 push = to.lengthSqr() > 1.0E-4 ? to.normalize().scale(1.4) : Vec3.ZERO;
			target.setDeltaMovement(push.x, 0.45, push.z);
			target.needsSync = true;
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.3F, 0.6F);
			return false;
		}
		return true;
	}

	/** (From {@link Captains#died}) he's gone: what this class kept on his body. */
	static void forget(UUID body) {
		PARRIED.remove(body);
		READING.remove(body);
		NEXT_MOVE.remove(body);
		NEXT_THROW.remove(body);
		SHOT_AT.remove(body);
		PENDING.remove(body);
		CHARGING.remove(body);
		CHARGE_AT.remove(body);
		PACKED.remove(body);
		PRAYED.remove(body);
		BARGAINED.remove(body);
	}
}
