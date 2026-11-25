package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The risen dead are led by the Pallid. Andy (2026-11-25): "the named zombies and skeletons should be replaced with skeleton,
 * pallid, or zombie hollow npcs instead, this makes more sense that they can speak, and will return from the dead. they should
 * fight the same as their counterparts at default only learning as they progress." Then: "the pallids are intelligent hollow
 * and will end up leading armies of the skeleton and zombies, as another faction. the captain ladder should work primarily
 * through them as you encounter them most. that also gives skeletons and zombies reason to gain titles again."
 * <ul>
 * <li><b>Who leads:</b> when a new captain of the raised ({@link Rivals.Side#RAISED}) rises from a zombie or skeleton near a
 * player ({@link Rivals#loaded}), most often ({@link #PALLID_LEAD}) a Pallid stands up in its place: an MCA villager of the
 * Hollow's blood, of the first look, who can be talked to. Otherwise the dead one takes the title himself, as before, and so
 * does a zombie or skeleton that kills a player ({@code Captains.slew}): the titled dead are the Pallids' lieutenants.</li>
 * <li><b>Their armies:</b> a Pallid captain calls up {@link #ARMY_START} of the dead (and {@link #ARMY_PER_RANK} more a rank)
 * when he rises, and gathers the untitled and titled dead near him to {@link #ARMY_MOST}: they keep with him and go for whoever
 * he goes for ({@link #army}).</li>
 * <li><b>The same man back:</b> his class and his whole saved self are kept ({@link #LOOKS}, {@link #FACES}), so when
 * he comes back from the dead it's him again, in the next of the dead to walk his land.</li>
 * <li><b>How they fight:</b> (Andy: "pallids should fight with their old class abilities and weapons") a Pallid keeps the
 * class he had in life ({@link PlayerClass}, rolled when he first rises): its starting weapons and armour, what it's good at
 * (a Warlord's or Brute's heavier blow, a Thief's speed, a Paladin's hide), a Ranger's or Marksman's shooting from afar, a
 * caster's bolt and a Cleric's or Druid's mending of his army. On a zombie's body ({@link #ZOMBIE_HEALTH}). A new captain has
 * none of a captain's strengths or moves: those come only as he climbs (a strength for each player he kills, what he learns
 * each time he comes back, a move of his own at rank four).</li>
 * <li>The dead leave them be ({@code mixin/nightgifts/ZombieBlightMixin}), and they're outside the law ({@link Outlaws}).</li>
 * </ul>
 * Without MCA the raised keep their zombies and skeletons, as before.
 */
public final class HollowCaptains {
	// ================================================================================================================
	// Tuning
	/** Of the raised's new captains who rise near a player, this share are Pallids who lead the dead; the rest titled dead. */
	static final float PALLID_LEAD = 0.75F;
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
	/** A blow at most this often (ticks): a zombie's. */
	static final int MELEE_EVERY = 20;
	/** A bowman's: shoots from within this range (blocks), backs off inside this, draws this long and shoots this often (ticks). */
	static final double BOW_RANGE = 15.0, BOW_NEAR = 5.0;
	static final int BOW_DRAW = 20, BOW_EVERY = 40;
	/** The arrow's speed and spread (a skeleton's on normal difficulty). */
	static final float BOW_SPEED = 1.6F, BOW_SPREAD = 6.0F;
	/** Ticks a foe who hurt them stays their foe; how often an idle one wanders, and how far (blocks). */
	static final int GRUDGE_TICKS = 200, WANDER_EVERY = 120, WANDER = 8;
	// ================================================================================================================

	/** On every Pallid captain's body. */
	public static final String TAG = "jaymoskingdoms.hollow_captain";
	/** On each of the dead in a Pallid captain's army, then his captain id. */
	static final String ARMY_TAG = "jaymoskingdoms.pallid_army:";
	/** On each Pallid captain, then the key of the class he had in life. */
	static final String CLASS_TAG = "jaymoskingdoms.pallid_class:";
	/** Their role: outside the law, enlisted to nobody. */
	static final int ROLE = 98;

	/** On the Overworld: Pallid captain id → the class he had in life ({@link PlayerClass#key}). Saved. */
	static final AttachmentType<Map<String, String>> LOOKS = AttachmentRegistry.create(JaymosKingdoms.id("pallid_captain_classes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On the Overworld: captain id → his body's saved self (face, name, blood). Saved. */
	static final AttachmentType<Map<String, CompoundTag>> FACES = AttachmentRegistry.create(JaymosKingdoms.id("pallid_captain_faces"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC)));

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
		});
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
	 * A Pallid stands up in {@code dead}'s place (it's gone): {@code id}'s own if he was one before, else a new one, of a class
	 * rolled now. Null (and {@code dead} left as it is) without MCA or if none could be made.
	 */
	static @Nullable PathfinderMob rise(ServerLevel level, PathfinderMob dead, @Nullable String id) {
		if (!live() || is(dead)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		String was = id == null ? null : Factions.level(server).getAttachedOrElse(LOOKS, Map.of()).get(id);
		PlayerClass cls = was == null ? null : PlayerClass.byKey(was);
		if (cls == null) {
			// (what he was in life: any of the classes)
			PlayerClass[] all = PlayerClass.values();
			cls = all[level.getRandom().nextInt(all.length)];
		}
		CompoundTag face = id == null ? null : Factions.level(server).getAttachedOrElse(FACES, Map.of()).get(id);
		PathfinderMob body = McaCompat.hollowBody(level, dead.position(), dead.getYRot(), ROLE, face);
		if (body == null) {
			return null;
		}
		if (face == null) {
			VillagerHeritage.born(body, Heritage.Kind.HOLLOW, null);
		}
		// (the Pallid: the Hollow's first look)
		HeritageVariants.set(body, null);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			body.setItemSlot(slot, ItemStack.EMPTY);
			body.setDropChance(slot, 0.0F);
		}
		build(body, cls);
		body.addTag(TAG);
		body.addTag(CLASS_TAG + cls.key());
		body.addTag(Rivals.ROLLED_TAG);
		if (!level.addFreshEntity(body)) {
			return null;
		}
		McaCompat.untree(level, body);
		level.sendParticles(ParticleTypes.SOUL, body.getX(), body.getY(0.4), body.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.HOSTILE, 0.8F, 0.7F);
		dead.discard();
		return body;
	}

	/** As {@link #rise}, or {@code dead} itself if no Pallid could stand up in its place. */
	static PathfinderMob riseOr(ServerLevel level, PathfinderMob dead, @Nullable String id) {
		PathfinderMob body = rise(level, dead, id);
		return body == null ? dead : body;
	}

	/**
	 * His build and arms, which a captain's are put on top of: a zombie's body (a ranged class sees as far as a skeleton), the
	 * weapons and armour his class starts with ({@link Classes#gear}), and what his class is good at (as the Flame Avatars'
	 * classes are: {@code nether/AvatarClasses}).
	 */
	private static void build(PathfinderMob body, PlayerClass cls) {
		base(body, Attributes.MAX_HEALTH, ZOMBIE_HEALTH);
		base(body, Attributes.ATTACK_DAMAGE, ZOMBIE_DAMAGE);
		base(body, Attributes.MOVEMENT_SPEED, ZOMBIE_SPEED);
		base(body, Attributes.ARMOR, ZOMBIE_ARMOUR);
		base(body, Attributes.FOLLOW_RANGE, ranged(cls) ? SKELETON_SIGHT : ZOMBIE_SIGHT);
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

	/** {@code body} is Pallid captain {@code id}'s: how he fights, and (the first time) his saved self, kept for when he comes back. */
	static void keep(MinecraftServer server, String id, LivingEntity body) {
		if (!is(body)) {
			return;
		}
		ServerLevel home = Factions.level(server);
		PlayerClass cls = classOf(body);
		if (cls == null) {
			return;
		}
		Map<String, String> looks = new HashMap<>(home.getAttachedOrElse(LOOKS, Map.of()));
		looks.put(id, cls.key());
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

	/** (From {@link Rivals#loaded}, put off a tick) Captain {@code id}, due back, gets up in {@code dead}'s place. */
	static void comeBack(ServerLevel level, String id, PathfinderMob dead) {
		Captain c = Captains.get(level.getServer(), id);
		if (c == null || c.fallen() || c.body().isPresent() || !dead.isAlive() || dead.isRemoved() || Captains.is(dead)) {
			return;
		}
		// (a Pallid gets up again a Pallid; one of the titled dead takes the next of the dead, as before)
		boolean pallid = Factions.level(level.getServer()).getAttachedOrElse(LOOKS, Map.of()).containsKey(id);
		PathfinderMob body = pallid ? riseOr(level, dead, id) : dead;
		Captains.embodyIn(level, c, body);
		keep(level.getServer(), id, body);
		muster(level, c, body);
	}

	// ---- his army -------------------------------------------------------------------------------------------------------

	/** A Pallid captain {@code c} has just risen in {@code body}: the dead get up round him. */
	static void muster(ServerLevel level, Captain c, LivingEntity body) {
		if (!is(body)) {
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
		if (c == null || leader.tickCount % ARMY_EVERY != 0) {
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

	/** A Pallid captain's step (from {@link Workers#step}, in place of his brain): his captain's ways, his army, then the dead's fight. */
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
		if (ranged(cls)) {
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
			if (mob.tickCount % 10 == 0 || mob.getNavigation().isDone()) {
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
			LivingEntity worst = mob.getHealth() < mob.getMaxHealth() - 2.0F ? mob : null;
			if (c != null) {
				for (net.minecraft.world.entity.Mob m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, mob.getBoundingBox().inflate(MEND_RANGE),
					m -> m.isAlive() && m.entityTags().contains(ARMY_TAG + c.id()) && m.getHealth() < m.getMaxHealth() - 2.0F)) {
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
				Vec3 away = DefaultRandomPos.getPosAway(mob, 8, 3, foe.position());
				if (away != null) {
					mob.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
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
