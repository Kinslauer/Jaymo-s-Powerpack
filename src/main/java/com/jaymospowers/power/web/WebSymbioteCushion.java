package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.13.4) The symbiote won't let a fall kill you. The moment you're falling hard enough that the landing would, it
 * pools over your legs from the feet up (everyone sees it; {@code WebSuitGrowth}) and warns you; when you land it takes
 * the blow, and you're left hurt but alive ({@link #LEFT_HEALTH}). It holds whether or not the suit is on (the suit and
 * the big body take no fall damage at all), whatever its mood: it wants to live. Fall damage still hurts, it only
 * won't kill.
 * <p>
 * {@link #CUSHION} is synced: above zero, the game tick it began pooling; below zero, minus the tick it began draining
 * back off after the landing (or after the fall was broken some other way).
 */
public final class WebSymbioteCushion {
	/** Falling at least this far, this fast (blocks a tick, downward), before it looks at the landing. */
	public static final double MIN_FALL = 4.0, MIN_SPEED = 0.6;
	/** How far below it looks for the ground (blocks). */
	public static final double LOOK_DOWN = 320.0;
	/** What it leaves you with (health points: a heart). */
	public static final float LEFT_HEALTH = 2.0F;
	/** Pooling up the legs, and draining back off after (ticks); the client draws the same spans. */
	public static final int POOL_TICKS = 12, DRAIN_TICKS = 24;
	/** How many lines it has for each (lang {@code message.jaymospowers.web_symbiote.cushion.warn.N}, {@code .caught.N}). */
	static final int WARN_LINES = 3, CAUGHT_LINES = 3;

	public static final AttachmentType<Long> CUSHION = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_cushion"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private WebSymbioteCushion() {
	}

	static void register() {
		// a fall that would kill a bonded host: it takes the blow
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL) || !WebSymbiote.isBonded(player)) {
				return true;
			}
			player.setHealth(LEFT_HEALTH);
			caught(player);
			return false;
		});
	}

	/** Each tick for a bonded host: pools over the legs as a deadly fall begins, drains off once it's over. */
	static void tick(ServerPlayer player, long now) {
		Long cushion = player.getAttached(CUSHION);
		if (cushion != null && cushion < 0L) {
			if (now + cushion > DRAIN_TICKS) {
				player.removeAttached(CUSHION);
			}
			return;
		}
		boolean deadly = deadly(player);
		if (cushion == null && deadly) {
			player.setAttached(CUSHION, now);
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 1.2F);
			WebSymbiote.whisper(player, "cushion.warn." + (1 + player.getRandom().nextInt(WARN_LINES)));
		} else if (cushion != null && (player.onGround() || player.isInWater() || player.fallDistance < 1.0)) {
			// down (or the fall broken some other way: a web, water, a glide): it drains back off
			player.setAttached(CUSHION, -now);
		}
	}

	/** Falling hard toward ground that would kill them on landing (not into water, not gliding or drifting down). */
	static boolean deadly(ServerPlayer player) {
		if (player.onGround() || player.isInWater() || player.isFallFlying() || player.isCreative() || player.isSpectator()
			|| player.hasEffect(MobEffects.SLOW_FALLING) || player.hasEffect(MobEffects.LEVITATION)
			|| player.fallDistance < MIN_FALL || player.getDeltaMovement().y > -MIN_SPEED) {
			return false;
		}
		Vec3 feet = player.position();
		BlockHitResult hit = player.level().clip(new ClipContext(feet, feet.subtract(0.0, LOOK_DOWN, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.ANY, player));
		if (hit.getType() == HitResult.Type.MISS || !player.level().getFluidState(hit.getBlockPos()).isEmpty()) {
			return false;
		}
		double drop = feet.y - hit.getLocation().y;
		double fall = player.fallDistance + drop - player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
		float damage = Mth.ceil(fall * player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
		return damage >= player.getHealth() + player.getAbsorptionAmount();
	}

	/** The landing it took for you: goo splashing out from under you, and it lets you know. */
	private static void caught(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		player.setAttached(CUSHION, -now);
		player.resetFallDistance();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(WebSymbiote.dust(1.8F), player.getX(), player.getY() + 0.1, player.getZ(), 40, 0.9, 0.1, 0.9, 0.02);
		WebSymbiote.whisper(player, "cushion.caught." + (1 + player.getRandom().nextInt(CAUGHT_LINES)));
	}
}
