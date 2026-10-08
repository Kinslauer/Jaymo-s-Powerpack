package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.NonNullList;
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
 * Pocket Stash: a personal 27-slot pocket dimension. The contents live in a player attachment (saved, kept through
 * death) and open in a vanilla chest screen. Summoned weapons can't be put in (see {@code SlotMixin}).
 */
public final class ArsenalStash {
	public static final int SIZE = 27;

	public static final AttachmentType<ItemContainerContents> STASH = AttachmentRegistry.create(JaymosPowers.id("arsenal_stash"),
		builder -> builder.persistent(ItemContainerContents.CODEC).copyOnDeath());

	private ArsenalStash() {
	}

	public static void init() {
		// forces the attachment type to register
	}

	public static void open(ServerPlayer player) {
		StashContainer container = new StashContainer(player);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, container),
			Component.translatable("container.jaymospowers.arsenal_stash")));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.6F, 1.3F);
		player.level().sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
			player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.5, 0.4, 0.02);
	}

	/** The stash as a container, writing every change straight back to the attachment. */
	public static final class StashContainer extends SimpleContainer {
		private final ServerPlayer owner;
		private boolean loading;

		StashContainer(ServerPlayer owner) {
			super(SIZE);
			this.owner = owner;
			ItemContainerContents contents = owner.getAttachedOrElse(STASH, ItemContainerContents.EMPTY);
			NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
			contents.copyInto(items);
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
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return !Summoned.is(stack);
		}

		@Override
		public boolean stillValid(Player player) {
			return player == owner && owner.isAlive();
		}

		@Override
		public void stopOpen(ContainerUser user) {
			super.stopOpen(user);
			save();
			owner.level().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.5F, 1.3F);
		}
	}
}
