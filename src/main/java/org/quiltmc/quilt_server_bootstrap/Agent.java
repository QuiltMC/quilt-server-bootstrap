package org.quiltmc.quilt_server_bootstrap;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class Agent {
	private static Instrumentation instrumentation;

	public static void agentmain(String agentArgs, Instrumentation inst) {
		instrumentation = inst;
	}

	public static void addJar(Path path) throws IOException {
		var jar = new JarFile(path.toFile());
		instrumentation.appendToSystemClassLoaderSearch(jar);
	}
}
