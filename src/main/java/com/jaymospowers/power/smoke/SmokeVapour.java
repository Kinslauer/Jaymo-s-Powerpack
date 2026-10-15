package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindFlight;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * R, Vapour Form: a mode you switch on and off ({@link #ARMED}). While it's on, breaking into a sprint turns everything
 * below your waist to smoke and you shoot off along your look, Wind-style (the client flies you: {@code SmokeFlightClient}).
 * The cruise holds its speed without Sprint held; S brakes, and slowed right down you hover (Sprint again to cruise).
 * R switches the mode off (and lands you); Sneak on the ground lands you but the mode stays on (Andy, 2026-10-15).
 * It costs hunger, more the faster you go, and ends when you're too hungry. Your smoke half pours a thick trail into
 * the {@link SmokeField}; arrows and other shots pass through it (blows, fire and blasts still land). Everyone sees the
 * smoke legs ({@link #FORM}).
 * <p>
 * Smoke can't put armour on in Vapour Form; what you already wear stays on until you take it off (the inventory,
 * right-click and dispenser routes are refused in {@code SmokeArmourMixin}/{@code SmokeEquipMixin}, and anything that
 * still lands in an empty armour slot, like the creative inventory, is handed straight back).
 */
public final class SmokeVapour {
	/** Smoke a tick your legs pour out (a block's worth is 1); a fresh awakening's (only this ability) is thinner. */
	static final float TRAIL = 0.6F;
	static final float STARTER_TRAIL = 0.35F;
	/** Hunger a tick, plus a tick per block moved. At or below this food you can't keep it up. */
	static final float EXHAUSTION = 0.01F;
	static final float EXHAUSTION_PER_BLOCK = 0.012F;
	static final int MIN_FOOD = 6;
	/** Standing on the ground this long (after the take-off's grace) ends it. */
	static final int GROUNDED_TICKS = 30;
	static final int TAKEOFF_GRACE = 10;

	/** On a player in Vapour Form: synced to everyone, who draw the smoke legs. */
	public static final AttachmentType<Boolean> FORM = AttachmentRegistry.create(JaymosPowers.id("smoke_vapour"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/**
	 * Fully mastered, cruising (Sprint) in Vapour Form turns the whole body to smoke: on a player who is, synced to
	 * everyone (no body drawn, and blocks short of a full cube don't stop them on either side). Intangible: nothing hurts
	 * them, shots and creatures pass through, and they can't attack or use abilities.
	 */
	public static final AttachmentType<Boolean> WHOLLY = AttachmentRegistry.create(JaymosPowers.id("smoke_wholly"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On a Smoke user with Vapour Form switched on (R): sprinting turns them to smoke. Synced to them only (the HUD). */
	public static final AttachmentType<Boolean> ARMED = AttachmentRegistry.create(JaymosPowers.id("smoke_vapour_armed"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Smoke a tick the whole smoke body pours out, on top of the trail. */
	static final float WHOLLY_TRAIL = 0.5F;

	/** Client → server: the local flier started ({@code on}) or stopped cruising, for the whole-body smoke. */
	public record Whole(boolean on) implements CustomPacketPayload {
		public static final Type<Whole> TYPE = new Type<>(JaymosPowers.id("smoke_wholly"));
		public static final StreamCodec<ByteBuf, Whole> CODEC = ByteBufCodecs.BOOL.map(Whole::new, Whole::on);

		@Override
		public Type<Whole> type() {
			return TYPE;
		}
	}

	/** Each flier's last position and ticks on the ground. */
	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<UUID, Integer> GROUNDED = new HashMap<>();
	/** Which armour slots each flier had filled last tick: a slot that fills up while in vapour is handed back. */
	private static final Map<UUID, EnumSet<EquipmentSlot>> WORN = new HashMap<>();
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private SmokeVapour() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Whole.TYPE, Whole.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Whole.TYPE, (payload, ctx) -> setWhole(ctx.player(), payload.on()));
		// wholly smoke can't strike anything
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> wholly(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !active(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			if (wholly(player)) {
				return false;
			}
			if (source.is(DamageTypeTags.IS_FALL)) {
				return false;
			}
			if (!(source.getDirectEntity() instanceof Projectile) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
				return true;
			}
			// the shot goes through the smoke: a swirl where it went in
			ServerLevel level = player.level();
			Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.3, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.02);
			SmokeField.puff(level, at, 0.8F, player);
			return false;
		});
	}

	/** In Vapour Form (both sides). */
	public static boolean active(Player player) {
		return player.getAttached(FORM) != null && Powers.has(player, SmokePower.INSTANCE);
	}

	/** Wholly smoke: in Vapour Form, fully mastered and cruising (both sides). */
	public static boolean wholly(Player player) {
		return player.getAttached(WHOLLY) != null && active(player);
	}

	/** From the client: cruising began or ended. Only a fully mastered flier in Vapour Form goes wholly to smoke. */
	static void setWhole(ServerPlayer player, boolean on) {
		boolean can = on && active(player) && SmokePower.mastered(player);
		if (can == (player.getAttached(WHOLLY) != null)) {
			return;
		}
		if (can) {
			player.setAttached(WHOLLY, true);
			Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			SmokeField.puff(player.level(), at, 3.0F, player);
			player.level().playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 0.4F);
		} else {
			player.removeAttached(WHOLLY);
		}
	}

	/** Vapour Form is switched on (both sides). */
	public static boolean armed(Player player) {
		return player.getAttached(ARMED) != null && Powers.has(player, SmokePower.INSTANCE);
	}

	/** R: Vapour Form switched on (sprint to take off; already sprinting, off you go), or off (landing you). */
	static void toggle(ServerPlayer player) {
		if (player.getAttached(ARMED) != null) {
			disarm(player);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_vapour_off").withStyle(ChatFormatting.GRAY));
			return;
		}
		player.setAttached(ARMED, true);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_vapour_on").withStyle(ChatFormatting.GRAY));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.4F);
		SmokeMoves.begin(player, SmokeMoves.ARM);
	}

	/** The mode off, and back to flesh if in the form. */
	static void disarm(ServerPlayer player) {
		if (player.getAttached(ARMED) != null) {
			player.removeAttached(ARMED);
		}
		end(player);
	}

	static void start(ServerPlayer player) {
		if (!WindFlight.canFly(player) || player.isFallFlying() || spent(player)) {
			return;
		}
		ServerLevel level = player.level();
		SmokePower.set(player, SmokePower.state(player).withVapour(level.getGameTime()));
		player.setAttached(FORM, true);
		player.resetFallDistance();
		LAST.put(player.getUUID(), player.position());
		GROUNDED.remove(player.getUUID());
		WORN.put(player.getUUID(), worn(player));
		// the legs go up in a gout of smoke
		Vec3 at = player.position().add(0.0, 0.4, 0.0);
		SmokeField.puff(level, at, 6.0F, player);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 0.4, 0.3, 0.4, 0.06);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 20, 0.4, 0.2, 0.4, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	static void tick(ServerPlayer player) {
		if (player.getAttached(FORM) == null) {
			// switched on: breaking into a sprint takes off (the client goes straight into a cruise)
			if (player.getAttached(ARMED) != null && player.isSprinting() && SmokePower.ready(player, SmokeState.VAPOUR)) {
				start(player);
			}
			return;
		}
		ServerLevel level = player.level();
		UUID id = player.getUUID();
		long since = level.getGameTime() - SmokePower.state(player).vapourFrom();
		int grounded = player.onGround() && since > TAKEOFF_GRACE ? GROUNDED.getOrDefault(id, 0) + 1 : 0;
		GROUNDED.put(id, grounded);
		// landed (Sneak on the ground, or stood there a while), out of food, or something else has the body
		if (!player.isAlive() || !WindFlight.canFly(player) || player.isFallFlying() || spent(player)
			|| grounded > 0 && player.isShiftKeyDown() || grounded >= GROUNDED_TICKS) {
			end(player);
			return;
		}
		player.resetFallDistance();
		handBackArmour(player);
		if (player.getAttached(WHOLLY) != null) {
			if (!SmokePower.mastered(player)) {
				player.removeAttached(WHOLLY);
			} else {
				// the whole body pours, head to the waist too
				SmokeField.puff(level, player.position().add(0.0, player.getBbHeight() * 0.6, 0.0), WHOLLY_TRAIL, player);
			}
		}
		Vec3 now = player.position();
		Vec3 last = LAST.put(id, now);
		double moved = last == null ? 0.0 : Math.min(4.0, now.distanceTo(last));
		player.causeFoodExhaustion(EXHAUSTION + (float)moved * EXHAUSTION_PER_BLOCK);
		// the legs pour out a trail, from the hips down
		SmokeField.puff(level, now.add(0.0, player.getBbHeight() * 0.25, 0.0), Mastery.starterOnly(player) ? STARTER_TRAIL : TRAIL, player);
		if (player.tickCount % 16 == 0) {
			level.playSound(null, now.x, now.y, now.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.25F,
				0.4F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	private static EnumSet<EquipmentSlot> worn(Player player) {
		EnumSet<EquipmentSlot> worn = EnumSet.noneOf(EquipmentSlot.class);
		for (EquipmentSlot slot : ARMOUR) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				worn.add(slot);
			}
		}
		return worn;
	}

	/** Armour that got into a slot that was empty last tick comes back off, into the inventory (or dropped). */
	private static void handBackArmour(ServerPlayer player) {
		EnumSet<EquipmentSlot> before = WORN.getOrDefault(player.getUUID(), EnumSet.noneOf(EquipmentSlot.class));
		boolean refused = false;
		for (EquipmentSlot slot : ARMOUR) {
			ItemStack stack = player.getItemBySlot(slot);
			if (!stack.isEmpty() && !before.contains(slot)) {
				player.setItemSlot(slot, ItemStack.EMPTY);
				if (!player.getInventory().add(stack)) {
					player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				refused = true;
			}
		}
		if (refused) {
			refuseArmour(player);
		}
		WORN.put(player.getUUID(), worn(player));
	}

	/** Tells the player (server side) why the armour won't go on. */
	public static void refuseArmour(Player player) {
		if (player instanceof ServerPlayer server) {
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_vapour_armour").withStyle(ChatFormatting.GRAY));
		}
	}

	private static boolean spent(ServerPlayer player) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= MIN_FOOD;
	}

	/** Back to flesh: the legs come back and the cooldown starts. */
	static void end(ServerPlayer player) {
		LAST.remove(player.getUUID());
		GROUNDED.remove(player.getUUID());
		WORN.remove(player.getUUID());
		if (player.getAttached(WHOLLY) != null) {
			player.removeAttached(WHOLLY);
		}
		if (player.getAttached(FORM) == null) {
			return;
		}
		player.removeAttached(FORM);
		SmokePower.set(player, SmokePower.state(player).withVapour(-1L));
		SmokePower.cooldown(player, SmokeState.VAPOUR, SmokePower.VAPOUR_COOLDOWN);
		Vec3 at = player.position().add(0.0, 0.4, 0.0);
		SmokeField.puff(player.level(), at, 2.5F, player);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	static void clear() {
		LAST.clear();
		GROUNDED.clear();
		WORN.clear();
	}
}
