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

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

import java.lang.reflect.Method;

import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.CallStack;
import com.jkoolcloud.nisha.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class SimpleTestConstructor extends BaseTransformers {

	public static final String ADVICE_NAME = "SimpleTestConstuctor";
	public static final String[] INTERCEPTING_CLASS = { "lt.slabs.com.jkoolcloud.nisha.JustATest2" };
	public static final String INTERCEPTING_METHOD = "constructor";

	public static TaggedLogger logger;

	public static ThreadLocal<CallStack> stackThreadLocal = new ThreadLocal<>();

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(SimpleTestConstructor.class.getClassLoader())
			.include(RemoraConfig.INSTANCE.classLoader)
			.advice(methodMatcher(), "com.jkoolcloud.nisha.advices.SimpleTestConstructor");

	private static ElementMatcher.Junction<MethodDescription> methodMatcher() {
		return isAnnotatedWith(named("lt.slabs.com.jkoolcloud.nisha.onEnter"));
	}

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return named(INTERCEPTING_CLASS[0]);
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	@Advice.OnMethodEnter
	public static void before(@Advice.AllArguments Object[] args, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Origin Method method, //
			@Advice.This Object thiz, //
			@Advice.Local("startTime") long starttime) //
	{
		try {
			ctx = prepareIntercept(SimpleTestConstructor.class, thiz, method, args);
			if (!ctx.intercept) {
				return;
			}
			System.out.println("BEFORE METHOD CALL");
			ed = getEntryDefinition(ed, SimpleTestConstructor.class, ctx);

			if (args != null && args[0] instanceof String) {
				System.out.println("OK");
			} else {
				System.out.println("NOK");
			}

			switch (method.getName()) {
			case "a":
				System.out.println("A");
				break;
			default:
				System.out.println("Default");
				break;

			}
			starttime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, ctx);
		} catch (Throwable t) {
			logger.info(t, "Exception during OnBefore");
			handleAdviceException(t, ctx);
		}
	}

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void after(@Advice.This Object obj, //
			@Advice.Origin Method method, //
			@Advice.AllArguments Object[] arguments, //
			@Advice.Thrown Throwable exception, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		boolean doFinally = true;
		try {
			ctx = prepareIntercept(SimpleTestConstructor.class, obj, method, arguments);
			if (!ctx.intercept) {
				return;
			}
			doFinally = checkEntryDefinition(ed, ctx);
			fillDefaultValuesAfter(ed, startTime, exception, ctx);
		} catch (Throwable t) {
			handleAdviceException(t, ctx);
		} finally {
			if (doFinally) {
				doFinally(ctx, obj.getClass());
			}
		}
	}

	@Override
	public AgentBuilder.Listener getListener() {
		return new TransformationLoggingListener(logger);
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}

}
