package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * (0.15.82) Up again, but wounded. Andy (2026-10-22): "when you go down you should get back up wounded". A player who
 * gets up from the downed state (brought round, Second Wind, smelling salts, the symbiote's rescue: everything through
 * {@link Downed#getUp} and {@link Downed#rouse}) is <b>battered</b> ({@link #UNTIL}) for {@link #TICKS}:
 * <ul>
 * <li>nothing heals them past {@link #HEALTH_CAP} of their health (food, potions, powers: {@code DownedHealMixin}); a
 * dressing still works its own part, and whatever got them up with more keeps it;</li>
 * <li>slower ({@link #SPEED}) and weaker ({@link #DAMAGE}), unless the pain is dulled ({@link Pain#numb});</li>
 * <li>a night's sleep in a bed ends it, and so does being healed whole ({@link Wounds#healAll}) or dying.</li>
 * </ul>
 * Going down again while battered starts it afresh when they get up. A knock-out from standing (the poppy) is no
 * downing: they come round unbattered. (0.15.82, Andy: "yes") Everyone who goes down gets up battered: villagers,
 * folk, every humanoid, tamed animals; a mob sleeps it off once it's seen asleep (a villager's night in bed).
 */
public final class Battered {
	/** How long it lasts (ticks; half a Minecraft day). */
	public static final int TICKS = 12000;
	/** Nothing heals a battered one past this share of their most health. */
	public static final float HEALTH_CAP = 0.6F;
	/** Speed and attack damage lost (shares) while the pain isn't dulled. */
	public static final double SPEED = -0.15, DAMAGE = -0.2;
	/** How often the effects are refreshed (ticks). */
	static final int REFRESH_TICKS = 20;

	/** Battered until (game time). Saved, synced to its owner, not kept through death. */
	public static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(JaymosPowers.id("wounds_battered"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.targetOnly()));

	private static final Identifier SPEED_ID = JaymosPowers.id("wounds_battered_speed");
	private static final Identifier DAMAGE_ID = JaymosPowers.id("wounds_battered_damage");

	/** Battered mobs (players are found through the player list). Not saved: a reload finds them again. */
	private static final Set<LivingEntity> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

	private Battered() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Battered::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof ServerPlayer) && entity instanceof LivingEntity living && living.hasAttached(UNTIL)) {
				TRACKED.add(living);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				TRACKED.remove(living);
			}
		});
	}

	/** Whether {@code entity} is battered now. */
	public static boolean is(LivingEntity entity) {
		Long until = entity.getAttached(UNTIL);
		return until != null && entity.level().getGameTime() < until;
	}

	/** How much of {@code amount} healing {@code entity} may take (all of it unless battered). */
	public static float allowed(LivingEntity entity, float amount) {
		if (amount <= 0.0F || !is(entity)) {
			return amount;
		}
		return Math.max(0.0F, Math.min(amount, entity.getMaxHealth() * HEALTH_CAP - entity.getHealth()));
	}

	/** Just got up from being downed: battered for {@link #TICKS}. */
	static void gotUp(LivingEntity entity) {
		if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()
			|| entity instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())) {
			return;
		}
		entity.setAttached(UNTIL, level.getGameTime() + TICKS);
		refresh(entity);
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.battered").withStyle(ChatFormatting.GOLD));
		} else {
			TRACKED.add(entity);
		}
	}

	/** No longer battered: the mark and its effects gone. */
	static void clear(LivingEntity entity) {
		entity.removeAttached(UNTIL);
		modifier(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0);
		modifier(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, 0.0);
	}

	private static void refresh(LivingEntity entity) {
		boolean on = !Pain.numb(entity);
		modifier(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, on ? SPEED : 0.0);
		modifier(entity, Attributes.ATTACK_DAMAGE, DAMAGE_ID, on ? DAMAGE : 0.0);
	}

	private static void tick(MinecraftServer server) {
		boolean refresh = server.getTickCount() % REFRESH_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.hasAttached(UNTIL)) {
				continue;
			}
			if (!is(player) || player.isCreative() || player.isSpectator()) {
				clear(player);
				if (player.isAlive() && !player.isCreative() && !player.isSpectator()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.battered_over").withStyle(ChatFormatting.GREEN));
				}
				continue;
			}
			// a night's rest in a bed sleeps it off
			if (player.isSleepingLongEnough()) {
				clear(player);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.battered_slept").withStyle(ChatFormatting.GREEN));
				continue;
			}
			if (refresh) {
				refresh(player);
			}
		}
		if (!refresh || TRACKED.isEmpty()) {
			return;
		}
		// (a copy: clearing untracks)
		for (LivingEntity mob : new ArrayList<>(TRACKED)) {
			if (mob.isRemoved() || !mob.isAlive()) {
				TRACKED.remove(mob);
				continue;
			}
			// worn off, or slept off (a villager in its bed)
			if (!is(mob) || mob.isSleeping()) {
				clear(mob);
				TRACKED.remove(mob);
				continue;
			}
			refresh(mob);
		}
	}

	/** {@code attribute}'s modifier {@code id} at {@code amount} (a share of the total), or gone at 0. */
	private static void modifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			if (instance.getModifier(id) != null) {
				instance.removeModifier(id);
			}
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (now == null || now.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}
}
