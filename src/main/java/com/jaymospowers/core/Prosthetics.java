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
 * drops with the body and the stump respawns bare (0.18.87: lost limbs stay lost), ready to be fitted again.
 * A smith villager does not fit them yet: self-fitting only.
 */
public final class Prosthetics {
	/** A fitted limb's strength against 1 for a sound one (a broken limb is 0.4): wood is weaker, iron sturdier. */
	public static final float WOOD_ARM = 0.5F, WOOD_LEG = 0.55F, IRON_ARM = 0.7F, IRON_LEG = 0.75F, RIG_ARM = 0.9F, RIG_LEG = 0.95F;
	/** Swing speed lost per fitted arm (a share of attack speed): the wooden arm is slow, the iron one a little heavy. */
	public static final double WOOD_ARM_SWING = -0.25, IRON_ARM_SWING = -0.1;
	/** Walking speed and jump lost per fitted leg (a missing leg costs {@link Dismember#LEG_SPEED} and {@link Dismember#LEG_JUMP}). */
	public static final double WOOD_LEG_SPEED = -0.22, IRON_LEG_SPEED = -0.16, WOOD_LEG_JUMP = -0.25, IRON_LEG_JUMP = -0.2;
	/** Iron's weight: walking speed lost per fitted iron limb, on top of the leg's own. */
	public static final double IRON_WEIGHT = -0.04;
	/** A Rig limb ({@code rig.RigLimbs}) is balanced: no swing lost, a little walking speed given for the leg's mass. */
	public static final double RIG_LEG_SPEED = -0.04;
	/** How often (ticks) a body is checked for a prosthetic whose limb has grown back. */
	public static final int CHECK_EVERY = 20;
	/** Iron ingots a forged limb takes (the smithing anvil). */
	public static final int IRON_ARM_INGOTS = 4, IRON_LEG_INGOTS = 5;
	/** The iron flags sit this many bits above the fitted ones in {@link #FITTED}. */
	private static final int IRON_SHIFT = 8;
	/** The Rig flags sit this many bits above the fitted ones; above them, the frame each Rig limb was built as (two bits a limb). */
	private static final int RIG_SHIFT = 16, FRAME_SHIFT = 24;
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

	public static Item WOODEN_ARM, WOODEN_LEG, IRON_ARM_ITEM, IRON_LEG_ITEM, RIG_ARM_ITEM, RIG_LEG_ITEM;

	/** What a limb is made of. */
	public enum Kind {
		WOOD, IRON, RIG
	}

	private Prosthetics() {
	}

