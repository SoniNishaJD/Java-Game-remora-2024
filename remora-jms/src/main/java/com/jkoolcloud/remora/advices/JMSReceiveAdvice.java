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

import com.jkoolcloud.nisha.RemoraConfig;
import com.jkoolcloud.nisha.core.EntryDefinition;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.tinylog.TaggedLogger;

import javax.jms.*;
import java.lang.reflect.Method;

import static com.jkoolcloud.nisha.core.utils.ReflectionUtils.getFieldValue;
import static net.bytebuddy.matcher.ElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class JMSReceiveAdvice extends BaseTransformers implements RemoraAdvice {
    public static final String ADVICE_NAME = "JMSReceiveAdvice";
    public static final String[] INTERCEPTING_CLASS = {"javax.jms.MessageConsumer"};
    public static final String INTERCEPTING_METHOD = "receive";

    @RemoraConfig.Configurable
    public static boolean fetchMsg = false;
    static AgentBuilder.Transformer.ForAdvice advice = new AgentBuilder.Transformer.ForAdvice()
            .include(JMSReceiveAdvice.class.getClassLoader())//
            .include(RemoraConfig.INSTANCE.classLoader) //
            .advice(methodMatcher(), JMSReceiveAdvice.class.getName());

    /**
     * Method matcher intended to match intercepted class method/s to instrument. See (@ElementMatcher) for available
     * method matches.
     */

    private static ElementMatcher.Junction<NamedElement> methodMatcher() {
        return named(INTERCEPTING_METHOD);
    }

    /**
     * Advices before method is called before instrumented method code
     *
     * @param thiz      reference to method object
     * @param arguments arguments provided for method
     * @param method    instrumented method description
     * @param ed        {@link EntryDefinition} for collecting ant passing values to
     *                  {@link com.jkoolcloud.nisha.core.output.OutputManager}
     * @param startTime method startTime
     */

    @Advice.OnMethodEnter
    public static void before(@Advice.This MessageConsumer thiz, //
                              @Advice.AllArguments Object[] arguments, //
                              @Advice.Origin Method method, //
                              @Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
                              @Advice.Local("startTime") long startTime)//
    {
        try {
            ctx = prepareIntercept(JMSReceiveAdvice.class, thiz, method, arguments);
            if (!ctx.intercept) {
                return;
            }
            ed = getEntryDefinition(ed, JMSReceiveAdvice.class, ctx);
            ed.setEventType(EntryDefinition.EventType.RECEIVE);
            startTime = fillDefaultValuesBefore(ed, stackThreadLocal, thiz, method, ctx);
            if (thiz instanceof QueueReceiver) {
                String queueName = ((QueueReceiver) thiz).getQueue().getQueueName();
                ed.addPropertyIfExist("QUEUE", queueName);
                ed.setResource(queueName, EntryDefinition.ResourceType.QUEUE);
            }
        } catch (Throwable t) {
            handleAdviceException(t, ctx);
        }
    }

    /**
     * Method called on instrumented method finished.
     *
     * @param obj       reference to method object
     * @param method    instrumented method description
     * @param arguments arguments provided for method
     * @param exception exception thrown in method exit (not caught)
     * @param ed        {@link EntryDefinition} passed along the method (from before method)
     * @param startTime startTime passed along the method
     */

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(@Advice.This MessageConsumer obj, //
                             @Advice.Origin Method method, //
                             @Advice.AllArguments Object[] arguments, //
                             @Advice.Thrown Throwable exception, //
                             @Advice.Return Message message, //
                             @Advice.Local("ed") EntryDefinition ed, @Advice.Local("context") InterceptionContext ctx, //
                             @Advice.Local("startTime") long startTime) //
    {
        boolean doFinally = true;
        try {
            ctx = prepareIntercept(JMSReceiveAdvice.class, obj, method, arguments);
            if (!ctx.intercept) {
                return;
            }
            TaggedLogger logger = ctx.interceptorInstance.getLogger();

            doFinally = checkEntryDefinition(ed, ctx);

            if (message != null) {
                ed.addPropertyIfExist("MESSAGE_ID", message.getJMSMessageID());
                ed.addPropertyIfExist("CORR_ID", message.getJMSCorrelationID());
                ed.addPropertyIfExist("TYPE", message.getJMSType());
                Destination jmsDestination = message.getJMSDestination();
                if (jmsDestination == null) {
                    jmsDestination = getFieldValue(message, Destination.class, "destination");
                    logger.debug("Destination2: " + jmsDestination);
                } else {
                    logger.debug("Destination1: " + jmsDestination);
                }
                if (jmsDestination != null) {
                    String resource;
                    if (jmsDestination instanceof Queue) {
                        resource = ((Queue) jmsDestination).getQueueName();
                    } else if (jmsDestination instanceof Topic) {
                        resource = ((Topic) jmsDestination).getTopicName();
                    } else {
                        resource = String.valueOf(jmsDestination);
                    }
                    ed.setResource(resource, EntryDefinition.ResourceType.QUEUE);
                }
                if (fetchMsg && message instanceof TextMessage) {
                    ed.addPropertyIfExist("MSG", ((TextMessage) message).getText());
                }
            } else {
                logger.debug("Message is null");
            }
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
     * Type matcher should find the class intended for intrumentation See (@ElementMatcher) for available matches.
     */

    @Override
    public ElementMatcher<TypeDescription> getTypeMatcher() {
        return hasSuperType(named(INTERCEPTING_CLASS[0]));
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
