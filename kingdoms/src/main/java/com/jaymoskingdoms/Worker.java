package com.jaymoskingdoms;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.21) One of a settlement's working people: a builder ({@link Folk#FOREMAN}, {@link Folk#WORKER}), a
 * {@link Folk#FARMER}, a {@link Folk#MINER} or a {@link Folk#WOODCUTTER}. Without Minecraft Comes Alive (and in
 * barbarian camps) that's a {@link Folk}; with it, a settlement's workers are its own MCA villagers, promoted to the
 * job in place ({@code McaWorkerMixin} makes them workers). The work code ({@link Construction}, {@link Mines},
 * {@link Farmers}, {@link Gatherers}, {@link Debris}) only sees this.
 * <p>
 * The defaults pass the mob's own methods through ({@link #mob()}); {@link Folk} and the villagers have them already.
 */
public interface Worker {
	/** The worker itself. */
	PathfinderMob mob();

	/** Its job (a {@link Folk} role id; a realm's MCA guard is a {@link Folk#GUARD}), or -1 for a villager who isn't in a settlement's service. */
	int role();

	/** The faction it works for. */
	int factionId();

	@Nullable BlockPos site();

	void setSite(@Nullable BlockPos site);

	String task();

	void setTask(String task);

	/** What it's carrying (the load in its hands, not a copy), or empty. */
	ItemStack carried();

	boolean isCarrying();

	/** Shows {@code load} in its hands, its tool put away; empty puts the tool back. */
	void setCarried(ItemStack load);

	/** Takes the load out of its hands (returned), its tool back in them. */
	default ItemStack takeCarried() {
		ItemStack load = this.carried();
		this.setCarried(ItemStack.EMPTY);
		return load;
	}

	/** Its tool (back in hand when it isn't carrying). */
	ItemStack tool();

	/** The tool for {@code role} (a camp's builders have stone ones). */
	static ItemStack toolFor(int role, boolean wild) {
		if (role == Folk.FARMER) {
			return new ItemStack(Items.IRON_HOE);
		}
		if (role == Folk.MINER) {
			return new ItemStack(Items.IRON_PICKAXE);
		}
		if (role == Folk.WOODCUTTER) {
			return new ItemStack(Items.IRON_AXE);
		}
		if (role == Folk.FOREMAN) {
			return new ItemStack(wild ? Items.STONE_AXE : Items.WOODEN_AXE);
		}
		return new ItemStack(wild ? Items.STONE_SHOVEL : Items.IRON_SHOVEL);
	}

	/** Whether {@code role} is one a worker does (and goes home to bed after). */
	static boolean isWork(int role) {
		return Folk.isBuilder(role) || role == Folk.FARMER || role == Folk.MINER || role == Folk.WOODCUTTER;
	}

	// ---- the mob's own ----------------------------------------------------------------------------------------------

	default PathNavigation getNavigation() {
		return this.mob().getNavigation();
	}

	default LookControl getLookControl() {
		return this.mob().getLookControl();
	}

	default Level level() {
		return this.mob().level();
	}

	default double getX() {
		return this.mob().getX();
	}

	default double getY() {
		return this.mob().getY();
	}

	default double getZ() {
		return this.mob().getZ();
	}

	default int getId() {
		return this.mob().getId();
	}

	default UUID getUUID() {
		return this.mob().getUUID();
	}

	default BlockPos blockPosition() {
		return this.mob().blockPosition();
	}

	default Vec3 position() {
		return this.mob().position();
	}

	default double distanceToSqr(Vec3 pos) {
		return this.mob().distanceToSqr(pos);
	}

	default double distanceToSqr(double x, double y, double z) {
		return this.mob().distanceToSqr(x, y, z);
	}

	default boolean isAlive() {
		return this.mob().isAlive();
	}

	default @Nullable LivingEntity getLastHurtByMob() {
		return this.mob().getLastHurtByMob();
	}

	default int getLastHurtByMobTimestamp() {
		return this.mob().getLastHurtByMobTimestamp();
	}

	/** How long it's been about (the mob's tick count). */
	default int ticksAlive() {
		return this.mob().tickCount;
	}

	default boolean swing(InteractionHand hand, SwingAnimation animation, boolean all) {
		return this.mob().swing(hand, animation, all);
	}

	default void teleportTo(double x, double y, double z) {
		this.mob().teleportTo(x, y, z);
	}

	default void setHomeTo(BlockPos home, int radius) {
		this.mob().setHomeTo(home, radius);
	}

	default BlockPos getHomePosition() {
		return this.mob().getHomePosition();
	}

	default int getHomeRadius() {
		return this.mob().getHomeRadius();
	}

	default ItemStack getMainHandItem() {
		return this.mob().getMainHandItem();
	}
}
