package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A throne of the tribes (Tensura Neo Otherworld's demon lords, ARR: the idea only, our own code), the endgame of the monster-born.
 * A player reborn a monster ({@link MonsterBorn}) who has grown to the last stage, wears a realm's crown ({@link Crowns}: founded,
 * claimed or taken) and has won the favour of the tribes ({@link TribePacts#FRIEND_AT} or better with {@link #TRIBES_NEEDED} tribes, at
 * least one goblin and one orc) takes the throne when they stand before their own: the tribes bow to them. From then the war bands of the
 * tribes leave that realm be ({@link TribePacts#spared}) and its sovereign is named the {@link #TITLE}. It sits on the realm that
 * already has a crown and a throne: no new place, no new dimension.
 */
public final class DemonThrone {
	/** How many tribes must stand with the claimant, and how near their throne they must stand (blocks). */
	static final int TRIBES_NEEDED = 3;
	static final double AT_THRONE = 4.0;
	static final int EVERY = 100;
	/** The title a taken throne gives (lang {@code title.jaymoskingdoms.tribe_lord}); the id in {@link TitleChoice}. */
	public static final String TITLE = "tribe_lord";

	/** On the realm level: the realms whose thrones have been taken by a monster-born sovereign. Saved. */
	static final AttachmentType<List<Integer>> THRONES = AttachmentRegistry.create(JaymosKingdoms.id("tribe_thrones"), b -> b.persistent(Codec.INT.listOf()));
	/** Per player: the throne is theirs. Saved, kept through death. */
	static final AttachmentType<Boolean> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("tribe_throne_taken"), b -> b.persistent(Codec.BOOL).copyOnDeath());

	private DemonThrone() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DemonThrone::tick);
	}

	/** Whether realm {@code f} sits under a taken throne of the tribes (so long as its sovereign is the one who took it). */
	static boolean holds(MinecraftServer server, @Nullable Faction f) {
		return f != null && Factions.level(server).getAttachedOrElse(THRONES, List.<Integer>of()).contains(f.id()) && Crowns.sovereign(server, f) != null;
	}

	/** The title entry for the sovereign of a taken throne, for {@link TitleChoice#earned}. */
	static List<TitleChoice.Entry> earned(Player player) {
		return player.getAttachedOrElse(TAKEN, false) ? List.of(new TitleChoice.Entry(TITLE, Component.translatable("title.jaymoskingdoms." + TITLE), 0x8CC060)) : List.of();
	}

	/** How many tribes stand with {@code player}: those whose favour is {@link TribePacts#FRIEND_AT} or better; the goblin and the orc ones apart. */
	static int friends(MinecraftServer server, UUID player, boolean[] both) {
		Map<String, Tribes.Tribe> all = Factions.level(server).getAttachedOrElse(Tribes.TRIBES, Map.<String, Tribes.Tribe>of());
		int n = 0;
		boolean goblin = false, orc = false;
		for (String key : all.keySet()) {
			if (Tribes.grudge(server, player, key) <= TribePacts.FRIEND_AT) {
				n++;
				goblin |= key.startsWith(Tribes.GOBLIN + "@");
				orc |= key.startsWith(Tribes.ORC + "@");
			}
		}
		both[0] = goblin && orc;
		return n;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.getAttachedOrElse(TAKEN, false) || MonsterBorn.stage(p) < 3 || !(p.level() instanceof ServerLevel level)) {
				continue;
			}
			Faction realm = Crowns.crownOf(server, p.getUUID());
			if (realm == null || level != Factions.level(server) || p.blockPosition().distSqr(Crowns.throne(server, realm)) > AT_THRONE * AT_THRONE) {
				continue;
			}
			boolean[] both = new boolean[1];
			int friends = friends(server, p.getUUID(), both);
			if (friends < TRIBES_NEEDED || !both[0]) {
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.throne.not_yet", friends, TRIBES_NEEDED).withStyle(ChatFormatting.GRAY));
				continue;
			}
			take(server, level, p, realm);
		}
	}

	private static void take(MinecraftServer server, ServerLevel level, ServerPlayer player, Faction realm) {
		List<Integer> thrones = new ArrayList<>(level.getAttachedOrElse(THRONES, List.<Integer>of()));
		thrones.add(realm.id());
		level.setAttached(THRONES, List.copyOf(thrones));
		player.setAttached(TAKEN, true);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.8, 1.0, 0.8, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 0.6F);
		KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.throne.taken", player.getDisplayName(), realm.name()).withStyle(ChatFormatting.DARK_GREEN));
	}
}
