package com.jaymospowers.power.zfighter.breath;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.tinkering.smithing.Grades;
import com.jaymospowers.tinkering.smithing.Smithing;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.20, SevTech picks) Cinder's heat, the mirror of Glacia's cold ({@link BreathCompat}). Out in the forge-world's air a
 * player is slowed after {@link #SLOW_AFTER} seconds and takes {@link #HURT} a point every {@link #HURT_EVERY} seconds once
 * {@link #HURT_AFTER} have gone. It lets up while they stand in water, are fire-proof, or hold or eat something cool
 * ({@code #jaymospowers:cooling_items}, with {@link #COOL_GRACE} seconds' grace after the last bite); a quenched, graded piece
 * (a smith's work that is cold again: {@link Grades} and no {@code HEATED_AT}) worn or held cuts the damage by half. The
 * pod's seat is cool inside.
 */
public final class Heat {
	// ---- tuning ----
	/** Seconds in the heat before it slows you, before it hurts, and between hurts after that. */
	public static final int SLOW_AFTER = 30, HURT_AFTER = 60, HURT_EVERY = 8;
	public static final float HURT = 1.0F;
	/** The share of the damage a quenched piece lets through. */
	public static final float QUENCHED_SHARE = 0.5F;
	/** Seconds the heat stays off after something cooled you, and how fast (seconds a second) it wears off meanwhile. */
	public static final int COOL_GRACE = 10;
	public static final int COOL_RATE = 3;
	/** Cinder's heat is slow, not stopping. */
	static final int SLOW_AMPLIFIER = 0;

	/** Things that cool a body down held or eaten (data: {@code data/jaymospowers/tags/item/cooling_items.json}). */
	public static final TagKey<Item> COOLING = TagKey.create(Registries.ITEM, JaymosPowers.id("cooling_items"));

	private static final Map<UUID, Integer> EXPOSED = new HashMap<>();
	private static final Map<UUID, Long> COOL_UNTIL = new HashMap<>();

	private Heat() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Heat::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel cinder = server.getLevel(ZFighterSpace.DESTS[ZFighterSpace.CINDER].level());
		if (cinder == null) {
			EXPOSED.clear();
			return;
		}
		// whoever has left Cinder forgets its heat
		EXPOSED.keySet().removeIf(id -> !(server.getPlayerList().getPlayer(id) instanceof ServerPlayer p && p.level() == cinder));
		long now = cinder.getGameTime();
		for (ServerPlayer player : cinder.players()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			UUID id = player.getUUID();
			if (player.getVehicle() instanceof PodEntity) {
				EXPOSED.remove(id);
				continue;
			}
			if (player.isInWater() || player.hasEffect(MobEffects.FIRE_RESISTANCE) || coolHand(player)
				|| com.jaymospowers.power.zfighter.backhome.FarFood.warded(player)) {
				COOL_UNTIL.put(id, now + COOL_GRACE * 20L);
			}
			if (COOL_UNTIL.getOrDefault(id, 0L) > now) {
				EXPOSED.computeIfPresent(id, (k, v) -> v - COOL_RATE > 0 ? v - COOL_RATE : null);
				continue;
			}
			int seconds = EXPOSED.merge(id, 1, Integer::sum);
			if (seconds == SLOW_AFTER) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.heat.slow").withStyle(ChatFormatting.GOLD));
			}
			if (seconds >= SLOW_AFTER) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, SLOW_AMPLIFIER, true, false, true));
				cinder.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getEyeY(), player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			}
			if (seconds >= HURT_AFTER && (seconds - HURT_AFTER) % HURT_EVERY == 0) {
				player.hurtServer(cinder, player.damageSources().inFire(), quenched(player) ? HURT * QUENCHED_SHARE : HURT);
			}
		}
	}

	/** Holding something cool, or eating it right now. */
	static boolean coolHand(Player player) {
		return player.getMainHandItem().is(COOLING) || player.getOffhandItem().is(COOLING)
			|| player.isUsingItem() && player.getUseItem().is(COOLING);
	}

	/** Whether a quenched piece of a smith's work is worn or held: graded, and cold again. */
	public static boolean quenched(Player player) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (isQuenched(player.getItemBySlot(slot))) {
				return true;
			}
		}
		return isQuenched(player.getItemInHand(InteractionHand.MAIN_HAND)) || isQuenched(player.getItemInHand(InteractionHand.OFF_HAND));
	}

	static boolean isQuenched(ItemStack stack) {
		return !stack.isEmpty() && Grades.of(stack) != null && !stack.has(Smithing.HEATED_AT);
	}
}
