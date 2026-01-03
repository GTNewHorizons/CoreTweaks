package makamys.coretweaks.bugfix;

import static makamys.coretweaks.CoreTweaks.LOGGER;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;

import cpw.mods.fml.relauncher.FMLRelaunchLog;

@SuppressWarnings("static-access")
public class SafeFMLLog {

    public static void log(FMLRelaunchLog coreLog, String targetLog, Level level, Throwable ex, String format,
        Object[] data) {
        try {
            coreLog.log(targetLog, level, ex, format, data);
        } catch (Throwable t) {
            LOGGER.trace(
                "Prevented crash while trying to log stack trace (" + t + "). Trying again using printStackTrace.");
            coreLog.log(targetLog, level, ex, format + "\n" + ExceptionUtils.getStackTrace(ex), data);
        }
    }

    public static void log(FMLRelaunchLog coreLog, Level level, Throwable ex, String format, Object[] data) {
        try {
            coreLog.log(level, ex, format, data);
        } catch (Throwable t) {
            LOGGER.trace(
                "Prevented crash while trying to log stack trace (" + t + "). Trying again using printStackTrace.");
            coreLog.log(level, format + "\n" + ExceptionUtils.getStackTrace(ex), data);
        }
    }

}
