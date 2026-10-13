package com.jaymoskingdoms.client.monsters;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** What the monsters of legend's models need to draw a frame (a humanoid's, for the werewolf by day). */
public final class LegendState extends HumanoidRenderState {
	int variant;
	/** Which arm is swinging (left 1, right 2, both 3), and how far each arm's blow has run (ticks). */
	int arm;
	float leftAttack, rightAttack;
	/** An ogre with two heads; a werewolf hunched for the hunt or mid-change; a mimic shut. */
	boolean twoHeads, hunched, changing, shut;
	boolean sprinting, aggressive;
}
