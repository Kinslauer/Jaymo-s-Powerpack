package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.73) Guards aren't killed where they stand: the blow that would kill one of a realm's guards or archers (its
 * {@link Folk}, or a villager in its service or in a guard's or archer's job) puts them down instead. Downed, they're
 * out of the fight (they go for no one): some drag themselves away from whoever felled them, the rest lie where they
 * dropped. A player can take the weapons out of their hands (right-click), but nothing else: no pockets to pick, no
 * corpse to search. Sneak + right-click picks one up over your shoulder; then right-click throws them, sneak +
 * right-click sets them down. Mobs, fire and falls (a throw's too) still hurt them, out of {@link #DOWNED_HEALTH},
 * and can kill them (Andy). Left alone, they get back up after {@link #RECOVER_TICKS} (Andy: "they
 * should get back up unless they are hit by a player, they can be hit repeatedly until they bleed out, up to 10
 * times"): once a player has hit them they won't, and they bleed out {@link #BLEED_OUT_TICKS} after the last hit, or
 * at the {@link #MAX_WOUNDS}th. Then they're gone, leaving nothing behind (their death counts then, for wars, quests
 * and crimes).
 * Drawn lying down by the client's {@code DownedPose}; their AI is held by {@code DownedMixin}.
 */
public final class Downed {
	/** A downed guard's health: what mobs, fire and falls have left to take. */
	static final float DOWNED_HEALTH = 6.0F;
	/** A throw: how hard (blocks a tick) along your look, and the lift on top. */
	static final double THROW_SPEED = 0.9, THROW_LIFT = 0.25;
	/** How long a downed guard no player has hit lies there before getting back up (ticks), and the health it gets up with (share of its most). */
	static final int RECOVER_TICKS = 1200;
	static final float RECOVER_HEALTH = 0.3F;
	/** How long after a player's last hit a wounded guard bleeds out (ticks), and the hit that kills it outright. */
	static final int BLEED_OUT_TICKS = 2400;
	static final int MAX_WOUNDS = 10;
	/** The share of downed guards who drag themselves along; the rest lie still. */
	static final float CRAWLER_CHANCE = 0.5F;
	/** A crawler's pace, as a share of its walking speed, and how far it gets from whoever felled it before it stops. */
	static final double CRAWL_SPEED = 0.3, CRAWL_FROM = 14.0;
	/** Kitchen's tag for a body that leaves no corpse (matched by name: Kitchen is optional). */
	static final String NO_CORPSE_TAG = "jaymoskitchen.no_corpse";

	/**
	 * A downed guard: the game time it went down, shifted up one bit, the low bit set for a crawler. Saved (it stays
	 * down through a reload) and synced to everyone who can see it (who draw it on the ground).
	 */
	public static final AttachmentType<Long> DOWNED = AttachmentRegistry.create(JaymosKingdoms.id("downed"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));

	/** A downed guard players have hit: when the last hit landed, shifted up 4 bits, with how many hits in the low bits. Saved. */
	public static final AttachmentType<Long> WOUNDS = AttachmentRegistry.create(JaymosKingdoms.id("downed_wounds"),
		builder -> builder.persistent(Codec.LONG));

	/** The blow that put each down: whose kill it is when it dies (not saved; after a reload it bleeds out unclaimed). */
	private static final Map<LivingEntity, DamageSource> FELLED_BY = new WeakHashMap<>();

	private Downed() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			if (isDowned(entity)) {
				// (a mob, fire, a fall, the void: it goes, but leaves nothing)
				finish(level, entity, source);
				return false;
			}
			if (!isGuard(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			goDown(level, entity, source);
			return false;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!isDowned(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(source.getEntity() instanceof Player)) {
				// (mobs, fire, falls: real harm, out of what it has left)
				return true;
			}
			// a player's blow (or arrow): a wound (they won't get up again), the tenth kills
			if (entity.level() instanceof ServerLevel level) {
				wound(level, entity, source);
			}
			return false;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(entity instanceof LivingEntity body) || !isDowned(body) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			// (taken here, so a downed villager's screen doesn't open, and neither hand gets at their pockets)
			if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND) {
				if (body.getVehicle() == player) {
					if (player.isShiftKeyDown()) {
						setDown(player, body);
					} else {
						toss(player, body);
					}
				} else if (player.isShiftKeyDown()) {
					pickUp(player, body);
				} else {
					disarm(player, body);
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** One of a realm's guards or archers: its folk, a villager in its service, or a villager in that job. */
	public static boolean isGuard(LivingEntity entity) {
		if (entity instanceof Worker w && (w.role() == Folk.GUARD || w.role() == Folk.ARCHER)) {
			return true;
		}
		if (entity instanceof Villager v && !v.isBaby()) {
			String job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
			return job.equals("guard") || job.equals("archer");
		}
		return false;
	}

	public static boolean isDowned(Entity entity) {
		return entity.hasAttached(DOWNED);
	}

	/** Whether this downed guard drags itself along (rather than lying still). */
	public static boolean crawls(Entity entity) {
		Long downed = entity.getAttached(DOWNED);
		return downed != null && (downed & 1L) != 0L;
	}

	/** The game time it went down. */
	public static long since(Entity entity) {
		Long downed = entity.getAttached(DOWNED);
		return downed == null ? 0L : downed >> 1;
	}

	private static void goDown(ServerLevel level, LivingEntity entity, DamageSource source) {
		boolean crawler = level.getRandom().nextFloat() < CRAWLER_CHANCE;
		entity.setHealth(Math.min(DOWNED_HEALTH, entity.getMaxHealth()));
		entity.setAttached(DOWNED, level.getGameTime() << 1 | (crawler ? 1L : 0L));
		FELLED_BY.put(entity, source);
		entity.stopUsingItem();
		if (entity.isSleeping()) {
			entity.stopSleeping();
		}
		entity.stopRiding();
		entity.ejectPassengers();
		entity.clearFire();
		if (entity instanceof Mob mob) {
			mob.setTarget(null);
			mob.setAggressive(false);
			mob.getNavigation().stop();
		}
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.NEUTRAL, 0.9F, 0.8F);
	}

	/**
	 * A downed mob's tick, in place of its AI: it bleeds out, and a crawler hauls itself away from whoever felled it
	 * (while they're near). Called by {@code DownedMixin} on the server.
	 */
	public static void step(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long wounds = mob.getAttached(WOUNDS);
		if (wounds == null && now - since(mob) >= RECOVER_TICKS) {
			getUp(level, mob);
			return;
		}
		if (wounds != null && now - (wounds >> 4) >= BLEED_OUT_TICKS) {
			DamageSource source = FELLED_BY.get(mob);
			finish(level, mob, source != null ? source : level.damageSources().generic());
			return;
		}
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.setJumping(false);
		mob.setXxa(0.0F);
		mob.setSpeed(0.0F);
		if (mob.getVehicle() instanceof Player carrier) {
			// over the shoulder, across it
			float across = carrier.getYRot() + 90.0F;
			mob.setYRot(across);
			mob.setYHeadRot(across);
			mob.yBodyRot = across;
			return;
		}
		DamageSource source = FELLED_BY.get(mob);
		Entity from = source == null ? null : source.getEntity();
		if (crawls(mob) && from != null && from.isAlive() && from.level() == level && mob.distanceToSqr(from) < CRAWL_FROM * CRAWL_FROM) {
			float yaw = (float) (Mth.atan2(mob.getZ() - from.getZ(), mob.getX() - from.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			mob.setYRot(yaw);
			mob.setYHeadRot(yaw);
			mob.yBodyRot = yaw;
			mob.setSpeed((float) (mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * CRAWL_SPEED));
		}
	}

	/** A player hit a downed guard: one more wound; the {@link #MAX_WOUNDS}th is its death. */
	private static void wound(ServerLevel level, LivingEntity entity, DamageSource source) {
		Long wounds = entity.getAttached(WOUNDS);
		int count = (wounds == null ? 0 : (int) (wounds & 0xF)) + 1;
		FELLED_BY.put(entity, source);
		if (count >= MAX_WOUNDS) {
			finish(level, entity, source);
			return;
		}
		entity.setAttached(WOUNDS, level.getGameTime() << 4 | count);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY() + 0.3, entity.getZ(), 3, 0.3, 0.1, 0.3, 0.1);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_HURT, SoundSource.NEUTRAL, 0.7F, 0.8F);
	}

	/** {@code player} heaves a downed guard up over their shoulder (one at a time). */
	private static void pickUp(Player player, LivingEntity body) {
		if (!player.getPassengers().isEmpty() || body.isPassenger()) {
			return;
		}
		body.ejectPassengers();
		if (body.startRiding(player, true, true)) {
			body.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.9F, 0.7F);
		}
	}

	/** Set down at your feet. */
	private static void setDown(Player player, LivingEntity body) {
		body.stopRiding();
		Vec3 ahead = player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(0.8);
		body.snapTo(player.getX() + ahead.x, player.getY(), player.getZ() + ahead.z, body.getYRot(), 0.0F);
		body.setDeltaMovement(Vec3.ZERO);
		body.resetFallDistance();
	}

	/** Thrown where you're looking: it lands as anything does (falls hurt). */
	private static void toss(Player player, LivingEntity body) {
		body.stopRiding();
		Vec3 look = player.getLookAngle();
		body.snapTo(player.getX() + look.x * 0.6, player.getEyeY() - 0.4, player.getZ() + look.z * 0.6, body.getYRot(), 0.0F);
		body.resetFallDistance();
		body.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, THROW_LIFT, 0.0));
		body.needsSync = true;
		body.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.6F);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	/** Where a carried guard sits on its carrier: over the shoulder, a little ahead. */
	public static Vec3 carriedAt(LivingEntity carrier) {
		Vec3 ahead = Vec3.directionFromRotation(0.0F, carrier.getYRot()).scale(0.25);
		return carrier.position().add(ahead.x, carrier.getBbHeight() * 0.72, ahead.z);
	}

	/** Left alone long enough: it gets back on its feet, hurt, and back to its post (its AI takes up again). */
	private static void getUp(ServerLevel level, Mob mob) {
		FELLED_BY.remove(mob);
		mob.stopRiding();
		mob.removeAttached(DOWNED);
		mob.removeAttached(WOUNDS);
		mob.setHealth(Math.max(1.0F, mob.getMaxHealth() * RECOVER_HEALTH));
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.NEUTRAL, 0.8F, 0.9F);
	}

	/** Its death, at last: no corpse, no loot (see {@code DownedMixin}); the body is simply gone. */
	private static void finish(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (entity.isRemoved() || entity.isDeadOrDying()) {
			return;
		}
		FELLED_BY.remove(entity);
		entity.stopRiding();
		entity.addTag(NO_CORPSE_TAG);
		entity.setHealth(0.0F);
		entity.die(source);
		level.broadcastEntityEvent(entity, (byte) 60);
		entity.remove(Entity.RemovalReason.KILLED);
	}

	/** {@code player} takes the weapons out of a downed guard's hands (and nothing else). */
	private static void disarm(Player player, LivingEntity body) {
		boolean took = false;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack held = body.getItemBySlot(slot);
			if (held.isEmpty()) {
				continue;
			}
			body.setItemSlot(slot, ItemStack.EMPTY);
			if (!player.getInventory().add(held) && !held.isEmpty()) {
				player.drop(held, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			took = true;
		}
		if (took) {
			body.level().playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.downed.disarmed", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.downed.nothing").withStyle(ChatFormatting.GRAY));
		}
	}
}
