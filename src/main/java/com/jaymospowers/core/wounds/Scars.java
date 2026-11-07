package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.stamina.Feathers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * (0.20.26) What one left for dead carries for the rest of that life ({@link Mortality}, Jungxvv: "permanent character injuries
 * that forever hinder your gameplay"). Nothing cures a scar; a new life ({@link Mortality#endLife}) starts without them.
 * <ul>
 * <li><b>Burns</b> (fire): the skin is thin and tight, so blows and cuts go deeper: less armour and toughness.</li>
 * <li><b>Weak lungs</b> (drowned): {@link #LUNG_FEATHERS} half-feathers of stamina held back for good.</li>
 * <li><b>Frostbite</b> (frozen): stiff fingers, slower swings and digging.</li>
 * <li><b>Lightning scars</b>, <b>wasting</b> (starved): less most health.</li>
 * <li><b>Crushed ribs</b> (anything else the world did: walls, cactus, crowding): a slower step.</li>
 * </ul>
 * Synced so the wounds screen (and a later skin layer for the burns) can show them.
 */
public final class Scars {
	static final double BURN_ARMOR = -3.0, BURN_TOUGHNESS = -2.0;
	static final int LUNG_FEATHERS = 4;
	static final double FROST_ATTACK_SPEED = -0.1, FROST_DIG = -0.15;
	static final double LIGHTNING_HEALTH = -2.0, WASTING_HEALTH = -2.0;
	static final double CRUSHED_SPEED = -0.05;

	public enum Kind {
		BURNS, WEAK_LUNGS, FROSTBITE, LIGHTNING, WASTING, CRUSHED;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The scars, by {@link Kind#key()}. */
	public static final AttachmentType<List<String>> SCARS = AttachmentRegistry.create(JaymosPowers.id("scars"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

	private Scars() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Scars::tick);
		Feathers.addPenalty(player -> has(player, Kind.WEAK_LUNGS) ? LUNG_FEATHERS : 0);
	}

	public static List<String> of(Player player) {
		List<String> list = player.getAttached(SCARS);
		return list == null ? List.of() : list;
	}

	public static boolean has(Player player, Kind kind) {
		return of(player).contains(kind.key());
	}

	/** {@code kind} for life (once). */
	public static void add(ServerPlayer player, Kind kind) {
		if (has(player, kind)) {
			return;
		}
		List<String> list = new ArrayList<>(of(player));
		list.add(kind.key());
		player.setAttached(SCARS, List.copyOf(list));
		apply(player);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.scar." + kind.key()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	static void clear(ServerPlayer player) {
		player.removeAttached(SCARS);
		apply(player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 100 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			apply(player);
		}
	}

	/** Brings the scars' modifiers in line (they're transient: put back after a respawn or a rejoin). */
	static void apply(ServerPlayer player) {
		set(player, Attributes.ARMOR, "burns_armor", has(player, Kind.BURNS) ? BURN_ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ARMOR_TOUGHNESS, "burns_toughness", has(player, Kind.BURNS) ? BURN_TOUGHNESS : 0.0, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ATTACK_SPEED, "frostbite_swing", has(player, Kind.FROSTBITE) ? FROST_ATTACK_SPEED : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		set(player, Attributes.BLOCK_BREAK_SPEED, "frostbite_dig", has(player, Kind.FROSTBITE) ? FROST_DIG : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		double health = (has(player, Kind.LIGHTNING) ? LIGHTNING_HEALTH : 0.0) + (has(player, Kind.WASTING) ? WASTING_HEALTH : 0.0);
		set(player, Attributes.MAX_HEALTH, "scarred_health", health, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MOVEMENT_SPEED, "crushed_step", has(player, Kind.CRUSHED) ? CRUSHED_SPEED : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = JaymosPowers.id("scar_" + name);
		if (amount == 0.0) {
			instance.removeModifier(id);
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (now == null || now.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}
}
