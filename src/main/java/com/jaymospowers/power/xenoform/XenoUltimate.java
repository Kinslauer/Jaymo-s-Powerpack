package com.jaymospowers.power.xenoform;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.9, Ultimates patch 0) An alien's Ultimate: its own evolved body with its own R G V B and a big move on C, on top of its
 * own health bar. It extends {@link XenoForm} so it carries a body ({@code scale}, {@code health}, {@code damage}, ...) and the
 * move helpers for free, but it is never registered in {@link XenoForms}: it is never on the dial and never counted toward a
 * complete band. It takes the base alien's {@link #id}, so everything keyed on the id (the models, the colour, the lang) finds the
 * base alien; the player's {@code XenoformPower.Live#form} stays the base alien and {@code Live#ult} is this.
 *
 * <p>Each alien's {@link XenoForm#evolved()} returns one. Tuning numbers sit at the top of each Ultimate's class.
 */
public abstract class XenoUltimate extends XenoForm {
	/** The alien this evolves from. */
	public final XenoForm base;

	protected XenoUltimate(XenoForm base, float scale, float health, double damage, double speed, double knockbackResist, double stepUp) {
		super(base.id, scale, health, damage, speed, knockbackResist, stepUp, base.icon, base.color);
		this.base = base;
	}

	/** The Ultimate's own name: lang {@code xenoform.jaymospowers.<id>.ultimate}. */
	@Override
	public Component name() {
		return base.ultimateName();
	}

	@Override
	public final boolean scans(Entity creature) {
		return false;
	}

	@Override
	public final boolean hidden() {
		return true;
	}

	/** C while the Ultimate is on and was tapped (held, it drops back to the base alien instead). */
	public abstract void onBigMove(net.minecraft.server.level.ServerPlayer player, XenoformPower.Live live);

	/** The chips for R G V B and C (five items) the HUD draws. Default: the base alien's icon on each. */
	public Item[] icons() {
		return new Item[] {icon, icon, icon, icon, icon};
	}

	/**
	 * The parts the change-in film zooms on, in order, each a model part name the Ultimate's model knows ("right_lower_arm",
	 * "pauldron", "spine", "head"). The whole body follows after the last one. Default: just the body.
	 */
	public String[] morphParts() {
		return new String[] {"body"};
	}

	/** Nothing is scanned; keeps the compiler's nullable analysis happy for callers that hold an Ultimate as a form. */
	@Override
	public @Nullable XenoUltimate evolved() {
		return null;
	}
}
