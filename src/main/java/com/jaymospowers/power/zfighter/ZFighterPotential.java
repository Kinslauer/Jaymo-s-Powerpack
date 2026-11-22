package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Unlocked potential (Z Fighter gaps 2; Andy, 2026-11-22): a late rite that wakes the strength of a fighter's strongest
 * mastered form in his base state, without that form's look or drain. The Keeper performs it on the Lookout for the
 * Warrior-born, Earthlings, Heartlanders and the Dawnborn; everyone else must go to a Verdant elder on the Verdant World.
 * Heartlanders, the Dawnborn and Earthlings wake to {@link #KIN_BOOST} of it. The rite is held at night, once; a form counts
 * when mastered to {@link ZFighterTraining#FREE_ENTRY_MASTERY}. Unlocked, in base form his fists, speed and ki techniques
 * are that form's ({@link #statForm}).
 * <p>
 * The Verdant elder: the Verdant World's villages have no Kingdoms masters, so the eldest grown villager near a fighter
 * there is made one ({@link ZFighterMasters#TAG}), every {@link #ELDER_SCAN} ticks, if none is within {@link #ELDER_RANGE}.
 */
public final class ZFighterPotential {
	/** How much of the strongest form's edge Heartlanders, the Dawnborn and Earthlings wake to (others: all of it, 1). */
	static final float KIN_BOOST = 1.2F;
	static final int ELDER_SCAN = 200;
	static final double ELDER_RANGE = 64.0, ELDER_PICK = 48.0;

	public static final AttachmentType<Boolean> UNLOCKED = AttachmentRegistry.create(JaymosPowers.id("zfighter_potential"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Kingdoms heritages (its {@code jaymoskingdoms_heritage_} tag) the Keeper will wake, and those he wakes stronger. */
	private static final String HERITAGE_TAG = "jaymoskingdoms_heritage_";
	private static final List<String> KEEPER_HERITAGES = List.of("warriorborn", "earthling", "heartlander", "dawnborn");
	private static final List<String> KIN_HERITAGES = List.of("earthling", "heartlander", "dawnborn");

	private ZFighterPotential() {
	}

	/** From {@link ZFighterPower#init}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ZFighterPotential::tick);
	}

	static boolean unlocked(Player player) {
		return Boolean.TRUE.equals(player.getAttached(UNLOCKED));
	}

	/** His Kingdoms heritage's key, or null (no Kingdoms, or none picked). */
	private static String heritage(Player player) {
		for (String tag : player.entityTags()) {
			if (tag.startsWith(HERITAGE_TAG)) {
				return tag.substring(HERITAGE_TAG.length());
			}
		}
		return null;
	}

	/** Whether the Keeper (rather than a Verdant elder) holds his rite. */
	static boolean keepersOwn(Player player) {
		String heritage = heritage(player);
		if (heritage != null) {
			return KEEPER_HERITAGES.contains(heritage);
		}
		int lineage = ZFighterLineage.of(player);
		return lineage == ZFighterLineage.EARTHLING || lineage == ZFighterLineage.WARRIOR;
	}

	/** How much of the strongest form's edge he wakes to. */
	static float boost(Player player) {
		String heritage = heritage(player);
		boolean kin = heritage != null ? KIN_HERITAGES.contains(heritage) : ZFighterLineage.of(player) == ZFighterLineage.EARTHLING;
		return kin ? KIN_BOOST : 1.0F;
	}

	/** His strongest form mastered to free entry (by fists), or 0 if none is. */
	static int strongest(Player player) {
		int best = 0;
		float mastered = ZFighterTraining.FREE_ENTRY_MASTERY / (float)ZFighterTraining.MASTERY_MAX;
		for (int form = 1; form < ZFighterPower.FORM_FIST.length; form++) {
			if (ZFighterTraining.mastery(player, form) >= mastered && ZFighterPower.FORM_FIST[form] > ZFighterPower.FORM_FIST[best]) {
				best = form;
			}
		}
		return best;
	}

	/** The form whose strength he has now: his own form, or in base with his potential unlocked, his strongest. */
	static int statForm(Player player, ZFighterPower.Live live) {
		return live.form > 0 || !unlocked(player) ? live.form : strongest(player);
	}

	/** The edge on top of {@link #statForm}'s: {@link #boost} when it is his woken potential, else 1. */
	static float edge(Player player, ZFighterPower.Live live) {
		return statForm(player, live) != live.form ? boost(player) : 1.0F;
	}

	/** Whether {@code player} can ask this one ({@code keeper}: the Keeper; else a Verdant elder) for the rite now. */
	static boolean canAsk(ServerPlayer player, boolean keeper) {
		return Powers.has(player, ZFighterPower.INSTANCE) && !unlocked(player) && keepersOwn(player) == keeper;
	}

	/** The rite: at night, with a form mastered, his potential wakes. */
	static void rite(ServerPlayer player, LivingEntity elder) {
		ServerLevel level = player.level();
		if (strongest(player) == 0) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_potential_unready").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!level.isDarkOutside()) {
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_potential_night").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(UNLOCKED, true);
		level.sendParticles(MagicFx.LIGHT, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 1.0, 0.5, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_potential_woken").withStyle(ChatFormatting.AQUA));
		ZFighterPower.refresh(player, ZFighterPower.live(player));
	}

	/** The rite as a talk option ({@code keeper}: on the Keeper's talk; else a Verdant elder's). */
	static ZFighterNpcs.Option option(ServerPlayer player, LivingEntity elder, boolean keeper, Runnable back) {
		return ZFighterNpcs.Option.of("talk.jaymospowers.zfighter.potential", canAsk(player, keeper),
			() -> ZFighterNpcs.reply(player, elder, () -> rite(player, elder), back));
	}

	/** Whether this master is a Verdant elder (a master on the Verdant World). */
	static boolean verdantElder(LivingEntity master) {
		return master.level().dimension() == ZFighterSites.VERDANT_WORLD;
	}

	/** On the Verdant World, near each fighter: an elder to hold the rite, if none is near. */
	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(ZFighterSites.VERDANT_WORLD);
		if (level == null || level.getGameTime() % ELDER_SCAN != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (!Powers.has(player, ZFighterPower.INSTANCE) || player.isSpectator()) {
				continue;
			}
			if (!level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ELDER_RANGE),
				e -> e.isAlive() && e.entityTags().contains(ZFighterMasters.TAG)).isEmpty()) {
				continue;
			}
			AABB around = player.getBoundingBox().inflate(ELDER_PICK);
			level.getEntitiesOfClass(Villager.class, around, v -> v.isAlive() && !v.isBaby())
				.stream().max(Comparator.comparingLong(v -> ZFighterKingdoms.grownDays(level, v)))
				.ifPresent(elder -> {
					elder.addTag(ZFighterMasters.TAG);
					elder.setPersistenceRequired();
				});
		}
	}
}
