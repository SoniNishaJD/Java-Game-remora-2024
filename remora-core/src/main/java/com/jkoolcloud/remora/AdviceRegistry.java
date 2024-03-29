
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import com.jkoolcloud.nisha.advices.BaseTransformers;
import com.jkoolcloud.nisha.advices.RemoraAdvice;

public enum AdviceRegistry {
	INSTANCE;

	private List<RemoraAdvice> adviceList = new CopyOnWriteArrayList<RemoraAdvice>();
	private Map<String, RemoraAdvice> adviceMap = new ConcurrentHashMap<>(50);

	public void report(List<RemoraAdvice> adviceList) {

		this.adviceList = adviceList;
		adviceMap = adviceList.stream()
				.collect(Collectors.toMap(entry -> entry.getClass().getSimpleName(), entry -> entry));
	}

	public List<RemoraAdvice> getRegisteredAdvices() {
		return adviceList;
	}

	public RemoraAdvice getAdviceByName(String name) throws ClassNotFoundException {
		if (!adviceMap.containsKey(name)) {
			throw new ClassNotFoundException();
		}
		return adviceMap.get(name);
	}

	public BaseTransformers getBaseTransformerByName(String name) throws ClassNotFoundException {

		RemoraAdvice adviceByName = getAdviceByName(name);
		if (adviceByName instanceof BaseTransformers) {
			return (BaseTransformers) adviceByName;
		} else {
			throw new ClassNotFoundException();
		}
	}

}
