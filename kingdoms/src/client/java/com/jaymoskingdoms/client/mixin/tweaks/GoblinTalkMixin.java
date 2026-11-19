package com.jaymoskingdoms.client.mixin.tweaks;

import com.jaymoskingdoms.Dialects;
import com.jaymoskingdoms.GoblinTalk;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A goblin's line ({@link Dialects#broken}: our prefix before the line's own key) is looked up here in this client's
 * own language (MCA's lines too), in the goblin dialect where the line has one, and broken into goblin English
 * ({@link GoblinTalk}) before its names are put in. A line not found here keeps the server's broken English.
 */
@Mixin(TranslatableContents.class)
abstract class GoblinTalkMixin {
	/** Not a line: what {@link Language#getOrDefault} gives back for a key this client doesn't have. */
	@org.spongepowered.asm.mixin.Unique
	private static final String JAYMOSKINGDOMS$NONE = "\u0000";

	@Shadow
	@Final
	private String key;

	@ModifyArg(method = "decompose", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/network/chat/contents/TranslatableContents;decomposeTemplate(Ljava/lang/String;Ljava/util/function/Consumer;)V"), index = 0)
	private String jaymoskingdoms$goblin(String template) {
		if (!this.key.startsWith(Dialects.BROKEN)) {
			return template;
		}
		String line = this.key.substring(Dialects.BROKEN.length());
		Language language = Language.getInstance();
		String said = language.getOrDefault("dialect.jaymoskingdoms.goblin." + line, JAYMOSKINGDOMS$NONE);
		if (said.equals(JAYMOSKINGDOMS$NONE)) {
			said = language.getOrDefault(line, JAYMOSKINGDOMS$NONE);
		}
		return said.equals(JAYMOSKINGDOMS$NONE) ? template : GoblinTalk.speak(said);
	}
}
