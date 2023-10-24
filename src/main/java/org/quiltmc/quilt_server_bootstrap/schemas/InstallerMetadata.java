/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.quiltmc.quilt_server_bootstrap.schemas;

import java.util.List;

public record InstallerMetadata(
	Loader loader,
	Intermediate hashed,
	Intermediate intermediary,
    LauncherMeta launcherMeta
) {
	public record Loader(
		String separator,
		int build,
		String maven,
		String version
	) {}

	public record Intermediate(
		String maven,
		String version
	) {}

	public record LauncherMeta(
		int version,
		Libraries libraries
	) {
		public record Libraries(
			List<Library> client,
			List<Library> common,
			List<Library> server
		) {}
	}

    public record Library(
        String name,
        String url
    ) {}
}