	public static void init() {
		WOODEN_ARM = item("wooden_arm", Kind.WOOD, true);
		WOODEN_LEG = item("wooden_leg", Kind.WOOD, false);
		IRON_ARM_ITEM = item("iron_arm", Kind.IRON, true);
		IRON_LEG_ITEM = item("iron_leg", Kind.IRON, false);
		RIG_ARM_ITEM = item("rig_arm", Kind.RIG, true);
		RIG_LEG_ITEM = item("rig_leg", Kind.RIG, false);
		CreativeModeTabEvents.modifyOutputEvent(com.jaymospowers.tinkering.kiln.Kiln.tab()).register(output -> {
			output.accept(WOODEN_ARM);
			output.accept(WOODEN_LEG);
			output.accept(IRON_ARM_ITEM);
			output.accept(IRON_LEG_ITEM);
			output.accept(RIG_ARM_ITEM);
			output.accept(RIG_LEG_ITEM);
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

	private static Item item(String id, Kind kind, boolean arm) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key,
			new ProstheticItem(new Item.Properties().setId(key).stacksTo(kind == Kind.RIG ? 1 : 16), kind, arm));
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

	/** The Rig limbs' bits, for drawing and for what they can do. */
	public static int rigMask(net.minecraft.world.entity.Entity entity) {
		Integer mask = entity.getAttached(FITTED);
		return mask == null ? 0 : mask >> RIG_SHIFT & ALL;
	}

	/** Whether the prosthetic on {@code part} is a Rig limb. */
	public static boolean rig(net.minecraft.world.entity.Entity entity, int part) {
		return (rigMask(entity) & part) != 0;
	}

	/** Whether {@code entity} wears a Rig limb on any of {@code parts} (a {@link Dismember} arm or leg bit mask). */
	public static boolean hasRig(net.minecraft.world.entity.Entity entity, int parts) {
		return (rigMask(entity) & fitted(entity) & parts) != 0;
	}

	/** The id of the frame the Rig limb on {@code part} was built as (empty for the standard one). */
	public static String rigFrame(net.minecraft.world.entity.Entity entity, int part) {
		Integer mask = entity.getAttached(FITTED);
		if (mask == null) {
			return "";
		}
		int index = Math.max(0, Integer.numberOfTrailingZeros(part) - 1);
		return FRAME_IDS[mask >> FRAME_SHIFT + 2 * index & 3];
	}

	/** The frames a Rig limb can carry, in the order they are saved (the same ids as {@code rig.RigFrame}). */
	private static final String[] FRAME_IDS = {"", "scrap", "shade", "siege"};

	/** How well the prosthetic on {@code part} works: 0 when there is none. */
	public static float strength(net.minecraft.world.entity.Entity entity, int part) {
		if ((fitted(entity) & part) == 0) {
			return 0.0F;
		}
		boolean iron = iron(entity, part);
		boolean arm = (part & ARMS) != 0;
		if (rig(entity, part)) {
			return arm ? RIG_ARM : RIG_LEG;
		}
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
	public static boolean fit(ServerPlayer player, Kind kind, boolean arm, String frame) {
		int part = arm ? free(player, ARMS, Dismember.RIGHT_ARM, Dismember.LEFT_ARM) : free(player, LEGS, Dismember.RIGHT_LEG, Dismember.LEFT_LEG);
		if (part == 0) {
			return false;
		}
		int mask = fittedRaw(player) | part | (kind == Kind.IRON ? part << IRON_SHIFT : 0) | (kind == Kind.RIG ? part << RIG_SHIFT : 0);
		if (kind == Kind.RIG) {
			int slot = FRAME_SHIFT + 2 * (Integer.numberOfTrailingZeros(part) - 1);
			mask = mask & ~(3 << slot) | frameBits(frame) << slot;
		}
		player.setAttached(FITTED, mask);
		apply(player);
		if (player.level() instanceof ServerLevel level) {
			SoundEvent sound = kind == Kind.WOOD ? SoundEvents.WOOD_PLACE : SoundEvents.ANVIL_USE;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F,
				kind == Kind.IRON ? 1.4F : kind == Kind.RIG ? 1.8F : 0.9F);
		}
		return true;
	}

	private static int frameBits(String frame) {
		for (int i = 0; i < FRAME_IDS.length; i++) {
			if (FRAME_IDS[i].equals(frame)) {
				return i;
			}
		}
		return 0;
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
			boolean rig = rig(entity, part);
			boolean arm = (part & ARMS) != 0;
			Item item = rig ? (arm ? RIG_ARM_ITEM : RIG_LEG_ITEM) : arm ? (iron ? IRON_ARM_ITEM : WOODEN_ARM) : (iron ? IRON_LEG_ITEM : WOODEN_LEG);
			String frame = rig ? rigFrame(entity, part) : "";
			mask &= ~(part | part << IRON_SHIFT | part << RIG_SHIFT | 3 << FRAME_SHIFT + 2 * (Integer.numberOfTrailingZeros(part) - 1));
			if (entity.level() instanceof ServerLevel level) {
				ItemStack dropped = new ItemStack(item);
				if (!frame.isEmpty()) {
					dropped.set(com.jaymospowers.rig.Rig.FRAME, frame);
				}
				entity.spawnAtLocation(level, dropped);
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
			if (rig(entity, part)) {
				if ((part & ARMS) == 0) {
					speed += RIG_LEG_SPEED;
				}
			} else if ((part & ARMS) != 0) {
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
		private final Kind kind;
		private final boolean arm;

		ProstheticItem(Item.Properties properties, Kind kind, boolean arm) {
			super(properties);
			this.kind = kind;
			this.arm = arm;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer server)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			String frame = stack.getOrDefault(com.jaymospowers.rig.Rig.FRAME, "");
			if (fit(server, this.kind, this.arm, frame)) {
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
			builder.accept(Component.translatable("tooltip.jaymospowers.prosthetic." + this.kind.name().toLowerCase(java.util.Locale.ROOT)
				+ (this.kind == Kind.RIG ? "." + (this.arm ? "arm" : "leg") : "")).withStyle(ChatFormatting.GRAY));
			if (this.kind == Kind.RIG) {
				String frame = stack.getOrDefault(com.jaymospowers.rig.Rig.FRAME, "");
				builder.accept(Component.translatable("rig.jaymospowers.frame." + (frame.isEmpty() ? "standard" : frame)).withStyle(ChatFormatting.DARK_AQUA));
			}
			builder.accept(Component.translatable("tooltip.jaymospowers.prosthetic.fit").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
