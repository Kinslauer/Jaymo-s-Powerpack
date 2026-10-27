package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.48, Andy's NOEA picks) The golden Great Ape. A Z Fighter who has gone Super ({@link ZFighterTraining#formsOpen} of
 * 1 or more) and turns Great Ape under the moon is a golden ape: gold fur ({@code zfighter_great_ape_golden.png}, the
 * client swaps the skin), {@link #FISTS} harder fists and {@link #HEALTH} more health on the ape's own, a little faster
 * ({@link #SPEED} less lumbering), and the ape keeps a bit more of his mind: its rage builds at {@link #RAGE_FACTOR} of
 * the usual speed ({@link ApeRage}). It is also a step towards Primal Super: every fit he wins as the golden ape counts
 * {@link #FIT_WEIGHT} towards {@link ApeRage#SUPER_4_FITS} instead of one (so three wins, not five). Never the wild ape
 * (no Z Fighter), and never before Super. Decided as he turns; gone when he turns back.
 */
public final class ZFighterGoldenApe {
	/** More fists (a share, on top of the ape's), more health, less of the ape's lumbering (a share of walking speed regained). */
	static final double FISTS = 0.5;
	static final double HEALTH = 20.0;
	static final double SPEED = 0.15;
	/** The rage builds this fast (a share of the usual) while golden. */
	static final float RAGE_FACTOR = 0.5F;
	/** A fit won counts this many towards Primal Super. */
	static final int FIT_WEIGHT = 2;
	/** Gold motes off the ape: every this many ticks, this many, and a spark in this many. */
	static final int MOTE_EVERY = 4, MOTES = 4, SPARK_ODDS = 3;
	static final int COLOR = 0xFFD54A;

	private static final Identifier FIST_ID = JaymosPowers.id("zfighter_golden_ape_fists");
	private static final Identifier HEALTH_ID = JaymosPowers.id("zfighter_golden_ape_health");
	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_golden_ape_speed");

	/** He is the golden ape right now, for everyone's client (the fur). Not saved. */
	public static final AttachmentType<Boolean> GOLDEN = AttachmentRegistry.create(JaymosPowers.id("zfighter_golden_ape"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private ZFighterGoldenApe() {
	}

	static void init() {
	}

	public static boolean golden(Player player) {
		return Boolean.TRUE.equals(player.getAttached(GOLDEN));
	}

	/** He turned Great Ape ({@link ZFighterLineage}): golden if he is a Z Fighter who has gone Super. */
	static void began(ServerPlayer player) {
		if (!ZFighterLineage.wild(player) && ZFighterTraining.formsOpen(player) >= 1) {
			player.setAttached(GOLDEN, true);
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.zfighter_ape_golden")
				.withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.ITALIC));
		}
	}

	/** He turned back. */
	static void ended(ServerPlayer player) {
		player.removeAttached(GOLDEN);
	}

	/** What the ape's rage builds at (1 for the ordinary ape). */
	static float rageFactor(Player player) {
		return golden(player) ? RAGE_FACTOR : 1.0F;
	}

	/** What a fit won counts for towards Primal Super. */
	static int fitWeight(Player player) {
		return golden(player) ? FIT_WEIGHT : 1;
	}

	/** Part of {@link ZFighterPower#refresh}: the golden ape's extra strength, health and pace. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		boolean gold = golden(player) && ZFighterLineage.ape(player);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, gold ? FISTS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, gold ? HEALTH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, gold ? SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Every tick while golden: gold motes and now and then a spark, scaled by how grown he is. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (!golden(player)) {
			return;
		}
		if (!ZFighterLineage.ape(player)) {
			ended(player);
			return;
		}
		ServerLevel level = player.level();
		if (level.getGameTime() % MOTE_EVERY != 0 || live.apeSize < 0.2F) {
			return;
		}
		double w = player.getBbWidth() * 0.5, h = player.getBbHeight();
		level.sendParticles(new DustParticleOptions(COLOR, 2.5F), true, false, player.getX(), player.getY() + h * 0.5, player.getZ(), MOTES, w, h * 0.4, w, 0.02);
		if (player.getRandom().nextInt(SPARK_ODDS) == 0) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + h * 0.5, player.getZ(), 2, w, h * 0.4, w, 0.1);
		}
	}
}
