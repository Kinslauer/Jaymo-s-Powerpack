package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

/**
 * Puddle: the host melts flat into goo for a few seconds, in the thin suit or the big suit. Its box shrinks to
 * {@link #WIDTH} by {@link #HEIGHT} (it slips under a half slab and through a one-block gap, the eye drops with it),
 * blows aimed at a body (a swing, an arrow, a bolt) pass over it, it walks at {@link #SPEED_CUT} less than usual and can
 * neither hit nor use anything. Everyone sees the model gone and a spread of goo in its place
 * ({@code WebPuddlePose}). The key again, or the time running out, pours it back up. Fall, fire, lava, drowning, magic
 * and explosions still hurt.
 */
public final class WebPuddle {
	// ================================================================================================================
	// Tuning
	/** Longest it can be held, the shortest before the key lets go, and the wait after it (ticks). */
	public static final int MAX_TICKS = 80, MIN_HOLD = 10, COOLDOWN = 240;
	/** The flat body's box and eye height (blocks). */
	public static final float WIDTH = 0.6F, HEIGHT = 0.3F, EYE = 0.2F;
	/** Walking speed lost (a share). */
	static final double SPEED_CUT = 0.3;
	/** How long the melt and the pour take on the client's drawing (ticks). */
	public static final int SPREAD_TICKS = 6;
	// ================================================================================================================

	/** The game time the puddle began. Present while the host is flat; synced to everyone. */
	public static final AttachmentType<Long> PUDDLE = AttachmentRegistry.create(JaymosPowers.id("web_puddle"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** The box a puddle has, in every pose. */
	public static final EntityDimensions DIMENSIONS = EntityDimensions.fixed(WIDTH, HEIGHT).withEyeHeight(EYE);

	private static final Identifier SPEED_ID = JaymosPowers.id("web_puddle_speed");

	private WebPuddle() {
	}

	/** The host is a puddle. Both sides. */
	public static boolean puddled(Entity entity) {
		return entity.hasAttached(PUDDLE);
	}

	/** The game time the puddle began, or -1. Both sides. */
	public static long since(Entity entity) {
		Long since = entity.getAttached(PUDDLE);
		return since == null ? -1L : since;
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebPuddle::tick);
		// blows aimed at a body pass over a puddle; the ground's harms (fall, fire, lava, drowning), magic and blasts do not
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof Player player && puddled(player) && overhead(source)));
		// flat on the ground there is nothing to swing or use
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> puddled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> puddled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> puddled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> puddled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> puddled(player) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Blows meant for a body standing up: melee and anything shot or thrown. */
	private static boolean overhead(DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)
			|| source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.STING);
	}

	/** The key: pours back up if it is a puddle already, melts if it can. */
	static void press(ServerPlayer player) {
		if (puddled(player)) {
			if (player.level().getGameTime() - since(player) >= MIN_HOLD) {
				end(player);
			}
			return;
		}
		if (!WebGoo.ready(player, WebGoo.PUDDLE) || !WebGoo.pay(player, WebGoo.PUDDLE_COST)) {
			return;
		}
		ServerLevel level = player.level();
		player.stopRiding();
		player.setSprinting(false);
		player.setAttached(PUDDLE, level.getGameTime());
		speed(player, true);
		player.refreshDimensions();
		fx(level, player);
		WebGoo.remark(player, "goo_puddle");
	}

	private static void end(ServerPlayer player) {
		player.removeAttached(PUDDLE);
		speed(player, false);
		player.refreshDimensions();
		WebGoo.cool(player, WebGoo.PUDDLE, COOLDOWN);
		fx(player.level(), player);
	}

	private static void fx(ServerLevel level, ServerPlayer player) {
		int color = WebSymbioteStrains.color(WebSymbiote.state(player).kind());
		Vec3 at = player.position();
		MagicFx.pool(level, at, 0.7F, color, 12);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9F, 0.7F);
	}

	private static void speed(ServerPlayer player, boolean on) {
		AttributeInstance instance = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (instance == null) {
			return;
		}
		if (on) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(SPEED_ID, -SPEED_CUT, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else {
			instance.removeModifier(SPEED_ID);
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!puddled(player)) {
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || !WebSymbiote.isBonded(player)
				|| player.level().getGameTime() - since(player) >= MAX_TICKS) {
				end(player);
				continue;
			}
			if (player.isSprinting()) {
				player.setSprinting(false);
			}
		}
	}
}
