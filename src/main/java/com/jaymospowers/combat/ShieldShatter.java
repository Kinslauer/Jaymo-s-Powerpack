package com.jaymospowers.combat;

import com.jaymospowers.power.darktide.DarktideStatus;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-11): "splintering wood from shattering shields". A wooden shield ({@link ClashFx#wooden}) that has taken
 * too much breaks apart in a burst of shards.
 * <ul>
 * <li>A player's wears out as it always did (its durability); when it breaks, it breaks like this ({@link #broke}).</li>
 * <li>A mob's would outlast any fight that way, so it has a strength of its own: after {@link #MOB_SHIELD_HEALTH} points
 * blocked (an axe's blows count {@link #AXE_FACTOR} times) it shatters, and the mob reels {@link #STAGGER} ticks with
 * nothing left to hide behind. The count is forgotten {@link #FORGET_TICKS} after the last blow.</li>
 * <li>The mob isn't left without one for good: it takes up another {@link #SPARE_TICKS} later, or when it's unloaded
 * (so a realm's guard keeps the shield with his realm's arms).</li>
 * </ul>
 * Metal shields never shatter: they throw sparks ({@link ClashFx#blocked}). {@link #shatter} breaks a held wooden shield
 * outright, for anything else that should (the films' staged fights).
 */
public final class ShieldShatter {
	/** Damage a mob's wooden shield blocks before it breaks, and how much more an axe's blows count. */
	public static final float MOB_SHIELD_HEALTH = 28.0F, AXE_FACTOR = 2.0F;
	/** A mob whose shield broke reels this long (ticks). */
	public static final int STAGGER = 12;
	/** A mob's shield is whole again this long (ticks) after its last blow; a mob takes up a spare this long after losing one. */
	public static final int FORGET_TICKS = 20 * 30, SPARE_TICKS = 20 * 240;

	/** What a mob's shield has taken, and when it last took any (game time). */
	private record Worn(float taken, long at) {
	}

	/** A mob's broken shield: which hand it was in, what it was, and when the spare comes out (game time). */
	private record Spare(InteractionHand hand, ItemStack shield, long at) {
	}

	private static final Map<Mob, Worn> WORN = new WeakHashMap<>();
	private static final Map<Mob, Spare> SPARES = new WeakHashMap<>();

	private ShieldShatter() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ShieldShatter::tick);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				WORN.remove(mob);
				giveBack(mob);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Mob mob : new ArrayList<>(SPARES.keySet())) {
				giveBack(mob);
			}
			WORN.clear();
		});
	}

	/** A mob's raised wooden shield took {@code taken} of a blow: it may break. */
	static void took(ServerLevel level, LivingEntity defender, ItemStack guard, DamageSource source, float taken) {
		if (!(defender instanceof Mob mob) || !mob.isAlive()) {
			return;
		}
		long now = level.getGameTime();
		Worn worn = WORN.get(mob);
		float before = worn == null || now - worn.at() > FORGET_TICKS ? 0.0F : worn.taken();
		boolean axe = source.getDirectEntity() instanceof LivingEntity attacker && attacker.getSecondsToDisableBlocking() > 0.0F;
		float total = before + taken * (axe ? AXE_FACTOR : 1.0F);
		if (total < MOB_SHIELD_HEALTH) {
			WORN.put(mob, new Worn(total, now));
			return;
		}
		WORN.remove(mob);
		shatter(mob, source.getSourcePosition());
	}

	/**
	 * {@code holder}'s wooden shield (either hand) breaks apart now: gone from the hand, shards thrown away from
	 * {@code from} (null: every way), the crack of it, and a mob reels. False if they hold none. A mob takes up a spare
	 * later; a player's is simply gone.
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
			if (holder instanceof Mob mob) {
				SPARES.put(mob, new Spare(hand, held.copy(), level.getGameTime() + SPARE_TICKS));
				DarktideStatus.stun(mob, STAGGER);
			}
			burst(level, holder, from);
			level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.SHIELD_BREAK.value(),
				holder instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, 0.9F, 0.9F + holder.getRandom().nextFloat() * 0.2F);
			return true;
		}
		return false;
	}

	/** Something {@code holder} wore or held broke ({@code LivingEntity.onEquippedItemBroken}): a wooden shield bursts. */
	public static void broke(LivingEntity holder, ItemStack broken) {
		if (holder.level() instanceof ServerLevel level && ClashFx.wooden(broken)) {
			burst(level, holder, null);
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
