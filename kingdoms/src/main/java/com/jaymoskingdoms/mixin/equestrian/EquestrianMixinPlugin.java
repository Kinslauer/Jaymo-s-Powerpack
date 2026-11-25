package com.jaymoskingdoms.mixin.equestrian;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * With Icy's Better Horses in the pack its screens and camera replace ours, so the mixins that restyle every mount's
 * inventory screen (they would redraw Icy's own horse inventory) are left out.
 */
public final class EquestrianMixinPlugin implements IMixinConfigPlugin {
	/** Left out when Icy's is there. */
	private static final Set<String> OFF_WITH_ICY = Set.of("AbstractMountInventoryScreenMixin", "MountLabelsMixin");

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		if (!FabricLoader.getInstance().isModLoaded("icys-better-horses")) {
			return true;
		}
		return !OFF_WITH_ICY.contains(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
