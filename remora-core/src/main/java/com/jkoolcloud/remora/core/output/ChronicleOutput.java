/*
 * Copyright 2019-2020 NASTEL TECHNOLOGIES, INC.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.jkoolcloud.nisha.core.output;

import com.jkoolcloud.nisha.Remora;
import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.EntryDefinition;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycles;
import net.openhft.chronicle.queue.impl.RollingChronicleQueue;
import net.openhft.chronicle.queue.impl.StoreFileListener;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;

public class ChronicleOutput implements AgentOutput<EntryDefinition> {

    public static final String DISABLE_PROXY_CODEGEN = "disableProxyCodegen";
    TaggedLogger logger = Logger.tag(Remora.MAIN_REMORA_LOGGER);
    @RemoraConfig.Configurable
    String queuePath = System.getProperty(Remora.REMORA_PATH) + "/queue";
    @RemoraConfig.Configurable
    RollCycles rollCycle = RollCycles.valueOf("HOURLY");
    @RemoraConfig.Configurable
    Long timeout = 5000L;
    @RemoraConfig.Configurable
    Integer keepQueueRolls = 2;
    @RemoraConfig.Configurable
    Integer errorReportingSchedule = 2;
    Deque<File> unusedQueues;
    private ChronicleQueue queue;
    private ScheduledQueueErrorReporter scheduledQueueErrorReporter;

    @Override
    public void init() {
        if (System.getProperty(DISABLE_PROXY_CODEGEN) == null) {
            // Disable proxy codegen by default
            System.setProperty(DISABLE_PROXY_CODEGEN, "true");
        }

        File queueDir = Paths.get(queuePath).toFile();
        unusedQueues = new LinkedBlockingDeque<>(keepQueueRolls);
        File[] cq4s = queueDir.listFiles(new CQ4FileFilter());
        if (cq4s != null) {
            unusedQueues.addAll(Arrays.asList(cq4s));
        }

        logger.info("Output {} Writing to  {}", this, queueDir.getAbsolutePath());
        scheduledQueueErrorReporter = new ScheduledQueueErrorReporter(logger, errorReportingSchedule);

        queue = ChronicleQueue.singleBuilder(queueDir.getPath()).rollCycle(rollCycle).timeoutMS(timeout)
                .storeFileListener(new StoreFileListener() {

                    @Override
                    public void onReleased(int cycle, File file) {
                        while (!unusedQueues.offer(file)) {
                            File fileToDelete = unusedQueues.removeFirst();
                            fileToDelete.delete();
                            queue.refreshDirectoryListing();
                            logger.debug("Deleting unused queue file {} ", queueDir.getAbsolutePath());
                        }

                    }
                })
                .doubleBuffer(true)
                .build();

        if (queue != null) {
            logger.info("Queue initialized {}", this);
            if (queue instanceof RollingChronicleQueue) {
                RollingChronicleQueue queue = (RollingChronicleQueue) this.queue;
                queue.storeForCycle(queue.cycle(), 0, false, null);
            }
        } else {
            logger.error("Queue initializaton failed file={}", queueDir);
        }

    }

    @Override
    public void send(EntryDefinition entry) {
        ExcerptAppender appender = null;
        try {
            Thread thread = Thread.currentThread();
            if (thread instanceof ChronicleOutput.ChronicleAppenderThread) {
                appender = ((ChronicleOutput.ChronicleAppenderThread) thread).getAppender();
            } else {
                appender = queue.acquireAppender();
                logger.warn("Current thread has no appender. Getting new, hope it's running on test");
                markSendError(appender, new Exception("Thread is not the instance of ChronicleAppenderThread"));
            }

            if (entry.isFinished()) {
                if (entry.sentEntry) {
                    entry.exit.write(appender);
                } else {
                    entry.entry.write(appender);
                    entry.sentEntry = true;
                    entry.exit.write(appender);
                }
            } else {
                entry.entry.write(appender);
                entry.sentEntry = true;
            }

            // }
        } catch (Exception e) {
            markSendError(appender, e);
        }

    }

    private void markSendError(ExcerptAppender appender, Exception e) {
        try {
            if (appender != null) {
                ScheduledQueueErrorReporter.lastIndexAppender = appender.lastIndexAppended();
            }
        } catch (IllegalStateException e2) {
            // nothing appended/ run on test
        }
        ScheduledQueueErrorReporter.chronicleQueueFailCount.incrementAndGet();
        ScheduledQueueErrorReporter.lastException = e;
    }

    @Override
    public void shutdown() {
        logger.info("Shutting down chronicle queue:" + this);
        if (scheduledQueueErrorReporter != null) {
            scheduledQueueErrorReporter.shutdown();
        }
        if (queue != null) {
            queue.close();
        }
    }

    @Override
    public ThreadFactory getThreadFactory() {
        return new AppenderThreadFactory();
    }

    private static class CQ4FileFilter implements FilenameFilter {
        @Override
        public boolean accept(File path, String name) {
            String nfn = name.toLowerCase();
            return nfn.endsWith(".cq4");
        }
    }

    public static class ChronicleAppenderThread extends Thread {
        private final ExcerptAppender threadAppender;

        public ChronicleAppenderThread(Runnable r, ExcerptAppender appender) {
            super(r);

            threadAppender = appender;
        }

        public ExcerptAppender getAppender() {
            return threadAppender;
        }
    }

    private class AppenderThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(@NotNull Runnable r) {
            logger.info("Creating new thread for ouput queue {}", queue);

            ExcerptAppender threadAppender = queue.acquireAppender();
            if (threadAppender != null) {
                logger.info("Appender initialized {}", threadAppender);
            } else {
                logger.error("Appender failed {}, queue", threadAppender);
            }

            ChronicleAppenderThread chronicleAppenderThread = new ChronicleAppenderThread(r, threadAppender);
            chronicleAppenderThread.setName("Chronicle appender thread: " + System.currentTimeMillis());
            chronicleAppenderThread.setDaemon(true);
            return chronicleAppenderThread;
        }
    }
}
