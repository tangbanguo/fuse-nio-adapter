package org.cryptomator.frontend.fuse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class MirroringTest {

	private static final boolean IS_WIN = System.getProperty("os.name").contains("Windows");

	public static void main(String[] args) throws IOException {
		try (Scanner scanner = new Scanner(System.in)) {
			// System.out.println("Enter path to the directory you want to mirror:");
			// Path p = Paths.get(scanner.nextLine());
			Path p = Paths.get("/Users/sebastian/Desktop/FOO/a");
			int uid = (int) Files.getAttribute(p, "unix:uid");
			int gid = (int) Files.getAttribute(p, "unix:gid");
			// System.out.println("Enter mount point or free drive letter (J:\\) on Windows:");
			// Path m = Paths.get(scanner.nextLine());
			Path m = Paths.get("/Users/sebastian/Desktop/FOO/b");
			if (Files.isDirectory(p) && (IS_WIN || Files.isDirectory(m))) {
				try (FuseNioAdapter fs = AdapterFactory.createReadWriteAdapter(p, uid, gid)) {
					fs.mount(m, false, true);
					System.out.println("Mounted successfully. Enter anything to stop the server...");
					System.in.read();
					fs.umount();
					System.out.println("Unmounted successfully. Exiting...");
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				System.err.println("Invalid directory.");
			}
		}
	}

}
