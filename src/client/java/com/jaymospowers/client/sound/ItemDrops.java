package com.jaymospowers.client.sound;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Items that sound as they land (Andy's ImmersiveFX pick, built as our own): a dropped sword rings, a log thuds, coins
 * clink, a stone clacks, a bottle tinks, a gem ticks, paper flaps, bone clacks, and soft things (food, leather, wool)
 * land with a soft thump ({@link SoundKinds#stuff}). Each item sounds once, the first time it touches the ground after
 * falling; one that was lying still when it came into view, or that floats, says nothing. A bigger stack lands a little
 * louder. Looked for within {@link #RADIUS} every {@link #EVERY} ticks.
 */
public final class ItemDrops {
	static final double RADIUS = 14.0;
	static final int EVERY = 2;
	static final float VOLUME = 0.3F, STACK_BONUS = 0.25F;

	/** Item → still falling (false once it has landed, or if it was never seen falling). */
	private static final Map<ItemEntity, Boolean> FALLING = new WeakHashMap<>();
	private static ClientLevel lastLevel;

	private ItemDrops() {
	}

	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(ItemDrops::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null || mc.isPaused()) {
			return;
		}
		if (level != lastLevel) {
			lastLevel = level;
			FALLING.clear();
		}
		if (ImmersiveSounds.tick() % EVERY != 0) {
			return;
		}
		AABB around = player.getBoundingBox().inflate(RADIUS);
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, e -> !e.isRemoved())) {
			boolean ground = item.onGround();
			Boolean falling = FALLING.get(item);
			if (falling == null) {
				FALLING.put(item, !ground && !item.isInWater());
				continue;
			}
			if (falling && ground) {
				FALLING.put(item, false);
				int count = item.getItem().getCount();
				float volume = VOLUME * (1.0F + STACK_BONUS * Mth.clamp((count - 1) / 16.0F, 0.0F, 1.0F));
				ImmersiveSounds.play(level, item.getX(), item.getY(), item.getZ(), "drop." + SoundKinds.stuff(item.getItem()).id,
					SoundSource.NEUTRAL, volume, 0.92F + level.getRandom().nextFloat() * 0.16F);
			} else if (falling && item.isInWater()) {
				FALLING.put(item, false);
			}
		}
	}
}
