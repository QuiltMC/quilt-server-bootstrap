/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
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
