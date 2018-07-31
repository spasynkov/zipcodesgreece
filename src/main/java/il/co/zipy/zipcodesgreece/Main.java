package il.co.zipy.zipcodesgreece;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main implements Serializable {
	private transient final WebDriver driver;
	private transient final WebDriverWait wait;
	private transient boolean tableUpdated = false;

	private static final String CITIES_URLS = "citiesUrls.ser";
	private static final String ADDRESSES = "address.ser";

	public Main() throws FileNotFoundException {
		String chromeDriverPath = "resources/chromedriver.exe";
		File file = getResourceFile(chromeDriverPath);

		System.setProperty("webdriver.chrome.driver", file.getAbsolutePath());

		driver = new ChromeDriver();
		wait = new WebDriverWait(driver, 20);
	}

	private File getResourceFile(String path) throws FileNotFoundException {
		File result = new File(path);
		if (result.exists() && result.isFile()) return result;

		result = new File("src/main/" + path);
		if (result.exists() && result.isFile()) return result;

		throw new FileNotFoundException("ChromeDriver file is not found: " + path);
	}

	public static void main(String[] args) throws FileNotFoundException {
		new Main().run();
	}

	private void run() {
		Map<String, Set<ZipCodeDataHolder>> pageUrlWithZipCodes = new HashMap<>();  // <url, zipCodes>

		List<String> citiesUrl = null;
		File file = new File(CITIES_URLS);
		if (file.exists() && file.isFile()) {
			citiesUrl = deserialize(file.getAbsolutePath());
			System.out.println("Loaded " + citiesUrl.size() + " cities urls from file " + CITIES_URLS);
		}

		if (citiesUrl == null || citiesUrl.isEmpty()) {
			citiesUrl = collectCities();
		}

		Collections.shuffle(citiesUrl);

		boolean isFinished = false;
		try {
			collectZipCodes(citiesUrl, pageUrlWithZipCodes);
			isFinished = true;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Saving " + pageUrlWithZipCodes.size() + " entries");
			serialize(pageUrlWithZipCodes, ADDRESSES);  // saving data to avoid getting it later
		}

		driver.quit();

		if (!isFinished) return;

		// saving data
		final List<ZipCodeDataHolder> result = new LinkedList<>();
		pageUrlWithZipCodes.forEach((k, v) -> {
			result.addAll(v);
		});

		try (FileWriter fw = new FileWriter("greekzipcodes.txt", true)) {
			result.forEach(c -> {
				try {
					fw.write(c.toString());
				} catch (IOException e) {
					System.err.print("Unable to write zipcode: " + c);
				}
			});
		} catch (IOException e1) {
			System.err.println(e1.getMessage());
		}
	}

	private void collectZipCodes(List<String> citiesUrl, Map<String, Set<ZipCodeDataHolder>> result) {
		// loading previous data
		if (result.isEmpty()) {
			File file = new File(ADDRESSES);
			if (file.exists() && file.isFile()) {
				Map<String, Set<ZipCodeDataHolder>> previousData = deserialize(file.getAbsolutePath());
				if (previousData != null) {
					String key;
					Set<ZipCodeDataHolder> value;
					for (Map.Entry<String, Set<ZipCodeDataHolder>> pair : previousData.entrySet()) {
						key = pair.getKey();
						value = pair.getValue();
						citiesUrl.remove(key);
						result.put(key, value);
					}
					System.out.println("Loaded " + result.size() + " entries from file " + ADDRESSES);
				}
			}
		}

		// collecting new data
		for (String cityUrl : citiesUrl) {
			driver.get(cityUrl);
			Set<ZipCodeDataHolder> dataFromTable = parsePage();
			result.put(cityUrl, dataFromTable);
		}

		// saving
		serialize(result, ADDRESSES);
	}

	private <T> void serialize(T object, String filename) {
		try {
			FileOutputStream fout = new FileOutputStream(filename);
			ObjectOutputStream oos = new ObjectOutputStream(fout);
			oos.writeObject(object);
		} catch (Exception e) {
			System.err.println("Error while serialising object");
			e.printStackTrace();
		}
	}

	private <T> T deserialize(String filename) {
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

	private List<String> collectCities() {
		// collecting data
		List<String> perfecturesUrls = getLinksInDivByClassName("https://www.xo.gr/dir-tk/?lang=en", "perfecturesearches");
		Collections.shuffle(perfecturesUrls);
		closeCookiesInfoMessage();
		List<String> citiesUrl = new LinkedList<>();
		for (String perfectureUrl : perfecturesUrls) {
			Set<String> citiesPagesUrls = collectCitiesPages(perfectureUrl);
			for (String cityPage : citiesPagesUrls) {
				avoidRobotAlert();
				citiesUrl.addAll(getLinksInDivByClassName(cityPage, "moreCities_content"));
			}

		}

		System.out.println("Collected " + citiesUrl.size() + " cities url");

		serialize(citiesUrl, CITIES_URLS);
		return citiesUrl;
	}

	private Set<String> collectCitiesPages(String perfectureUrl) {
		Set<String> result = new HashSet<>();
		result.add(perfectureUrl);
		driver.get(perfectureUrl);
		try {
			for(WebElement e : driver.findElements(By.cssSelector("div[class=\"row pagination\"] a"))) {
				result.add(e.getAttribute("href"));
			}
		} catch (Exception ignored) {}

		return result;
	}

	private List<String> getLinksInDivByClassName(String url, String className) {
		System.out.println("Going to page " + url);
		driver.get(url);
		return driver.findElement(By.className(className)).findElements(By.tagName("a"))
				.stream().map(x -> x.getAttribute("href")).collect(Collectors.toList());
	}

	private Set<ZipCodeDataHolder> parsePage() {
		Set<ZipCodeDataHolder> result = new HashSet<>();
		WebElement table;
		By tableLocator = By.id("zipTable");
		do {
			avoidRobotAlert();

			if (tableUpdated) {
				wait.until(ExpectedConditions.visibilityOfElementLocated(tableLocator));
				wait.until(ExpectedConditions.elementToBeClickable(tableLocator));
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {
				}
			}

			table = driver.findElement(tableLocator);
			result.addAll(parseTable(table.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))));
		} while (clickNext());
		System.out.println("Got " + result.size() + " element(s) in total from page\t" + driver.getCurrentUrl());
		return result;
	}

	private void avoidRobotAlert() {
		wait.until(input -> ((JavascriptExecutor) input).executeScript("return document.readyState").equals("complete"));
		if (driver.getPageSource().contains("Are you a robot!")) {
			System.err.println("Robot alert occurs. Data could be doubled probably");
			doRandomWait(2000);
			driver.navigate().refresh();
		}
	}

	private List<ZipCodeDataHolder> parseTable(List<WebElement> table) {
		List<ZipCodeDataHolder> result = new LinkedList<>();

		for (WebElement row : table) {
			String streetAndNumber = row.findElement(By.xpath("./td[1]")).getText();
			String code = row.findElement(By.xpath("./td[2]")).getText();
			String city = row.findElement(By.xpath("./td[3]")).getText();
			String pref = row.findElement(By.xpath("./td[4]")).getText();
			result.add(new ZipCodeDataHolder(pref, city, streetAndNumber, code));
		}
		System.out.println("Added " + result.size() + " element(s) from the page\t" + driver.getCurrentUrl());

		doRandomWait();
		return result;
	}

	private void doRandomWait() {
		doRandomWait(0);
	}


	private void doRandomWait(long basicLatencyMillis) {
		int fromInSeconds = 3;
		int toInSeconds = 10;

		long wait = (int) (fromInSeconds * 1000 + Math.random() * ((toInSeconds - fromInSeconds) * 1000)) + basicLatencyMillis;
		try {
			Thread.sleep(wait);
		} catch (InterruptedException ignored) {
		}
	}

	private boolean clickNext() {
		tableUpdated = false;
		try {
			closeCookiesInfoMessage();
			List<WebElement> pages = driver.findElement(By.id("pager")).findElements(By.tagName("li"));
			WebElement link = findNextPage(pages).findElement(By.tagName("a"));
			//wait.until(ExpectedConditions.elementToBeClickable(link));
			link.click();
			tableUpdated = true;
			return true;
		} catch (NullPointerException e) {
			return false;
		} catch (Exception e) {
			if (e.getMessage().contains("unknown error")) {
				e.printStackTrace();
			}
			return false;
		}
	}

	private void closeCookiesInfoMessage() {
		try {
			driver.findElement(By.cssSelector("a[data-cc-event=\"click:dismiss\"]")).click();
			System.out.println("Cookies warning is closed");
		} catch (Exception e) {
		}
	}

	private WebElement findNextPage(List<WebElement> pages) {
		WebElement page;
		for (Iterator<WebElement> iterator = pages.iterator(); iterator.hasNext(); ) {
			page = iterator.next();
			if (page.getAttribute("class").contains("disabled")) {
				System.out.println("Clicking at page " + page.getText() + " in " + driver.getCurrentUrl());
				return iterator.hasNext() ? iterator.next() : null;
			}
		}
		return null;
	}

	private class ZipCodeDataHolder implements Serializable {
		private String prefecture;
		private String city;
		private String streetAndNumber;
		private String postalCode;

		public ZipCodeDataHolder(String prefecture, String city, String streetAndNumber, String postalCode) {
			super();
			this.prefecture = prefecture;
			this.city = city;
			this.streetAndNumber = streetAndNumber;
			this.postalCode = postalCode;
		}

		@Override
		public String toString() {
			return this.prefecture + ";" + this.city + ";" + this.streetAndNumber + ";" + this.postalCode + "\n";
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			ZipCodeDataHolder that = (ZipCodeDataHolder) o;
			return Objects.equals(prefecture, that.prefecture) &&
					Objects.equals(city, that.city) &&
					Objects.equals(streetAndNumber, that.streetAndNumber) &&
					Objects.equals(postalCode, that.postalCode);
		}

		@Override
		public int hashCode() {
			return Objects.hash(prefecture, city, streetAndNumber, postalCode);
		}
	}
}
