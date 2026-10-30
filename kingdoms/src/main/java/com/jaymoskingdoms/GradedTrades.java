package com.jaymoskingdoms;

import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.Nullable;

/**
 * The smiths of a realm's towns sell better-forged gear to those the realm thinks well of. With Jaymo's
 * Powers, a weapon, tool or armour piece in an armourer's, weaponsmith's or toolsmith's trades on a realm's land gets a
 * forging grade by the buyer's reputation with that realm: from mostly Poorly Forged for the hated, through Well Forged
 * for strangers, to Expertly, Perfectly and (for the revered) now and then Masterwork gear. The grade is only set here
 * (Powers' own component, by id, so neither mod needs the other); Powers bakes it into the item's numbers the first
 * time it is in a pack. An offer's roll is fixed by the buyer and the offer, so reopening the trade shows the same
 * grade and a rise in reputation only ever raises it; an offer someone has bought from is left as it was.
 */
public final class GradedTrades {
	/** Powers' grade component (a grade id string). */
	private static final Identifier GRADE_ID = Identifier.fromNamespaceAndPath("jaymospowers", "forging_grade");
	private static final String[] GRADES = {"poor", "well", "expert", "perfect", "master"};

	/** Reputation (-1000 to 1000) at which each chance starts to rise and where it tops out. */
	static final int MASTER_FROM = 500, PERFECT_FROM = 200, EXPERT_FROM = -500, TOP = 1000;
	/** The chance of a Masterwork, of at least Perfect, of at least Expert, at the very top reputation. */
	static final float MASTER_MAX = 0.05F, PERFECT_MAX = 0.22F, EXPERT_MAX = 0.60F;
	/** The chance of Poorly Forged gear at the bottom of reputation, and the reputation above which there is none. */
	static final float POOR_MAX = 0.70F;
	static final int POOR_UNTIL = 300, BOTTOM = -1000;

	private static boolean looked;
	private static @Nullable DataComponentType<String> grade;

	private GradedTrades() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && world instanceof ServerLevel level
				&& entity instanceof Villager v && v.isAlive() && !v.isBaby() && v.getTradingPlayer() == null && smith(v)) {
				grade(level, sp, v);
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean smith(Villager v) {
		var profession = v.getVillagerData().profession();
		return profession.is(VillagerProfession.ARMORER) || profession.is(VillagerProfession.WEAPONSMITH)
			|| profession.is(VillagerProfession.TOOLSMITH);
	}

	private static @Nullable DataComponentType<String> component() {
		if (!looked) {
			looked = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				@SuppressWarnings("unchecked")
				DataComponentType<String> type = (DataComponentType<String>)BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(GRADE_ID).orElse(null);
				grade = type;
			}
		}
		return grade;
	}

	private static void grade(ServerLevel level, ServerPlayer player, Villager v) {
		DataComponentType<String> type = component();
		if (type == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction owner = Banners.owner(server, ChunkPos.containing(v.blockPosition()));
		if (owner == null || owner.fallen() || !owner.type().isRealm()) {
			return;
		}
		int rep = Reputation.get(player, owner.id());
		int index = 0;
		for (MerchantOffer offer : v.getOffers()) {
			ItemStack result = offer.getResult();
			if (offer.getUses() == 0 && gear(result)) {
				// (0.7.383) and never above the grade the buyer's world tier allows (TierGate)
				result.set(type, GRADES[Math.min(roll(rep, draw(player.getUUID(), v.getUUID(), index)), TierGate.maxGrade(player))]);
			}
			index++;
		}
	}

	/** Gear that takes a grade: a damageable tool, weapon or armour piece. */
	private static boolean gear(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem()
			&& (stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON) || stack.has(DataComponents.EQUIPPABLE));
	}

	/** A steady number in [0, 1) for this buyer, seller and offer. */
	static float draw(UUID buyer, UUID seller, int offer) {
		long mix = buyer.getMostSignificantBits() * 31 + buyer.getLeastSignificantBits();
		mix = mix * 31 + seller.getMostSignificantBits();
		mix = mix * 31 + seller.getLeastSignificantBits();
		mix = (mix * 31 + offer) * 0x9E3779B97F4A7C15L;
		mix ^= mix >>> 32;
		return (float)((mix >>> 11 & ((1L << 24) - 1)) / (double)(1L << 24));
	}

	/** The grade (index into poor, well, expert, perfect, master) a draw {@code u} earns at reputation {@code rep}. */
	static int roll(int rep, float u) {
		float master = ramp(rep, MASTER_FROM, TOP, 0.0F, MASTER_MAX);
		float perfect = Math.max(master, ramp(rep, PERFECT_FROM, TOP, 0.0F, PERFECT_MAX));
		float expert = Math.max(perfect, ramp(rep, EXPERT_FROM, TOP, 0.0F, EXPERT_MAX));
		float poor = ramp(rep, BOTTOM, POOR_UNTIL, POOR_MAX, 0.0F);
		if (u < master) {
			return 4;
		}
		if (u < perfect) {
			return 3;
		}
		if (u < expert) {
			return 2;
		}
		return u >= 1.0F - poor ? 0 : 1;
	}

	private static float ramp(int rep, int from, int to, float low, float high) {
		return Mth.lerp(Mth.clamp((rep - from) / (float)(to - from), 0.0F, 1.0F), low, high);
	}
}
