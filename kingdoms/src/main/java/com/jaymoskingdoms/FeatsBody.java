package com.jaymoskingdoms;

import com.jaymoskingdoms.Feats.Feat;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The feats that change how a body copes (see {@link Feats}):
 * <ul>
 * <li><b>Alert</b>: never surprised ({@link Surprise#addImmunity}: no frozen heartbeat from an ambush).</li>
 * <li><b>Lucky</b>: {@link #LUCKY_PER_DAY} sparks a day, spent like a held spark ({@link Sparks#rescue}: a failed lock pick or a lost nerve turns).</li>
 * <li><b>Mobile</b>: {@link #MOBILE_SPEED} faster afoot, and breaking off from a foe costs no free blow ({@link Opportunity}).</li>
 * <li><b>Tough</b>: {@link #TOUGH_BASE} more health and {@link #TOUGH_PER_LEVEL} more for each knowledge level ({@link Feats#attributes}).</li>
 * <li><b>Skulker</b>: mobs see you from {@link #SKULKER_DIM} of the distance in dim light and {@link #SKULKER_SNEAK} when you sneak ({@link Senses#factor}).</li>
 * <li><b>Observant</b>: {@link #OBSERVANT_NOTICE} more chance a second of noticing ({@link Notice}), and liars' tells seen sooner ({@link Liars#sharp}).</li>
 * <li><b>Dungeon Delver</b>: {@link #DELVER_NOTICE} more chance to notice, and traps (pressure plates, dispensers and droppers, trapped chests,
 * tripwire hooks) within {@link #DELVER_RANGE} blocks give themselves away; the blow of a trap (an arrow from a dispenser, a blast with
 * no hand behind it, a stalagmite, a falling block) lands at {@link #DELVER_TRAP_LEFT} of its strength.</li>
 * </ul>
 */
public final class FeatsBody {
	/** Tough: health points, and more per knowledge level. */
	static final double TOUGH_BASE = 4.0, TOUGH_PER_LEVEL = 1.0;
	/** Mobile: a share more speed. */
	static final double MOBILE_SPEED = 0.12;
	/** Lucky: sparks a day. */
	static final int LUCKY_PER_DAY = 3;
	/** Skulker: the share of a mob's range left in dim light (light under half), and when sneaking. */
	static final double SKULKER_DIM = 0.55, SKULKER_SNEAK = 0.8;
	/** Observant and Dungeon Delver: the extra chance a second of noticing; Observant's extra sight of a liar's tell. */
	static final float OBSERVANT_NOTICE = 0.25F, OBSERVANT_SHARP = 0.15F, DELVER_NOTICE = 0.3F;
	/** Dungeon Delver: how far (blocks) traps give themselves away, and the share of a trap's blow that lands. */
	static final int DELVER_RANGE = 8;
	static final float DELVER_TRAP_LEFT = 0.5F;

	/** The day and the sparks used that day, as day * 10 + used. Saved. */
	private static final AttachmentType<Long> LUCK = AttachmentRegistry.create(JaymosKingdoms.id("feat_luck"), b -> b.persistent(Codec.LONG));

	private FeatsBody() {
	}

	/** Skybound: in the Aether a body has this much more room to fall and this share of a shove's push left off. */
	static final double SKYBOUND_FALL = 12.0, SKYBOUND_STEADY = 0.4;
	/** Lucky's second chance: the odds a killing blow leaves {@link #SECOND_CHANCE_HEALTH} health instead, and the ticks before it can again. */
	static final float SECOND_CHANCE_ODDS = 0.2F;
	static final float SECOND_CHANCE_HEALTH = 4.0F;
	static final long SECOND_CHANCE_GAP = 24000L * 3L;
	/** The game time of a Lucky player's last second chance. Saved. */
	private static final AttachmentType<Long> SECOND_CHANCE = AttachmentRegistry.create(JaymosKingdoms.id("feat_second_chance"), b -> b.persistent(Codec.LONG));

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					boolean sky = Feats.has(p, Feat.SKYBOUND) && SkyFolk.in(p);
					Skills.modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE, "feat_skybound", sky ? SKYBOUND_FALL : 0.0,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
					Skills.modifier(p, net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE, "feat_skybound", sky ? SKYBOUND_STEADY : 0.0,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
				}
			}
		});
		// Lucky: now and then a blow that would kill leaves a sliver of health instead
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer p) || !Feats.has(p, Feat.LUCKY)) {
				return true;
			}
			long now = p.level().getGameTime();
			long last = p.getAttachedOrElse(SECOND_CHANCE, -SECOND_CHANCE_GAP);
			if (now - last < SECOND_CHANCE_GAP && now >= last || p.getRandom().nextFloat() >= SECOND_CHANCE_ODDS) {
				return true;
			}
			p.setAttached(SECOND_CHANCE, now);
			p.setHealth(SECOND_CHANCE_HEALTH);
			p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
			Fx.burst(p.level(), p.getX(), p.getY(1.2), p.getZ(), Sparks.COLOR, 0.8F, 20, 0.4, 0.5, 0.4);
			return false;
		});
		Surprise.addImmunity(who -> who instanceof ServerPlayer p && Feats.has(p, Feat.ALERT));
		Notice.add(FeatsBody::traps);
	}

	/** A Lucky player's spark for a failure just met: spent from today's three, with light and sound. */
	static boolean luck(ServerPlayer player) {
		if (!Feats.has(player, Feat.LUCKY)) {
			return false;
		}
		long day = player.level().getGameTime() / 24000L;
		long state = player.getAttachedOrElse(LUCK, -1L);
		long used = state / 10 == day ? state % 10 : 0;
		if (used >= LUCKY_PER_DAY) {
			return false;
		}
		player.setAttached(LUCK, day * 10 + used + 1);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.9F);
		Fx.burst(level, player.getX(), player.getY(1.2), player.getZ(), Sparks.COLOR, 0.5F, 8, 0.3, 0.4, 0.3);
		return true;
	}

	/** What Observant and Dungeon Delver add to the chance a second of noticing. */
	static float noticing(ServerPlayer player) {
		return (Feats.has(player, Feat.OBSERVANT) ? OBSERVANT_NOTICE : 0.0F) + (Feats.has(player, Feat.DUNGEON_DELVER) ? DELVER_NOTICE : 0.0F);
	}

	/** What Observant adds to the chance of seeing a liar's tell. */
	static float sharper(ServerPlayer player) {
		return Feats.has(player, Feat.OBSERVANT) ? OBSERVANT_SHARP : 0.0F;
	}

	/** The factor Skulker puts on how far a mob sees {@code player}, given the light at their eyes (0 to 1). */
	static double sight(ServerPlayer player, double light) {
		if (!Feats.has(player, Feat.SKULKER)) {
			return 1.0;
		}
		double f = light < 0.5 ? SKULKER_DIM : 1.0;
		return player.isCrouching() ? f * SKULKER_SNEAK : f;
	}

	/** Whether {@code mover} breaks off from a foe without a free blow (Mobile). */
	static boolean slipsAway(LivingEntity mover) {
		return mover instanceof ServerPlayer p && Feats.has(p, Feat.MOBILE);
	}

	/** Dungeon Delver's eyes: the traps near. */
	private static void traps(ServerPlayer player, List<Notice.Spot> into) {
		if (!Feats.has(player, Feat.DUNGEON_DELVER)) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-DELVER_RANGE, -2, -DELVER_RANGE), at.offset(DELVER_RANGE, 2, DELVER_RANGE))) {
			var state = level.getBlockState(p);
			if (state.is(BlockTags.PRESSURE_PLATES) || state.is(Blocks.DISPENSER) || state.is(Blocks.DROPPER) || state.is(Blocks.TRAPPED_CHEST)
				|| state.is(Blocks.TRIPWIRE_HOOK)) {
				into.add(new Notice.Spot(p.asLong() ^ 0xDE17L << 40, Vec3.atCenterOf(p), SoundEvents.TRIPWIRE_CLICK_ON));
			}
		}
	}

	/** A trap's blow on a Dungeon Delver lands softer. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof ServerPlayer p) || !Feats.has(p, Feat.DUNGEON_DELVER) || source.getEntity() != null) {
			return amount;
		}
		boolean trap = source.getDirectEntity() instanceof AbstractArrow || source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.STALAGMITE)
			|| source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE);
		return trap ? amount * DELVER_TRAP_LEFT : amount;
	}
}
