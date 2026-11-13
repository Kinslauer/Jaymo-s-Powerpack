package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Paladin, sworn to an oath (mercy, justice or honour, picked with the class) and to a patron's symbol.
 *
 * <ul>
 * <li><b>Smite:</b> sneak and the class key readies a smite for {@link #READY_TICKS} ticks; the next blow of a weapon spends one of
 * {@link #SMITES} (back after a night's sleep, {@link Rests}) and hits {@link #SMITE_BONUS} harder, twice that against the dead.</li>
 * <li><b>Lay on hands:</b> the key heals who you look at (or yourself) from a pool of {@link #HANDS_POOL} health a day.</li>
 * <li><b>Aura:</b> the Paladin and friends near them ({@link #AURA_RANGE}) save better ({@link #AURA_SAVE}, {@link Saves}) and the
 * Paladin is not afraid ({@link #fearless}).</li>
 * <li><b>Oath:</b> each oath has acts that break it. Mercy: killing the helpless (the held, the fleeing, the downed, folk and pets).
 * Justice: any crime ({@link Theft}). Honour: an unprovoked blow, or a theft or pickpocket. Broken, the oath takes smite, hands and aura
 * away until atoned for, by prayer at any shrine or by a priest's rite ({@link ClassTeachers}).</li>
 * </ul>
 */
public final class Paladins {
	/** Smites a day, and health the hands can heal a day. */
	static final int SMITES = 3;
	static final float HANDS_POOL = 20.0F;
	/** How long a readied smite waits (ticks), what it adds to a blow (health) and how far the hands reach (blocks). */
	static final int READY_TICKS = 200;
	static final float SMITE_BONUS = 6.0F;
	static final double HANDS_REACH = 4.0;
	/** The aura: how far (blocks) and how much it adds to a save. */
	static final double AURA_RANGE = 10.0;
	static final float AURA_SAVE = 0.12F;
	/** The silver an atonement at a priest costs. */
	static final int ATONE_SILVER = 8;

	/** Whether the oath is broken. Saved, kept through death. */
	static final AttachmentType<Boolean> BROKEN = AttachmentRegistry.create(JaymosKingdoms.id("oath_broken"),
		b -> b.persistent(Codec.BOOL).copyOnDeath());

	private static final Map<UUID, Long> READY = new HashMap<>();

	private Paladins() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(Paladins::died);
	}

	static void clear() {
		READY.clear();
	}

	/** A new Paladin takes the oath they picked (and the symbol's making is a priest's to teach). */
	static void begin(ServerPlayer player, String oath) {
		player.removeAttached(BROKEN);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.sworn", Component.translatable("option.jaymoskingdoms.oath." + oath)));
	}

	/** The class no longer. */
	static void unmake(ServerPlayer player) {
		player.removeAttached(BROKEN);
		READY.remove(player.getUUID());
	}

	/** Whether {@code player} is a Paladin whose oath holds. */
	static boolean keeping(Player player) {
		return Classes.is(player, PlayerClass.PALADIN) && !player.getAttachedOrElse(BROKEN, false);
	}

	/** The smites a day {@code player} has, and the health of their hands (the second class has a share). */
	static int smites(Player player) {
		return Classes.scaled(player, PlayerClass.PALADIN, SMITES);
	}

	static int hands(Player player) {
		return Classes.scaled(player, PlayerClass.PALADIN, (int) HANDS_POOL);
	}

	static String oath(Player player) {
		return Classes.optionOf(player, PlayerClass.PALADIN);
	}

	// ---- keys ---------------------------------------------------------------------------------------------------------

	/** The class key: lay on hands; with sneak held, ready a smite. */
	static void key(ServerPlayer player, boolean sneak) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (!keeping(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.broken"));
			return;
		}
		if (sneak) {
			if (Rests.left(player, Rests.SMITE, smites(player)) <= 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.no_smite"));
				return;
			}
			READY.put(player.getUUID(), level.getGameTime() + READY_TICKS);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.smite_ready", Rests.left(player, Rests.SMITE, smites(player))));
			Fx.ring(level, player.position().add(0.0, 0.1, 0.0), 1.2F, 0xFFE9A0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.5F);
			return;
		}
		LivingEntity target = Arcana.sighted(player);
		LivingEntity who = target != null && player.distanceTo(target) <= HANDS_REACH && Restoration.friend(player, target) ? target : player;
		float missing = who.getMaxHealth() - who.getHealth();
		int left = Rests.left(player, Rests.HANDS, hands(player));
		if (missing < 0.5F) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.hands_whole"));
			return;
		}
		int give = (int) Math.min(Math.ceil(missing), left);
		if (give <= 0 || !Rests.spend(player, Rests.HANDS, give, hands(player))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.hands_spent"));
			return;
		}
		who.heal(give);
		Fx.beam(level, player.getEyePosition().add(0.0, -0.3, 0.0), who.getBoundingBox().getCenter(), 0.05F, 0xFFE9A0, 8);
		level.sendParticles(ParticleTypes.HEART, who.getX(), who.getY(1.0), who.getZ(), 5, 0.3, 0.4, 0.3, 0.0);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.hands", give, Rests.left(player, Rests.HANDS, hands(player))));
	}

	// ---- the blow -----------------------------------------------------------------------------------------------------

	/** A Paladin's blow lands on {@code target}: a readied smite spends itself on it. Called from the class perks. */
	static float struck(ServerPlayer player, LivingEntity target, ItemStack held, float amount) {
		honourCheck(player, target);
		if (!keeping(player)) {
			return amount;
		}
		Long until = READY.get(player.getUUID());
		if (until == null) {
			return amount;
		}
		READY.remove(player.getUUID());
		if (until < player.level().getGameTime() || !Rests.spend(player, Rests.SMITE, 1, smites(player))) {
			return amount;
		}
		float extra = Restoration.undead(target) ? SMITE_BONUS * 2.0F : SMITE_BONUS;
		if (player.level() instanceof ServerLevel level) {
			Fx.blast(level, target.getBoundingBox().getCenter(), 1.4F, 0xFFE9A0);
			level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.15);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.8F);
		}
		return amount + extra;
	}

	// ---- aura ---------------------------------------------------------------------------------------------------------

	/** What a Paladin's aura adds to {@code who}'s saves: {@code who} is a Paladin, or stands near one, with an oath that holds. */
	public static float auraBonus(LivingEntity who) {
		if (!(who.level() instanceof ServerLevel level) || level.players().isEmpty()) {
			return 0.0F;
		}
		if (who instanceof Player p && keeping(p)) {
			return AURA_SAVE;
		}
		if (who instanceof Enemy) {
			return 0.0F;
		}
		for (Player p : level.players()) {
			if (keeping(p) && p.distanceToSqr(who) <= AURA_RANGE * AURA_RANGE) {
				return AURA_SAVE;
			}
		}
		return 0.0F;
	}

	/** Whether {@code who} is a Paladin whose oath holds: they do not know fear. */
	public static boolean fearless(LivingEntity who) {
		return who instanceof Player p && keeping(p);
	}

	// ---- oath-breaking ------------------------------------------------------------------------------------------------

	/** The oath is broken by {@code why} (a lang key part). */
	static void breakOath(ServerPlayer player, String why) {
		if (player.getAttachedOrElse(BROKEN, false)) {
			return;
		}
		player.setAttached(BROKEN, true);
		READY.remove(player.getUUID());
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.oath_broken." + oath(player), Component.translatable("message.jaymoskingdoms.paladin.why." + why))
			.withStyle(net.minecraft.ChatFormatting.DARK_RED));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.4F);
	}

	/** A crime ({@code kind} is {@link Theft.Crime#name}) was committed by {@code player}. Called from {@link Theft}. */
	static void crime(ServerPlayer player, String crime) {
		if (!keeping(player)) {
			return;
		}
		switch (oath(player)) {
			case "justice" -> breakOath(player, "crime");
			case "honour" -> {
				if (crime.equals("PICKPOCKET") || crime.equals("TAKE") || crime.equals("CHEST") || crime.equals("STOREHOUSE") || crime.equals("SADDLEBAG")) {
					breakOath(player, "theft");
				}
			}
			default -> {
			}
		}
	}

	/** Honour: a blow on something that has not noticed you and is no monster is no fair fight. */
	private static void honourCheck(ServerPlayer player, LivingEntity target) {
		if (keeping(player) && oath(player).equals("honour") && target instanceof Mob mob && !(mob instanceof Enemy) && mob.getTarget() != player
			&& !(mob instanceof net.minecraft.world.entity.NeutralMob)) {
			breakOath(player, "unprovoked");
		}
	}

	private static void died(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !keeping(player) || !oath(player).equals("mercy")) {
			return;
		}
		boolean helpless = victim instanceof AbstractVillager || victim instanceof net.minecraft.world.entity.TamableAnimal || Fear.frightened(victim)
			|| HeldFast.held(victim) || Downed.isDowned(victim);
		if (helpless && !(victim instanceof Enemy && !Fear.frightened(victim) && !HeldFast.held(victim)) && victim != player) {
			breakOath(player, "helpless");
		}
	}

	// ---- atonement ----------------------------------------------------------------------------------------------------

	/** Atones: the oath mends. */
	static boolean atone(ServerPlayer player) {
		if (!Classes.is(player, PlayerClass.PALADIN) || !player.getAttachedOrElse(BROKEN, false)) {
			return false;
		}
		player.removeAttached(BROKEN);
		if (player.level() instanceof ServerLevel level) {
			Fx.burst(level, player.getX(), player.getY() + 1.0, player.getZ(), 0xFFE9A0, 1.2F, 24, 0.5, 0.8, 0.5);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.atoned").withStyle(net.minecraft.ChatFormatting.GOLD));
		return true;
	}

	/** Prayer at a shrine: a broken oath is mended. Called from {@link Patrons#prayed}. */
	static void prayed(ServerPlayer player) {
		atone(player);
	}

	/** A night's sleep: the smites and hands come back. */
	static void rested(ServerPlayer player) {
		if (Classes.is(player, PlayerClass.PALADIN)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.paladin.rested"));
		}
	}
}
