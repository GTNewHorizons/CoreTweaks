package makamys.coretweaks.optimization.transformercache.lite;

import static makamys.coretweaks.CoreTweaks.LOGGER;
import static makamys.coretweaks.CoreTweaks.logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import com.esotericsoftware.kryo.kryo5.unsafe.UnsafeInput;
import com.esotericsoftware.kryo.kryo5.unsafe.UnsafeOutput;
import com.google.common.collect.Sets;
import com.google.common.hash.Hashing;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import cpw.mods.fml.repackage.com.nothome.delta.Delta;
import cpw.mods.fml.repackage.com.nothome.delta.GDiffWriter;
import lombok.EqualsAndHashCode;
import makamys.coretweaks.Config;
import makamys.coretweaks.CoreTweaks;
import makamys.coretweaks.IModEventListener;
import makamys.coretweaks.optimization.transformercache.lite.TransformerCache.TransformerData.CachedTransformation;
import makamys.coretweaks.optimization.transformerproxy.ITransformerWrapper;
import makamys.coretweaks.optimization.transformerproxy.TransformerProxyManager;
import makamys.coretweaks.optimization.transformerproxy.TransformerProxyManager.ITransformerWrapperProvider;
import makamys.coretweaks.util.FastByteBufferSeekableSource;
import makamys.coretweaks.util.InMemoryGDiffPatcher;
import makamys.coretweaks.util.Util;

/*
 * Format:
 * int8 0
 * int8 version
 * CacheMeta meta
 * Map<String, TransformerData> map
 */
public class TransformerCache implements IModEventListener, ITransformerWrapperProvider {

    public static final TransformerCache instance = new TransformerCache();

    private final List<CachedTransformerWrapper> myTransformers = new ArrayList<>();
    private final Map<String, TransformerData> transformerMap = new ConcurrentHashMap<>();
    private final CacheMeta meta = new CacheMeta();

    private static final byte MAGIC_0 = 0;
    private static final byte VERSION = 3;

    private static final File DAT_OLD = Util.childFile(CoreTweaks.CACHE_DIR, "transformerCache.dat");
    private static final File DAT = Util.childFile(CoreTweaks.CACHE_DIR, "classTransformerLite.cache");
    private static final File DAT_ERRORED = Util.childFile(CoreTweaks.CACHE_DIR, "classTransformerLite.cache.errored");
    private static final File TRANSFORMERCACHE_PROFILER_CSV = Util
        .childFile(CoreTweaks.OUT_DIR, "transformercache_profiler.csv");
    private Kryo kryo;

    private static final ThreadLocal<Delta> deltaThreadLocal = ThreadLocal.withInitial(Delta::new);

    private static final byte[] NULL_BYTE_ARRAY = new byte[0];

    private Set<String> transformersToCache = new HashSet<>();

    private volatile boolean savedAndCleared = false;

    private static class HashMemo {

        byte[] data;
        int value;
    }

    private static final ThreadLocal<HashMemo> memoizedHash = ThreadLocal.withInitial(HashMemo::new);

    public void init(boolean late) {

        transformersToCache = Sets.newHashSet(Config.transformersToCache.get());

        // We get a ClassCircularityError if we don't add these
        Launch.classLoader
            .addTransformerExclusion("makamys.coretweaks.optimization.transformercache.lite.TransformerCache");
        Launch.classLoader.addTransformerExclusion("makamys.coretweaks.util.InMemoryGDiffPatcher");
        Launch.classLoader.addTransformerExclusion("makamys.coretweaks.util.FastByteBufferSeekableSource");

        loadData();

        TransformerProxyManager.instance.addAdditionListener(this, !late);
    }

