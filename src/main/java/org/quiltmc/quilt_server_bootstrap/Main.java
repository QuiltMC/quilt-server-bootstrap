package org.quiltmc.quilt_server_bootstrap;
/*
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

import org.quiltmc.quilt_server_bootstrap.schemas.InstallerMetadata;
import qsb.gson.Gson;
import qsb.gson.GsonBuilder;
import qsb.gson.JsonParser;

public class Main {
	public static final Path BOOTSTRAP_CACHE_PATH = Path.of("./.quilt/bootstrap/");
	public static final boolean KEEP_BUNDLER = false;
	public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	// FIXME - i literally keep eating exceptions with a blanket Throwable... Fix that!
	public static void main(String[] args) throws Exception {
		new Main().download();
	}

	public void download() throws Exception {
		var pathQJ = Main.class.getResourceAsStream("/a.json");

		var installerMetadata = GSON.fromJson(new InputStreamReader(pathQJ, StandardCharsets.UTF_8), InstallerMetadata.class);
		var list = new ArrayList<InstallerMetadata.Library>();
		list.addAll(installerMetadata.launcherMeta().libraries().common());
		list.addAll(installerMetadata.launcherMeta().libraries().server());
		list.add(new InstallerMetadata.Library(installerMetadata.loader().maven(), "https://maven.quiltmc.org/repository/release/"));
		list.add(new InstallerMetadata.Library(installerMetadata.hashed().maven(), "https://maven.quiltmc.org/repository/release/"));
		list.add(new InstallerMetadata.Library(installerMetadata.intermediary().maven(), "https://maven.fabricmc.net/"));

		List<Library> libraries = new ArrayList<>();
		for (var library : list) {
			var splitDep = library.name().split(":");
			var pathFolder = splitDep[0].replace(".", "/")
					+ "/" + splitDep[1]
					+ "/" + splitDep[2];
			var path = pathFolder
					+ "/" + splitDep[1]
					+ "-" + splitDep[2] + (splitDep.length == 4 ? "-" + splitDep[3] : "")
					+ ".jar";
			var uri = library.url() + path;
			Files.createDirectories(Path.of("./libraries/" + pathFolder));
			libraries.add(new Library(URI.create(uri), "./libraries/" + path));
		}

		// Ensure that our bootstrap cache folder exists
		Files.createDirectories(BOOTSTRAP_CACHE_PATH);

		var futures = new ArrayList<CompletableFuture<?>>();
		var urls = new ArrayList<URL>();

		for (var library : libraries) {
			var path = Path.of(library.path());
			urls.add(path.toUri().toURL());
			if (!Files.exists(path)) {
				System.out.println("Downloading " + library.uri());
				var request = HttpRequest.newBuilder(library.uri()).GET().build();
				var future = HTTP_CLIENT.sendAsync(request, BodyHandlers.ofFile(path, StandardOpenOption.WRITE, StandardOpenOption.CREATE))
					.thenApply(HttpResponse::body)
					.thenAccept(pathy -> {});
				futures.add(future);
			}
		}

		var futureArray = futures.toArray(new CompletableFuture[futures.size()]);
		var bigFuture = CompletableFuture.allOf(futureArray);

		bigFuture.thenAccept(path -> {
			if (futures.size() != 0) {
				System.out.println("Downloaded all the Quilt Loader libraries!");
			}

			try {
				for (var library : libraries) {
					Agent.addJar(Path.of(library.path()));
				}

				this.loadBundler();
				this.run();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		});
		bigFuture.join();
	}

	public void loadBundler() throws Throwable {
		if (KEEP_BUNDLER) {
			var bundlerPath = BOOTSTRAP_CACHE_PATH.resolve("server.jar");
			if (!Files.exists(bundlerPath)) {
				this.downloadBundler();
			}

			Agent.addJar(bundlerPath);
			System.setProperty("loader.gameJarPath", bundlerPath.toString());
		} else {
			this.attemptBundleExtraction();
		}
	}

	public void attemptBundleExtraction() throws Throwable {
		var jsonPath = BOOTSTRAP_CACHE_PATH.resolve("vanilla_bundler_extraction.json");
		var pathList = new ArrayList<Path>();
		Path versionPath = null;
		boolean isComplete = true;

		if (Files.exists(jsonPath)) {
			var json = JsonParser.parseReader(new FileReader(jsonPath.toFile())).getAsJsonObject();
			var array = json.get("libraries").getAsJsonArray();

			for (var element : array) {
				var libraryPath = Path.of(element.getAsString());
				if (!Files.exists(libraryPath)) {
					isComplete = false;
				} else {
					pathList.add(libraryPath);
				}
			}

			versionPath = Path.of(json.get("version").getAsString());
			pathList.add(versionPath);
		} else {
			isComplete = false;
		}

		if (isComplete) {
			for (var path: pathList) {
				Agent.addJar(path);
			}

			System.setProperty("loader.gameJarPath", versionPath.toAbsolutePath().toString());
		} else {
			var bundlerPath = BOOTSTRAP_CACHE_PATH.resolve("server.jar");
			if (!Files.exists(bundlerPath)) {
				this.downloadBundler();
			}

			this.extractBundle(bundlerPath);
			Files.delete(bundlerPath);
		}
	}

	public void extractBundle(Path path) throws Throwable {
		var zip = new ZipFile(path.toFile());
		var librariesEntry = zip.getEntry("META-INF/libraries.list");
		var versionsEntry = zip.getEntry("META-INF/versions.list");

		var libraryLines = new BufferedReader(new InputStreamReader(zip.getInputStream(librariesEntry))).lines();
		var versionsLines = new BufferedReader(new InputStreamReader(zip.getInputStream(versionsEntry)));
		var bigListOfDifference = new ArrayList<Path>();

		//Extract libraries
		libraryLines.forEach(lines -> {
			try {
				// Hash, Maven, Directory
				var listInfo = lines.split("\t");
				var inputStream = zip.getInputStream(zip.getEntry("META-INF/libraries/" + listInfo[2]));
				var libPath = Path.of("./libraries/" + listInfo[2]);

				Files.createDirectories(libPath.getParent());
				Files.write(libPath, inputStream.readAllBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
				bigListOfDifference.add(libPath);
				Agent.addJar(libPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		// Extract version
		var versionInfo = versionsLines.readLine().split("\t");
		var versionInputStream = zip.getInputStream(zip.getEntry("META-INF/versions/" + versionInfo[2]));
		var versionPath = Path.of("./versions/" + versionInfo[2]);

		Files.createDirectories(versionPath.getParent());
		Files.write(versionPath, versionInputStream.readAllBytes(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		Agent.addJar(versionPath);

		if (versionsLines.read() != -1) {
			throw new Exception("This is wrong... There are more than two versions??");
		}

		zip.close();

		var writer = GSON.newJsonWriter(new FileWriter(BOOTSTRAP_CACHE_PATH.resolve("vanilla_bundler_extraction.json").toFile()));

		writer.beginObject()
			.name("libraries")
			.beginArray();
		for (var aaaaa: bigListOfDifference) {
			writer.value(aaaaa.toString());
		}
		writer.endArray()
			.name("version")
			.value(versionPath.toString())
			.endObject()
			.close();

		// Point Loader to the server JAR
		System.setProperty("loader.gameJarPath", versionPath.toAbsolutePath().toString());
	}

	public void downloadBundler() throws Throwable {
		var bundlerPath = BOOTSTRAP_CACHE_PATH.resolve("server.jar");
		var bundlerLink = "https://piston-data.mojang.com/v1/objects/f69c284232d7c7580bd89a5a4931c3581eae1378/server.jar";

		System.out.println("Downloading the server JAR");
		// This is a boring sync download since we have nothing to do simultaneously at this point
		HTTP_CLIENT.send(
			HttpRequest.newBuilder(new URI(bundlerLink)).GET().build(),
			BodyHandlers.ofFile(bundlerPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
		System.out.println("Downloaded the server JAR!");
	}

	public void run() throws Throwable {
		var classLoader = ClassLoader.getSystemClassLoader();

		var classy = Class.forName("org.quiltmc.loader.impl.launch.knot.KnotServer", true, classLoader);
		var handle = MethodHandles.lookup().findStatic(classy, "main", MethodType.methodType(Void.TYPE, String[].class)).asFixedArity();
		handle.invoke(new String[] {});
	}

	public record Library(
		URI uri,
		String path
	) {}
}
*/
