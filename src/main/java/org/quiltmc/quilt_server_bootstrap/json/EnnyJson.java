/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.quiltmc.quilt_server_bootstrap.json;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonToken;
import org.quiltmc.quilt_server_bootstrap.schemas.InstallerMetadata;

public class EnnyJson {
	enum Mode {
		UNKNOWN,
		KEY,
		VALUE,
		KV_END,
		OBJECT_END,
		ARRAY_VALUE,
	}

	// TODO - Move to Java 21 asap or else maintainability will be a nightmare
	public static EnnyMapOrArray parse(JsonReader reader) throws IOException {
		boolean done = false;
		int depth = 0;
		var mode = Mode.UNKNOWN;
		var roots = new ArrayList<EnnyMapOrArray>();
		var keys = new ArrayList<String>();
		EnnyMapOrArray tree = null;


		while (reader.peek() != JsonToken.END_DOCUMENT && !done) {
			switch (mode) {
				case UNKNOWN -> {
					switch (reader.peek()) {
						case BEGIN_ARRAY -> {
							tree = EnnyMapOrArray.newList();
							reader.beginArray();
							mode = Mode.ARRAY_VALUE;
							depth++;
							throw new IllegalStateException("Array at root isn't supported yet!");
						}
						case BEGIN_OBJECT -> {
							tree = EnnyMapOrArray.newMap();
							reader.beginObject();
							mode = Mode.KEY;
							depth++;
						}
						default -> throw new IllegalStateException();
					}
				}
				case KEY -> {
					switch (reader.peek()) {
						case NAME -> {
							keys.add(reader.nextName());
							mode = Mode.VALUE;
						}
						default -> throw new IllegalStateException();
					}
				}
				case VALUE -> {
					switch (reader.peek()) {
						case STRING -> {
							tree.getMap().put(keys.remove(keys.size() - 1), new EnnyJsonString(reader.nextString()));
							mode = Mode.KV_END;
						}
						case BOOLEAN -> {
							tree.getMap().put(keys.remove(keys.size() - 1), new EnnyJsonBoolean(reader.nextBoolean()));
							mode = Mode.KV_END;
						}
						case NUMBER -> {
							tree.getMap().put(keys.remove(keys.size() - 1), new EnnyJsonNumber(reader.nextNumber()));
							mode = Mode.KV_END;
						}
						case NULL -> {
							reader.nextNull();
							tree.getMap().put(keys.remove(keys.size() - 1), null);
							mode = Mode.KV_END;
						}
						case BEGIN_OBJECT -> {
							reader.beginObject();
							roots.add(tree);
							tree = EnnyMapOrArray.newMap();
							mode = Mode.KV_END;
							depth++;
						}
						case BEGIN_ARRAY -> {
							reader.beginArray();
							roots.add(tree);
							tree = EnnyMapOrArray.newList();
							mode = Mode.ARRAY_VALUE;
							depth++;
						}
						default -> throw new IllegalStateException();
					}
				}
				case KV_END -> {
					switch (reader.peek()) {
						case NAME -> {
							keys.add(reader.nextName());
							mode = Mode.VALUE;
						}
						case END_OBJECT -> {
							depth--;
							// TODO - This is a object root-only thing; support arrays!
							if (depth == 0) {
								mode = Mode.OBJECT_END;
								reader.endObject();
							} else {
								var lastRoot = roots.remove(roots.size() - 1);
								if (lastRoot.isMap()) {
									lastRoot.getMap().put(keys.remove(keys.size() - 1), new EnnyJsonMap(tree.getMap()));
									mode = Mode.KV_END;
								} else {
									lastRoot.getList().add(new EnnyJsonMap(tree.getMap()));
									mode = Mode.ARRAY_VALUE;
								}
								tree = lastRoot;
								reader.endObject();
							}
						}
						default -> throw new IllegalStateException();
					}
				}
				case OBJECT_END -> {
					switch (reader.peek())	{
						case END_DOCUMENT -> {
							done = true;
						}
						default -> throw new IllegalStateException();
					}
				}
				case ARRAY_VALUE -> {
					switch (reader.peek()) {
						case STRING -> {
							tree.getList().add(new EnnyJsonString(reader.nextString()));
							mode = Mode.ARRAY_VALUE;
						}
						case BOOLEAN -> {
							tree.getList().add(new EnnyJsonBoolean(reader.nextBoolean()));
							mode = Mode.ARRAY_VALUE;
						}
						case NUMBER -> {
							tree.getList().add(new EnnyJsonNumber(reader.nextNumber()));
							mode = Mode.ARRAY_VALUE;
						}
						case BEGIN_OBJECT -> {
							reader.beginObject();
							roots.add(tree);
							tree = EnnyMapOrArray.newMap();
							mode = Mode.KV_END;
							depth++;
						}
						case BEGIN_ARRAY -> {
							reader.beginArray();
							roots.add(tree);
							tree = EnnyMapOrArray.newList();
							mode = Mode.ARRAY_VALUE;
							depth++;
						}
						case END_ARRAY -> {
							depth--;
							var lastRoot = roots.remove(roots.size() - 1);
							if (lastRoot.isMap()) {
								lastRoot.getMap().put(keys.remove(keys.size() - 1), new EnnyJsonArray(tree.getList()));
								tree = lastRoot;
								mode = Mode.KV_END;
							} else {
								lastRoot.getList().add(new EnnyJsonArray(tree.getList()));
								tree = lastRoot;
								mode = Mode.ARRAY_VALUE;
							}
							reader.endArray();
						}
						default -> throw new IllegalStateException();
					}
				}
			}
		}

		return tree;
	}

