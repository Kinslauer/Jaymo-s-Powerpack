package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What a wake costs (Mortal Burden's idea, CC0, by osinnanna: a burden each death adds and time takes away). Each time one
 * left for dead wakes ({@link Mortality}), the body has paid for it: {@link #TOLL_PER_WAKE} of most health is gone (up to
 * {@link #MAX_TOLL}), and a weakness and a heaviness in the hands linger a while after they come round. Unlike a scar
 * ({@link Scars}) the health comes back: always, slowly, and faster for one who is cared for: wounds seen to (nothing
 * bleeding, every break set), well fed (Kitchen's Nourishment or Comfort), and a night slept in a bed. A new life
 * ({@link Mortality#endLife}) or a new body ({@link Mortality#shedGifts}) starts without it. Nothing is said in chat:
 * a tip card once they're up again tells them what it is.
 */
public final class DeathToll {
	/** Most health (points; two to a heart) each wake takes, and the most the toll holds. */
	static final int TOLL_PER_WAKE = 4, MAX_TOLL = 8;
	/** How often the toll is looked at (ticks). */
	static final int CHECK_TICKS = 100;
	/** Ticks of mending that give one point of health back, at the plain rate (each kind of care adds the plain rate again). */
	static final int MEND_TICKS = 20 * 60 * 10;
	/** Points a night slept in a bed gives back. */
	static final int NIGHT_MENDS = 2;
	/** How long the weakness and the heavy hands linger after a wake (ticks), and how much longer for each point of toll. */
	static final int LINGER_TICKS = 20 * 60 * 2, LINGER_PER_TOLL = 20 * 20;
	/** Kitchen's good meals (looked up by id, so nothing without Kitchen). */
	private static final Identifier NOURISHMENT = Identifier.fromNamespaceAndPath("jaymoskitchen", "nourishment"),
		COMFORT = Identifier.fromNamespaceAndPath("jaymoskitchen", "comfort");
	private static final Identifier MODIFIER = JaymosPowers.id("death_toll");

	/** On a player: the most health the toll holds back now (points). Saved, kept through death. Absent at none. */
	public static final AttachmentType<Integer> TOLL = AttachmentRegistry.create(JaymosPowers.id("death_toll"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** On a player: the mending done toward the next point back (ticks at the plain rate). Saved, kept through death. */
	public static final AttachmentType<Integer> MENDED = AttachmentRegistry.create(JaymosPowers.id("death_toll_mended"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Woken players not yet shown the tip card (it waits till they're up). */
	private static final Set<UUID> UNTOLD = new HashSet<>();

	private DeathToll() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DeathToll::tick);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player && player.level().isBrightOutside() && toll(player) > 0) {
				mend(player, NIGHT_MENDS);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> UNTOLD.clear());
	}

	public static int toll(ServerPlayer player) {
		return player.getAttachedOrElse(TOLL, 0);
	}

	/** {@code player} woke from being left for dead: the toll grows, and the weakness lingers. */
	static void woke(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		int toll = Math.min(MAX_TOLL, toll(player) + TOLL_PER_WAKE);
		player.setAttached(TOLL, toll);
		apply(player);
		int linger = LINGER_TICKS + LINGER_PER_TOLL * toll;
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, linger, 0, false, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, linger, 0, false, false, true));
		UNTOLD.add(player.getUUID());
	}

	/** The toll is gone (a new life, a new body). */
	static void clear(ServerPlayer player) {
		player.removeAttached(TOLL);
		player.removeAttached(MENDED);
		UNTOLD.remove(player.getUUID());
		apply(player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (toll(player) > 0 && player.isAlive() && !Downed.isDowned(player) && !Mortality.lying(player)) {
				if (UNTOLD.remove(player.getUUID())) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.death_toll.hint"));
				}
				int mended = player.getAttachedOrElse(MENDED, 0) + CHECK_TICKS * (1 + care(player));
				if (mended >= MEND_TICKS) {
					player.setAttached(MENDED, mended - MEND_TICKS);
					mend(player, 1);
				} else {
					player.setAttached(MENDED, mended);
				}
			}
			apply(player);
		}
	}

	/** How many kinds of care {@code player} has now: wounds seen to, a good meal in them (Nourishment, Comfort). */
	static int care(ServerPlayer player) {
		int care = 0;
		if (Bleeding.bleeding(player) == 0 && Breaks.unset(player) == 0) {
			care++;
		}
		if (has(player, NOURISHMENT)) {
			care++;
		}
		if (has(player, COMFORT)) {
			care++;
		}
		return care;
	}

	private static boolean has(ServerPlayer player, Identifier effect) {
		Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.get(effect).map(h -> (Holder<MobEffect>)h).orElse(null);
		return holder != null && player.hasEffect(holder);
	}

	/** {@code points} of the toll come back. */
	private static void mend(ServerPlayer player, int points) {
		int toll = Math.max(0, toll(player) - points);
		if (toll == 0) {
			player.removeAttached(TOLL);
			player.removeAttached(MENDED);
		} else {
			player.setAttached(TOLL, toll);
		}
		apply(player);
	}

	/** Brings the held-back health in line (the modifier is transient: put back after a respawn or a rejoin). */
	static void apply(ServerPlayer player) {
		AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		int toll = toll(player);
		if (toll == 0) {
			health.removeModifier(MODIFIER);
			return;
		}
		AttributeModifier now = health.getModifier(MODIFIER);
		if (now == null || now.amount() != -toll) {
			health.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER, -toll, AttributeModifier.Operation.ADD_VALUE));
			if (player.getHealth() > player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
			}
		}
	}
}
