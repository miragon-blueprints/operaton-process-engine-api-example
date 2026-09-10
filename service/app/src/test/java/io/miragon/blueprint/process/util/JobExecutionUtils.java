package io.miragon.blueprint.process.util;

import java.util.List;
import org.operaton.bpm.engine.ProcessEngine;
import org.operaton.bpm.engine.runtime.Job;

public final class JobExecutionUtils {

    private JobExecutionUtils() {
    }

    /**
     * Drives the process to its next wait state. Necessary because the job executor is disabled in tests
     * for determinism.
     *
     * <p>Unlike the classic-delegate blueprint, this process-engine-api variant runs its service tasks as
     * external tasks consumed by the real, asynchronously polling {@code @ProcessEngineWorker} beans. So this
     * helper both executes parked async-continuation ({@code camunda:asyncAfter}) message jobs synchronously
     * <em>and</em> gives the polling workers time to pick up and complete any open external tasks — settling
     * once no message job and no external task remains for a few consecutive checks.
     */
    public static void continueToNextWaitState(ProcessEngine engine) {
        continueToNextWaitState(engine, 15_000);
    }

    public static void continueToNextWaitState(ProcessEngine engine, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        int idleIterations = 0;
        while (System.currentTimeMillis() < deadline) {
            List<Job> jobs = engine.getManagementService().createJobQuery()
                .active()
                .messages()
                .listPage(0, 1);
            if (!jobs.isEmpty()) {
                engine.getManagementService().executeJob(jobs.get(0).getId());
                idleIterations = 0;
                continue;
            }
            if (engine.getExternalTaskService().createExternalTaskQuery().count() == 0L) {
                if (++idleIterations >= 3) {
                    return;
                }
            } else {
                idleIterations = 0;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while driving the process to its next wait state", e);
            }
        }
    }
}
