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

import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@Tracked
public class SocketAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "SocketAdvice";
	public static String[] INTERCEPTING_CLASS = { "java.net.Socket" };
	public static String INTERCEPTING_METHOD = "connect";

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */

	private static ElementMatcher<? super MethodDescription> methodMatcher() {
		return named(INTERCEPTING_METHOD).and(takesArguments(2));
	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return hasSuperType(named(INTERCEPTING_CLASS[0]));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(SocketAdvice.class.getClassLoader()).include(RemoraConfig.INSTANCE.classLoader)//
			.advice(methodMatcher(), SocketAdvice.class.getName());

	/**
	 * Advices before method is called before instrumented method code
	 *
	 * @param thiz
	 *            reference to method object
	 * @param socketAddress
	 * @param timeout
	 *            arguments provided for method
	 * @param method
	 *            instrumented method description
	 * @param ed
	 *            {@link EntryDefinition} for collecting ant passing values to
	 *            {@link com.jkoolcloud.nisha.core.output.OutputManager}
	 * @param startTime
	 *            method startTime
	 *
	 */

	@Advice.OnMethodEnter
	public static void before(@Advice.This Socket thiz, //
			@Advice.Argument(0) SocketAddress socketAddress, //
			@Advice.Argument(1) int timeout, //
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		try {
			ctx = prepareIntercept(SocketAdvice.class, thiz, method, socketAddress, timeout);
			if (!ctx.intercept) {
				return;
			}
			ed = getEntryDefinition(ed, SocketAdvice.class, ctx);
			startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, ctx);
			ed.addPropertyIfExist("resource",
					thiz.getInetAddress() == null ? null : thiz.getInetAddress().getHostName());
			ed.addPropertyIfExist("localAddress",
					thiz.getLocalAddress() == null ? null : thiz.getLocalAddress().getHostName());
			ed.addPropertyIfExist("localPort", thiz.getLocalPort());
			ed.addPropertyIfExist("port", thiz.getPort());
			if (socketAddress instanceof InetSocketAddress) {
				ed.addPropertyIfExist("port", ((InetSocketAddress) socketAddress).getPort());
				ed.addPropertyIfExist("hostName", ((InetSocketAddress) socketAddress).getHostName());
				ed.addPropertyIfExist("hostString", ((InetSocketAddress) socketAddress).getHostString());

			}
		} catch (Throwable t) {
			handleAdviceException(t, ctx);
		}
	}

	/**
	 * Method called on instrumented method finished.
	 *
	 * @param thiz
	 *            reference to method object
	 * @param method
	 *            instrumented method description
	 * @param socketAddress
	 * @param timeout
	 *            arguments provided for method
	 * @param exception
	 *            exception thrown in method exit (not caught)
	 * @param ed
	 *            {@link EntryDefinition} passed along the method (from before method)
	 * @param startTime
	 *            startTime passed along the method
	 */

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void after(@Advice.This Object thiz, //
			@Advice.Origin Method method, //
			@Advice.Argument(0) SocketAddress socketAddress, //
			@Advice.Argument(1) int timeout, //
			@Advice.Thrown Throwable exception, @Advice.Local("ed") EntryDefinition ed,
			@Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		boolean doFinally = true;
		try {
			ctx = prepareIntercept(SocketAdvice.class, thiz, method, socketAddress, timeout);
			if (!ctx.intercept) {
				return;
			}
			ed.setEventType(EntryDefinition.EventType.OPEN);
			doFinally = checkEntryDefinition(ed, ctx);
			fillDefaultValuesAfter(ed, startTime, exception, ctx);
		} catch (Throwable t) {
			handleAdviceException(t, ctx);
		} finally {
			if (doFinally) {
				doFinally(ctx, thiz.getClass());
			}
		}
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}

}
