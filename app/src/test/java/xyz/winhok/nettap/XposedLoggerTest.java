package xyz.winhok.nettap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import de.robv.android.xposed.XposedBridge;

import org.junit.Before;
import org.junit.Test;

public final class XposedLoggerTest {
    @Before
    public void reset() {
        XposedBridge.resetForTesting();
    }

    @Test
    public void formatPrefixedAddsPrefixToEveryLine() {
        String result = XposedLogger.formatPrefixed("NetTap", "one\ntwo");

        assertEquals("NetTap: one\nNetTap: two", result);
    }

    @Test
    public void logWritesFormattedMessageToXposedBridge() {
        new XposedLogger("NetTap").log("hello %s", "world");

        assertTrue(XposedBridge.logs().contains("NetTap: hello world"));
    }
}
