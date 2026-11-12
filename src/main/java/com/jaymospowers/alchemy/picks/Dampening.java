package com.jaymospowers.alchemy.picks;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * Dampening: a brew that shuts a power away for a while. Drunk as the {@link #DAMPENING_DRAUGHT}, or put on a dart (the
 * draught makes tipped arrows, as the game's own tipped arrows do, so a dart's dose is the shorter one) or in a splash vial,
 * through the {@link #DAMPENED} effect and its potion. While a player is dampened their power's keys do nothing
 * ({@link Powers#silenced}) and its passive tick pauses ({@link Powers#paused}); it always wears off and never takes the
 * power away. A great foe (a boss, anything with {@link #GREAT_HEALTH} health or more) shrugs most of it off.
 */
public final class Dampening {
	// ---- tuning ----
	/** The draught's length (ticks): five minutes. The potion a dart or vial carries is the same; a tipped arrow gives an eighth of it. */
	public static final int DRAUGHT_TICKS = 20 * 60 * 5;
	/** A great foe keeps this share of the time. */
	public static final float GREAT_SHARE = 0.1F;
	/** Health from which a creature counts as a great foe. */
	public static final float GREAT_HEALTH = 100.0F;
	public static final String RESEARCH = "dampening";

	public static Holder<MobEffect> DAMPENED;
	public static Holder<Potion> POTION;
	public static Item DAMPENING_DRAUGHT;

	/** Great foes that just caught it and have to have the time cut (done out of the effect tick, which iterates the effects). */
	private static final List<LivingEntity> TRIM = new ArrayList<>();

	private Dampening() {
	}

	static void init() {
		DAMPENED = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, JaymosPowers.id("dampened")),
			new DampenedEffect());
		POTION = Registry.registerForHolder(BuiltInRegistries.POTION, JaymosPowers.id("dampening"),
			new Potion("dampening", new MobEffectInstance(DAMPENED, DRAUGHT_TICKS)));
		DAMPENING_DRAUGHT = Alchemy.item("dampening_draught", p -> new DraughtItem(p.stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK)));
		Research.register(new Research.Entry(RESEARCH, Research.Category.CRUCIBLE, () -> DAMPENING_DRAUGHT,
			EssenceList.of(Essence.MIND, 10, Essence.SALT, 10).plus(Essence.SHADOW, 6), List.of("alembic"), 0, 3, 2));
		Powers.addSilencer(Dampening::dampened);
		Powers.addPauser(Dampening::dampened);
		ServerTickEvents.END_SERVER_TICK.register(Dampening::tick);
	}

	/** Whether {@code player}'s power is shut away. */
	public static boolean dampened(ServerPlayer player) {
		return DAMPENED != null && player.hasEffect(DAMPENED);
	}

	/** A great foe: a boss or anything with a great deal of health. */
	public static boolean great(LivingEntity entity) {
		return !(entity instanceof ServerPlayer) && entity.getMaxHealth() >= GREAT_HEALTH;
	}

	/** The draught: drunk, it dampens the drinker for {@link #DRAUGHT_TICKS}. */
	public static class DraughtItem extends Item {
		public DraughtItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			ItemStack left = super.finishUsingItem(stack, level, entity);
			if (level instanceof ServerLevel) {
				entity.addEffect(new MobEffectInstance(DAMPENED, DRAUGHT_TICKS));
			}
			return left;
		}
	}

	/** The status itself: a clear line and an icon; the work is in {@link Powers} (which asks {@link #dampened}). */
	public static class DampenedEffect extends MobEffect {
		public DampenedEffect() {
			super(MobEffectCategory.HARMFUL, 0x7C8791, MagicFx.PALE);
		}

		@Override
		public void onEffectStarted(LivingEntity entity, int amplifier) {
			if (entity instanceof ServerPlayer player) {
				Powers.releaseKeys(player);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.dampened").withStyle(ChatFormatting.GRAY));
			} else if (great(entity) && !TRIM.contains(entity)) {
				TRIM.add(entity);
			}
		}

		@Override
		public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
			return true;
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return false;
		}
	}

	private static void tick(MinecraftServer server) {
		if (TRIM.isEmpty()) {
			return;
		}
		List<LivingEntity> caught = new ArrayList<>(TRIM);
		TRIM.clear();
		for (LivingEntity entity : caught) {
			MobEffectInstance dose = entity.getEffect(DAMPENED);
			if (dose == null || !entity.isAlive()) {
				continue;
			}
			// a fresh, short dose in place of the long one (a dose already trimmed is not trimmed again: it is too short)
			if (dose.getDuration() > Math.round(DRAUGHT_TICKS * GREAT_SHARE) + 1) {
				entity.removeEffect(DAMPENED);
				entity.addEffect(dose.withScaledDuration(GREAT_SHARE));
			}
		}
	}
}
