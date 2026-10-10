package com.jaymospowers.power.gravity;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Gravity ignores weight (every mastery level): a Gravity user lightens whatever they hold. They can carry any mob
 * that isn't a boss (the big ones overhead, see {@code core/Carry}) with no carry slowdown, pull any Kitchen cart by
 * hand with no drag ({@code GravityCartMixin}), and no equipped armor or held item slows them down.
 */
public final class GravityWeight {
	/** Wider or taller than this (blocks) and even Gravity can't carry it. */
	static final double MAX_WIDTH = 4.0, MAX_HEIGHT = 6.0;
	/** A mob (never a player) wider or taller than this (blocks) is lifted overhead instead of held in the arms. */
	static final double OVERHEAD_WIDTH = 0.8, OVERHEAD_HEIGHT = 1.0;
	/** How often (ticks) the counter to equipment slowdown is worked out again. */
	static final int ARMOR_CHECK_TICKS = 20;
	/** A multiplying slowdown is only countered down to this share of the speed it leaves (so no huge counters). */
	static final double MIN_TOTAL_FACTOR = 0.05;

	/** Bosses (the conventional tag, if any mod fills it), never carried. */
	public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("c", "bosses"));
	private static final Identifier UNBURDEN_ADD_ID = JaymosPowers.id("gravity_unburden_add");
	private static final Identifier UNBURDEN_BASE_ID = JaymosPowers.id("gravity_unburden_base");
	private static final Identifier UNBURDEN_TOTAL_ID = JaymosPowers.id("gravity_unburden_total");
	/** Jaymo's Kitchen's cart drag on a player pulling by hand (lifted if it was put on before the power came). */
	private static final Identifier KITCHEN_TOWING_ID = Identifier.fromNamespaceAndPath("jaymoskitchen", "towing");

	private GravityWeight() {
	}

	/** Whether this is a Gravity user, for whom nothing they hold, pull or wear weighs anything. Both sides. */
	public static boolean lightens(@Nullable Entity entity) {
		return entity instanceof Player player && Powers.of(player) == GravityPower.INSTANCE;
	}

	/** Whether a Gravity user can pick this up: any living thing but a boss, an armor stand or something absurdly big. */
	public static boolean liftable(Entity entity) {
		if (!(entity instanceof LivingEntity) || entity instanceof ArmorStand || !entity.isAlive() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof EnderDragon || entity instanceof WitherBoss || entity.getType().builtInRegistryHolder().is(BOSSES)) {
			return false;
		}
		if (entity.getBbWidth() > MAX_WIDTH || entity.getBbHeight() > MAX_HEIGHT) {
			return false;
		}
		// not out of Lift's own grip
		return entity.getAttached(GravityPower.LIFTED) == null;
	}

	/** Whether a Gravity user holds this up overhead: a mob too big for the arms' holds (never a player). */
	public static boolean overhead(Entity entity) {
		return !(entity instanceof Player) && (entity.getBbWidth() > OVERHEAD_WIDTH || entity.getBbHeight() > OVERHEAD_HEIGHT);
	}

	/** From Gravity's tick: cancels any slowdown from equipped armor or held items, rechecked every second. */
	static void tick(ServerPlayer player) {
		if (player.level().getGameTime() % ARMOR_CHECK_TICKS != 0) {
			return;
		}
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		double[] sums = {0.0, 0.0, 1.0};
		Set<Identifier> seen = new HashSet<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			ItemStack stack = player.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			stack.forEachModifier(slot, (attribute, modifier) -> {
				// only a slowdown that's really on the player right now, once each
				if (attribute.value() != Attributes.MOVEMENT_SPEED.value() || modifier.amount() >= 0.0
					|| !speed.hasModifier(modifier.id()) || !seen.add(modifier.id())) {
					return;
				}
				switch (modifier.operation()) {
					case ADD_VALUE -> sums[0] += modifier.amount();
					case ADD_MULTIPLIED_BASE -> sums[1] += modifier.amount();
					case ADD_MULTIPLIED_TOTAL -> sums[2] *= 1.0 + modifier.amount();
				}
			});
		}
		counter(speed, UNBURDEN_ADD_ID, -sums[0], AttributeModifier.Operation.ADD_VALUE);
		counter(speed, UNBURDEN_BASE_ID, -sums[1], AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		counter(speed, UNBURDEN_TOTAL_ID, 1.0 / Math.max(MIN_TOTAL_FACTOR, sums[2]) - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		speed.removeModifier(KITCHEN_TOWING_ID);
	}

	private static void counter(AttributeInstance speed, Identifier id, double amount, AttributeModifier.Operation operation) {
		if (Math.abs(amount) < 1.0E-6) {
			speed.removeModifier(id);
		} else {
			speed.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** The power's gone: the equipment's weight comes back. */
	static void clear(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(UNBURDEN_ADD_ID);
			speed.removeModifier(UNBURDEN_BASE_ID);
			speed.removeModifier(UNBURDEN_TOTAL_ID);
		}
	}
}
