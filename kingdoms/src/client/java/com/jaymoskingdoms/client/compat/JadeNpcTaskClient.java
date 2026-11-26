package com.jaymoskingdoms.client.compat;

import com.jaymoskingdoms.JadeNpcTask;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The client half of {@link JadeNpcTask}: the line under a person's name saying what they're doing ("Hunting",
 * "On patrol", "Going home"), from the server's answer. A trade with no line of its own reads "At work"; a settlement
 * worker's own task with no line shows its plain words.
 */
enum JadeNpcTaskClient implements IEntityComponentProvider {
	INSTANCE;

	private static final String PREFIX = "npc_task.jaymoskingdoms.";

	@Override
	public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
		String key = accessor.getServerData().getStringOr(JadeNpcTask.KEY, "");
		if (key.isEmpty()) {
			return;
		}
		String plain = accessor.getServerData().getStringOr(JadeNpcTask.PLAIN, "");
		MutableComponent line;
		if (!plain.isEmpty()) {
			line = Component.translatableWithFallback(PREFIX + key, plain);
		} else if (key.startsWith("trade.")) {
			line = Component.translatableWithFallback(PREFIX + key, Component.translatable(PREFIX + "working").getString());
		} else {
			line = Component.translatable(PREFIX + key);
		}
		tooltip.add(line.withStyle(ChatFormatting.GRAY));
	}

	@Override
	public Identifier getUid() {
		return JadeNpcTask.ID;
	}

	@Override
	public int getDefaultPriority() {
		// first under the name, above health and the rest
		return -10000;
	}
}
