package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Gags (fifth edition's verbal components, {@link SpellParts}): a rag gag tied on a captive, a prisoner in chains or a
 * netted or downed foe, and they cannot speak a spell. A gagged captive can only mumble when they plead
 * ({@link Captives}). Take it off with an empty hand. It stays on as long as they are helpless; a minute after they are
 * free again it slips off and falls.
 */
public final class Gags {
	/** How long (ticks) someone has been free before the rag slips off. */
	static final long SLIPS_AFTER = 20L * 60L;
	/** How often (ticks) the gagged are looked at. */
	static final int EVERY = 20;

	public static Item RAG;

	/** On someone gagged. Saved with them. */
	public static final AttachmentType<Boolean> GAGGED = AttachmentRegistry.create(JaymosKingdoms.id("gagged"), b -> b.persistent(Codec.BOOL));

	/** The gagged who are loaded now, and since when each has been free (game time; absent while helpless). */
	private static final Set<LivingEntity> WORN = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<LivingEntity, Long> FREE_SINCE = new WeakHashMap<>();

	private Gags() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("gag attachment: {}", GAGGED);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("rag_gag"));
		RAG = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(16)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(RAG));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WORN.clear();
			FREE_SINCE.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living && gagged(living)) {
				WORN.add(living);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Gags::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof LivingEntity who) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(RAG) && !gagged(who) && helpless(who)) {
				gag(who);
				held.consume(1, player);
				return InteractionResult.SUCCESS;
			}
			if (held.isEmpty() && gagged(who) && player.isShiftKeyDown()) {
				ungag(who, player);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code who} wears a gag. */
	public static boolean gagged(LivingEntity who) {
		return Boolean.TRUE.equals(who.getAttached(GAGGED));
	}

	/** Whether {@code who} cannot stop a gag going on: a captive in chains, held fast, netted, downed or tied. */
	static boolean helpless(LivingEntity who) {
		return Captives.bound(who) || HeldFast.locked(who) || Nets.netted(who) || Downed.isDowned(who) || PowersCompat.helpless(who)
			|| who.hasAttached(Shackles.TETHER);
	}

	/** A gag goes on {@code who}. */
	public static void gag(LivingEntity who) {
		who.setAttached(GAGGED, true);
		WORN.add(who);
		FREE_SINCE.remove(who);
		if (who.level() instanceof ServerLevel level) {
			level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8F, 0.9F);
		}
	}

	/** The gag comes off {@code who}; {@code by} (if any) gets the rag, else it falls. */
	public static void ungag(LivingEntity who, LivingEntity by) {
		if (!gagged(who)) {
			return;
		}
		who.removeAttached(GAGGED);
		WORN.remove(who);
		FREE_SINCE.remove(who);
		ItemStack rag = new ItemStack(RAG);
		if (by instanceof Player player) {
			if (!player.getInventory().add(rag)) {
				player.spawnAtLocation((ServerLevel) player.level(), rag);
			}
		} else if (who.level() instanceof ServerLevel level) {
			who.spawnAtLocation(level, rag);
		}
		who.level().playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8F, 1.1F);
	}

	private static void tick(MinecraftServer server) {
		if (WORN.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (LivingEntity who : java.util.List.copyOf(WORN)) {
			if (who.isRemoved() || !who.isAlive() || !gagged(who)) {
				WORN.remove(who);
				continue;
			}
			long now = who.level().getGameTime();
			if (helpless(who)) {
				FREE_SINCE.remove(who);
			} else if (now - FREE_SINCE.computeIfAbsent(who, w -> now) >= SLIPS_AFTER) {
				ungag(who, who);
			}
		}
	}
}
