package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/**
 * What each {@link FrontierArmour} set does for the one who wears it whole (any of its colourings, mixed or not), on top
 * of whatever their class gives them:
 * <ul>
 * <li><b>Primalist</b>: {@link #PRIMALIST_SPEED} faster on their feet.</li>
 * <li><b>Ranger</b>: guns load {@link #RANGER_RELOAD} quicker (WeaponMod's through {@link WeaponModGuns#quicker}, the
 * {@link Musketoon} itself, and a guard's own loading).</li>
 * <li><b>Scoundrel</b>: no slowing while eating, drinking or drawing a bow (the client's {@code ItemUseSpeedMixin}).</li>
 * <li><b>Sanguine</b>: every healing counts {@link #SANGUINE_HEALING} more ({@code LivingEntityHealMixin}).</li>
 * <li><b>Despot</b>: {@link #DESPOT_ABSORPTION} points of gold-plate absorption that grow back once nothing has struck
 * them for {@link #DESPOT_CALM} ticks. Cut for a greenskin's frame: on a player who wasn't raised among goblins or
 * orcs a piece slides off again.</li>
 * <li><b>Templar</b> and <b>Juggernaut</b> stand their ground (knockback resistance, a piece at a time, in their
 * materials); every piece of Juggernaut plate also slows its wearer by {@link #JUGGERNAUT_SLOW}.</li>
 * </ul>
 */
public final class FrontierPerks {
	static final double PRIMALIST_SPEED = 0.1, JUGGERNAUT_SLOW = 0.03;
	public static final double RANGER_RELOAD = 0.15;
	public static final float SANGUINE_HEALING = 0.2F;
	static final double DESPOT_ABSORPTION = 8.0;
	/** Ticks unhurt before the Despot's absorption grows back, and ticks between the points it grows by. */
	static final int DESPOT_CALM = 200, DESPOT_REGROW = 40;
	/** How often each player's sets are looked at (ticks). */
	static final int CHECK = 10;

	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private FrontierPerks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK == 0) {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					tick(player, server.getTickCount());
				}
			}
		});
	}

	private static void tick(ServerPlayer player, int now) {
		int despot = FrontierArmour.pieces(player, "despot");
		if (despot > 0 && !GreenskinLife.raised(player) && !player.isCreative()) {
			shed(player);
			despot = 0;
		}
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "frontier_primalist", FrontierArmour.whole(player, "primalist") ? PRIMALIST_SPEED : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "frontier_juggernaut", -JUGGERNAUT_SLOW * FrontierArmour.pieces(player, "juggernaut"),
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		boolean plated = despot >= ARMOUR.length;
		Skills.modifier(player, Attributes.MAX_ABSORPTION, "frontier_despot", plated ? DESPOT_ABSORPTION : 0.0, AttributeModifier.Operation.ADD_VALUE);
		float most = player.getMaxAbsorption();
		if (player.getAbsorptionAmount() > most) {
			player.setAbsorptionAmount(most);
		} else if (plated && player.getAbsorptionAmount() < DESPOT_ABSORPTION && now % DESPOT_REGROW == 0
			&& player.tickCount - player.getLastHurtByMobTimestamp() > DESPOT_CALM) {
			player.setAbsorptionAmount((float) Math.min(DESPOT_ABSORPTION, player.getAbsorptionAmount() + 1.0F));
		}
	}

	/** The Despot's plate comes off {@code player} (not a greenskin) and back into their pack. */
	private static void shed(ServerPlayer player) {
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack worn = player.getItemBySlot(slot);
			if ("despot".equals(FrontierArmour.setOf(worn))) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				player.getInventory().placeItemBackInInventory(worn, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.frontier.despot_shed"));
	}

	/** The share quicker {@code user} loads a gun: the Ranger's harness, worn whole. */
	public static double reload(LivingEntity user) {
		return FrontierArmour.whole(user, "ranger") ? RANGER_RELOAD : 0.0;
	}

	/** {@code ticks} of loading as {@code user} gets through them. */
	static int reload(LivingEntity user, int ticks) {
		return (int) Math.round(ticks * (1.0 - reload(user)));
	}

	/** What {@code amount} of healing comes to on {@code healed}: more in the Sanguine coat, worn whole. */
	public static float healing(LivingEntity healed, float amount) {
		return FrontierArmour.whole(healed, "sanguine") ? amount * (1.0F + SANGUINE_HEALING) : amount;
	}

	/** {@code user} keeps their pace while using an item: the Scoundrel's wrappings, worn whole. */
	public static boolean surefooted(LivingEntity user) {
		return FrontierArmour.whole(user, "scoundrel");
	}
}
