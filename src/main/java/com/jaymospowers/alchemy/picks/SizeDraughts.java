package com.jaymospowers.alchemy.picks;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * The two size draughts, infusions anyone may drink. The Small Draught (red) shrinks the drinker to half size for
 * {@link #TICKS}: they fit a one-block gap, jump high for their size and hit softer, and reach a little less. The Great
 * Draught (blue) swells them to half again: longer in reach, heavier in the blow, slower on their feet. Both wear off on
 * their own, and drinking one swaps out the other. The strength is attribute modifiers (never saved: they are put back
 * each tick from the saved end time, so a relog keeps them and a wear-off or death removes them). Splashing mobs with
 * them is not built.
 */
public final class SizeDraughts {
	/** How long either lasts: 4 minutes. */
	public static final int TICKS = 20 * 60 * 4;
	/** Small: the scale added (-0.5 is half size), the jump, attack damage and reach it multiplies by (as shares added). */
	static final double SMALL_SCALE = -0.5, SMALL_JUMP = 0.5, SMALL_DAMAGE = -0.4, SMALL_REACH = -0.3;
	/** Great: the scale (+0.5 is half again), reach, damage, extra knockback, and the speed and swing speed it loses. */
	static final double GREAT_SCALE = 0.5, GREAT_REACH = 0.5, GREAT_DAMAGE = 0.35, GREAT_KNOCK = 0.8, GREAT_SLOW = -0.15, GREAT_SWING = -0.15;

	private static final Identifier ID = JaymosPowers.id("size_draught");

	/** The game time a draught's size runs out, on the drinker (saved; not kept through death). */
	public static final AttachmentType<Long> SMALL_UNTIL = AttachmentRegistry.create(JaymosPowers.id("small_draught_until"),
		builder -> builder.persistent(Codec.LONG));
	public static final AttachmentType<Long> GREAT_UNTIL = AttachmentRegistry.create(JaymosPowers.id("great_draught_until"),
		builder -> builder.persistent(Codec.LONG));

	public static Item SMALL, GREAT;

	private SizeDraughts() {
	}

	static void init() {
		SMALL = Alchemy.item("small_draught", p -> new SizeItem(p.stacksTo(1).rarity(Rarity.UNCOMMON).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()), false));
		GREAT = Alchemy.item("great_draught", p -> new SizeItem(p.stacksTo(1).rarity(Rarity.UNCOMMON).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().build()), true));
		Research.register(new Research.Entry("shrinking", Research.Category.INFUSION, () -> SMALL,
			EssenceList.of(Essence.AIR, 10, Essence.WATER, 8).plus(Essence.SALT, 6), List.of("ichor", "stability"), 0, 3, 0));
		Research.register(new Research.Entry("growing", Research.Category.INFUSION, () -> GREAT,
			EssenceList.of(Essence.EARTH, 10, Essence.SPIRIT, 8).plus(Essence.WATER, 6), List.of("ichor", "stability"), 0, 3, 2));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player);
			}
		});
	}

	private static boolean running(ServerPlayer player, AttachmentType<Long> type) {
		Long until = player.getAttached(type);
		return until != null && player.level().getGameTime() < until;
	}

	private static void tick(ServerPlayer player) {
		boolean small = running(player, SMALL_UNTIL), great = running(player, GREAT_UNTIL);
		if (!small && player.getAttached(SMALL_UNTIL) != null) {
			player.removeAttached(SMALL_UNTIL);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.size_draught.small_off").withStyle(ChatFormatting.GRAY));
		}
		if (!great && player.getAttached(GREAT_UNTIL) != null) {
			player.removeAttached(GREAT_UNTIL);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.size_draught.great_off").withStyle(ChatFormatting.GRAY));
		}
		set(player, Attributes.SCALE, small ? SMALL_SCALE : great ? GREAT_SCALE : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "scale");
		set(player, Attributes.JUMP_STRENGTH, small ? SMALL_JUMP : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "jump");
		set(player, Attributes.ATTACK_DAMAGE, small ? SMALL_DAMAGE : great ? GREAT_DAMAGE : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "damage");
		double reach = small ? SMALL_REACH : great ? GREAT_REACH : 0.0;
		set(player, Attributes.ENTITY_INTERACTION_RANGE, reach, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "entity_reach");
		set(player, Attributes.BLOCK_INTERACTION_RANGE, reach, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "block_reach");
		set(player, Attributes.ATTACK_KNOCKBACK, great ? GREAT_KNOCK : 0.0, AttributeModifier.Operation.ADD_VALUE, "knock");
		set(player, Attributes.MOVEMENT_SPEED, great ? GREAT_SLOW : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "slow");
		set(player, Attributes.ATTACK_SPEED, great ? GREAT_SWING : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "swing");
	}

	/** One transient modifier: on at {@code amount} (changed if it differs), off at zero. */
	private static void set(ServerPlayer player, Holder<Attribute> attribute, double amount, AttributeModifier.Operation op, String name) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier id = ID.withSuffix("_" + name);
		AttributeModifier old = instance.getModifier(id);
		if (amount == 0.0) {
			if (old != null) {
				instance.removeModifier(id);
			}
		} else if (old == null || old.amount() != amount) {
			instance.removeModifier(id);
			instance.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** A size draught: drunk, it starts its size and swaps out the other. */
	public static class SizeItem extends Item {
		private final boolean great;

		public SizeItem(Item.Properties properties, boolean great) {
			super(properties);
			this.great = great;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
			if (level instanceof ServerLevel server && entity instanceof ServerPlayer player) {
				player.removeAttached(great ? SMALL_UNTIL : GREAT_UNTIL);
				player.setAttached(great ? GREAT_UNTIL : SMALL_UNTIL, server.getGameTime() + TICKS);
				server.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
				server.playSound(null, player.getX(), player.getY(), player.getZ(), great ? SoundEvents.PUFFER_FISH_BLOW_UP : SoundEvents.PUFFER_FISH_BLOW_OUT,
					SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			return super.finishUsingItem(stack, level, entity);
		}
	}
}
