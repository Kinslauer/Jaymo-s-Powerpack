package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A cat that needs you (Reference Mods patch 7, Mo' Creatures' cat; applied to the game's own cats, so every tamed cat
 * has it and no cat model of ours is drawn). A cat you keep:
 * <ul>
 * <li>gets <b>hungry</b>: a day after its last fish ({@link #HUNGRY_AFTER}); a mouse it catches ({@link SmallLife}) is a meal;</li>
 * <li>wants a <b>bed</b> (any bed within {@link #BED_REACH}) and a <b>sand box</b> (sand under a roof, on a floor of its own,
 * within {@link #BOX_REACH}): its needs are shown by its voice, never by a message: a begging mew for food, a plaintive one for
 * a bed, a hiss for a box;</li>
 * <li>is judged every {@link #JUDGE_EVERY} ticks. Each need unmet adds a point of {@link #NEGLECT}; met, it eases off by
 * {@link #EASE}. From {@link #SULK_AT} the cat <b>sulks</b>: it sits, back turned, an angry cloud over it, and will not
 * come when called until you have seen to it. From {@link #LEAVE_AT} it <b>wanders off</b>: no longer yours, it goes its way
 * and is a stray again;</li>
 * <li><b>grows</b> from a kitten as the game has it, and a kitten whose needs are all met grows quicker ({@link #KITTEN_BOOST}
 * ticks off its youth each judgment): the stewardship is the reward;</li>
 * <li>rides on your <b>shoulder</b> (the game has no seat for it there: it sits where a rider sits on you): sneak and use an
 * empty hand on it; sneak and use an empty hand in the air to set it down. It comes down by itself when you swim, are hurt,
 * or are far from a floor.</li>
 * </ul>
 * Nobody is punished for leaving cats alone: an untamed cat has none of this.
 */
public final class CatKeeping {
	/** How often the keeper's cats are looked at (ticks), and how often each one is judged. */
	static final int LOOK_EVERY = 100;
	static final int JUDGE_EVERY = 400;
	/** A day without a fish and the cat is hungry (ticks). */
	static final long HUNGRY_AFTER = 24000L;
	/** How far from the cat a bed and a sand box are looked for (blocks). */
	static final int BED_REACH = 8, BOX_REACH = 10;
	/** Neglect: sulks from here, leaves from here; how much a fully met judgment eases it; how much a fish or a mouse does. */
	static final int SULK_AT = 90, LEAVE_AT = 300, EASE = 3, MEAL = 20;
	/** A kitten's youth each judgment takes off when all is well (ticks). */
	static final int KITTEN_BOOST = 1200;
	/** How far round a keeper their cats are looked for (blocks). */
	static final double KEEP_RANGE = 40.0;

	/** On a cat: when it last ate (game time). Saved. */
	static final AttachmentType<Long> FED_AT = AttachmentRegistry.create(JaymosKingdoms.id("cat_fed_at"), builder -> builder.persistent(Codec.LONG));
	/** On a cat: its neglect. Saved. */
	static final AttachmentType<Integer> NEGLECT = AttachmentRegistry.create(JaymosKingdoms.id("cat_neglect"), builder -> builder.persistent(Codec.INT));

	/** Cats that are sulking (so it is begun, and ended, once). */
	private static final Map<UUID, Boolean> SULKING = new HashMap<>();

	private CatKeeping() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CatKeeping::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SULKING.clear());
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || level.isClientSide() || !(entity instanceof Cat cat) || !cat.isTame() || !cat.isOwnedBy(player)) {
				return InteractionResult.PASS;
			}
			if (player.getItemInHand(hand).is(ItemTags.CAT_FOOD)) {
				fed((ServerLevel) level, cat);
				return InteractionResult.PASS;
			}
			if (player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty() && !cat.isPassenger() && !player.isPassenger()
				&& player.getPassengers().stream().noneMatch(e -> e instanceof Cat)) {
				cat.setOrderedToSit(false);
				cat.setInSittingPose(false);
				if (cat.startRiding(player, true, true)) {
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
				for (net.minecraft.world.entity.Entity rider : java.util.List.copyOf(player.getPassengers())) {
					if (rider instanceof Cat cat) {
						setDown(cat, player);
						return InteractionResult.SUCCESS;
					}
				}
			}
			return InteractionResult.PASS;
		});
	}

	/** A cat is fed (a fish, or a mouse of its own catching). */
	static void fed(ServerLevel level, Cat cat) {
		cat.setAttached(FED_AT, level.getGameTime());
		int n = cat.getAttachedOrElse(NEGLECT, 0);
		cat.setAttached(NEGLECT, Math.max(0, n - MEAL));
		unsulk(cat);
	}

	/** A cat has caught a mouse: a meal, and it purrs for it. */
	static void caught(ServerLevel level, Cat cat) {
		if (cat.isTame()) {
			fed(level, cat);
		}
		level.sendParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.8, cat.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		level.playSound(null, cat.blockPosition(), SoundEvents.OCELOT_AMBIENT, SoundSource.NEUTRAL, 0.6F, 0.7F);
	}

	private static void setDown(Cat cat, net.minecraft.world.entity.player.Player player) {
		cat.stopRiding();
		cat.teleportTo(player.getX(), player.getY(), player.getZ());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % LOOK_EVERY != 0) {
			return;
		}
		long now0 = server.getTickCount() / LOOK_EVERY;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
				continue;
			}
			// a cat on the shoulder comes down when its keeper swims, is hurt or has no floor
			for (net.minecraft.world.entity.Entity rider : java.util.List.copyOf(player.getPassengers())) {
				if (rider instanceof Cat cat && (player.isUnderWater() || player.isSwimming() || player.hurtTime > 0 || player.isFallFlying())) {
					setDown(cat, player);
				}
			}
			for (Cat cat : level.getEntitiesOfClass(Cat.class, player.getBoundingBox().inflate(KEEP_RANGE), c -> c.isTame() && c.isAlive() && c.isOwnedBy(player))) {
				if ((now0 + cat.getId()) % (JUDGE_EVERY / LOOK_EVERY) == 0) {
					judge(level, cat, player);
				}
			}
		}
	}

	/** One judgment: the cat's needs, and what comes of them. */
	private static void judge(ServerLevel level, Cat cat, ServerPlayer keeper) {
		long now = level.getGameTime();
		Long fed = cat.getAttached(FED_AT);
		if (fed == null) {
			cat.setAttached(FED_AT, now);
			fed = now;
		}
		boolean hungry = now - fed > HUNGRY_AFTER;
		boolean bed = near(level, cat.blockPosition(), BED_REACH, s -> s.is(BlockTags.BEDS), false);
		boolean box = near(level, cat.blockPosition(), BOX_REACH, s -> s.is(BlockTags.SAND), true);
		int missing = (hungry ? 1 : 0) + (bed ? 0 : 1) + (box ? 0 : 1);
		int neglect = cat.getAttachedOrElse(NEGLECT, 0);
		if (missing == 0) {
			neglect = Math.max(0, neglect - EASE);
			if (cat.isBaby() && cat.getAge() < 0) {
				cat.setAge(Math.min(0, cat.getAge() + KITTEN_BOOST));
				level.sendParticles(ParticleTypes.HEART, cat.getX(), cat.getY() + 0.7, cat.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
			}
		} else {
			neglect += missing;
			if (level.getRandom().nextFloat() < 0.4F && !cat.isOrderedToSit()) {
				// a begging mew for food, a low plaintive one for a bed, a hiss for a box
				level.playSound(null, cat.blockPosition(), hungry || !bed ? SoundEvents.OCELOT_AMBIENT : SoundEvents.OCELOT_HURT, SoundSource.NEUTRAL, 0.8F,
					hungry ? 1.5F : !bed ? 0.8F : 1.0F);
			}
		}
		cat.setAttached(NEGLECT, neglect);
		if (neglect >= LEAVE_AT) {
			wander(level, cat);
		} else if (neglect >= SULK_AT) {
			SULKING.put(cat.getUUID(), true);
			cat.setOrderedToSit(true);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, cat.getX(), cat.getY() + 0.7, cat.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
		} else if (neglect < SULK_AT / 2) {
			unsulk(cat);
		}
	}

	private static void unsulk(Cat cat) {
		if (SULKING.remove(cat.getUUID()) != null) {
			cat.setOrderedToSit(false);
		}
	}

	/** It has had enough: no longer yours, it goes off (as a stray again). */
	private static void wander(ServerLevel level, Cat cat) {
		SULKING.remove(cat.getUUID());
		cat.stopRiding();
		cat.setOrderedToSit(false);
		cat.setTame(false, false);
		cat.setOwnerReference(null);
		cat.removeAttached(NEGLECT);
		cat.removeAttached(FED_AT);
		level.sendParticles(ParticleTypes.SMOKE, cat.getX(), cat.getY() + 0.5, cat.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
		BlockPos far = cat.blockPosition().offset(level.getRandom().nextIntBetweenInclusive(-24, 24), 0, level.getRandom().nextIntBetweenInclusive(-24, 24));
		cat.getNavigation().moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5, 1.0);
	}

	/**
	 * Whether a block that {@code wanted} says is within {@code reach} of {@code at}; a sand box also has to be under a
	 * roof, open at the top, and stand on a floor that is not sand's own.
	 */
	private static boolean near(ServerLevel level, BlockPos at, int reach, java.util.function.Predicate<BlockState> wanted, boolean box) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-reach, -2, -reach), at.offset(reach, 2, reach))) {
			BlockState s = level.getBlockState(p);
			if (!wanted.test(s)) {
				continue;
			}
			if (!box) {
				return true;
			}
			BlockState below = level.getBlockState(p.below());
			if (!level.canSeeSky(p.above()) && level.getBlockState(p.above()).isAir() && !below.is(BlockTags.SAND) && !below.is(Blocks.SANDSTONE)
				&& !below.is(Blocks.RED_SANDSTONE) && !below.isAir()) {
				return true;
			}
		}
		return false;
	}
}
