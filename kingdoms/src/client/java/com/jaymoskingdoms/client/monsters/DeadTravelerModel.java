package com.jaymoskingdoms.client.monsters;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.client.bb.BbModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * (0.7.321) The silver skeleton, drawn as polyjo's Dead Traveler Skeleton (Blockbench Workshop, CC BY 4.0, credits in
 * textures/entity/bb/CREDITS.txt): a sword in its right hand, a buckler on its left. Its own animations: idle, walk,
 * the charge when it sprints, the slash when it cuts, the buckler raised when it takes a blow on it, and knocked aside
 * when an axe breaks its guard.
 */
final class DeadTravelerModel extends EntityModel<LegendState> {
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/bb/dead_traveler.png");
	/** How long it holds its buckler up after a blow lands on it, and how fast it raises and lowers it (ticks). */
	static final float BLOCK_HOLD = 12.0F, BLOCK_EASE = 3.0F;
	/** One stride of the walk (and of the charge), in walk distance: a vanilla leg swing's. */
	static final float STRIDE = Mth.TWO_PI / 0.6662F;

	private final BbModel bb;

	DeadTravelerModel() {
		this(BbModel.load(JaymosKingdoms.MOD_ID, "dead_traveler"));
	}

	private DeadTravelerModel(BbModel bb) {
		super(bb.root);
		this.bb = bb;
	}

	@Override
	public void setupAnim(LegendState state) {
		super.setupAnim(state);
		float pace = Mth.clamp(state.walkAnimationSpeed * 2.0F, 0.0F, 1.0F);
		float charge = state.sprinting ? pace : 0.0F;
		this.bb.play("idle", state.ageInTicks / 20.0F, 1.0F - pace);
		this.bb.play("walk", state.walkAnimationPos / STRIDE * this.bb.length("walk"), pace - charge);
		this.bb.play("charge", state.walkAnimationPos / STRIDE * this.bb.length("charge"), charge);
		if (state.sinceGuardBroken < this.bb.length("block_destroy") * 20.0F) {
			this.bb.play("block_destroy", state.sinceGuardBroken / 20.0F, 1.0F);
		} else if (state.sinceBlocked < BLOCK_HOLD + BLOCK_EASE) {
			float up = Math.min(state.sinceBlocked / BLOCK_EASE, 1.0F) * Math.min((BLOCK_HOLD + BLOCK_EASE - state.sinceBlocked) / BLOCK_EASE, 1.0F);
			this.bb.play("block", 0.0F, Mth.clamp(up, 0.0F, 1.0F));
		}
		if (state.rightAttack > 0.0F) {
			this.bb.play("slash", state.rightAttack / 20.0F, 1.0F);
		}
		this.bb.part("head").yRot += state.yRot * Mth.DEG_TO_RAD;
		this.bb.part("head").xRot += state.xRot * Mth.DEG_TO_RAD;
	}
}
