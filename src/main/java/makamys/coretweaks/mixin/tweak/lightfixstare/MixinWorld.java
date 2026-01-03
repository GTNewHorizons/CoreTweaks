package makamys.coretweaks.mixin.tweak.lightfixstare;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import makamys.coretweaks.tweak.LightFixStare;

@Mixin(World.class)
public abstract class MixinWorld {

    @Inject(method = "setActivePlayerChunksAndCheckLight", at = @At("TAIL"))
    private void postPlayerCheckLight(CallbackInfo ci) {
        LightFixStare.postPlayerCheckLight((World) (Object) this);
    }
}
