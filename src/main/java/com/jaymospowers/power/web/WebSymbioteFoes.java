package com.jaymospowers.power.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.69) Andy: "It should also make more comments about what you're fighting." Whatever you're fighting (what you
 * last hit, or what last hit you, within {@code RECENT_TICKS}), it has something to say about: a fresh foe
 * {@code NEW_CHANCE} of the time, the same one again every {@code AGAIN_MIN}-{@code AGAIN_MAX} ticks
 * {@code AGAIN_CHANCE} of the time, never over another line (one voice at a time). The lines go by what it is
 * ({@link #kind}: undead, skeletons, creepers, spiders, endermen, slimes, the Nether's, piglins, golems, illagers,
 * villagers, players, beasts, sea things, bosses, small things, big things, anything else), each naming it.
 */
public final class WebSymbioteFoes {
	static final int CHECK_TICKS = 10, RECENT_TICKS = 100;
	static final float NEW_CHANCE = 0.75F, AGAIN_CHANCE = 0.5F;
	static final int AGAIN_MIN = 300, AGAIN_MAX = 600;
	static final double RANGE = 24.0;
	static final int LINES = 3;

	private static final class Fight {
		int foe = -1;
		long nextAt;
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	private WebSymbioteFoes() {
	}

	static void tick(ServerPlayer player, long now) {
		if (player.tickCount % CHECK_TICKS != 0) {
			return;
		}
		LivingEntity foe = foe(player);
		Fight fight = FIGHTS.computeIfAbsent(player.getUUID(), id -> new Fight());
		if (foe == null) {
			fight.foe = -1;
			return;
		}
		boolean fresh = foe.getId() != fight.foe;
		if (!fresh && now < fight.nextAt || !WebSymbiote.canSpeak(player, now)) {
			return;
		}
		fight.foe = foe.getId();
		fight.nextAt = now + AGAIN_MIN + player.getRandom().nextInt(AGAIN_MAX - AGAIN_MIN + 1);
		if (player.getRandom().nextFloat() >= (fresh ? NEW_CHANCE : AGAIN_CHANCE)) {
			return;
		}
		WebSymbiote.whisper(player, "foe." + kind(foe) + "." + (1 + player.getRandom().nextInt(LINES)), foe.getDisplayName());
	}

	/** What you're fighting: what you last hit, else what last hit you, lately and close by. */
	private static @Nullable LivingEntity foe(ServerPlayer player) {
		LivingEntity hit = player.getLastHurtMob();
		if (hit != null && hit.isAlive() && player.tickCount - player.getLastHurtMobTimestamp() <= RECENT_TICKS && hit.distanceTo(player) <= RANGE) {
			return hit;
		}
		LivingEntity by = player.getLastHurtByMob();
		if (by != null && by.isAlive() && player.tickCount - player.getLastHurtByMobTimestamp() <= RECENT_TICKS && by.distanceTo(player) <= RANGE) {
			return by;
		}
		return null;
	}

	/** Its kind, by what it is (its id, so other mods' creatures sort too). */
	static String kind(LivingEntity foe) {
		if (foe instanceof Player) {
			return "player";
		}
		String id = BuiltInRegistries.ENTITY_TYPE.getKey(foe.getType()).getPath();
		if (id.equals("ender_dragon") || id.equals("wither") || id.equals("warden") || id.equals("elder_guardian")) {
			return "boss";
		}
		if (id.contains("creeper")) {
			return "creeper";
		}
		if (id.contains("piglin") || id.contains("hoglin")) {
			return "piglin";
		}
		if (id.contains("blaze") || id.contains("ghast") || id.contains("magma") || id.contains("strider") || id.contains("wither")) {
			return "nether";
		}
		if (id.contains("skeleton") || id.contains("stray") || id.contains("bogged")) {
			return "skeleton";
		}
		if (id.contains("zombie") || id.contains("husk") || id.contains("drowned") || id.contains("zoglin") || id.contains("ghoul")) {
			return "undead";
		}
		if (id.contains("spider")) {
			return "spider";
		}
		if (id.contains("ender")) {
			return "enderman";
		}
		if (id.contains("slime")) {
			return "slime";
		}
		if (id.contains("golem")) {
			return "golem";
		}
		if (id.contains("pillager") || id.contains("vindicator") || id.contains("evoker") || id.contains("illusioner") || id.contains("ravager")
			|| id.contains("witch") || id.contains("vex")) {
			return "illager";
		}
		if (id.contains("villager") || id.contains("trader")) {
			return "villager";
		}
		if (id.contains("guardian") || id.contains("squid") || id.contains("dolphin") || id.contains("fish") || id.contains("cod")
			|| id.contains("salmon") || id.contains("shark") || id.contains("turtle")) {
			return "sea";
		}
		if (id.contains("wolf") || id.contains("bear") || id.contains("fox") || id.contains("cat") || id.contains("ocelot") || id.contains("panda")
			|| id.contains("boar") || id.contains("lion") || id.contains("tiger") || id.contains("horse") || id.contains("goat")) {
			return "beast";
		}
		float size = foe.getBbWidth() * foe.getBbWidth() * foe.getBbHeight();
		if (size < 0.3F) {
			return "small";
		}
		if (foe.getBbHeight() > 2.5F || foe.getBbWidth() > 1.4F) {
			return "big";
		}
		return "other";
	}
}
