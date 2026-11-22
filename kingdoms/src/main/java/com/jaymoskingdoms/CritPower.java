package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Powers' two crit multipliers ({@code jaymospowers:sneak_attack_power} and {@code headshot_power}, 1 with nothing worn),
 * read by registry lookup so Kingdoms never needs Powers: a missing attribute is a multiplier of 1. The hidden blade, while it is
 * out, lifts the sneak attack power by {@link #BLADE_LIFT}, as Powers' held daggers do (a transient modifier, looked at every
 * {@link #CHECK_TICKS}).
 */
public final class CritPower {
	/** The hidden blade's lift (a share of the base: 0.25 is 25% more), and how often (ticks) it is looked at. */
	static final double BLADE_LIFT = 0.25;
	static final int CHECK_TICKS = 10;
	private static final Identifier BLADE_ID = JaymosKingdoms.id("held_hidden_blade");

	private CritPower() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				held(player, HiddenBlade.bladeOut(player));
			}
		});
	}

	private static @Nullable Holder<Attribute> attribute(String name) {
		return BuiltInRegistries.ATTRIBUTE.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", name))
			.map(BuiltInRegistries.ATTRIBUTE::wrapAsHolder).orElse(null);
	}

	private static double of(LivingEntity body, String name) {
		Holder<Attribute> attribute = attribute(name);
		return attribute != null && body.getAttributes().hasAttribute(attribute) ? body.getAttributeValue(attribute) : 1.0;
	}

	/** {@code body}'s sneak attack power: how much more a sneak attack, a Thief's burst or an assassination deals (1 without Powers). */
	public static float sneak(LivingEntity body) {
		return (float) of(body, "sneak_attack_power");
	}

	/** {@code body}'s headshot power (1 without Powers). */
	public static float headshot(LivingEntity body) {
		return (float) of(body, "headshot_power");
	}

	private static void held(ServerPlayer player, boolean holding) {
		Holder<Attribute> attribute = attribute("sneak_attack_power");
		AttributeInstance instance = attribute == null ? null : player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (holding) {
			if (!instance.hasModifier(BLADE_ID)) {
				instance.addTransientModifier(new AttributeModifier(BLADE_ID, BLADE_LIFT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		} else if (instance.hasModifier(BLADE_ID)) {
			instance.removeModifier(BLADE_ID);
		}
	}
}
