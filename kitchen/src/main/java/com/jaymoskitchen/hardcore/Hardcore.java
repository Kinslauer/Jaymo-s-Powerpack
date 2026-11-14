package com.jaymoskitchen.hardcore;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.tanning.Tanning;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Better With Mods' hardcore tweaks (MIT; our own code), the three Andy picked:
 * <ul>
 * <li><b>Lumber.</b> Jaymo's Kingdoms already gives nothing for a log broken without an axe. Here a bare hand (or a
 * wrong tool) at least strips {@link #BARK} bark and {@link #STICKS} sticks from it, as BWM's hardcore lumber does.</li>
 * <li><b>Hunger.</b> An empty belly slows you, and then weakens you, as the bar drops (BWM's hunger penalties, set to
 * the vanilla bar). Jumping is left alone: a player who can't hop a block can't walk home to eat. Androids and the
 * Divine-born are kept full by Jaymo's Powers, so they never feel it.</li>
 * <li><b>Beds.</b> No bed sets your spawn ({@code mixin/BedSpawnMixin}); you still sleep in one. Respawn anchors,
 * lit outpost campfires and the world's spawn are left.</li>
 * </ul>
 */
public final class Hardcore {
	/** What a log broken without an axe still gives. */
	public static final int BARK = 1, STICKS = 2;
	/** At or under this much food you're slowed (vanilla stops your sprint here too). */
	public static final int PECKISH = 6;
	/** At or under this you're slower and weak. */
	public static final int HUNGRY = 4;
	/** At or under this you're slower still, weaker, and slow to dig. */
	public static final int STARVING = 2;
	/** Speed lost at each step (a share of your base walking speed). */
	public static final double PECKISH_SLOW = -0.15, HUNGRY_SLOW = -0.3, STARVING_SLOW = -0.5;
	/** How often the penalties are looked at (ticks); the effects last a little longer so they never flicker. */
	static final int CHECK_TICKS = 20;
	static final int EFFECT_TICKS = 50;

	private static final Identifier SLOW_ID = JaymosKitchen.id("hunger_slow");

	private Hardcore() {
	}

	public static void register() {
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || player.isCreative() || player.isSpectator() || !state.is(BlockTags.LOGS)
				|| player.hasCorrectToolForDrops(state)) {
				return;
			}
			Block.popResource(level, pos, new ItemStack(Tanning.BARK, BARK));
			Block.popResource(level, pos, new ItemStack(Items.STICK, STICKS));
		});
		ServerTickEvents.END_SERVER_TICK.register(Hardcore::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			hunger(player);
		}
	}

	private static void hunger(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		int food = player.getFoodData().getFoodLevel();
		boolean spared = player.isCreative() || player.isSpectator() || !player.isAlive();
		double slow = spared || food > PECKISH ? 0.0 : food > HUNGRY ? PECKISH_SLOW : food > STARVING ? HUNGRY_SLOW : STARVING_SLOW;
		if (slow == 0.0) {
			speed.removeModifier(SLOW_ID);
			return;
		}
		AttributeModifier current = speed.getModifier(SLOW_ID);
		if (current == null || current.amount() != slow) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, slow, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
		if (food <= HUNGRY) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, food <= STARVING ? 1 : 0, true, false, true));
		}
		if (food <= STARVING) {
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, EFFECT_TICKS, 0, true, false, true));
		}
	}
}
