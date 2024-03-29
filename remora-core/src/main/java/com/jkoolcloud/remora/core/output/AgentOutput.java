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

import java.lang.reflect.Method;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.jkoolcloud.nisha.advices.BaseTransformers;
import com.jkoolcloud.nisha.core.EntryDefinition;

/**
 * Agent output for sending the {@link com.jkoolcloud.nisha.core.EntryDefinition} data.
 * {@link com.jkoolcloud.nisha.core.EntryDefinition} is a wrapper for twe entities Entry and Exit and it's a Output's
 * implementation to decide what to send. Entry is filled as method entered see
 * {@link com.jkoolcloud.nisha.advices.BaseTransformers#fillDefaultValuesBefore(EntryDefinition, ThreadLocal, Object, Method, BaseTransformers.InterceptionContext)}
 * and Exits is send on
 * {@link BaseTransformers#fillDefaultValuesAfter(EntryDefinition, long, Throwable, BaseTransformers.InterceptionContext)}.
 *
 * Output is responsible for setting the {@link EntryDefinition#sentEntry} when/if Entry is sent already. Otherwise if
 * the data is sent asynchronously, there might be that {@link EntryDefinition#isFinished()} returns true before Entry
 * is sent.
 *
 * Implementation of {@link AgentOutput} is loaded by {@link OutputManager}.
 *
 * @param <T>
 */

// TODO remove generic
public interface AgentOutput<T> {
	/**
	 * Init for output
	 *
	 * @throws OutputException
	 */

	void init() throws OutputException;

	/**
	 * Actual send. This is called on method enter and exit.
	 *
	 * @param entry
	 */
	void send(T entry);

	/**
	 * Output cleanup code
	 */
	void shutdown();

	/**
	 * Thread factory for the output. If there is no difference for implementation you can use
	 * {@code Executors.defaultThreadFactory()}.
	 *
	 * @return
	 */
	ThreadFactory getThreadFactory();

	class OutputException extends Exception {
		private static final long serialVersionUID = -6937653706786664128L;

		public OutputException(String message) {
			super(message);
		}
	}

    class OutputThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        OutputThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                    Thread.currentThread().getThreadGroup();
            namePrefix = "RemoraOutput-" +
                    poolNumber.getAndIncrement() +
                    "-thread-";
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                    namePrefix + threadNumber.getAndIncrement(),
                    0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

}
