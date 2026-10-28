package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Heritage;
import com.jaymoskingdoms.HeritageVariants;
import com.jaymoskingdoms.McaCompat;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.282) The figure of you on the life story's pages ({@link LifeStoryScreen}). Andy: "make it act out? or at least
 * find different gear". Each answer is a little scene ({@link #SCENES}, by {@code question.answer}): what you wear, what
 * you hold, and what you do (walk, work, swing, raise the thing held, look up or down), all the while looking slowly
 * about. A birthplace walks in plain clothes so the heritage shows. (0.7.284) With MCA the figure is a stand-in MCA
 * villager in job clothes for the scene ({@link McaStoryFigure}): before MCA's creator the player has no MCA body to draw.
 * Without MCA it's you: your heritage, gear and arm are lent to the figure only while it's drawn (nothing of it reaches
 * the server: client attachments aren't synced, a client swing isn't sent, and equipping on the client makes no
 * sound), then put back.
 */
final class StoryFigure {
	/** How long one slow look from side to side takes (ms), and how far it turns (a fraction of the box's width each way). */
	static final long TURN_MS = 9000L;
	static final float TURN = 0.9F;
	/** The box's height to the figure's scale. */
	static final float SCALE = 0.42F;
	/** Between strokes while working, and between swings (ms); one step of walking (ms) and its stride. */
	static final long WORK_MS = 650L, SWING_MS = 1400L, STEP_MS = 50L;
	static final float STRIDE = 0.9F;
	/** How far up or down the figure looks when its scene says so (pixels of the look point above or below its eyes). */
	static final float LOOK_UP = 60F, LOOK_DOWN = 40F;

	/** What a scene's figure does. */
	enum Act {
		/** Stands, looking about. */
		STAND,
		/** Walks along. */
		WALK,
		/** Strokes away at work with what it holds, looking down at it. */
		WORK,
		/** Swings what it holds (or a fist), now and then. */
		SWING,
		/** Raises what it holds as if using it: draws the bow, blocks with the shield, drinks, sounds the horn. */
		USE,
		/** Blocks with what's in the off hand. */
		GUARD,
		/** Stands looking up. */
		LOOK_UP,
		/** Stands looking down at what it holds. */
		LOOK_DOWN
	}

	/** A scene: the act, and the gear (null slots left bare): head, chest, legs, feet, main hand, off hand. */
	record Scene(Act act, @Nullable Item head, @Nullable Item chest, @Nullable Item legs, @Nullable Item feet, @Nullable Item main, @Nullable Item off) {
	}

	static final Map<String, Scene> SCENES = new HashMap<>();
	/** (0.7.284) The MCA job whose clothes the stand-in wears, by answer key ({@code "none"}: everyday clothes). */
	static final Map<String, String> JOBS = new HashMap<>();

	private static long lastSwing, lastStep, lastTick;

	private StoryFigure() {
	}

	private static void scene(String key, Act act, @Nullable Item main, @Nullable Item off, @Nullable Item head, @Nullable Item chest, @Nullable Item legs, @Nullable Item feet) {
		SCENES.put(key, new Scene(act, head, chest, legs, feet, main, off));
	}

	private static void scene(String key, Act act, @Nullable Item main) {
		scene(key, act, main, null, null, null, null, null);
	}

