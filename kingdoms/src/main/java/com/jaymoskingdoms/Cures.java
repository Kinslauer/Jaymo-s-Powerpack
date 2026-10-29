package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Werewolf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.0) The only cure for vampirism and the wolf's curse (Andy): a rare quest from a priest. Few clerics know the old
 * rite ({@link #RITE_SHARE} of them, fixed by who they are); one who does sees what you are when you ask them for work,
 * and sets you a hunt in place of anything else they'd ask: for the bitten or turned, slay {@link #VAMPIRE_HUNT} of the
 * vampires among the living; for the wolf-cursed, slay {@link #WOLF_HUNT} of the cursed in their beast shape. Bring word
 * back and the rite is done there and then: the curse is lifted, whatever it was. Players turned into vampires or
 * beasts themselves don't count.
 */
public final class Cures {
	/** The share of clerics who know the rite. */
	static final float RITE_SHARE = 0.25F;
	/** The hunts: vampires slain, cursed beasts slain. */
	static final int VAMPIRE_HUNT = 2, WOLF_HUNT = 1;
	static final String VAMPIRE = "cure.vampire", WOLF = "cure.wolf";
	/** Quest targets ({@link Quests#matches}). */
	static final String VAMPIRE_FOE = "*vampire", WOLF_FOE = "*werewolf";

	private Cures() {
	}

	/** A cleric who knows the rite (fixed by their UUID, so the same few always do). */
	static boolean riteKeeper(LivingEntity giver) {
		if (!(giver instanceof Villager v) || v.isBaby() || !v.getVillagerData().profession().is(VillagerProfession.CLERIC)) {
			return false;
		}
		UUID id = giver.getUUID();
		long mixed = id.getLeastSignificantBits() * 0xC2B2AE3D27D4EB4FL ^ id.getMostSignificantBits();
		return Math.floorMod(mixed ^ (mixed >>> 29), 10000L) / 10000.0F < RITE_SHARE;
	}

	/** What ails {@code player} that a rite can lift: {@link #VAMPIRE}, {@link #WOLF}, or null. */
	static @Nullable String ailment(ServerPlayer player) {
		if (Vampires.isVampire(player) || player.hasAttached(Vampires.BITTEN)) {
			return VAMPIRE;
		}
		return Lycans.cursed(player) ? WOLF : null;
	}

	/** The rite's hunt, if {@code giver} knows it and {@code player} needs it (and isn't on it already); else null. */
	static @Nullable Quest offer(ServerPlayer player, LivingEntity giver, Faction f) {
		String ailment = ailment(player);
		if (ailment == null || !riteKeeper(giver) || Quests.log(player).stream().anyMatch(q -> q.template().startsWith("cure."))) {
			return null;
		}
		boolean vampire = VAMPIRE.equals(ailment);
		Quest.Giver who = new Quest.Giver(giver.getUUID(), giver.getName().getString(), f.id(), false, "cleric");
		Quest.Objective objective = new Quest.Objective(vampire ? VAMPIRE_FOE : WOLF_FOE, vampire ? VAMPIRE_HUNT : WOLF_HUNT, 0, Optional.empty(),
			Optional.empty(), "", f.name(), false);
		Quest.Reward reward = new Quest.Reward(0, List.of(), Optional.empty(), Optional.empty(), 0, 0, 0);
		return new Quest(UUID.randomUUID().toString(), Quest.SLAY, ailment, who, objective, reward, 0L);
	}

	/** Whether {@code entity} counts for a rite's hunt ({@code target} one of ours); null if {@code target} isn't ours. */
	static @Nullable Boolean matches(String target, Entity entity) {
		if (VAMPIRE_FOE.equals(target)) {
			return Vampires.isVampire(entity) && !(entity instanceof Player);
		}
		if (WOLF_FOE.equals(target)) {
			return entity instanceof Werewolf w && w.host() != null && !WerewolfPlayers.puppet(w);
		}
		return null;
	}

	/** The hunt's done: the priest performs the rite. */
	static void rite(ServerLevel level, ServerPlayer player, LivingEntity priest, Quest q) {
		if (!q.template().startsWith("cure.")) {
			return;
		}
		boolean vampire = Vampires.isVampire(player);
		BloodThralls.release(player); // (0.7.332) the rite also frees a thrall
		player.removeAttached(Vampires.VAMPIRE);
		player.removeAttached(Vampires.BITTEN);
		if (vampire) {
			// (0.7.88) a Nightborn keeps a trace of it
			NightbornBlood.cured(player);
		}
		if (Lycans.cursed(player)) {
			WerewolfPlayers.cure(player);
		}
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(1.0), player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (priest instanceof Villager v) {
			v.getLookControl().setLookAt(player);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.cure.done").withStyle(ChatFormatting.GOLD));
	}
}
