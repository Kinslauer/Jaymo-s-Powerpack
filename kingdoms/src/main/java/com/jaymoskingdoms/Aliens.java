package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.1) Jaymo's Powers' Xenoform: a player wearing the Xeno-Band can become an alien. To a kingdom's people that is
 * a monster: villagers run from one ({@link Frights}) and guards hunt it ({@link Folk}'s target goals,
 * {@link MobCombat#foe}), unless they know better. A kingdom's folk who <i>see</i> you change (within {@link #WITNESS}
 * blocks, in line of sight) know you for who you are from then on, and that kingdom treats the alien as it treats you.
 *
 * <p>Read by entity tag ({@link #TAG}, set by Powers while a player is changed), so Kingdoms needs no Powers classes.
 */
final class Aliens {
	/** The tag Powers puts on a changed player; who has seen a player change (faction ids), kept across deaths. */
	static final String TAG = "jaymospowers_xenoform";
	static final AttachmentType<List<String>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("alien_known"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** How close (blocks) a kingdom's people must be, and seeing you, to catch you changing; how often we look. */
	static final double WITNESS = 16.0;
	static final int EVERY = 10;

	private static final Set<UUID> WAS = new HashSet<>();

	private Aliens() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Aliens::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WAS.clear());
	}

	/** Whether {@code e} is a player changed into one of the band's aliens right now. */
	static boolean alien(Entity e) {
		return e.entityTags().contains(TAG);
	}

	/** Whether {@code faction}'s people have seen {@code player} change, and so know the alien is them. */
	static boolean known(ServerPlayer player, int faction) {
		List<String> known = player.getAttached(KNOWN);
		return known != null && known.contains(Integer.toString(faction));
	}

	/** Whether {@code faction}'s people take {@code player} for a monster: an alien they haven't seen change. */
	static boolean feared(ServerPlayer player, int faction) {
		return !player.isCreative() && !player.isSpectator() && alien(player) && !known(player, faction);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean now = alien(player);
			boolean was = WAS.contains(player.getUUID());
			if (now && !was) {
				witness(server, player);
				WAS.add(player.getUUID());
			} else if (!now && was) {
				WAS.remove(player.getUUID());
			}
		}
	}

	/** They changed just now: every kingdom with someone near enough, and looking, learns the alien is them. */
	private static void witness(MinecraftServer server, ServerPlayer player) {
		ServerLevel level = player.level();
		Set<Integer> factions = new LinkedHashSet<>();
		List<LivingEntity> witnesses = new ArrayList<>();
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WITNESS),
			e -> e.isAlive() && (e instanceof Folk || e instanceof Villager) && e.hasLineOfSight(player))) {
			Faction f = factionOf(server, near);
			if (f != null && !f.fallen() && f.type().isRealm()) {
				if (factions.add(f.id())) {
					witnesses.add(near);
				}
			}
		}
		if (factions.isEmpty()) {
			return;
		}
		List<String> known = new ArrayList<>(player.getAttachedOrElse(KNOWN, List.of()));
		boolean news = false;
		for (int id : factions) {
			if (!known.contains(Integer.toString(id))) {
				known.add(Integer.toString(id));
				news = true;
				Faction f = Factions.get(server, id);
				if (f != null) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.alien.seen", f.name()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
		}
		if (news) {
			player.setAttached(KNOWN, known);
			LivingEntity speaker = witnesses.get(0);
			Speech.say(level, speaker, Component.translatable("talk.jaymoskingdoms.alien.seen"), Speech.YELLOW, 24.0);
		}
	}

	/** The kingdom {@code e} belongs to: a folk's own, a villager's by the land they stand on. */
	private static @Nullable Faction factionOf(MinecraftServer server, LivingEntity e) {
		if (e instanceof Folk folk) {
			return Factions.get(server, folk.factionId());
		}
		Faction at = Factions.at(server, e.level(), e.blockPosition());
		return at != null ? at : Factions.around(server, e.level(), e.blockPosition(), 64.0);
	}
}
