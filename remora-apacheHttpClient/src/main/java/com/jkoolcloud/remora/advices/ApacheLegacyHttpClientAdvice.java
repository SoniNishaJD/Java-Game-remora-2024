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

import static net.bytebuddy.matcher.ElementMatchers.*;

import java.lang.reflect.Method;

import org.apache.http.client.methods.HttpRequestWrapper;
import org.apache.http.conn.routing.HttpRoute;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ApacheLegacyHttpClientAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "ApacheLegacyHttpClientAdvice";
	public static final String[] INTERCEPTING_CLASS = { "<CHANGE HERE>" };
	public static final String INTERCEPTING_METHOD = "<CHANGE HERE>";

	@RemoraConfig.Configurable
	public static String headerCorrIDName = "REMORA_CORR";

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */

	private static ElementMatcher<? super MethodDescription> methodMatcher() {
		return named("execute").and(takesArguments(3)).and(returns(hasSuperType(named("org.apache.http.HttpResponse"))))
				.and(takesArgument(0, hasSuperType(named("org.apache.http.HttpHost"))))
				.and(takesArgument(1, hasSuperType(named("org.apache.http.HttpRequest"))))
				.and(takesArgument(2, hasSuperType(named("org.apache.http.protocol.HttpContext"))));
	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return hasSuperType(named("org.apache.http.client.RequestDirector"));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(ApacheHttpClientAdvice.class.getClassLoader())//
			.include(RemoraConfig.INSTANCE.classLoader) //
			.advice(methodMatcher(), ApacheHttpClientAdvice.class.getName());

	/**
	 * Advices before method is called before instrumented method code
	 *
	 * @param thiz
	 *            reference to method object
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
	public static void before(@Advice.This Object thiz, //
			@Advice.Argument(0) HttpRoute route, @Advice.Argument(1) HttpRequestWrapper request,
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		try {
			ctx = prepareIntercept(ApacheLegacyHttpClientAdvice.class, thiz, method, route, request);
			if (!ctx.intercept) {
				return;
			}
			TaggedLogger logger = ctx.interceptorInstance.getLogger();
			ed = getEntryDefinition(ed, ApacheHttpClientAdvice.class, ctx);

			startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, ctx);
			ed.addPropertyIfExist("URI", request.getURI().toString());
			ed.addPropertyIfExist("HOST", route.getTargetHost().getHostName());
			ed.setResource(request.getURI().toString(), EntryDefinition.ResourceType.NETADDR);

			request.addHeader(headerCorrIDName, ed.getId());
			ed.addProperty(headerCorrIDName, ed.getId());
			logger.info("Atached correlator:  {}", ctx.interceptorInstance, ed.getId());
		} catch (Throwable t) {
			handleAdviceException(t, ctx);
		}
	}

	/**
	 * Method called on instrumented method finished.
	 *
	 * @param obj
	 *            reference to method object
	 * @param method
	 *            instrumented method description
	 * @param arguments
	 *            arguments provided for method
	 * @param exception
	 *            exception thrown in method exit (not caught)
	 * @param ed
	 *            {@link EntryDefinition} passed along the method (from before method)
	 * @param startTime
	 *            startTime passed along the method
	 */

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void after(@Advice.This Object obj, //
			@Advice.Origin Method method, //
			@Advice.AllArguments Object[] arguments, //
			// @Advice.Return Object returnValue, // //TODO needs separate Advice capture for void type
			@Advice.Thrown Throwable exception, @Advice.Local("ed") EntryDefinition ed,
			@Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		boolean doFinally = true;
		try {
			ctx = prepareIntercept(ApacheLegacyHttpClientAdvice.class, obj, method, arguments);
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
	public String getName() {
		return ADVICE_NAME;
	}
}
