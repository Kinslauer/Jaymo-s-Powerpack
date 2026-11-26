package com.jaymoskingdoms;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * The server half of the task line under a person's name in Jade's panel ({@link NpcTasks}): what they're doing is
 * worked out here, where the systems that keep them busy live, and sent with the panel's data. Only loaded by Jade
 * (registered from {@code client/compat/JadeIntroductions}, which Jade loads on both sides).
 */
public enum JadeNpcTask implements IServerDataProvider<EntityAccessor> {
	INSTANCE;

	/** The provider's id (the client half shares it) and the data's keys. */
	public static final Identifier ID = Identifier.fromNamespaceAndPath(JaymosKingdoms.MOD_ID, "npc_task");
	public static final String KEY = "JkTask", PLAIN = "JkTaskPlain";

	@Override
	public void appendServerData(CompoundTag data, EntityAccessor accessor) {
		if (!(accessor.getEntity() instanceof Villager v) || !v.isAlive()) {
			return;
		}
		NpcTasks.Task task = NpcTasks.of(v);
		if (task != null) {
			data.putString(KEY, task.key());
			if (task.plain() != null) {
				data.putString(PLAIN, task.plain());
			}
		}
	}

	@Override
	public Identifier getUid() {
		return ID;
	}
}
