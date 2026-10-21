package com.jaymospowers.aether.gear;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * The bonus of a whole set of armour: zanite is quicker (attack speed, mining speed, walking), arkenium shrugs off blasts and
 * a share of every hit, gravitite halves falls (and gives the extra mid-air jump, see {@link GravititeTools}), neptune swims
 * and holds its breath. Attributes are put on and taken off as the set is worn, every {@link #CHECK_TICKS}; they are
 * transient, so they are never saved with the player.
 */
public final class ArmorAbilities {
	// ---- tuning ----
	public static final int CHECK_TICKS = 5;
	/** Zanite: attack speed added, mining speed added, walking speed (share, multiplied). */
	public static final double ZANITE_ATTACK_SPEED = 0.15, ZANITE_MINING = 2.0, ZANITE_SPEED = 0.05;
	/** Arkenium: blast knockback resisted, and the share of a hit that is healed straight back. */
	public static final double ARKENIUM_BLAST_RESIST = 0.3;
	public static final float ARKENIUM_CUT = 0.16F;
	/** Gravitite: falls hurt this much less (added to the fall damage multiplier). */
	public static final double GRAVITITE_FALL = -0.5;
	/** Neptune: water movement efficiency added (the game caps it at 1), and extra breath. */
	public static final double NEPTUNE_WATER = 2.0, NEPTUNE_OXYGEN = 1.5;

	private ArmorAbilities() {
	}

	/** One attribute a set gives. */
	private record Bonus(Holder<Attribute> attribute, Identifier id, double amount, Operation operation) {
	}

	private static Bonus bonus(Holder<Attribute> attribute, String name, double amount, Operation operation) {
		return new Bonus(attribute, JaymosPowers.id("aether_set_" + name), amount, operation);
	}

	private static final Bonus[] ZANITE = {
		bonus(Attributes.ATTACK_SPEED, "zanite_attack", ZANITE_ATTACK_SPEED, Operation.ADD_VALUE),
		bonus(Attributes.MINING_EFFICIENCY, "zanite_mining", ZANITE_MINING, Operation.ADD_VALUE),
		bonus(Attributes.MOVEMENT_SPEED, "zanite_speed", ZANITE_SPEED, Operation.ADD_MULTIPLIED_TOTAL)};
	private static final Bonus[] ARKENIUM = {
		bonus(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "arkenium_blast", ARKENIUM_BLAST_RESIST, Operation.ADD_VALUE)};
	private static final Bonus[] GRAVITITE = {
		bonus(Attributes.FALL_DAMAGE_MULTIPLIER, "gravitite_fall", GRAVITITE_FALL, Operation.ADD_VALUE)};
	private static final Bonus[] NEPTUNE = {
		bonus(Attributes.WATER_MOVEMENT_EFFICIENCY, "neptune_water", NEPTUNE_WATER, Operation.ADD_VALUE),
		bonus(Attributes.OXYGEN_BONUS, "neptune_oxygen", NEPTUNE_OXYGEN, Operation.ADD_VALUE)};

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_TICKS != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				apply(player, ArmorSet.ZANITE, ZANITE);
				apply(player, ArmorSet.ARKENIUM, ARKENIUM);
				apply(player, ArmorSet.GRAVITITE, GRAVITITE);
				apply(player, ArmorSet.NEPTUNE, NEPTUNE);
			}
		});
		// arkenium: a share of the blow is healed straight back (the game has no hook to shrink damage before it lands;
		// a killing blow never reaches here, which is why the cut is small)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && damageTaken > 0.0F && entity instanceof Player player && ArmorSet.ARKENIUM.wornBy(player) && player.isAlive()) {
				player.heal(damageTaken * ARKENIUM_CUT);
			}
		});
	}

	private static void apply(Player player, ArmorSet set, Bonus[] bonuses) {
		boolean worn = !player.isSpectator() && set.wornBy(player);
		for (Bonus bonus : bonuses) {
			AttributeInstance instance = player.getAttribute(bonus.attribute());
			if (instance == null) {
				continue;
			}
			if (worn) {
				if (!instance.hasModifier(bonus.id())) {
					instance.addTransientModifier(new AttributeModifier(bonus.id(), bonus.amount(), bonus.operation()));
				}
			} else if (instance.hasModifier(bonus.id())) {
				instance.removeModifier(bonus.id());
			}
		}
	}

	/** Whether {@code entity} wears the whole gravitite set (for the extra jump). */
	public static boolean gravitite(LivingEntity entity) {
		return ArmorSet.GRAVITITE.wornBy(entity);
	}
}
