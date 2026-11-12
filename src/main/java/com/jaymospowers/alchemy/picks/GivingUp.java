package com.jaymospowers.alchemy.picks;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.web.WebSymbiote;
import com.jaymospowers.power.xenoform.XenoBands;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.zfighter.ZFighterPower;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Giving a power up: the Draught of Release, a rare, costly infusion (late research) that makes the drinker give up their
 * power for good. Drinking is slow ({@link #DRINK_SECONDS}) and has to be done twice within {@link #CONFIRM_TICKS}: the first
 * draught only asks, and is not used up.
 *
 * <p><b>What it covers.</b> Xenoform: the Xeno-Band comes off with every alien it learned and is left on the ground for
 * whoever finds it. Every other power except the ones below: the power is sealed in a {@link #POWER_PHIAL}, left at the
 * drinker's feet; a powerless player who drinks it wakes that power, at its normal start (no mastery carried over).
 * <b>Refused</b> (the draught is not used up): no power; Z Fighter (a lineage is blood, and it stays); a Web Slinger bonded to
 * a symbiote (the symbiote will not be parted from them); and anyone bound by a pact ({@link Pacts}).
 * One who gave a power up does not wake another by themselves ({@link #settled}).
 */
public final class GivingUp {
	// ---- tuning ----
	/** The slow drink (seconds) and how long the first draught's question stands (ticks). */
	public static final float DRINK_SECONDS = 6.0F;
	public static final int CONFIRM_TICKS = 20 * 15;

	/** Gave a power up and has not taken another since: no natural awakening. Saved, kept through death. */
	public static final AttachmentType<Boolean> SETTLED = AttachmentRegistry.create(JaymosPowers.id("power_given_up"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** Game time the question of the first draught stands until. */
	private static final AttachmentType<Long> ASKED = AttachmentRegistry.create(JaymosPowers.id("release_asked"));

	public static Item RELEASE_DRAUGHT, POWER_PHIAL;
	private static final String POWER_KEY = "power";

	private GivingUp() {
	}

	static void init() {
		RELEASE_DRAUGHT = Alchemy.item("release_draught", p -> new ReleaseItem(p.stacksTo(1).rarity(Rarity.RARE).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().consumeSeconds(DRINK_SECONDS).build())));
		// the phial is not in the creative tab: it is only ever made by the draught, around a power
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("power_phial"));
		POWER_PHIAL = Registry.register(BuiltInRegistries.ITEM, key, new PhialItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)
			.usingConvertsTo(Alchemy.PHIAL).component(DataComponents.CONSUMABLE, Consumables.defaultDrink().consumeSeconds(DRINK_SECONDS).build())));
		Research.register(new Research.Entry("release", Research.Category.INFUSION, () -> RELEASE_DRAUGHT,
			EssenceList.of(Essence.SPIRIT, 24, Essence.ARCANA, 18).plus(Essence.MIND, 12).plus(Essence.SALT, 12), List.of("ichor", "stability"), 0, 2, 1));
	}

	/** Whether {@code player} gave a power up and has none since. */
	public static boolean settled(ServerPlayer player) {
		return Boolean.TRUE.equals(player.getAttached(SETTLED));
	}

	/** The reason {@code player} cannot give their power up (a lang key), or null if they can. */
	public static @Nullable String refusal(ServerPlayer player) {
		Power power = Powers.of(player);
		if (power == null) {
			return "message.jaymospowers.release.none";
		}
		if (power.id().equals(ZFighterPower.ID)) {
			return "message.jaymospowers.release.blood";
		}
		if (Pacts.bound(player)) {
			return "message.jaymospowers.release.pact";
		}
		if (power.id().equals(WebPower.ID) && WebSymbiote.isBonded(player)) {
			return "message.jaymospowers.release.bond";
		}
		return null;
	}

	/** The draught: asks first, then lets go. */
	public static class ReleaseItem extends Item {
		public ReleaseItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) {
				return stack;
			}
			String refused = refusal(player);
			if (refused != null) {
				player.sendOverlayMessage(Component.translatable(refused).withStyle(ChatFormatting.GRAY));
				return stack;
			}
			Long asked = player.getAttached(ASKED);
			if (asked == null || level.getGameTime() > asked) {
				player.setAttached(ASKED, level.getGameTime() + CONFIRM_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.release.again").withStyle(ChatFormatting.GRAY));
				return stack;
			}
			player.removeAttached(ASKED);
			ItemStack left = super.finishUsingItem(stack, level, entity);
			release(server, player);
			return left;
		}
	}

	/** Lets {@code player}'s power go: the band, or a phial, left at their feet. */
	private static void release(ServerLevel level, ServerPlayer player) {
		Power power = Powers.of(player);
		if (power == null) {
			return;
		}
		ItemStack left;
		if (power.id().equals(XenoformPower.ID)) {
			left = XenoBands.surrender(player);
		} else {
			left = new ItemStack(POWER_PHIAL);
			CompoundTag tag = new CompoundTag();
			tag.putString(POWER_KEY, power.id().toString());
			left.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			left.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymospowers.power_phial.of", power.name()));
			Powers.set(player, null);
		}
		player.setAttached(SETTLED, true);
		player.drop(left, false, Prediction.SERVER_ONLY);
		level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.6F);
		MagicFx.blast(level, player.position().add(0.0, 1.0, 0.0), 1.6F, 0xC9B8FF);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.release.done").withStyle(ChatFormatting.GRAY));
	}

	/** A sealed phial holding a power: a powerless player who drinks it wakes that power. */
	public static class PhialItem extends Item {
		public PhialItem(Item.Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (!(level instanceof ServerLevel) || !(entity instanceof ServerPlayer player)) {
				return stack;
			}
			Power power = held(stack);
			if (power == null) {
				return stack;
			}
			if (Powers.of(player) != null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.release.has").withStyle(ChatFormatting.GRAY));
				return stack;
			}
			ItemStack left = super.finishUsingItem(stack, level, entity);
			player.removeAttached(SETTLED);
			Powers.awakenAs(player, power);
			return left;
		}
	}

	/** The power sealed in a phial, or null. */
	public static @Nullable Power held(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		Identifier id = Identifier.tryParse(data.copyTag().getStringOr(POWER_KEY, ""));
		return Powers.get(id);
	}
}
