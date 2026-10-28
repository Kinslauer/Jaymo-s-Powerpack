package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.gifted.Gifted;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.103, Andy: "you get a player's power or villager if you absorb them", then "maybe they just get the power's R
 * ability, and keep their power if they have one") A Rubber-folk (Jaymo's Kingdoms' {@code RubberFolk}, by reflection)
 * who swallows a downed player or a gifted villager keeps their own power and takes only the swallowed power's R
 * ability ({@link #SWALLOWED}), used with the heritage key when there's nothing in sight to swallow ({@link #use}). Only
 * one at a time: the next swallow replaces it, and it's gone when they die.
 */
public final class SwallowedPower {
	/** The power whose R they took. Saved; lost on death. */
	public static final AttachmentType<Identifier> SWALLOWED = AttachmentRegistry.create(JaymosPowers.id("swallowed_power"),
		builder -> builder.persistent(Identifier.CODEC));

	/** Power → its R as a one-shot a swallower can use (true: used, start the cooldown). Each power adds its own. */
	private static final Map<Identifier, Predicate<ServerPlayer>> MOVES = new HashMap<>();
	/** Power → the cooldown (ticks) after its R. */
	private static final Map<Identifier, Integer> COOLDOWNS = new HashMap<>();
	/** Player → the game time their swallowed R is ready again. Not saved. */
	private static final Map<java.util.UUID, Long> READY = new HashMap<>();

	private SwallowedPower() {
	}

	/** A power's R, for swallowers: {@code use} it, and wait {@code cooldown} ticks before the next. */
	public static void register(Identifier power, int cooldown, Predicate<ServerPlayer> use) {
		MOVES.put(power, use);
		COOLDOWNS.put(power, cooldown);
	}

	/**
	 * {@code taker} swallowed {@code body}: they take on its power's R, in place of whatever they took before. The power's
	 * name, or null if it had none. Called by Jaymo's Kingdoms (by reflection) before the body dies.
	 */
	public static @Nullable Component take(ServerPlayer taker, LivingEntity body) {
		Power power;
		if (body instanceof ServerPlayer victim) {
			power = Powers.of(victim);
		} else {
			String gift = Gifted.power(body);
			power = gift == null ? null : Powers.byName(gift);
		}
		if (power == null) {
			return null;
		}
		taker.setAttached(SWALLOWED, power.id());
		READY.remove(taker.getUUID());
		Component name = power.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(power.color())).withBold(true));
		Component move = Component.translatable("power." + power.id().getNamespace() + "." + power.id().getPath() + ".swallowed");
		taker.sendSystemMessage(Component.translatable("message.jaymospowers.swallowed_power", body.getDisplayName(), name, move)
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		return name;
	}

	/**
	 * The heritage key, with nothing to swallow in sight: the swallowed power's R, if they hold one. False if they don't
	 * (for Kingdoms to do whatever else the key does).
	 */
	public static boolean use(ServerPlayer player) {
		Identifier id = player.getAttached(SWALLOWED);
		if (id == null) {
			return false;
		}
		Predicate<ServerPlayer> move = MOVES.get(id);
		if (move == null) {
			return false;
		}
		long now = player.level().getGameTime();
		long ready = READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.swallowed_power_wait", (ready - now + 19) / 20)
				.withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (move.test(player)) {
			READY.put(player.getUUID(), now + COOLDOWNS.getOrDefault(id, 100));
		}
		return true;
	}

	/** The living thing {@code player} looks at within {@code range} (not through walls), or null. */
	public static @Nullable LivingEntity sighted(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e != player, 0.3F);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}
}
