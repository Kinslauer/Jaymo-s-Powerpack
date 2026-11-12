package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.AABB;

/**
 * Andy (2026-11-12): "you shouldnt be able to walk upright with no leg, unless you make a cane or crutch". A player with
 * a leg gone and nothing fitted in its place ({@link Prosthetics}) can't stand: they are held to a crawl
 * ({@link Breaks#crawlOnly}) like one with two broken legs. With one leg left, a Walking Cane or a Crutch held in either
 * hand lets them up (Jump, as from any crawl; one this class put down rises by itself when the support comes to hand),
 * at the slow pace a missing leg already costs ({@link Dismember#LEG_SPEED}); the crutch takes more of the weight and
 * walks a little faster. With both legs gone there is nothing to stand on: only a fitted leg helps.
 *
 * <p>The arms do the crawling, so a legless crawl loses far less pace than the missing legs cost a walk
 * ({@link #CRAWL_ONE}, {@link #CRAWL_NONE}). A transformed Xenoform's body is its own and isn't held down.
 */
public final class Crutches {
	/** How often (ticks) each player's legs and hands are checked. */
	public static final int CHECK_EVERY = 4;
	/** Walking pace on one leg with a crutch, against a sound walk (a cane, or nothing, leaves {@link Dismember#LEG_SPEED}'s 0.55). */
	public static final double CRUTCH_PACE = 0.7;
	/** A crawl's pace with one leg gone and with both gone, against a sound body's crawl. */
	public static final double CRAWL_ONE = 0.85, CRAWL_NONE = 0.65;

	private static final int LEGS = Dismember.RIGHT_LEG | Dismember.LEFT_LEG;
	private static final Identifier GAIT_ID = JaymosPowers.id("legless_gait");
	/** Players this class put on the ground (they rise by themselves once something holds them up again). */
	private static final Set<UUID> FELLED = new HashSet<>();

	public static Item WALKING_CANE, CRUTCH;

	private Crutches() {
	}

	public static void register() {
		WALKING_CANE = item("walking_cane");
		CRUTCH = item("crutch");
		CreativeModeTabEvents.modifyOutputEvent(com.jaymospowers.tinkering.kiln.Kiln.tab()).register(output -> {
			output.accept(WALKING_CANE);
			output.accept(CRUTCH);
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
			FELLED.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		});
	}

	private static Item item(String id) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new SupportItem(new Item.Properties().setId(key).stacksTo(1), id));
	}

	// ---- queries -------------------------------------------------------------------------------------------------------

	/** How many of {@code entity}'s legs are gone with nothing fitted in their place (0 to 2). */
	public static int bareLegs(Entity entity) {
		int bare = Dismember.missing(entity) & ~Prosthetics.fitted(entity) & LEGS;
		return Integer.bitCount(bare);
	}

	/** Whether {@code player} holds a cane or a crutch in either hand. */
	public static boolean supported(Player player) {
		return support(player.getMainHandItem()) || support(player.getOffhandItem());
	}

	private static boolean support(ItemStack stack) {
		return stack.getItem() instanceof SupportItem;
	}

	private static boolean crutch(Player player) {
		return CRUTCH != null && (player.getMainHandItem().is(CRUTCH) || player.getOffhandItem().is(CRUTCH));
	}

	/**
	 * A player who can't stand for want of a leg: one gone and bare with no cane or crutch in hand, or both gone and
	 * bare. Read the same on both sides (the missing parts, the fitted limbs and the hands are all synced).
	 */
	public static boolean legless(Entity entity) {
		if (!(entity instanceof Player player) || player.isCreative() || player.isSpectator()) {
			return false;
		}
		int bare = bareLegs(player);
		if (bare == 0 || bare == 1 && supported(player)) {
			return false;
		}
		return !com.jaymospowers.power.xenoform.XenoformState.transformed(player);
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(ServerPlayer player) {
		int bare = bareLegs(player);
		UUID id = player.getUUID();
		if (bare == 0 && !FELLED.contains(id)) {
			gait(player, 0.0);
			return;
		}
		boolean downed = Downed.isDowned(player);
		boolean crawling = player.hasAttached(Crawl.CRAWLING);
		if (legless(player)) {
			if (!crawling && !downed && player.isAlive() && player.onGround() && Crawl.canCrawl(player)
				&& !com.jaymospowers.core.Carry.isCarried(player)) {
				// nothing to stand on: down on the ground
				player.setAttached(Crawl.CRAWLING, true);
				crawling = true;
				if (FELLED.add(id)) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.legless." + (bare == 2 ? "both" : "one"))
						.withStyle(ChatFormatting.RED));
				}
			}
		} else if (FELLED.remove(id) && crawling && !downed && !Breaks.crawlOnly(player) && roomToStand(player)) {
			// the cane in hand, a leg fitted or grown back: up again
			player.removeAttached(Crawl.CRAWLING);
			player.removeAttached(Crawl.SUPINE);
			crawling = false;
		}
		// the pace: arms haul a crawl, a crutch carries a walk
		double base = 1.0 + (bare == 2 ? Dismember.LEGS_SPEED : Dismember.LEG_SPEED);
		if (bare == 0 || downed) {
			gait(player, 0.0);
		} else if (crawling) {
			gait(player, (bare == 2 ? CRAWL_NONE : CRAWL_ONE) / base - 1.0);
		} else if (bare == 1 && crutch(player)) {
			gait(player, CRUTCH_PACE / base - 1.0);
		} else {
			gait(player, 0.0);
		}
	}

	private static boolean roomToStand(ServerPlayer player) {
		AABB box = EntityDimensions.scalable(0.6F, 1.8F).scale(player.getScale()).makeBoundingBox(player.position()).deflate(1.0E-3);
		return player.level().noCollision(player, box);
	}

	private static void gait(ServerPlayer player, double amount) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		AttributeModifier now = speed.getModifier(GAIT_ID);
		if (amount == 0.0) {
			if (now != null) {
				speed.removeModifier(GAIT_ID);
			}
		} else if (now == null || now.amount() != amount) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(GAIT_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	// ---- the items -----------------------------------------------------------------------------------------------------

	/** A walking cane or a crutch: held in either hand, it stands a one-legged player up. */
	public static final class SupportItem extends Item {
		private final String id;

		SupportItem(Item.Properties properties, String id) {
			super(properties);
			this.id = id;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers." + this.id).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.support.hold").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
