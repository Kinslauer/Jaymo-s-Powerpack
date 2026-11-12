package com.jaymoskingdoms.music;

import com.jaymoskingdoms.BossBars;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.remnants.AetherGiant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Which theme a great foe has, and who is in a fight with one. Each theme is a set of short stems the player's own game
 * mixes ({@code FoeMusicClient}): this side only says "this theme, this tense" a few times a second to the players
 * fighting it, and "none" when the fight is over. A player is in a fight with a foe when it is after them or they have
 * struck it ({@link BossBars}), within {@link #RANGE}. Nothing is played on the server.
 * <ul>
 * <li>The Aether's Giants: one theme each (highfield, magnetic, arctic, irradiated).</li>
 * <li>The Gaunt; the werewolf; the wraiths and the silver skeleton; the ogres, the minotaur and the grizzly; a wild wind
 * dragon; the Nether forts' warlords.</li>
 * </ul>
 */
public final class FoeThemes {
	/** How near a foe must be for its theme to play (blocks), and how often fights are looked for (ticks). */
	public static final double RANGE = 48.0;
	public static final int SCAN_EVERY = 10;
	/** A theme is told again this often while the fight goes on (ticks), so the client can tell it has ended if told no more. */
	public static final int REPEAT_TICKS = 40;
	/** The foe's health share at or under which the fight is tense (1) and desperate (2). */
	public static final float TENSE_AT = 0.7F, DESPERATE_AT = 0.35F;
	/** Scans with no foe before the theme is told to stop. */
	static final int GRACE_SCANS = 3;

	/** "Play this theme at this tension", or an empty theme: stop. */
	public record Play(String theme, int tension) implements CustomPacketPayload {
		public static final Type<Play> TYPE = new Type<>(JaymosKingdoms.id("foe_theme"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Play::theme,
			ByteBufCodecs.VAR_INT, Play::tension, Play::new);

		@Override
		public Type<Play> type() {
			return TYPE;
		}
	}

	/** What each player was last told, the game time they were told, how many scans they have gone without a foe, and the foe's id. */
	private record Told(String theme, int tension, long at, int quiet, int foe) {
	}

	private static final Map<UUID, Told> TOLD = new HashMap<>();

	private FoeThemes() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(FoeThemes::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TOLD.remove(handler.player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> TOLD.remove(player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TOLD.clear());
	}

	/** The theme {@code entity} has (a folder name under {@code sounds/foe}), or null if it has none. */
	public static @Nullable String themeOf(@Nullable Entity entity) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof AetherGiant giant) {
			return giant.element().name().toLowerCase(Locale.ROOT);
		}
		var type = entity.getType();
		if (type == com.jaymoskingdoms.hunger.Hunger.GAUNT) {
			return "gaunt";
		}
		if (type == Monsters.WEREWOLF) {
			return "werewolf";
		}
		if (type == Monsters.WRAITH || type == Monsters.FLAME_WRAITH || type == Monsters.SILVER_SKELETON) {
			return "wraith";
		}
		if (type == Monsters.OGRE || type == Monsters.CAVE_OGRE || type == Monsters.FIRE_OGRE || type == Monsters.ARMOURED_OGRE
			|| type == com.jaymoskingdoms.beasts.Beasts.MINOTAUR || entity instanceof com.jaymoskingdoms.wild.Grizzly) {
			return "ogre";
		}
		if (entity instanceof com.jaymoskingdoms.dragons.WindDragon dragon && !dragon.isTamed()) {
			return "dragon";
		}
		if (com.jaymoskingdoms.nether.NetherForts.warlord(entity)
			|| entity instanceof com.jaymoskingdoms.nether.Hornkin hornkin && hornkin.kind() == com.jaymoskingdoms.nether.Hornkin.Kind.WARLORD) {
			return "warlord";
		}
		return null;
	}

	/** Whether {@code player} is in a fight with {@code foe}: it is after them, or they have struck it. */
	static boolean fighting(ServerPlayer player, LivingEntity foe) {
		return foe instanceof Mob mob && mob.getTarget() == player || BossBars.hasHit(foe, player);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			scan(player);
		}
	}

	private static void scan(ServerPlayer player) {
		long now = player.level().getGameTime();
		Told told = TOLD.get(player.getUUID());
		LivingEntity best = null;
		String theme = null;
		double bestD = RANGE * RANGE;
		if (player.isAlive() && !player.isSpectator()) {
			for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), e -> e != player && e.isAlive())) {
				double d = e.distanceToSqr(player);
				String t = d < bestD ? themeOf(e) : null;
				if (t != null && fighting(player, e)) {
					best = e;
					theme = t;
					bestD = d;
				}
			}
		}
		if (best == null) {
			if (told == null) {
				return;
			}
			// the foe it was told of is gone (slain or left behind): stop at once; a lost target gets a moment's grace
			Entity was = player.level().getEntity(told.foe());
			if (was == null || !was.isAlive() || told.quiet() + 1 >= GRACE_SCANS || !player.isAlive()) {
				TOLD.remove(player.getUUID());
				ServerPlayNetworking.send(player, new Play("", 0));
			} else {
				TOLD.put(player.getUUID(), new Told(told.theme(), told.tension(), told.at(), told.quiet() + 1, told.foe()));
			}
			return;
		}
		float share = best.getHealth() / Math.max(1.0F, best.getMaxHealth());
		int tension = share <= DESPERATE_AT ? 2 : share <= TENSE_AT ? 1 : 0;
		if (told == null || !told.theme().equals(theme) || told.tension() != tension || now - told.at() >= REPEAT_TICKS) {
			if (ServerPlayNetworking.canSend(player, Play.TYPE)) {
				ServerPlayNetworking.send(player, new Play(theme, tension));
			}
			TOLD.put(player.getUUID(), new Told(theme, tension, now, 0, best.getId()));
		} else {
			TOLD.put(player.getUUID(), new Told(told.theme(), told.tension(), told.at(), 0, best.getId()));
		}
	}
}
