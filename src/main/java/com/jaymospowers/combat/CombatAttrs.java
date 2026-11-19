package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.KnifeGuard;
import com.jaymospowers.armory.RapierItem;
import com.jaymospowers.armory.RecurveBowItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Andy's Hackers 'n Slashers pick (All Rights Reserved, ideas only): the four numbers a fighter's gear can raise. Each is a
 * multiplier, 1 with nothing worn: {@link #RIPOSTE_POWER} (how hard the counter after a parry lands, {@link Guard}),
 * {@link #PARRY_RECHARGE} (how soon a guard can be raised again after a miss, {@link Guard}), and
 * {@link #SNEAK_ATTACK_POWER} and {@link #HEADSHOT_POWER}, which Kingdoms reads by registry lookup. Players carry them
 * ({@code PlayerCritAttributesMixin}); only gear we already have raises them (see {@link com.jaymospowers.baubles.Baubles} and
 * {@link CritGear}).
 */
public final class CombatAttrs {
	/** What nothing worn gives, and the most gear can lift a multiplier to. */
	public static final double BASE = 1.0, MAX = 4.0;
	/** Held gear's lift (a share of the base, so 0.25 is 25% more) and how often (ticks) the held gear is looked at again. */
	public static final double HELD_LIFT = 0.25;
	static final int HELD_CHECK_TICKS = 10;

	public static final Holder<Attribute> RIPOSTE_POWER = make("riposte_power");
	public static final Holder<Attribute> SNEAK_ATTACK_POWER = make("sneak_attack_power");
	public static final Holder<Attribute> HEADSHOT_POWER = make("headshot_power");
	public static final Holder<Attribute> PARRY_RECHARGE = make("parry_recharge");

	private CombatAttrs() {
	}

	private static Holder<Attribute> make(String name) {
		return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, JaymosPowers.id(name),
			new RangedAttribute("attribute.name.jaymospowers." + name, BASE, 0.1, MAX).setSyncable(true));
	}

	/** Loads the class, which registers the attributes (it may already have been, when the first player's attributes were built). */
	public static void init() {
		// held gear: a rapier lifts the riposte, a dagger or knife the sneak attack, a recurve bow the headshot
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % HELD_CHECK_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ItemStack main = player.getMainHandItem();
				held(player, RIPOSTE_POWER, "held_rapier", main.getItem() instanceof RapierItem);
				held(player, SNEAK_ATTACK_POWER, "held_dagger", isDagger(main));
				held(player, HEADSHOT_POWER, "held_recurve", main.getItem() instanceof RecurveBowItem || player.getOffhandItem().getItem() instanceof RecurveBowItem);
			}
		});
	}

	private static boolean isDagger(ItemStack stack) {
		String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return KnifeGuard.isKnife(stack.getItem()) || path.endsWith("_dagger") || path.equals("dagger");
	}

	private static void held(ServerPlayer player, Holder<Attribute> attribute, String name, boolean holding) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = JaymosPowers.id(name);
		if (holding) {
			if (!instance.hasModifier(id)) {
				instance.addTransientModifier(new AttributeModifier(id, HELD_LIFT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
			}
		} else if (instance.hasModifier(id)) {
			instance.removeModifier(id);
		}
	}

	/** {@code body}'s value of {@code attribute}, 1 for anything that doesn't carry it. */
	public static double of(LivingEntity body, Holder<Attribute> attribute) {
		return body.getAttributes().hasAttribute(attribute) ? body.getAttributeValue(attribute) : BASE;
	}
}
