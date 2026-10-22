package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The Void Pocket (Nadir, sneak + G): {@link #SIZE} slots of the void, kept on the player (saved, through death) and
 * opened in a vanilla chest screen, only while Nadir is out. What's put in waits for the next Nadir.
 */
public final class NadirStash {
	public static final int SIZE = 27;

	public static final AttachmentType<ItemContainerContents> STASH = AttachmentRegistry.create(JaymosPowers.id("nadir_stash"),
		builder -> builder.persistent(ItemContainerContents.CODEC).copyOnDeath());

	private NadirStash() {
	}

	public static void open(ServerPlayer player) {
		Pocket pocket = new Pocket(player);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, pocket),
			Component.translatable("container.jaymospowers.nadir_stash")));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.6F, 0.6F);
		player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.4, 0.5, 0.4, 0.05);
	}

	/** The pocket as a container, writing every change straight back to the attachment. */
	static final class Pocket extends SimpleContainer {
		private final ServerPlayer owner;
		private boolean loading;

		Pocket(ServerPlayer owner) {
			super(SIZE);
			this.owner = owner;
			NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
			owner.getAttachedOrElse(STASH, ItemContainerContents.EMPTY).copyInto(items);
			loading = true;
			for (int i = 0; i < SIZE; i++) {
				setItem(i, items.get(i));
			}
			loading = false;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (!loading) {
				save();
			}
		}

		private void save() {
			owner.setAttached(STASH, ItemContainerContents.fromItems(getItems()));
		}

		@Override
		public boolean stillValid(Player player) {
			return player == owner && owner.isAlive();
		}

		@Override
		public void stopOpen(ContainerUser user) {
			super.stopOpen(user);
			save();
			owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.5F, 0.6F);
		}
	}
}
