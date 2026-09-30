package com.lab.labtimesheet.feature.notification.service;

import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Bounded background worker pool dispatching asynchronous notification email deliveries.
 *
 * <p>Protects {@code NOT-006}, {@code D49}, and {@code AC-NOT-008}. Configures exactly two
 * worker threads and a bounded 100-item task queue. When the queue rejects a task, a WARN
 * entry naming the notification identifier is logged without throwing, preserving caller
 * transaction completion while leaving the pending notification in the database for later
 * retry sweeps.
 */
@Component
@Slf4j
public class NotificationDeliveryDispatcher {

    private static final int WORKER_THREADS = 2;
    private static final int QUEUE_CAPACITY = 100;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final ThreadPoolExecutor executor;

    public NotificationDeliveryDispatcher() {
        this.executor = new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "notification-delivery-worker");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, exec) -> {
                    if (runnable instanceof DeliveryTask task) {
                        log.warn("Notification delivery queue full; task rejected for notification id {}",
                                task.notificationId());
                    } else {
                        log.warn("Notification delivery queue full; task rejected");
                    }
                });
    }

    /**
     * Submits one notification delivery task to the bounded worker pool.
     *
     * @param notificationId notification row identifier
     * @param deliveryAction delivery logic to execute on the background thread
     */
    public void dispatch(long notificationId, LongConsumer deliveryAction) {
        dispatch(java.util.List.of(notificationId), deliveryAction);
    }

    /**
     * Submits multiple notification delivery tasks as an ordered unit to the bounded worker pool.
     *
     * @param notificationIds collection of notification row identifiers
     * @param deliveryAction delivery logic to execute on the background thread
     */
    public void dispatch(Collection<Long> notificationIds, LongConsumer deliveryAction) {
        if (notificationIds == null || notificationIds.isEmpty()) {
            return;
        }
        long firstId = notificationIds.iterator().next();
        try {
            executor.execute(new DeliveryTask(firstId, () -> {
                for (Long id : notificationIds) {
                    if (id != null) {
                        try {
                            deliveryAction.accept(id);
                        } catch (RuntimeException ex) {
                            log.warn("Asynchronous notification delivery failed for id {}", id, ex);
                        }
                    }
                }
            }));
        } catch (RejectedExecutionException ex) {
            log.warn("Notification delivery dispatcher rejected notification ids {}", notificationIds, ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private record DeliveryTask(long notificationId, Runnable action) implements Runnable {
        @Override
        public void run() {
            action.run();
        }
    }
}
