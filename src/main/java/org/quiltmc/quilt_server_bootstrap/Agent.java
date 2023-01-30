package org.quiltmc.quilt_server_bootstrap;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

public class Agent {
	public static final Path BOOTSTRAP_CACHE_PATH = Path.of("./.quilt/bootstrap/");

    private static Instrumentation instrumentation;

    public static void agentmain(String agentArgs, Instrumentation inst) throws Throwable {
        instrumentation = inst;
		// TODO - Replace me with internal JAR reading shenanigans!
		var stream = Agent.class.getResourceAsStream("/META-INF/jars/gson_wrapper.jar");
		var file = BOOTSTRAP_CACHE_PATH.resolve("gson_wrapper.jar").toFile();
		if (!file.exists()) {
			Files.createDirectories(file.toPath().getParent());
			Files.createFile(file.toPath());
			var outputStream = new FileOutputStream(file);
			stream.transferTo(outputStream);
		}
		addJar(BOOTSTRAP_CACHE_PATH.resolve("gson_wrapper.jar"));
	}

    public static void addJar(Path path) throws IOException {
        var jar = new JarFile(path.toFile());
        instrumentation.appendToSystemClassLoaderSearch(jar);
    }
}
