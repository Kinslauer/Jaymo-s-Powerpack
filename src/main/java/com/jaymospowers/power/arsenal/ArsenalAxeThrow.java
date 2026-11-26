package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.BladeThrow;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.TreeLocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * The thrown axe (Axe Throw's idea, MIT-0, by ekulxam: its throwing grip, its draw scaled by the axe's swing speed and its
 * share of the swing's damage; the code is ours). Andy: "apply this to one of the axes arsenal can pull": the diamond axe,
 * the one axe of the rift's pool that Axe Throw throws too ({@link #AXES}; a data pack can add others).
 * <ul>
 * <li>Right-click the axe in your pack (an empty hand on the cursor) to take it in a throwing grip, and again to let the
 * grip go: in the grip, use draws it back like a trident and letting go throws it, end over end; out of it, it is a plain
 * axe (it strips logs, and a shield in the other hand still blocks).</li>
 * <li>Drawn back long enough ({@link #DRAW_TICKS}, longer for a slower axe) it throws; a click says it's ready. It deals
 * {@link #THROWN_SHARE} of the axe's swing and wears it by one.</li>
 * <li>No free axes: the axe thrown is the one in your hand. It sticks where it lands for you to pick up, except in the
 * hand of an Arsenal: the rift has learned its hand, and the axe flies home to it once it has struck or stuck (as
 * Axe Throw's Loyalty does). One pulled out of the rift still goes back there when its time is up, flying or lying.</li>
 * <li>Throwing a weapon is learned: Kingdoms' Thrower ({@link TreeLocks#THROWER}).</li>
 * </ul>
 */
public final class ArsenalAxeThrow {
	// ===== the knobs ================================================================================================
	/** The draw (ticks) for an axe that swings once a second; a faster or slower axe draws in proportion. */
	static final float DRAW_TICKS = 11.0F;
	/** The throw's speed (blocks a tick; a dagger leaves the hand at about 1.6), and its share of the axe's swing damage. */
	static final float THROW_SPEED = 1.8F, THROWN_SHARE = 0.7F;
	// ================================================================================================================

	/** The axes that take a throwing grip. */
	public static final TagKey<Item> AXES = TagKey.create(Registries.ITEM, JaymosPowers.id("throwing_axes"));

	/** On an axe: held in the throwing grip. */
	public static DataComponentType<Boolean> GRIP;

	private ArsenalAxeThrow() {
	}

	static void init() {
		GRIP = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("axe_throw_grip"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
	}

	/** Whether {@code stack} is an axe that takes a throwing grip. */
	public static boolean throwable(ItemStack stack) {
		return !stack.isEmpty() && stack.is(AXES);
	}

	/** Whether {@code stack} is such an axe, held in the throwing grip now. */
	public static boolean gripped(ItemStack stack) {
		return throwable(stack) && GRIP != null && Boolean.TRUE.equals(stack.get(GRIP));
	}

	/** Right-clicked in the pack: the grip taken or let go. */
	public static void toggle(ItemStack stack, Player player) {
		if (gripped(stack)) {
			stack.remove(GRIP);
		} else {
			stack.set(GRIP, true);
		}
		player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4F, gripped(stack) ? 1.0F : 0.75F);
	}

	/** The draw {@code stack} needs before it throws (ticks): {@link #DRAW_TICKS} for a swing a second, in proportion to its speed. */
	public static int drawTicks(ItemStack stack, LivingEntity user) {
		double speed = modifiers(stack).compute(Attributes.ATTACK_SPEED, user.getAttributeBaseValue(Attributes.ATTACK_SPEED), EquipmentSlot.MAINHAND);
		return Math.max(4, Math.round(DRAW_TICKS / (float)Math.max(0.25, speed)));
	}

	/** Drawn back {@code held} ticks: the ready click, once, in the thrower's ears. */
	public static void drawing(LivingEntity user, ItemStack stack, int held) {
		if (user instanceof ServerPlayer player && held == drawTicks(stack, user)) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.4F, 1.3F);
		}
	}

	/** Let go after {@code held} ticks drawn: thrown if drawn far enough (true). */
	public static boolean release(ItemStack stack, LivingEntity user, int held) {
		if (!(user instanceof Player player) || held < drawTicks(stack, user) || stack.nextDamageWillBreak()) {
			return false;
		}
		if (!(player instanceof ServerPlayer server)) {
			return true;
		}
		if (!TreeLocks.check(server, TreeLocks.THROWER)) {
			// (asked before the axe leaves the hand, so a refused throw keeps it there)
			return false;
		}
		double swing = modifiers(stack).compute(Attributes.ATTACK_DAMAGE, server.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
		stack.hurtWithoutBreaking(1, server);
		ItemStack axe = stack.consumeAndReturn(1, server);
		boolean home = Powers.has(server, ArsenalPower.INSTANCE);
		if (!BladeThrow.hurl(server, axe, (float)swing * THROWN_SHARE, THROW_SPEED, home)) {
			// (nothing flew: the axe goes back where it was)
			server.getInventory().add(axe);
			return false;
		}
		server.awardStat(Stats.ITEM_USED.get(axe.getItem()));
		return true;
	}

	private static ItemAttributeModifiers modifiers(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
	}
}
