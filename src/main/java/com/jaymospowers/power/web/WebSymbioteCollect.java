package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (0.17.149) Andy: "Add an option to the wheel for a "Collect" mode. When active (and using living armor) it will use
 * living tendrils to periodically reach out and grab items bringing them to you. This is unlocked at 70 Bond, and before
 * then it will do it on it's own while tendrils are active, grabbing surrounding items within 12 blocks, and upon the
 * first time grabbing an item, the symbiote will hold it in front of you, taking time to inspect and examine it like it
 * does mobs. you can grab the item at any time to skip, but the symbiote will comment on it and give information hints
 * if given time. Only one inspection per item ever."
 * <p>
 * Every {@code EVERY} ticks, with a tendril free ({@link WebTendrils#freeHand}), it reaches for the nearest item on the
 * ground within {@code RANGE} that it can see and that nobody just dropped (a pickup delay), grabs it with
 * {@link WebTendrils#grab} and, once the tendril has it, hands it to you ({@code HAND_TICKS} later: dropped at your feet,
 * free to pick up). From {@code COLLECT_BOND} it does that only with the wheel's Collect slot on
 * ({@link WebSymbiote#COLLECT_TOGGLE}, {@link WebSymbioteState#COLLECT}) and Living Armor on, tendrils out; before that
 * bond it does it on its own whenever its tendrils are out. The first time it ever holds a kind of item
 * ({@link #SEEN}, saved) it stops and studies it instead, held up in front of you with its head swelling as it stares at
 * a creature ({@link WebSymbiote#STARING}) for {@code INSPECT_TICKS}: a line about it as it starts ({@code inspect.<kind>.N}),
 * and, if you let it look that long ({@code HINT_AT}), a hint about what it's good for ({@code hint.<kind>.N}, with the
 * food it gives or how long it lasts where that means something). Use (right click) takes it out of its grip at once
 * ({@link WebNet.InspectGrab}): it gives it up with a line ({@code inspect.skip.N}) and no hint.
 */
public final class WebSymbioteCollect {
	/** The Collect slot (and grabbing items unasked before it) opens at this bond. */
	public static final int COLLECT_BOND = 70;
	static final int EVERY = 30;
	static final double RANGE = 12.0;
	/** A grabbed item reaches your feet this long after the tendril has it. */
	static final int HAND_TICKS = 4;
	/** A first look: how long it studies the item, and when the hint comes. */
	static final int INSPECT_TICKS = 120, HINT_AT = 70;
	static final int LINES = 3, HINT_LINES = 2;

	/** Every kind of item it has studied (item ids): only one inspection per item, ever. Saved, kept through death. */
	public static final AttachmentType<List<String>> SEEN = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_seen_items"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** The item entity it is studying right now, for the owner's client (Use takes it). Not saved. */
	public static final AttachmentType<Integer> INSPECTING = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_inspecting"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	private static final class Fetch {
		int item = -1;
		int hand;
		long reachedAt = -1L;
		boolean inspecting;
		long inspectAt;
		boolean hinted;
		String kind = "misc";
	}

	private static final Map<UUID, Fetch> FETCHES = new HashMap<>();

	private WebSymbioteCollect() {
	}

	static void register() {
		ServerPlayNetworking.registerGlobalReceiver(WebNet.InspectGrab.TYPE, (payload, ctx) -> grab(ctx.player()));
	}

	/** The Collect slot works (its bond, or everything lent to you; creative has it). */
	public static boolean unlocked(net.minecraft.world.entity.player.Player player) {
		return player.isCreative() || WebSymbiote.empowered(player) || WebSymbiote.state(player).bond() >= COLLECT_BOND;
	}

	/** It's reaching for items now: Collect on in Living Armor past its bond, or tendrils out before it. */
	static boolean active(ServerPlayer player) {
		if (!WebSymbiote.armored(player) || WebSymbiote.driving(player) || WebSymbioteRescue.rescuing(player) || Downed.isDowned(player)
			|| player.isSleeping() || WebSymbioteBond.dormant(player)) {
			return false;
		}
		if (unlocked(player)) {
			return WebSymbiote.isToggled(player, WebSymbioteState.COLLECT) && WebSymbiote.livingArmor(player);
		}
		return true;
	}

	/** From the symbiote's tick (bonded, alive). */
	static void tick(ServerPlayer player, long now) {
		Fetch fetch = FETCHES.get(player.getUUID());
		if (fetch != null) {
			tickFetch(player, fetch, now);
			return;
		}
		if (now % EVERY != 0 || !active(player)) {
			return;
		}
		int hand = WebTendrils.freeHand(player);
		if (hand < 0) {
			return;
		}
		ItemEntity item = nearest(player);
		if (item == null) {
			return;
		}
		WebTendrils.grab(player, hand, item, -1L);
		if (!WebTendrils.held(item)) {
			return;
		}
		fetch = new Fetch();
		fetch.item = item.getId();
		fetch.hand = hand;
		FETCHES.put(player.getUUID(), fetch);
	}

	/** The nearest item on the ground it can see, that nobody just dropped and nothing holds. */
	private static ItemEntity nearest(ServerPlayer player) {
		ItemEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(RANGE))) {
			if (!item.isAlive() || item.hasPickUpDelay() || item.getItem().isEmpty() || WebTendrils.held(item) || !player.hasLineOfSight(item)) {
				continue;
			}
			double distance = item.distanceToSqr(player);
			if (distance <= RANGE * RANGE && distance < bestDistance) {
				bestDistance = distance;
				best = item;
			}
		}
		return best;
	}

	private static void tickFetch(ServerPlayer player, Fetch fetch, long now) {
		Entity entity = player.level().getEntity(fetch.item);
		WebTendrils.Grips grips = player.getAttachedOrElse(WebTendrils.GRIPS, WebTendrils.Grips.NONE);
		if (!(entity instanceof ItemEntity item) || !item.isAlive() || grips.get(fetch.hand) != fetch.item || !player.isAlive()) {
			// gone, or the tendril let go of it (you used it for something else)
			stop(player);
			return;
		}
		if (!WebTendrils.reached(player, fetch.hand)) {
			return;
		}
		if (fetch.reachedAt < 0L) {
			fetch.reachedAt = now;
			String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
			List<String> seen = player.getAttachedOrElse(SEEN, List.of());
			if (!seen.contains(id)) {
				// never held one of these: it stops to look
				List<String> next = new ArrayList<>(seen);
				next.add(id);
				player.setAttached(SEEN, next);
				startInspect(player, fetch, item, now);
			}
		}
		if (fetch.inspecting) {
			tickInspect(player, fetch, item, now);
		} else if (now - fetch.reachedAt >= HAND_TICKS) {
			handOver(player, fetch, item);
		}
	}

	private static void startInspect(ServerPlayer player, Fetch fetch, ItemEntity item, long now) {
		fetch.inspecting = true;
		fetch.inspectAt = now;
		fetch.kind = kind(item.getItem());
		// (held up in front of you: not yours until it's done looking, or you take it)
		item.setPickUpDelay(INSPECT_TICKS + 20);
		player.setAttached(INSPECTING, item.getId());
		player.setAttached(WebSymbiote.STARING, now);
		player.setAttached(WebSymbiote.TENDRILS_OUT, now + INSPECT_TICKS + 10);
		WebSymbiote.whisper(player, "inspect." + fetch.kind + "." + (1 + player.getRandom().nextInt(LINES)), item.getItem().getHoverName());
		WebSymbiote.hush(player, now + INSPECT_TICKS + 40);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 1.0F, 0.9F);
	}

	private static void tickInspect(ServerPlayer player, Fetch fetch, ItemEntity item, long now) {
		long age = now - fetch.inspectAt;
		if (age > 0 && age % 30 == 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.PLAYERS, 0.7F, 1.0F);
		}
		if (!fetch.hinted && age >= HINT_AT) {
			fetch.hinted = true;
			WebSymbiote.whisper(player, "hint." + fetch.kind + "." + (1 + player.getRandom().nextInt(HINT_LINES)), item.getItem().getHoverName(),
				figure(item.getItem(), fetch.kind));
		}
		if (age >= INSPECT_TICKS) {
			handOver(player, fetch, item);
		}
	}

	/** Use pressed while it studies something: it gives it up, with a word, before the hint. */
	private static void grab(ServerPlayer player) {
		Fetch fetch = FETCHES.get(player.getUUID());
		if (fetch == null || !fetch.inspecting || !(player.level().getEntity(fetch.item) instanceof ItemEntity item)) {
			return;
		}
		if (!fetch.hinted) {
			WebSymbiote.whisper(player, "inspect.skip." + (1 + player.getRandom().nextInt(LINES)), item.getItem().getHoverName());
		}
		handOver(player, fetch, item);
	}

	/** Yours: let go of, at your feet, free to pick up. */
	private static void handOver(ServerPlayer player, Fetch fetch, ItemEntity item) {
		stop(player);
		WebTendrils.release(player, fetch.hand, false);
		item.setPos(player.getX(), player.getY() + 0.3, player.getZ());
		item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		item.setNoPickUpDelay();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.3F);
	}

	private static void stop(ServerPlayer player) {
		Fetch fetch = FETCHES.remove(player.getUUID());
		if (fetch != null && fetch.inspecting) {
			player.removeAttached(INSPECTING);
			player.removeAttached(WebSymbiote.STARING);
		}
	}

	/** Logged out or the bond gone: nothing carries over (what it saw stays seen). */
	public static void forget(ServerPlayer player) {
		stop(player);
	}

	/** What sort of thing it is, for its lines. */
	static String kind(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.is(Items.LAVA_BUCKET) || stack.is(Items.BLAZE_ROD)
			|| stack.is(Items.BLAZE_POWDER) || stack.is(Items.TORCH) || stack.is(Items.CAMPFIRE) || stack.is(Items.MAGMA_CREAM)) {
			return "fire";
		}
		if (stack.is(Items.BELL) || stack.is(Items.NOTE_BLOCK) || stack.is(Items.GOAT_HORN) || stack.is(Items.JUKEBOX)
			|| stack.is(ItemTags.CREEPER_DROP_MUSIC_DISCS) || path.startsWith("music_disc")) {
			return "noise";
		}
		if (stack.is(Items.DIAMOND) || stack.is(Items.EMERALD) || stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHERITE_SCRAP)
			|| stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.NETHER_STAR)
			|| stack.is(Items.ENDER_PEARL) || stack.is(Items.HEART_OF_THE_SEA)) {
			return "valuable";
		}
		if (stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES) || WebSymbiote.RAW_MEAT.contains(stack.getItem())) {
			return "meat";
		}
		if (stack.has(DataComponents.FOOD)) {
			return "food";
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS) || stack.is(Items.BOW) || stack.is(Items.CROSSBOW) || stack.is(Items.TRIDENT)
			|| stack.is(Items.MACE) || stack.is(ItemTags.ARROWS)) {
			return "weapon";
		}
		if (stack.is(ItemTags.HEAD_ARMOR) || stack.is(ItemTags.CHEST_ARMOR) || stack.is(ItemTags.LEG_ARMOR) || stack.is(ItemTags.FOOT_ARMOR)
			|| stack.is(Items.SHIELD)) {
			return "armor";
		}
		if (stack.is(ItemTags.AXES) || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES) || stack.is(Items.SHEARS)
			|| stack.is(Items.FISHING_ROD)) {
			return "tool";
		}
		if (stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
			return "potion";
		}
		if (stack.is(Items.BOOK) || stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK) || stack.is(Items.ENCHANTED_BOOK)
			|| stack.is(ItemTags.BOOKSHELF_BOOKS) || stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(Items.FILLED_MAP)) {
			return "book";
		}
		if (stack.is(ItemTags.COALS) || path.contains("ingot") || path.contains("nugget") || path.startsWith("raw_") || path.endsWith("_ore")
			|| stack.is(Items.REDSTONE) || stack.is(Items.LAPIS_LAZULI) || stack.is(Items.QUARTZ) || stack.is(Items.AMETHYST_SHARD)
			|| stack.is(Items.COPPER_INGOT)) {
			return "ore";
		}
		if (stack.is(ItemTags.SAPLINGS) || stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || stack.is(ItemTags.LOGS) || path.endsWith("_seeds")
			|| path.contains("flower") || path.endsWith("_tulip") || stack.is(Items.SUGAR_CANE) || stack.is(Items.WHEAT)) {
			return "plant";
		}
		if (stack.is(Items.BONE) || stack.is(Items.STRING) || stack.is(Items.GUNPOWDER) || stack.is(Items.SPIDER_EYE) || stack.is(Items.SLIME_BALL)
			|| stack.is(Items.FEATHER) || stack.is(Items.LEATHER) || stack.is(Items.ENDER_EYE) || stack.is(Items.PHANTOM_MEMBRANE)
			|| stack.is(Items.INK_SAC) || stack.is(Items.RABBIT_HIDE)) {
			return "drop";
		}
		if (stack.getItem() instanceof BlockItem) {
			return "block";
		}
		return "misc";
	}

	/** The number a hint gives: the food it is, or how long it lasts; else how many fit together. */
	private static Object figure(ItemStack stack, String kind) {
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food != null) {
			return food.nutrition();
		}
		if (stack.isDamageableItem()) {
			return stack.getMaxDamage();
		}
		return stack.getMaxStackSize();
	}
}
