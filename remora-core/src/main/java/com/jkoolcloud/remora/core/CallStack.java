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

package com.jkoolcloud.nisha.core;

import java.util.Arrays;
import java.util.Stack;

import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.advices.BaseTransformers;

public class CallStack extends Stack<EntryDefinition> {
	private static final long serialVersionUID = 1273371157804943471L;

	private final TaggedLogger logger;
	protected final BaseTransformers.InterceptionContext ctx;

	private String application = null;
	private String server = null;
	private final String stackCorrelator;
	private final int limit;

	public CallStack(BaseTransformers.InterceptionContext ctx, int limit) {
		logger = ctx.interceptorInstance.getLogger();
		this.ctx = ctx;
		this.limit = limit;
		stackCorrelator = JUGFactoryImpl.newUUID();
	}

	@Override
	public EntryDefinition push(EntryDefinition item) {
		if (size() >= limit) {
			if (logger != null) {
				logger.error("Stack limit reached: {}, {} : {}", ctx.interceptorInstance, (size() + 1),
						item.getAdviceClass(), item.getId());
			}
			return null;

		}

		if (logger != null) {
			logger.debug("{}---> Stack push: {}, {} : {}", ctx.interceptorInstance, addPadding(), (size() + 1),
					item.getAdviceClass(), item.getId());
		}
		item.setApplication(application);
		item.setServer(server);
		item.setCorrelator(stackCorrelator);

		return super.push(item);
	}

	private String addPadding() {
		char[] data = new char[size() * 3];
		Arrays.fill(data, '-');
		return String.valueOf(data);

	}

	@Override
	public synchronized EntryDefinition pop() {
		EntryDefinition pop = super.pop();
		if (logger != null) {
			logger.debug("<---{} Stack pop: {} : {} ", ctx.interceptorInstance, addPadding(), size(), pop.getId());
		}

		return pop;
	}

	public String getApplication() {
		return application;
	}

	public void setApplication(String application) {
		this.application = application;
		for (EntryDefinition entryDefinition : this) {
			entryDefinition.setApplication(application);
		}
	}

	public String getServer() {
		return server;
	}

	public void setServer(String server) {
		this.server = server;
		for (EntryDefinition entryDefinition : this) {
			entryDefinition.setServer(server);
		}
	}

}
