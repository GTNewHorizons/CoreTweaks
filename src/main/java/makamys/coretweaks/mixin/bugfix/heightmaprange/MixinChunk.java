package makamys.coretweaks.mixin.bugfix.heightmaprange;

import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Chunk.class)
public abstract class MixinChunk {

    // See https://bugs.mojang.com/browse/MC-7508
    @ModifyVariable(
        method = { "generateHeightMap", "generateSkylightMap" },
        at = @At(value = "STORE", ordinal = 0),
        name = "l")
    public int modifyYIterator(int value) {
        return value + 1;
    }

}
