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

import static net.bytebuddy.matcher.ElementMatchers.hasGenericSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.io.InputStream;
import java.lang.reflect.Method;

import com.jkoolcloud.nisha.RemoraConfig;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class InputStreamReadAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "InputStreamReadAdvice";
	public static final String[] INTERCEPTING_CLASS = { "java.io.InputStream" };
	public static final String INTERCEPTING_METHOD = "read";

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */

	private static ElementMatcher<? super MethodDescription> methodMatcher() {
		return named(INTERCEPTING_METHOD);
	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return hasGenericSuperType(named(INTERCEPTING_CLASS[0]));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(InputStreamReadAdvice.class.getClassLoader()).include(RemoraConfig.INSTANCE.classLoader)//
			.advice(methodMatcher(), InputStreamReadAdvice.class.getName());

	/**
	 * Advices before method is called before instrumented method code
	 *
	 * @param thiz
	 *            reference to method object
	 * @param arguments
	 *            arguments provided for method
	 * @param method
	 *            instrumented method description
	 *
	 */

	@Advice.OnMethodEnter
	public static void before(@Advice.This InputStream thiz, //
			@Advice.AllArguments Object[] arguments, //
			@Advice.Origin Method method//
	) {
		InterceptionContext ctx = null;
		try {
			ctx = prepareIntercept(InputStreamReadAdvice.class, thiz, method, arguments);
			if (!ctx.intercept) {
				return;
			}
			StreamStats streamStats = StreamsManager.INSTANCE.get(thiz, ctx, method);
			if (streamStats == null) {
				throw new IllegalStateException(
						"Stream stats is null for " + method.getDeclaringClass() + "." + method.getName());
			}
			if (arguments == null || arguments.length == 0) { // for a read() case
				streamStats.advanceCount();
			} else if (arguments instanceof Object[] && arguments.length == 3) {// for a read(byte b[], int off, int
																				// len)
				streamStats.advanceCount((int) arguments[2]);
			} else if (arguments instanceof Object[] && arguments.length == 1) { // for a read(byte b[])
				streamStats.advanceCount(((byte[]) arguments[0]).length);
			}
		} catch (Throwable t) {
			handleAdviceException(t, ctx);
		}
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}

}
