package makamys.coretweaks.mixin.tweak.farplane;

import net.minecraft.client.renderer.EntityRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import makamys.coretweaks.tweak.FarPlaneDistanceTweaker;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer {

    @ModifyArg(
        method = "setupCameraTransform",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false),
        index = 3)
    private float modifyFarPlane(float original) {
        return FarPlaneDistanceTweaker.modifyFarPlane(original);
    }

}
