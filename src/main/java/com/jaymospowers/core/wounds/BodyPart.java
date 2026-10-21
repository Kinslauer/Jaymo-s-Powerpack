package com.jaymospowers.core.wounds;

import net.minecraft.network.chat.Component;

/** One part of a body that keeps its own share of the health ({@link BodyPlan} says which a creature has). */
public enum BodyPart {
	HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, LEFT_FOOT, RIGHT_FOOT, FRONT_LEGS, HIND_LEGS, LEGS, WINGS;

	/** Head and body: when either is gone, so is the creature. */
	public boolean vital() {
		return this == HEAD || this == BODY;
	}

	public String key() {
		return "wounds.jaymospowers.part." + this.name().toLowerCase(java.util.Locale.ROOT);
	}

	public Component displayName() {
		return Component.translatable(this.key());
	}

	/** Where what a gone part can't take spills over to: a limb into the body, a foot into its leg, the body into the head and the head into the body. */
	public BodyPart overflow() {
		return switch (this) {
			case LEFT_FOOT -> LEFT_LEG;
			case RIGHT_FOOT -> RIGHT_LEG;
			case BODY -> HEAD;
			default -> BODY;
		};
	}
}
