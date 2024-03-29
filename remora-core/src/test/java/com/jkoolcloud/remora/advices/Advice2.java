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

package com.jkoolcloud.nisha.advices;

import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.RemoraConfig;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class Advice2 extends BaseTransformers {
	private static final TaggedLogger LOGGER = Logger.tag("INFO");
	private static final String ADVICE_NAME = "2";

	@RemoraConfig.Configurable
	public static String test = "TEST2";
	@RemoraConfig.Configurable
	public static boolean enabled2 = false;

	public Advice2() {
		LOGGER.info("Initialsed1");
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return null;
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return null;
	}

	@Override
	protected AgentBuilder.Listener getListener() {
		return null;
	}
}
