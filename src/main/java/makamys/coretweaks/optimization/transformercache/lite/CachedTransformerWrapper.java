package makamys.coretweaks.optimization.transformercache.lite;

import static makamys.coretweaks.optimization.transformercache.lite.TransformerCache.calculateHash;
import static makamys.coretweaks.optimization.transformercache.lite.TransformerCache.nullSafeLength;

import makamys.coretweaks.optimization.transformercache.lite.TransformerCache.TransformerData;
import makamys.coretweaks.optimization.transformercache.lite.TransformerCache.TransformerData.CachedTransformation;
import makamys.coretweaks.optimization.transformerproxy.ITransformerWrapper;
import makamys.coretweaks.optimization.transformerproxy.TransformerProxy;

public class CachedTransformerWrapper implements ITransformerWrapper {

    private static final byte[] NULL_BYTE_ARRAY = new byte[0];

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
        runs++;
        byte[] result = this.getCached(transformedName, basicClass);
        if (result == null) {
            misses++;
            result = proxy.invokeNextHandler(name, transformedName, basicClass);
            this.putCached(transformedName, basicClass, result != null ? result.clone() : null);
        }
        return fromNullableByteArray(result);
    }

    private byte[] getCached(String transformedName, byte[] basicClass) {
        CachedTransformation trans = this.data.transformationMap.get(transformedName);
        if (trans != null) {
            if (nullSafeLength(basicClass) == trans.preLength && calculateHash(basicClass) == trans.preHash) {
                trans.updateAccessTime();
                if (trans.postHash == trans.preHash) {
                    return toNullableByteArray(basicClass);
                }
                byte[] result = trans.getNewClass(basicClass);
                if (result == null) {
                    this.data.transformationMap.remove(transformedName);
                    return null;
                }
                return result;
            }
        }
        return null;
    }

    private void putCached(String transformedName, byte[] basicClass, byte[] transformedBytes) {
        CachedTransformation cached = new CachedTransformation(
            transformedName,
            basicClass,
            nullSafeLength(basicClass),
            transformedBytes);
        if (cached.isValid()) {
            this.data.transformationMap.put(transformedName, cached);
        }
    }

    private static byte[] toNullableByteArray(byte[] array) {
        return array == null ? NULL_BYTE_ARRAY : array;
    }

    private static byte[] fromNullableByteArray(byte[] array) {
        return array == NULL_BYTE_ARRAY ? null : array;
    }

    public String getProfileString() {
        return transformerName + "," + runs + "," + misses;
    }
}
