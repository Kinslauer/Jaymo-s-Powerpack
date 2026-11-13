package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jspecify.annotations.Nullable;

/**
 * Unknown properties. A found magic thing (a bauble from a chest, a relic out of a ruin) shows what it does only once it is
 * known. Four ways: use it a while (bonded, held or worn: ten minutes; Jaymo's Powers and {@link Relics} count it), pay a court
 * wizard to look at it (hold it and speak to them), the identify ritual ({@link Rituals#onIdentify}), or look it up: the
 * "found things" line of study at a library or with a sage ({@link Research}). Crafted gear is never unknown.
 */
public final class Unknowns {
	/** A court wizard's fee for looking at a thing, in coin. */
	static final int WIZARD_FEE = 6;
	/** How many unknown things are listed to look up at once. */
	static final int LISTED = 6;

	private Unknowns() {
	}

	static void register() {
		Rituals.onIdentify(reveal -> {
			ItemStack stack = reveal.stack();
			if (ItemBonds.unknown(stack)) {
				ItemBonds.reveal(stack);
				reveal.lines().addAll(lines(reveal.player(), stack));
			}
		});
		Research.addTopic(new Research.Topic() {
			@Override
			public String id() {
				return "found";
			}

			@Override
			public Component title() {
				return Component.translatable("research.jaymoskingdoms.topic.found");
			}

			@Override
			public List<Research.Subject> subjects(ServerPlayer player) {
				List<Research.Subject> out = new ArrayList<>();
				List<ItemStack> all = held(player);
				for (int i = 0; i < all.size() && out.size() < LISTED; i++) {
					if (ItemBonds.unknown(all.get(i))) {
						out.add(new Research.Subject(Integer.toString(i), all.get(i).getHoverName()));
					}
				}
				return out;
			}

			@Override
			public Component learn(ServerPlayer player, String subject) {
				List<ItemStack> all = held(player);
				try {
					ItemStack stack = all.get(Integer.parseInt(subject));
					if (ItemBonds.unknown(stack)) {
						ItemBonds.reveal(stack);
						ItemBonds.resync(player);
						return Component.translatable("research.jaymoskingdoms.found.learned", stack.getHoverName());
					}
				} catch (NumberFormatException | IndexOutOfBoundsException e) {
					// the thing has gone
				}
				return Component.translatable("research.jaymoskingdoms.found.gone");
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && !sp.isShiftKeyDown()
				&& CourtWizards.is(entity) && ItemBonds.unknown(sp.getMainHandItem()) && entity instanceof LivingEntity wizard) {
				List<Menus.Choice> choices = new ArrayList<>();
				choices.add(Menus.Choice.of(Component.translatable("unknown.jaymoskingdoms.pay", WIZARD_FEE), Money.canPay(sp, Money.worth(WIZARD_FEE)),
					p -> study(server, p, wizard)));
				Menus.ask(sp, wizard, Component.translatable("unknown.jaymoskingdoms.wizard"), choices);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	/** Everything {@code player} has that can be unknown: carried, worn, then the pack, in a stable order. */
	static List<ItemStack> held(ServerPlayer player) {
		List<ItemStack> all = new ArrayList<>(ItemBonds.carried(player));
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			all.add(player.getInventory().getItem(i));
		}
		return all;
	}

	private static void study(ServerLevel level, ServerPlayer player, LivingEntity wizard) {
		ItemStack stack = player.getMainHandItem();
		if (!ItemBonds.unknown(stack) || !Money.charge(player, Money.worth(WIZARD_FEE))) {
			return;
		}
		ItemBonds.reveal(stack);
		ItemBonds.resync(player);
		level.playSound(null, wizard.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 1.1F);
		Speech.tell(level, wizard, player, Component.translatable("unknown.jaymoskingdoms.told", stack.getHoverName()), Speech.WHITE);
	}

	/** What a known thing's tooltip says (its name left out), for the identify ritual's page. */
	static List<Component> lines(ServerPlayer player, ItemStack stack) {
		List<Component> out = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL));
		if (!out.isEmpty()) {
			out.removeFirst();
		}
		return out;
	}

	/** Marks {@code stack} known without a sound (a thing you made or were given by a maker's hand). */
	static void know(@Nullable ItemStack stack) {
		if (stack != null && ItemBonds.unknown(stack)) {
			ItemBonds.reveal(stack);
		}
	}
}