    @Override
    public ITransformerWrapper wrap(IClassTransformer transformer) {
        if (transformersToCache.contains(
            transformer.getClass()
                .getCanonicalName())) {
            CachedTransformerWrapper proxy = new CachedTransformerWrapper(transformer);
            myTransformers.add(proxy);
            return proxy;
        } else {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadData() {
        long t0 = System.nanoTime();

        kryo = new Kryo();
        kryo.register(TransformerCache.CacheMeta.class);
        kryo.register(ConcurrentHashMap.class);
        kryo.register(TransformerCache.TransformerData.class);
        kryo.register(TransformerCache.TransformerData.CachedTransformation.class);
        kryo.register(byte[].class);

        if (DAT_OLD.exists() && !DAT.exists()) {
            LOGGER.info("Migrating class cache: {} -> {}", DAT_OLD, DAT);
            DAT_OLD.renameTo(DAT);
        }

        if (DAT.exists()) {
            try (Input is = new UnsafeInput(new BufferedInputStream(new FileInputStream(DAT)))) {
                byte magic0 = kryo.readObject(is, byte.class);
                byte version = kryo.readObject(is, byte.class);

                CacheMeta storedMeta = kryo.readObject(is, CacheMeta.class);
                if (magic0 != MAGIC_0 || version != VERSION) {
                    CoreTweaks.LOGGER.warn("Transformer cache is either a different version or corrupted, discarding.");
                } else if (!storedMeta.equals(meta)) {
                    CoreTweaks.LOGGER.warn("Transformer cache settings have changed, discarding.");
                } else {
                    transformerMap.putAll(kryo.readObject(is, ConcurrentHashMap.class));
                }

                Iterator<Entry<String, TransformerData>> it = transformerMap.entrySet()
                    .iterator();
                while (it.hasNext()) {
                    Entry<String, TransformerData> e = it.next();
                    if (!Arrays.asList(Config.transformersToCache.get())
                        .contains(e.getKey())) {
                        CoreTweaks.LOGGER
                            .info("Dropping " + e.getKey() + " from cache because we don't care about it anymore.");
                        it.remove();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                CoreTweaks.LOGGER.error(
                    "There was an error reading the transformer cache. A new one will be created. The previous one has been saved as {} for inspection.",
                    DAT_ERRORED.getName());
                DAT.renameTo(DAT_ERRORED);
                e.printStackTrace();
            }
            long t1 = System.nanoTime();
            LOGGER
                .debug("Loaded lite transformer cache with {} entries in {}s", getSize(), (t1 - t0) / 1_000_000_000.0);
        } else {
            long t1 = System.nanoTime();
            LOGGER.debug("Created new lite transformer cache in {}s", (t1 - t0) / 1_000_000_000.0);
        }
    }

    private int getSize() {
        return transformerMap.values()
            .stream()
            .mapToInt(d -> d.transformationMap.size())
            .sum();
    }

    private void persistCache() {
        if (savedAndCleared) {
            return;
        }
        if (CachedTransformation.diffErrors > 0) {
            logger().warn(
                "{} entries have errored. Please report this if it keeps happening!",
                CachedTransformation.diffErrors);
        }
        try {
            final long l = System.currentTimeMillis();
            saveTransformerCache();
            saveProfilingResults();
            logger().info("Saved transformer cache in {}ms", (System.currentTimeMillis() - l));
        } catch (IOException e) {
            logger().error("Error saving lite transformer cache", e);
        }
    }

    private int clientTicks;

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            final Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null && mc.thePlayer != null) {
                clientTicks++;
                if (clientTicks > 40) {
                    FMLCommonHandler.instance()
                        .bus()
                        .unregister(this);
                    freeCacheDuringRuntime();
                }
            }
        }
    }

    @Override
    public void onServerStarted(FMLServerStartedEvent event) {
        freeCacheDuringRuntime();
    }

    private void freeCacheDuringRuntime() {
        synchronized (transformerMap) {
            if (savedAndCleared) {
                return;
            }
            persistCache();
            transformerMap.clear();
            myTransformers.clear();
            memoizedHash.remove();
            savedAndCleared = true;
        }
        LOGGER.info("Lite transformer cache saved and cleared from memory");
    }

    @Override
    public void onShutdown() {
        persistCache();
    }

    private void saveTransformerCache() throws IOException {
        if (!DAT.exists()) {
            DAT.getParentFile()
                .mkdirs();
            DAT.createNewFile();
        }
        logger().info("Saving transformer cache");
        trimCache((long) Config.liteTransformerCacheMaxSizeMB * 1024L * 1024L);
        try (Output output = new UnsafeOutput(new BufferedOutputStream(new FileOutputStream(DAT)))) {
            kryo.writeObject(output, MAGIC_0);
            kryo.writeObject(output, VERSION);
            kryo.writeObject(output, meta);
            kryo.writeObject(output, transformerMap);
        }
    }

    private void trimCache(long maxSize) {
        if (maxSize == -1) return;

        List<CachedTransformation> data = new ArrayList<>();

        for (TransformerData transData : transformerMap.values()) {
            data.addAll(transData.transformationMap.values());
        }

        data.sort(this::sortByAge);

        long usedSpace = 0;
        int cutoff = -1;
        for (int i = data.size() - 1; i >= 0; i--) {
            usedSpace += data.get(i)
                .getEstimatedSize();
            if (usedSpace > maxSize) {
                cutoff = data.get(i).lastAccessed;
                break;
            }
        }

        if (cutoff != -1) {
            final int cutoffCopy = cutoff;
            for (TransformerData transData : transformerMap.values()) {
                transData.transformationMap.entrySet()
                    .removeIf(e -> e.getValue().lastAccessed <= cutoffCopy);
            }
            transformerMap.entrySet()
                .removeIf(e -> e.getValue().transformationMap.isEmpty());
        }
    }

    private int sortByAge(CachedTransformation a, CachedTransformation b) {
        return Integer.compare(a.lastAccessed, b.lastAccessed);
    }

    private void saveProfilingResults() throws IOException {
        try (FileWriter fw = new FileWriter(TRANSFORMERCACHE_PROFILER_CSV)) {
            fw.write("transformer,runs,misses\n");
            for (CachedTransformerWrapper transformer : myTransformers) {
                final String name = transformer.transformerName;
                final int runs = transformer.runs;
                final int misses = transformer.misses;
                fw.write(name + "," + runs + "," + misses + "\n");
            }
        }
    }

    public byte[] getCached(String transName, String transformedName, byte[] basicClass) {
        TransformerData transData = transformerMap.get(transName);
        if (transData != null) {
            CachedTransformation trans = transData.transformationMap.get(transformedName);
            if (trans != null) {
                if (nullSafeLength(basicClass) == trans.preLength && calculateHash(basicClass) == trans.preHash) {
                    trans.lastAccessed = now();
                    if (trans.postHash == trans.preHash) {
                        return toNullableByteArray(basicClass);
                    }
                    byte[] result = trans.getNewClass(basicClass);
                    if (result == null) {
                        transData.transformationMap.remove(transformedName);
                        return null;
                    }
                    return result;
                }
            }
        }
        return null;
    }

    public static byte[] toNullableByteArray(byte[] array) {
        return array == null ? NULL_BYTE_ARRAY : array;
    }

    public static byte[] fromNullableByteArray(byte[] array) {
        return array == NULL_BYTE_ARRAY ? null : array;
    }

    private static int nullSafeLength(byte[] array) {
        return array == null ? -1 : array.length;
    }

    public void putCached(String transName, String transformedName, byte[] preTransformBytes, byte[] result) {
        synchronized (transformerMap) {
            TransformerData data = transformerMap.get(transName);
            if (data == null) {
                transformerMap.put(transName, data = new TransformerData(transName));
            }
            CachedTransformation cached = new CachedTransformation(
                transformedName,
                preTransformBytes,
                nullSafeLength(preTransformBytes),
                result);
            if (cached.isValid()) {
                data.transformationMap.put(transformedName, cached);
            }
        }
    }

    private static int calculateHash(byte[] data) {
        return calculateHash(data, nullSafeLength(data));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static int calculateHash(byte[] data, int len) {
        HashMemo memo = memoizedHash.get();
        if (data == memo.data) {
            return memo.value;
        }
        int hash = data == null ? -1
            : Hashing.adler32()
                .hashBytes(data, 0, len)
                .asInt();
        memo.data = data;
        memo.value = hash;
        return hash;
    }

    private static int now() {
        // TODO update the format in 6055
        return (int) (System.currentTimeMillis() / 1000 / 60);
    }

    @EqualsAndHashCode
    public static class CacheMeta {

        boolean enableDiffs = Config.useDiffsInTransformerCache;
    }

    public static class TransformerData {

        String transformerClassName;
        Map<String, CachedTransformation> transformationMap = new ConcurrentHashMap<>();

        public TransformerData(String transformerClassName) {
            this.transformerClassName = transformerClassName;
        }

        public TransformerData() {}

        public static class CachedTransformation {

            private static final byte[] INVALID_RESULT = new byte[] {};

            static int diffErrors = 0;

            String targetClassName;
            int preLength;
            int preHash;
            int postLength;
            int postHash;
            byte[] diff;
            int lastAccessed;

            public CachedTransformation() {}

            public boolean isValid() {
                return diff != INVALID_RESULT;
            }

            public CachedTransformation(String targetClassName, byte[] source, int sourceLen, byte[] target) {
                this.targetClassName = targetClassName;
                this.preHash = calculateHash(source, sourceLen);
                this.preLength = sourceLen;
                this.postLength = nullSafeLength(target);
                this.postHash = calculateHash(target);
                if (preHash != postHash) {
                    diff = generateDiff(source, sourceLen, target, targetClassName);
                }
                this.lastAccessed = now();
            }

            private static byte[] generateDiff(byte[] source, int sourceLen, byte[] target, String name) {
                if (source == null || !TransformerCache.instance.meta.enableDiffs) {
                    return target;
                }

                try {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    deltaThreadLocal.get()
                        .compute(
                            new FastByteBufferSeekableSource(ByteBuffer.wrap(source, 0, sourceLen)),
                            new ByteArrayInputStream(target),
                            new GDiffWriter(os));
                    return os.toByteArray();
                } catch (ClosedByInterruptException e) {
                    // nothome delta library uses interruptible channels which throw an error if the thread gets
                    // interrupted.
                    // No big deal, it's a race condition so it probably won't happen next time.
                    LOGGER.debug("Failed to generate diff for class {}, thread was interrupted.", name);
                } catch (Exception e) {
                    // Unknown exception. We want to know more about this, but it's not worth crashing over if it's a
                    // rare issue.
                    LOGGER.error(
                        "Failed to generate diff for class {}. Please report this if it keeps happening!",
                        name,
                        e);
                }
                diffErrors++;
                return INVALID_RESULT;
            }

            @SuppressWarnings("UnstableApiUsage")
            public byte[] getNewClass(byte[] source) {
                if (source == null || !TransformerCache.instance.meta.enableDiffs) {
                    return diff;
                }
                byte[] newClass = new byte[postLength];
                try {
                    InMemoryGDiffPatcher.patch(source, diff, newClass);
                } catch (Exception e) {
                    LOGGER.warn("Failed to apply cached diff for " + targetClassName + ", discarding entry");
                    return null;
                }
                int actualHash = Hashing.adler32()
                    .hashBytes(newClass)
                    .asInt();
                if (actualHash != postHash) {
                    LOGGER
                        .warn("Hash mismatch after applying cached diff for " + targetClassName + ", discarding entry");
                    return null;
                }
                return newClass;
            }

            public int getEstimatedSize() {
                return targetClassName.length() + 4 + 4 + 4 + (diff != null ? diff.length : 0) + 4;
            }
        }
    }
}
