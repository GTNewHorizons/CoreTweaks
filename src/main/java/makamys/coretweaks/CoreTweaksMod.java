package makamys.coretweaks;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppedEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import makamys.coretweaks.bugfix.DoubleEatFixer;
import makamys.coretweaks.command.CoreTweaksCommand;
import makamys.coretweaks.optimization.ClientChunkMapTicker;
import makamys.coretweaks.optimization.JarDiscovererCache;
import makamys.coretweaks.optimization.transformercache.lite.TransformerCache;
import makamys.coretweaks.tweak.LoadLastWorldButton;
import makamys.mclib.core.MCLib;

@Mod(modid = CoreTweaks.MODID, version = CoreTweaks.VERSION)
public class CoreTweaksMod {

    private static final List<IModEventListener> listeners = new ArrayList<>();

    @EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        MCLib.init();

        Config.reload();

        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(() -> listeners.forEach(IModEventListener::onShutdown), "CoreTweaks shutdown thread"));

        if (Config.transformerCache.isActive() && Config.transformerCacheMode == Config.TransformerCache.LITE) {
            registerListener(TransformerCache.instance);
        }
        if (Config.jarDiscovererCache.isActive()) {
            registerListener(JarDiscovererCache.instance);
        }
        if (Config.mainMenuContinueButton.isActive()) {
            registerListener(LoadLastWorldButton.instance = new LoadLastWorldButton());
        }
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        listeners.forEach(l -> l.onPreInit(event));
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        if (Config.clientChunkMap.isActive()) {
            FMLCommonHandler.instance()
                .bus()
                .register(new ClientChunkMapTicker());
        }

        if (Config.coreTweaksCommand.isActive()) {
            ClientCommandHandler.instance.registerCommand(new CoreTweaksCommand());
        }
        if (CoreTweaks.textureLoader != null) {
            FMLCommonHandler.instance()
                .bus()
                .register(CoreTweaks.textureLoader);
        }
        if (Config.fixDoubleEat.isActive()) {
            FMLCommonHandler.instance()
                .bus()
                .register(new DoubleEatFixer());
        }

        listeners.forEach(l -> l.onInit(event));
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        listeners.forEach(l -> l.onPostInit(event));
    }

    @EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        listeners.forEach(l -> l.onLoadComplete(event));
    }

    @EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
        Config.reload();
        listeners.forEach(l -> l.onServerAboutToStart(event));
    }

    @EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        listeners.forEach(l -> l.onServerStarting(event));
    }

    @EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        listeners.forEach(l -> l.onServerStarted(event));
    }

    @EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        listeners.forEach(l -> l.onServerStopping(event));
    }

    @EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        listeners.forEach(l -> l.onServerStopped(event));
    }

    public void registerListener(IModEventListener listener) {
        listeners.add(listener);
    }
}
