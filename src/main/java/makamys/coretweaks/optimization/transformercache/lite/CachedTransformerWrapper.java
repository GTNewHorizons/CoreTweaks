package makamys.coretweaks.optimization.transformercache.lite;

import net.minecraft.launchwrapper.IClassTransformer;

import makamys.coretweaks.optimization.transformerproxy.ITransformerWrapper;
import makamys.coretweaks.optimization.transformerproxy.TransformerProxy;

public class CachedTransformerWrapper implements ITransformerWrapper {

    public int runs = 0;
    public int misses = 0;

    public CachedTransformerWrapper(IClassTransformer original) {
        this.transformerName = original.getClass()
            .getCanonicalName();
    }

    private String transformerName;

    @Override
    public byte[] wrapTransform(String name, String transformedName, byte[] basicClass, TransformerProxy proxy) {
        runs++;
        byte[] result = TransformerCache.instance.getCached(transformerName, name, transformedName, basicClass);
        if (result == null) {
            misses++;
            TransformerCache.instance.prePutCached(transformerName, name, transformedName, basicClass);
            result = proxy.invokeNextHandler(name, transformedName, basicClass);
            TransformerCache.instance.putCached(transformerName, name, transformedName, result);
        }
        return TransformerCache.fromNullableByteArray(result);
    }
}
