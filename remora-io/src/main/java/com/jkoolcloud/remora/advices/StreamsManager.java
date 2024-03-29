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

import static com.jkoolcloud.nisha.advices.BaseTransformers.doFinally;
import static com.jkoolcloud.nisha.advices.BaseTransformers.getStackTrace;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.jetbrains.annotations.Nullable;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.core.EntryDefinition;

public enum StreamsManager {

	INSTANCE;

	public final AtomicLong totalTrackedInputStreams = new AtomicLong();
	public final AtomicLong totalTrackedOutputStreams = new AtomicLong();

	public final WeakHashMap<InputStream, EntryDefinition> availableInputStreams = new CountingWeakHashMap<>(
			totalTrackedInputStreams);
	public WeakHashMap<EntryDefinition, StreamStats> availableInputStreamsEntries = new WeakHashMap<>(500);

	public final WeakHashMap<OutputStream, EntryDefinition> availableOutputStreams = new CountingWeakHashMap<>(
			totalTrackedOutputStreams);
	public WeakHashMap<EntryDefinition, StreamStats> availableOutputStreamsEntries = new WeakHashMap<>(500);

	public StreamStats get(InputStream thiz, BaseTransformers.InterceptionContext ctx, Method method) {

		WeakHashMap<InputStream, EntryDefinition> availableInputStreams = this.availableInputStreams;
		WeakHashMap<EntryDefinition, StreamStats> availableInputStreamsEntries = this.availableInputStreamsEntries;

		return checkForEntryOrCreate(thiz, ctx, method, availableInputStreams, availableInputStreamsEntries,
				InputStreamReadAdvice.class);

	}

	public StreamStats get(OutputStream thiz, BaseTransformers.InterceptionContext ctx, Method method) {

		WeakHashMap<OutputStream, EntryDefinition> availableOutputStreams = this.availableOutputStreams;
		WeakHashMap<EntryDefinition, StreamStats> availableOutputStreamsEntries = this.availableOutputStreamsEntries;

		return checkForEntryOrCreate(thiz, ctx, method, availableOutputStreams, availableOutputStreamsEntries,
				OutputStreamWriteAdvice.class);

	}

	public StreamStats close(InputStream thiz, BaseTransformers.InterceptionContext ctx, Method method) {
		WeakHashMap<InputStream, EntryDefinition> availableStreams = availableInputStreams;
		WeakHashMap<EntryDefinition, StreamStats> availableStreamsEntries = availableInputStreamsEntries;

		return closeAndGenerateStats(thiz, ctx, availableStreamsEntries, availableStreams);
	}

	public StreamStats close(OutputStream thiz, BaseTransformers.InterceptionContext ctx, Method method) {
		WeakHashMap<OutputStream, EntryDefinition> availableStreams = availableOutputStreams;
		WeakHashMap<EntryDefinition, StreamStats> availableStreamsEntries = availableOutputStreamsEntries;

		return closeAndGenerateStats(thiz, ctx, availableStreamsEntries, availableStreams);
	}

	@Nullable
	private static <T> StreamStats closeAndGenerateStats(T thiz, BaseTransformers.InterceptionContext ctx,
			WeakHashMap<EntryDefinition, StreamStats> availableStreamsEntries,
			WeakHashMap<T, EntryDefinition> availableStreams) {
		boolean doFinally = true;
		try {

			StreamStats streamStats = checkForEntryOrCreate(thiz, ctx, ctx.method, availableStreams,
					availableStreamsEntries, ctx.interceptorInstance.getClass());
			TaggedLogger logger = ctx.interceptorInstance.getLogger();
			EntryDefinition ed = availableStreams.get(thiz);
			if (ed == null) {
				if (logger != null) {
					logger.error("Stream closed but not tracked: {}, {}", ctx.interceptorInstance, thiz,
							thiz.getClass());
				}
				doFinally = false;
			} else {
				if (logger != null) {
					logger.info("Close invoked on stream {}", ctx.interceptorInstance, ed.getId());
				}

				if (!ed.isChained()) {
					if (streamStats != null) {
						ed.setEventType(EntryDefinition.EventType.CLOSE);
						ed.addPropertyIfExist("bytesCount", streamStats.count);
						ed.addPropertyIfExist("lastAccessed", streamStats.accessTimestamp);
						ed.addPropertyIfExist("accessCount", streamStats.accessCount);
					} else {
						logger.error("{} has no stats", ctx, thiz);
					}
					if (ctx.interceptorInstance.sendStackTrace) {
						ed.addPropertyIfExist("closeStackTrace", getStackTrace());
					}
					if (ctx.method.getName().equals("finalize")) {
						ed.addProperty("finalize", "true");
					}
				}
				BaseTransformers.fillDefaultValuesAfter(ed,
						streamStats == null ? System.currentTimeMillis() : streamStats.starttime, null, ctx);

				if (ed.isFinished() && !availableStreams.containsValue(ed)) {
					availableStreamsEntries.remove(ed);
				}
			}
		} catch (Throwable t) {
			BaseTransformers.handleAdviceException(t, ctx);
		} finally {
			if (doFinally) {
				doFinally(ctx, thiz.getClass());
			}
		}
		return null;
	}