	static {
		Item lh = Items.LEATHER_HELMET, lc = Items.LEATHER_CHESTPLATE, ll = Items.LEATHER_LEGGINGS, lb = Items.LEATHER_BOOTS;
		// raised by
		scene("raised.farmers", Act.WORK, Items.IRON_HOE, null, null, null, ll, lb);
		scene("raised.smiths", Act.WORK, Items.IRON_PICKAXE, null, null, lc, null, null);
		scene("raised.fisherfolk", Act.LOOK_DOWN, Items.FISHING_ROD, null, null, null, null, lb);
		scene("raised.hunters", Act.USE, Items.BOW, null, lh, lc, null, null);
		scene("raised.merchants", Act.STAND, Items.EMERALD, Items.BOOK, null, lc, null, null);
		scene("raised.soldiers", Act.SWING, Items.IRON_SWORD, Items.SHIELD, Items.IRON_HELMET, Items.CHAINMAIL_CHESTPLATE, null, null);
		scene("raised.healers", Act.USE, Items.POTION);
		scene("raised.wizard", Act.LOOK_DOWN, Items.ENCHANTED_BOOK);
		scene("raised.outlaws", Act.WALK, Items.STONE_SWORD, null, lh, null, null, null);
		scene("raised.nobles", Act.STAND, null, null, Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, null, null);
		scene("raised.foundling", Act.WALK, null);
		// childhood
		scene("childhood.roofs", Act.LOOK_UP, Items.FEATHER);
		scene("childhood.caves", Act.WORK, Items.WOODEN_PICKAXE, Items.TORCH, null, null, null, null);
		scene("childhood.fights", Act.SWING, null);
		scene("childhood.books", Act.LOOK_DOWN, Items.WRITABLE_BOOK);
		scene("childhood.swimming", Act.WALK, Items.KELP, null, Items.TURTLE_HELMET, null, null, null);
		scene("childhood.storms", Act.LOOK_UP, Items.COPPER_INGOT);
		scene("childhood.fire", Act.LOOK_DOWN, Items.TORCH);
		scene("childhood.snow", Act.SWING, Items.SNOWBALL, null, null, null, null, lb);
		scene("childhood.bells", Act.WORK, Items.BELL);
		scene("childhood.blades", Act.SWING, Items.WOODEN_SWORD);
		// youth: the class
		scene("youth.cutpurses", Act.WALK, Items.STONE_SWORD, null, lh, lc, null, null);
		scene("youth.levy", Act.SWING, Items.IRON_AXE, Items.SHIELD, Items.IRON_HELMET, Items.CHAINMAIL_CHESTPLATE, null, null);
		scene("youth.apprentice", Act.LOOK_DOWN, Items.ENCHANTED_BOOK);
		scene("youth.hermit", Act.SWING, Items.BLAZE_ROD);
		scene("youth.hunted", Act.USE, Items.BOW, null, lh, lc, null, lb);
		scene("youth.winter", Act.WORK, Items.STONE_AXE, null, lh, lc, ll, lb);
		scene("youth.pits", Act.GUARD, Items.IRON_AXE, Items.SHIELD, null, lc, null, null);
		scene("youth.gunsmith", Act.USE, Items.CROSSBOW, null, lh, null, null, null);
		scene("youth.messenger", Act.USE, Items.GOAT_HORN, null, null, null, null, lb);
		// then one day
		scene("turn.bite", Act.LOOK_DOWN, null);
		scene("turn.weapon", Act.SWING, Items.IRON_SWORD);
		scene("turn.fall", Act.LOOK_UP, Items.FEATHER);
		scene("turn.storm", Act.SWING, Items.WIND_CHARGE);
		scene("turn.clock", Act.LOOK_DOWN, Items.CLOCK);
		scene("turn.bell", Act.WORK, Items.BELL);
		scene("turn.sea", Act.WALK, Items.NAUTILUS_SHELL, null, Items.TURTLE_HELMET, null, null, null);
		scene("turn.fists", Act.SWING, null);
		scene("turn.smoke", Act.LOOK_DOWN, Items.CHARCOAL);
		scene("turn.blizzard", Act.LOOK_UP, null, null, lh, lc, ll, lb);
		scene("turn.fate", Act.LOOK_UP, Items.ENDER_EYE);
		// the road
		scene("road.city", Act.WALK, Items.GOLD_NUGGET, null, null, lc, null, null);
		scene("road.banished", Act.WALK, null);
		scene("road.cave", Act.WORK, Items.WOODEN_PICKAXE, Items.TORCH, null, null, null, null);
		scene("road.shipwreck", Act.LOOK_DOWN, null);
		scene("road.sworn", Act.GUARD, Items.IRON_SWORD, Items.SHIELD, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
		scene("road.village", Act.WORK, Items.IRON_HOE);
		scene("road.monolith", Act.LOOK_UP, null);
		scene("road.wild", Act.WALK, Items.COMPASS);
		scene("road.skyfall", Act.LOOK_UP, Items.AMETHYST_SHARD);
		scene("road.timelost", Act.LOOK_DOWN, Items.RECOVERY_COMPASS);
		scene("road.burned", Act.WALK, Items.CHARCOAL);
		scene("road.snowed", Act.STAND, Items.LANTERN, null, lh, lc, ll, lb);
		scene("road.captive", Act.LOOK_DOWN, Items.LEAD);
		scene("road.whalers", Act.USE, Items.TRIDENT, null, null, lc, null, lb);
		scene("road.unarmed", Act.STAND, null);
		scene("road.fate", Act.LOOK_UP, Items.ENDER_EYE);
	}

	private static void jobs(String job, String... answers) {
		for (String a : answers) {
			JOBS.put(a, job);
		}
	}

	static {
		jobs("farmer", "farmers", "village");
		jobs("armorer", "smiths");
		jobs("fisherman", "fisherfolk", "swimming", "sea", "shipwreck", "whalers");
		jobs("archer", "hunters", "hunted");
		jobs("cartographer", "merchants", "city");
		jobs("guard", "soldiers", "levy", "pits", "sworn", "weapon", "blades", "fights", "fists");
		jobs("cleric", "healers", "nobles", "bell", "bells", "temple", "shrine");
		jobs("librarian", "wizard", "apprentice", "books", "clock", "timelost", "hermit", "scholars");
		jobs("leatherworker", "outlaws", "cutpurses", "captive", "banished");
		jobs("miner", "caves", "cave", "monolith", "bite", "mountain", "forge");
		jobs("shepherd", "winter", "snowed", "blizzard", "snow", "north", "foundling");
		jobs("toolsmith", "gunsmith");
		jobs("fletcher", "messenger", "wild");
		jobs("butcher", "fire", "smoke", "burned");
	}

	/** The job clothes for {@code scene} ({@code question.answer}). */
	static String job(String scene) {
		return JOBS.getOrDefault(scene.substring(scene.indexOf('.') + 1), "none");
	}

	/** {@code scene}'s scene: a birthplace walks in plain clothes, an answer without its own stands holding its picture. */
	static Scene of(String scene, ItemStack icon) {
		Scene found = SCENES.get(scene);
		if (found != null) {
			return found;
		}
		if (scene.isEmpty() || scene.startsWith("born.")) {
			return new Scene(Act.WALK, null, null, null, null, null, null);
		}
		return new Scene(Act.STAND, null, null, null, null, icon.isEmpty() ? null : icon.getItem(), null);
	}

	/**
	 * You in the box {@code x0,y0}-{@code x1,y1}, as heritage {@code kind}'s look {@code look} (null: as you are),
	 * playing {@code scene} ({@code question.answer}; {@code icon} the answer's picture).
	 */
	static void draw(GuiGraphicsExtractor graphics, LocalPlayer player, Heritage.@Nullable Kind kind, int look, String scene, ItemStack icon,
		int x0, int y0, int x1, int y1) {
		Scene sc = of(scene, icon);
		LivingEntity target = McaCompat.LOADED ? McaStoryFigure.figure(player, kind, look, job(scene)) : null;
		boolean standIn = target != null;
		if (target == null) {
			target = player;
		}
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
		Item[] gear = {sc.head(), sc.chest(), sc.legs(), sc.feet(), sc.main(), sc.off()};
		ItemStack[] was = new ItemStack[slots.length];
		String heritageWas = player.getAttached(Heritage.HERITAGE), variantWas = player.getAttached(HeritageVariants.VARIANT);
		boolean using = false, lent = !standIn && kind != null;
		for (int i = 0; i < slots.length; i++) {
			was[i] = target.getItemBySlot(slots[i]);
			target.setItemSlot(slots[i], gear[i] == null ? ItemStack.EMPTY : new ItemStack(gear[i]));
		}
		if (lent) {
			HeritageVariants.Variant v = HeritageVariants.at(kind, look);
			player.setAttached(Heritage.HERITAGE, kind.key());
			player.setAttached(HeritageVariants.VARIANT, v == null ? HeritageVariants.BASE : v.key());
		}
		try {
			long now = System.currentTimeMillis();
			if (standIn && now - lastTick >= STEP_MS) {
				lastTick = now;
				McaStoryFigure.tick(target);
			}
			act(target, sc.act(), now);
			if ((sc.act() == Act.USE || sc.act() == Act.GUARD) && !target.isUsingItem()) {
				InteractionHand hand = sc.act() == Act.GUARD ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
				if (!target.getItemInHand(hand).isEmpty()) {
					target.startUsingItem(hand);
					using = target.isUsingItem();
				}
			}
			// (a slow look from side to side, as if the mouse swept past it, raised or lowered as the scene says)
			double t = (now % TURN_MS) / (double)TURN_MS * Math.PI * 2;
			float cx = (x0 + x1) / 2F, cy = y0 + (y1 - y0) * 0.35F;
			float sweep = sc.act() == Act.WORK || sc.act() == Act.LOOK_DOWN ? 0.4F : 1F;
			float lookX = cx + (float)Math.sin(t) * (x1 - x0) * TURN * sweep;
			float lookY = cy + (float)Math.sin(t * 2) * 6F + switch (sc.act()) {
				case LOOK_UP -> -LOOK_UP;
				case WORK, LOOK_DOWN -> LOOK_DOWN;
				default -> 0F;
			};
			InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, Math.max(10, (int)((y1 - y0) * SCALE)), 0.0625F, lookX, lookY, target);
		} finally {
			if (using) {
				target.stopUsingItem();
			}
			for (int i = 0; i < slots.length; i++) {
				target.setItemSlot(slots[i], was[i]);
			}
			if (lent) {
				restore(player, Heritage.HERITAGE, heritageWas);
				restore(player, HeritageVariants.VARIANT, variantWas);
			}
		}
	}

	/** The story closed: a stand-in figure is let go. */
	static void closed() {
		if (McaCompat.LOADED) {
			McaStoryFigure.release();
		}
	}

	/** Moves the figure's limbs for {@code act}: a stride every {@link #STEP_MS} while walking, a swing now and then. */
	private static void act(LivingEntity player, Act act, long now) {
		if (act == Act.WALK) {
			if (now - lastStep >= STEP_MS) {
				lastStep = now;
				player.walkAnimation.update(STRIDE, 1.0F, 1.0F);
			}
		} else if (act == Act.WORK || act == Act.SWING) {
			if (now - lastSwing >= (act == Act.WORK ? WORK_MS : SWING_MS) && !player.isSwinging()) {
				lastSwing = now;
				player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
			}
		}
	}

	private static void restore(LocalPlayer player, AttachmentType<String> type, @Nullable String was) {
		if (was == null) {
			player.removeAttached(type);
		} else {
			player.setAttached(type, was);
		}
	}
}
