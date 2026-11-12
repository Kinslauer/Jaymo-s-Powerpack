package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Bulwark's fury: a rage that builds from blows given and blows taken (0 to {@link #MAX}) and burns down a few seconds
 * after the last one. The fuller it is the harder Bulwark hits and the less anything moves it; at full it hits
 * {@link #DAMAGE_BONUS} harder and carries {@link #KNOCKBACK_BONUS} more knockback resistance. Synced to its wearer for
 * the Xenoform HUD.
 */
public final class BulwarkFury {
	// ---- tuning ----
	public static final int MAX = 100;
	/** Fury gained from a blow dealt, and from a blow taken (per point of damage, kept between the two limits). */
	public static final int GAIN_DEALT = 7, GAIN_TAKEN_MIN = 4, GAIN_TAKEN_MAX = 14;
	public static final float GAIN_TAKEN_PER_DAMAGE = 1.5F;
	/** Ticks after the last gain before it fades, then how much it loses every {@code DECAY_EVERY} ticks. */
	public static final int DECAY_DELAY = 100, DECAY_EVERY = 3, DECAY_STEP = 1;
	/** At full fury: more melee damage (share), more knockback resistance (added). */
	public static final double DAMAGE_BONUS = 0.5, KNOCKBACK_BONUS = 0.4;
	/** The effects change in steps of this much fury, so the attributes are not rewritten on every point. */
	public static final int STEP = 5;
	public static final int COLOR = 0xC84A2A;

	/** Fury, 0 to {@link #MAX}. Synced to its wearer for the HUD. */
	public static final AttachmentType<Integer> FURY = AttachmentRegistry.create(JaymosPowers.id("bulwark_fury"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final Identifier DAMAGE_ID = JaymosPowers.id("xenoform_bulwark_fury_damage");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("xenoform_bulwark_fury_knockback");

	/** Last game time each player's fury grew. */
	private static final Map<UUID, Long> LAST_GAIN = new HashMap<>();

	private BulwarkFury() {
	}

	/** Fury, 0 when there is none. */
	public static int fury(Player player) {
		Integer value = player.getAttached(FURY);
		return value == null ? 0 : value;
	}

	/** A blow dealt. */
	public static void dealt(ServerPlayer player) {
		gain(player, GAIN_DEALT);
	}

	/** A blow of {@code amount} taken. */
	public static void taken(ServerPlayer player, float amount) {
		gain(player, Math.max(GAIN_TAKEN_MIN, Math.min(GAIN_TAKEN_MAX, Math.round(amount * GAIN_TAKEN_PER_DAMAGE))));
	}

	/** {@code damage} as the fury makes it: up to {@link #DAMAGE_BONUS} more at full. */
	public static float boost(ServerPlayer player, float damage) {
		return damage * (1.0F + (float)(DAMAGE_BONUS * share(fury(player))));
	}

	private static double share(int value) {
		return Math.min(MAX, value) / STEP * STEP / (double)MAX;
	}

	private static void gain(ServerPlayer player, int amount) {
		int before = fury(player);
		int after = Math.min(MAX, before + amount);
		LAST_GAIN.put(player.getUUID(), player.level().getGameTime());
		if (after == before) {
			return;
		}
		set(player, after);
		if (after >= MAX) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_GOLEM_REPAIR, SoundSource.PLAYERS, 1.2F, 0.5F);
			MagicFx.ring(player.level(), player.position(), 2.5F, COLOR);
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.xenoform.bulwark.fury_full").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC));
		}
	}

	private static void set(ServerPlayer player, int value) {
		if (value <= 0) {
			player.removeAttached(FURY);
		} else {
			player.setAttached(FURY, value);
		}
		double share = share(value);
		modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, DAMAGE_BONUS * share);
		modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, AttributeModifier.Operation.ADD_VALUE, KNOCKBACK_BONUS * share);
	}

	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, AttributeModifier.Operation operation, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier existing = instance.getModifier(id);
		if (amount <= 0.0) {
			if (existing != null) {
				instance.removeModifier(id);
			}
		} else if (existing == null || existing.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Every tick as Bulwark: out of a fight a while, fury burns down. */
	public static void tick(ServerPlayer player) {
		int value = fury(player);
		if (value <= 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - LAST_GAIN.getOrDefault(player.getUUID(), 0L) >= DECAY_DELAY && now % DECAY_EVERY == 0) {
			set(player, Math.max(0, value - DECAY_STEP));
		}
	}

	/** Bulwark is over: the fury goes with it. */
	public static void clear(ServerPlayer player) {
		LAST_GAIN.remove(player.getUUID());
		set(player, 0);
	}
}
