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
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
 * <p>
 * (0.7.83) Andy: "villagers should try to help others that are downed, and they can all be downed now, unless
 * decapitated". Every villager (MCA's too, children too) and the settlements' own folk (not their rulers) go down
 * now, not only guards; a blow that takes the head off still kills outright. A townsperson who isn't a guard leaves
 * their body and belongings when they do die at last, as anyone would. And the folk about look after their own: a
 * grown villager near a downed one, not afraid or fighting, comes over, kneels and tends them, and after
 * {@link #TEND_TICKS} at their side has them back on their feet (a player's hit while they tend starts it over).
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
	/** (0.7.83) Help: how far a villager comes to tend a downed one, how often one is looked for, how close is at their side, how long tending takes (ticks). */
	static final double HELP_RANGE = 24.0, TEND_REACH = 1.8;
	static final int HELP_LOOK_TICKS = 20, TEND_TICKS = 200;
	static final float HELP_SPEED = 0.8F;
	/** (0.7.83) What a beheading is (matched by name: Jaymo's Powers deals it), and the tag a beheaded body carries. */
	static final String DECAPITATION = "jaymospowers:decapitation", HEADLESS_TAG = "jaymoskitchen.missing.head";
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

	/** (0.7.83) Who's tending each downed one, and how long they've been at it (ticks). Not saved. */
	private static final class Tending {
		final Villager helper;
		int ticks;

		Tending(Villager helper) {
			this.helper = helper;
		}
	}

	private static final Map<LivingEntity, Tending> TENDED = new WeakHashMap<>();
	/** The villagers tending someone just now. */
	private static final Map<Villager, LivingEntity> HELPING = new WeakHashMap<>();
	/** (0.7.83) Townsfolk being finished off (their death let through as it comes). */
	private static final java.util.Set<LivingEntity> FINISHING = java.util.Collections.newSetFromMap(new WeakHashMap<>());

	private Downed() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity.level() instanceof ServerLevel level)) {
				return true;
			}
			if (FINISHING.contains(entity)) {
				return true;
			}
			if (isDowned(entity)) {
				if (!isGuard(entity)) {
					// (0.7.83) a townsperson dies as anyone would, body and all
					forget(entity);
					return true;
				}
				// (a mob, fire, a fall, the void: it goes, but leaves nothing)
				finish(level, entity, source);
				return false;
			}
			if (!downable(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || beheaded(entity, source)) {
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
				Tending tending = TENDED.get(entity);
				if (tending != null) {
					// (whoever was tending them starts over)
					tending.ticks = 0;
				}
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

	/**
	 * (0.7.83) Whether the blow that would kill {@code entity} downs it instead: any villager, a settlement's own folk
	 * (its guards and workers, not its ruler, nor orcs, raiders or barbarians).
	 */
	public static boolean downable(LivingEntity entity) {
		if (isGuard(entity) || entity instanceof Villager) {
			return true;
		}
		if (entity instanceof Folk folk && !Orcs.isOrc(folk) && entity.level() instanceof ServerLevel level) {
			Folk.Role role = Folk.role(folk.role());
			if (role != null && role.leader() || Assault.attacker(folk)) {
				return false;
			}
			Faction f = Factions.get(level.getServer(), folk.factionId());
			return f != null && f.type() != FactionType.BARBARIAN;
		}
		return false;
	}

	/** A beheading (or a body already without its head): no getting up from that. */
	private static boolean beheaded(LivingEntity entity, DamageSource source) {
		return entity.entityTags().contains(HEADLESS_TAG)
			|| source.typeHolder().unwrapKey().map(k -> k.identifier().toString().equals(DECAPITATION)).orElse(false);
	}

	/** (0.7.83) No longer down (dying as anyone would): its marks and its tending gone. */
	private static void forget(LivingEntity entity) {
		FELLED_BY.remove(entity);
		untend(entity);
		entity.removeAttached(DOWNED);
		entity.removeAttached(WOUNDS);
	}

	/** (0.7.83) {@code v} is tending someone downed just now. */
	public static boolean helping(LivingEntity v) {
		return v instanceof Villager villager && HELPING.containsKey(villager);
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
		if (tend(level, mob)) {
			getUp(level, mob);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + 0.8, mob.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
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

	/**
	 * (0.7.83) Someone comes to tend a downed {@code body}: the nearest grown villager free to (not downed, afraid,
	 * fighting, asleep or tending another) walks over, kneels and sees to them. True once they've been at it long enough
	 * to get them up.
	 */
	private static boolean tend(ServerLevel level, Mob body) {
		if (body.isPassenger() || body.isOnFire()) {
			untend(body);
			return false;
		}
		Tending tending = TENDED.get(body);
		if (tending != null && !canHelp(tending.helper, body)) {
			untend(body);
			tending = null;
		}
		if (tending == null) {
			if (body.tickCount % HELP_LOOK_TICKS != 0) {
				return false;
			}
			Villager helper = null;
			double best = HELP_RANGE * HELP_RANGE;
			for (Villager v : level.getEntitiesOfClass(Villager.class, body.getBoundingBox().inflate(HELP_RANGE), v -> canHelp(v, body) && !HELPING.containsKey(v))) {
				double d = v.distanceToSqr(body);
				if (d < best) {
					best = d;
					helper = v;
				}
			}
			if (helper == null) {
				return false;
			}
			tending = new Tending(helper);
			TENDED.put(body, tending);
			HELPING.put(helper, body);
			level.playSound(null, helper.getX(), helper.getY(), helper.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 1.0F, 1.2F);
		}
		Villager helper = tending.helper;
		if (helper.distanceToSqr(body) > TEND_REACH * TEND_REACH) {
			helper.setShiftKeyDown(false);
			if (body.tickCount % 10 == 0) {
				BehaviorUtils.setWalkAndLookTargetMemories(helper, body.blockPosition(), HELP_SPEED, 1);
			}
			return false;
		}
		// at their side: kneeling, hands busy
		helper.getNavigation().stop();
		helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		helper.getLookControl().setLookAt(body.getX(), body.getY() + 0.2, body.getZ());
		helper.setShiftKeyDown(true);
		if (++tending.ticks % 25 == 0) {
			helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
		}
		if (tending.ticks >= TEND_TICKS) {
			untend(body);
			return true;
		}
		return false;
	}

	private static boolean canHelp(Villager v, LivingEntity body) {
		return v.isAlive() && v != body && !v.isBaby() && !isDowned(v) && !v.isSleeping() && !v.isPassenger() && !v.isNoAi() && v.getTarget() == null
			&& !Frights.afraid(v.getUUID()) && !McaCompat.panicking(v) && v.level() == body.level() && v.distanceToSqr(body) < HELP_RANGE * HELP_RANGE * 1.5;
	}

	private static void untend(LivingEntity body) {
		Tending tending = TENDED.remove(body);
		if (tending != null) {
			HELPING.remove(tending.helper);
			tending.helper.setShiftKeyDown(false);
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
		untend(mob);
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
		entity.stopRiding();
		if (!isGuard(entity)) {
			// (0.7.83) anyone else dies as anyone would: their body and what they had are left behind
			forget(entity);
			FINISHING.add(entity);
			try {
				entity.setInvulnerableTime(0);
				entity.hurtServer(level, source, Float.MAX_VALUE);
			} finally {
				FINISHING.remove(entity);
			}
			return;
		}
		FELLED_BY.remove(entity);
		untend(entity);
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
