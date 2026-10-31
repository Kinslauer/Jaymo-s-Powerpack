package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.82) Wooden and iron limbs (docs/NewRoads-Plan.md, Patch 4). A player missing an arm or a leg
 * ({@link Dismember}, or born without) can fit a prosthetic by using it on themselves: a wooden one is a carpenter's
 * job (a grid recipe of planks, sticks and leather), an iron one is forged at the Smithing Anvil
 * ({@code ProstheticForging}). A fitted limb counts as present for {@link com.jaymospowers.core.wounds.LimbUse}, but
 * weaker ({@link #strength}): wood grips and climbs poorly and swings slowly, iron is sturdier but heavy (a little
 * slower on its own). It stays {@link Dismember#MISSING} underneath, so the wound is still there, and when the real
 * limb grows back (a Hermit Bean, a Verdant) the prosthetic pops off and drops as an item ({@link #sync}). On death it
 * drops with the body and the stump respawns bare (0.18.86: lost limbs stay lost), ready to be fitted again.
 * A smith villager does not fit them yet: self-fitting only.
 */
public final class Prosthetics {
	/** A fitted limb's strength against 1 for a sound one (a broken limb is 0.4): wood is weaker, iron sturdier. */
	public static final float WOOD_ARM = 0.5F, WOOD_LEG = 0.55F, IRON_ARM = 0.7F, IRON_LEG = 0.75F;
	/** Swing speed lost per fitted arm (a share of attack speed): the wooden arm is slow, the iron one a little heavy. */
	public static final double WOOD_ARM_SWING = -0.25, IRON_ARM_SWING = -0.1;
	/** Walking speed and jump lost per fitted leg (a missing leg costs {@link Dismember#LEG_SPEED} and {@link Dismember#LEG_JUMP}). */
	public static final double WOOD_LEG_SPEED = -0.22, IRON_LEG_SPEED = -0.16, WOOD_LEG_JUMP = -0.25, IRON_LEG_JUMP = -0.2;
	/** Iron's weight: walking speed lost per fitted iron limb, on top of the leg's own. */
	public static final double IRON_WEIGHT = -0.04;
	/** How often (ticks) a body is checked for a prosthetic whose limb has grown back. */
	public static final int CHECK_EVERY = 20;
	/** Iron ingots a forged limb takes (the smithing anvil). */
	public static final int IRON_ARM_INGOTS = 4, IRON_LEG_INGOTS = 5;
	/** The iron flags sit this many bits above the fitted ones in {@link #FITTED}. */
	private static final int IRON_SHIFT = 8;
	private static final int ALL = Dismember.RIGHT_ARM | Dismember.LEFT_ARM | Dismember.RIGHT_LEG | Dismember.LEFT_LEG;
	private static final int ARMS = Dismember.RIGHT_ARM | Dismember.LEFT_ARM, LEGS = Dismember.RIGHT_LEG | Dismember.LEFT_LEG;

	private static final Identifier SWING_ID = JaymosPowers.id("prosthetic_swing");
	private static final Identifier LEG_SPEED_ID = JaymosPowers.id("prosthetic_leg_speed");
	private static final Identifier LEG_JUMP_ID = JaymosPowers.id("prosthetic_leg_jump");

	/**
	 * The limbs fitted with a prosthetic: {@link Dismember}'s arm and leg bits, and above them (shifted by 8) which of
	 * those are iron. Saved, synced to everyone (it is drawn), not kept through death (it drops with the body).
	 */
	public static final AttachmentType<Integer> FITTED = AttachmentRegistry.create(JaymosPowers.id("prosthetics"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	public static Item WOODEN_ARM, WOODEN_LEG, IRON_ARM_ITEM, IRON_LEG_ITEM;

	private Prosthetics() {
	}

	public static void init() {
		WOODEN_ARM = item("wooden_arm", false, true);
		WOODEN_LEG = item("wooden_leg", false, false);
		IRON_ARM_ITEM = item("iron_arm", true, true);
		IRON_LEG_ITEM = item("iron_leg", true, false);
		CreativeModeTabEvents.modifyOutputEvent(com.jaymospowers.tinkering.kiln.Kiln.tab()).register(output -> {
			output.accept(WOODEN_ARM);
			output.accept(WOODEN_LEG);
			output.accept(IRON_ARM_ITEM);
			output.accept(IRON_LEG_ITEM);
		});
		// the body that falls takes nothing with it: the limbs drop where it fell
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (fitted(entity) != 0) {
				popAll(entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CHECK_EVERY != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (fitted(player) != 0 || hasModifiers(player)) {
					sync(player);
				}
			}
		});
	}

	private static Item item(String id, boolean iron, boolean arm) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new ProstheticItem(new Item.Properties().setId(key).stacksTo(16), iron, arm));
	}

	// ---- queries -------------------------------------------------------------------------------------------------------

	/** The limbs {@code entity} wears a prosthetic on ({@link Dismember}'s arm and leg bits). */
	public static int fitted(net.minecraft.world.entity.Entity entity) {
		Integer mask = entity.getAttached(FITTED);
		return mask == null ? 0 : mask & ALL;
	}

	/** Whether the prosthetic on {@code part} (a {@link Dismember} bit) is iron rather than wood. */
	public static boolean iron(net.minecraft.world.entity.Entity entity, int part) {
		Integer mask = entity.getAttached(FITTED);
		return mask != null && (mask >> IRON_SHIFT & part) != 0;
	}

	/** The iron limbs' bits, for drawing. */
	public static int ironMask(net.minecraft.world.entity.Entity entity) {
		Integer mask = entity.getAttached(FITTED);
		return mask == null ? 0 : mask >> IRON_SHIFT & ALL;
	}

	/** How well the prosthetic on {@code part} works: 0 when there is none. */
	public static float strength(net.minecraft.world.entity.Entity entity, int part) {
		if ((fitted(entity) & part) == 0) {
			return 0.0F;
		}
		boolean iron = iron(entity, part);
		boolean arm = (part & ARMS) != 0;
		return arm ? (iron ? IRON_ARM : WOOD_ARM) : (iron ? IRON_LEG : WOOD_LEG);
	}

	// ---- fitting and coming off -----------------------------------------------------------------------------------------

	/** The first of {@code parts} that is missing and bare, or 0. */
	private static int free(LivingEntity entity, int parts, int... order) {
		int missing = Dismember.missing(entity), fitted = fitted(entity);
		for (int part : order) {
			if ((parts & part) != 0 && (missing & part) != 0 && (fitted & part) == 0) {
				return part;
			}
		}
		return 0;
	}

	/** Fits a prosthetic of this kind to the first limb of its kind that is missing and bare. True if it went on. */
	public static boolean fit(ServerPlayer player, boolean iron, boolean arm) {
		int part = arm ? free(player, ARMS, Dismember.RIGHT_ARM, Dismember.LEFT_ARM) : free(player, LEGS, Dismember.RIGHT_LEG, Dismember.LEFT_LEG);
		if (part == 0) {
			return false;
		}
		int mask = fittedRaw(player) | part | (iron ? part << IRON_SHIFT : 0);
		player.setAttached(FITTED, mask);
		apply(player);
		if (player.level() instanceof ServerLevel level) {
			SoundEvent sound = iron ? SoundEvents.ANVIL_USE : SoundEvents.WOOD_PLACE;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F, iron ? 1.4F : 0.9F);
		}
		return true;
	}

	private static int fittedRaw(net.minecraft.world.entity.Entity entity) {
		Integer mask = entity.getAttached(FITTED);
		return mask == null ? 0 : mask;
	}

	/** Pops every prosthetic whose limb is no longer missing (it grew back): each drops as an item. */
	public static void sync(LivingEntity entity) {
		int grown = fitted(entity) & ~Dismember.missing(entity);
		if (grown != 0) {
			pop(entity, grown);
		}
		apply(entity);
	}

	private static void popAll(LivingEntity entity) {
		pop(entity, fitted(entity));
	}

	private static void pop(LivingEntity entity, int parts) {
		int mask = fittedRaw(entity);
		for (int part : Dismember.PARTS) {
			if ((parts & part & ALL) == 0) {
				continue;
			}
			boolean iron = iron(entity, part);
			boolean arm = (part & ARMS) != 0;
			Item item = arm ? (iron ? IRON_ARM_ITEM : WOODEN_ARM) : (iron ? IRON_LEG_ITEM : WOODEN_LEG);
			mask &= ~(part | part << IRON_SHIFT);
			if (entity.level() instanceof ServerLevel level) {
				entity.spawnAtLocation(level, new ItemStack(item));
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), iron ? SoundEvents.ITEM_BREAK.value() : SoundEvents.WOOD_BREAK,
					SoundSource.PLAYERS, 0.8F, 1.2F);
			}
		}
		if (mask == 0) {
			entity.removeAttached(FITTED);
		} else {
			entity.setAttached(FITTED, mask);
		}
		apply(entity);
	}

	private static boolean hasModifiers(LivingEntity entity) {
		AttributeInstance swing = entity.getAttribute(Attributes.ATTACK_SPEED);
		AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
		return swing != null && swing.getModifier(SWING_ID) != null || speed != null && speed.getModifier(LEG_SPEED_ID) != null;
	}

	/** The weight and clumsiness of what is fitted, onto the body's attributes (idempotent), and the bare legs' own penalty reworked. */
	public static void apply(LivingEntity entity) {
		Dismember.legs(entity, Dismember.missing(entity));
		double swing = 0.0, speed = 0.0, jump = 0.0;
		for (int part : new int[] {Dismember.RIGHT_ARM, Dismember.LEFT_ARM, Dismember.RIGHT_LEG, Dismember.LEFT_LEG}) {
			if ((fitted(entity) & part) == 0) {
				continue;
			}
			boolean iron = iron(entity, part);
			if ((part & ARMS) != 0) {
				swing += iron ? IRON_ARM_SWING : WOOD_ARM_SWING;
			} else {
				speed += iron ? IRON_LEG_SPEED : WOOD_LEG_SPEED;
				jump += iron ? IRON_LEG_JUMP : WOOD_LEG_JUMP;
			}
			if (iron) {
				speed += IRON_WEIGHT;
			}
		}
		set(entity.getAttribute(Attributes.ATTACK_SPEED), SWING_ID, swing);
		set(entity.getAttribute(Attributes.MOVEMENT_SPEED), LEG_SPEED_ID, speed);
		set(entity.getAttribute(Attributes.JUMP_STRENGTH), LEG_JUMP_ID, jump);
	}

	private static void set(@Nullable AttributeInstance attribute, Identifier id, double amount) {
		if (attribute == null) {
			return;
		}
		if (amount == 0.0) {
			attribute.removeModifier(id);
		} else {
			attribute.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	// ---- the item ------------------------------------------------------------------------------------------------------

	/** A wooden or iron arm or leg: used on yourself, it takes the place of a limb you have lost. */
	public static final class ProstheticItem extends Item {
		private final boolean iron, arm;

		ProstheticItem(Item.Properties properties, boolean iron, boolean arm) {
			super(properties);
			this.iron = iron;
			this.arm = arm;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer server)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (fit(server, this.iron, this.arm)) {
				stack.consume(1, player);
				return InteractionResult.SUCCESS_SERVER;
			}
			server.sendOverlayMessage(Component.translatable("message.jaymospowers.prosthetic.nothing_to_fit." + (this.arm ? "arm" : "leg")));
			return InteractionResult.FAIL;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
			TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.prosthetic." + (this.iron ? "iron" : "wood")).withStyle(ChatFormatting.GRAY));
			builder.accept(Component.translatable("tooltip.jaymospowers.prosthetic.fit").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
