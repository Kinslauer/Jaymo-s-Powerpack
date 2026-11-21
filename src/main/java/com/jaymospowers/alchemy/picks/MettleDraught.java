package com.jaymospowers.alchemy.picks;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * The Draught of Mettle: a rare infusion for people with no power. For {@link #TICKS} after it is drunk they run faster,
 * jump higher, swim quicker, grow hungry more slowly, and have one more jump in the air (the jump itself is the client's,
 * {@code client/alchemy/MettleJump}). A player who holds a power gets nothing from it but a sick stomach.
 */
public final class MettleDraught {
	/** How long it lasts: 8 minutes. */
	public static final int TICKS = 20 * 60 * 8;
	/** Speed I and Jump Boost II; Dolphin's Grace is the quicker swimming. */
	public static final int SPEED_LEVEL = 0, JUMP_LEVEL = 1;
	/** Slower hunger: every {@link #HUNGER_EVERY} ticks the drinker is given {@link #HUNGER_BACK} saturation back (about half of what a busy player spends). */
	public static final int HUNGER_EVERY = 100;
	public static final float HUNGER_BACK = 0.4F;
	/** What a drinker with a power suffers (ticks of nausea). */
	public static final int SICK_TICKS = 200;

	/** The game time the draught's strength runs out (synced to its drinker, for the mid-air jump). Saved; not kept through death. */
	public static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(JaymosPowers.id("mettle_until"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));

	public static Item DRAUGHT;

	private MettleDraught() {
	}

	static void init() {
		DRAUGHT = Alchemy.item("mettle_draught", p -> new DraughtItem(p.stacksTo(1).rarity(Rarity.RARE).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build())));
		Research.register(new Research.Entry("mettle", Research.Category.INFUSION, () -> DRAUGHT,
			EssenceList.of(Essence.AIR, 16, Essence.SPIRIT, 12).plus(Essence.WATER, 8).plus(Essence.SALT, 8), List.of("ichor", "stability"), 0, 3, 1));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	/** Whether {@code player}'s draught is still working (on either side: the attachment is synced to its owner). */
	public static boolean active(Player player) {
		Long until = player.getAttached(UNTIL);
		return until != null && player.level().getGameTime() < until;
	}

	private static void tick(ServerPlayer player) {
		Long until = player.getAttached(UNTIL);
		if (until == null) {
			return;
		}
		// worn off, or a power came to the drinker meanwhile: it is over
		if (player.level().getGameTime() >= until || Powers.of(player) != null) {
			player.removeAttached(UNTIL);
			return;
		}
		if (player.tickCount % HUNGER_EVERY == 0) {
			player.getFoodData().setSaturation(Math.min(player.getFoodData().getSaturationLevel() + HUNGER_BACK, player.getFoodData().getFoodLevel()));
		}
	}

	/** The draught. */
	public static class DraughtItem extends Item {
		public DraughtItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
				if (Powers.of(player) != null) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SICK_TICKS, 0));
				} else {
					int left = TICKS;
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, left, SPEED_LEVEL));
					player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, left, JUMP_LEVEL));
					player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, left, 0));
					player.setAttached(UNTIL, server.getGameTime() + left);
				}
			}
			return super.finishUsingItem(stack, level, entity);
		}
	}
}
