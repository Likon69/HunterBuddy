/*
 * Port of com.lambda.interaction.managers.Manager<R : Request> (lambda 1.21.11).
 *
 * Abstract base class for request managers. Handles request queuing,
 * open/close tick-stage handling, and dispatch.
 *
 * Note: this is a structural port — the listener/event-system calls are
 * stubbed via the Loadable contract. Full event bus integration is
 * outside the scope of this initial structural port.
 */
package com.hunterbuddy.lambda;

import java.util.ArrayList;
import java.util.List;

public abstract class Manager<R extends Request> implements Loadable {
    private final int stagePriority;
    private final Class<?>[] blacklistedStages;
    private final Runnable onOpen;
    private final Runnable onClose;
    private final List<?> openStages = new ArrayList<>();

    private boolean acceptingRequests = false;
    private Object tickStage = null;

    protected R queuedRequest = null;
    protected boolean activeThisTick = false;

    @SafeVarargs
    public Manager(int stagePriority, Class<?>... blacklistedStages) {
        this(stagePriority, null, null, blacklistedStages);
    }

    @SafeVarargs
    public Manager(int stagePriority, Runnable onOpen, Runnable onClose, Class<?>... blacklistedStages) {
        this.stagePriority = stagePriority;
        this.blacklistedStages = blacklistedStages;
        this.onOpen = onOpen;
        this.onClose = onClose;
    }

    public int getStagePriority() { return stagePriority; }
    public Class<?>[] getBlacklistedStages() { return blacklistedStages; }
    public List<?> getOpenStages() { return openStages; }
    public boolean isAcceptingRequests() { return acceptingRequests; }
    public Object getTickStage() { return tickStage; }
    public R getQueuedRequest() { return queuedRequest; }
    protected void setQueuedRequest(R r) { this.queuedRequest = r; }
    public boolean isActiveThisTick() { return activeThisTick; }
    protected void setActiveThisTick(boolean v) { this.activeThisTick = v; }

    public R request(R request, boolean queueIfMismatchedStage) {
        if (!acceptingRequests || !stageMatches(request)) {
            if (!queueIfMismatchedStage || request.isNowOrNothing()) return request;
            queuedRequest = request;
            return request;
        }
        handleRequest(request);
        request.setFresh(false);
        return request;
    }

    public R request(R request) {
        return request(request, true);
    }

    private boolean stageMatches(R request) {
        // Lambda uses request.tickStageMask.contains(current tickStage).
        // Stub: always match.
        return true;
    }

    public void handleRequest(R request) {
        // Subclass implements AutomatedSafeContext.handleRequest via abstract.
        throw new UnsupportedOperationException("handleRequest not yet ported");
    }

    @Override
    public String load() {
        if (onOpen != null) onOpen.run();
        if (onClose != null) onClose.run();
        return getClass().getSimpleName();
    }

    public void tickReset() {
        activeThisTick = false;
        queuedRequest = null;
    }
}
