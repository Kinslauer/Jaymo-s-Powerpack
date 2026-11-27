package com.jaymospowers.armory;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Couching the Armory lance, the same way Kingdoms' Couched Lance spear form does, without the form: right-click while
 * riding and the lance is couched for {@link #COUCH_TICKS} ticks. The first foe it meets is hit for damage that grows
 * with how fast the mount is going, thrown back, and knocked out of its saddle if it rides. Only from a mount the lance
 * reaches down from ({@link #horseback}): horses, pigs, goats, llamas and the like, never a camel, moa, elephant or
 * dragon. Mounted the right-click couches instead of raising the guard ({@code combat.Guard}); on foot it guards as before.
 */
public final class LanceCouch {
	/** How long the lance stays couched (ticks), how far ahead it reaches (blocks), the slowest mount that still drives it home (blocks/tick). */
	static final int COUCH_TICKS = 30;
	static final double COUCH_REACH = 3.2, COUCH_MIN_SPEED = 0.1;
	/** The wait before couching again (ticks). */
	static final int COOLDOWN = 60;
	/** Its damage (times your attack): a base and what each block/tick of the mount's speed adds (a gallop is about 0.3-0.4), up to a cap. */
	static final float COUCH_BASE = 0.6F, COUCH_PER_SPEED = 8.0F, COUCH_MAX = 4.0F;
	/** The shove a foe takes (per block/tick of mount speed) and the slowness of one thrown from the saddle (ticks, level). */
	static final double COUCH_SHOVE = 2.0;
	static final int UNSEAT_SLOW_TICKS = 40, UNSEAT_SLOW = 2;
	/** The tallest mount a lance still reaches down from (blocks; a horse is 1.6, a llama 1.87, a camel 2.375, a moa 2.35). */
	static final float MAX_MOUNT_HEIGHT = 2.0F;
	/** Mounts the lance never reaches down from whatever their size, by words in their entity ids. */
	private static final String[] TOO_HIGH = {"camel", "elephant", "mammoth", "dragon", "wyvern"};

	/** A couched lance: until when. */
	private record Couch(long until) {
	}

	private static final Map<UUID, Couch> COUCHES = new ConcurrentHashMap<>();
	/** True while a couched blow is being dealt, so the lance's swing bonus ({@code Armory.charge}) doesn't add to it. */
	static boolean striking;

	private LanceCouch() {
	}

	static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack held = player.getItemInHand(hand);
			if (hand != InteractionHand.MAIN_HAND || !Armory.isLance(held) || player.getCooldowns().isOnCooldown(held)) {
				return InteractionResult.PASS;
			}
			if (!horseback(player)) {
				// (on foot right-click is the guard, so only a rider up too high is told)
				if (!level.isClientSide() && tooHigh(player)) {
					player.sendOverlayMessage(Component.translatable("item.jaymospowers.lance.too_high").withStyle(ChatFormatting.GRAY));
				}
				return InteractionResult.PASS;
			}
			player.getCooldowns().addCooldown(held, COOLDOWN);
			if (level instanceof ServerLevel server) {
				COUCHES.put(player.getUUID(), new Couch(server.getGameTime() + COUCH_TICKS));
				server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.8F);
				player.sendOverlayMessage(Component.translatable("item.jaymospowers.lance.couched").withStyle(ChatFormatting.GOLD));
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(LanceCouch::tick);
	}

	/**
	 * Whether {@code rider} sits up front on a mount low enough for the lance to reach a foe on the ground: a horse,
	 * donkey, mule, pig, goat, llama and the like. Not on anything taller than {@link #MAX_MOUNT_HEIGHT} (camels, moas,
	 * war elephants, mammoths) or any dragon or wyvern.
	 */
	public static boolean horseback(@Nullable Entity rider) {
		if (rider == null || !(rider.getVehicle() instanceof LivingEntity mount) || mount.getBbHeight() > MAX_MOUNT_HEIGHT
			|| mount.getControllingPassenger() != rider && mount.getFirstPassenger() != rider) {
			return false;
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getPath();
		for (String word : TOO_HIGH) {
			if (id.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/** Whether {@code rider} is on a mount the lance can't reach down from (too tall, or a dragon). */
	static boolean tooHigh(Entity rider) {
		return rider.getVehicle() instanceof LivingEntity && !horseback(rider) && rider.getVehicle().getFirstPassenger() == rider;
	}

	/** How hard a lance couched on a mount going {@code speed} blocks/tick lands (times the wielder's attack). */
	static float multiple(double speed) {
		return Mth.clamp(COUCH_BASE + (float) speed * COUCH_PER_SPEED, COUCH_BASE, COUCH_MAX);
	}

	/** How fast {@code mount} is going across the ground (blocks/tick). */
	static double speed(Entity mount) {
		return Math.max(mount.getKnownMovement().horizontalDistance(), mount.position().subtract(mount.xo, mount.yo, mount.zo).horizontalDistance());
	}

	private static void tick(MinecraftServer server) {
		if (COUCHES.isEmpty()) {
			return;
		}
		for (Map.Entry<UUID, Couch> entry : new ArrayList<>(COUCHES.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !(player.level() instanceof ServerLevel level) || !player.isAlive() || !horseback(player)
				|| !Armory.isLance(player.getMainHandItem()) || level.getGameTime() > entry.getValue().until()) {
				COUCHES.remove(entry.getKey());
				continue;
			}
			tickCouch(level, player);
		}
	}

	private static void tickCouch(ServerLevel level, ServerPlayer player) {
		Entity mount = player.getVehicle();
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : look.normalize();
		Vec3 tip = player.position().add(0.0, 1.3, 0.0).add(look.scale(2.0));
		if (level.getGameTime() % 2 == 0) {
			level.sendParticles(ParticleTypes.CRIT, tip.x, tip.y, tip.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		double speed = mount == null ? 0.0 : speed(mount);
		if (speed < COUCH_MIN_SPEED) {
			// (too slow to drive it home: it stays couched until it runs out)
			return;
		}
		AABB reach = player.getBoundingBox().expandTowards(look.scale(COUCH_REACH)).inflate(0.4, 0.5, 0.4);
		LivingEntity foe = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach, e -> e != player && e != mount && e.isAlive()
			&& e.isAttackable() && !e.isSpectator() && !player.isAlliedTo(e) && !e.isPassengerOfSameVehicle(player) && !e.hasPassenger(player)
			&& !(e instanceof OwnableEntity owned && owned.getOwner() == player))) {
			double d = e.distanceToSqr(player);
			if (d < best) {
				foe = e;
				best = d;
			}
		}
		if (foe == null) {
			return;
		}
		COUCHES.remove(player.getUUID());
		double attack = player.getAttribute(Attributes.ATTACK_DAMAGE) == null ? 2.0 : player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		foe.setInvulnerableTime(0);
		boolean landed;
		striking = true;
		try {
			landed = foe.hurtServer(level, level.damageSources().playerAttack(player), (float) attack * multiple(speed));
		} finally {
			striking = false;
		}
		if (landed) {
			player.getMainHandItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
		}
		unseat(level, foe);
		if (foe.isAlive()) {
			foe.setDeltaMovement(foe.getDeltaMovement().add(look.x * speed * COUCH_SHOVE, 0.25, look.z * speed * COUCH_SHOVE));
			foe.needsSync = true;
		}
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY(0.6), foe.getZ(), 14, 0.3, 0.4, 0.3, 0.3);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
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

	/** Whether {@code player} has a lance couched right now. */
	public static boolean couched(Player player) {
		return COUCHES.containsKey(player.getUUID());
	}
}
