package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.sky.Charts;
import com.jaymospowers.power.zfighter.sky.PodScope;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stardust: a falling star's pinch of dust, caught in a glass bottle by someone who is watching it come down
 * ({@link #caught}, called when a star lands). A stargazer, or a Patrol officer, trades one line of a hint for a pinch
 * ({@link #hint}): which part of the sky to search for a world whose light is not on the pod's map yet ({@link PodScope#hint}).
 * It is talk, never screen text; what a player was told is also kept in the logbook ({@link #HINTED}, {@code PodLog}). Kingdoms'
 * stargazers reach it through {@code SkyApi} by reflection.
 */
public final class PodStardust {
	// ---- tuning ----
	/** A glass bottle in hand, this far (blocks) from where a star came down, looking within this cosine of it: a pinch. */
	public static final double CATCH_RANGE = 96.0, CATCH_AIM = 0.8;
	/** A great star gives this many pinches. */
	public static final int GREAT_PINCHES = 3;

	public static Item STARDUST;
	/** On the player: a bit for each world whose hint they were given. */
	public static final AttachmentType<Integer> HINTED = AttachmentRegistry.create(JaymosPowers.id("zfighter_pod_hinted"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	private static boolean ready;

	private PodStardust() {
	}

	/** Registers the stardust. Called once, from {@link PodTravel#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("stardust"));
		STARDUST = Registry.register(BuiltInRegistries.ITEM, key, new com.jaymospowers.power.zfighter.fuel.PodFuel.TipItem(
			new Item.Properties().setId(key).rarity(Rarity.RARE), "item.jaymospowers.stardust.tooltip"));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(STARDUST));
	}

	/**
	 * A star has come down at {@code at}: everyone watching it with a glass bottle in hand catches {@code pinches} of its dust
	 * (the bottle stays theirs).
	 */
	public static void caught(ServerLevel level, Vec3 at, int pinches) {
		for (ServerPlayer player : level.players()) {
			if (!player.isAlive() || player.isSpectator() || player.distanceToSqr(at) > CATCH_RANGE * CATCH_RANGE) {
				continue;
			}
			boolean bottle = player.getMainHandItem().is(Items.GLASS_BOTTLE) || player.getOffhandItem().is(Items.GLASS_BOTTLE);
			Vec3 to = at.subtract(player.getEyePosition());
			if (!bottle || to.lengthSqr() < 1.0E-4 || player.getLookAngle().dot(to.normalize()) < CATCH_AIM) {
				continue;
			}
			ItemStack dust = new ItemStack(STARDUST, pinches);
			if (!player.getInventory().add(dust)) {
				player.drop(dust, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			PodLog.note(player, PodLog.Step.DUST);
			level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.8F, 1.7F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.stardust.caught").withStyle(ChatFormatting.AQUA));
		}
	}

	/** Whether {@code player} has a pinch to trade. */
	public static boolean has(ServerPlayer player) {
		return player.isCreative() || player.getInventory().contains(stack -> stack.is(STARDUST));
	}

	/** The world whose light {@code player} would be told of next: the first they have not read, cheapest trip first; -1 when they know them all. */
	private static int next(ServerPlayer player) {
		List<Integer> unread = Charts.nextUnread(player, 1);
		return unread.isEmpty() ? -1 : unread.getFirst();
	}

	/**
	 * Trades a pinch of {@code player}'s stardust for one line of hint, said by whoever asks (the line is returned; null when
	 * there is no stardust or nothing left to find). The world is remembered in {@link #HINTED}.
	 */
	public static @Nullable Component hint(ServerPlayer player) {
		int world = next(player);
		if (world < 0) {
			return Component.translatable("talk.jaymospowers.stardust.known_all");
		}
		if (!has(player)) {
			return null;
		}
		if (!player.isCreative()) {
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(STARDUST)) {
					stack.shrink(1);
					break;
				}
			}
		}
		player.setAttached(HINTED, player.getAttachedOrElse(HINTED, 0) | 1 << world);
		PodLog.note(player, PodLog.Step.HINT);
		return PodScope.hint(world);
	}

	/** The worlds {@code player} was given a hint of and has not found yet. */
	public static List<Integer> pending(ServerPlayer player) {
		int mask = player.getAttachedOrElse(HINTED, 0);
		List<Integer> worlds = new java.util.ArrayList<>();
		for (int world = 0; world < Charts.WORLDS; world++) {
			if ((mask & 1 << world) != 0 && !Charts.read(player, world)) {
				worlds.add(world);
			}
		}
		return worlds;
	}
}
