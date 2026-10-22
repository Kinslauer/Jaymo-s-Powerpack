package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.11.101) The races' own left-mouse holds, for anyone of that blood (Z Fighter or not), bare-handed and not at a
 * block: a Verdant's <b>stretch punch</b>, the arm shooting out up to {@link #STRETCH_RANGE} blocks to strike what it
 * meets or snatch an item back; a Frost-born's <b>ice prison</b> (Jaymo's Kingdoms' FrostPrison, asked for through the
 * {@link #FROST_TAG} tag), and once that's spent for the day a thin white beam ({@link FrostBeam}); (0.14.0) an Emberborn's <b>Kindle</b>, a held stream of flame ({@code power.ember.EmberKindle},
 * started here). A Z Fighter's hold combo keeps anything within plain reach; a Verdant Z Fighter's whole hold
 * is his combo, which stretches ({@link ZFighterStretchCombo}).
 */
public final class RaceMoves {
	/**
	 * How far the arm stretches (blocks), how long one punch takes out and back, and how often a held button asks
	 * again (ticks). {@code STRETCH_COOLDOWN}: the cooldown after each stretch (ticks; 2 s), so a Z Fighter's hold
	 * combo, which the stretch is part of, can't spam it.
	 */
	public static final double STRETCH_RANGE = 6.0;
	public static final int STRETCH_TICKS = 6, STRETCH_EVERY = 10, STRETCH_COOLDOWN = 40;
	/** Kingdoms reads this off a Frost-born who held left mouse at something, and locks it in ice (it takes the tag off). */
	public static final String FROST_TAG = "jaymoskingdoms_frost_prison";

	/** A stretch under way: when it began (game time) and how far out the hand goes (blocks). Seen by all, not saved. */
	public record Stretch(long start, float reach) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Stretch> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Stretch::start, ByteBufCodecs.FLOAT, Stretch::reach, Stretch::new);
	}

	public static final AttachmentType<Stretch> STRETCH = AttachmentRegistry.create(JaymosPowers.id("verdant_stretch"),
		builder -> builder.syncWith(Stretch.CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: the race's hold move went off. */
	public record Hold() implements CustomPacketPayload {
		public static final Type<Hold> TYPE = new Type<>(JaymosPowers.id("race_hold"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Hold> CODEC = StreamCodec.unit(new Hold());

		@Override
		public Type<Hold> type() {
			return TYPE;
		}
	}

	private RaceMoves() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Hold.TYPE, Hold.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Hold.TYPE, (payload, context) -> hold(context.player()));
		// (0.14.0) an Emberborn's Kindle, a held stream with its own packets (power.ember)
		com.jaymospowers.power.ember.EmberKindle.init();
		FrostBeam.init();
		// (0.15.72) a Stormborn's Thunderclap and a Dawnborn's Sunflare, charged holds (power.heritage)
		com.jaymospowers.power.heritage.HeritageMoves.init();
	}

	private static void hold(ServerPlayer player) {
		if (!player.isAlive() || player.isSpectator() || !player.getMainHandItem().isEmpty() || ZFighterLineage.ape(player)) {
			return;
		}
		int lineage = ZFighterLineage.of(player);
		if (lineage == ZFighterLineage.VERDANT) {
			// (a Verdant Z Fighter's hold is his combo, which stretches: ZFighterStretchCombo)
			if (!com.jaymospowers.core.Powers.has(player, ZFighterPower.INSTANCE)) {
				stretch(player);
			}
		} else if (lineage == ZFighterLineage.FROST) {
			// (0.15.70) the ice prison once a day, the thin white beam after (FrostBeam)
			FrostBeam.hold(player);
		}
	}

	/** The arm shoots out: the first living thing or item along the look within reach is struck or snatched. */
	private static void stretch(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Stretch last = player.getAttached(STRETCH);
		if (last != null && now - last.start() < STRETCH_COOLDOWN && now >= last.start()) {
			// still on its cooldown
			return;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = eye.add(look.scale(STRETCH_RANGE));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = wall.getType() == HitResult.Type.MISS ? STRETCH_RANGE : wall.getLocation().distanceTo(eye);
		end = eye.add(look.scale(reach));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0),
			e -> e != player && !e.isSpectator() && (e instanceof LivingEntity living && living.isAlive() && e.isPickable() || e instanceof ItemEntity),
			reach * reach);
		Entity target = hit == null ? null : hit.getEntity();
		if (target != null) {
			reach = hit.getLocation().distanceTo(eye);
		}
		player.setAttached(STRETCH, new Stretch(now, (float)reach));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.8F, 1.6F);
		if (target instanceof ItemEntity item) {
			// snatched back into the hand's reach: picked up at once
			item.setPos(player.getX(), player.getY() + 0.5, player.getZ());
			item.setDeltaMovement(Vec3.ZERO);
			item.setNoPickUpDelay();
			item.playerTouch(player);
		} else if (target != null) {
			player.attack(target);
			player.resetAttackStrengthTicker();
		}
	}

	/** How far out a stretching arm is now, 0-1 of its reach (out, then back), or -1 when it isn't stretching. */
	public static float stretchOut(Stretch stretch, long now, float partial) {
		float t = (now - stretch.start() + partial) / STRETCH_TICKS;
		if (t < 0.0F || t >= 1.0F) {
			return -1.0F;
		}
		return (float)Math.sin(t * Math.PI);
	}
}
