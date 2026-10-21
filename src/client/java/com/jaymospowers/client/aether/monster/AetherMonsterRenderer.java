package com.jaymospowers.client.aether.monster;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.aether.monster.AechorPlant;
import com.jaymospowers.aether.monster.BladeshroomHunter;
import com.jaymospowers.aether.monster.CarrionSprout;
import com.jaymospowers.aether.monster.Cockatrice;
import com.jaymospowers.aether.monster.GravititeTaluton;
import com.jaymospowers.aether.monster.Skephid;
import com.jaymospowers.aether.monster.Swet;
import com.jaymospowers.aether.monster.Tempest;
import com.jaymospowers.aether.monster.Zephyr;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Draws the Aether's hostile mobs: one renderer, eleven looks. What differs per mob (which skin, what it adds to the
 * state, how far it sinks, whether it is see-through, whether it has a glowing layer) is the {@link Look}; the model
 * does the posing from the {@link State}.
 */
final class AetherMonsterRenderer<T extends Mob> extends MobRenderer<T, AetherMonsterRenderer.State, AetherMonsterModel> {
	static final class State extends LivingEntityRenderState {
		/** What it is doing (each model says what the numbers mean). */
		int action;
		/** 0 to 1: how far into whatever it is doing (hiding, digging, burying, snapping...). */
		float progress;
		boolean ceiling, armed, glowing, charging;
		/** The swing of a bite or blow, 0 to 1, and the ticks into a strike (-1 for none). */
		float attack, strike = -1.0F;
		/** A swet's squash: 0 sitting, positive in the air (stretch). */
		float stretch;
	}

	/** What one mob adds to the state, and how it is drawn. */
	interface Look<T extends Mob> {
		void extract(T mob, State state, float partialTicks);

		/** The skin's name (a png under textures/entity/aether/). */
		String skin(State state);

		/** How far it sinks into the ground (blocks). */
		default float sink(State state) {
			return 0.0F;
		}

		/** An extra scale on top of its size. */
		default float scale(State state) {
			return 1.0F;
		}

		default boolean translucent() {
			return false;
		}

		/** The name of a glowing overlay under textures/entity/aether/, or null. */
		default @Nullable String glow() {
			return null;
		}
	}

	private final Look<T> look;

	AetherMonsterRenderer(EntityRendererProvider.Context context, AetherMonsterModel model, float shadow, Look<T> look) {
		super(context, model, shadow);
		this.look = look;
		String glow = look.glow();
		if (glow != null) {
			RenderType eyes = RenderTypes.eyes(texture(glow));
			this.addLayer(new EyesLayer<>(this) {
				@Override
				public RenderType renderType() {
					return eyes;
				}
			});
		}
	}

	private static Identifier texture(String name) {
		return JaymosPowers.id("textures/entity/aether/" + name + ".png");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T mob, State state, float partialTicks) {
		super.extractRenderState(mob, state, partialTicks);
		state.attack = mob.getSwingAnimation(partialTicks);
		state.stretch = mob.onGround() ? 0.0F : Mth.clamp((float)mob.getDeltaMovement().y * 2.5F, -0.4F, 0.6F);
		this.look.extract(mob, state, partialTicks);
		state.isUpsideDown = state.ceiling;
	}

	@Override
	protected void setupRotations(State state, PoseStack poseStack, float bodyRot, float scale) {
		float sink = this.look.sink(state);
		if (sink != 0.0F) {
			poseStack.translate(0.0F, -sink, 0.0F);
		}
		super.setupRotations(state, poseStack, bodyRot, scale);
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		float scale = this.look.scale(state);
		if (this.look.translucent()) {
			// a swet stretches in the air and squashes when it lands
			float sy = 1.0F + state.stretch * 0.5F;
			poseStack.scale(scale / Mth.sqrt(sy), scale * sy, scale / Mth.sqrt(sy));
		} else if (scale != 1.0F) {
			poseStack.scale(scale, scale, scale);
		}
	}

	@Override
	protected RenderType getRenderType(State state, boolean bodyVisible, boolean translucent, boolean glowing) {
		if (this.look.translucent() && bodyVisible) {
			return RenderTypes.entityTranslucent(this.getTextureLocation(state));
		}
		return super.getRenderType(state, bodyVisible, translucent, glowing);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return texture(this.look.skin(state));
	}

