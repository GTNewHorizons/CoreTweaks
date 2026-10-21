package makamys.coretweaks.optimization.transformerproxy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import makamys.coretweaks.api.IWrapper;

/**
 * <p>
 * A proxy for an {@link IClassTransformer} that intercepts the
 * {@link IClassTransformer#transform(String, String, byte[])} method and redirects it to a series of wrappers who may
 * modify the method call as they wish.
 * </p>
 */
public class TransformerProxy implements IClassTransformer, IWrapper<IClassTransformer> {

    protected IClassTransformer original;

    private final List<ITransformerWrapper> wrappers = new ArrayList<>();
    // classes can be transformed on several threads at once, so each thread walks the chain with its own cursor
    private final ThreadLocal<int[]> nextWrapper = ThreadLocal.withInitial(() -> new int[1]);

    public void addWrapper(ITransformerWrapper wrapper) {
        wrappers.add(0, wrapper);
    }

    public TransformerProxy(IClassTransformer original) {
        this.original = original;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        nextWrapper.get()[0] = -1;
        return invokeNextHandler(name, transformedName, basicClass);
    }

    public static TransformerProxy of(IClassTransformer transformer) throws Exception {
        Class<?> cls = TransformerProxyGenerator.generate(TransformerProxy.class, transformer.getClass());
        return (TransformerProxy) cls.getConstructor(IClassTransformer.class)
            .newInstance(transformer);
    }

    @Override
    public IClassTransformer getOriginal() {
        return original;
    }

    public byte[] invokeNextHandler(String name, String transformedName, byte[] basicClass) {
        int[] cursor = nextWrapper.get();
        int index = ++cursor[0];
        try {
            if (index == wrappers.size()) {
                return original.transform(name, transformedName, basicClass);
            } else {
                return wrappers.get(index)
                    .wrapTransform(name, transformedName, basicClass, this);
            }
        } finally {
            cursor[0]--;
        }
    }

    @Override
    public String toString() {
        String s = "TransformerProxy(";
        boolean first = true;
        for (ITransformerWrapper l : wrappers) {
            if (!first) {
                s += ";";
            }
            s += l.getClass()
                .getSimpleName();
            first = false;
        }
        s += ")";
        return s;
    }
}
