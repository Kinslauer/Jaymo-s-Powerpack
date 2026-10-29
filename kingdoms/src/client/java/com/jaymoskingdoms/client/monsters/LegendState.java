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
	/** (0.7.0) A cursed werewolf: how much of the beast shows (0-1, mid-change), and its build (0 lean, 1, 2 heavy). */
	float beast = 1.0F;
	int build = 1;
	/** (0.7.1) A cursed werewolf's look (0 wild; else 1 + wear * 3 + rags) and its rags' colour. */
	int look, rags;
	/** (0.7.322) A silver skeleton: how long since it last took a blow on its buckler, and since its guard was broken (ticks). */
	float sinceBlocked = 1000.0F, sinceGuardBroken = 1000.0F;
}
