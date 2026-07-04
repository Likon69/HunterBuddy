package com.hunterbuddy.modules.regear.util;

import com.hunterbuddy.HunterBuddyAddon;

/**
 * Reference parity: mlep.util.LogUtil. Logs to HunterBuddyAddon's logger with
 * the "[Mlep]" prefix (matches mlep's exact format for compatibility).
 */
public class LogUtil {
    public static void info(String msg) {
        HunterBuddyAddon.LOG.info("{} {}", MsgUtil.getRawPrefix(), msg);
    }

    public static void info(String msg, String module) {
        HunterBuddyAddon.LOG.info("{}{} {}", new Object[]{MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg});
    }

    public static void warn(String msg) {
        HunterBuddyAddon.LOG.warn("{} {}", MsgUtil.getRawPrefix(), msg);
    }

    public static void warn(String msg, String module) {
        HunterBuddyAddon.LOG.warn("{}{} {}", new Object[]{MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg});
    }

    public static void error(String msg) {
        HunterBuddyAddon.LOG.error("{} {}", MsgUtil.getRawPrefix(), msg);
    }

    public static void error(String msg, String module) {
        HunterBuddyAddon.LOG.error("{}{} {}", new Object[]{MsgUtil.getRawPrefix(), MsgUtil.getRawPrefix(module), msg});
    }
}
