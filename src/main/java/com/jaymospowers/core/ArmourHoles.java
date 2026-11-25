package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Holes worn into armour: a hard blow that lands on a worn piece may leave a dent or a hole on the face of the piece it
 * landed on (front, back or a side, by where the striker stood). The spots ride on the piece itself, a short list
 * ({@link #MOST}) of packed ints, and the client paints them into the worn armour's texture
 * ({@code ArmourGrimeTextures}). Any repair that restores durability (an anvil, Mending, a mob's own mending) wipes
 * them ({@code ArmourHolesRepairMixin}).
 * <p>
 * A spot is a pixel of the vanilla humanoid armour layout (64 wide; a bigger texture scales it): bits 0-7 x, 8-15 y,
 * 16 how big (0 a dent, 1 a hole). {@link #rect} says which part of the layout each face of each piece is.
 */
public final class ArmourHoles {
	// ===== the knobs ================================================================================================
	/** A blow of less damage than this wears no hole. */
	public static final float MIN_DAMAGE = 2.5F;
	/** A blow of this much damage always may (less: in proportion). */
	public static final float SURE_DAMAGE = 9.0F;
	/** A blow of this much damage or more makes a bigger hole. */
	public static final float BIG_DAMAGE = 8.0F;
	/** The most spots a piece keeps; past it, the oldest is covered. */
	public static final int MOST = 14;
	// ================================================================================================================
	/** The faces of a piece, by where the striker stood: its right, front, left, back. */
	private static final int RIGHT = 0, FRONT = 1, LEFT = 2, BACK = 3;

	/** The spots worn into this piece (packed ints, see above). */
	public static DataComponentType<List<Integer>> HOLES;

	private ArmourHoles() {
	}

	public static void init() {
		HOLES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("armour_holes"),
			DataComponentType.<List<Integer>>builder().persistent(Codec.INT.listOf())
				.networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())).build());
	}

	/** The spots of {@code stack} to draw (empty when it has none). */
	public static int[] spots(ItemStack stack) {
		List<Integer> spots = stack.get(HOLES);
		return spots == null ? new int[0] : spots.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Wipes the holes off {@code stack} (repaired). */
	public static void clear(ItemStack stack) {
		if (stack.has(HOLES)) {
			stack.remove(HOLES);
		}
	}

	/**
	 * A blow of {@code damage} from {@code from} lands on the piece {@code victim} wears in {@code slot} (on its arm part
	 * when {@code arm}): it may wear a hole there.
	 */
	public static void strike(LivingEntity victim, LivingEntity from, EquipmentSlot slot, boolean arm, float damage) {
		ItemStack piece = victim.getItemBySlot(slot);
		RandomSource random = victim.getRandom();
		if (damage < MIN_DAMAGE || !ArmourWear.armour(piece) || ArmourWear.broken(piece)
			|| random.nextFloat() >= Math.min(1.0F, damage / SURE_DAMAGE)) {
			return;
		}
		Rect rect = rect(slot, arm, face(victim, from, slot == EquipmentSlot.HEAD));
		int x = rect.x + random.nextInt(rect.w), y = rect.y + random.nextInt(rect.h);
		int spot = x | y << 8 | (damage >= BIG_DAMAGE ? 1 : 0) << 16;
		List<Integer> spots = new ArrayList<>();
		List<Integer> old = piece.get(HOLES);
		if (old != null) {
			spots.addAll(old);
		}
		if (spots.size() >= MOST) {
			spots.remove(0);
		}
		spots.add(spot);
		piece.set(HOLES, List.copyOf(spots));
	}

	/** Which face of a piece {@code from} is on: {@link #RIGHT}, {@link #FRONT}, {@link #LEFT} or {@link #BACK} of the wearer. */
	private static int face(LivingEntity victim, LivingEntity from, boolean head) {
		double bearing = Mth.atan2(from.getZ() - victim.getZ(), from.getX() - victim.getX()) * Mth.RAD_TO_DEG - 90.0;
		float diff = Mth.wrapDegrees((float) bearing - (head ? victim.yHeadRot : victim.yBodyRot));
		if (Math.abs(diff) <= 45.0F) {
			return FRONT;
		}
		return Math.abs(diff) >= 135.0F ? BACK : diff > 0.0F ? RIGHT : LEFT;
	}

	private record Rect(int x, int y, int w, int h) {
	}

	/** The pixels of the 64-wide armour layout that one face of the piece in {@code slot} covers. */
	private static Rect rect(EquipmentSlot slot, boolean arm, int face) {
		return switch (slot) {
			// the head box: right, front, left, back faces side by side
			case HEAD -> new Rect(face * 8, 8, 8, 8);
			case CHEST -> arm
				// the arm box
				? new Rect(40 + face * 4, 20, 4, 12)
				: new Rect(new int[] {16, 20, 28, 32}[face], 20, face % 2 == 1 ? 8 : 4, 12);
			// the lower end of a leg box for boots
			case FEET -> new Rect(face * 4, 28, 4, 4);
			// a leg box (leggings)
			default -> new Rect(face * 4, 20, 4, 12);
		};
	}
}
