package com.bountyhunt.mixin;

import com.bountyhunt.monster.BountyScaled;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.data.TrackedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/** Scales the hitbox (width and height proportionally) for bounty monsters. Also affects reach, pathing and eye height. */
	@Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
	private void bountyhunt$scaleDimensions(EntityPose pose, CallbackInfoReturnable<EntityDimensions> cir) {
		if ((Object) this instanceof BountyScaled scaled) {
			float s = scaled.bountyhunt$getScale();
			if (s != 1.0f) {
				cir.setReturnValue(cir.getReturnValue().scaled(s));
			}
		}
	}

	/** On the client, recompute the hitbox when the synced scale value arrives. */
	@Inject(method = "onTrackedDataSet", at = @At("HEAD"))
	private void bountyhunt$trackedDataSet(TrackedData<?> data, CallbackInfo ci) {
		if ((Object) this instanceof BountyScaled scaled) {
			scaled.bountyhunt$onTrackedData(data);
		}
	}
}
