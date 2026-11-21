package com.swapnil.cache.internal;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runnable that periodically invokes a sweep action to remove expired
 * entries from a cache.
 *
 * <p>The task wraps the sweep in a try-catch so that a single failed sweep
 * does not kill the owning {@link java.util.concurrent.ScheduledExecutorService}.
 * Failures are logged at {@link Level#WARNING}.</p>
 *
 * <p>Intended to be scheduled via
 * {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate}.</p>
 */
final class CleanUpTask implements Runnable {
    private static final Logger LOG = Logger
                                .getLogger(CleanUpTask.class.getName());
    private final Runnable sweepAction;
    
    CleanUpTask(Runnable sweepAction){
        this.sweepAction = Objects.requireNonNull(sweepAction,"sweepAction");
    }

    @Override 
    public void run(){
        try {
            sweepAction.run();
        } catch (Throwable t) {
            LOG.log(Level.WARNING,"Background cleanup sweep failed",t);
        }
    }


}