	private static <T> StreamStats checkForEntryOrCreate(T thiz, BaseTransformers.InterceptionContext ctx,
			Method method, WeakHashMap<T, EntryDefinition> availableStreams,
			WeakHashMap<EntryDefinition, StreamStats> availableStreamsEntries,
			Class<? extends BaseTransformers> adviceClass) {
		TaggedLogger logger = ctx.interceptorInstance.getLogger();
		EntryDefinition ed = null;
		if (!availableStreams.containsKey(thiz)) {
			ed = BaseTransformers.getEntryDefinition(ed, adviceClass, ctx);
			logger.debug("New stream {}, ed: {}", ctx, thiz, ed);
			availableStreams.put(thiz, ed);

		} else {
			logger.debug("Known stream {}, ed: {}", ctx, thiz, ed);
			ed = availableStreams.get(thiz);
		}

		if (!availableStreamsEntries.containsKey(ed)) {
			StreamStats streamStats = new StreamStats();
			logger.debug("Creating the new stream stats: {}, {}", ctx.interceptorInstance, ed.getId(), streamStats);
			ed.setEventType(EntryDefinition.EventType.OPEN);
			BaseTransformers.fillDefaultValuesBefore(ed, BaseTransformers.stackThreadLocal, thiz, method, ctx);
			streamStats.starttime = ed.getStartTime();
			ed.addProperty("toString", String.valueOf(thiz));
			availableStreamsEntries.put(ed, streamStats);
			doFinally(ctx, thiz.getClass());
			return streamStats;
		} else {
			StreamStats streamStats = availableStreamsEntries.get(ed);
			logger.debug("Fetched the stream stats entry: {}, {}", ctx.interceptorInstance, ed.getId(), streamStats);
			return streamStats;

		}

	}

	public WeakHashMap<EntryDefinition, StreamStats> getAvailableInputStreamsEntries() {
		return availableInputStreamsEntries;
	}

	public WeakHashMap<EntryDefinition, StreamStats> getAvailableOutputStreamsEntries() {
		return availableOutputStreamsEntries;
	}

	// For test only
	public void setAvailableInputStreamsEntries(
			WeakHashMap<EntryDefinition, StreamStats> availableInputStreamsEntries) {
		this.availableInputStreamsEntries = availableInputStreamsEntries;
	}

	public void setAvailableOutputStreamsEntries(
			WeakHashMap<EntryDefinition, StreamStats> availableOutputStreamsEntries) {
		this.availableOutputStreamsEntries = availableOutputStreamsEntries;
	}

	public WeakHashMap<InputStream, EntryDefinition> getAvailableInputStreams() {
		return availableInputStreams;
	}

	public WeakHashMap<OutputStream, EntryDefinition> getAvailableOutputStreams() {
		return availableOutputStreams;
	}

	private static class CountingWeakHashMap<K, V> extends WeakHashMap<K, V> {
		final AtomicLong count;

		public CountingWeakHashMap(AtomicLong countVar) {
			super(500);
			count = countVar;
		}

		@Override
		public V put(K key, V value) {
			count.incrementAndGet();
			return (V) super.put(key, value);
		}

	}
}