	public static InstallerMetadata createInstallerMetadata(Reader reader) throws IOException {
		var jsonReader = JsonReader.json(reader);
		var map = parse(jsonReader).getMap();

		var loaderMap = map.get("loader").asMap();
		var hashedMap = map.get("hashed").asMap();
		var intermediaryMap = map.get("intermediary").asMap();
		var launcherMetaMap = map.get("launcherMeta").asMap();
		var librariesMap = launcherMetaMap.get("libraries").asMap();
		var librariesClientArray = librariesMap.get("client").asList().stream().map(
			element -> new InstallerMetadata.Library(
				element.asMap().get("name").asString(),
				element.asMap().get("url").asString()
			)
		).toList();
		var librariesCommonArray = librariesMap.get("common").asList().stream().map(
			element -> new InstallerMetadata.Library(
				element.asMap().get("name").asString(),
				element.asMap().get("url").asString()
			)
		).toList();
		var librariesServerArray = librariesMap.get("server").asList().stream().map(
			element -> new InstallerMetadata.Library(
				element.asMap().get("name").asString(),
				element.asMap().get("url").asString()
			)
		).toList();

		return new InstallerMetadata(
			new InstallerMetadata.Loader(
				loaderMap.get("separator").asString(),
				loaderMap.get("build").asNumber().intValue(),
				loaderMap.get("maven").asString(),
				loaderMap.get("version").asString()
			),
			new InstallerMetadata.Intermediate(
				hashedMap.get("maven").asString(),
				hashedMap.get("version").asString()
			),
			new InstallerMetadata.Intermediate(
				intermediaryMap.get("maven").asString(),
				intermediaryMap.get("version").asString()
			),
			new InstallerMetadata.LauncherMeta(
				launcherMetaMap.get("version").asNumber().intValue(),
				new InstallerMetadata.LauncherMeta.Libraries(
					librariesClientArray,
					librariesCommonArray,
					librariesServerArray
				)
			)
		);
	}

	public sealed interface EnnyJsonElement {
		default EnnyJsonMap asJsonMap() {
			return (EnnyJsonMap) this;
		}

		default Map<String, EnnyJsonElement> asMap() {
			return this.asJsonMap().map();
		}

		default EnnyJsonArray asJsonArray() {
			return (EnnyJsonArray) this;
		}

		default List<EnnyJsonElement> asList() {
			return this.asJsonArray().list();
		}

		default EnnyJsonString asJsonString() {
			return (EnnyJsonString) this;
		}

		default String asString() {
			return this.asJsonString().value();
		}

		default EnnyJsonBoolean asJsonBoolean() {
			return (EnnyJsonBoolean) this;
		}

		default boolean asBoolean() {
			return this.asJsonBoolean().value();
		}

		default EnnyJsonNumber asJsonNumber() {
			return (EnnyJsonNumber) this;
		}

		default Number asNumber() {
			return this.asJsonNumber().value();
		}
	}

	public record EnnyJsonMap(Map<String, EnnyJsonElement> map) implements EnnyJsonElement {}

	public record EnnyJsonArray(List<EnnyJsonElement> list) implements EnnyJsonElement {}

	public record EnnyJsonString(String value) implements EnnyJsonElement {}

	public record EnnyJsonBoolean(boolean value) implements EnnyJsonElement {}

	public record EnnyJsonNumber(Number value) implements EnnyJsonElement {}

	public static class EnnyMapOrArray {
		private final Map<String, EnnyJsonElement> map;
		private final List<EnnyJsonElement> list;

		public EnnyMapOrArray(Map<String, EnnyJsonElement> map) {
			this.map = map;
			this.list = null;
		}

		public EnnyMapOrArray(List<EnnyJsonElement> list) {
			this.map = null;
			this.list = list;
		}

		public static EnnyMapOrArray newMap() {
			return new EnnyMapOrArray(new HashMap<>());
		}

		public static EnnyMapOrArray newList() {
			return new EnnyMapOrArray(new ArrayList<>());
		}

		public Map<String, EnnyJsonElement> getMap() {
			return map;
		}

		public List<EnnyJsonElement> getList() {
			return list;
		}

		public boolean isMap() {
			return this.map != null && this.list == null;
		}

		public boolean isList() {
			return this.map == null && this.list != null;
		}

		@Override
		public String toString() {
			if (this.isMap()) {
				return "Map:" + this.getMap().toString();
			} else {
				return "List:" + this.getList().toString();
			}
		}
	}
}
