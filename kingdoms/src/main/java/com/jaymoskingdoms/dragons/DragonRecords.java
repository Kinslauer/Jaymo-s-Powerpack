package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.141) What each player has done to the wind dragons, for Eurus and Aeolus in their disguises ({@link Disguises}).
 * Andy: they "will transform after questioning you if you have already beaten two other dragons"; "if you haven't
 * killed the past three they simply shoo you off unless you have wind control, a tamed dragon, an unlocked heritage,
 * arsenal, chrono or darktide". Only kills count (Andy), and only different kinds: Eurus wants two of the wind dragons
 * beaten, Aeolus three. Saved, kept through death, synced to the player.
 */
public final class DragonRecords {
	/** How many kinds of wind dragon a player must have beaten for Eurus, and for Aeolus, to show itself. */
	public static final int EURUS_BEATEN = 2, AEOLUS_BEATEN = 3;
	/** The powers (Jaymo's Powers, by id path) a disguised dragon shows itself to anyway. */
	static final Set<String> TRUSTED_POWERS = Set.of("wind", "arsenal", "chrono", "darktide");

	/** The kinds of wind dragon ("notus", "boreas", ...) the player has had a hand in killing. */
	public static final AttachmentType<List<String>> SLAIN = AttachmentRegistry.create(JaymosKingdoms.id("dragons_slain"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));
	/** Whether the player has ever tamed a wind dragon. */
	public static final AttachmentType<Boolean> TAMER = AttachmentRegistry.create(JaymosKingdoms.id("dragon_tamer"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/**
	 * Whether anyone in this world has killed a dragon (any dragon: ours, the Ender Dragon, another mod's), saved on the
	 * Overworld. Andy: dragons "can only appear in the overworld after you've killed one. Any dragon counts. Besides that
	 * they either fly around in the aether or the nether". Until then no dragon spawns or comes calling in the Overworld
	 * (Encounters, Dragons, Disguises); they are met in the Aether, and Ignivorus and Notus in the Nether.
	 */
	static final AttachmentType<Boolean> WORLD_SLAIN = AttachmentRegistry.create(JaymosKingdoms.id("world_dragon_slain"),
		builder -> builder.persistent(Codec.BOOL));
	/** {@link #WORLD_SLAIN}, kept to hand: natural spawning asks it off the server thread too. */
	private static volatile boolean worldSlain;

	private DragonRecords() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> worldSlain = Boolean.TRUE.equals(server.overworld().getAttached(WORLD_SLAIN)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> worldSlain = false);
		ServerLivingEntityEvents.AFTER_DEATH.register(DragonRecords::died);
	}

	/** Someone has killed a dragon in this world: they may come to the Overworld now. */
	public static boolean worldSlain() {
		return worldSlain;
	}

	/** A dragon died: if a player killed it (and it was nobody's), dragons may come to the Overworld from now on. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (worldSlain || !(entity.level() instanceof ServerLevel level) || !dragon(entity)
			|| entity instanceof OwnableEntity owned && owned.getOwnerReference() != null || entity instanceof WindDragon tame && tame.isTamed()
			|| !(source.getEntity() instanceof Player) && !(entity.getKillCredit() instanceof Player)) {
			return;
		}
		markSlain(level.getServer());
	}

	/** A dragon of any kind: the Ender Dragon, one of ours, or any mob whose id names it one. */
	static boolean dragon(LivingEntity entity) {
		return entity instanceof EnderDragon || entity instanceof WindDragon
			|| BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().contains("dragon");
	}

	static void markSlain(MinecraftServer server) {
		worldSlain = true;
		server.overworld().setAttached(WORLD_SLAIN, true);
		JaymosKingdoms.LOGGER.info("A dragon was slain: dragons may come to the Overworld now");
	}

	/** {@code player} had a hand in killing a {@code species}. */
	static void slain(Player player, String species) {
		List<String> slain = player.getAttached(SLAIN);
		if (slain != null && slain.contains(species)) {
			return;
		}
		List<String> now = slain == null ? new ArrayList<>() : new ArrayList<>(slain);
		now.add(species);
		player.setAttached(SLAIN, now);
	}

	/** {@code player} tamed a wind dragon. */
	static void tamed(Player player) {
		player.setAttached(TAMER, true);
	}

	/** How many kinds of wind dragon {@code player} has beaten. */
	public static int beaten(Player player) {
		List<String> slain = player.getAttached(SLAIN);
		return slain == null ? 0 : slain.size();
	}

	public static boolean hasBeaten(Player player, String species) {
		List<String> slain = player.getAttached(SLAIN);
		return slain != null && slain.contains(species);
	}

	public static boolean hasTamed(Player player) {
		return Boolean.TRUE.equals(player.getAttached(TAMER));
	}

	/**
	 * Whether a disguised dragon shows itself to {@code player} whatever they've beaten: wind control, Arsenal, Chrono or
	 * Darktide (Jaymo's Powers), a tamed dragon, or an earned heritage woken (not the Hollow).
	 */
	public static boolean trusted(Player player) {
		String power = PowersCompat.power(player);
		if (power != null && TRUSTED_POWERS.contains(power) || hasTamed(player)) {
			return true;
		}
		for (Heritage.Kind kind : Heritage.Kind.values()) {
			// (0.7.490) not the Hollow: dying once to the undead is no deed a dragon heeds
			if (kind.earned && kind != Heritage.Kind.HOLLOW && Heritage.unlocked(player, kind)) {
				return true;
			}
		}
		return false;
	}
}
