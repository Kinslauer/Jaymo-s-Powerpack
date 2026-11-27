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

/**
 * Draw stance follows the katana's swings: each swing (a miss too) steps the combo and starts its clip, so the clip leads
 * the hit; a hit then adds that cut's launch, spike or push. Targeting and item wear stay with combat. A charged draw
 * (held, then let go, for {@link #DRAW_COST} Feathers) makes the next swing a draw slash: {@link #DRAW_REACH} more reach
 * and {@link #DRAW_DAMAGE} times the hit again on top.
 */
public final class KatanaForms {
	private static final int CHARGE_TICKS = 12, MAX_HOLD = 60, DRAW_WINDOW = 35, COMBO_WINDOW = 40;
	private static final int DRAW_COST = 4;
	/** The next swing after a charged draw reaches this far further (blocks) and hits for this share of the damage again. */
	private static final double DRAW_REACH = 1.5;
	private static final float DRAW_DAMAGE = 0.75F;
	private static final net.minecraft.resources.Identifier DRAW_REACH_ID = JaymosPowers.id("katana_draw_reach");
	private static final int KIND_GROUND = 0, KIND_AIR = 1, KIND_DASH = 2, KIND_DRAW = 3;
	/** Set while the draw's extra damage is dealt, so it doesn't count as another hit. */
	private static boolean bonusing;
	private static final double LAUNCH = 0.6, DASH = 0.56, SPIKE = -0.8;
	private static final String[] GROUND = {"slash_1", "slash_2", "slash_3_launcher", "slash_4_finisher"};
	private static final int[] GROUND_TICKS = {7, 8, 9, 10};
	private static final String[] AIR = {"air_slash_1", "air_slash_2", "air_spike"};
	private static final int[] AIR_TICKS = {8, 8, 10};

	private static final AttachmentType<Stance> STANCE = AttachmentRegistry.create(JaymosPowers.id("katana_stance"));

	private static final class Stance {
		final ItemStack blade;
		final long entered;
		long pressed = -1, drawUntil, nextCut, lastCut, cutAt = -1;
		int ground, air;
		/** The cut the last swing started (a kind and its step), and whether a hit has used it yet. */
		int kind, step;
		boolean spent = true;

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
			if (!bonusing && !blocked && taken > 0 && source.is(DamageTypes.PLAYER_ATTACK)
				&& source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
				hit(player, victim, source, taken);
			}
		});
		net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
			used(player);
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			used(player);
			return net.minecraft.world.InteractionResult.PASS;
		});
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			used(player);
			return net.minecraft.world.InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!USED.isEmpty()) {
				long time = server.overworld().getGameTime();
				USED.values().removeIf(at -> time - at > 5);
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Stance stance = player.getAttached(STANCE);
				long now = player.level().getGameTime();
				reach(player, stance != null && now < stance.drawUntil);
				if (stance == null) {
					continue;
				}
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

	private static void used(Player player) {
		if (player instanceof ServerPlayer server) {
			USED.put(server.getUUID(), server.level().getGameTime());
		}
	}

	/** The reach a charged draw lends the next swing. */
	private static void reach(ServerPlayer player, boolean on) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance range = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ENTITY_INTERACTION_RANGE);
		if (range == null || range.hasModifier(DRAW_REACH_ID) == on) {
			return;
		}
		if (on) {
			range.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(DRAW_REACH_ID, DRAW_REACH,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		} else {
			range.removeModifier(DRAW_REACH_ID);
		}
	}

	/** Game time of each player's last use of an item, block or creature (right click): its swing is not an attack. */
	private static final java.util.Map<java.util.UUID, Long> USED = new java.util.HashMap<>();

	/**
	 * A main-hand swing (from {@code SwingNoteMixin}): an attack in the air or at a creature steps the combo, hit or miss.
	 * A swing at a block (mining, or a click on one) or right after a use is not an attack, so a charged draw is never
	 * spent on one.
	 */
	public static void swung(ServerPlayer player) {
		if (player.getAttached(STANCE) == null) {
			return;
		}
		Long used = USED.get(player.getUUID());
		if (used != null && player.level().getGameTime() - used <= 1) {
			return;
		}
		if (player.pick(player.blockInteractionRange(), 1.0F, false).getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			return;
		}
		begin(player);
	}

	/** Steps the combo and starts its clip. */
	private static void begin(ServerPlayer player) {
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
		stance.cutAt = now;
		stance.spent = false;
		if (now < stance.drawUntil) {
			stance.drawUntil = 0;
			stance.kind = KIND_DRAW;
			cut(player, stance, now, "draw_slash", 9);
		} else if (!player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.getAbilities().flying) {
			int step = player.isShiftKeyDown() ? 2 : stance.air;
			stance.air = (step + 1) % AIR.length;
			stance.kind = KIND_AIR;
			stance.step = step;
			cut(player, stance, now, AIR[step], AIR_TICKS[step]);
		} else if (player.isSprinting()) {
			stance.kind = KIND_DASH;
			cut(player, stance, now, "dash_slash", 8);
			Vec3 push = Vec3.directionFromRotation(0, player.getYRot()).scale(DASH);
			Vec3 motion = new Vec3(push.x, player.getKnownMovement().y, push.z);
			player.setDeltaMovement(motion);
			player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
		} else {
			int step = stance.ground;
			stance.ground = (step + 1) % GROUND.length;
			stance.kind = KIND_GROUND;
			stance.step = step;
			cut(player, stance, now, GROUND[step], GROUND_TICKS[step]);
		}
	}

	/** A hit lands: the cut the swing started adds its launch, spike, push or the draw's extra damage. */
	private static void hit(ServerPlayer player, LivingEntity victim, net.minecraft.world.damagesource.DamageSource source, float taken) {
		Stance stance = player.getAttached(STANCE);
		if (stance == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (stance.cutAt < now - 2 || stance.spent) {
			// the hit came ahead of its swing packet (or no cut is running): the swing starts here
			begin(player);
		}
		if (stance.cutAt < now - 2 || stance.spent) {
			return;
		}
		stance.spent = true;
		switch (stance.kind) {
			case KIND_DRAW -> {
				bonusing = true;
				try {
					victim.setInvulnerableTime(0);
					victim.hurtServer(player.level(), source, taken * DRAW_DAMAGE);
				} finally {
					bonusing = false;
				}
			}
			case KIND_AIR -> {
				if (stance.step == 2) {
					vertical(victim, SPIKE);
				}
			}
			case KIND_GROUND -> {
				if (stance.step == 2) {
					vertical(victim, LAUNCH);
				} else if (stance.step == 3) {
					victim.knockback(0.55, player.getX() - victim.getX(), player.getZ() - victim.getZ(), source, 1.0F);
				}
			}
			default -> {
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
