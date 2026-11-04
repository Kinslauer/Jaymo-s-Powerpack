package com.jaymospowers.client.compat;

import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * (0.20.50) Respawn Recap's {@code GuiMixin} keeps the death screen (and, while it plays, every screen) from opening, for
 * everyone, always. Andy: "limit it to chrono". Its handler can't be targeted by a mixin, so once it has been merged into
 * {@code Gui} (our {@code RecapGuiMarkerMixin} is applied after it), its first lines become
 * {@code if (!ChronoCheatDeathClient.recapMayHide(screen)) return;}: outside Chrono's rewind it does nothing at all.
 * Respawn Recap's jar itself is untouched. None of this config applies when it isn't installed.
 */
public final class RespawnRecapPlugin implements IMixinConfigPlugin {
	private static final Logger LOGGER = LoggerFactory.getLogger("jaymospowers");
	private static final String HANDLER = "respawnrecap$suppressDeathScreen";
	private static final String SCREEN = "Lnet/minecraft/client/gui/screens/Screen;";

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return FabricLoader.getInstance().isModLoaded("respawnrecap");
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
		if (!mixinClassName.endsWith("RecapGuiMarkerMixin")) {
			return;
		}
		for (MethodNode method : targetClass.methods) {
			if (method.name.endsWith(HANDLER) && method.desc.startsWith("(" + SCREEN) && (method.access & Opcodes.ACC_STATIC) == 0) {
				InsnList gate = new InsnList();
				LabelNode go = new LabelNode();
				gate.add(new VarInsnNode(Opcodes.ALOAD, 1));
				gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/jaymospowers/client/power/chrono/ChronoCheatDeathClient",
					"recapMayHide", "(" + SCREEN + ")Z", false));
				gate.add(new JumpInsnNode(Opcodes.IFNE, go));
				gate.add(new InsnNode(Opcodes.RETURN));
				gate.add(go);
				method.instructions.insert(gate);
				LOGGER.info("Respawn Recap's death-screen takeover is limited to Chrono's rewind ({})", method.name);
				return;
			}
		}
		LOGGER.warn("Respawn Recap's death-screen handler wasn't found in Gui; its replay may hide death screens");
	}
}
