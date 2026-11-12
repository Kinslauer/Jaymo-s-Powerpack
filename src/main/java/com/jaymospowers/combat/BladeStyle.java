package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.stamina.Feathers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * A fighting style's four trades (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its lightsaber forms are
 * stat trades, and so are these). Whoever fights in a style pays feathers for the swings that land, pays more or less
 * for a blow their weapon guard blocks, swings faster or slower, and gets their feathers back faster or slower. What
 * the styles are and who fights in which is not ours to say: Kingdoms' schools set it ({@link #set}, by name).
 */
public final class BladeStyle {
	/** The least ticks between two swings that are charged for (a sweep through three foes is one swing). */
	static final int SWING_GAP = 5;

	/**
	 * The trades: feathers a landed swing costs (a fraction adds up over swings), what a blocked blow costs (times the
	 * usual), attack speed (times the weapon's own) and how fast feathers regrow (times the usual).
	 */
	public record Style(float swing, float block, float speed, float regen) {
	}

	/** On a player fighting in a style. Not saved, not synced: Kingdoms sets it again whenever it changes. */
	public static final AttachmentType<Style> STYLE = AttachmentRegistry.create(JaymosPowers.id("blade_style"));
	private static final Identifier SPEED_ID = JaymosPowers.id("blade_style");

	/** Each styled player's swing cost not yet charged, and when they were last charged for a swing. */
	private static final Map<UUID, Float> OWED = new HashMap<>();
	private static final Map<UUID, Long> LAST = new HashMap<>();

	private BladeStyle() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer player) {
				swung(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			OWED.remove(handler.player.getUUID());
			LAST.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OWED.clear();
			LAST.clear();
		});
	}

	/** {@code player} fights in a style with these trades from now on. */
	public static void set(ServerPlayer player, float swing, float block, float speed, float regen) {
		player.setAttached(STYLE, new Style(swing, block, speed, regen));
		AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed != null) {
			attackSpeed.removeModifier(SPEED_ID);
			if (speed != 1.0F) {
				attackSpeed.addTransientModifier(new AttributeModifier(SPEED_ID, speed - 1.0F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
	}

	/** {@code player} fights plain again. */
	public static void clear(ServerPlayer player) {
		player.removeAttached(STYLE);
		AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
		if (attackSpeed != null) {
			attackSpeed.removeModifier(SPEED_ID);
		}
		OWED.remove(player.getUUID());
	}

	/** What a blow {@code player}'s weapon guard blocks costs, times the usual. */
	public static float block(Player player) {
		Style style = player.getAttached(STYLE);
		return style == null ? 1.0F : style.block();
	}

	/** How fast {@code player}'s feathers regrow, times the usual. */
	public static float regen(Player player) {
		Style style = player.getAttached(STYLE);
		return style == null ? 1.0F : style.regen();
	}

	/** A swing of {@code player}'s landed: the style's price is paid, as far as there are feathers for it. */
	private static void swung(ServerPlayer player) {
		Style style = player.getAttached(STYLE);
		if (style == null || style.swing() <= 0.0F || player.isCreative()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < SWING_GAP) {
			return;
		}
		LAST.put(player.getUUID(), now);
		float owed = OWED.getOrDefault(player.getUUID(), 0.0F) + style.swing();
		while (owed >= 1.0F) {
			owed -= 1.0F;
			if (!Feathers.spend(player, 1)) {
				owed = 0.0F;
			}
		}
		OWED.put(player.getUUID(), owed);
	}
}
