package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * Forged papers. They are never crafted: the fence at a speakeasy sells them, and so do the especially shady crooks of a
 * hotbed ({@link Underworld#stock}).
 * <ul>
 * <li>A <b>forged travel pass</b> is shown to a guard at a checkpoint (the halt in {@link Approaches}), at a haven's gate
 * ({@link HavenGates}) and wherever a guard would stop you; if it holds, he lets you by for the day.</li>
 * <li>A <b>forged pardon</b> is shown when you are taken for sentencing ({@link Busts}); if it holds, the sentence is
 * dropped.</li>
 * </ul>
 * Each showing uses up a paper. A guard's Wits ({@link #wits}, fixed by who he is, 20 to 80) may spot the fake: the
 * chance is {@link #SPOT_BASE} plus {@link #SPOT_PER_WIT} a point, less your Presence ({@link Presence#talk}). A spotted
 * fake is forgery, a crime ({@link Theft.Crime#FORGERY}), and the guard keeps the paper.
 */
public final class ForgedPapers {
	/** The chance a guard of no Wits spots a fake, and what each point of his Wits adds. */
	static final float SPOT_BASE = 0.1F, SPOT_PER_WIT = 0.006F;
	/** A guard's Wits run from this to this plus the spread. */
	static final int WITS_MIN = 20, WITS_SPREAD = 60;
	/** Ticks a pass that held is good for with a realm's guards. */
	static final long PASS_TICKS = 24000L;
	/** The price of a pass and a pardon at a fence, in emeralds; a hotbed's crook asks this much more. */
	public static final int PASS_PRICE = 14, PARDON_PRICE = 30, CROOK_EXTRA = 8;
	public static final int STOCK = 3;

	public static Item PASS, PARDON;

	private record Seen(UUID player, int realm) {
	}

	/** Passes shown today: held (good until the game time) or spotted (-1 until). */
	private static final Map<Seen, Long> GOOD = new HashMap<>();
	private static final Map<Seen, Long> SPOTTED = new HashMap<>();

	private ForgedPapers() {
	}

	static void register() {
		PASS = paper("forged_pass");
		PARDON = paper("forged_pardon");
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(PASS);
			output.accept(PARDON);
		});
	}

	private static Item paper(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(8).rarity(Rarity.UNCOMMON)));
	}

	/** A guard's Wits: who he is decides. */
	static int wits(LivingEntity guard) {
		long h = guard.getUUID().getLeastSignificantBits() ^ guard.getUUID().getMostSignificantBits() * 31L;
		return WITS_MIN + (int)Math.floorMod(h >>> 7, (long)WITS_SPREAD + 1L);
	}

	/** The chance {@code guard} spots a fake shown by {@code player}. */
	static float spot(ServerPlayer player, LivingEntity guard) {
		return Math.max(0.02F, Math.min(0.95F, SPOT_BASE + SPOT_PER_WIT * wits(guard) - Presence.talk(player)));
	}

	private static boolean take(ServerPlayer player, Item paper) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(paper)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	/** Whether {@code player} carries {@code paper}. */
	static boolean holds(ServerPlayer player, Item paper) {
		return player.getInventory().contains(stack -> stack.is(paper));
	}

	/** A pass already held up to {@code f}'s guards today. */
	public static boolean passed(ServerPlayer player, Faction f) {
		Long until = GOOD.get(new Seen(player.getUUID(), f.id()));
		return until != null && player.level().getGameTime() < until;
	}

	/**
	 * {@code player} holds up a forged pass to {@code guard} of {@code f}: used up, then held or spotted. True if it held
	 * (or had held already); a spotted fake is forgery and the guard keeps it.
	 */
	static boolean show(ServerLevel level, ServerPlayer player, LivingEntity guard, Faction f) {
		if (passed(player, f)) {
			return true;
		}
		Seen seen = new Seen(player.getUUID(), f.id());
		Long bad = SPOTTED.get(seen);
		long now = level.getGameTime();
		if (bad != null && now < bad) {
			return false;
		}
		if (!take(player, PASS)) {
			return false;
		}
		if (level.getRandom().nextFloat() < spot(player, guard)) {
			SPOTTED.put(seen, now + PASS_TICKS);
			Speech.tell(level, guard, player, Component.translatable("papers.jaymoskingdoms.spotted." + level.getRandom().nextInt(2)), Speech.RED);
			Theft.commit(player, level, f, Theft.Crime.FORGERY, guard.blockPosition(), null, true);
			Busts.charge(player, f, "forgery");
			return false;
		}
		GOOD.put(seen, now + PASS_TICKS);
		Speech.tell(level, guard, player, Component.translatable("papers.jaymoskingdoms.passed." + level.getRandom().nextInt(2)), Speech.YELLOW);
		Skills.grant(player, Skills.Pillar.STEALTH, 2);
		return true;
	}

	/** At a gate or checkpoint: the pass in the pack is shown if there is one. */
	static boolean atGate(ServerLevel level, ServerPlayer player, LivingEntity guard, Faction f) {
		return passed(player, f) || holds(player, PASS) && show(level, player, guard, f);
	}

	/** What a sentencing guard makes of a pardon: 0 none held, 1 waived, 2 spotted (and the crime is forgery). */
	static int pardon(ServerLevel level, ServerPlayer player, LivingEntity guard, Faction f) {
		if (!take(player, PARDON)) {
			return 0;
		}
		if (Liars.honest(guard) || level.getRandom().nextFloat() < spot(player, guard)) {
			Speech.tell(level, guard, player, Component.translatable("papers.jaymoskingdoms.pardon_spotted." + level.getRandom().nextInt(2)), Speech.RED);
			return 2;
		}
		Speech.tell(level, guard, player, Component.translatable("papers.jaymoskingdoms.pardon_held." + level.getRandom().nextInt(2)), Speech.YELLOW);
		return 1;
	}
}
