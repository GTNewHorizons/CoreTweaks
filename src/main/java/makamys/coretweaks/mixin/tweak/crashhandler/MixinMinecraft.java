package makamys.coretweaks.mixin.tweak.crashhandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.LWJGLException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import makamys.coretweaks.CoreTweaks;
import makamys.coretweaks.tweak.crashhandler.CrashHandler;
import makamys.coretweaks.tweak.crashhandler.GuiFatalErrorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMemoryErrorScreen;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.crash.CrashReport;
import net.minecraft.util.MinecraftError;
import net.minecraft.util.ReportedException;

@Mixin(Minecraft.class)
abstract class MixinMinecraft {
    
    @Shadow
    volatile boolean running = true;
    
    @Shadow
    private boolean hasCrashed;
    
    @Shadow
    private CrashReport crashReporter;
    
    @Shadow
    private static Logger logger = LogManager.getLogger();
    
    @Shadow
    abstract void startGame() throws LWJGLException;
    
    @Shadow
    public abstract CrashReport addGraphicsAndWorldToCrashReport(CrashReport p_71396_1_);
    
    @Shadow
    public abstract void displayCrashReport(CrashReport p_71377_1_);
    
    @Shadow
    abstract void runGameLoop();
    
    @Shadow
    public abstract void freeMemory();
    
    @Shadow
    public abstract void displayGuiScreen(GuiScreen p_147108_1_);
    
    @Shadow
    public abstract void shutdownMinecraftApplet();
    
    /**
     * @author makamys 
     * @reason Change game loop to recover from crashes.
     * */
    @Overwrite
    public void run() {
        this.running = true;
        CrashReport crashreport;

        try {
            this.startGame();
        } catch (Throwable throwable) {
            crashreport = CrashReport.makeCrashReport(throwable, "Initializing game");
            crashreport.makeCategory("Initialization");
            this.displayCrashReport(this.addGraphicsAndWorldToCrashReport(crashreport));
            return;
        }

        while (true) {
            try {
                while (this.running) {
                    if (!this.hasCrashed || this.crashReporter == null) {
                        try {
                            runGameLoop();
                            
                            if (hasCrashed) {
                                hasCrashed = false;
                                throw new RuntimeException("Exception on server thread");
                            }
                        } catch (Throwable t) {
                            CoreTweaks.LOGGER.error("#@!@# Game crashed! CoreTweaks will attempt to keep the game running.");
                            t.printStackTrace();
                            
                            if(!(t instanceof OutOfMemoryError)) {
                                // Create crash report, mirroring logic in Minecraft#run
                                // Note: addGraphicsAndWorldToCrashReport has to be called inside the same method as runGameLoop,
                                // to preserve the assumption it makes about the call stack
                                if(crashReporter != null) {
                                    CrashHandler.createCrashReport(crashReporter);
                                } else if(t instanceof MinecraftError) {
                                    // do nothing
                                } else if(t instanceof ReportedException) {
                                    ReportedException re = (ReportedException)t;
                                    Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(re.getCrashReport());
                                    CrashHandler.createCrashReport(re.getCrashReport());
                                } else {
                                    CrashReport cr = Minecraft.getMinecraft().addGraphicsAndWorldToCrashReport(new CrashReport("Unexpected error", t));
                                    CrashHandler.createCrashReport(cr);
                                }
                            }
                            
                            GuiScreen screen = ((Minecraft)(Object)this).currentScreen;
                            if(!(screen instanceof GuiMemoryErrorScreen || screen instanceof GuiFatalErrorScreen)) {
                                CrashHandler.resetState();
                                
                                this.freeMemory();
                                this.displayGuiScreen(t instanceof OutOfMemoryError ? new GuiMemoryErrorScreen() : new GuiFatalErrorScreen(t));
                                System.gc();
                            } else {
                                CoreTweaks.LOGGER.warn("Got a crash on a crash error screen, quitting game to avoid infinite loop.");
                                this.running = false;
                            }
                        }

                        continue;
                    }

                    this.displayCrashReport(this.crashReporter);
                    return;
                }
            } catch (MinecraftError minecrafterror) {
                ;
            } catch (ReportedException reportedexception) {
                this.addGraphicsAndWorldToCrashReport(reportedexception.getCrashReport());
                this.freeMemory();
                logger.fatal("Reported exception thrown!", reportedexception);
                this.displayCrashReport(reportedexception.getCrashReport());
            } catch (Throwable throwable1) {
                crashreport = this.addGraphicsAndWorldToCrashReport(new CrashReport("Unexpected error", throwable1));
                this.freeMemory();
                logger.fatal("Unreported exception thrown!", throwable1);
                this.displayCrashReport(crashreport);
            } finally {
                this.shutdownMinecraftApplet();
            }

            return;
        }
    }

}
