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

package com.jkoolcloud.nisha.filters;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClassNameFilterTest {

	public static class TestClass {
		public void main() {

		}
	}

	@Test
	public void maches() throws NoSuchMethodException {
		ClassNameFilter classNameFilter = new ClassNameFilter();
		classNameFilter.regex = true;
		classNameFilter.classNames.add("com\\.jkoolcloud.*");
		assertTrue(classNameFilter.matches(new TestClass(), TestClass.class.getMethod("main")));
	}
}