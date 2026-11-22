package com.jaymospowers.client.mixin;

import com.jaymospowers.client.McaHeadOwner;
import java.util.UUID;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Gives a skull block's render state the player whose head it is (McaHeadOwner). */
@Mixin(SkullBlockRenderState.class)
abstract class McaHeadOwnerStateMixin implements McaHeadOwner {
	@Unique
	private @Nullable UUID jaymospowers$owner;

	@Override
	public @Nullable UUID jaymospowers$headOwner() {
		return this.jaymospowers$owner;
	}

	@Override
	public void jaymospowers$setHeadOwner(@Nullable UUID owner) {
		this.jaymospowers$owner = owner;
	}
}
