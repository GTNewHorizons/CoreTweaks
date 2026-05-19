package makamys.coretweaks.optimization.transformercache.lite;

import javax.annotation.Nonnull;

import makamys.coretweaks.optimization.transformercache.lite.TransformerCache.TransformerData;
import makamys.coretweaks.optimization.transformercache.lite.TransformerCache.TransformerData.CachedTransformation;
import makamys.coretweaks.optimization.transformerproxy.ITransformerWrapper;
import makamys.coretweaks.optimization.transformerproxy.TransformerProxy;

public class CachedTransformerWrapper implements ITransformerWrapper {

    private final TransformerData data;
    private final String transformerName;
    private int runs = 0;
    private int misses = 0;

    public CachedTransformerWrapper(TransformerData data, String transformerName) {
        this.data = data;
        this.transformerName = transformerName;
    }

    @Override
    public byte[] wrapTransform(String name, String transformedName, byte[] basicClass, TransformerProxy proxy) {
        if (basicClass == null) return null;
        runs++;
        byte[] result = this.getCached(transformedName, basicClass);
        if (result == null) {
            misses++;
            result = proxy.invokeNextHandler(name, transformedName, basicClass);
            this.putCached(transformedName, basicClass, result != null ? result.clone() : null);
        }
        return result;
    }

    private byte[] getCached(String transformedName, @Nonnull byte[] basicClass) {
        CachedTransformation cached = this.data.transformationMap.get(transformedName);
        if (cached != null && cached.basicClassMatches(basicClass)) {
            cached.updateAccessTime();
            if (!cached.isDiff()) {
                return basicClass;
            }
            byte[] transformedBytes = cached.getTransformedBytes(basicClass);
            if (transformedBytes == null) {
                this.data.transformationMap.remove(transformedName);
                return null;
            }
            return transformedBytes;
        }
        return null;
    }

    private void putCached(String transformedName, @Nonnull byte[] basicClass, byte[] transformedBytes) {
        CachedTransformation cached = new CachedTransformation(transformedName, basicClass, transformedBytes);
        if (cached.isValid()) {
            this.data.transformationMap.put(transformedName, cached);
        }
    }

    public String getProfileString() {
        return transformerName + "," + runs + "," + misses;
    }
}
