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

import java.io.File;
import java.io.FileFilter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.tinylog.Logger;
import org.tinylog.TaggedLogger;

import com.jkoolcloud.nisha.core.EntryDefinition;
import com.jkoolcloud.nisha.core.output.OutputManager;
import com.jkoolcloud.nisha.core.utils.RemoraClassLoader;

public class Remora {

	public static final String REMORA_VM_IDENTIFICATION = "nisha.vmid";
	public static final String MAIN_REMORA_LOGGER = "INIT";
	public static TaggedLogger logger;

	public static final boolean DEBUG_BOOT_LOADER = true;
	public static final String MODULES_DIR = "/modules";
	public static final String REMORA_PATH = "nisha.path";
	public static final String MAIN_LOG = "/log/" + "nisha%u.log";
	private static URLClassLoader bootLoader;

	public Remora() {
		logger.info("App loaded with classloader: " + getClass().getClassLoader());
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Starting from main");
		System.out.println("This application intended to be used as javaAgent.");
	}

	public static void premain(String options, Instrumentation inst) throws Exception {
		if (System.getProperty(REMORA_PATH) == null) {
			System.setProperty(REMORA_PATH, options == null ? getJarContainingFolder(Remora.class) : options);
		}
		String baseRemoraDir = System.getProperty(Remora.REMORA_PATH);
		String version = getVersion();
		System.out.println("Running RemoraJ " + version);

		inst.appendToBootstrapClassLoaderSearch(new JarFile(baseRemoraDir + "/" + "nisha.jar"));
		bootLoader = new RemoraClassLoader(findJars(baseRemoraDir + MODULES_DIR), Remora.class.getClassLoader(), inst);
		logger = Logger.tag(MAIN_REMORA_LOGGER);

		logger.info("Running RemoraJ {}", version);
		logger.info("Running from {}", System.getProperty(REMORA_PATH));
		logRunEnv();
		logger.info("Initializing classloader: " + bootLoader);
		Class<?> appClass = bootLoader.loadClass("com.jkoolcloud.nisha.RemoraInit");
		Object instance = appClass.newInstance();
		logger.info("Initializing agent class: " + appClass);
		Method initializeAdvices = appClass.getMethod("initializeAdvices", Instrumentation.class, ClassLoader.class);
		logger.info("Initializing advices: " + appClass);
		initializeAdvices.invoke(instance, inst, bootLoader);

		String vmid = System.getProperty(REMORA_VM_IDENTIFICATION);
		if (vmid == null) {
			vmid = getDefaultVM();
			System.setProperty(REMORA_VM_IDENTIFICATION, vmid);
		}
		EntryDefinition.setVmIdentification(vmid);
		// Load output and config manager by Bootstarp classloader;
		RemoraConfig.INSTANCE.name();
		OutputManager.INSTANCE.install();
	}

	static void logRunEnv() {
		List<String> envProps = Arrays.asList(new String[] { // set of interesting runtime environment properties
				"java.version", "java.vendor", "java.vm.name", "java.vm.version", // JVM props
				"os.name", "os.version" // OS props
		});

		Map<Boolean, List<Map.Entry<Object, Object>>> sortedProperties = System.getProperties().entrySet().stream()
				.collect(Collectors.partitioningBy(e -> envProps.contains(e.getKey())));

		List<Map.Entry<Object, Object>> importantProperties = sortedProperties.get(Boolean.TRUE);
		List<Map.Entry<Object, Object>> otherProperties = sortedProperties.get(Boolean.FALSE);

		logger.info("------------------------------------------------------------------------\n"); // NON-NLS
		for (Map.Entry<Object, Object> property : importantProperties) {
			logger.info(String.format("%20s: %s", property.getKey(), property.getValue()));

		}
		for (Map.Entry<Object, Object> property : otherProperties) {
			logger.debug(String.format("%20s: %s", property.getKey(), property.getValue()));

		}

		logger.info("------------------------------------------------------------------------\n"); // NON-NLS

	}

	public static String getJarContainingFolder(Class<Remora> aclass) throws Exception {
		String path = aclass.getResource(aclass.getSimpleName() + ".class").getPath();
		String jarFilePath = path.substring(path.indexOf(":") + 1, path.indexOf("!"));
		jarFilePath = URLDecoder.decode(jarFilePath, "UTF-8");
		File jarFile = new File(jarFilePath);
		return jarFile.getParentFile().getAbsolutePath();
	}

	private static String getDefaultVM() {
		String processName;
		try {
			processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
		} catch (Exception e) {
			processName = "NA";
		}
		return processName;
	}

	public static URL[] findJars(String location) throws MalformedURLException {
		File moduleFolder = new File(location);

		if (moduleFolder.exists()) {

			File[] modulesFiles = moduleFolder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File pathname) {
					String name = pathname.getName();
					return name.endsWith(".jar") || name.endsWith(".zip");
				}
			});
			if (modulesFiles == null) {
				logger.error("No modules found");
				return null;
			}
			List<File> files = Arrays.asList(modulesFiles);

			URL[] uris = files.stream().map(file -> {
				try {
					return file.toURI().toURL();
				} catch (MalformedURLException e) {
					return null;
				}
			}).peek(file -> logFoundClasspathEntry(file)).collect(Collectors.toList()).toArray(new URL[files.size()]);

			return uris;
		} else {
			logger.error("Module dir is not existing; Check java agent parameters; --javaagent:nisha.jar=parameters. "
					+ "Parameters should point to the directory nisha exists");
			return null;
		}
	}

	private static void logFoundClasspathEntry(URL file) {
		if (logger == null) {
			System.out.println("Found: " + file);
		} else {
			logger.info("Found: " + file);
		}
	}

	public static String getVersion() {
		Package sPkg = Remora.class.getPackage();
		return sPkg.getImplementationVersion();
	}

	public static URLClassLoader getClassLoader() {
		return Remora.bootLoader;
	}

}
