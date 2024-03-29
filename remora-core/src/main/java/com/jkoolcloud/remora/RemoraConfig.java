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

package com.jkoolcloud.nisha;

import static com.jkoolcloud.nisha.Remora.REMORA_PATH;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;
import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.core.EntryDefinition;
import com.jkoolcloud.nisha.core.output.AgentOutput;
import com.jkoolcloud.nisha.filters.AdviceFilter;
import com.jkoolcloud.nisha.filters.FilterManager;
import com.jkoolcloud.nisha.filters.StatisticEnabledFilter;

public enum RemoraConfig {
	INSTANCE;

	public static final String REMORA_PROPERTIES_FILE = "/config/nisha.properties";
	public static final String PREFIX = "filter.";
	public static final String SUFFIX = ".type";

	// If anyone wonders why it's not static
	// https://stackoverflow.com/questions/49141972/nullpointerexception-in-enum-logger
	private TaggedLogger logger = Logger.tag(Remora.MAIN_REMORA_LOGGER);
	public Properties config;
	public ClassLoader classLoader = null;

	RemoraConfig() {
		init();
	}

	public static void configure(Object object) throws IllegalAccessException {
		Class<?> aClass = object.getClass();

		while (!aClass.equals(Object.class)) {
			Field[] fields = aClass.getDeclaredFields();
			for (Field field : fields) {
				if (field.isAnnotationPresent(Configurable.class)) {
					field.setAccessible(true);

					String configValue = getConfigValue(object.getClass(), field.getName());
					Object appliedValue = getAppliedValue(field, configValue);
					if (appliedValue != null) {
						INSTANCE.logger.debug("Setting {} class config field: \"{}\" = {}", object.getClass().getName(),
								field.getName(), appliedValue.toString());
						field.set(object, appliedValue);
					}

				}
			}
			aClass = aClass.getSuperclass();
		}
	}

	@Nullable
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object getAppliedValue(Field field, String configValue) {
		Object appliedValue = null;
		if (configValue != null) {
			if (field.getType().isEnum()) {
				appliedValue = Enum.valueOf((Class<Enum>) field.getType(), configValue);
			} else {
				switch (field.getType().getName()) {
				case "java.lang.String":
					appliedValue = configValue;
					break;
				case "java.util.List":
					if (field.getGenericType() instanceof ParameterizedType
							&& ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]
									.equals(AdviceFilter.class)) {
						appliedValue = FilterManager.INSTANCE.get(getList(configValue));
					} else {
						appliedValue = getList(configValue);
					}
					break;
				case "boolean":
					appliedValue = Boolean.parseBoolean(configValue.trim());
					//TODO apply trim for others too
					break;

				case "int":
				case "java.lang.Integer":
					appliedValue = Integer.parseInt(configValue);
					break;

				case "long":
				case "java.lang.Long":
					appliedValue = Long.parseLong(configValue);
					break;

				case "com.jkoolcloud.nisha.core.output.AgentOutput":
					try {
						Class<?> outClass = Class.forName(configValue);
						appliedValue = (AgentOutput<EntryDefinition>) outClass.newInstance();
					} catch (Exception e) {
						INSTANCE.logger.error(e, "AgentOutput couldn't be defined: {}", e);
						appliedValue = null;
					}

				case "default":
					INSTANCE.logger.info("Unsupported property {}, {}", field.getType().getSimpleName(), configValue);

				}
			}
		}
		return appliedValue;
	}

	private static List<?> getList(String configValue) {
		if (configValue == null) {
			return null;
		}
		String[] split = configValue.split(";");
		return Arrays.stream(split).map(v -> v.trim()).collect(Collectors.toList());
	}

	private static String getConfigValue(Class<?> aClass, String name) {
		Class<?> workingClass = aClass;
		String value = null;
		while (value == null && !workingClass.equals(Object.class)) {
			value = INSTANCE.config.getProperty(workingClass.getName() + "." + name);
			workingClass = workingClass.getSuperclass();
		}
		return value;
	}

	protected void init() {
		config = new Properties();

		String nishaPath = System.getProperty(REMORA_PATH);
		File file = new File(nishaPath + REMORA_PROPERTIES_FILE);
		try (FileInputStream inStream = new FileInputStream(file)) {
			config.load(inStream);
			logger.info("Successfully loaded {} properties from configuration file", config.size());
		} catch (IOException e) {
			logger.error(e, "Failed loading properties file");
		}
		configureFilters();
	}

	protected void configureFilters() {

		List<String> filterNames = config.entrySet().stream()
				.filter(property -> property.getKey().toString().startsWith(PREFIX)
						&& property.getKey().toString().endsWith(SUFFIX))
				.map(property -> {
					String s = property.getKey().toString();
					return s.substring(PREFIX.length(), s.length() - SUFFIX.length());
				}).collect(Collectors.toList());
		filterNames.forEach(filterName -> {
			try {
				String filterClass = config.getProperty(PREFIX + filterName + SUFFIX);
				Class<?> aClass = Class.forName(filterClass);
				StatisticEnabledFilter adviceFilter = (StatisticEnabledFilter) aClass.newInstance();

				for (Field field : aClass.getFields()) {
					if (field.isAnnotationPresent(Configurable.class)) {
						String propValue = config.getProperty(PREFIX + filterName + "." + field.getName());
						if (propValue != null) {
							Object appliedValue = getAppliedValue(field, propValue);
							field.set(adviceFilter, appliedValue);
						}

					}
				}
				FilterManager.INSTANCE.add(filterName, adviceFilter);

			} catch (Exception e) {
				INSTANCE.logger.error("Filter configuration failed", e);
			}
		});
	}

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Configurable {
		boolean configurableOnce() default false;
	}

}
