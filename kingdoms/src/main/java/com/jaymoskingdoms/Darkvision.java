package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Seeing in the dark by blood (fifth edition's darkvision, Andy's pick F2). Some bloods see in the dark as grey shapes
 * out to {@link #RANGE} blocks, with no colour in them: the Nightborn, the Deepborn, the Stoneborn who live under the
 * hills, the Moon elves, the Hollow (the risen dead) and the goblin and orc broods, and, by Andy's note ("the lizard and
 * feline beast folk should get that too"), the Serpen (the lizard-kin), the Feline, and the Arachnid with them. The
 * other bloods need light, and fighting into darkness puts them on bad footing ({@link UpperHand}).
 * <p>
 * This class only says who has it. The sight itself is the client's: a grey, colourless lift of whatever is dim, within
 * {@link #RANGE} blocks, on their own screen ({@code client.DarkvisionClient} and the {@code darkvision} post effect). A
 * potion's or an artefact's Night Vision is the whole-colour kind and wins where it is worn. {@link UpperHand} is told of
 * it here, so a blood that sees in the dark isn't on bad footing striking into it.
 */
public final class Darkvision {
	/** How far (blocks) the grey shapes reach. The post effect's own reach is set to the same in its JSON. */
	public static final double RANGE = 16.0;

	/** The bloods that see in the dark (a table; add a blood here and it sees). */
	static final Set<Heritage.Kind> BLOODS = EnumSet.of(Heritage.Kind.NIGHTBORN, Heritage.Kind.DEEPBORN, Heritage.Kind.STONEBORN,
		Heritage.Kind.HOLLOW, Heritage.Kind.GOBLIN, Heritage.Kind.ORC);
	/** And the looks of other bloods that do: the moon elves, and the beast folk with a cat's, a lizard's or a spider's eyes. */
	static final Set<HeritageVariants.Variant> LOOKS = EnumSet.of(HeritageVariants.Variant.MOONELF, HeritageVariants.Variant.FELINE,
		HeritageVariants.Variant.SERPEN, HeritageVariants.Variant.ARACHNID);

	private Darkvision() {
	}

	static void register() {
		UpperHand.addSight(Darkvision::has);
	}

	/** Whether {@code who} (a player or a villager of that blood) sees in the dark by blood. */
	public static boolean has(@Nullable Entity who) {
		if (who == null) {
			return false;
		}
		Heritage.Kind kind = VillagerHeritage.of(who);
		return kind != null && has(kind, HeritageVariants.of(who));
	}

	/** Whether a blood of {@code kind}, in {@code look} (null: its first look), sees in the dark. */
	public static boolean has(Heritage.Kind kind, HeritageVariants.@Nullable Variant look) {
		return BLOODS.contains(kind) || look != null && look.heritage == kind && LOOKS.contains(look);
	}

	/** Whether {@code who} sees in the dark in any way at all (blood, a potion, a power). */
	public static boolean any(LivingEntity who) {
		return UpperHand.seesInDark(who);
	}
}
