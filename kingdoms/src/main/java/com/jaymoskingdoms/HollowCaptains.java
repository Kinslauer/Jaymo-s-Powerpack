package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import com.jaymoskingdoms.Classes.PlayerClass;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Hollow who walk among the risen dead, and the captains they become. Andy (2026-11-25): "the named zombies and skeletons
 * should be replaced with skeleton, pallid, or zombie hollow npcs instead, this makes more sense that they can speak, and will
 * return from the dead. they should fight the same as their counterparts at default only learning as they progress." Then:
 * "the pallids are intelligent hollow and will end up leading armies of the skeleton and zombies, as another faction"; "pallids
 * should fight with their old class abilities and weapons"; and "Hollow of all kinds except vampire should be among the zombie
 * and skeleton hordes at night and otherwise. if ever ANY of them kill you is when they are promoted. a new world should spawn
 * with a finite amount, and that number can increase or decrease. the number should be 6".
 * <ul>
 * <li><b>How many:</b> the world keeps a count of the Hollow who walk with the dead ({@link #COUNT}, {@link #START} in a new
 * world). While fewer than that walk ({@link #WALKING}), one stands up among the dead: in a horde of the dead or of skeletons as
 * it comes ({@code ZombieHordes}, {@link #HORDE_CHANCE}), or by day or night in place of a zombie or skeleton out of a player's
 * way ({@link #ROAM_EVERY}). The count falls by one each time one of them dies for good, and rises by one when the dead kill a
 * villager and take them ({@link #TAKE_CHANCE}).</li>
 * <li><b>Of every kind but the vampire's:</b> an MCA villager of the Hollow's blood ({@link #KINDS}): a Zombie Hollow, a
 * Skeleton Hollow, a Reaper or a Pallid. The Wraith, the vampire's thrall, is never among them.</li>
 * <li><b>Promoted by a kill:</b> the raised's captains ({@link Rivals.Side#RAISED}) are made only when one of the Hollow, or a
 * zombie or skeleton, kills a player ({@code Captains.slew}); none rise from the land by chance any more. The titled dead are
 * the Hollow's lieutenants.</li>
 * <li><b>The Pallid lead:</b> a Pallid captain calls up {@link #ARMY_START} of the dead (and {@link #ARMY_PER_RANK} more a
 * rank) when he rises, and gathers the dead near him to {@link #ARMY_MOST}: they keep with him and go for whoever he goes for
 * ({@link #army}).</li>
 * <li><b>How they fight:</b> a Zombie Hollow and a Reaper as a zombie, a Skeleton Hollow as a skeleton (its bow); a Pallid with
 * the class he had in life ({@link PlayerClass}): its starting weapons and armour, what it's good at, a Ranger's or Marksman's
 * shooting, a caster's bolt, a Cleric's or Druid's mending of his army. A new captain has none of a captain's strengths or
 * moves: those come only as he climbs (a strength for each player he kills, what he learns each time he comes back, a move of
 * his own at rank four).</li>
 * <li><b>The same man back:</b> a captain's kind, class and whole saved self are kept ({@link #LOOKS}, {@link #FACES}), so when
 * he comes back (as any captain may) it's him again, in the next of the dead to walk his land.</li>
 * <li>The dead leave them be ({@code mixin/nightgifts/ZombieBlightMixin}), and they're outside the law ({@link Outlaws}).</li>
 * </ul>
 * Without MCA none of the Hollow walk, and the raised keep their zombies and skeletons as before.
 */
public final class HollowCaptains {
	// ================================================================================================================
	// Tuning
	/** How many of the Hollow walk with the dead in a new world. */
		static final int START = 6;
		/** How many Hollow captains each player gets on their own ladder. */
		static final int PER_PLAYER = 12;
		/** The kinds they are, and how many in each hundred (the Pallid most: Andy, "you encounter them most"). */
		static final List<String> KINDS = List.of("pallid", "zombie", "skeleton", "reaper");
		static final int[] KIND_WEIGHTS = {40, 25, 25, 10};
		/** The chance a horde of the dead (or of skeletons) brings one along while there's room. */
		static final float HORDE_CHANCE = 0.5F;
		/** How often (ticks) one may stand up among the dead near a player while there's room, at what chance; how near and how far from them (blocks). */
		static final int ROAM_EVERY = 1200;
		static final float ROAM_CHANCE = 0.5F;
		static final double ROAM_NEAR = 24.0, ROAM_FAR = 64.0;
		/** The chance a villager the dead kill is taken among them (one more Hollow to walk). */
		static final float TAKE_CHANCE = 0.25F;
		/** A Pallid captain's army: the dead he calls up as he rises, more for each rank, the most who follow him. */
		static final int ARMY_START = 2, ARMY_PER_RANK = 1, ARMY_MOST = 10;
		/** How far (blocks) he gathers the dead, how far they stray from him idle, and how often he looks to them (ticks). */
		static final double ARMY_CALL = 24.0, ARMY_KEEP = 8.0;
		static final int ARMY_EVERY = 20;
		/** Of the dead he calls up, this share are skeletons. */
		static final float ARMY_SKELETONS = 0.4F;
		/** A zombie's build, before a captain's: health, blow, pace, hide, how far it sees. */
		static final double ZOMBIE_HEALTH = 20.0, ZOMBIE_DAMAGE = 3.0, ZOMBIE_SPEED = 0.23, ZOMBIE_ARMOUR = 2.0, ZOMBIE_SIGHT = 35.0;
		/** How far a bowman sees (a skeleton's). */
		static final double SKELETON_SIGHT = 16.0;
		/** What each class adds (as the Flame Avatars' do): blows, health, armour, speed (a share), steadiness. */
		static final double WARLORD_DAMAGE = 3.0, BRUTE_DAMAGE = 3.0, BRUTE_HEALTH = 12.0, BRUTE_STEADY = 0.6, THIEF_SPEED = 0.2,
			PALADIN_ARMOUR = 8.0, MONK_DAMAGE = 2.0, MONK_ARMOUR = 4.0, MONK_SPEED = 0.1;
		/** A caster's bolt: this often (ticks), at a foe from this far to this far (blocks). */
		static final int BOLT_EVERY = 100;
		static final double BOLT_NEAR = 4.0, BOLT_FAR = 20.0;
		/** A mender's healing: this often (ticks), this far (blocks), this much health, this colour (ARGB, a cold soul blue). */
		static final int MEND_EVERY = 200;
		static final double MEND_RANGE = 10.0;
		static final float MEND = 6.0F;
		static final int MEND_COLOUR = 0xFF7FD8E0;
		/** How often a mender checks for wounded (the 2.0F threshold below max health). */
		static final float MEND_THRESHOLD = 2.0F;
		/** A blow at most this often (ticks): a zombie's. */
		static final int MELEE_EVERY = 20;
		/** A bowman's: shoots from within this range (blocks), backs off inside this, draws this long and shoots this often (ticks). */
		static final double BOW_RANGE = 15.0, BOW_NEAR = 5.0;
		static final int BOW_DRAW = 20, BOW_EVERY = 40;
		/** The arrow's speed and spread (a skeleton's on normal difficulty). */
		static final float BOW_SPEED = 1.6F, BOW_SPREAD = 6.0F;
		/** Ticks a foe who hurt them stays their foe; how often an idle one wanders, and how far (blocks). */
		static final int GRUDGE_TICKS = 200, WANDER_EVERY = 120, WANDER = 8;
		/** Path refresh interval for melee fighters (ticks). */
		static final int PATH_REFRESH_TICKS = 10;
		/** Retreat: search radius (blocks), attempts, and speed multiplier. */
		static final int RETREAT_RADIUS = 8, RETREAT_ATTEMPTS = 3;
		static final double RETREAT_SPEED = 1.1;
	// ================================================================================================================

	/** On every one of the Hollow who walk with the dead (captain or not). */
	public static final String TAG = "jaymoskingdoms.hollow_captain";
	/** On each of the dead in a Pallid captain's army, then his captain id. */
	static final String ARMY_TAG = "jaymoskingdoms.pallid_army:";
	/** On each Pallid, then the key of the class he had in life. */
	static final String CLASS_TAG = "jaymoskingdoms.pallid_class:";
	/** Their role: outside the law, enlisted to nobody. */
	static final int ROLE = 98;

	/** On the Overworld: Hollow captain id → his kind, and a Pallid's class ({@code zombie}, {@code pallid:warlord}). Saved. */
	static final AttachmentType<Map<String, String>> LOOKS = AttachmentRegistry.create(JaymosKingdoms.id("hollow_captain_looks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On the Overworld: captain id → his body's saved self (face, name, blood). Saved. */
	static final AttachmentType<Map<String, CompoundTag>> FACES = AttachmentRegistry.create(JaymosKingdoms.id("hollow_captain_faces"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC)));
	/** On the Overworld: how many of the Hollow walk with the dead. Saved. */
		static final AttachmentType<Integer> COUNT = AttachmentRegistry.create(JaymosKingdoms.id("hollow_count"), builder -> builder.persistent(Codec.INT));
		/** On the Overworld: the bodies of the Hollow walking now (uuids). Saved. */
		static final AttachmentType<List<String>> WALKING = AttachmentRegistry.create(JaymosKingdoms.id("hollow_walking"),
			builder -> builder.persistent(Codec.STRING.listOf()));
		/** On a player: how many Hollow captains they have (max {@link #PER_PLAYER}). Saved, kept through death. */
		static final AttachmentType<Integer> PLAYER_COUNT = AttachmentRegistry.create(JaymosKingdoms.id("hollow_player_count"),
			builder -> builder.persistent(Codec.INT).copyOnDeath());
		/** On a player: the bodies of their Hollow captains walking now (uuids). Saved, kept through death. */
		static final AttachmentType<List<String>> PLAYER_WALKING = AttachmentRegistry.create(JaymosKingdoms.id("hollow_player_walking"),
			builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
		/** On a player: the captain ids on their ladder. Saved, kept through death. */
		static final AttachmentType<List<String>> PLAYER_LADDER = AttachmentRegistry.create(JaymosKingdoms.id("hollow_player_ladder"),
			builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	/** Work put off to the end of the tick (a body can't be swapped while its chunk is still loading it). */
	private static final List<Runnable> LATER = new ArrayList<>();
	/** Each one's last shot and when it started drawing (game time). */
	private static final Map<java.util.UUID, long[]> BOWS = new HashMap<>();
	/** When each caster or mender may next use his class's gift (game time). */
	private static final Map<java.util.UUID, Long> NEXT = new HashMap<>();

	private HollowCaptains() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			BOWS.remove(entity.getUUID());
			NEXT.remove(entity.getUUID());
			Entity.RemovalReason why = entity.getRemovalReason();
					if (is(entity) && why != null && why.shouldDestroy() && why != Entity.RemovalReason.KILLED) {
						// (taken out of the world some other way: not dead, so the count stands, and another will walk in its place)
						// Find the player this Hollow belongs to and remove from their walking list
						Captain c = Captains.is(entity) ? Captains.of(entity) : null;
						String captainId = c == null ? null : c.id();
						ServerPlayer owner = captainId == null ? null : findPlayerWithCaptain(level.getServer(), captainId);
						if (owner != null) {
							playerWalking(owner, entity, false);
						} else {
							walking(level.getServer(), entity, false);
						}
					}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register(HollowCaptains::died);
		ServerTickEvents.END_SERVER_TICK.register(HollowCaptains::roam);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (LATER.isEmpty()) {
				return;
			}
			List<Runnable> now = List.copyOf(LATER);
			LATER.clear();
			now.forEach(Runnable::run);
		});
	}

	/** Whether Pallids rise to lead the dead (MCA is here). */
	static boolean live() {
		return McaCompat.LOADED;
	}

	/** {@code e} is one of the Pallids who lead the risen dead (or was). */
	public static boolean is(Entity e) {
		return e.entityTags().contains(TAG);
	}

	static void later(Runnable r) {
		LATER.add(r);
	}

	// ---- rising ---------------------------------------------------------------------------------------------------------

	/**
		 * One of the Hollow stands up in {@code dead}'s place (it's gone): captain {@code id}'s own if he was one before, else a new
		 * one of a kind (and a Pallid's class) rolled now. Null (and {@code dead} left as it is) without MCA or if none could be made.
		 * The risen Hollow is added to {@code forPlayer}'s ladder.
		 */
		static @Nullable PathfinderMob rise(ServerLevel level, PathfinderMob dead, @Nullable String id, @Nullable ServerPlayer forPlayer) {
			if (!live() || is(dead) || forPlayer == null) {
				return null;
			}
			MinecraftServer server = level.getServer();
			String was = id == null ? null : Factions.level(server).getAttachedOrElse(LOOKS, Map.of()).get(id);
			String kind = was == null ? rollKind(level.getRandom()) : was.split(":")[0];
			PlayerClass cls = was != null && was.contains(":") ? PlayerClass.byKey(was.substring(was.indexOf(':') + 1)) : null;
			if (cls == null && "pallid".equals(kind)) {
				// (what he was in life: any of the classes)
				PlayerClass[] all = PlayerClass.values();
				cls = all[level.getRandom().nextInt(all.length)];
			}
			ItemStack held = dead.getMainHandItem().copy();
			CompoundTag face = id == null ? null : Factions.level(server).getAttachedOrElse(FACES, Map.of()).get(id);
			PathfinderMob body = McaCompat.hollowBody(level, dead.position(), dead.getYRot(), ROLE, face);
			if (body == null) {
				return null;
			}
			if (face == null) {
				VillagerHeritage.born(body, Heritage.Kind.HOLLOW, null);
			}
			// (the Pallid is the Hollow's first look)
			HeritageVariants.set(body, "pallid".equals(kind) ? null : HeritageVariants.Variant.byKey(kind));
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				body.setItemSlot(slot, ItemStack.EMPTY);
				body.setDropChance(slot, 0.0F);
			}
			build(body, kind, cls, held);
			body.addTag(TAG);
			if (cls != null) {
				body.addTag(CLASS_TAG + cls.key());
			}
			body.addTag(Rivals.ROLLED_TAG);
			if (!level.addFreshEntity(body)) {
				return null;
			}
			playerWalking(forPlayer, body, true);
			McaCompat.untree(level, body);
			level.sendParticles(ParticleTypes.SOUL, body.getX(), body.getY(0.4), body.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 0.8F, 0.7F);
			dead.discard();
			return body;
		}

		/** As {@link #rise}, or {@code dead} itself if none of the Hollow could stand up in its place. */
		static PathfinderMob riseOr(ServerLevel level, PathfinderMob dead, @Nullable String id, @Nullable ServerPlayer forPlayer) {
			PathfinderMob body = rise(level, dead, id, forPlayer);
			return body == null ? dead : body;
		}

	/**
	 * Their build and arms, which a captain's are put on top of: a zombie's body (a bowman sees only as far as a skeleton). A
	 * Skeleton Hollow carries a bow, a Zombie Hollow or a Reaper what the dead one held; a Pallid the weapons and armour his class
	 * starts with ({@link Classes#gear}) and what his class is good at (as the Flame Avatars' classes are: {@code nether/AvatarClasses}).
	 */
	private static void build(PathfinderMob body, String kind, @Nullable PlayerClass cls, ItemStack held) {
		boolean bow = "skeleton".equals(kind) || ranged(cls);
		base(body, Attributes.MAX_HEALTH, ZOMBIE_HEALTH);
		base(body, Attributes.ATTACK_DAMAGE, ZOMBIE_DAMAGE);
		base(body, Attributes.MOVEMENT_SPEED, ZOMBIE_SPEED);
		base(body, Attributes.ARMOR, ZOMBIE_ARMOUR);
		base(body, Attributes.FOLLOW_RANGE, bow ? SKELETON_SIGHT : ZOMBIE_SIGHT);
		if (cls == null) {
			body.setItemSlot(EquipmentSlot.MAINHAND, "skeleton".equals(kind) ? held.getItem() instanceof BowItem ? held : new ItemStack(Items.BOW)
				: held.getItem() instanceof BowItem ? ItemStack.EMPTY : held);
			body.setHealth(body.getMaxHealth());
			return;
		}
		List<ItemStack> gear = Classes.gear(cls, cls.options.isEmpty() ? "" : cls.options.getFirst());
		// (the hand he fought with: the first of his kit, but a cleric's staff, a monk's cestus, a paladin's or tinkerer's blade)
		int hand = switch (cls) {
			case CLERIC, MONK, PALADIN, TINKERER -> 1;
			default -> 0;
		};
		if (hand < gear.size()) {
			body.setItemSlot(EquipmentSlot.MAINHAND, gear.get(hand).copyWithCount(1));
		}
		for (ItemStack piece : gear) {
			EquipmentSlot slot = body.getEquipmentSlotForItem(piece);
			if (piece.is(Items.SHIELD)) {
				body.setItemSlot(EquipmentSlot.OFFHAND, piece.copyWithCount(1));
			} else if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && body.getItemBySlot(slot).isEmpty()) {
				body.setItemSlot(slot, piece.copyWithCount(1));
			}
		}
		switch (cls) {
			case WARLORD -> add(body, Attributes.ATTACK_DAMAGE, WARLORD_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
			case BRUTE -> {
				add(body, Attributes.ATTACK_DAMAGE, BRUTE_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
				add(body, Attributes.MAX_HEALTH, BRUTE_HEALTH, AttributeModifier.Operation.ADD_VALUE);
				add(body, Attributes.KNOCKBACK_RESISTANCE, BRUTE_STEADY, AttributeModifier.Operation.ADD_VALUE);
			}
			case THIEF -> add(body, Attributes.MOVEMENT_SPEED, THIEF_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			case PALADIN -> add(body, Attributes.ARMOR, PALADIN_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
			case MONK -> {
				add(body, Attributes.ATTACK_DAMAGE, MONK_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
				add(body, Attributes.ARMOR, MONK_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
				add(body, Attributes.MOVEMENT_SPEED, MONK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			default -> {
			}
		}
		body.setHealth(body.getMaxHealth());
	}

	private static void add(LivingEntity body, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> what, double amount,
		AttributeModifier.Operation how) {
		AttributeInstance a = body.getAttribute(what);
		if (a != null) {
			a.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("pallid_class"), amount, how));
		}
	}

	/** The class {@code mob} had in life, or null. */
	static @Nullable PlayerClass classOf(Entity mob) {
		for (String t : mob.entityTags()) {
			if (t.startsWith(CLASS_TAG)) {
				return PlayerClass.byKey(t.substring(CLASS_TAG.length()));
			}
		}
		return null;
	}

	/** A class that fights from afar: the Ranger's bow, the Marksman's shot. */
	static boolean ranged(@Nullable PlayerClass cls) {
		return cls == PlayerClass.RANGER || cls == PlayerClass.MARKSMAN;
	}

	/** A class that casts: a bolt at a foe out of reach. */
	static boolean caster(@Nullable PlayerClass cls) {
		return cls == PlayerClass.MAGE || cls == PlayerClass.SORCERER || cls == PlayerClass.WARLOCK;
	}

	/** A class that mends: the worst hurt of his army, or himself. */
	static boolean mender(@Nullable PlayerClass cls) {
		return cls == PlayerClass.CLERIC || cls == PlayerClass.DRUID;
	}

	private static void base(LivingEntity body, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> what, double value) {
		AttributeInstance a = body.getAttribute(what);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	/** {@code body} is Hollow captain {@code id}'s: his kind and class, and (the first time) his saved self, kept for when he comes back. */
	static void keep(MinecraftServer server, String id, LivingEntity body) {
		if (!is(body)) {
			return;
		}
		ServerLevel home = Factions.level(server);
		Map<String, String> looks = new HashMap<>(home.getAttachedOrElse(LOOKS, Map.of()));
		looks.put(id, lookOf(body));
		home.setAttached(LOOKS, Map.copyOf(looks));
		Map<String, CompoundTag> faces = home.getAttachedOrElse(FACES, Map.of());
		if (!faces.containsKey(id)) {
			CompoundTag self = McaCompat.saved(body);
			if (self != null) {
				Map<String, CompoundTag> fresh = new HashMap<>(faces);
				fresh.put(id, self);
				home.setAttached(FACES, Map.copyOf(fresh));
			}
		}
	}

	/** (From {@link Rivals#loaded}, put off a tick) Captain {@code id}, due back, gets up in {@code dead}'s place for their player. */
		static void comeBack(ServerLevel level, String id, PathfinderMob dead) {
			Captain c = Captains.get(level.getServer(), id);
			if (c == null || c.fallen() || c.body().isPresent() || !dead.isAlive() || dead.isRemoved() || Captains.is(dead)) {
				return;
			}
			// Find the player this captain belongs to
			ServerPlayer owner = findPlayerWithCaptain(level.getServer(), id);
			if (owner == null) {
				return;
			}
			// (one of the Hollow gets up again as himself; his ladder spot was reserved)
			boolean hollow = Factions.level(level.getServer()).getAttachedOrElse(LOOKS, Map.of()).containsKey(id);
			PathfinderMob body = hollow ? riseOr(level, dead, id, owner) : dead;
			Captains.embodyIn(level, c, body);
			keep(level.getServer(), id, body);
			playerWalking(owner, body, true);
			muster(level, c, body);
		}

	/** {@code body}'s kind, and a Pallid's class: {@code zombie}, {@code skeleton}, {@code reaper}, {@code pallid:<class>}. */
	static String lookOf(LivingEntity body) {
		HeritageVariants.Variant v = HeritageVariants.of(body);
		PlayerClass cls = classOf(body);
		return v != null ? v.key() : cls == null ? "pallid" : "pallid:" + cls.key();
	}

	private static String rollKind(net.minecraft.util.RandomSource random) {
		int total = 0;
		for (int w : KIND_WEIGHTS) {
			total += w;
		}
		int roll = random.nextInt(total);
		for (int i = 0; i < KINDS.size(); i++) {
			roll -= KIND_WEIGHTS[i];
			if (roll < 0) {
				return KINDS.get(i);
			}
		}
		return KINDS.getFirst();
	}

	// ---- how many walk (per player) -----------------------------------------------------------------------------------------

		/** Default Hollow captains per player. */
		static final int DEFAULT_PLAYER_COUNT = PER_PLAYER;

		/** Get a player's Hollow captain count (max {@link #PER_PLAYER}). */
		static int playerCount(ServerPlayer player) {
			return player.getAttachedOrElse(PLAYER_COUNT, DEFAULT_PLAYER_COUNT);
		}

		/** Set a player's Hollow captain count. */
		private static void playerCount(ServerPlayer player, int value) {
			player.setAttached(PLAYER_COUNT, Math.max(0, Math.min(PER_PLAYER, value)));
		}

		/** How many more Hollow captains may stand up for this player. */
		static int playerRoom(ServerPlayer player) {
			return playerCount(player) - player.getAttachedOrElse(PLAYER_WALKING, List.of()).size();
		}

		/** Add or remove a body from a player's walking list. */
		private static void playerWalking(ServerPlayer player, Entity body, boolean is) {
			List<String> now = new ArrayList<>(player.getAttachedOrElse(PLAYER_WALKING, List.of()));
			String id = body.getStringUUID();
			if (is ? !now.contains(id) && now.add(id) : now.remove(id)) {
				player.setAttached(PLAYER_WALKING, List.copyOf(now));
			}
		}

		/** Get a player's ladder (captain ids). */
		static List<String> playerLadder(ServerPlayer player) {
			return new ArrayList<>(player.getAttachedOrElse(PLAYER_LADDER, List.of()));
		}

		/** Add a captain to a player's ladder (if not already present and room exists). Returns true if added. */
		static boolean addToLadder(ServerPlayer player, String captainId) {
			List<String> ladder = new ArrayList<>(player.getAttachedOrElse(PLAYER_LADDER, List.of()));
			if (ladder.contains(captainId)) {
				return false;
			}
			if (ladder.size() >= PER_PLAYER) {
				return false;
			}
			ladder.add(captainId);
			player.setAttached(PLAYER_LADDER, List.copyOf(ladder));
			return true;
		}

		/** Remove a captain from a player's ladder. */
		static void removeFromLadder(ServerPlayer player, String captainId) {
			List<String> ladder = new ArrayList<>(player.getAttachedOrElse(PLAYER_LADDER, List.of()));
			if (ladder.remove(captainId)) {
				player.setAttached(PLAYER_LADDER, List.copyOf(ladder));
			}
		}

		/** Check if a captain is on a player's ladder. */
		static boolean isOnLadder(ServerPlayer player, String captainId) {
			return player.getAttachedOrElse(PLAYER_LADDER, List.of()).contains(captainId);
		}

		/** Find a player who has this captain on their ladder. */
		static @Nullable ServerPlayer findPlayerWithCaptain(MinecraftServer server, String captainId) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (isOnLadder(p, captainId)) {
					return p;
				}
			}
			return null;
		}

		/** Get all online players who have room for more Hollow captains. */
		private static List<ServerPlayer> playersWithRoom(MinecraftServer server) {
			List<ServerPlayer> out = new ArrayList<>();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (fair(p) && playerRoom(p) > 0) {
					out.add(p);
				}
			}
			return out;
		}

		/** Pick a random online player with room for more Hollow captains. */
		private static @Nullable ServerPlayer randomPlayerWithRoom(MinecraftServer server, net.minecraft.util.RandomSource random) {
			List<ServerPlayer> candidates = playersWithRoom(server);
			if (candidates.isEmpty()) {
				return null;
			}
			return candidates.get(random.nextInt(candidates.size()));
		}

	// ---- legacy world-wide methods (for backward compatibility with existing worlds) ----

	/** @deprecated Use per-player {@link #playerCount(ServerPlayer)} instead. */
	@Deprecated
	static int count(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(COUNT, START);
	}

	/** @deprecated Use per-player {@link #playerCount(ServerPlayer, int)} instead. */
	@Deprecated
	private static void count(MinecraftServer server, int by) {
		Factions.level(server).setAttached(COUNT, Math.max(0, count(server) + by));
	}

	/** @deprecated Use per-player {@link #playerRoom(ServerPlayer)} instead. */
	@Deprecated
	static int room(MinecraftServer server) {
		return count(server) - Factions.level(server).getAttachedOrElse(WALKING, List.of()).size();
	}

	/** @deprecated Use per-player {@link #playerWalking(ServerPlayer, Entity, boolean)} instead. */
	@Deprecated
	private static void walking(MinecraftServer server, Entity body, boolean is) {
		List<String> now = new ArrayList<>(Factions.level(server).getAttachedOrElse(WALKING, List.of()));
		String id = body.getStringUUID();
		if (is ? !now.contains(id) && now.add(id) : now.remove(id)) {
			Factions.level(server).setAttached(WALKING, List.copyOf(now));
		}
	}

	/** {@code e} is a plain zombie or skeleton one of the Hollow may stand up in place of. */
	private static boolean plainDead(Entity e) {
		return e instanceof PathfinderMob && !is(e) && !Captains.is(e) && Rivals.roamer(e) == Rivals.Side.RAISED && armyOf(e) == null
			&& Banes.bandOf(e) == null;
	}

	/** (From {@code ZombieHordes}) A horde of the dead or of skeletons has come: now and then one of the Hollow walks in it for a nearby player. */
		static void joinHorde(ServerLevel level, List<net.minecraft.world.entity.Mob> members) {
			if (!live() || level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
				return;
			}
			ServerPlayer near = level.getNearestPlayer(members.getFirst().getX(), members.getFirst().getY(), members.getFirst().getZ(), ROAM_FAR, false);
			if (near == null || !fair(near) || playerRoom(near) <= 0 || level.getRandom().nextFloat() >= HORDE_CHANCE) {
				return;
			}
			for (int i = 0; i < members.size(); i++) {
				if (plainDead(members.get(i)) && members.get(i) instanceof PathfinderMob dead) {
					PathfinderMob body = rise(level, dead, null, near);
					if (body != null) {
						members.set(i, body);
					}
					return;
				}
			}
		}

	/** Every {@link #ROAM_EVERY} ticks, while there's room: one of the Hollow stands up among the dead near a random online player with room. */
		private static void roam(MinecraftServer server) {
			if (!live() || server.getTickCount() % ROAM_EVERY != 0) {
				return;
			}
			ServerLevel level = server.overworld();
			net.minecraft.util.RandomSource random = level.getRandom();
			ServerPlayer p = randomPlayerWithRoom(server, random);
			if (p == null || random.nextFloat() >= ROAM_CHANCE) {
				return;
			}
			List<PathfinderMob> dead = level.getEntitiesOfClass(PathfinderMob.class, p.getBoundingBox().inflate(ROAM_FAR),
				e -> e.isAlive() && plainDead(e) && e.distanceTo(p) >= ROAM_NEAR && !p.hasLineOfSight(e));
			if (!dead.isEmpty()) {
				rise(level, dead.get(random.nextInt(dead.size())), null, p);
			}
		}

	/** One of the Hollow died: unless he's a captain due back, there's one fewer. The dead took a villager: now and then, one more. */
		private static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
			if (!(entity.level() instanceof ServerLevel level) || !live()) {
				return;
			}
			MinecraftServer server = level.getServer();
			if (is(entity)) {
				// Find which player this Hollow belongs to
				Captain c = Captains.is(entity) ? Captains.of(entity) : null;
				String captainId = c == null ? null : c.id();
				ServerPlayer owner = captainId == null ? null : findPlayerWithCaptain(server, captainId);
			
				if (owner != null) {
					playerWalking(owner, entity, false);
					// If this captain is due back (fallen with back >= 0), they KEEP their place on the ladder
					// The COUNT only drops when they're gone for good
					if (c != null && c.fallen() && c.back() >= 0) {
						// Captain due back: keep their place on the ladder, don't decrement count
						// The walking list entry is removed (body died) but ladder spot is reserved
						return;
					}
					// Not a captain due back: permanently decrement the player's count
					playerCount(owner, playerCount(owner) - 1);
					removeFromLadder(owner, captainId);
					return;
				}
				// No owner found (shouldn't happen for captains, but handle non-captain Hollows)
				// Fall back to old behavior for non-captain Hollows
				walking(server, entity, false);
				if (c == null || !c.fallen() || c.back() < 0) {
					count(server, -1);
				}
				return;
			}
			Entity by = source.getEntity();
			if (entity instanceof net.minecraft.world.entity.npc.villager.Villager && by != null && (is(by) || Rivals.roamer(by) == Rivals.Side.RAISED)
				&& level.getRandom().nextFloat() < TAKE_CHANCE) {
				// Villager taken by the dead: find the player this killer belongs to and increment their count
				ServerPlayer owner = by instanceof ServerPlayer ? (ServerPlayer) by : findPlayerWithCaptain(server, Captains.is(by) ? Captains.of(by).id() : null);
				if (owner != null) {
					playerCount(owner, playerCount(owner) + 1);
				} else {
					count(server, 1);
				}
			}
		}

	// ---- his army -------------------------------------------------------------------------------------------------------

	/** A Pallid captain {@code c} has just risen in {@code body}: the dead get up round him. */
	static void muster(ServerLevel level, Captain c, LivingEntity body) {
		if (!is(body) || classOf(body) == null) {
			return;
		}
		net.minecraft.util.RandomSource random = level.getRandom();
		int count = ARMY_START + ARMY_PER_RANK * Math.max(0, c.rank() - 1);
		for (int i = 0; i < Math.min(count, ARMY_MOST); i++) {
			PathfinderMob dead = random.nextFloat() < ARMY_SKELETONS ? EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT)
				: EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			if (dead == null) {
				continue;
			}
			net.minecraft.core.BlockPos at = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				body.blockPosition().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4));
			dead.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			dead.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
			dead.addTag(Rivals.ROLLED_TAG);
			dead.addTag(ARMY_TAG + c.id());
			if (level.addFreshEntity(dead)) {
				level.sendParticles(ParticleTypes.SOUL, dead.getX(), dead.getY(0.3), dead.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
			}
		}
	}

	/** Whose army {@code e} is in (a captain id), or null. */
	static @Nullable String armyOf(Entity e) {
		for (String t : e.entityTags()) {
			if (t.startsWith(ARMY_TAG)) {
				return t.substring(ARMY_TAG.length());
			}
		}
		return null;
	}

	/**
	 * Every {@link #ARMY_EVERY} ticks for a Pallid captain: the dead near him who follow nobody fall in (to {@link #ARMY_MOST}),
	 * and his own go for whoever he goes for, or keep with him.
	 */
	private static void army(ServerLevel level, PathfinderMob leader, @Nullable LivingEntity foe) {
		Captain c = Captains.of(leader);
		if (c == null || c.fallen() || classOf(leader) == null || leader.tickCount % ARMY_EVERY != 0) {
			return;
		}
		String mine = ARMY_TAG + c.id();
		List<net.minecraft.world.entity.Mob> near = level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, leader.getBoundingBox().inflate(ARMY_CALL),
			m -> m != leader && m.isAlive() && !is(m) && Rivals.roamer(m) == Rivals.Side.RAISED);
		int have = 0;
		for (net.minecraft.world.entity.Mob m : near) {
			if (m.entityTags().contains(mine)) {
				have++;
			}
		}
		for (net.minecraft.world.entity.Mob m : near) {
			if (!m.entityTags().contains(mine)) {
				if (have >= ARMY_MOST || armyOf(m) != null) {
					continue;
				}
				m.addTag(mine);
				have++;
			}
			if (foe != null && foe.isAlive()) {
				if (m.getTarget() != foe) {
					m.setTarget(foe);
				}
			} else if (m.getTarget() == null && m.distanceTo(leader) > ARMY_KEEP && m.getNavigation().isDone()) {
				m.getNavigation().moveTo(leader, 1.0);
			}
		}
	}

	// ---- their fight ----------------------------------------------------------------------------------------------------

	/** One of the Hollow's step (from {@link Workers#step}, in place of the brain): a captain's ways, a Pallid's army, then the fight. */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (Downed.isDowned(mob)) {
			return false;
		}
		if (Captains.step(level, w)) {
			return true;
		}
		LivingEntity foe = foe(level, mob);
		army(level, mob, foe);
		if (foe == null) {
			if (mob.isUsingItem()) {
				mob.stopUsingItem();
			}
			if (mob.tickCount % WANDER_EVERY == 0 && mob.getNavigation().isDone()) {
				Vec3 to = DefaultRandomPos.getPos(mob, WANDER, 4);
				if (to != null) {
					mob.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
				}
			}
			return true;
		}
		Rulers.charge(mob, foe);
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		PlayerClass cls = classOf(mob);
		if (ranged(cls) || mob.getMainHandItem().getItem() instanceof BowItem) {
			shoot(level, mob, foe);
		} else {
			strike(level, mob, foe);
		}
		ability(level, mob, foe, cls);
		return true;
	}

	/** Who they go for: whoever hurt them lately, else the nearest player they can see within their sight (as the dead do). */
	private static @Nullable LivingEntity foe(ServerLevel level, PathfinderMob mob) {
		LivingEntity by = mob.getLastHurtByMob();
		if (by != null && by.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < GRUDGE_TICKS && !(by instanceof ServerPlayer p && !fair(p))) {
			return by;
		}
		double sight = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
		ServerPlayer near = null;
		for (ServerPlayer p : level.players()) {
			if (fair(p) && p.distanceTo(mob) <= sight && mob.hasLineOfSight(p) && (near == null || p.distanceTo(mob) < near.distanceTo(mob))) {
				near = p;
			}
		}
		return near;
	}

	/** A player they'd go for: alive, not watching, and (as the dead leave them be) not carrying the Hollow's blight. */
	private static boolean fair(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative() && !HollowBlight.blighted(p);
	}

	/** A fighter's: close and strike with what he holds. */
		private static void strike(ServerLevel level, PathfinderMob mob, LivingEntity foe) {
			if (!mob.isWithinMeleeAttackRange(foe)) {
				if (mob.tickCount % PATH_REFRESH_TICKS == 0 || mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(foe, 1.0);
				}
				return;
			}
		mob.getNavigation().stop();
		if (mob.tickCount % MELEE_EVERY == 0) {
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			mob.doHurtTarget(level, foe);
		}
	}

	/**
	 * What his class could do in life, at most every so often: a caster's bolt at a foe out of arm's reach, a mender's healing of
	 * the worst hurt of his army (or himself).
	 */
	private static void ability(ServerLevel level, PathfinderMob mob, LivingEntity foe, @Nullable PlayerClass cls) {
		long now = level.getGameTime();
		if (now < NEXT.getOrDefault(mob.getUUID(), 0L)) {
			return;
		}
		if (caster(cls)) {
			double d = mob.distanceTo(foe);
			if (d < BOLT_NEAR || d > BOLT_FAR || !mob.hasLineOfSight(foe)) {
				return;
			}
			NEXT.put(mob.getUUID(), now + BOLT_EVERY);
			Vec3 dir = new Vec3(foe.getX(), foe.getY(0.5), foe.getZ()).subtract(mob.getEyePosition()).normalize();
			SmallFireball bolt = new SmallFireball(level, mob, dir);
			bolt.setPos(mob.getX() + dir.x * 0.8, mob.getEyeY() - 0.15, mob.getZ() + dir.z * 0.8);
			level.addFreshEntity(bolt);
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.8F);
		} else if (mender(cls)) {
					Captain c = Captains.of(mob);
					LivingEntity worst = mob.getHealth() < mob.getMaxHealth() - MEND_THRESHOLD ? mob : null;
					if (c != null) {
						for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mob.getBoundingBox().inflate(MEND_RANGE),
							m -> m.isAlive() && m.entityTags().contains(ARMY_TAG + c.id()) && m.getHealth() < m.getMaxHealth() - MEND_THRESHOLD)) {
					if (worst == null || m.getHealth() / m.getMaxHealth() < worst.getHealth() / worst.getMaxHealth()) {
						worst = m;
					}
				}
			}
			if (worst != null) {
				NEXT.put(mob.getUUID(), now + MEND_EVERY);
				worst.heal(MEND);
				Fx.ring(level, worst.position(), 1.2F, MEND_COLOUR);
				level.playSound(null, worst.getX(), worst.getY(), worst.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 1.2F);
			}
		}
	}

	/** A bowman's fight: in range and in sight, draw and loose; too near, back off; else come on. */
	private static void shoot(ServerLevel level, PathfinderMob mob, LivingEntity foe) {
		long now = level.getGameTime();
		long[] bow = BOWS.computeIfAbsent(mob.getUUID(), k -> new long[] {Long.MIN_VALUE / 2, -1L});
		double d = mob.distanceTo(foe);
		boolean sees = mob.hasLineOfSight(foe);
		if (d > BOW_RANGE || !sees) {
			if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone()) {
				mob.getNavigation().moveTo(foe, 1.0);
			}
		} else if (d < BOW_NEAR) {
					if (mob.tickCount % 10 == 0) {
						Vec3 away = DefaultRandomPos.getPosAway(mob, RETREAT_RADIUS, RETREAT_ATTEMPTS, foe.position());
						if (away != null) {
							mob.getNavigation().moveTo(away.x, away.y, away.z, RETREAT_SPEED);
						}
					}
		} else {
			mob.getNavigation().stop();
		}
		if (!sees || d > BOW_RANGE || now - bow[0] < BOW_EVERY) {
			if (mob.isUsingItem()) {
				mob.stopUsingItem();
			}
			bow[1] = -1L;
			return;
		}
		if (bow[1] < 0) {
			bow[1] = now;
			mob.startUsingItem(InteractionHand.MAIN_HAND);
			return;
		}
		if (now - bow[1] < BOW_DRAW) {
			return;
		}
		mob.stopUsingItem();
		ItemStack held = mob.getMainHandItem();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, new ItemStack(Items.ARROW), 1.0F, held);
		double dx = foe.getX() - mob.getX(), dz = foe.getZ() - mob.getZ();
		double dy = foe.getY(0.3333) - arrow.getY();
		arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, BOW_SPEED, BOW_SPREAD);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		level.addFreshEntity(arrow);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F,
			1.0F / (mob.getRandom().nextFloat() * 0.4F + 0.8F));
		bow[0] = now;
		bow[1] = -1L;
	}
}
