package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a Rider (the class) is beyond the numbers in {@code ClassPerks}. Andy: "noone plays it as is", then "add everything
 * you thought up, and everything in this wheel should be available to riders immediately, not exclusively". So:
 * <ul>
 * <li><b>Every order, at once:</b> a horse heeds its Rider owner's command wheel, roster call and whistle from the day
 * it's theirs, without waiting on its trust ({@link #heeds}). Anyone else still earns it as before.</li>
 * <li><b>Breaking horses:</b> a Rider tames a wild horse (or llama) in a fraction of the tries ({@link #TAME_HEAD_START})
 * and it starts out trusting them more ({@link #TAME_TRUST}); trust grows faster too ({@code ClassPerks.RIDER_TRUST}).</li>
 * <li><b>Bareback:</b> a Rider rides and steers any tamed horse, camel or llama without a saddle ({@link #bareback}).
 * Everyone else still needs one.</li>
 * <li><b>A horse of their own:</b> a new Rider gets a saddled horse that already trusts them ({@link #starter}).</li>
 * <li><b>Lance charge:</b> a lance or spear from the saddle hits harder the faster the mount is going ({@link #lance}),
 * and their horse's gallop charge fills quicker and runs things down harder ({@link #CHARGE_FILL}, {@link #RUN_DOWN}).</li>
 * <li><b>Running mount:</b> jump at a sprint beside your own horse and you vault into the saddle ({@link #vault}); the
 * whistle reaches much further and the horse comes at a gallop ({@link #WHISTLE_RANGE}).</li>
 * <li><b>Pillion rescue:</b> from the saddle, use a downed player to pull them up behind you ({@link #rescue}).</li>
 * <li><b>Courier pay:</b> deliveries and the courier's job pay a Rider more ({@link #COURIER_PAY}); races at a
 * settlement's stable pay them a bigger prize ({@code Races}).</li>
 * </ul>
 * A downed horse (Jaymo's Powers' Wounds) is brought round by a Rider twice as fast and with no treat: Powers reads the
 * scoreboard tag {@code ClassPerks.RIDER_TAG}. Tuning numbers live at the top.
 */
public final class Riders {
	/** A Rider's taming roll counts this share of the horse's most temper on top of what it has. */
	public static final float TAME_HEAD_START = 0.5F;
	/** The trust a horse a Rider tamed starts at, and a Rider's first horse. */
	public static final float TAME_TRUST = 50.0F, STARTER_TRUST = 60.0F;
	/** A lance or spear from the saddle at full pace hits this share harder; under this share of top pace, no bonus. */
	public static final double LANCE_CHARGE = 0.8, LANCE_MIN_PACE = 0.35;
	/** How much faster a Rider's horse builds its charge, and how much harder it runs things down. */
	public static final float CHARGE_FILL = 1.5F, RUN_DOWN = 1.25F;
	/** The vault: how far your horse may be (blocks), how fast you must be rising, how long till the next. */
	static final double VAULT_REACH = 2.6, VAULT_RISE = 0.08;
	static final int VAULT_COOLDOWN = 20;
	/** A Rider's whistle: how far it carries, and how fast the horse comes. */
	public static final double WHISTLE_RANGE = 256.0, WHISTLE_SPEED = 2.6;
	/** What deliveries and the courier's job pay a Rider, as a share of what they'd pay. */
	public static final double COURIER_PAY = 1.5;
	/** The scoreboard tag on a player who's had their first horse (so a reborn Rider doesn't get another). */
	static final String STARTER_TAG = "jaymoskingdoms.rider_horse";

	/** Rider → the game time they may vault again. */
	private static final Map<UUID, Long> VAULTED = new HashMap<>();

	private Riders() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(Riders::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> VAULTED.clear());
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && rescue(sp, entity)) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	public static boolean is(@Nullable Entity e) {
		return e instanceof Player p && Classes.is(p, PlayerClass.RIDER);
	}

	/**
	 * Whether {@code horse} does what {@code player} asks of it that takes {@code need} trust (an order from the wheel, a
	 * call from the roster, the whistle): always for a Rider, otherwise once it trusts them that much. Ownership is the
	 * caller's check.
	 */
	public static boolean heeds(AbstractHorse horse, Player player, float need) {
		return is(player) || Bonding.trust(horse, player) >= need;
	}

	/**
	 * (From {@code MobSaddleMixin}, on both sides) a tamed horse, camel or llama with a Rider on its back goes as if
	 * saddled: they steer it, it jumps, it takes a second rider.
	 */
	public static boolean bareback(Mob mob) {
		return mob instanceof AbstractHorse h && (h.isTamed() || h instanceof Camel) && !h.isBaby() && is(h.getFirstPassenger());
	}

	/** (From {@code RunAroundLikeCrazyGoalMixin}) the temper a wild horse's taming roll counts: more under a Rider. */
	public static int temper(AbstractHorse horse, int temper) {
		return is(horse.getFirstPassenger()) ? temper + Math.round(horse.getMaxTemper() * TAME_HEAD_START) : temper;
	}

	/** The trust a horse {@code player} just tamed starts at. */
	static float tameTrust(Player player) {
		return is(player) ? TAME_TRUST : Bonding.TAME_TRUST;
	}

	/** (From {@code Classes#set}) a new Rider's first horse, saddled and at their side (once per player). */
	public static void starter(ServerPlayer player) {
		if (player.entityTags().contains(STARTER_TAG)) {
			return;
		}
		ServerLevel level = player.level();
		Breed breed = switch (level.getRandom().nextInt(3)) {
			case 0 -> Breed.COURSER;
			case 1 -> Breed.DESTRIER;
			default -> Breed.PALFREY;
		};
		AbstractHorse horse = Equestrian.spawn(level, breed, player.blockPosition());
		if (horse == null) {
			return;
		}
		player.addTag(STARTER_TAG);
		horse.tameWithName(player);
		horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
		Bond b = Bonding.bond(horse);
		horse.setAttached(Bonding.BOND, b.withTrust(player.getUUID(), Math.max(STARTER_TRUST, b.trustOf(player.getUUID()))));
		horse.setPersistenceRequired();
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rider.starter", Component.translatable(breed.key())));
	}

	/** (From {@code ClassPerks#dealt}) a Rider's melee blow: a lance or spear from a moving mount hits harder the faster it goes. */
	public static float lance(ServerPlayer rider, LivingEntity target, ItemStack held, float amount) {
		if (!(rider.getVehicle() instanceof LivingEntity mount) || mount.getControllingPassenger() != rider || !lance(held)) {
			return amount;
		}
		double top = mount instanceof AbstractHorse h && Equestrian.covered(h) ? Bonding.topSpeed(h, Equestrian.equine(h))
			: mount.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double pace = Mth.clamp(mount.getDeltaMovement().horizontalDistance() / Math.max(0.05, top), 0.0, 1.0);
		if (pace < LANCE_MIN_PACE) {
			return amount;
		}
		if (rider.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
			level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
		}
		Vec3 ahead = Vec3.directionFromRotation(0.0F, mount.getYRot());
		target.push(ahead.x * 0.6 * pace, 0.15 * pace, ahead.z * 0.6 * pace);
		return amount * (float)(1.0 + LANCE_CHARGE * pace);
	}

	static boolean lance(ItemStack held) {
		if (held.isEmpty()) {
			return false;
		}
		if (held.is(ItemTags.SPEARS) || held.is(Items.TRIDENT)) {
			return true;
		}
		String path = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();
		return path.contains("lance") || path.contains("spear") || path.contains("pike");
	}

	/** How much faster {@code rider}'s horse builds its gallop charge. */
	static float chargeFill(ServerPlayer rider) {
		return is(rider) ? CHARGE_FILL : 1.0F;
	}

	/** How much harder {@code rider}'s horse runs things down. */
	static float runDown(ServerPlayer rider) {
		return is(rider) ? RUN_DOWN : 1.0F;
	}

	/** How far {@code player}'s whistle carries. */
	static double whistleRange(Player player) {
		return is(player) ? WHISTLE_RANGE : Bonding.WHISTLE_RANGE;
	}

	/** How fast a horse comes to {@code player}'s whistle. */
	static double whistleSpeed(Player player) {
		return is(player) ? WHISTLE_SPEED : Bonding.WHISTLE_SPEED;
	}

	/** What a delivery or a courier's wage of {@code pay} comes to for {@code player}. */
	public static long courierPay(Player player, long pay) {
		return is(player) ? Math.round(pay * COURIER_PAY) : pay;
	}

	// ---- the vault and the rescue --------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isPassenger() || !player.isSprinting() || player.onGround() || !is(player) || player.isSpectator()) {
				continue;
			}
			if (player.getKnownMovement().y < VAULT_RISE) {
				continue;
			}
			Long until = VAULTED.get(player.getUUID());
			if (until != null && until > now) {
				continue;
			}
			vault(level, player, now);
		}
		if (now % 1200 == 0) {
			VAULTED.values().removeIf(t -> t < now);
		}
	}

	/** A Rider jumping at a sprint beside their own horse vaults into its saddle. */
	private static void vault(ServerLevel level, ServerPlayer player, long now) {
		AbstractHorse best = null;
		double bestDist = VAULT_REACH * VAULT_REACH;
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(VAULT_REACH, 1.5, VAULT_REACH),
			h -> Equestrian.covered(h) && h.isAlive() && h.isTamed() && !h.isBaby() && !h.isVehicle() && Bonding.owner(h, player)
				&& !com.jaymoskingdoms.Downed.isDowned(h) && !com.jaymoskingdoms.Stables.boarded(h))) {
			double d = horse.distanceToSqr(player);
			if (d < bestDist) {
				bestDist = d;
				best = horse;
			}
		}
		if (best == null) {
			return;
		}
		VAULTED.put(player.getUUID(), now + VAULT_COOLDOWN);
		if (best.isLeashed()) {
			best.dropLeash();
		}
		if (player.startRiding(best)) {
			level.playSound(null, best.blockPosition(), SoundEvents.HORSE_SADDLE.value(), SoundSource.NEUTRAL, 0.8F, 1.2F);
			level.sendParticles(ParticleTypes.CLOUD, best.getX(), best.getY() + 0.3, best.getZ(), 6, 0.4, 0.1, 0.4, 0.02);
		}
	}

	/**
	 * A Rider in the saddle using a downed player pulls them up behind: the second seat. Returns whether it did (and so
	 * nothing else happens with the click).
	 */
	private static boolean rescue(ServerPlayer rider, Entity target) {
		if (!(target instanceof Player body) || body == rider || !is(rider) || !com.jaymoskingdoms.Downed.isDowned(body) || body.isPassenger()
			|| !(rider.getVehicle() instanceof AbstractHorse horse) || horse.getControllingPassenger() != rider || horse.getPassengers().size() != 1) {
			return false;
		}
		if (!body.startRiding(horse, true, true)) {
			return false;
		}
		ServerLevel level = rider.level();
		level.playSound(null, horse.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.9F, 1.0F);
		rider.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rider.rescued", body.getDisplayName()));
		return true;
	}
}
