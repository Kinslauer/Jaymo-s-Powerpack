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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The risen dead's captains are Hollow men. Andy (2026-11-25): "the named zombies and skeletons should be replaced with
 * skeleton, pallid, or zombie hollow npcs instead, this makes more sense that they can speak, and will return from the dead.
 * they should fight the same as their counterparts at default only learning as they progress."
 * <ul>
 * <li><b>Who they are:</b> a captain of the raised ({@link Rivals.Side#RAISED}) no longer wears a plain zombie or skeleton. When
 * one rises (a zombie or skeleton near a player, {@link Rivals#loaded}; one that killed a player, {@code Captains.slew}), an MCA
 * villager of the Hollow's blood stands up in its place: a skeleton gives a Skeleton Hollow, a zombie a Zombie Hollow or now
 * and then ({@link #PALLID_SHARE}) a Pallid. Captains of the raised still in a zombie's or skeleton's body from before are made
 * Hollow the next time they're loaded.</li>
 * <li><b>The same man back:</b> his look and his whole saved self are kept ({@link #LOOKS}, {@link #FACES}), so when he comes
 * back from the dead it's him again, in the next of the dead to walk his land, whatever that was. Like the Pallid, he always
 * comes back, unless he was finished while down, lost his head or burned.</li>
 * <li><b>How they fight:</b> as the dead they rose from, at first: a zombie's or skeleton's health, blow, pace and sight
 * ({@link #ZOMBIE_HEALTH}, {@link #SKELETON_HEALTH}), a Zombie Hollow or a Pallid closing to strike bare-handed or with what it
 * held, a Skeleton Hollow keeping off and loosing arrows. A new one has none of a captain's strengths or moves: those come only
 * as he climbs (a strength for each player he kills, what he learns each time he comes back, a move of his own at rank four).</li>
 * <li>The dead leave them be ({@code mixin/nightgifts/ZombieBlightMixin}), and they're outside the law ({@link Outlaws}).</li>
 * </ul>
 * Without MCA the raised keep their zombies and skeletons, as before.
 */
public final class HollowCaptains {
	// ================================================================================================================
	// Tuning
	/** Of the captains who rise from a zombie, this share are Pallid; the rest Zombie Hollows. */
	static final float PALLID_SHARE = 0.3F;
	/** A zombie's build, before a captain's: health, blow, pace, hide, how far it sees. */
	static final double ZOMBIE_HEALTH = 20.0, ZOMBIE_DAMAGE = 3.0, ZOMBIE_SPEED = 0.23, ZOMBIE_ARMOUR = 2.0, ZOMBIE_SIGHT = 35.0;
	/** A skeleton's. */
	static final double SKELETON_HEALTH = 20.0, SKELETON_DAMAGE = 2.0, SKELETON_SPEED = 0.25, SKELETON_SIGHT = 16.0;
	/** A blow at most this often (ticks): a zombie's. */
	static final int MELEE_EVERY = 20;
	/** A skeleton's bow: shoots from within this range (blocks), backs off inside this, draws this long and shoots this often (ticks). */
	static final double BOW_RANGE = 15.0, BOW_NEAR = 5.0;
	static final int BOW_DRAW = 20, BOW_EVERY = 40;
	/** The arrow's speed and spread (a skeleton's on normal difficulty). */
	static final float BOW_SPEED = 1.6F, BOW_SPREAD = 6.0F;
	/** Ticks a foe who hurt them stays their foe; how often an idle one wanders, and how far (blocks). */
	static final int GRUDGE_TICKS = 200, WANDER_EVERY = 120, WANDER = 8;
	// ================================================================================================================

	/** On every Hollow captain's body. */
	public static final String TAG = "jaymoskingdoms.hollow_captain";
	/** Their role: outside the law, enlisted to nobody. */
	static final int ROLE = 98;

	/** On the Overworld: captain id → his look ({@code zombie}, {@code skeleton}, {@code pallid}). Saved. */
	static final AttachmentType<Map<String, String>> LOOKS = AttachmentRegistry.create(JaymosKingdoms.id("hollow_captain_looks"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** On the Overworld: captain id → his body's saved self (face, name, blood). Saved. */
	static final AttachmentType<Map<String, CompoundTag>> FACES = AttachmentRegistry.create(JaymosKingdoms.id("hollow_captain_faces"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC)));

	/** Work put off to the end of the tick (a body can't be swapped while its chunk is still loading it). */
	private static final List<Runnable> LATER = new ArrayList<>();
	/** Each one's last shot and when it started drawing (game time). */
	private static final Map<java.util.UUID, long[]> BOWS = new HashMap<>();

	private HollowCaptains() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> BOWS.remove(entity.getUUID()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (LATER.isEmpty()) {
				return;
			}
			List<Runnable> now = List.copyOf(LATER);
			LATER.clear();
			now.forEach(Runnable::run);
		});
	}

	/** Whether the raised rise as Hollow men (MCA is here). */
	static boolean live() {
		return McaCompat.LOADED;
	}

	/** {@code e} is one of the risen dead's Hollow captains (or was). */
	public static boolean is(Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** {@code e} is a raised captain still in a plain zombie's or skeleton's body. */
	static boolean stillDead(Entity e) {
		if (!live() || is(e) || !Captains.is(e)) {
			return false;
		}
		Captain c = Captains.of(e);
		return c != null && !c.fallen() && Rivals.side(c.tribe()) == Rivals.Side.RAISED;
	}

	static void later(Runnable r) {
		LATER.add(r);
	}

	// ---- rising ---------------------------------------------------------------------------------------------------------

	/**
	 * A Hollow man stands up in {@code dead}'s place (it's gone): {@code id}'s own if he was one before, else a new one of the
	 * look {@code dead} gives. Null (and {@code dead} left as it is) without MCA or if none could be made.
	 */
	static @Nullable PathfinderMob rise(ServerLevel level, PathfinderMob dead, @Nullable String id) {
		if (!live() || is(dead)) {
			return null;
		}
		MinecraftServer server = level.getServer();
		String was = id == null ? null : Factions.level(server).getAttachedOrElse(LOOKS, Map.of()).get(id);
		String look = was != null ? was : dead instanceof AbstractSkeleton ? "skeleton" : level.getRandom().nextFloat() < PALLID_SHARE ? "pallid" : "zombie";
		CompoundTag face = id == null ? null : Factions.level(server).getAttachedOrElse(FACES, Map.of()).get(id);
		PathfinderMob body = McaCompat.hollowBody(level, dead.position(), dead.getYRot(), ROLE, face);
		if (body == null) {
			return null;
		}
		if (face == null) {
			VillagerHeritage.born(body, Heritage.Kind.HOLLOW, null);
		}
		HeritageVariants.set(body, "pallid".equals(look) ? null : HeritageVariants.Variant.byKey(look));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			body.setItemSlot(slot, ItemStack.EMPTY);
			body.setDropChance(slot, 0.0F);
		}
		// (what it rose with: a skeleton's bow, a zombie's empty hands or whatever it picked up)
		ItemStack held = dead.getMainHandItem();
		body.setItemSlot(EquipmentSlot.MAINHAND, "skeleton".equals(look) ? held.getItem() instanceof BowItem ? held.copy() : new ItemStack(Items.BOW)
			: held.getItem() instanceof BowItem ? ItemStack.EMPTY : held.copy());
		build(body, "skeleton".equals(look));
		body.addTag(TAG);
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

	/** As {@link #rise}, or {@code dead} itself if no Hollow could stand up in its place. */
	static PathfinderMob riseOr(ServerLevel level, PathfinderMob dead, @Nullable String id) {
		PathfinderMob body = rise(level, dead, id);
		return body == null ? dead : body;
	}

	/** The dead's own build, which a captain's is put on top of: a skeleton's or a zombie's. */
	private static void build(PathfinderMob body, boolean skeleton) {
		base(body, Attributes.MAX_HEALTH, skeleton ? SKELETON_HEALTH : ZOMBIE_HEALTH);
		base(body, Attributes.ATTACK_DAMAGE, skeleton ? SKELETON_DAMAGE : ZOMBIE_DAMAGE);
		base(body, Attributes.MOVEMENT_SPEED, skeleton ? SKELETON_SPEED : ZOMBIE_SPEED);
		base(body, Attributes.ARMOR, skeleton ? 0.0 : ZOMBIE_ARMOUR);
		base(body, Attributes.FOLLOW_RANGE, skeleton ? SKELETON_SIGHT : ZOMBIE_SIGHT);
		body.setHealth(body.getMaxHealth());
	}

	private static void base(LivingEntity body, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> what, double value) {
		AttributeInstance a = body.getAttribute(what);
		if (a != null) {
			a.setBaseValue(value);
		}
	}

	/** {@code body} is captain {@code id}'s: his look, and (the first time) his saved self, kept for when he comes back. */
	static void keep(MinecraftServer server, String id, LivingEntity body) {
		if (!is(body)) {
			return;
		}
		ServerLevel home = Factions.level(server);
		HeritageVariants.Variant v = HeritageVariants.of(body);
		Map<String, String> looks = new HashMap<>(home.getAttachedOrElse(LOOKS, Map.of()));
		looks.put(id, v == null ? "pallid" : v.key());
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

	/** (From {@link Rivals#loaded}, put off a tick) A raised captain still in a zombie's or skeleton's body gets up a Hollow. */
	static void rehouse(ServerLevel level, PathfinderMob dead) {
		Captain c = Captains.of(dead);
		if (c == null || c.fallen() || !dead.isAlive() || dead.isRemoved()) {
			return;
		}
		PathfinderMob body = rise(level, dead, c.id());
		if (body != null) {
			Captains.embodyIn(level, c, body);
			keep(level.getServer(), c.id(), body);
		}
	}

	/** (From {@link Rivals#loaded}, put off a tick) Captain {@code id}, due back, gets up in {@code dead}'s place. */
	static void comeBack(ServerLevel level, String id, PathfinderMob dead) {
		Captain c = Captains.get(level.getServer(), id);
		if (c == null || c.fallen() || c.body().isPresent() || !dead.isAlive() || dead.isRemoved() || Captains.is(dead)) {
			return;
		}
		PathfinderMob body = riseOr(level, dead, id);
		Captains.embodyIn(level, c, body);
		keep(level.getServer(), id, body);
	}

	// ---- their fight ----------------------------------------------------------------------------------------------------

	/** A Hollow captain's step (from {@link Workers#step}, in place of his brain): his captain's ways, then the dead's fight. */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (Downed.isDowned(mob)) {
			return false;
		}
		if (Captains.step(level, w)) {
			return true;
		}
		LivingEntity foe = foe(level, mob);
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
		if (mob.getMainHandItem().getItem() instanceof BowItem) {
			shoot(level, mob, foe);
		} else {
			strike(level, mob, foe);
		}
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

	/** A zombie's fight: close and strike. */
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

	/** A skeleton's fight: in range and in sight, draw and loose; too near, back off; else come on. */
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
