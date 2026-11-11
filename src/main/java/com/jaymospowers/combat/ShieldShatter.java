package com.jaymospowers.combat;

import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "splintering wood from shattering shields", then: "The shields break when their durability is gone,
 * and axe hits deal more damage to its durability than swords". A shield breaks when its durability runs out and at no
 * other time, a player's and a mob's alike (vanilla wears a raised shield by what it blocks, whoever holds it).
 * <ul>
 * <li>An axe (any weapon that knocks a shield aside) wears a shield {@link #AXE_FACTOR} times as fast as anything else
 * ({@link #axe}), wooden or metal.</li>
 * <li>A wooden shield ({@link ClashFx#wooden}) that breaks bursts into shards ({@link #broke}); a mob that lost one this
 * way reels {@link #STAGGER} ticks.</li>
 * <li>The mob isn't left without one for good: it takes up another {@link #SPARE_TICKS} later, or when it's unloaded
 * (so a realm's guard keeps the shield with his realm's arms).</li>
 * </ul>
 * {@link #shatter} breaks a held wooden shield outright, for anything else that should (the films' staged fights).
 */
public final class ShieldShatter {
	/** An axe's blow wears a raised shield this many times what a sword's of the same weight does. */
	public static final float AXE_FACTOR = 3.0F;
	/** A mob whose shield broke reels this long (ticks). */
	public static final int STAGGER = 12;
	/** A mob takes up a spare shield this long (ticks) after losing one. */
	public static final int SPARE_TICKS = 20 * 240;

	/** A mob's broken shield: which hand it was in, what it was, and when the spare comes out (game time). */
	private record Spare(InteractionHand hand, ItemStack shield, long at) {
	}

	private static final Map<Mob, Spare> SPARES = new WeakHashMap<>();

	private ShieldShatter() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ShieldShatter::tick);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				giveBack(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Mob mob : new ArrayList<>(SPARES.keySet())) {
				giveBack(mob);
			}
		});
	}

	/**
	 * {@code defender}'s raised shield (not a weapon's guard) took {@code taken} of an axe's blow: the wear vanilla already
	 * gave it for the blow, {@link #AXE_FACTOR} - 1 times more. It may break of it ({@link #broke}).
	 */
	static void axe(LivingEntity defender, DamageSource source, float taken) {
		if (!(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == defender || attacker.getSecondsToDisableBlocking() <= 0.0F) {
			return;
		}
		ItemStack shield = defender.getItemBlockingWith();
		if (shield == null || shield.isEmpty() || shield.has(DataComponents.WEAPON)) {
			return;
		}
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null) {
			return;
		}
		int extra = Math.round(blocks.itemDamage().apply(taken) * (AXE_FACTOR - 1.0F));
		if (extra > 0) {
			shield.hurtAndBreak(extra, defender, defender.getUsedItemHand());
		}
	}

	/**
	 * {@code holder}'s wooden shield (either hand) breaks apart now, whatever durability it had left: gone from the hand,
	 * shards thrown away from {@code from} (null: every way), the crack of it, and a mob reels. False if they hold none.
	 * A mob takes up a spare later; a player's is simply gone.
	 */
	public static boolean shatter(LivingEntity holder, @Nullable Vec3 from) {
		if (!(holder.level() instanceof ServerLevel level)) {
			return false;
		}
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = holder.getItemInHand(hand);
			if (!ClashFx.wooden(held)) {
				continue;
			}
			if (holder.isUsingItem() && holder.getUsedItemHand() == hand) {
				holder.stopUsingItem();
			}
			holder.setItemInHand(hand, ItemStack.EMPTY);
			lost(level, holder, hand, held);
			burst(level, holder, from);
			level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SHIELD_BREAK.value(),
				holder instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 0.9F, 0.9F + holder.getRandom().nextFloat() * 0.2F);
			return true;
		}
		return false;
	}

	/**
	 * Something {@code holder} wore or held ran out of durability ({@code LivingEntity.onEquippedItemBroken}): a wooden
	 * shield bursts into shards, and a mob that lost its shield (wooden or not) reels and is owed a spare.
	 */
	public static void broke(LivingEntity holder, ItemStack broken, EquipmentSlot slot) {
		if (!(holder.level() instanceof ServerLevel level) || !broken.has(DataComponents.BLOCKS_ATTACKS) || broken.has(DataComponents.WEAPON)) {
			return;
		}
		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
			lost(level, holder, slot == EquipmentSlot.MAINHAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, broken);
		}
		if (ClashFx.wooden(broken)) {
			burst(level, holder, null);
		}
	}

	/** A mob lost the shield it held in {@code hand}: it reels, and a whole one like it comes out later. */
	private static void lost(ServerLevel level, LivingEntity holder, InteractionHand hand, ItemStack shield) {
		if (holder instanceof Mob mob) {
			ItemStack spare = shield.copyWithCount(1);
			spare.setDamageValue(0);
			SPARES.put(mob, new Spare(hand, spare, level.getGameTime() + SPARE_TICKS));
			DarktideStatus.stun(mob, STAGGER);
		}
	}

	/** The shards and the crack of a shield breaking on {@code holder}'s arm, thrown away from {@code from}. */
	private static void burst(ServerLevel level, LivingEntity holder, @Nullable Vec3 from) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, holder.getYHeadRot());
		Vec3 at = holder.position().add(0.0, holder.getBbHeight() * 0.65, 0.0).add(facing.scale(holder.getBbWidth() * 0.6 + 0.2));
		Vec3 line = from == null ? null : at.subtract(from).multiply(1.0, 0.0, 1.0);
		ClashFx.splinters(level, at, line, ClashFx.SHATTER_SHARDS / 2, ClashFx.WOOD);
		ClashFx.splinters(level, at, line, ClashFx.SHATTER_SHARDS - ClashFx.SHATTER_SHARDS / 2, ClashFx.DARK_WOOD);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_BREAK, holder instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 1.0F,
			0.7F + holder.getRandom().nextFloat() * 0.2F);
	}

	/** {@code mob}'s spare comes out, if it's owed one and that hand is still free. */
	private static void giveBack(Mob mob) {
		Spare spare = SPARES.remove(mob);
		if (spare != null && mob.isAlive() && mob.getItemInHand(spare.hand()).isEmpty()) {
			mob.setItemInHand(spare.hand(), spare.shield());
		}
	}

	private static void tick(MinecraftServer server) {
		if (SPARES.isEmpty() || server.getTickCount() % 100 != 0) {
			return;
		}
		for (Map.Entry<Mob, Spare> entry : new ArrayList<>(SPARES.entrySet())) {
			Mob mob = entry.getKey();
			if (mob == null || !mob.isAlive()) {
				SPARES.remove(mob);
			} else if (mob.level().getGameTime() >= entry.getValue().at() && mob.getTarget() == null) {
				giveBack(mob);
			}
		}
	}
}
