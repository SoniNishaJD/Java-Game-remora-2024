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
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.DispatcherType;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.CallStack;
import com.jkoolcloud.nisha.core.EntryDefinition;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class JavaxServletAdvice extends BaseTransformers implements RemoraAdvice {
	public static final String ADVICE_NAME = "JavaxHttpServlet";
	public static final String[] INTERCEPTING_CLASS = { "javax.servlet.http.HttpServlet" };
	public static final String INTERCEPTING_METHOD = "service";

	static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
			.include(JavaxServletAdvice.class.getClassLoader()) //
			.include(RemoraConfig.INSTANCE.classLoader) //
			.advice(methodMatcher(), JavaxServletAdvice.class.getName());
	@RemoraConfig.Configurable
	public static String cookiePrefix = "CKIE_";
	@RemoraConfig.Configurable
	public static String headerPrefix = "HDR_";
	@RemoraConfig.Configurable
	public static boolean attachCorrelator = true;
	@RemoraConfig.Configurable
	public static String headerCorrIDName = "REMORA_CORR";

	/**
	 * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
	 * method matches.
	 */
	public static ElementMatcher<? super MethodDescription> methodMatcher() {
		return named("service").and(takesArgument(0, named("javax.servlet.ServletRequest")))
				.and(takesArgument(1, named("javax.servlet.ServletResponse")));

	}

	/**
	 * Type matcher should find the class intended for instrumentation See (@ElementMatcher) for available matches.
	 */
	@Override
	public ElementMatcher<TypeDescription> getTypeMatcher() {
		return not(isInterface()).and(hasSuperType(named("javax.servlet.Servlet")));
	}

	/**
	 * Advices before method is called before instrumented method code
	 *
	 * @param thiz
	 *            reference to method object
	 * @param req
	 *            servlet request
	 * @param resp
	 *            servlet response
	 * @param method
	 *            instrumented method description
	 * @param ed
	 *            {@link EntryDefinition} for collecting ant passing values to
	 *            {@link com.jkoolcloud.nisha.core.output.OutputManager}
	 * @param startTime
	 *            method startTime
	 */
	@Advice.OnMethodEnter
	public static void before(@Advice.This Object thiz, //
			@Advice.Argument(0) ServletRequest req, //
			@Advice.Argument(1) ServletResponse resp, //
			@Advice.Origin Method method, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) //
	{
		try {
			ctx = prepareIntercept(JavaxServletAdvice.class, thiz, method, req, resp);
			if (!ctx.intercept) {
				return;
			}
			TaggedLogger logger = ctx.interceptorInstance.getLogger();

			ed = getEntryDefinition(ed, JavaxServletAdvice.class, ctx);
			startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, ctx);

			if (req instanceof HttpServletRequest && req.getDispatcherType() == DispatcherType.REQUEST) {
				try {
					ed.addPropertyIfExist("CLIENT", req.getRemoteAddr());
					ed.addPropertyIfExist("SERVER", req.getLocalName());

					HttpServletRequest request = (HttpServletRequest) req;

					ed.addPropertyIfExist("PROTOCOL", request.getProtocol());
					ed.addPropertyIfExist("METHOD", request.getMethod());
					ed.addPropertyIfExist("SECURE", request.isSecure());
					ed.addPropertyIfExist("SCHEME", request.getScheme());
					ed.addPropertyIfExist("SERVER", request.getServerName());
					ed.addPropertyIfExist("PORT", request.getServerPort());
					String requestURI = request.getRequestURI();
					ed.addPropertyIfExist("RESOURCE", requestURI);

					ed.setResource(requestURI, EntryDefinition.ResourceType.HTTP);

					if (stackThreadLocal != null && stackThreadLocal.get() != null
							&& stackThreadLocal.get() instanceof CallStack) {
						Pattern compile = Pattern.compile("/.[^/]*/");
						Matcher matcher = compile.matcher(requestURI);
						if (matcher.find()) {
							stackThreadLocal.get().setApplication(matcher.group(0));
						}
					}

					ed.addPropertyIfExist("QUERY", request.getQueryString());
					ed.addPropertyIfExist("CONTENT_TYPE", request.getHeader("Content-Type"));

					if (request.getCookies() != null) {
						for (Cookie cookie : request.getCookies()) {
							ed.addPropertyIfExist(cookiePrefix + cookie.getName(), cookie.getValue());
						}
					}
					Enumeration<String> headerNames = request.getHeaderNames();
					if (headerNames != null) {
						while (headerNames.hasMoreElements()) {
							String headerName = headerNames.nextElement();
							Enumeration<String> headerValues = request.getHeaders(headerName);
							StringBuilder headerValue = new StringBuilder();
							while (headerValues.hasMoreElements()) {
								headerValue.append(headerValues.nextElement());
								if (headerValues.hasMoreElements()) {
									headerValue.append(";");
								}
							}
							ed.addPropertyIfExist(headerPrefix + headerName, headerValue.toString());
						}
					}
					if (attachCorrelator && resp instanceof HttpServletResponse) {
						String nishaHeader = ((HttpServletRequest) req).getHeader(headerCorrIDName);
						if (nishaHeader == null) {
							((HttpServletResponse) resp).addHeader(headerCorrIDName, ed.getId());
							logger.info("Added header {} = {} ", ctx, headerCorrIDName, ed.getId());
						} else {
							((HttpServletResponse) resp).addHeader(headerCorrIDName, nishaHeader);
							ed.addPropertyIfExist(headerCorrIDName, nishaHeader);
						}
					}
				} catch (Throwable t) {
					logger.info("Failed getting some of properties" + req);
					logger.error(t);

				}

			} else {
				logger.info("Request is null: {}, {}, {}", req, resp, ed.getId());
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
	 * @param req
	 *            servlet request
	 * @param resp
	 *            servlet response
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
			@Advice.Argument(0) ServletRequest req, //
			@Advice.Argument(1) ServletResponse resp, //
			@Advice.Thrown Throwable exception, //
			@Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
			@Advice.Local("startTime") long startTime) //
	{
		boolean doFinally = true;
		try {
			ctx = prepareIntercept(JavaxServletAdvice.class, obj, method, req, resp);
			if (!ctx.intercept) {
				return;
			}
			doFinally = checkEntryDefinition(ed, ctx);
			fillDefaultValuesAfter(ed, startTime, exception, ctx);
			ed.addProperty("RespContext", resp.getContentType());
		} catch (Throwable t) {
			handleAdviceException(t, ctx);
		} finally {
			if (doFinally) {
				doFinally(ctx, obj.getClass());
			}
		}
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
