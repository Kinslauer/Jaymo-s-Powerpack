package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.power.xenoform.LurkBlend;
import com.jaymospowers.power.xenoform.TailSweep;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * What the hunters add to a body's pose: a tail sweep spins the whole body once ({@link TailSweep#SWEEP}; the tailed
 * aliens and the Warrior-born Z Fighter alike), and Lurk's blend tint ({@link LurkBlend#TINT}) for {@link XenoformLayer}.
 */
public final class HunterPose {
	/** How far round the sweep has turned (0-1), or null when none is playing. */
	public static final RenderStateDataKey<Float> SWEEP = RenderStateDataKey.create(() -> "jaymospowers:hunter_sweep");
	/** Lurk's look laid over its skin (ARGB), or null. */
	public static final RenderStateDataKey<Integer> TINT = RenderStateDataKey.create(() -> "jaymospowers:hunter_tint");

	private HunterPose() {
	}

	/** From the avatar renderer's extractRenderState. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Long start = entity.getAttached(TailSweep.SWEEP);
		Float phase = null;
		if (start != null) {
			float t = (entity.level().getGameTime() - start + partialTicks) / TailSweep.SPIN_TICKS;
			if (t >= 0.0F && t < 1.0F) {
				phase = t;
			}
		}
		state.setData(SWEEP, phase);
		String form = entity.getAttached(com.jaymospowers.power.xenoform.XenoformState.FORM);
		state.setData(TINT, "lurk".equals(form) ? entity.getAttached(LurkBlend.TINT) : null);
	}

	/** Right after a model animates: the body turned round on its feet. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar)) {
			return;
		}
		@Nullable Float phase = avatar.getData(SWEEP);
		if (phase != null) {
			float eased = phase * phase * (3.0F - 2.0F * phase);
			model.root().yRot += eased * Mth.TWO_PI;
		}
	}
}
