package com.jaymoskingdoms.client;

import com.jaymoskingdoms.wild.SpiderKinds;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

/** (0.7.12) A spider's kind on its render state (mixin.SpiderKindStateMixin), for its skin (mixin.SpiderSkinMixin). */
public final class SpiderSkins {
	public static final RenderStateDataKey<SpiderKinds.Kind> KIND = RenderStateDataKey.create(() -> "jaymoskingdoms:spider_kind");

	private SpiderSkins() {
	}
}
