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

package com.jkoolcloud.nisha.takes;

import java.util.WeakHashMap;

import org.junit.Test;
import org.takes.facets.fork.RqRegex;
import org.takes.rq.RqFake;
import org.takes.rs.RsPrint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jkoolcloud.nisha.advices.InputStreamReadAdvice;
import com.jkoolcloud.nisha.advices.OutputStreamWriteAdvice;
import com.jkoolcloud.nisha.advices.StreamStats;
import com.jkoolcloud.nisha.advices.StreamsManager;
import com.jkoolcloud.nisha.core.EntryDefinition;

public class TKStreamsTest {

	@Test
	public void act() throws Exception {
		StreamStats value = new StreamStats();
		value.starttime = System.currentTimeMillis();
		Thread.sleep(100);
		value.accessTimestamp = System.currentTimeMillis();

		StreamsManager.INSTANCE.setAvailableInputStreamsEntries(new WeakHashMap<EntryDefinition, StreamStats>() {
			{
				EntryDefinition ed = new EntryDefinition(InputStreamReadAdvice.class, false);
				ed.addProperty("toString", "test".chars().toString());
				ed.addProperty("toString_1", "tets2".chars().toString());
				put(ed, value);

				put(ed, value);
			}
		});

		EntryDefinition entryDefinition = new EntryDefinition(OutputStreamWriteAdvice.class, false);
		entryDefinition.setClazz("java.io");

		EntryDefinition entryDefinition2 = new EntryDefinition(OutputStreamWriteAdvice.class, false);
		entryDefinition2.setClazz("java.io2");

		StreamsManager.INSTANCE.setAvailableOutputStreamsEntries(new WeakHashMap<EntryDefinition, StreamStats>() {
			{
				put(entryDefinition, value);
				put(entryDefinition2, value);
			}
		});
		String x = new RsPrint(new TKStreams().act(new RqFake())).printBody();
		new ObjectMapper().readTree(x);
		System.out.println(x);

		String y = new RsPrint(
				new TKStreamDetails().act(new RqRegex.Fake(new TKStreamDetails().getEnpointPath(), "/streams/null")))
						.printBody();
		System.out.println(y);
		new ObjectMapper().readTree(y);

	}
}