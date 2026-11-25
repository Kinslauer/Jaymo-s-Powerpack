package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.tinkering.smithing.Grade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A pod forged well flies better. The class, C to S, is read from the forging quality ({@link Grade}) the keel and the
 * hull plates were hammered to: the keel counts double, a part nobody graded counts as Well Forged. A class sets how long a
 * flight takes ({@link #flight}), how much the tank holds ({@link #fuel}) and how many rows the cargo hold has
 * ({@link #holdRows}). The grades live on the pod ({@link PodEntity#forgedGrade}).
 */
public enum PodClass {
	// ---- tuning ----
	/** Flight time times this; fuel units added to the tank's size; rows of the hold (nine slots each). */
	C("C", 1.10F, -1, 1, ChatFormatting.GRAY),
	B("B", 1.0F, 0, 1, ChatFormatting.WHITE),
	A("A", 0.9F, 1, 2, ChatFormatting.GREEN),
	S("S", 0.8F, 2, 3, ChatFormatting.GOLD);

	/** The average forging grade (index into {@link Grade}: Poor 0 ... Legendary 5) a class begins at. */
	static final float B_FROM = 0.75F, A_FROM = 1.75F, S_FROM = 3.0F;
	/** How many times the keel counts against one plate. */
	static final int KEEL_WEIGHT = 2;

	public final String label;
	public final float flight;
	public final int fuel, holdRows;
	public final ChatFormatting colour;

	PodClass(String label, float flight, int fuel, int holdRows, ChatFormatting colour) {
		this.label = label;
		this.flight = flight;
		this.fuel = fuel;
		this.holdRows = holdRows;
		this.colour = colour;
	}

	/** The class of {@code pod}: the keel's and the fitted plates' grades, averaged. */
	public static PodClass of(PodEntity pod) {
		int total = 0;
		int weight = 0;
		if (pod.has(PodPart.KEEL, 0)) {
			total += KEEL_WEIGHT * pod.forgedGrade(PodPart.KEEL, 0).ordinal();
			weight += KEEL_WEIGHT;
		}
		for (int i = 0; i < PodPart.PLATE.count; i++) {
			if (pod.has(PodPart.PLATE, i)) {
				total += pod.forgedGrade(PodPart.PLATE, i).ordinal();
				weight++;
			}
		}
		return weight == 0 ? B : fromAverage(total / (float)weight);
	}

	/** The class an average grade (0 Poor to 5 Legendary) makes. */
	public static PodClass fromAverage(float average) {
		return average >= S_FROM ? S : average >= A_FROM ? A : average >= B_FROM ? B : C;
	}

	/** "Class A", in the class's colour. */
	public MutableComponent displayName() {
		return Component.translatable("pod.jaymospowers.class", label).withStyle(colour);
	}
}
