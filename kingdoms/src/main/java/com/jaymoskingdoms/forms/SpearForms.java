package com.jaymoskingdoms.forms;

import com.jaymoskingdoms.CombatArts;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.forms.Forms.Family;
import com.jaymoskingdoms.forms.Forms.Form;
import com.jaymoskingdoms.forms.Forms.Kind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.382) The spear family's forms (the {@code form_spears} tag: vanilla spears and tridents, Powers' lances and Tinkers
 * javelin, WeaponMod's spears and javelin).
 * <ul>
 * <li>Spear Lunge: a long lunge (longer than the rapier's) that runs the first foe in the way through, and goes on into a
 * second one behind it.
 * <li>Couched Lance: only while you control a mount (a horse, a Cavalry mount, a pig, a camel...). The lance is couched for
 * {@value #COUCH_TICKS} ticks; the first foe it meets is hit for damage that grows with how fast the mount is going, and a
 * foe in the saddle is knocked out of it. Riders hit harder ({@link #RIDER_BONUS}). Kingdoms' mounted guards that carry a spear
 * charge too ({@link #charge}, called from {@code Cavalry}).
 * <li>Pinning Throw (masterstroke): the held spear is thrown at a fallen foe and pins it where it lies (rooted) for a few
 * seconds. A second press of any form key tugs the spear back to the hand. The spear is never used up: it is kept (and saved
 * on the player, so a crash can't lose it) until it comes back to the hand when tugged, or drops at the foe when the pin
 * runs out or the foe dies.
 * </ul>
 */
public final class SpearForms {
	static final Form SPEAR_LUNGE = new Form("spear_lunge", Family.SPEAR, Kind.ART, 90, 5, 1.5F, 6);
	static final Form COUCHED_LANCE = new Form("couched_lance", Family.SPEAR, Kind.ART, 120, 0, 2.0F, 6);
	static final Form PINNING_THROW = new Form("pinning_throw", Family.SPEAR, Kind.MASTERSTROKE, 300, 6, 2.5F, 4);

	/** Spear Lunge: speed (blocks/tick) at rank 1 and each rank past it (the rapier's is 0.65, about 2.8 blocks; 0.95 carries about 4.1), the lift, and how long it can still strike (ticks). */
	static final double LUNGE_SPEED = 0.95, LUNGE_SPEED_RANK = 0.07, LUNGE_UP = 0.12;
	static final int LUNGE_TICKS = 9;
	/** How far in front of the lunger the point reaches (blocks; the rapier's is 1.4). */
	static final double LUNGE_REACH = 1.9;
	/** What the first foe takes (times your attack), the share the foe behind it takes, how far behind the first foe the point goes on and how wide that reach is (blocks). */
	static final float LUNGE_DAMAGE = 1.5F, PIERCE_SHARE = 0.75F;
	static final double PIERCE_DEPTH = 2.6, PIERCE_WIDTH = 0.5;
	/** The share of the lunge's speed kept once it has struck. */
	static final double LUNGE_KEPT = 0.25;

	/** Couched Lance: how long the lance stays couched (ticks), how far ahead it reaches (blocks), the slowest mount that still drives it home (blocks/tick). */
	static final int COUCH_TICKS = 30;
	static final double COUCH_REACH = 3.2, COUCH_MIN_SPEED = 0.1;
	/** Its damage (times your attack): a base and what each block/tick of the mount's speed adds (a galloping horse is about 0.3-0.4), up to a cap; a Rider's blow is this much harder. */
	static final float COUCH_BASE = 0.6F, COUCH_PER_SPEED = 8.0F, COUCH_MAX = 4.0F, RIDER_BONUS = 1.3F;
	/** The shove a foe takes (per block/tick of mount speed) and the slowness of one thrown from the saddle (ticks, level). */
	static final double COUCH_SHOVE = 2.0;
	static final int UNSEAT_SLOW_TICKS = 40, UNSEAT_SLOW = 2;
	/** How much the mount's recent speed counts the newest tick (the rest is the older figure): it smooths out a horse's stride. */
	static final double SPEED_EASE = 0.5;
	/** Mounted guards: how often they look for a foe to charge (ticks), how far off a foe is charged from (blocks), the wait between charges (ticks), the most they ever deal (times their attack). */
	public static final int NPC_CHECK_TICKS = 10, NPC_COOLDOWN = 80;
	public static final double NPC_REACH = 3.4, NPC_MIN_SPEED = 0.12;
	static final float NPC_MAX = 2.5F;

	/** Pinning Throw: how long the pin holds (ticks) and each rank adds, the throw's damage (times your attack; it must not kill outright), how far from the foe you can be (blocks) before it lets go, the biggest foe it can hold (max health). */
	static final int PIN_TICKS = 80, PIN_RANK_TICKS = 20;
	static final float PIN_DAMAGE = 0.35F;
	static final double PIN_LEASH = 24.0;
	static final float PIN_MAX_HEALTH = 150.0F;
	/** A pinned foe is held with this much slowness (level) refreshed every few ticks, and shown this often (ticks). */
	static final int PIN_SLOW = 9, PIN_REFRESH = 5, PIN_SHOW = 10;
	/** The screen shake of each (about 0.3 a nudge, 1 a blow). */
	static final float LUNGE_SHAKE = 0.3F, COUCH_SHAKE = 0.5F, PIN_SHAKE = 0.4F;

	/** The spear held for a Pinning Throw in flight: saved on the player so a crash or a restart can't lose it. */
	private static final AttachmentType<ItemStack> PINNED = AttachmentRegistry.create(JaymosKingdoms.id("pinned_spear"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC));

	/** A lunge under way: until when it can strike, which way, what it deals, the form's rank. */
	private record Lunge(long until, Vec3 dir, float damage, int rank) {
	}

	/** A couched lance: until when, the form's rank. */
	private record Couch(long until, int rank) {
	}

	/** A pin: the spear held, whom it holds, until when, the dimension, and where the foe last lay. */
	private static final class Pin {
		final ItemStack spear;
		final UUID foe;
		final long until;
		final ResourceKey<Level> dimension;
		Vec3 lastAt;

		Pin(ItemStack spear, UUID foe, long until, ResourceKey<Level> dimension, Vec3 lastAt) {
			this.spear = spear;
			this.foe = foe;
			this.until = until;
			this.dimension = dimension;
			this.lastAt = lastAt;
		}
	}

	private static final Map<UUID, Lunge> LUNGES = new HashMap<>();
	private static final Map<UUID, Couch> COUCHES = new HashMap<>();
	private static final Map<UUID, Pin> PINS = new HashMap<>();
	/** A rider's mount: where it was last tick, and how fast it is going (blocks/tick, smoothed). */
	private static final Map<UUID, Vec3> MOUNT_AT = new HashMap<>();
	private static final Map<UUID, Double> MOUNT_SPEED = new HashMap<>();
	/** When each mounted guard may charge again (game time). */
	private static final Map<Entity, Long> NPC_READY = new WeakHashMap<>();

	private SpearForms() {
	}

	static void addTo(Map<String, Form> forms) {
		forms.put(SPEAR_LUNGE.id(), SPEAR_LUNGE);
		forms.put(COUCHED_LANCE.id(), COUCHED_LANCE);
		forms.put(PINNING_THROW.id(), PINNING_THROW);
	}

	/** Called from {@link Forms#register}. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SpearForms::tick);
		// a spear still in flight when the player was last on is handed back as they join (a crash or a restart mid-pin)
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ItemStack kept = handler.player.getAttachedOrElse(PINNED, ItemStack.EMPTY);
			if (!kept.isEmpty() && !PINS.containsKey(handler.player.getUUID())) {
				handler.player.removeAttached(PINNED);
				giveBack(handler.player, kept);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			endPin(handler.player, null, false);
			UUID id = handler.player.getUUID();
			LUNGES.remove(id);
			COUCHES.remove(id);
			MOUNT_AT.remove(id);
			MOUNT_SPEED.remove(id);
		});
		// the server going down: every spear still pinning comes home first
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				endPin(player, null, false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LUNGES.clear();
			COUCHES.clear();
			PINS.clear();
			MOUNT_AT.clear();
			MOUNT_SPEED.clear();
			NPC_READY.clear();
		});
	}

	static void perform(ServerPlayer player, Form form, int rank) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (form == SPEAR_LUNGE) {
			lunge(level, player, rank);
		} else if (form == COUCHED_LANCE) {
			couch(level, player, rank);
		} else if (form == PINNING_THROW) {
			pinningThrow(level, player, form, rank);
		}
	}

	/** Whether {@code player} controls a mount (the saddle of a horse, a Cavalry mount, a pig, a camel...): what Couched Lance needs. */
	static boolean mounted(ServerPlayer player) {
		return player.getVehicle() instanceof LivingEntity mount && mount.getControllingPassenger() == player;
	}

	/** Whether a Pinning Throw is holding a foe for {@code player} (their hand is empty of the spear until it is tugged back). */
	static boolean pinned(Player player) {
		return PINS.containsKey(player.getUUID());
	}

	// ---- Spear Lunge ---------------------------------------------------------------------------------------------------

	private static void lunge(ServerLevel level, ServerPlayer player, int rank) {
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4) {
			look = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		look = look.normalize();
		double speed = LUNGE_SPEED + LUNGE_SPEED_RANK * (rank - 1);
		player.setDeltaMovement(look.x * speed, Math.max(player.getDeltaMovement().y, 0.0) + LUNGE_UP, look.z * speed);
		player.syncVelocity = true;
		LUNGES.put(player.getUUID(), new Lunge(level.getGameTime() + LUNGE_TICKS, look, Forms.damage(player, LUNGE_DAMAGE, rank), rank));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.5F);
		Vec3 from = player.position().add(0.0, 1.1, 0.0);
		Trails.spear(level, from, from.add(look.scale(1.5)));
	}

	private static void tickLunge(ServerLevel level, ServerPlayer player, Lunge lunge) {
		UUID id = player.getUUID();
		boolean over = level.getGameTime() > lunge.until();
		if (!player.isAlive() || player.isPassenger() || !Family.SPEAR.holds(player.getMainHandItem()) || over) {
			LUNGES.remove(id);
			if (over && player.isAlive()) {
				// (a lunge at nothing still counts as practice at a post)
				Forms.landed(player, SPEAR_LUNGE, List.of());
			}
			return;
		}
		AABB reach = player.getBoundingBox().expandTowards(lunge.dir().scale(LUNGE_REACH)).inflate(0.3, 0.1, 0.3);
		LivingEntity first = nearest(Forms.targets(level, player, reach), player.position(), null);
		Vec3 from = player.position().add(0.0, 1.1, 0.0);
		Trails.spear(level, from.subtract(lunge.dir().scale(0.6)), from);
		if (first == null) {
			return;
		}
		LUNGES.remove(id);
		List<LivingEntity> hits = new ArrayList<>();
		hits.add(first);
		CombatArts.strike(level, player, first, lunge.damage());
		// on through the first foe into the one behind it
		AABB behind = first.getBoundingBox().expandTowards(lunge.dir().scale(PIERCE_DEPTH)).inflate(PIERCE_WIDTH, 0.3, PIERCE_WIDTH);
		LivingEntity second = nearest(Forms.targets(level, player, behind), first.position(), first);
		Vec3 tip = first.getBoundingBox().getCenter();
		if (second != null) {
			hits.add(second);
			CombatArts.strike(level, player, second, lunge.damage() * PIERCE_SHARE);
			Vec3 through = second.getBoundingBox().getCenter();
			Trails.spear(level, tip, through);
			level.sendParticles(ParticleTypes.CRIT, through.x, through.y, through.z, 8, 0.25, 0.3, 0.25, 0.25);
			tip = through;
		}
		level.sendParticles(ParticleTypes.CRIT, first.getX(), first.getY(0.6), first.getZ(), 8, 0.25, 0.3, 0.25, 0.25);
		level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.3F);
		// the lunge spends itself on the foe
		Vec3 now = player.getDeltaMovement();
		player.setDeltaMovement(now.x * LUNGE_KEPT, now.y, now.z * LUNGE_KEPT);
		player.syncVelocity = true;
		PowersCompat.shake(player, LUNGE_SHAKE);
		Forms.landed(player, SPEAR_LUNGE, hits);
	}

	/** The one of {@code list} nearest {@code to}, leaving out {@code not}. */
	private static @Nullable LivingEntity nearest(List<LivingEntity> list, Vec3 to, @Nullable LivingEntity not) {
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : list) {
			double d = e.distanceToSqr(to);
			if (e != not && d < bestDistance) {
				best = e;
				bestDistance = d;
			}
		}
		return best;
	}

	// ---- Couched Lance -------------------------------------------------------------------------------------------------

	private static void couch(ServerLevel level, ServerPlayer player, int rank) {
		if (!mounted(player)) {
			return;
		}
		COUCHES.put(player.getUUID(), new Couch(level.getGameTime() + COUCH_TICKS, rank));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.couched").withStyle(ChatFormatting.GOLD));
	}

	/** How hard a lance couched on a mount going {@code speed} blocks/tick lands (times the wielder's attack, before rank and class). */
	static float couchMultiple(double speed) {
		return Mth.clamp(COUCH_BASE + (float)speed * COUCH_PER_SPEED, COUCH_BASE, COUCH_MAX);
	}

	private static void tickCouch(ServerLevel level, ServerPlayer player, Couch couch) {
		UUID id = player.getUUID();
		if (!player.isAlive() || !mounted(player) || !Family.SPEAR.holds(player.getMainHandItem()) || level.getGameTime() > couch.until()) {
			COUCHES.remove(id);
			return;
		}
		double speed = MOUNT_SPEED.getOrDefault(id, 0.0);
		if (speed < COUCH_MIN_SPEED) {
			// (too slow to drive it home: it stays couched until it runs out)
			return;
		}
		Entity mount = player.getVehicle();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : look.normalize();
		AABB reach = player.getBoundingBox().expandTowards(look.scale(COUCH_REACH)).inflate(0.4, 0.5, 0.4);
		List<LivingEntity> foes = new ArrayList<>(Forms.targets(level, player, reach));
		foes.removeIf(e -> e == mount || e.isPassengerOfSameVehicle(player) || e.hasPassenger(player));
		LivingEntity foe = nearest(foes, player.position(), null);
		Vec3 from = player.position().add(0.0, 1.3, 0.0);
		Trails.spear(level, from, from.add(look.scale(2.0)));
		if (foe == null) {
			return;
		}
		COUCHES.remove(id);
		float multiple = couchMultiple(speed) * (Classes.is(player, Classes.PlayerClass.RIDER) ? RIDER_BONUS : 1.0F);
		CombatArts.strike(level, player, foe, Forms.damage(player, multiple, couch.rank()));
		unseat(level, foe);
		if (foe.isAlive()) {
			foe.setDeltaMovement(foe.getDeltaMovement().add(look.x * speed * COUCH_SHOVE, 0.25, look.z * speed * COUCH_SHOVE));
			foe.needsSync = true;
		}
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.6), foe.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
		PowersCompat.shake(player, COUCH_SHAKE);
		Forms.landed(player, COUCHED_LANCE, List.of(foe));
	}

	/** Knocks {@code foe} out of the saddle, and whoever sits on it, if it is a mount, out of theirs. */
	private static void unseat(ServerLevel level, LivingEntity foe) {
		if (foe.isPassenger()) {
			foe.stopRiding();
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, UNSEAT_SLOW_TICKS, UNSEAT_SLOW));
			level.sendParticles(ParticleTypes.POOF, foe.getX(), foe.getY(0.5), foe.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
		}
		if (foe.isVehicle()) {
			for (Entity rider : new ArrayList<>(foe.getPassengers())) {
				rider.stopRiding();
				if (rider instanceof LivingEntity living) {
					living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, UNSEAT_SLOW_TICKS, UNSEAT_SLOW));
				}
			}
		}
	}

	/**
	 * (0.7.382) A mounted guard's charge, from {@code Cavalry}: if {@code rider} sits on {@code mount} going fast enough, holds
	 * a spear and {@code target} is within {@link #NPC_REACH}, the lance goes home (damage by the mount's speed, capped, no class
	 * bonus) and a foe in the saddle is knocked out of it. At most once every {@link #NPC_COOLDOWN} ticks, so a guard that can't
	 * close on its foe doesn't loop. True if it struck.
	 */
	public static boolean charge(ServerLevel level, Mob rider, LivingEntity mount, LivingEntity target) {
		long now = level.getGameTime();
		if (!rider.isAlive() || !target.isAlive() || !rider.getMainHandItem().is(Forms.SPEARS) || NPC_READY.getOrDefault(rider, 0L) > now) {
			return false;
		}
		Vec3 motion = mount.getDeltaMovement();
		double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
		if (speed < NPC_MIN_SPEED || mount.distanceTo(target) > NPC_REACH + target.getBbWidth() / 2.0F) {
			return false;
		}
		Vec3 to = target.position().subtract(mount.position()).multiply(1.0, 0.0, 1.0);
		// (it must be going toward the foe, not past it)
		if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(motion.multiply(1.0, 0.0, 1.0).normalize()) < 0.3) {
			return false;
		}
		NPC_READY.put(rider, now + NPC_COOLDOWN);
		double attack = rider.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 3.0 : rider.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float damage = (float)attack * Math.min(NPC_MAX, couchMultiple(speed));
		target.hurtServer(level, rider.damageSources().mobAttack(rider), damage);
		unseat(level, target);
		if (target.isAlive()) {
			target.setDeltaMovement(target.getDeltaMovement().add(motion.x * COUCH_SHOVE, 0.25, motion.z * COUCH_SHOVE));
			target.needsSync = true;
		}
		Vec3 from = rider.position().add(0.0, 1.5, 0.0);
		Trails.spear(level, from, target.getBoundingBox().getCenter());
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.3);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.0F, 0.8F);
		return true;
	}

	// ---- Pinning Throw -------------------------------------------------------------------------------------------------

	private static void pinningThrow(ServerLevel level, ServerPlayer player, Form form, int rank) {
		LivingEntity foe = Forms.masterTarget(player);
		ItemStack held = player.getMainHandItem();
		if (foe == null || held.isEmpty() || PINS.containsKey(player.getUUID())) {
			return;
		}
		// the spear leaves the hand (one of a stack of javelins: one), and is kept, on the player too, until it comes home
		ItemStack spear = held.copyWithCount(1);
		held.shrink(1);
		if (held.isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		player.setAttached(PINNED, spear.copy());
		PINS.put(player.getUUID(), new Pin(spear, foe.getUUID(), level.getGameTime() + PIN_TICKS + PIN_RANK_TICKS * (rank - 1), level.dimension(), foe.position()));
		Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 to = foe.getBoundingBox().getCenter();
		Trails.spear(level, from, to);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.TRIDENT_HIT_GROUND, SoundSource.PLAYERS, 1.0F, 0.9F);
		CombatArts.strike(level, player, foe, Forms.damage(player, PIN_DAMAGE, rank));
		PowersCompat.shake(player, PIN_SHAKE);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.form.pinned").withStyle(ChatFormatting.GOLD));
		Forms.landed(player, form, List.of(foe));
		if (!foe.isAlive()) {
			endPin(player, foe, true);
		}
	}

	/**
	 * A press while a spear is thrown: tugs it back to the hand. True if there was a pin to tug (so {@link Forms} treats the
	 * press as spent).
	 */
	static boolean tug(ServerPlayer player) {
		Pin pin = PINS.get(player.getUUID());
		if (pin == null) {
			return false;
		}
		endPin(player, player.level() instanceof ServerLevel level ? level.getEntity(pin.foe) : null, false, true);
		return true;
	}

	private static void endPin(ServerPlayer player, @Nullable Entity foe, boolean dropAtFoe) {
		endPin(player, foe, dropAtFoe, false);
	}

	/**
	 * Lets a pin go. {@code tugged}: it flies back to the hand. {@code dropAtFoe}: it drops where the foe lay (the pin ran out, or
	 * the foe died). Otherwise (leaving the game, the world ending) it goes straight into the inventory. A spear can't be lost: it
	 * is always handed over, dropped or put away.
	 */
	private static void endPin(ServerPlayer player, @Nullable Entity foe, boolean dropAtFoe, boolean tugged) {
		Pin pin = PINS.remove(player.getUUID());
		if (pin == null) {
			return;
		}
		player.removeAttached(PINNED);
		if (foe instanceof LivingEntity living) {
			living.removeEffect(MobEffects.SLOWNESS);
		}
		if (!(player.level() instanceof ServerLevel level)) {
			giveBack(player, pin.spear);
			return;
		}
		if (tugged && player.isAlive() && level.dimension().equals(pin.dimension)) {
			Vec3 at = foe != null ? foe.getBoundingBox().getCenter() : pin.lastAt;
			Trails.spear(level, at, player.getEyePosition().add(0.0, -0.3, 0.0));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 1.0F, 1.1F);
			giveBack(player, pin.spear);
		} else if (dropAtFoe || !player.isAlive() || !level.dimension().equals(pin.dimension)) {
			if (level.dimension().equals(pin.dimension)) {
				Vec3 at = foe != null ? foe.position() : pin.lastAt;
				level.addFreshEntity(new ItemEntity(level, at.x, at.y + 0.3, at.z, pin.spear));
			} else {
				// (another dimension: it can't drop where the foe lay)
				giveBack(player, pin.spear);
			}
		} else {
			giveBack(player, pin.spear);
		}
	}

	/** The spear into the hand if it is empty, else the inventory, else at the player's feet. */
	private static void giveBack(ServerPlayer player, ItemStack spear) {
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, spear);
		} else if (!player.getInventory().add(spear) && !spear.isEmpty() && player.level() instanceof ServerLevel level) {
			level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), spear));
		}
	}

	private static void tickPin(MinecraftServer server, ServerPlayer player, Pin pin) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Entity found = level.getEntity(pin.foe);
		long now = level.getGameTime();
		if (!player.isAlive() || !level.dimension().equals(pin.dimension)) {
			endPin(player, found, true);
			return;
		}
		if (!(found instanceof LivingEntity foe) || !foe.isAlive()) {
			endPin(player, found, true);
			return;
		}
		pin.lastAt = foe.position();
		if (now >= pin.until || foe.distanceTo(player) > PIN_LEASH) {
			endPin(player, foe, true);
			return;
		}
		// held fast: no walking, no jumping (a fall still falls)
		if (foe.getMaxHealth() < PIN_MAX_HEALTH) {
			Vec3 motion = foe.getDeltaMovement();
			foe.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
			if (foe instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if (now % PIN_REFRESH == 0) {
				foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_REFRESH * 2, PIN_SLOW, false, false), player);
				if (foe instanceof ServerPlayer) {
					foe.syncVelocity = true;
				}
			}
		}
		if (now % PIN_SHOW == 0) {
			level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, pin.spear.getItem()), foe.getX(), foe.getY(0.3), foe.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
			level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.5), foe.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		// how fast each rider's mount is going (its own movement, which the server doesn't keep as a velocity for a ridden horse)
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (player.getVehicle() != null) {
				Vec3 at = player.getVehicle().position();
				Vec3 before = MOUNT_AT.put(id, at);
				if (before != null) {
					double moved = Math.sqrt((at.x - before.x) * (at.x - before.x) + (at.z - before.z) * (at.z - before.z));
					MOUNT_SPEED.merge(id, moved, (old, now) -> old * (1.0 - SPEED_EASE) + now * SPEED_EASE);
				}
			} else if (MOUNT_AT.remove(id) != null) {
				MOUNT_SPEED.remove(id);
			}
			if (!LUNGES.isEmpty() && LUNGES.get(id) instanceof Lunge lunge && player.level() instanceof ServerLevel level) {
				tickLunge(level, player, lunge);
			}
			if (!COUCHES.isEmpty() && COUCHES.get(id) instanceof Couch couch && player.level() instanceof ServerLevel level) {
				tickCouch(level, player, couch);
			}
			if (!PINS.isEmpty() && PINS.get(id) instanceof Pin pin) {
				tickPin(server, player, pin);
			}
		}
		// (maps of players who are gone are cleared as they leave; a lunge or couch of someone who went elsewhere ends on its own time)
		if (!LUNGES.isEmpty() || !COUCHES.isEmpty()) {
			long now = server.overworld().getGameTime();
			LUNGES.values().removeIf(l -> now > l.until() + 200L);
			COUCHES.values().removeIf(c -> now > c.until() + 200L);
		}
	}
}
