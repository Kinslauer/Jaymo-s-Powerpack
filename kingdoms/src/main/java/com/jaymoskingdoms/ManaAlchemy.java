package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.235) Mana and Jaymo's Powers' alchemy. Andy: "can we link alchemy? using vis and flux slowly attuning or corrupting
 * you? corruption would be anti magic, reading the mana user's intentions". Vis here is alchemy's essence, carried in
 * phials.
 * <ul>
 * <li><b>Vis attunes:</b> anyone not attuned who carries essence wears a channel in themselves, a little every few seconds
 * the more they carry ({@link #ATTUNING}); full, they're attuned as one who found it. The attuned get a trickle of mana from
 * what they carry.</li>
 * <li><b>Flux drains:</b> standing where flux has pooled ({@link #FLUX_FROM} or more in the chunk) bleeds mana away.</li>
 * <li><b>Corruption is anti-magic.</b> It eats into the most mana an attuned player can hold ({@link #left}). Corrupted far
 * enough ({@link #SIGHT}), you see magic as the attuned do, glows and the intent in every cast's ripple, attuned or not
 * ({@link #readsMagic}); and magic near you is smothered: an attuned player casting within {@link #SMOTHER_RANGE} pays
 * {@link #SMOTHER} times the mana.</li>
 * </ul>
 * All of it reads Powers by reflection ({@link PowersCompat}); without Powers' alchemy none of it happens.
 */
public final class ManaAlchemy {
	/** Attunement worn in by vis: per point of essence carried, each look ({@link #EVERY} ticks); counted up to {@link #VIS_MOST}. */
	static final float VIS_ATTUNES = 0.05F, ATTUNED_AT = 100.0F;
	static final int VIS_MOST = 60, EVERY = 100;
	/** Mana a second from vis carried, at {@link #VIS_MOST} or more. */
	static final float VIS_REGEN = 0.5F;
	/** Flux in the chunk from which it drains mana, and mana a second per point of flux. */
	static final int FLUX_FROM = 100;
	static final float FLUX_DRAIN = 1.0F / 200.0F;
	/** Corruption at which you read magic and smother it. */
	public static final int SIGHT = 25;
	/** The most mana an attuned player can hold shrinks this much a point of corruption, at most by {@link #SHRINK_MOST}. */
	static final float SHRINK_PER_POINT = 0.005F, SHRINK_MOST = 0.5F;
	static final double SMOTHER_RANGE = 6.0;
	static final float SMOTHER = 1.5F;

	/** How far vis has worn its channel in an unattuned player (0-{@link #ATTUNED_AT}). Saved. */
	public static final AttachmentType<Float> ATTUNING = AttachmentRegistry.create(JaymosKingdoms.id("mana_attuning"),
		builder -> builder.persistent(Codec.FLOAT));

	private ManaAlchemy() {
	}

	/** Every {@link #EVERY} ticks for an unattuned player: the vis they carry wears its channel. */
	static void unattunedTick(ServerPlayer player, long now) {
		if ((now + player.getId()) % EVERY != 0L) {
			return;
		}
		int vis = Math.min(VIS_MOST, PowersCompat.visCarried(player));
		if (vis <= 0) {
			return;
		}
		float worn = player.getAttachedOrElse(ATTUNING, 0.0F) + vis * VIS_ATTUNES;
		if (worn >= ATTUNED_AT) {
			player.removeAttached(ATTUNING);
			Mana.attune(player, "message.jaymoskingdoms.mana.attuned.vis");
		} else {
			player.setAttached(ATTUNING, worn);
		}
	}

	/** Mana a second from alchemy for an attuned player: vis carried feeds it, pooled flux drains it. */
	static float perSecond(ServerPlayer player) {
		float vis = Math.min(VIS_MOST, PowersCompat.visCarried(player)) / (float)VIS_MOST * VIS_REGEN;
		int flux = player.level() instanceof ServerLevel level ? PowersCompat.flux(level, player.blockPosition()) : 0;
		return vis - (flux >= FLUX_FROM ? flux * FLUX_DRAIN : 0.0F);
	}

	/** The share of their most mana corruption leaves {@code player} (1 uncorrupted). */
	static float left(Player player) {
		return 1.0F - Math.min(SHRINK_MOST, PowersCompat.corruption(player) * SHRINK_PER_POINT);
	}

	/** Whether {@code viewer} is corrupted enough to read magic (their own corruption is synced to them). */
	public static boolean readsMagic(Player viewer) {
		return PowersCompat.corruption(viewer) >= SIGHT;
	}

	/** How many times the mana a cast costs {@code caster} here: {@link #SMOTHER} near a corrupted player, else 1. */
	static float smother(ServerPlayer caster) {
		for (ServerPlayer other : ((ServerLevel)caster.level()).players()) {
			if (other != caster && other.distanceToSqr(caster) < SMOTHER_RANGE * SMOTHER_RANGE && !other.isSpectator() && readsMagic(other)) {
				return SMOTHER;
			}
		}
		return 1.0F;
	}
}
