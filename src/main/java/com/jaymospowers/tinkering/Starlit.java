package com.jaymospowers.tinkering;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.56, SevTech picks: star-metal) The Starlit trait's second half. The first, a quicker swing, is in {@link ToolStats}. A
 * Starlit tool swung in the air (at something, or at a block) lets its holder come down without the fall's damage: the landing
 * is soft for {@link #GUARD_TICKS} after the swing. Only while the tool is whole, and never in the creative sense of "free":
 * the guard is spent by the first fall it softens.
 */
public final class Starlit {
	/** How long after an airborne swing the landing is soft (ticks). */
	public static final int GUARD_TICKS = 60;
	/** A swing only counts when the holder is this far above where they started falling (blocks fallen so far). */
	public static final float MIN_FALL = 2.0F;

	/** Who is guarded, until when (game time). */
	private static final Map<UUID, Long> GUARDED = new HashMap<>();

	private Starlit() {
	}

	static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide()) {
				swung(player);
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!level.isClientSide()) {
				swung(player);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL)) {
				Long until = GUARDED.get(player.getUUID());
				if (until != null) {
					GUARDED.remove(player.getUUID());
					if (until >= player.level().getGameTime()) {
						return false;
					}
				}
			}
			return true;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GUARDED.clear());
	}

	/** Whether {@code stack} is a whole tool with the Starlit trait. */
	public static boolean starlit(ItemStack stack) {
		if (stack.isEmpty() || ToolStats.isBroken(stack)) {
			return false;
		}
		ToolStats.Stats stats = ToolStats.stats(stack);
		return stats != null && stats.level(Trait.STARLIT) > 0;
	}

	private static void swung(Player player) {
		if (player.onGround() || player.fallDistance < MIN_FALL || !starlit(player.getMainHandItem())) {
			return;
		}
		GUARDED.put(player.getUUID(), player.level().getGameTime() + GUARD_TICKS);
	}
}
