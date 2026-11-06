package com.bountyhunt.mixin;

import com.bountyhunt.monster.BountyScaled;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	/** A downed player stays in the crawling pose, on the server and on every client. */
	@Inject(method = "updatePose", at = @At("HEAD"), cancellable = true)
	private void bountyhunt$crawlWhenDowned(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if ((Object) self instanceof BountyScaled scaled && scaled.bountyhunt$isDowned()) {
			self.setPose(EntityPose.SWIMMING);
			ci.cancel();
		}
	}
}
