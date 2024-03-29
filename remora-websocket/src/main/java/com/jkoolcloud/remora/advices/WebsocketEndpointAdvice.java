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

import javax.websocket.CloseReason;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class WebsocketEndpointAdvice extends BaseTransformers implements RemoraAdvice {

	public static final String ADVICE_NAME = "WebsocketEndpointAdvice";
	public static final String[] INTERCEPTING_CLASS = { "javax.websocket.Endpoint" };
	public static final String INTERCEPTING_METHOD = "onClose,onOpen,onError";

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(WebsocketEndpointAdvice.class.getClassLoader()).include(RemoraConfig.INSTANCE.classLoader)//
			.advice(methodMatcher(), WebsocketEndpointAdvice.class.getName());

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */

	private static ElementMatcher<? super MethodDescription> methodMatcher() {
		return (nameStartsWith("on").or(isAnnotatedWith(nameStartsWith("javax.websocket.On"))))//
				.and(takesArgument(0, named("javax.websocket.Session")));
	}

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
			@Advice.AllArguments Object[] args, //
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		try {
			ctx = prepareIntercept(WebsocketEndpointAdvice.class, thiz, method, args);
			if (!ctx.intercept) {
				return;
			}
			TaggedLogger logger = ctx.interceptorInstance.getLogger();
			ed = getEntryDefinition(ed, WebsocketEndpointAdvice.class, ctx);

			startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, ctx);

			if (args != null && args.length >= 1 && args[0] instanceof Session) {
				Session session = (Session) args[0];

				String name = method.getName();
				if ("onOpen".equals(name)) {
					logger.info("Encountered WebSocket onOpen");
					ed.setEventType(EntryDefinition.EventType.OPEN);
					for (MessageHandler handler : session.getMessageHandlers()) {

						WebsocketSessionAdvice.sessionHandlers.put(handler, session);
						logger.info("Adding known handler {} for session {}", handler, ctx.interceptorInstance,
								session);
					}
					WebsocketSessionAdvice.sessionEndpoints.put(session.getBasicRemote(), session);
					WebsocketSessionAdvice.sessionEndpoints.put(session.getAsyncRemote(), session);
				}
				if ("onClose".equals(name)) {
					logger.info("Encountered WebSocket onclose");
					ed.setEventType(EntryDefinition.EventType.CLOSE);
					if (args.length >= 2 && args[1] instanceof CloseReason) {
						ed.addPropertyIfExist("CLOSE_REASON", ((CloseReason) args[1]).getReasonPhrase());
						ed.addPropertyIfExist("CLOSE_CODE", ((CloseReason) args[1]).getCloseCode().getCode());
					}
					WebsocketSessionAdvice.sessionHandlers.keySet().removeAll(session.getMessageHandlers());
					WebsocketSessionAdvice.sessionEndpoints.remove(session.getBasicRemote());
					WebsocketSessionAdvice.sessionEndpoints.remove(session.getAsyncRemote());

				}
				if ("OnError".equals(name)) {
					logger.info("Encountered WebSocket onError");
					ed.setEventType(EntryDefinition.EventType.CLOSE);
					if (args.length >= 2 && args[1] instanceof Throwable) {
						if (args[1] instanceof Throwable) {
							ed.setException(((Throwable) args[1]));

						}
					}
					WebsocketSessionAdvice.sessionHandlers.keySet().removeAll(session.getMessageHandlers());
					WebsocketSessionAdvice.sessionEndpoints.remove(session.getBasicRemote());
					WebsocketSessionAdvice.sessionEndpoints.remove(session.getAsyncRemote());

				}
			}
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
			@Advice.Thrown Throwable exception, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) {
		boolean doFinally = true;
		try {
			ctx = prepareIntercept(WebsocketEndpointAdvice.class, obj, method, arguments);
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

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */

	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return (hasSuperType(nameStartsWith(INTERCEPTING_CLASS[0]))
				.or(isAnnotatedWith(named("javax.websocket.server.ServerEndpoint")))).and(not(isAbstract()))
						.and(not(isInterface()));
	}

	@Override
	public AgentBuilder.Transformer getAdvice() {
		return advice;
	}

	@Override
	public String getName() {
		return ADVICE_NAME;
	}

}
