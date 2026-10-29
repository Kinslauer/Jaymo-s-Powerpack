package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * (0.7.332) Accuse at the priest: "Accuse someone here", a talk option on a cleric's MCA menu (Andy: "this should be one
 * of the context sensitive talk options"), shown only while another player stands within {@link #RANGE} blocks. The
 * nearest one is the accused, and the priest throws holy water at them (splash, sound).
 *
 * <ul>
 * <li>If the accused is a vampire or a cursed werewolf, they're exposed to the realm until dawn ({@link #exposed}: guards
 * go for them as for a vampire seen hunting, villagers run from a vampire), everyone near the priest suspects them
 * ({@link Suspicion}), and the accuser gains {@link #GAIN} reputation with the realm the priest lives under.</li>
 * <li>If not, the accuser loses {@link #LOSS} reputation, and the priests won't hear them again for a day
 * ({@link #DEAF_TICKS}).</li>
 * </ul>
 *
 * Both players get a line on their action bar; neither is told in chat.
 */
public final class Accusations {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The talk option ({@code McaAnswersMixin}, {@code McaDialogueMixin}). */
	public static final String ANSWER = "jaymoskingdoms_accuse";
	/** The accused is the nearest other player within this (blocks). */
	static final double RANGE = 10.0;
	/** Reputation won for a true accusation, lost for a false one. */
	static final int GAIN = 10, LOSS = 15;
	/** After a false accusation no priest hears them for this long (ticks: a day). */
	static final long DEAF_TICKS = 24000L;
	/** Those near the priest (blocks) take a true accusation to heart. */
	static final double WORD_RANGE = 24.0;
	/** The priest's cry carries this far (blocks). */
	static final double CRY_RANGE = 24.0;

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** On the accuser: the game time until which no priest hears them. Kept. */
	static final AttachmentType<Long> DEAF_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("priest_deaf_until"),
		builder -> builder.persistent(Codec.LONG));

	/** Not kept: the accused who are exposed, and the game time until which. */
	private static final Map<UUID, Long> EXPOSED = new HashMap<>();

	private Accusations() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> EXPOSED.clear());
	}

	/** Whether the talk option shows: a priest, and someone here to accuse. */
	public static boolean canAccuse(ServerPlayer player, Entity who) {
		return priest(who) && nearest(player) != null;
	}

	/** A grown cleric. */
	static boolean priest(Entity e) {
		return e instanceof Villager v && v.isAlive() && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.CLERIC);
	}

	/** Whether the priest has named {@code e} to the realm, and it's not yet dawn. */
	public static boolean exposed(Entity e) {
		Long until = EXPOSED.get(e.getUUID());
		return until != null && e.isAlive() && e.level().getGameTime() < until;
	}

	/** Named as a werewolf (a guard's quarry, as an exposed vampire is). */
	public static boolean wolfExposed(Entity e) {
		return exposed(e) && Lycans.cursed(e);
	}

	/** Whether {@code p} is what the priest would find: a vampire (not a dhampir), or a werewolf cursed in full. */
	private static boolean guilty(ServerPlayer p) {
		return Vampires.isVampire(p) && !VampirePlayers.dhampir(p) || FeralCurse.stillCursed(p);
	}

	private static ServerPlayer nearest(ServerPlayer accuser) {
		ServerPlayer best = null;
		double d = RANGE * RANGE;
		for (ServerPlayer p : accuser.level().players()) {
			double s = p.distanceToSqr(accuser);
			if (p != accuser && p.isAlive() && !p.isSpectator() && s <= d) {
				d = s;
				best = p;
			}
		}
		return best;
	}

	/** Ticks till the next dawn (this night's, or the coming night's). */
	static long untilDawn(long clock) {
		long tod = Math.floorMod(clock, 24000L);
		return tod < Vampires.NIGHT_TO ? Vampires.NIGHT_TO - tod : 24000L - tod + Vampires.NIGHT_TO;
	}

	/** The talk option chosen. */
	public static void accuse(ServerPlayer accuser, Villager priest) {
		accuse((ServerLevel) priest.level(), accuser, priest);
	}

	private static void accuse(ServerLevel level, ServerPlayer accuser, Villager priest) {
		long now = level.getGameTime();
		if (now < accuser.getAttachedOrElse(DEAF_UNTIL, 0L)) {
			accuser.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.accuse.deaf").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			priest.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
			return;
		}
		ServerPlayer accused = nearest(accuser);
		if (accused == null) {
			accuser.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.accuse.nobody").withStyle(ChatFormatting.GRAY));
			return;
		}
		priest.getLookControl().setLookAt(accused);
		priest.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.sendParticles(ParticleTypes.SPLASH, accused.getX(), accused.getY(1.0), accused.getZ(), 40, 0.35, 0.6, 0.35, 0.1);
		level.sendParticles(ParticleTypes.END_ROD, accused.getX(), accused.getY(1.0), accused.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, accused.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0F, 1.0F);
		Faction f = Theft.lawAt(level, priest.blockPosition());
		Component name = Recollections.nameTo(accused, priest);
		boolean vampire = Vampires.isVampire(accused) && !VampirePlayers.dhampir(accused);
		if (guilty(accused)) {
			EXPOSED.put(accused.getUUID(), now + untilDawn(level.getOverworldClockTime()));
			Suspicion.witnessed(level, accused, vampire ? Suspicion.VAMPIRE : Suspicion.WOLF, Suspicion.MAX, WORD_RANGE, true);
			if (f != null) {
				Reputation.change(accuser, f, GAIN);
			}
			Speech.say(level, priest, Component.translatable("talk.jaymoskingdoms.accuse.guilty." + (vampire ? "vampire" : "wolf"), name), Speech.RED, CRY_RANGE);
			accuser.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.accuse.right", name).withStyle(ChatFormatting.GOLD));
			accused.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.accuse.burned").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		} else {
			accuser.setAttached(DEAF_UNTIL, now + DEAF_TICKS);
			if (f != null) {
				Reputation.change(accuser, f, -LOSS);
			}
			Speech.say(level, priest, Component.translatable("talk.jaymoskingdoms.accuse.wrong", name), Speech.YELLOW, CRY_RANGE);
			accuser.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.accuse.wrong", name).withStyle(ChatFormatting.RED));
			accused.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.accuse.splashed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}
}
