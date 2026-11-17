package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.ClassTrees;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.Fear;
import com.jaymoskingdoms.Followers;
import com.jaymoskingdoms.JaymosKingdoms;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jspecify.annotations.Nullable;

/**
 * The Rider's tree (class trees, docs/ClassTrees-Plan.md): the saddle. The equestrian systems ({@link Equestrian},
 * {@link Bonding}, {@link Herding}, {@link Mounted}, {@link Riders}) ask here what the Rider's ranks change.
 * <ul>
 * <li><b>Spur</b> (from the start): the class key at the gallop: a short burst that costs no stamina; its wait shortens with
 * the Rider's level (practice).</li>
 * <li><b>Long Wind</b> (3): the horse's stamina drains less, each rank.</li>
 * <li><b>Horse Whisperer</b> (3): your horse never shies or throws you, and a timid one steadies under you.</li>
 * <li><b>Lancer</b> or <b>Horse Archer</b> (5): a spear at the gallop hits harder still and the mounted charge builds twice
 * as fast; or bows and crossbows from the saddle shoot true (no stray from the shot or the horse's sway).</li>
 * <li><b>Stay in the Saddle</b> (8): blows and blasts don't knock you off.</li>
 * <li><b>Trick Riding</b> (10): the class key off the gallop hangs you off the horse's side (and back up): arrows meet the
 * horse instead of you, and at the gallop you lean down and scoop up what lies on the ground, or a downed friend behind
 * you.</li>
 * <li><b>Cavalier</b> or <b>Outrider</b> (15): barding weighs nothing, charges and tramples hit harder and the war discipline
 * comes twice as fast; or faster at the trot and canter, your horse takes no falls, and the whistle, in the saddle, swaps
 * you onto a fresh horse from your herd on the move.</li>
 * <li><b>Herd Master</b> (18): the command wheel orders all your horses about at once, and they come to your aid whatever
 * their trust.</li>
 * <li><b>Banner Bearer</b> (20): a banner flown from your saddle: friends near you fight braver (no fear, a little harder)
 * and their horses gallop longer.</li>
 * <li><b>Bonded</b> or <b>Beast of Legend</b> (top): your horse goes down instead of dying, and can be brought round; or the
 * mounts of legend and war elephants trust you at once, and a bat horse flies twice as long.</li>
 * </ul>
 */
public final class Horsemanship {
	/** Spur: how much faster (a share of its speed), for how long (ticks), and the wait after (ticks, shortened by practice). */
	static final double SPUR_SPEED = 0.4;
	static final int SPUR_TICKS = 50, SPUR_COOLDOWN = 20 * 30;
	/** Long Wind: this share less stamina drained, a rank. */
	static final double LONG_WIND = 0.15;
	/** Horse Whisperer: a timid horse is this much braver under you. */
	static final double WHISPER_NERVE = 0.3;
	/** Lancer: the lance's gallop bonus this many times over; the charge builds this many times as fast. */
	static final double LANCER = 1.5;
	static final float LANCER_CHARGE = 2.0F;
	/** Horse Archer: a shot this close to the look (the cosine of the angle off it) is set straight along it. */
	static final double ARCHER_TRUE = 0.99;
	/** Stay in the Saddle: back on a horse no farther than this, knocked off in the tick it was hit. */
	static final double REMOUNT_REACH = 4.0;
	/** Trick Riding: a shot that finds you hanging hits the horse for this share; what you scoop up and from how far. */
	static final float HANG_SHIELD = 0.5F;
	static final double SCOOP_REACH = 2.5;
	/** Cavalier: charges and tramples this many times as hard; war discipline this many times as fast. */
	static final float CAVALIER_CHARGE = 1.5F, CAVALIER_WAR = 2.0F;
	/** Outrider: this many times as fast at the trot and canter; a fresh horse comes from this far, and is swapped onto this close. */
	static final double OUTRIDER_PACE = 1.15, SWAP_RANGE = 96.0, SWAP_REACH = 3.0, SWAP_SPEED = 2.6;
	static final int SWAP_TICKS = 20 * 30;
	/** Banner Bearer: how far it is seen; friends' blows this share harder; their horses' stamina this share. */
	static final double BANNER_REACH = 16.0, BANNER_WIND = 0.75;
	static final float BANNER_HEART = 0.1F;
	/** Beast of Legend: a bat horse aloft tires at this share. */
	static final double LEGEND_WIND = 0.5;

