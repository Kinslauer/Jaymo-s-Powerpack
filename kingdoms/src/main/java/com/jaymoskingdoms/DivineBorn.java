package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.262, Andy's NOEA picks) Divine-born, the celestial blood: pale gold skin with a halo over the head and marks that
 * glow faintly ({@code client/HeritageLooks}). They mend faster than anyone ({@link #REGEN_HEAL} every {@link #REGEN_EVERY}
 * ticks on top of the body's own), their blows smite the wicked ({@link #smite}: the undead, illagers, barbarians, a
 * Rogue (a Stealth alignment) or anyone whose power or gift has been corrupted), and healing magic loves them
 * ({@link #heal}, {@link #spellCost}: Restoration's spells and the Sorcerer's Mend, Renewal and Rekindle heal more and
 * cost less mana). Their flaw is the light itself: it burns bright, so they hunger {@link #HUNGER} times as fast
 * ({@link Heritage#exhaustion}). Jaymo's Powers reads the heritage tag ({@code jaymoskingdoms_heritage_divine}) to hide them from ki sense
 * and scouters. Not a Z Fighter lineage: it reads as Earthling there.
 */
public final class DivineBorn {
	/** The extra mending: health every this many ticks (on top of the body's own), while fed and not downed. */
	static final float REGEN_HEAL = 1.0F;
	static final int REGEN_EVERY = 80;
	/** Hunger drains this many times as fast (the flaw). */
	static final float HUNGER = 1.25F;
	/** Smite: the share more their blows do to the wicked. */
	static final float SMITE = 0.25F;
	/** Healing magic: the share more it heals when they cast it, and the share off its mana cost. */
	static final float HEAL_BONUS = 0.3F, SPELL_DISCOUNT = 0.25F;
	/** A mote of light off the skin when they mend. */
	private static final DustParticleOptions MOTE = new DustParticleOptions(0xFFE9A0, 0.7F);

	private DivineBorn() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DivineBorn::tick);
	}

	static boolean is(Player player) {
		return Heritage.is(player, Heritage.Kind.DIVINE);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % REGEN_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (is(player) && player.isAlive() && !player.isSpectator() && !Downed.isDowned(player) && player.getHealth() < player.getMaxHealth()
				&& player.getFoodData().getFoodLevel() > 0 && player.level() instanceof ServerLevel level) {
				player.heal(REGEN_HEAL);
				level.sendParticles(MOTE, player.getX(), player.getY(0.7), player.getZ(), 4, 0.3, 0.5, 0.3, 0.0);
			}
		}
	}

	/**
	 * Whether {@code target} is wicked in Kingdoms' eyes: the undead, an illager, a barbarian, a Rogue (a Stealth alignment), a
	 * player whose power the storm or long use has corrupted, or a gifted villager's corrupted gift.
	 */
	static boolean wicked(LivingEntity target) {
		if (target.is(EntityTypeTags.UNDEAD) || target.is(EntityTypeTags.ILLAGER) || Illagers.isCampIllager(target)) {
			return true;
		}
		if (target instanceof Folk folk && Barbarians.isBarbarian(folk)) {
			return true;
		}
		if (target instanceof Player player) {
			return Renown.alignment(Skills.of(player)) == Skills.Pillar.STEALTH || PowersCompat.corrupted(player);
		}
		return PowersCompat.giftCorrupted(target);
	}

	/** The factor on a blow from a Divine-born {@code attacker} to {@code target}: {@code 1 + SMITE} if it's wicked. */
	static float smite(LivingEntity attacker, LivingEntity target) {
		Thralls.Tag thrall = Thralls.tag(target);
		if (target == attacker || thrall != null && attacker.getUUID().equals(thrall.master())) {
			// (never themselves, or their own risen friends)
			return 1.0F;
		}
		if (!wicked(target)) {
			return 1.0F;
		}
		if (target.level() instanceof ServerLevel level) {
			level.sendParticles(MOTE, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
		}
		return 1.0F + SMITE;
	}

	/** The factor on what a healing spell heals when {@code caster} casts it (a Divine-born's is {@code 1 + HEAL_BONUS}). */
	static float heal(@Nullable LivingEntity caster) {
		return caster instanceof Player player && is(player) ? 1.0F + HEAL_BONUS : 1.0F;
	}

	/** The factor on the mana a spell or key {@code what} costs {@code caster}: healing magic is cheaper for a Divine-born. */
	static float spellCost(Player caster, String what) {
		return is(caster) && Mana.INTENT.getOrDefault(what, Mana.HARM) == Mana.HEAL ? 1.0F - SPELL_DISCOUNT : 1.0F;
	}
}
