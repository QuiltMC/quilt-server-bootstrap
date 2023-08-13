package org.quiltmc.quilt_server_bootstrap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.quiltmc.parsers.json.JsonReader;
import org.quiltmc.parsers.json.JsonToken;

public class AttemptSomething {
	enum Mode {
		UNKNOWN,
		KEY,
		VALUE,
		KV_END,
		OBJECT_END,
		ARRAY_VALUE,
	}

	// TODO - Move to Java 21 asap or else maintainability will be a nightmare
	public static void main(String[] args) throws IOException {
		var pathQJ = AttemptSomething.class.getResourceAsStream("/a.json");
		var reader = JsonReader.json(new InputStreamReader(pathQJ, StandardCharsets.UTF_8));

		boolean done = false;
		int depth = 1;
		var mode = Mode.UNKNOWN;
		var roots = new ArrayList<EnnyMapOrArray>();
		EnnyMapOrArray tree = new EnnyMapOrArray(new HashMap<>());
		//List<List<EnnyJsonElement>> otherArrays = new ArrayList<>();
		//List<EnnyJsonElement> array = null;
		//List<Boolean> objectOrArray = new ArrayList<>();
		var keys = new ArrayList<String>();


		while (reader.peek() != JsonToken.END_DOCUMENT && !done) {
			System.out.println(reader.peek() + " - " + mode + " - " + depth + " - " + keys);
			//System.out.println(roots);
			switch (mode) {
				case UNKNOWN -> {
					switch (reader.peek()) {
						case BEGIN_ARRAY -> {
							reader.beginArray();
							mode = Mode.ARRAY_VALUE;
							throw new IllegalStateException("Array at root isn't supported yet!");
						}
						case BEGIN_OBJECT -> {
							// TODO - You have an exciting conclusion!
							reader.beginObject();
							mode = Mode.KEY;
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
							tree.getMap().put(keys.get(keys.size() - 1), new EnnyJsonString(reader.nextString()));
							keys.remove(keys.get(keys.size() - 1));
							mode = Mode.KV_END;
						}
						case BOOLEAN -> {
							tree.getMap().put(keys.get(keys.size() - 1), new EnnyJsonBoolean(reader.nextBoolean()));
							keys.remove(keys.get(keys.size() - 1));
							mode = Mode.KV_END;
						}
						case NUMBER -> {
							tree.getMap().put(keys.get(keys.size() - 1), new EnnyJsonNumber(reader.nextNumber()));
							keys.remove(keys.get(keys.size() - 1));
							mode = Mode.KV_END;
						}
						case NULL -> {
							reader.nextNull();
							tree.getMap().put(keys.get(keys.size() - 1), null);
							keys.remove(keys.get(keys.size() - 1));
							mode = Mode.KV_END;
						}
						case BEGIN_OBJECT -> {
							reader.beginObject();
							roots.add(tree);
							tree = new EnnyMapOrArray(new HashMap<>());
							mode = Mode.KV_END;
							depth++;
						}
						case BEGIN_ARRAY -> {
							reader.beginArray();
							roots.add(tree);
							tree = new EnnyMapOrArray(new ArrayList<>());
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
							if (depth == 0) {
								mode = Mode.OBJECT_END;
								reader.endObject();
							} else {
								var lastRoot = roots.remove(roots.size() - 1);
								if (lastRoot.isMap()) {
									lastRoot.getMap().put(keys.get(keys.size() - 1), new EnnyJsonMap(tree.getMap()));
									keys.remove(keys.get(keys.size() - 1));
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
							System.out.println("Happy end!");
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
							tree = new EnnyMapOrArray(new HashMap<>());
							mode = Mode.KV_END;
							depth++;
						}
						case BEGIN_ARRAY -> {
							reader.beginArray();
							roots.add(tree);
							tree = new EnnyMapOrArray(new ArrayList<>());
							mode = Mode.ARRAY_VALUE;
							depth++;
						}
						case END_ARRAY -> {
							depth--;
							var lastRoot = roots.remove(roots.size() - 1);
							if (lastRoot.isMap()) {
								lastRoot.getMap().put(keys.get(keys.size() - 1), new EnnyJsonArray(tree.getList()));
								keys.remove(keys.get(keys.size() - 1));
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
		System.out.println(tree);
	}

	private sealed interface EnnyJsonElement {}

	private record EnnyJsonMap(Map<String, EnnyJsonElement> map) implements EnnyJsonElement {}

	private record EnnyJsonArray(List<EnnyJsonElement> list) implements EnnyJsonElement {}

	private record EnnyJsonString(String value) implements EnnyJsonElement {}

	private record EnnyJsonBoolean(boolean value) implements EnnyJsonElement {}

	private record EnnyJsonNumber(Number value) implements EnnyJsonElement {}

	private static class EnnyMapOrArray {
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
