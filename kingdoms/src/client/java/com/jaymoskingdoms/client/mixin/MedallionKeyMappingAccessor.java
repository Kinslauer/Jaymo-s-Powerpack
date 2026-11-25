package com.jaymoskingdoms.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A key mapping's bound key and its waiting presses, for the medallion board's hold on the numpad ({@link MedallionNumpadMixin}). */
@Mixin(KeyMapping.class)
public interface MedallionKeyMappingAccessor {
	/** Every key mapping by name, so the board can throw away presses left waiting on the numpad's other bindings. */
	@Accessor("ALL")
	static Map<String, KeyMapping> jaymoskingdoms$all() {
		throw new AssertionError();
	}

	@Accessor("key")
	InputConstants.Key jaymoskingdoms$key();

	@Accessor("clickCount")
	int jaymoskingdoms$clicks();

	@Accessor("clickCount")
	void jaymoskingdoms$setClicks(int clicks);
}
