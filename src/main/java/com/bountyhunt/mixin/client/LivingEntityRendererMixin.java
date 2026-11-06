package com.bountyhunt.mixin.client;

import com.bountyhunt.client.TierTextures;
import com.bountyhunt.monster.BountyScaled;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.client.render.entity.LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	/**
	 * Scales the model right after the renderer's own scale() step. This happens inside the renderer's
	 * push/pop pair and before the name tag is drawn, so the model grows but the name tag is not scaled twice.
	 */
	@Inject(
			method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;scale(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V",
					shift = At.Shift.AFTER))
	private void bountyhunt$applyBountyScale(LivingEntity entity, float yaw, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
		if ((Object) entity instanceof BountyScaled scaled) {
			float s = scaled.bountyhunt$getScale();
			if (s != 1.0f) {
				matrices.scale(s, s, s);
			}
		}
	}

	/** Swaps in the difficulty-tier texture (same UV layout as vanilla) when one has been generated. */
	@Inject(method = "getRenderLayer", at = @At("HEAD"), cancellable = true)
	private void bountyhunt$tierTexture(LivingEntity entity, boolean showBody, boolean translucent, boolean showOutline,
			CallbackInfoReturnable<RenderLayer> cir) {
		if (translucent) {
			return;
		}
		Identifier texture = TierTextures.of(entity);
		if (texture == null) {
			return;
		}
		if (showBody) {
			cir.setReturnValue(RenderLayer.getEntityCutoutNoCull(texture));
		} else if (showOutline) {
			cir.setReturnValue(RenderLayer.getOutline(texture));
		}
	}
}
