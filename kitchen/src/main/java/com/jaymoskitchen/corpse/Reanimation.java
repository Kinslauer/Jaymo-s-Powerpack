package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.84) Andy: "zombies should have their own reanimation animation too". A body that gets back up (a zombie, a
 * skeleton, a player's body as a zombie) no longer just stands there: it rises from where it lay over {@link #TICKS},
 * held still while it does (no AI), and every client draws it ({@code client/corpse/ReanimatePose}): lying on its back,
 * the arms jerk up first, then it tips up stiff from the heels like a plank, in fits, and lurches as it finds its feet.
 * The start time is synced to everyone; a tag marks one held still, so a save taken mid-rise lets it go again on load.
 */
public final class Reanimation {
	/** How long the rise takes (ticks). */
	public static final int TICKS = 50;
	/** Game time the rise began. Synced to all, not saved. */
	public static final AttachmentType<Long> RISING = AttachmentRegistry.create(JaymosKitchen.id("reanimating"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));
	/** Marks a mob held still for its rise (saved with it, so a reload frees it). */
	static final String HELD_TAG = "jaymoskitchen.reanimating";

	private static final List<LivingEntity> RISING_NOW = new ArrayList<>();

	private Reanimation() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (RISING_NOW.isEmpty()) {
				return;
			}
			for (int i = RISING_NOW.size() - 1; i >= 0; i--) {
				LivingEntity living = RISING_NOW.get(i);
				Long since = living.getAttached(RISING);
				if (living.isRemoved() || !living.isAlive() || since == null || living.level().getGameTime() - since >= TICKS) {
					RISING_NOW.remove(i);
					release(living);
				}
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && living.entityTags().contains(HELD_TAG) && !RISING_NOW.contains(living)) {
				release(living);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (LivingEntity living : RISING_NOW) {
				release(living);
			}
			RISING_NOW.clear();
		});
	}

	/** {@code living}, just back in the world where its body lay: it rises (call after it's added). */
	public static void start(ServerLevel level, LivingEntity living) {
		living.setAttached(RISING, level.getGameTime());
		if (living instanceof Mob mob && !mob.isNoAi()) {
			mob.setNoAi(true);
			mob.addTag(HELD_TAG);
		}
		RISING_NOW.add(living);
		// earth stirred where it lay
		BlockState ground = level.getBlockState(living.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), living.getX(), living.getY() + 0.1, living.getZ(), 12,
				0.5, 0.05, 0.5, 0.05);
		}
	}

	private static void release(LivingEntity living) {
		if (living.hasAttached(RISING)) {
			living.removeAttached(RISING);
		}
		if (living instanceof Mob mob && mob.entityTags().contains(HELD_TAG)) {
			mob.removeTag(HELD_TAG);
			mob.setNoAi(false);
		}
	}
}
