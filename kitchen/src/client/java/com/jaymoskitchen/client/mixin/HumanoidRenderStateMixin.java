package com.jaymoskitchen.client.mixin;

import com.jaymoskitchen.client.delight.SkilletFlipState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** (From Farmer's Delight, MIT.) A humanoid's render state remembers when the pan it's cooking with was last flipped. */
@Mixin(HumanoidRenderState.class)
public class HumanoidRenderStateMixin implements SkilletFlipState {
	@Unique
	private long jaymoskitchen$flipTimestamp = -1;

	@Override
	public long jaymoskitchen$getFlipTimestamp() {
		return jaymoskitchen$flipTimestamp;
	}

	@Override
	public void jaymoskitchen$setFlipTimestamp(long value) {
		jaymoskitchen$flipTimestamp = value;
	}
}