	public static final String WIND = "rid_wind", WHISPER = "rid_whisper", LANCER_KEY = "rid_lancer", ARCHER = "rid_archer", STAY = "rid_stay",
		TRICK = "rid_trick", CAVALIER = "rid_cavalier", OUTRIDER = "rid_outrider", HERD = "rid_herd", BANNER = "rid_banner",
		BONDED = "rid_bonded", LEGEND = "rid_legend";

	/** Hanging off the side (Trick Riding): seen by everyone, for the pose. Not kept. */
	public static final AttachmentType<Boolean> HANGING = AttachmentRegistry.create(JaymosKingdoms.id("rider_hanging"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Identifier SPUR_ID = JaymosKingdoms.id("rider_spur");
	/** Spur: until when each horse runs faster, and when each Rider may spur again. */
	private static final Map<AbstractHorse, Long> SPURRED = new HashMap<>();
	private static final Map<UUID, Long> SPUR_READY = new HashMap<>();
	/** Stay in the Saddle: the horse each one rode last tick, and when they were last hit. */
	private static final Map<UUID, AbstractHorse> MOUNT = new HashMap<>();
	private static final Map<UUID, Long> HIT = new HashMap<>();
	/** Outrider: fresh horses coming to their Rider (horse → rider), and until when. */
	private static final Map<AbstractHorse, UUID> SWAPPING = new HashMap<>();
	private static final Map<AbstractHorse, Long> SWAP_UNTIL = new HashMap<>();
	/** Banner Bearers this second. */
	private static final List<ServerPlayer> BEARERS = new ArrayList<>();

	private Horsemanship() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Horsemanship::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SPURRED.clear();
			SPUR_READY.clear();
			MOUNT.clear();
			HIT.clear();
			SWAPPING.clear();
			SWAP_UNTIL.clear();
			BEARERS.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && has(player, STAY)) {
				HIT.put(player.getUUID(), player.level().getGameTime());
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Horsemanship::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			// Bonded: a horse already down stays down rather than dying, to be brought round
			if (entity instanceof AbstractHorse horse && Downed.isDowned(horse) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
				&& owner(horse) instanceof ServerPlayer owner && has(owner, BONDED)) {
				horse.setHealth(1.0F);
				return false;
			}
			return true;
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			// Horse Archer: a shot from the saddle flies true
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer archer && archer.getVehicle() instanceof AbstractHorse
				&& has(archer, ARCHER)) {
				double speed = arrow.getDeltaMovement().length();
				// (a multishot's side bolts are left their spread)
				if (speed > 0.0 && arrow.getDeltaMovement().normalize().dot(archer.getLookAngle()) > ARCHER_TRUE) {
					arrow.setDeltaMovement(archer.getLookAngle().scale(speed));
				}
			}
		});
	}

	static boolean has(@Nullable Entity e, String key) {
		return e instanceof Player player && Classes.is(player, PlayerClass.RIDER) && ClassTrees.has(player, key) > 0;
	}

	private static @Nullable Player owner(AbstractHorse horse) {
		if (!(horse instanceof OwnableEntity ownable) || ownable.getOwnerReference() == null || !(horse.level() instanceof ServerLevel level)) {
			return null;
		}
		return level.getServer().getPlayerList().getPlayer(ownable.getOwnerReference().getUUID());
	}

	// ---- what the equestrian systems ask -------------------------------------------------------------------------------

	/** How fast {@code horse} spends stamina, as a share ({@link Equestrian#drain}): Long Wind, a banner near, a legend's wings. */
	static double wind(AbstractHorse horse) {
		Entity rider = horse.getControllingPassenger();
		double d = 1.0;
		if (rider instanceof Player player && Classes.is(player, PlayerClass.RIDER)) {
			d *= Math.max(0.2, 1.0 - LONG_WIND * ClassTrees.power(player, WIND));
			if (has(player, LEGEND) && MythicMounts.is(horse, Breed.BAT_HORSE) && !horse.onGround()) {
				d *= LEGEND_WIND;
			}
		}
		if (rider instanceof LivingEntity ridden && underBanner(ridden)) {
			d *= BANNER_WIND;
		}
		return d;
	}

	/** Horse Whisperer: {@code rider}'s horse neither shies nor throws them. */
	static boolean whisperer(@Nullable Entity rider) {
		return has(rider, WHISPER);
	}

	/** Horse Whisperer: how much braver a horse is under its rider. */
	static double nerve(AbstractHorse horse) {
		return whisperer(horse.getControllingPassenger()) ? WHISPER_NERVE : 0.0;
	}

	/** Stay in the Saddle: blows and blasts leave {@code rider} on. */
	static boolean stays(@Nullable Entity rider) {
		return has(rider, STAY);
	}

	/** Lancer: how many times over the lance's gallop bonus counts. */
	static double lancer(Player rider) {
		return has(rider, LANCER_KEY) ? LANCER : 1.0;
	}

	/** Lancer: how many times as fast the charge builds. */
	static float lancerCharge(Player rider) {
		return has(rider, LANCER_KEY) ? LANCER_CHARGE : 1.0F;
	}

	/** Cavalier: barding weighs nothing under {@code rider}. */
	static boolean light(@Nullable Entity rider) {
		return has(rider, CAVALIER);
	}

	/** Cavalier: how many times as hard {@code rider}'s charges and tramples hit. */
	static float cavalier(@Nullable Entity rider) {
		return has(rider, CAVALIER) ? CAVALIER_CHARGE : 1.0F;
	}

	/** Cavalier: how many times as fast the war discipline is learnt under {@code rider}. */
	static float warSchool(@Nullable Entity rider) {
		return has(rider, CAVALIER) ? CAVALIER_WAR : 1.0F;
	}

	/** Outrider: how many times as fast at {@code gait} (both sides: the rider's client moves the horse). */
	public static double pace(@Nullable Entity rider, Gait gait) {
		return (gait == Gait.TROT || gait == Gait.CANTER) && has(rider, OUTRIDER) ? OUTRIDER_PACE : 1.0;
	}

	/** Herd Master: {@code player}'s horses come to their aid whatever their trust, and take the wheel's orders all at once. */
	static boolean herdMaster(Player player) {
		return has(player, HERD);
	}

	/** Herd Master: an order given from the wheel to one horse goes to all of {@code player}'s horses about. */
	static void orderAll(ServerLevel level, ServerPlayer player, AbstractHorse given, Herd.Order order) {
		if (!herdMaster(player)) {
			return;
		}
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(Herding.ORDER_RANGE),
			h -> h != given && Equestrian.covered(h) && h.isAlive() && h.isTamed() && !h.isVehicle() && Bonding.owner(h, player)
				&& !com.jaymoskingdoms.Stables.boarded(h))) {
			if (order == Herd.Order.HOME) {
				Herding.sendHome(level, horse, null);
			} else {
				Herding.give(level, horse, order);
			}
		}
	}

	// ---- the class key ---------------------------------------------------------------------------------------------------

	/** The class key in the saddle: hanging off the side and back up (Trick Riding), or Spur at the gallop. */
	public static void key(ServerPlayer player, boolean sneak) {
		if (!(player.getVehicle() instanceof AbstractHorse horse) || !Equestrian.covered(horse) || horse.getControllingPassenger() != player) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.spur_saddle"));
			return;
		}
		ServerLevel level = player.level();
		boolean gallop = Equestrian.stride(horse).gaitOf() == Gait.GALLOP;
		if (Boolean.TRUE.equals(player.getAttached(HANGING)) || !gallop && has(player, TRICK)) {
			hang(player, !Boolean.TRUE.equals(player.getAttached(HANGING)));
			return;
		}
		if (!gallop) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.spur_gallop"));
			return;
		}
		long now = level.getGameTime();
		long ready = SPUR_READY.getOrDefault(player.getUUID(), 0L);
		if (ready > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.spur_wait", (ready - now) / 20 + 1));
			return;
		}
		// practice: the wait shortens with the Rider's level
		SPUR_READY.put(player.getUUID(), now + Math.round(SPUR_COOLDOWN / ClassTrees.practice(player, PlayerClass.RIDER)));
		SPURRED.put(horse, now + SPUR_TICKS);
		AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SPUR_ID, SPUR_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 1.2F, 1.2F);
		level.sendParticles(ParticleTypes.CLOUD, horse.getX(), horse.getY() + 0.2, horse.getZ(), 8, 0.4, 0.1, 0.4, 0.05);
	}

	private static void hang(ServerPlayer player, boolean on) {
		if (on) {
			player.setAttached(HANGING, true);
		} else {
			player.removeAttached(HANGING);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, on ? 0.9F : 1.2F);
	}

	/** Trick Riding: a shot finding a rider hanging off the side meets the horse instead. Outrider: the horse takes no falls. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (entity instanceof ServerPlayer player && Boolean.TRUE.equals(player.getAttached(HANGING)) && source.is(DamageTypeTags.IS_PROJECTILE)
			&& player.getVehicle() instanceof AbstractHorse horse && player.level() instanceof ServerLevel level) {
			horse.hurtServer(level, source, amount * HANG_SHIELD);
			return false;
		}
		return !(entity instanceof AbstractHorse horse && source.is(DamageTypeTags.IS_FALL) && has(horse.getControllingPassenger(), OUTRIDER));
	}

	// ---- Outrider's swap ----------------------------------------------------------------------------------------------

	/** The whistle in the saddle (an Outrider's): the freshest of their other horses comes, to be swapped onto at the gallop. */
	static boolean swap(ServerPlayer player) {
		if (!(player.getVehicle() instanceof AbstractHorse ridden) || !has(player, OUTRIDER)) {
			return false;
		}
		ServerLevel level = player.level();
		AbstractHorse best = null;
		float most = -1.0F;
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(SWAP_RANGE),
			h -> h != ridden && Equestrian.covered(h) && h.isAlive() && h.isTamed() && !h.isBaby() && !h.isVehicle() && Bonding.owner(h, player)
				&& !Downed.isDowned(h) && !com.jaymoskingdoms.Stables.boarded(h) && !h.isLeashed())) {
			float stamina = Equestrian.stride(horse).stamina();
			float fresh = stamina < 0.0F ? Float.MAX_VALUE : stamina;
			if (fresh > most) {
				most = fresh;
				best = horse;
			}
		}
		if (best == null) {
			return false;
		}
		SWAPPING.put(best, player.getUUID());
		SWAP_UNTIL.put(best, level.getGameTime() + SWAP_TICKS);
		best.playSound(SoundEvents.HORSE_AMBIENT, 1.5F, 1.1F);
		best.getNavigation().moveTo(player, SWAP_SPEED);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.fresh_horse", Bonding.name(best)));
		return true;
	}

	private static void swapping(MinecraftServer server, long now) {
		var it = SWAPPING.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			AbstractHorse fresh = e.getKey();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getValue());
			if (player == null || !fresh.isAlive() || fresh.level() != player.level() || SWAP_UNTIL.getOrDefault(fresh, 0L) < now || fresh.isVehicle()) {
				it.remove();
				SWAP_UNTIL.remove(fresh);
				continue;
			}
			if (!(player.getVehicle() instanceof AbstractHorse old)) {
				it.remove();
				SWAP_UNTIL.remove(fresh);
				continue;
			}
			if (fresh.distanceTo(player) <= SWAP_REACH) {
				it.remove();
				SWAP_UNTIL.remove(fresh);
				player.stopRiding();
				if (player.startRiding(fresh)) {
					Herding.give(player.level(), old, Herd.Order.FOLLOW);
					player.level().playSound(null, fresh.blockPosition(), SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
				} else {
					player.startRiding(old);
				}
				continue;
			}
			if (now % 10 == 0) {
				fresh.getNavigation().moveTo(player, SWAP_SPEED);
			}
		}
	}

	// ---- Banner Bearer --------------------------------------------------------------------------------------------------

	private static boolean bearer(ServerPlayer player) {
		if (!has(player, BANNER) || !(player.getVehicle() instanceof AbstractHorse horse)) {
			return false;
		}
		Tack tack = horse.getAttached(Bonding.TACK);
		return tack != null && !tack.banner().isEmpty();
	}

	/** Whether {@code e} is one of a Banner Bearer's friends, near the banner. */
	private static boolean underBanner(LivingEntity e) {
		for (ServerPlayer bearer : BEARERS) {
			if (bearer.level() == e.level() && bearer.distanceToSqr(e) <= BANNER_REACH * BANNER_REACH
				&& (e instanceof Player || Followers.takesOrders(e, bearer) || e instanceof OwnableEntity o && o.getOwnerReference() != null
					&& bearer.getUUID().equals(o.getOwnerReference().getUUID()))) {
				return true;
			}
		}
		return false;
	}

	/** A friend's blow under a Banner Bearer's banner lands a little harder ({@link com.jaymoskingdoms.TreePerks#dealt}). */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (BEARERS.isEmpty() || !(source.getEntity() instanceof LivingEntity attacker) || attacker == target || !underBanner(attacker)) {
			return amount;
		}
		return amount * (1.0F + BANNER_HEART);
	}

	// ---- the tick -------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!SPURRED.isEmpty()) {
			SPURRED.entrySet().removeIf(e -> {
				if (e.getValue() > now && e.getKey().isAlive()) {
					return false;
				}
				AttributeInstance speed = e.getKey().getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) {
					speed.removeModifier(SPUR_ID);
				}
				return true;
			});
		}
		if (!SWAPPING.isEmpty()) {
			swapping(server, now);
		}
		boolean second = server.getTickCount() % 20 == 0;
		if (second) {
			BEARERS.clear();
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AbstractHorse horse = player.getVehicle() instanceof AbstractHorse h && Equestrian.covered(h) ? h : null;
			if (horse == null && player.hasAttached(HANGING)) {
				player.removeAttached(HANGING);
			}
			if (!Classes.is(player, PlayerClass.RIDER)) {
				continue;
			}
			// Stay in the Saddle: knocked off by the blow that just landed: back on at once
			if (has(player, STAY)) {
				if (horse != null) {
					MOUNT.put(player.getUUID(), horse);
				} else {
					AbstractHorse was = MOUNT.remove(player.getUUID());
					Long hit = HIT.get(player.getUUID());
					if (was != null && hit != null && now - hit <= 1 && was.isAlive() && !was.isVehicle() && !Downed.isDowned(player)
						&& !Downed.isDowned(was) && was.distanceTo(player) <= REMOUNT_REACH) {
						player.startRiding(was);
					}
				}
			}
			if (horse != null && Boolean.TRUE.equals(player.getAttached(HANGING)) && Equestrian.stride(horse).gaitOf() == Gait.GALLOP) {
				scoop(player, horse);
			}
			if (second && horse != null) {
				if (bearer(player)) {
					BEARERS.add(player);
				}
				if (has(player, LEGEND) && (MythicMounts.is(horse, Breed.BAT_HORSE) || MythicMounts.is(horse, Breed.NIGHTMARE) || horse instanceof WarElephant)) {
					legend(player, horse);
				}
			}
		}
		if (second) {
			for (ServerPlayer bearer : BEARERS) {
				for (LivingEntity friend : bearer.level().getEntitiesOfClass(LivingEntity.class, bearer.getBoundingBox().inflate(BANNER_REACH),
					e -> e.isAlive() && Fear.frightened(e))) {
					if (underBanner(friend)) {
						Fear.calm(friend);
					}
				}
			}
			if (now % 1200 == 0) {
				SPUR_READY.values().removeIf(t -> t < now);
				HIT.values().removeIf(t -> t < now - 20);
			}
		}
	}

	/** Leaning down at the gallop: what lies close is scooped up, and a downed friend is pulled up behind. */
	private static void scoop(ServerPlayer player, AbstractHorse horse) {
		ServerLevel level = player.level();
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, horse.getBoundingBox().inflate(SCOOP_REACH, 1.0, SCOOP_REACH), ItemEntity::isAlive)) {
			item.setNoPickUpDelay();
			item.playerTouch(player);
		}
		if (horse.getPassengers().size() != 1) {
			return;
		}
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, horse.getBoundingBox().inflate(SCOOP_REACH, 1.0, SCOOP_REACH),
			e -> e != player && e != horse && !e.isPassenger() && Downed.isDowned(e) && (e instanceof Player || Followers.takesOrders(e, player)))) {
			if (body.startRiding(horse, true, true)) {
				level.playSound(null, horse.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.rescued", body.getDisplayName()));
				return;
			}
		}
	}

	/** Beast of Legend: a mount of legend or a war elephant trusts its rider at once (a wild one is theirs). */
	private static void legend(ServerPlayer player, AbstractHorse horse) {
		if (!horse.isTamed()) {
			horse.tameWithName(player);
			player.level().sendParticles(ParticleTypes.HEART, horse.getX(), horse.getY() + horse.getBbHeight(), horse.getZ(), 6, 0.4, 0.3, 0.4, 0.0);
		}
		Bond b = Bonding.bond(horse);
		if (b.trustOf(player.getUUID()) < 100.0F) {
			horse.setAttached(Bonding.BOND, b.withTrust(player.getUUID(), 100.0F));
		}
	}
}
