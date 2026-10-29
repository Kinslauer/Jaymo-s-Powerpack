package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import com.mojang.serialization.Codec;

/**
 * (0.7.332) Sleeping prey. A vampire can sneak and use a mortal player asleep in a bed to drink from them. The first bite
 * doesn't wake them (it takes {@link VampirePlayers#DRINK_DAMAGE} without a blow, never to the last heart); a second
 * does. However they wake, they wake with Weakness ({@link #WEAK_TICKS}), two small marks on the neck that everyone
 * sees for a day ({@link #BITE_MARKS}) and a line on their action bar. Such a bite never turns them (that's what the
 * thrall bond is for: {@link BloodThralls}).
 */
public final class SleepingPrey {
	/** How long they wake weak (ticks); and how long the marks show (ticks, one day). */
	static final int WEAK_TICKS = 3600;
	static final long MARKS_TICKS = 24000L;
	/** A bite this long ago (ticks) still counts as the first, for the wake-up's effects. */
	static final long REMEMBER_TICKS = 24000L;
	/** The bite's damage never takes them below this (health). */
	static final float QUIET_FLOOR = 1.0F;
	/** How many "you slept badly" lines there are. */
	static final int WAKE_LINES = 3;

	/** The game time until which the neck shows two marks; kept, and synced to everyone who sees them. */
	public static final AttachmentType<Long> BITE_MARKS = AttachmentRegistry.create(JaymosKingdoms.id("bite_marks"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Sleepers who've been bitten and not yet woken, with the game time of the first bite (not kept). */
	private static final Map<UUID, Long> BITTEN = new HashMap<>();

	private SleepingPrey() {
	}

	static void register() {
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Long first = BITTEN.remove(player.getUUID());
				if (first != null && player.level().getGameTime() - first < REMEMBER_TICKS) {
					woke(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BITTEN.clear());
	}

	/** Whether a bite on {@code target} is a bite on sleeping prey. */
	static boolean prey(LivingEntity target) {
		return target instanceof ServerPlayer p && p.isSleeping() && Vampires.mortal(p);
	}

	/** Whether the first bite has been taken from this sleeper already. */
	static boolean bitten(ServerPlayer sleeper) {
		return BITTEN.containsKey(sleeper.getUUID());
	}

	/** A bite on a sleeper (the first is quiet; the second wakes them). */
	static void bite(ServerLevel level, ServerPlayer vampire, ServerPlayer sleeper) {
		boolean second = bitten(sleeper);
		if (second) {
			sleeper.stopSleeping(); // (woke(): their wake-up effects)
		} else {
			BITTEN.put(sleeper.getUUID(), level.getGameTime());
		}
		VampirePlayers.drink(level, vampire, sleeper, !second, false);
	}

	/** They woke, bitten: weak, marked, and a bad night's sleep. */
	private static void woke(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0));
		player.setAttached(BITE_MARKS, player.level().getGameTime() + MARKS_TICKS);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.sleeping_prey.woke." + (1 + player.getRandom().nextInt(WAKE_LINES)))
			.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	/** Whether {@code entity} (on either side) shows the marks now. */
	public static boolean marked(net.minecraft.world.entity.Entity entity) {
		Long until = entity.getAttached(BITE_MARKS);
		return until != null && entity.level().getGameTime() < until;
	}
}
