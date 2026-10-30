package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.21, Andy's SevTech picks: "Mended Better" and "One Part Per World") A pod can be mended past the way it was built.
 * Each grade needs a part only one far world gives, taken from the planet's mission prize ({@code ZFighterSpaceMissions}):
 * grade I the Rustfall plate, II the Glacia ice-glass, III the Cinder bell alloy. Sneak and use the next one on a
 * finished pod ({@link #tryGrade}). What a grade is worth is in the constants: I, a trip that burns {@link #DISCOUNT_FROM} or
 * more burns one less ({@link #discount}); II, a second seat ({@link PodEntity#seats}); III, a hard landing never shakes a
 * part off ({@link #softLanding}). The grade lives on the pod and, when it is stripped to its keel, on the keel
 * ({@link PodParts#GRADE}).
 */
public final class PodGrades {
	// ---- tuning ----
	public static final int MAX_GRADE = 3;
	/** Grade I: a trip that burns at least this much fuel burns one less. */
	public static final int DISCOUNT_FROM = 3;
	/** The grade that adds a seat, and the one that makes a hard landing gentle on the hull. */
	public static final int SECOND_SEAT_GRADE = 2, SOFT_LANDING_GRADE = 3;
	/** The planet whose prize is each grade's part (the mission planets' keys), by grade - 1. */
	static final String[] WORLDS = {"rustfall", "glacia", "cinder"};
	static final String[] ITEMS = {"rustfall_plate", "glacia_ice_glass", "cinder_bell_alloy"};

	private static final Item[] PARTS = new Item[MAX_GRADE];
	private static boolean ready;

	private PodGrades() {
	}

	/** A world's part: a plain item with a one-line grey tooltip. */
	private static final class WorldPart extends Item {
		private final int grade;

		WorldPart(Item.Properties properties, int grade) {
			super(properties);
			this.grade = grade;
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymospowers.pod.world_part." + grade).withStyle(ChatFormatting.GRAY));
		}
	}

	/** Registers the three world parts. Called once, from {@link PodParts#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		for (int i = 0; i < MAX_GRADE; i++) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(ITEMS[i]));
			PARTS[i] = Registry.register(BuiltInRegistries.ITEM, key, new WorldPart(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON), i + 1));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item part : PARTS) {
				output.accept(part);
			}
		});
	}

	/** The part that grades a pod up to {@code grade} (1 to {@link #MAX_GRADE}). */
	public static Item partFor(int grade) {
		return PARTS[grade - 1];
	}

	/** The planet (its mission key) whose part is for {@code grade}. */
	public static String worldOf(int grade) {
		return WORLDS[grade - 1];
	}

	/** The part a planet's prize includes, by its mission key, or null for a planet without one. */
	public static @Nullable Item worldPart(String key) {
		for (int i = 0; i < WORLDS.length; i++) {
			if (WORLDS[i].equals(key)) {
				return PARTS[i];
			}
		}
		return null;
	}

	/** The grade {@code stack} is the part for, or 0 if it isn't one. */
	public static int gradeOf(ItemStack stack) {
		for (int i = 0; i < PARTS.length; i++) {
			if (stack.is(PARTS[i])) {
				return i + 1;
			}
		}
		return 0;
	}

	/** What a trip costing {@code cost} burns from a pod of {@code grade}. */
	public static int discount(int grade, int cost) {
		return grade >= 1 && cost >= DISCOUNT_FROM ? cost - 1 : cost;
	}

	/** Whether a pod of {@code grade} comes down gently (a hard landing shakes no part off). */
	public static boolean softLanding(int grade) {
		return grade >= SOFT_LANDING_GRADE;
	}

	/** "I", "II", "III". */
	public static String roman(int grade) {
		return "I".repeat(Math.max(0, grade));
	}

	/**
	 * Sneak-use of {@code held} on {@code pod}: if it is one of the world parts, the pod takes it when it is the next one and the
	 * pod is finished (it is told why not otherwise). False when {@code held} isn't a world part at all.
	 */
	static boolean tryGrade(ServerPlayer player, PodEntity pod, ItemStack held) {
		int grade = gradeOf(held);
		if (grade == 0) {
			return false;
		}
		if (pod.grade() >= MAX_GRADE) {
			tell(player, Component.translatable("message.jaymospowers.pod.grade_max"));
		} else if (grade != pod.grade() + 1) {
			tell(player, Component.translatable("message.jaymospowers.pod.grade_next", roman(pod.grade() + 1),
				Component.translatable("item.jaymospowers." + ITEMS[pod.grade()])));
		} else if (!pod.finished()) {
			tell(player, Component.translatable("message.jaymospowers.pod.grade_unfinished"));
		} else {
			held.consume(1, player);
			pod.setGrade(grade);
			ServerLevel level = player.level();
			Vec3 at = pod.getBoundingBox().getCenter();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.5F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
			level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y, at.z, 24, 0.7, 1.0, 0.7, 0.3);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.graded", roman(grade)).withStyle(ChatFormatting.GOLD));
		}
		return true;
	}

	private static void tell(ServerPlayer player, Component message) {
		player.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY));
	}
}
