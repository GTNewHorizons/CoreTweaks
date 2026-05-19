package makamys.coretweaks.optimization.transformercache.lite;

import java.util.Map;

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
    private volatile boolean stopped = false;

    public CachedTransformerWrapper(TransformerData data, String transformerName) {
        this.data = data;
        this.transformerName = transformerName;
    }

    @Override
    public byte[] wrapTransform(String name, String transformedName, byte[] basicClass, TransformerProxy proxy) {
        if (basicClass == null) return null;
        if (this.stopped) {
            return proxy.invokeNextHandler(name, transformedName, basicClass);
        }
        runs++;
        final Map<String, CachedTransformation> map = this.data.transformationMap;
        byte[] result = getCached(map, transformedName, basicClass);
        if (result == null) {
            misses++;
            result = proxy.invokeNextHandler(name, transformedName, basicClass);
            putCached(map, transformedName, basicClass, result != null ? result.clone() : null);
        }
        return result;
    }

    private static byte[] getCached(Map<String, CachedTransformation> map, String transformedName,
        @Nonnull byte[] basicClass) {
        CachedTransformation cached = map.get(transformedName);
        if (cached != null && cached.basicClassMatches(basicClass)) {
            cached.updateAccessTime();
            if (!cached.isDiff()) {
                return basicClass;
            }
            byte[] transformedBytes = cached.getTransformedBytes(basicClass);
            if (transformedBytes == null) {
                map.remove(transformedName);
                return null;
            }
            return transformedBytes;
        }
        return null;
    }

    private static void putCached(Map<String, CachedTransformation> map, String transformedName,
        @Nonnull byte[] basicClass, byte[] transformedBytes) {
        CachedTransformation cached = new CachedTransformation(transformedName, basicClass, transformedBytes);
        if (cached.isValid()) {
            map.put(transformedName, cached);
        }
    }

    public String getProfileString() {
        return transformerName + "," + runs + "," + misses;
    }

    public void stopTransformer() {
        this.stopped = true;
    }
}
