package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.323) Monster kinship. Vampires and werewolves hear each other. A vampire player or a cursed werewolf player with
 * another of the two in sight (the same {@link BloodFever#inSight} test as the vampire's heartbeat, up to
 * {@link #RANGE} blocks) hears a cold, slow beat: a low heartbeat for a vampire, a soft growl for a werewolf, nearer
 * louder ({@code KinshipClient}), and sees no red pulse (the mortal heartbeats of {@link BloodFever} and {@link MoonPull}
 * pass kin over). It is synced to the listener alone.
 */
public final class Kinship {
	/** How far kin are heard (blocks). */
	static final double RANGE = 150.0;
	/** The attachment's value: the kind seen times {@link #KIND}, plus how close they are in hundredths. */
	public static final int KIND = 1000, VAMPIRE_KIN = 1, WOLF_KIN = 2;

	/** What the listener hears now (kind * 1000 + closeness 1-100); synced to them alone. Not kept. */
	public static final AttachmentType<Integer> HEARD = AttachmentRegistry.create(JaymosKingdoms.id("kin_heard"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private Kinship() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Kinship::tick);
	}

	/** A vampire (not a dhampir), or a werewolf cursed in full (a beast too): what the kin hear in each other. */
	public static boolean monster(Player p) {
		return Vampires.isVampire(p) && !VampirePlayers.dhampir(p) || p instanceof ServerPlayer s && FeralCurse.stillCursed(s);
	}

	/** The kind of kin they hear ({@link #VAMPIRE_KIN}, {@link #WOLF_KIN}, or 0), on either side. */
	public static int kind(Player p) {
		Integer h = p.getAttached(HEARD);
		return h == null ? 0 : h / KIND;
	}

	/** How close the kin they hear is (0-1), on either side. */
	public static float closeness(Player p) {
		Integer h = p.getAttached(HEARD);
		return h == null ? 0.0F : (h % KIND) / 100.0F;
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 17) {
				continue;
			}
			int heard = 0;
			if (player.isAlive() && !player.isSpectator() && !player.isCreative() && monster(player)) {
				double best = Double.MAX_VALUE;
				for (ServerPlayer other : player.level().players()) {
					if (other == player || !other.isAlive() || other.isSpectator() || other.isInvisible() && !WerewolfPlayers.beast(other) || !monster(other)) {
						continue;
					}
					double d = other.distanceTo(player);
					if (d <= RANGE && d < best && BloodFever.inSight(player, other)) {
						best = d;
						int close = Math.max(1, (int) Math.round((1.0 - d / RANGE) * 100.0));
						heard = (Vampires.isVampire(other) ? VAMPIRE_KIN : WOLF_KIN) * KIND + close;
					}
				}
			}
			Integer was = player.getAttached(HEARD);
			if (heard == 0) {
				if (was != null) {
					player.removeAttached(HEARD);
				}
			} else if (was == null || was != heard) {
				player.setAttached(HEARD, heard);
			}
		}
	}
}
