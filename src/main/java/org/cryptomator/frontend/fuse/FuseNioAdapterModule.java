package org.cryptomator.frontend.fuse;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import java.nio.file.Path;

@Module
class FuseNioAdapterModule {

	private final Path root;
	private final int uid;
	private final int gid;

	FuseNioAdapterModule(Path root, int uid, int gid) {
		this.root = root;
		this.uid = uid;
		this.gid = gid;
	}

	@Provides
	@PerAdapter
	@Named("root")
	public Path provideRootPath() {
		return root;
	}

	@Provides
	@PerAdapter
	@Named("uid")
	public int provideUid() {
		return uid;
	}

	@Provides
	@PerAdapter
	@Named("gid")
	public int provideGid() {
		return gid;
	}

}
