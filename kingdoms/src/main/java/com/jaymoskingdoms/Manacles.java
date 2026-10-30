package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.414) Manacles, a watch recruit's iron: the Sworn road ({@link LifeStory.Start#SWORN}) comes with {@link #RECRUIT_PAIRS}
 * of them. Used on an outlaw ({@link Outlaws#is}) who is down (Powers' downed state, {@link Downed#isDowned}, or helpless), they
 * arrest them for the realm whose land it is, and the player is paid a bounty in emeralds ({@link #BOUNTY}) with a little goodwill
 * from the realm.
 * <ul>
 * <li>A downed looter with one of the realm's free guards within {@link #GUARD_SEARCH} is handed over ({@link Arrests#handOver}):
 * the guard has them up and marches them to the ruler for judgement, and the bounty is paid when the sentence is carried out
 * (all but a pardon).</li>
 * <li>Any other downed outlaw (a raider, an assassin, a masked bandit), or a looter with no guard near, is collected: a guard
 * of the realm comes for the prisoner and takes them off to the cells, which here means they are gone from the world, with a word
 * to the player, and the bounty is paid on the spot.</li>
 * </ul>
 * Not craftable: a recruit is issued them.
 */
public final class Manacles {
	/** The pairs a sworn recruit is issued, how far a guard of the realm is looked for (blocks), the bounty (emeralds) and the realm's goodwill for it. */
	static final int RECRUIT_PAIRS = 2, BOUNTY = 6, BOUNTY_REPUTATION = 4;
	static final double GUARD_SEARCH = 48.0;
	/** How notorious a manacled looter is held to be at judgement. */
	static final int NOTORIETY = 2;

	public static Item ITEM;

	/** Outlaw → the player whose manacles are on them, till the judge has sentenced them (the bounty is paid then). */
	private static final Map<UUID, Held> ARRESTED = new HashMap<>();

	/** Who manacled an outlaw, and when the claim lapses if no sentence comes (the crook died, the guard fell): a day. */
	private record Held(UUID by, long until) {
	}

	static final long CLAIM_TICKS = 24000L;

	private Manacles() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("manacles"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.SHEARS, ITEM));
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || sp.isSpectator()
				|| !sp.getMainHandItem().is(ITEM) || !(entity instanceof PathfinderMob outlaw) || !Outlaws.is(outlaw) || !outlaw.isAlive()) {
				return InteractionResult.PASS;
			}
			return manacle(sp, (ServerLevel)level, outlaw) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		});
		// the judge has sentenced one we arrested: the bounty, unless they were pardoned
		Arrests.SENTENCED.add((crook, faction, sentence) -> {
			Held held = ARRESTED.remove(crook.getUUID());
			UUID by = held == null || crook.level().getGameTime() > held.until() ? null : held.by();
			MinecraftServer server = crook.level().getServer();
			if (by != null && server != null && !"pardon".equals(sentence) && server.getPlayerList().getPlayer(by) instanceof ServerPlayer player) {
				Faction law = Factions.get(server, faction);
				if (law != null) {
					pay(player, law);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ARRESTED.clear());
	}

	/** {@code player} puts manacles on {@code outlaw}. False if they can't (not down, no law here): nothing is used. */
	private static boolean manacle(ServerPlayer player, ServerLevel level, PathfinderMob outlaw) {
		if (!Downed.isDowned(outlaw) && !PowersCompat.helpless(outlaw)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.manacles.up").withStyle(ChatFormatting.GRAY));
			return false;
		}
		MinecraftServer server = level.getServer();
		ARRESTED.values().removeIf(h -> level.getGameTime() > h.until());
		if (!hostile(outlaw)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.manacles.not_theirs").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (ARRESTED.containsKey(outlaw.getUUID()) || Arrests.held(outlaw)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.manacles.already").withStyle(ChatFormatting.GRAY));
			return false;
		}
		Faction law = Theft.lawAt(level, outlaw.blockPosition());
		if (law == null) {
			law = Factions.of(server, player.getUUID());
		}
		if (law == null) {
			law = Factions.realmNear(server, outlaw.blockPosition(), GUARD_SEARCH * 4);
		}
		if (law == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.manacles.no_law").withStyle(ChatFormatting.GRAY));
			return false;
		}
		PathfinderMob guard = null;
		double best = Double.MAX_VALUE;
		for (Worker w : Workers.near(level, outlaw.getBoundingBox().inflate(GUARD_SEARCH), Looters::free)) {
			double d = w.mob().distanceToSqr(outlaw);
			if (w.factionId() == law.id() && d < best) {
				best = d;
				guard = w.mob();
			}
		}
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		level.playSound(null, outlaw.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
		if (guard != null && Looters.is(outlaw)) {
			// a looter is led to the ruler like any a player brings in; the bounty comes with the sentence
			ARRESTED.put(outlaw.getUUID(), new Held(player.getUUID(), level.getGameTime() + CLAIM_TICKS));
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.manacles.led", law.name()).withStyle(ChatFormatting.GREEN));
			Arrests.handOver(level, outlaw, guard, law, "outlawry", NOTORIETY, player);
			return true;
		}
		// the rest are collected: taken off to the cells (out of the world), and the bounty paid now
		level.sendParticles(ParticleTypes.POOF, outlaw.getX(), outlaw.getY() + 0.6, outlaw.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, outlaw.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 0.8F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.manacles.taken", law.name()).withStyle(ChatFormatting.GREEN));
		McaCompat.vanish(outlaw);
		pay(player, law);
		return true;
	}

	/**
	 * Whether {@code outlaw} is one the watch takes in: a looter, or a camp's raider, a cove's hand or a greenskin's fighter.
	 * Never a chief, a captain, a king or warchief, a ruler, or a villager only wearing a mask or bandana.
	 */
	static boolean hostile(PathfinderMob outlaw) {
		if (Rulers.is(outlaw)) {
			return false;
		}
		if (Looters.is(outlaw)) {
			return true;
		}
		if (!(outlaw instanceof Worker w) || !Barbarians.isCampVillager(outlaw)) {
			return false;
		}
		int role = w.role();
		return role != Folk.CHIEF && role != Pirates.CAPTAIN && role != Goblins.KING && role != Orcs.WARCHIEF;
	}

	/** The realm's bounty for a manacled outlaw: emeralds in {@code player}'s pack (the floor if it's full) and a little goodwill. */
	private static void pay(ServerPlayer player, Faction law) {
		ItemStack purse = new ItemStack(Items.EMERALD, BOUNTY);
		player.getInventory().placeItemBackInInventory(purse, Prediction.SERVER_ONLY);
		Reputation.change(player, law, BOUNTY_REPUTATION);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.manacles.bounty", BOUNTY, law.name()).withStyle(ChatFormatting.GOLD));
	}
}
