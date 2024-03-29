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

import org.junit.Test;

//Enable power mockito if any of classes failing to mock
//@RunWith(PowerMockRunner.class)
//@PrepareForTest({WebApp.class})
//@SuppressStaticInitializationFor({""})
public class KafkaConsumerAdviceTest {

	@Test
	public void testKafkaConsummerInterceptor() throws NoSuchMethodException {
		// PowerMockito.mockStatic(<<classToIntercept>>.class);
		// WebApp webApp=mock(<<classToIntercept>>.class);

		// @SuppressWarnings("unused")
		// EntryDefinition handleRequestEntry = new EntryDefinition(KafkaConsumerAdvice.class, true);

		// Method method=Whitebox.getMethod(Object.class,"<<interceptingMethod>>");

		// test before method
		// KafkaConsumerAdvice.before();

		// test after method
		// KafkaConsumerAdvice.after();
	}
}
