package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.tinkering.WeaponParts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Patterns from old gear. A smith who takes a finished piece apart at the Drafting Table (use the table with
 * the piece in hand and paper in the pack, twice: the first use only warns) learns how it was made: the piece is
 * destroyed and its design comes out as a blueprint of its shape, one level under the piece's own grade, as a drafted
 * copy is. Any forged weapon, weapon head or armour piece has a pattern. For most it is a blueprint like any other; for
 * the sets the anvil won't start without their schematic ({@link FrontierForging}) it is how a set nobody sells, like
 * the Juggernaut plate, can be forged at all. Other mods add their own rule for who can read a design ({@link #GATES}).
 */
public final class Patterns {
	/** After the warning, the table must be used again within this long (ticks) to take the piece apart. */
	static final int CONFIRM = 100;

	/** Why this smith can't take a piece apart for its design (Kingdoms: not enough of the Knowledge), or null. */
	public static final List<Function<ServerPlayer, Component>> GATES = new ArrayList<>();

	/** The piece each smith was last warned about, and when. */
	private record Asked(Item piece, long at) {
	}

	private static final Map<UUID, Asked> ASKED = new HashMap<>();

	private Patterns() {
	}

	/** The forging shape {@code piece} is an example of ({@link Blueprints#shape}), or null if nothing is forged like it. */
	public static @Nullable String shapeOf(ItemStack piece) {
		if (piece.isEmpty() || piece.is(Blueprints.BLUEPRINT_ITEM)) {
			return null;
		}
		for (Forging.Option option : Forging.all().values()) {
			if (option.result().is(piece.getItem())) {
				return Blueprints.shape(option);
			}
		}
		// a finished weapon teaches its head
		for (WeaponParts.Kind kind : WeaponParts.loaded()) {
			if (WeaponParts.king(kind)) {
				continue;
			}
			for (WeaponParts.Metal metal : WeaponParts.Metal.values()) {
				if (metal.molten != null && kind.in(metal) == piece.getItem()) {
					return "weapon/" + kind.part();
				}
			}
		}
		return null;
	}

	/** The blueprint taking {@code piece} apart gives. */
	public static ItemStack pattern(ItemStack piece, String shape) {
		Grade grade = Grades.of(piece);
		ItemStack pattern = Blueprints.stack(shape, Blueprints.xpOf(grade == null ? Grade.WELL : grade.lower()));
		// (medieval picks) a realm's marked piece gives a captured pattern: it lets its holder forge that realm's version (RealmPatterns)
		String realm = piece.get(RealmPatterns.REALM_DESIGN);
		return realm == null ? pattern : RealmPatterns.capture(pattern, realm);
	}

	/** {@code smith} uses a Drafting Table with {@code piece} (which has a shape) in hand. */
	static void breakDown(ServerPlayer smith, ItemStack piece, String shape, ServerLevel level, BlockPos pos) {
		for (Function<ServerPlayer, Component> gate : GATES) {
			Component why = gate.apply(smith);
			if (why != null) {
				smith.sendOverlayMessage(why);
				level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.6F, 0.8F);
				return;
			}
		}
		int paper = paperSlot(smith.getInventory());
		if (paper < 0 && !smith.isCreative()) {
			smith.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.pattern_no_paper"));
			return;
		}
		long now = level.getGameTime();
		Asked asked = ASKED.get(smith.getUUID());
		if (asked == null || asked.piece() != piece.getItem() || now - asked.at() > CONFIRM || now < asked.at()) {
			ASKED.put(smith.getUUID(), new Asked(piece.getItem(), now));
			smith.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.pattern_ask", piece.getHoverName()));
			level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.7F, 1.2F);
			return;
		}
		ASKED.remove(smith.getUUID());
		ItemStack pattern = pattern(piece, shape);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, piece.getItem()), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12,
			0.2, 0.05, 0.2, 0.06);
		if (paper >= 0 && !smith.isCreative()) {
			smith.getInventory().getItem(paper).shrink(1);
		}
		piece.shrink(1);
		smith.getInventory().placeItemBackInInventory(pattern, Prediction.SERVER_ONLY);
		level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 0.9F);
		level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.9F);
		smith.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.pattern_done", pattern.getHoverName()));
	}

	private static int paperSlot(Inventory inventory) {
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(Items.PAPER)) {
				return i;
			}
		}
		return -1;
	}
}
