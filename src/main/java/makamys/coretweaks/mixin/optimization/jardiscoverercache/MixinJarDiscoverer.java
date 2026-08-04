package makamys.coretweaks.mixin.optimization.jardiscoverercache;

import static makamys.coretweaks.CoreTweaks.LOGGER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModContainerFactory;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.discovery.JarDiscoverer;
import cpw.mods.fml.common.discovery.ModCandidate;
import cpw.mods.fml.common.discovery.asm.ASMModParser;
import makamys.coretweaks.optimization.JarDiscovererCache;
import makamys.coretweaks.optimization.JarDiscovererCache.CachedModInfo;

@Mixin(value = JarDiscoverer.class, remap = false)
public abstract class MixinJarDiscoverer {

    @Unique
    private CachedModInfo crtw$lastCMI;

    /** Load the saved result if the jar's path and modification date haven't changed. */
    @Inject(method = "discover", at = @At("HEAD"))
    private void preDiscover(ModCandidate candidate, ASMDataTable table,
        CallbackInfoReturnable<List<ModContainer>> cir) {
        File file = candidate.getModContainer();
        String lastHash = file.getPath() + "@" + file.lastModified();
        crtw$lastCMI = JarDiscovererCache.instance.getCachedModInfo(lastHash);
        LOGGER.debug("preDiscover {}(hash {})", candidate.getModContainer(), lastHash);
    }

    /** Try to load cached ASMModParser instead of creating a new one. */
    @Redirect(method = "discover", at = @At(value = "NEW", target = "cpw/mods/fml/common/discovery/asm/ASMModParser"))
    private ASMModParser redirectNewASMModParser(InputStream stream, @Local(name = "ze") ZipEntry ze)
        throws IOException {
        ASMModParser parser = crtw$lastCMI.getCachedParser(ze);
        if (parser == null) {
            try (stream) {
                parser = new ASMModParser(stream);
            }
            crtw$lastCMI.putParser(ze, parser);
        }
        return parser;
    }

    /** Remember if the ModContainer was null last time; if it was, return null instead of trying to create one. */
    @Redirect(
        method = "discover",
        at = @At(
            value = "INVOKE",
            target = "Lcpw/mods/fml/common/ModContainerFactory;build(Lcpw/mods/fml/common/discovery/asm/ASMModParser;Ljava/io/File;Lcpw/mods/fml/common/discovery/ModCandidate;)Lcpw/mods/fml/common/ModContainer;"))
    private ModContainer redirectBuild(ModContainerFactory factory, ASMModParser modParser, File modSource,
        ModCandidate container, @Local(name = "ze") ZipEntry ze) {
        int isModClass = crtw$lastCMI.getCachedIsModClass(ze);
        ModContainer mc = null;
        if (isModClass != 0) {
            mc = factory.build(modParser, modSource, container);
            if (isModClass == -1) {
                crtw$lastCMI.putIsModClass(ze, mc != null);
            }
        }
        return mc;
    }
}
