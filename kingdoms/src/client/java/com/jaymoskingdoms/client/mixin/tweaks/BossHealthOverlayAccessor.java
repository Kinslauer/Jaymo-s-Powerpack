package com.jaymoskingdoms.client.mixin.tweaks;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The boss bars showing now, so the experience meter ({@code SkillMeter}) drops in under them. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
	@Accessor("events")
	Map<UUID, LerpingBossEvent> jaymoskingdoms$events();
}