	// ---- the looks ----

	static Look<Zephyr> zephyr() {
		return new Look<>() {
			@Override
			public void extract(Zephyr mob, State state, float partialTicks) {
				state.action = mob.action();
			}

			@Override
			public String skin(State state) {
				return "zephyr";
			}
		};
	}

	static Look<Tempest> tempest() {
		return new Look<>() {
			@Override
			public void extract(Tempest mob, State state, float partialTicks) {
				state.charging = mob.charging();
				state.progress = mob.hideProgress();
			}

			@Override
			public String skin(State state) {
				return "tempest";
			}

			@Override
			public float scale(State state) {
				// hiding from the sun, it shrinks away
				return 1.0F - 0.85F * state.progress;
			}

			@Override
			public String glow() {
				return "tempest_glow";
			}
		};
	}

	static Look<Cockatrice> cockatrice() {
		return new Look<>() {
			@Override
			public void extract(Cockatrice mob, State state, float partialTicks) {
				state.action = mob.spitting() ? 1 : 0;
				state.progress = mob.digProgress();
			}

			@Override
			public String skin(State state) {
				return "cockatrice";
			}

			@Override
			public float sink(State state) {
				return state.progress * 2.1F;
			}

			@Override
			public String glow() {
				return "cockatrice_glow";
			}
		};
	}

	static Look<Swet> swet(boolean golden) {
		return new Look<>() {
			@Override
			public void extract(Swet mob, State state, float partialTicks) {
				state.progress = Mth.clamp(mob.saturation() / Swet.FULL_SATURATION, 0.0F, 1.0F);
			}

			@Override
			public String skin(State state) {
				return golden ? "golden_swet" : "blue_swet";
			}

			@Override
			public boolean translucent() {
				return true;
			}
		};
	}

	static Look<AechorPlant> aechorPlant() {
		return new Look<>() {
			@Override
			public void extract(AechorPlant mob, State state, float partialTicks) {
				state.charging = mob.budding();
			}

			@Override
			public String skin(State state) {
				return "aechor_plant";
			}
		};
	}

	static Look<CarrionSprout> carrionSprout() {
		return new Look<>() {
			@Override
			public void extract(CarrionSprout mob, State state, float partialTicks) {
				state.armed = mob.armed();
				state.progress = Mth.clamp(mob.snapped() / 8.0F, 0.0F, 1.0F);
			}

			@Override
			public String skin(State state) {
				return "carrion_sprout";
			}
		};
	}

	static Look<Skephid> skephid() {
		return new Look<>() {
			@Override
			public void extract(Skephid mob, State state, float partialTicks) {
				state.ceiling = mob.onCeiling();
				state.action = mob.casting() >= 0 ? 1 : 0;
			}

			@Override
			public String skin(State state) {
				return "skephid";
			}

			@Override
			public String glow() {
				return "skephid_glow";
			}
		};
	}

	static Look<com.jaymospowers.aether.monster.ArkeniumTaluton> arkeniumTaluton() {
		return new Look<>() {
			@Override
			public void extract(com.jaymospowers.aether.monster.ArkeniumTaluton mob, State state, float partialTicks) {
			}

			@Override
			public String skin(State state) {
				return "arkenium_taluton";
			}

			@Override
			public String glow() {
				return "arkenium_taluton_glow";
			}
		};
	}

	static Look<GravititeTaluton> gravititeTaluton() {
		return new Look<>() {
			@Override
			public void extract(GravititeTaluton mob, State state, float partialTicks) {
				state.glowing = mob.glowing();
			}

			@Override
			public String skin(State state) {
				return "gravitite_taluton";
			}

			@Override
			public String glow() {
				return "gravitite_taluton_glow";
			}
		};
	}

	static Look<BladeshroomHunter> bladeshroomHunter() {
		return new Look<>() {
			@Override
			public void extract(BladeshroomHunter mob, State state, float partialTicks) {
				state.progress = mob.depth();
				state.strike = mob.strike() < 0 ? -1.0F : mob.strike() + partialTicks;
			}

			@Override
			public String skin(State state) {
				return "bladeshroom_hunter";
			}

			@Override
			public float sink(State state) {
				// only the caps show when it is buried
				return state.progress * 0.62F;
			}

			@Override
			public String glow() {
				return "bladeshroom_hunter_glow";
			}
		};
	}
}
