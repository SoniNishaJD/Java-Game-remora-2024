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
import java.sql.Statement;

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@TransparentAdvice
public class JDBCCallableStatementAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "JDBCStatementParamsAdvice";
	public static final String[] INTERCEPTING_CLASS = { "java.sql.Statement" };
	public static final String INTERCEPTING_METHOD = "set*";

	@RemoraConfig.Configurable
	public static final String parameterPrefix = "PARAM_";

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */

	private static ElementMatcher<? super MethodDescription> methodMatcher() {
		return nameStartsWith("set").and(takesArgument(0, String.class).or(takesArgument(0, int.class)))
				.and(takesArguments(2)).and(isPublic());
	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return not(isInterface()).and(hasSuperType(named(INTERCEPTING_CLASS[0])));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(JDBCCallableStatementAdvice.class.getClassLoader())//
			.include(RemoraConfig.INSTANCE.classLoader)//
			.advice(methodMatcher(), JDBCCallableStatementAdvice.class.getName());

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
	public static void before(@Advice.This Statement thiz, //
			@Advice.Argument(0) Object parameterName, //
			@Advice.Argument(1) Object parameterValue, //
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		try {
			ctx = prepareIntercept(JDBCCallableStatementAdvice.class, thiz, method, parameterName, parameterValue);
			if (!ctx.intercept) {
				return;
			}
			ed = getEntryDefinition(ed, JDBCCallableStatementAdvice.class, ctx);
			stackThreadLocal.get().push(ed);
			if (parameterName instanceof String) {
				ed.addPropertyIfExist(parameterName.toString(), parameterValue.toString());
			} else {
				ed.addPropertyIfExist(parameterPrefix + parameterName, parameterValue.toString());
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
	 * @param exception
	 *            exception thrown in method exit (not caught)
	 * @param ed
	 *            {@link EntryDefinition} passed along the method (from before method)
	 * @param startTime
	 *            startTime passed along the method
	 */

	@Advice.OnMethodExit(onThrowable = Throwable.class)
	public static void after(@Advice.This Statement thiz, //
			@Advice.Origin Method method, //
			// @Advice.Return Object returnValue, // //TODO needs separate Advice capture for void type
			@Advice.Thrown Throwable exception, @Advice.Local("ed") EntryDefinition ed,
			@Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		try {
			ctx = prepareIntercept(JDBCCallableStatementAdvice.class, thiz, method);
			if (!ctx.intercept) {
				return;
			}
			stackThreadLocal.get().pop();
		} catch (Exception e) {

		}
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}
}
