package il.co.zipy.zipcodesgreece;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

public class Utils {
	private static final String LAST_FAILED_URL_FILENAME = "lastFailedUrl.txt";

	public static <T> void serialize(T object, String filename) {
		try {
			FileOutputStream fout = new FileOutputStream(filename);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(object);
		} catch (Exception e) {
			System.err.println("Error while serialising object");
			e.printStackTrace();
		}
	}

	public static <T> T deserialize(String filename) {
		try {
			FileInputStream fileInputStream = new FileInputStream(filename);
			ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
			Object o = objectInputStream.readObject();
			return (T) o;
		} catch (Exception e) {
			System.err.println("Failed to read object");
		}
		return null;
	}

	public static long initScreenShootsCounter() {
		File dir = new File(".");
		if (!dir.isDirectory()) return 0;

		File[] screenshoots = dir.listFiles(f -> f != null && f.exists() && f.isFile() && f.getName().endsWith(".png") && f.getName().startsWith("error"));
		if (screenshoots == null || screenshoots.length == 0) return 0;

		return Stream.of(screenshoots)
				.map(x -> Integer.parseInt(x.getName().substring(x.getName().indexOf("error") + 5, x.getName().length() - 4)))
				.max(Comparator.naturalOrder())
				.get();
	}

	public static void saveScreenshoot(WebDriver driver, long screenshotsNumber) {
		File src = ((TakesScreenshot)driver).getScreenshotAs(OutputType.FILE);
		try {
			Files.copy(Paths.get(src.getAbsolutePath()), Paths.get(new File("error" + screenshotsNumber + ".png").getAbsolutePath()));
		} catch (IOException ignored) {}
	}

	public static File getResourceFile(String path) throws FileNotFoundException {
		File result = new File(path);
		if (result.exists() && result.isFile()) return result;

		result = new File("src/main/" + path);
		if (result.exists() && result.isFile()) return result;

		throw new FileNotFoundException("ChromeDriver file is not found: " + path);
	}

	public static void saveLastUrl(String url) {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(LAST_FAILED_URL_FILENAME))) {
			writer.write(url);
		} catch (IOException e) {
			System.err.println("Failed to save last failed url to file");
			e.printStackTrace();
		}
	}

	public static String getLastUrl() {
		String result = null;
		try (BufferedReader reader = new BufferedReader(new FileReader(LAST_FAILED_URL_FILENAME))) {
			result = reader.readLine();
		} catch (IOException e) {
			System.err.println("Failed to read last failed url from file");
			e.printStackTrace();
		}

		return result;
	}
}
