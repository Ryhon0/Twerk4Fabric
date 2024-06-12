package xyz.ryhon.twerk4fabric.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import xyz.ryhon.twerk4fabric.Twerk4Fabric;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
	@Inject(at = @At("HEAD"), method = "setSneaking")
	private void setSneaking(boolean sneaking, CallbackInfo info) {
		Entity th = (Entity)(Object)this;
		if(th instanceof ServerPlayerEntity p)
		{
			boolean wasSneaking = p.isSneaking();
			if(!wasSneaking && sneaking)
			{
				Twerk4Fabric.Twerk(p);
			}
		}
	}
}