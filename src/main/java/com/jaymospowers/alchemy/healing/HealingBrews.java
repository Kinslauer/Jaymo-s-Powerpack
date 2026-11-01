package com.jaymospowers.alchemy.healing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * (0.19.20) The two healing brews of the apothecary, made in the crucible from herbs (data
 * {@code alchemy/crucible/bone_knitting_elixir.json} and {@code draught_of_vigour.json}) and drunk like a potion; the glass
 * bottle is handed back.
 * <ul>
 * <li><b>Bone-knitting elixir:</b> for {@link #KNIT_SECONDS} the worst broken limb is set and knits, a step each second;
 * once the break is gone the limb carries on to {@link #KNIT_TO} of itself (another's craft, but a limb's, not the whole
 * body's). A second dose restarts the time.</li>
 * <li><b>Draught of vigour:</b> for {@link #VIGOUR_SECONDS} every part heals a sixtieth-of-its-health-a-second share, past
 * the 60% line self-care stops at.</li>
 * </ul>
 * Both are researched in the Codex ({@code bonesetting}, then {@code vigour}).
 */
public final class HealingBrews {
	// ---- tuning ----
	/** (0.19.20) How long the elixir works (seconds), and the share of a limb it heals up to. */
	public static final int KNIT_SECONDS = 120;
	public static final float KNIT_TO = 0.6F;
	/** (0.19.20) How long the draught works (seconds): every part heals its whole health over this. */
	public static final int VIGOUR_SECONDS = 60;
	/** (0.19.20) Drink stack size. */
	public static final int STACK = 16;

	/** Game time the elixir wears off at, on whoever drank it. */
	public static final AttachmentType<Long> KNIT_UNTIL = AttachmentRegistry.create(JaymosPowers.id("alchemy_knit_until"),
		builder -> builder.persistent(Codec.LONG));
	/** Game time the draught wears off at. */
	public static final AttachmentType<Long> VIGOUR_UNTIL = AttachmentRegistry.create(JaymosPowers.id("alchemy_vigour_until"),
		builder -> builder.persistent(Codec.LONG));

	public static Item BONE_KNITTING_ELIXIR, DRAUGHT_OF_VIGOUR;

	private HealingBrews() {
	}

	public static void init() {
		BONE_KNITTING_ELIXIR = Alchemy.item("bone_knitting_elixir", p -> new BrewItem(p.stacksTo(STACK).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK), true));
		DRAUGHT_OF_VIGOUR = Alchemy.item("draught_of_vigour", p -> new BrewItem(p.stacksTo(STACK).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK), false));
		Research.Category tab = Research.Category.CRUCIBLE;
		Research.register(new Research.Entry("bonesetting", tab, () -> BONE_KNITTING_ELIXIR,
			EssenceList.of(Essence.LIFE, 10, Essence.PLANT, 8).plus(Essence.EARTH, 6), List.of("crucible"), 0, 1, 3));
		Research.register(new Research.Entry("vigour", tab, () -> DRAUGHT_OF_VIGOUR,
			EssenceList.of(Essence.LIFE, 14, Essence.PLANT, 10).plus(Essence.LIGHT, 8), List.of("bonesetting"), 0, 2, 3));
		ServerTickEvents.END_SERVER_TICK.register(HealingBrews::tick);
	}

	/** A bottle of one of the two brews: drinking starts its work. */
	public static class BrewItem extends Item {
		private final boolean knit;

		public BrewItem(Item.Properties properties, boolean knit) {
			super(properties);
			this.knit = knit;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			ItemStack left = super.finishUsingItem(stack, level, entity);
			if (level instanceof ServerLevel && entity instanceof ServerPlayer player) {
				long now = level.getGameTime();
				if (this.knit) {
					player.setAttached(KNIT_UNTIL, now + KNIT_SECONDS * 20L);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.brew.knit").withStyle(ChatFormatting.GREEN));
				} else {
					player.setAttached(VIGOUR_UNTIL, now + VIGOUR_SECONDS * 20L);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.brew.vigour").withStyle(ChatFormatting.GREEN));
				}
			}
			return left;
		}
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive()) {
				continue;
			}
			long now = player.level().getGameTime();
			Long knit = player.getAttached(KNIT_UNTIL);
			if (knit != null) {
				if (now >= knit) {
					player.removeAttached(KNIT_UNTIL);
				} else {
					knitStep(player);
				}
			}
			Long vigour = player.getAttached(VIGOUR_UNTIL);
			if (vigour != null) {
				if (now >= vigour) {
					player.removeAttached(VIGOUR_UNTIL);
				} else {
					vigourStep(player);
				}
			}
		}
	}

	/** One second of the elixir: the worst broken limb is set, and heals by a {@link #KNIT_SECONDS}th of the share it knits to. */
	private static void knitStep(ServerPlayer player) {
		BodyPlan plan = Wounds.plan(player);
		if (plan == null) {
			return;
		}
		int worst = -1;
		float least = 2.0F;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (Wounds.isBroken(player, part) && Wounds.fraction(player, part) < least) {
				least = Wounds.fraction(player, part);
				worst = i;
			}
		}
		if (worst < 0) {
			return;
		}
		BodyPart part = plan.part(worst);
		Wounds.setPart(player, part);
		float room = Wounds.cap(player, plan, worst) * KNIT_TO - Wounds.points(player, part);
		float step = Wounds.cap(player, plan, worst) * KNIT_TO / KNIT_SECONDS;
		if (room > 0.0F) {
			Wounds.healPart(player, part, Math.min(step, room));
		}
	}

	/** One second of the draught: every part heals a {@link #VIGOUR_SECONDS}th of its health. */
	private static void vigourStep(ServerPlayer player) {
		BodyPlan plan = Wounds.plan(player);
		if (plan == null) {
			return;
		}
		for (int i = 0; i < plan.size(); i++) {
			Wounds.healPart(player, plan.part(i), Wounds.cap(player, plan, i) / VIGOUR_SECONDS);
		}
	}
}
