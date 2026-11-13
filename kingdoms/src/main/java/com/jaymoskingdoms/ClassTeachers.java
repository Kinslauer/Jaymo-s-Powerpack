package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.crafting.Recipe;

/**
 * Who teaches the new classes their craft. No class starts with gear (the recipe is the first thing a teacher gives): a village
 * priest teaches a Cleric or a Paladin the holy symbol, and, for a price, lifts a Paladin's broken oath (atonement); a farmer or a
 * shepherd teaches a Druid the sprig; a librarian teaches a Warlock the pact seal; the village smiths teach a Tinkerer to make
 * the gadgets ({@link Tinkerers#GADGETS}). Sneak and use the villager with an empty hand. A priest's lessons come in with their
 * other services ({@link Restoration#services}).
 */
public final class ClassTeachers {
	/** The silver a lesson costs. */
	static final int LESSON_SILVER = 4;

	private static final Map<String, List<ResourceKey<Recipe<?>>>> RECIPES = new HashMap<>();
	private record Offer(int teacher, List<String> kinds) {
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	static {
		RECIPES.put("teach_symbol", List.of(recipe("holy_symbol")));
		RECIPES.put("teach_sprig", List.of(recipe("druid_sprig")));
		RECIPES.put("teach_seal", List.of(recipe("pact_seal")));
		List<ResourceKey<Recipe<?>>> gadgets = new ArrayList<>();
		for (String g : Tinkerers.GADGETS) {
			gadgets.add(recipe(g));
		}
		RECIPES.put("teach_gadgets", gadgets);
	}

	private ClassTeachers() {
	}

	private static ResourceKey<Recipe<?>> recipe(String id) {
		return ResourceKey.create(Registries.RECIPE, JaymosKingdoms.id(id));
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
				|| !(entity instanceof Villager v) || v.isBaby() || Restoration.cleric(v) || CourtWizards.is(v) || !sp.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			List<String> kinds = new ArrayList<>(services(sp, v));
			if (kinds.isEmpty()) {
				return InteractionResult.PASS;
			}
			offer(sp, v, kinds);
			return InteractionResult.SUCCESS;
		});
	}

	static void clear() {
		OFFERS.clear();
	}

	/** The lessons {@code teacher} has for {@code player} now (option kinds). */
	static List<String> services(ServerPlayer player, LivingEntity teacher) {
		List<String> out = new ArrayList<>();
		if (!(teacher instanceof Villager v)) {
			return out;
		}
		if (Classes.classOf(player) == null) {
			return out;
		}
		boolean priest = Restoration.cleric(v);
		for (PlayerClass cls : Classes.both(player)) {
		switch (cls) {
			case CLERIC, PALADIN -> {
				if (priest) {
					out.add("teach_symbol");
				}
				if (priest && cls == PlayerClass.PALADIN && player.getAttachedOrElse(Paladins.BROKEN, false)) {
					out.add("atone");
				}
			}
			case DRUID -> {
				if (v.getVillagerData().profession().is(VillagerProfession.FARMER) || v.getVillagerData().profession().is(VillagerProfession.SHEPHERD)) {
					out.add("teach_sprig");
				}
			}
			case WARLOCK -> {
				if (v.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
					out.add("teach_seal");
				}
			}
			case TINKERER -> {
				var p = v.getVillagerData().profession();
				if (p.is(VillagerProfession.TOOLSMITH) || p.is(VillagerProfession.WEAPONSMITH) || p.is(VillagerProfession.ARMORER)) {
					out.add("teach_gadgets");
				}
			}
			default -> {
			}
		}
		}
		out.addAll(SecondClass.offers(player, v));
		out.removeIf(k -> RECIPES.containsKey(k) && known(player, k));
		return out;
	}

	static boolean known(ServerPlayer player, String kind) {
		List<ResourceKey<Recipe<?>>> all = RECIPES.get(kind);
		return all != null && all.stream().allMatch(r -> player.getRecipeBook().contains(r));
	}

	static boolean handles(String kind) {
		return kind.equals("atone") || kind.startsWith("learn:") || RECIPES.containsKey(kind);
	}

	private static int price(String kind) {
		return kind.equals("atone") ? Paladins.ATONE_SILVER : kind.startsWith("learn:") ? SecondClass.FEE_SILVER : LESSON_SILVER;
	}

	static Component line(ServerPlayer player, String kind) {
		if (kind.startsWith("learn:")) {
			return Component.translatable("talk.jaymoskingdoms.teacher.learn", Component.translatable("class.jaymoskingdoms." + kind.substring(6)),
				Money.text(player, Money.worth(price(kind))));
		}
		return Component.translatable("talk.jaymoskingdoms.teacher." + kind, Money.text(player, Money.worth(price(kind))));
	}

	static boolean open(ServerPlayer player, String kind) {
		return Money.canPay(player, Money.worth(price(kind)));
	}

	private static void offer(ServerPlayer player, Villager teacher, List<String> kinds) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		kinds.add("no");
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (String k : kinds) {
			options.add(k.equals("no") ? Component.translatable("talk.jaymoskingdoms.wizard.respec.no") : line(player, k));
			enabled.add(k.equals("no") || open(player, k));
		}
		OFFERS.put(player.getUUID(), new Offer(teacher.getId(), List.copyOf(kinds)));
		ServerPlayNetworking.send(player, new TalkNet.Ask(teacher.getId(), teacher.getDisplayName(), Component.translatable("talk.jaymoskingdoms.teacher.ask"), options, enabled));
	}

	/** An answer to a teacher's offer; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Offer offer = OFFERS.get(player.getUUID());
		if (offer == null || offer.teacher() != answer.entityId()) {
			return false;
		}
		OFFERS.remove(player.getUUID());
		if (answer.option() < 0 || answer.option() >= offer.kinds().size() || !(player.level().getEntity(answer.entityId()) instanceof LivingEntity teacher)
			|| teacher.distanceTo(player) > 8.0) {
			return true;
		}
		serve(player, teacher, offer.kinds().get(answer.option()));
		return true;
	}

	/** Teaches (or atones) for {@code kind}, if it can be paid for. */
	static boolean serve(ServerPlayer player, LivingEntity teacher, String kind) {
		if (!handles(kind)) {
			return false;
		}
		if (!services(player, teacher).contains(kind) || !open(player, kind)) {
			return true;
		}
		ServerLevel level = player.level();
		if (kind.startsWith("learn:")) {
			SecondClass.accept(player, teacher, kind);
			return true;
		}
		if (kind.equals("atone")) {
			Money.charge(player, Money.worth(price(kind)));
			Paladins.atone(player);
			Speech.tell(level, teacher, player, Component.translatable("talk.jaymoskingdoms.teacher.atoned"), Speech.GOLD);
			return true;
		}
		Money.charge(player, Money.worth(price(kind)));
		player.awardRecipesByKey(RECIPES.get(kind));
		Speech.tell(level, teacher, player, Component.translatable("talk.jaymoskingdoms.teacher.taught." + kind), Speech.GOLD);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.class.taught." + kind).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}
}
