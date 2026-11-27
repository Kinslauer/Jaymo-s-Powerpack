package com.jaymoskitchen.corpse;

import com.jaymoskitchen.HudBars;
import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.KitchenBlood;
import com.jaymoskitchen.butchery.Butchery;
import com.jaymoskitchen.butchery.Organs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Gutting with a blade, held. Use is held on a carcass with a sword, dagger or knife and the work goes in strokes with a
 * bar under the crosshair ({@link HudBars}): first the cut along the belly, drawn a stroke at a time on the body
 * ({@link CorpseEntity#DRESSED_CUT_SHIFT}), then one stroke to pry it open (ribs showing, {@code DRESSED_GUTTED}), then a
 * stroke for each piece of the insides, laid on the ground beside it as a gut pile ({@link Gibs#layPile}), and last a flick
 * of the blade to throw the blood off it. Strokes already made are kept if the hand lets go. A skinning knife does it at the
 * step the hook's work puts it at ({@link Hanging}); a sword or a dagger can gut an animal anywhere, before it's cut up.
 */
public final class Gutting {
	/** Strokes of the cut along the belly. */
	public static final int CUT_STROKES = 3;
	/** Work (ticks of hold) to a stroke. */
	static final int STROKE_TICKS = 16;
	/** Swords, daggers and knives that gut. */
	public static final TagKey<Item> BLADES = TagKey.create(Registries.ITEM, JaymosKitchen.id("gutting_blades"));
	/** How far to the side of the body the insides are laid (blocks, from its middle). */
	private static final double BESIDE = 0.9;

	private Gutting() {
	}

	/** Whether {@code corpse} can still be gutted: an animal's carcass that hasn't been cut up, lying where it is or hung. */
	static boolean gutable(CorpseEntity corpse) {
		return (Hanging.hangable(corpse) || monster(corpse)) && !corpse.gutted() && corpse.butcherStage() <= Hanging.SKINNED && corpse.carrier() == null
			&& corpse.dragger() == null;
	}

	/** A monster with insides worth taking ({@link Organs#gutsMonster}: a spider's silk, a creeper's powder), lying loose. */
	static boolean monster(CorpseEntity corpse) {
		return corpse.kind() == CorpseKind.OTHER && !corpse.isMachine() && Organs.gutsMonster(corpse.sourceType());
	}

	/** Insides to take out of it: a small body one piece, most two, a big carcass four. */
	static int pieces(CorpseEntity corpse) {
		return corpse.oneHanded() ? Hanging.OFFAL_SMALL : corpse.tooBigToLift() ? Hanging.OFFAL_BIG : Hanging.OFFAL;
	}

	/** Strokes it takes in all. */
	static int strokes(CorpseEntity corpse) {
		return CUT_STROKES + 1 + pieces(corpse);
	}

	/**
	 * A right-click on a carcass with a sword or dagger: a stroke's worth of work (use repeats while held). Null if this
	 * isn't a blade for it or it can't be gutted, and the click is used as before.
	 */
	static @Nullable InteractionResult use(CorpseEntity corpse, ServerLevel level, Player player, ItemStack held) {
		// (a knife guts a monster where it lies: it never goes on the hook, where a knife guts an animal)
		boolean knife = held.is(Butchery.SKINNING_KNIVES);
		if (!(held.is(BLADES) || knife) || knife && !monster(corpse) || !gutable(corpse) || player.isShiftKeyDown()) {
			return null;
		}
		return work(corpse, level, player, held);
	}

	/** One use of {@code tool} held on {@code corpse}: the hold's work, and a stroke when a stroke's worth is done. */
	static InteractionResult work(CorpseEntity corpse, ServerLevel level, Player player, ItemStack tool) {
		int total = strokes(corpse);
		int work = corpse.addGutWork(player, level.getGameTime());
		if (player instanceof ServerPlayer sp) {
			float fill = (corpse.gutStrokes() * STROKE_TICKS + Math.min(work, STROKE_TICKS)) / (float) (total * STROKE_TICKS);
			HudBars.hold(sp, "gut", fill, HudBars.RED, Component.translatable("bar.jaymoskitchen.gutting"), true);
		}
		if (work < STROKE_TICKS) {
			if (work % (CorpseEntity.USE_REPEAT * 2) == 0) {
				level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.4F,
					1.1F + level.getRandom().nextFloat() * 0.3F);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
		corpse.resetGutWork();
		stroke(corpse, level, player, tool, total);
		return InteractionResult.SUCCESS_SERVER;
	}

	private static void stroke(CorpseEntity corpse, ServerLevel level, Player player, ItemStack tool, int total) {
		int k = corpse.addGutStroke();
		Vec3 at = corpse.position().add(0.0, 0.3, 0.0);
		corpse.jolt(false);
		if (k <= CUT_STROKES) {
			// the cut along the belly, a stroke longer each time
			corpse.setCut(k);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.AXE_STRIP.value(), SoundSource.NEUTRAL, 0.7F, 1.2F + k * 0.1F);
			KitchenBlood.send(level, corpse, at.x, at.y, at.z, 4, 0.25, 0.05, 0.25, 0.08);
		} else if (k == CUT_STROKES + 1) {
			// pried open: the ribs show
			corpse.addDressed(CorpseEntity.DRESSED_GUTTED);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.9F, 0.6F);
			KitchenBlood.send(level, corpse, at.x, at.y, at.z, 12, 0.3, 0.1, 0.3, 0.12);
			wear(tool, player);
		} else {
			// a piece of the insides comes out, onto the ground beside it
			layInsides(corpse, level, k - CUT_STROKES - 2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL, 0.8F, 0.7F);
			KitchenBlood.send(level, corpse, at.x, at.y, at.z, 6, 0.3, 0.1, 0.3, 0.1);
		}
		if (k >= total) {
			corpse.setGutted();
			flick(level, player, tool);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.gut.done"));
		}
	}

	/**
	 * Piece {@code n} of the insides, as a gut pile (offal, then intestines, turn about) beside the body, with its share of
	 * the body's own organs ({@link Organs}) dropped by it. A monster's insides are only its organs.
	 */
	private static void layInsides(CorpseEntity corpse, ServerLevel level, int n) {
		for (Item organ : Organs.piece(organs(corpse), n, pieces(corpse))) {
			corpse.spawnAtLocation(level, new ItemStack(organ), 0.3F);
		}
		if (monster(corpse)) {
			return;
		}
		double side = corpse.getBbWidth() * 0.5 + BESIDE;
		double angle = corpse.getYRot() * Math.PI / 180.0 + Math.PI / 2.0 + (level.getRandom().nextDouble() - 0.5);
		BlockPos spot = BlockPos.containing(corpse.getX() - Math.sin(angle) * side, corpse.getY() + 1.0, corpse.getZ() + Math.cos(angle) * side);
		Block kind = n % 2 == 0 ? Butchery.OFFAL_PILE : Butchery.INTESTINES_PILE;
		if (!Gibs.layPile(level, spot, kind) && !Gibs.layPile(level, corpse.blockPosition().above(), kind)) {
			// (nowhere to lay it: it falls as it is)
			corpse.spawnAtLocation(level, new ItemStack(n % 2 == 0 ? Butchery.OFFAL : Butchery.INTESTINES), 0.3F);
		}
	}

	/** The organs {@code corpse}'s kind of body holds. */
	static java.util.List<Item> organs(CorpseEntity corpse) {
		return Organs.of(corpse.sourceType(), corpse.oneHanded(), corpse.tooBigToLift());
	}

	/** The blade flicked clean: blood flies off it to the side, and the arm swings. */
	private static void flick(ServerLevel level, Player player, ItemStack tool) {
		wear(tool, player);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 look = player.getLookAngle();
		Vec3 hand = player.getEyePosition().add(look.x * 0.7, -0.5, look.z * 0.7);
		KitchenBlood.send(level, hand.x, hand.y, hand.z, 8, 0.1, 0.05, 0.1, 0.3);
		level.sendParticles(ParticleTypes.CRIT, hand.x, hand.y, hand.z, 3, 0.1, 0.1, 0.1, 0.2);
		level.playSound(null, hand.x, hand.y, hand.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.5F);
	}

	private static void wear(ItemStack tool, Player player) {
		tool.hurtAndBreak(1, player, InteractionHand.MAIN_HAND);
	}
}
