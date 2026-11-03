package com.hunterbuddy.modules.regear.util;

interface TakeoffControl {
    void debug(String message);

    void toDeploying();

    void enterBoosting();

    void retry(String reason);

    void succeed(double speedBps);
}
