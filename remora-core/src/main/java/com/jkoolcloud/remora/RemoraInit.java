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

package com.jkoolcloud.nisha;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ServiceLoader;

import org.tinylog.Logger;
import org.tinylog.TaggedLogger;
import org.tinylog.configuration.Configuration;

import com.jkoolcloud.nisha.adviceListeners.LoggingAdviceListener;
import com.jkoolcloud.nisha.advices.BaseTransformers;
import com.jkoolcloud.nisha.advices.RemoraAdvice;
import com.jkoolcloud.nisha.core.utils.RemoraLoggingProvider;

public class RemoraInit {

	// private static final Logger LOGGER = LoggerFactory.getLogger(RemoraInit.class.getName());

	public static void initializeAdvices(Instrumentation inst, ClassLoader classLoader) {

		TaggedLogger logger = Logger.tag(Remora.MAIN_REMORA_LOGGER);
		ServiceLoader<RemoraAdvice> advices = ServiceLoader.load(RemoraAdvice.class, classLoader);
		Iterator<RemoraAdvice> iterator = advices.iterator();
		ArrayList<RemoraAdvice> adviceList = new ArrayList<>(50);
		HashMap<RemoraAdvice, Exception> failedList = new HashMap<>(50);

		while (iterator.hasNext()) {
			RemoraAdvice nishaAdvice = iterator.next();
			String adviceName = nishaAdvice.getName();
			String key = "writer" + adviceName;
			Configuration.set(key, "rolling file");
			Configuration.set(key + ".file",
					System.getProperty(Remora.REMORA_PATH) + "/log/" + adviceName + "{count}.log");
			Configuration.set(key + ".format", "{date} [{thread}] {class}.{method}()\n\t{level}: {message}");
			Configuration.set(key + ".tag", adviceName);
			Configuration.set(key + ".level", "debug");
			Configuration.set(key + ".policies", "size: 4mb");

			try {
				RemoraConfig.configure(nishaAdvice);
				adviceList.add(nishaAdvice);
			} catch (Exception e) {
				failedList.put(nishaAdvice, e);
			}

			logger.info("\t Found module: " + nishaAdvice);

		}

		try {
			RemoraConfig.configure(AdviceRegistry.INSTANCE);
		} catch (IllegalAccessException e) {
			logger.info("AdviceRegistry Config failed");
		}
		AdviceRegistry.INSTANCE.report(adviceList);
		// need to configure logger first
		adviceList.forEach(advice -> {
			advice.install(inst);
			logger.info("Installed {}, {}", advice.getName(),
					advice.getClass().getPackage().getImplementationVersion());
		});

		BaseTransformers.registerListener(LoggingAdviceListener.class);

		failedList.forEach((advice, exc) -> {
			logger.info("Failed configuring: ", advice.getName());
			logger.info(exc);
		});
		logger.info("Loading advices finished. Loaded {} advices, failed {}", adviceList.size(), failedList.size());
		RemoraLoggingProvider.startLogging();

	}

}
