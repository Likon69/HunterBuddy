package com.hunterbuddy.behavior.bot;

import com.hunterbuddy.behavior.mc.WorldIdentity;
import com.hunterbuddy.behavior.resource.Resource;
import java.util.Set;

public interface LiveWorldBot {
    WorldIdentity identity();
    Set<Resource> resources();
}
