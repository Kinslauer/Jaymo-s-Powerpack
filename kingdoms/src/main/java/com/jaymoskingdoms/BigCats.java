package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Liger;
import com.jaymoskingdoms.monsters.MocWild;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Big cats you can raise (Reference Mods patch 7, Mo' Creatures' lions, tigers and liger), on the wild lions and tigers the
 * pack already has (Naturalist's, and Cube Animals' tiger: found by their ids, so none of them is a dependency, and the
 * wild ones stay wild); the cubs and their mothers are theirs, what is added is how a cub comes to be yours:
 * <ul>
 * <li><b>An orphan.</b> When a grown lion or tiger dies, every cub of its kind within {@link #MOTHER_RANGE} blocks is
 * left without a mother ({@link #ORPHAN}).</li>
 * <li><b>Raising it.</b> Feed an orphan meat, {@link #FEEDINGS} times, at least {@link #FEED_GAP} ticks apart and it is yours,
 * and follows you ({@link #KEEPER}). A cub with a living mother is never taken: she would not allow it, and the cub does not
 * come.</li>
 * <li><b>Grown, it guards you.</b> A kept cat that is grown fights whatever hurts you, and what you strike; never you, and
 * never another of yours. It follows you (and comes to you from far off), and a cub follows too.</li>
 * <li><b>The cross.</b> A kept lion and a kept tiger of the same keeper, both grown and both fed meat to put them in the mood
 * ({@link Animal#setInLove}), side by side for a moment, may have a cub that is neither: a liger or a tigon
 * ({@link Liger}), at a one in {@link #CROSS_ODDS} chance, born tame to the keeper. Rare and costly: two cubs raised through
 * their youth and then an apart-of-nature union that mostly comes to nothing.</li>
 * </ul>
 * Stewardship: the cats are a reward of care. Feedback: a kept pair must be fed to cross; guarding costs the keeper
 * the food the cats want. Sympathy: a mother is the cub's reason to stay wild; with her gone, the cub bonds to whoever feeds it.
 */
public final class BigCats {
	/** The lions and tigers that count (the tag the other mods' ids are in). */
	static final Set<String> LIONS = Set.of("naturalist:lion"), TIGERS = Set.of("naturalist:tiger", "cubeanimals:tiger");
	/** How near a mother's death a cub is orphaned (blocks). */
	static final double MOTHER_RANGE = 28.0;
	/** Meals before a cub is yours, the least time between two (ticks). */
	static final int FEEDINGS = 4, FEED_GAP = 200;
	/** How often a keeper's cats are looked at (ticks); how far (blocks); how close a keeper's cat comes before it walks to them; how far before it is brought. */
	static final int LOOK_EVERY = 20;
	static final double RANGE = 48.0, COME_FROM = 9.0, BRING_FROM = 40.0;
	/** Their quarrel: how recent (ticks) a keeper's fight is to be taken up. */
	static final int QUARREL = 100;
	/** Two cats this near may cross; the odds against, and how long both then rest (ticks). */
	static final double CROSS_REACH = 3.5;
	static final int CROSS_ODDS = 3, REST_TICKS = 6000;

	/** On a cub: its mother is dead. Saved. */
	static final AttachmentType<Boolean> ORPHAN = AttachmentRegistry.create(JaymosKingdoms.id("big_cat_orphan"), builder -> builder.persistent(Codec.BOOL));
	/** On a cub: the meals it has had from one hand. Saved. */
	static final AttachmentType<Integer> MEALS = AttachmentRegistry.create(JaymosKingdoms.id("big_cat_meals"), builder -> builder.persistent(Codec.INT));
	/** On a cat: whose it is (a player's id). Saved. */
	public static final AttachmentType<String> KEEPER = AttachmentRegistry.create(JaymosKingdoms.id("big_cat_keeper"), builder -> builder.persistent(Codec.STRING));
	/** On a cat: the game time of its last meal from a hand. Saved. */
	static final AttachmentType<Long> LAST_MEAL = AttachmentRegistry.create(JaymosKingdoms.id("big_cat_last_meal"), builder -> builder.persistent(Codec.LONG));

	private BigCats() {
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (dead.level() instanceof ServerLevel level && kind(dead) != null && dead instanceof AgeableMob mother && !mother.isBaby()) {
				for (AgeableMob cub : level.getEntitiesOfClass(AgeableMob.class, dead.getBoundingBox().inflate(MOTHER_RANGE), c -> c.isBaby() && c.isAlive()
					&& kind(c) != null && kind(c).equals(kind(dead)))) {
					cub.setAttached(ORPHAN, true);
				}
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(player instanceof ServerPlayer keeper) || kind(entity) == null || !(entity instanceof AgeableMob cat)
				|| !player.getItemInHand(hand).is(ItemTags.MEAT)) {
				return InteractionResult.PASS;
			}
			ServerLevel server = (ServerLevel) level;
			if (cat.isBaby() && Boolean.TRUE.equals(cat.getAttached(ORPHAN)) && keeperOf(cat) == null) {
				return feedCub(server, keeper, cat);
			}
			if (!cat.isBaby() && keeper.getUUID().equals(keeperOf(cat)) && cat instanceof Animal animal && animal.getHealth() >= animal.getMaxHealth()
				&& animal.canFallInLove() && !animal.isInLove()) {
				// a kept, grown cat that is well fed is put in the mood: the cross
				player.getItemInHand(hand).consume(1, player);
				animal.setInLove(keeper);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(BigCats::tick);
	}

	/** "lion" or "tiger" for those that are, else null. */
	static @Nullable String kind(Entity e) {
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
		return LIONS.contains(id) ? "lion" : TIGERS.contains(id) ? "tiger" : null;
	}

	/** Whose a kept cat is, or null. */
	public static @Nullable UUID keeperOf(Entity cat) {
		String s = cat.getAttached(KEEPER);
		if (s == null) {
			return null;
		}
		try {
			return UUID.fromString(s);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static InteractionResult feedCub(ServerLevel level, ServerPlayer keeper, AgeableMob cub) {
		long now = level.getGameTime();
		Long last = cub.getAttached(LAST_MEAL);
		if (last != null && now - last < FEED_GAP) {
			return InteractionResult.CONSUME;
		}
		keeper.getMainHandItem().consume(1, keeper);
		cub.setAttached(LAST_MEAL, now);
		int meals = cub.getAttachedOrElse(MEALS, 0) + 1;
		cub.setAttached(MEALS, meals);
		cub.setPersistenceRequired();
		level.playSound(null, cub.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.6F, 1.2F);
		level.sendParticles(ParticleTypes.HEART, cub.getX(), cub.getY() + 0.6, cub.getZ(), meals >= FEEDINGS ? 7 : 2, 0.3, 0.3, 0.3, 0.0);
		if (meals >= FEEDINGS) {
			cub.setAttached(KEEPER, keeper.getUUID().toString());
			cub.removeAttached(ORPHAN);
		}
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
				continue;
			}
			UUID id = player.getUUID();
			List<Mob> kept = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(RANGE), m -> m.isAlive() && kind(m) != null && id.equals(keeperOf(m)));
			for (Mob cat : kept) {
				guard(level, player, cat);
			}
			cross(level, player, kept);
		}
	}

	/** A kept cat follows, and a grown one takes up its keeper's quarrels. */
	private static void guard(ServerLevel level, ServerPlayer keeper, Mob cat) {
		if (cat.getTarget() == keeper || cat.getTarget() instanceof Player || cat.getTarget() != null && id(cat.getTarget()) != null && keeper.getUUID().equals(keeperOf(cat.getTarget()))) {
			cat.setTarget(null);
		}
		double d = cat.distanceTo(keeper);
		if (d > BRING_FROM) {
			cat.teleportTo(keeper.getX(), keeper.getY(), keeper.getZ());
			cat.getNavigation().stop();
		} else if (d > COME_FROM && cat.getTarget() == null) {
			cat.getNavigation().moveTo(keeper, 1.2);
		}
		if (cat instanceof AgeableMob grown && grown.isBaby()) {
			return;
		}
		LivingEntity attacker = keeper.getLastHurtByMob();
		if (attacker != null && attacker.isAlive() && keeper.tickCount - keeper.getLastHurtByMobTimestamp() < QUARREL && quarrel(keeper, cat, attacker)) {
			cat.setTarget(attacker);
			return;
		}
		LivingEntity struck = keeper.getLastHurtMob();
		if (struck != null && struck.isAlive() && keeper.tickCount - keeper.getLastHurtMobTimestamp() < QUARREL && quarrel(keeper, cat, struck)) {
			cat.setTarget(struck);
		}
	}

	private static @Nullable UUID id(LivingEntity e) {
		return keeperOf(e);
	}

	/** Whether {@code foe} is a foe the cat takes up: not its keeper, not another kept cat or pet of the keeper's. */
	private static boolean quarrel(ServerPlayer keeper, Mob cat, LivingEntity foe) {
		if (foe == cat || foe == keeper || foe instanceof Player || keeper.getUUID().equals(keeperOf(foe))) {
			return false;
		}
		return !(foe instanceof TamableAnimal pet && pet.isOwnedBy(keeper));
	}

	/** A kept lion and a kept tiger of one keeper, both in the mood and near, may have a cub that is neither. */
	private static void cross(ServerLevel level, ServerPlayer keeper, List<Mob> kept) {
		for (Mob a : kept) {
			if (!(a instanceof Animal lion) || !"lion".equals(kind(a)) || lion.isBaby() || !lion.isInLove()) {
				continue;
			}
			for (Mob b : kept) {
				if (b instanceof Animal tiger && "tiger".equals(kind(b)) && !tiger.isBaby() && tiger.isInLove() && lion.distanceTo(tiger) <= CROSS_REACH) {
					lion.resetLove();
					tiger.resetLove();
					lion.setAge(REST_TICKS);
					tiger.setAge(REST_TICKS);
					level.sendParticles(ParticleTypes.HEART, lion.getX(), lion.getY() + 0.9, lion.getZ(), 6, 0.5, 0.4, 0.5, 0.0);
					if (level.getRandom().nextInt(CROSS_ODDS) == 0) {
						Liger cub = MocWild.LIGER.create(level, EntitySpawnReason.BREEDING);
						if (cub != null) {
							cub.snapTo(lion.getX(), lion.getY(), lion.getZ(), level.getRandom().nextFloat() * 360.0F, 0.0F);
							cub.setBaby(true);
							cub.tame(keeper);
							cub.setCoat(level.getRandom().nextInt(2));
							level.addFreshEntity(cub);
						}
					}
					return;
				}
			}
		}
	}
}
