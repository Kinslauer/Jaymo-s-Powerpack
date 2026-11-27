package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.BetterCombatWeapons;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.freerun.CoreMotion;
import com.jaymospowers.freerun.Freerun;
import com.jaymospowers.power.darktide.DarktideWeapons;
import com.jaymospowers.stamina.Feathers;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Draw stance follows normal katana hits, leaving targeting, reach, damage and item wear to combat. */
public final class KatanaForms {
	private static final int CHARGE_TICKS = 12, MAX_HOLD = 60, DRAW_WINDOW = 35, COMBO_WINDOW = 40;
	private static final int DRAW_COST = 4;
	private static final double LAUNCH = 0.6, DASH = 0.56, SPIKE = -0.8;
	private static final String[] GROUND = {"slash_1", "slash_2", "slash_3_launcher", "slash_4_finisher"};
	private static final int[] GROUND_TICKS = {7, 8, 9, 10};
	private static final String[] AIR = {"air_slash_1", "air_slash_2", "air_spike"};
	private static final int[] AIR_TICKS = {8, 8, 10};

	private static final AttachmentType<Stance> STANCE = AttachmentRegistry.create(JaymosPowers.id("katana_stance"));

	private static final class Stance {
		final ItemStack blade;
		final long entered;
		long pressed = -1, drawUntil, nextCut, lastCut;
		int ground, air;

		Stance(ItemStack blade, long entered) {
			this.blade = blade;
			this.entered = entered;
		}
	}

	public record Key(boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("katana_form"));
		private static final StreamCodec<ByteBuf, Key> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, Key::down, Key::new);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	private KatanaForms() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> key(ctx.player(), payload.down()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!blocked && taken > 0 && source.is(DamageTypes.PLAYER_ATTACK)
				&& source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
				hit(player, victim, source);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Stance stance = player.getAttached(STANCE);
				if (stance == null) {
					continue;
				}
				long now = player.level().getGameTime();
				if (stance.blade != player.getMainHandItem() || !player.isAlive() || player.isSpectator()) {
					player.removeAttached(STANCE);
					CoreMotion.Clip clip = player.getAttached(CoreMotion.CLIP);
					if (clip != null && clip.animation().getPath().startsWith("katana_form.")) {
						player.removeAttached(CoreMotion.CLIP);
					}
				} else if (stance.pressed >= 0 && now - stance.pressed >= MAX_HOLD) {
					key(player, false);
				}
			}
		});
	}

	public static boolean isKatana(ItemStack stack) {
		return DarktideWeapons.isKatana(stack) || "katana".equals(BetterCombatWeapons.category(stack));
	}

	public static boolean ready(Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isUsingItem() && Freerun.canMove(player)
			&& isKatana(player.getMainHandItem()) && KingdomsCompat.knowsForms(player);
	}

	private static void key(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		Stance stance = player.getAttached(STANCE);
		if (!down && stance != null && stance.pressed >= 0 && !ready(player)) {
			// Let go while blocking, eating or stunned: drop the charge quietly, no draw armed.
			stance.pressed = -1;
			player.removeAttached(CoreMotion.CLIP);
			return;
		}
		if (!ready(player) || stance != null && stance.blade != player.getMainHandItem()) {
			return;
		}
		if (stance == null) {
			if (down) {
				player.setAttached(STANCE, new Stance(player.getMainHandItem(), now));
				play(player, "stance_swap", 7);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.katana_on"));
			}
		} else if (down) {
			if (stance.pressed < 0 && now > stance.entered && now >= stance.nextCut) {
				stance.pressed = now;
				stance.drawUntil = 0;
				play(player, "draw_charge", MAX_HOLD);
			}
		} else if (stance.pressed >= 0) {
			long held = now - stance.pressed;
			stance.pressed = -1;
			if (held < CHARGE_TICKS) {
				player.removeAttached(STANCE);
				play(player, "stance_swap", 7);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.katana_off"));
			} else {
				player.removeAttached(CoreMotion.CLIP);
				if (Feathers.spend(player, DRAW_COST)) {
					stance.drawUntil = now + DRAW_WINDOW;
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.katana_draw"));
				}
			}
		}
	}

	private static void hit(ServerPlayer player, LivingEntity victim, net.minecraft.world.damagesource.DamageSource source) {
		Stance stance = player.getAttached(STANCE);
		long now = player.level().getGameTime();
		if (stance == null || stance.blade != player.getMainHandItem() || stance.pressed >= 0
			|| now < stance.nextCut || !ready(player)) {
			return;
		}
		if (now - stance.lastCut > COMBO_WINDOW) {
			stance.ground = 0;
			stance.air = 0;
		}
		stance.lastCut = now;
		if (now < stance.drawUntil) {
			stance.drawUntil = 0;
			cut(player, stance, now, "draw_slash", 9);
		} else if (!player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.getAbilities().flying) {
			int step = player.isShiftKeyDown() ? 2 : stance.air;
			stance.air = (step + 1) % AIR.length;
			cut(player, stance, now, AIR[step], AIR_TICKS[step]);
			if (step == 2) {
				vertical(victim, SPIKE);
			}
		} else if (player.isSprinting()) {
			cut(player, stance, now, "dash_slash", 8);
			Vec3 push = Vec3.directionFromRotation(0, player.getYRot()).scale(DASH);
			Vec3 motion = new Vec3(push.x, player.getKnownMovement().y, push.z);
			player.setDeltaMovement(motion);
			player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
		} else {
			int step = stance.ground;
			stance.ground = (step + 1) % GROUND.length;
			cut(player, stance, now, GROUND[step], GROUND_TICKS[step]);
			if (step == 2) {
				vertical(victim, LAUNCH);
			} else if (step == 3) {
				victim.knockback(0.55, player.getX() - victim.getX(), player.getZ() - victim.getZ(), source, 1.0F);
			}
		}
	}

	private static void vertical(LivingEntity victim, double speed) {
		Vec3 motion = victim.getDeltaMovement();
		double push = speed * (1 - victim.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
		victim.setDeltaMovement(motion.x, motion.y + push, motion.z);
		victim.needsSync = true;
		victim.syncVelocity = true;
	}

	private static void cut(ServerPlayer player, Stance stance, long now, String name, int ticks) {
		stance.nextCut = now + ticks;
		play(player, name, ticks);
	}

	private static void play(ServerPlayer player, String name, int ticks) {
		CoreMotion.play(player, "katana_form." + name, ticks);
	}
}
