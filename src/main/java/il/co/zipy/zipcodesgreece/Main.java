package il.co.zipy.zipcodesgreece;

import org.openqa.selenium.*;
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
	private transient long screenshotsCounter;
	private transient boolean areAllCitiesFound = false;

	public Main() throws FileNotFoundException {
		String chromeDriverPath = "resources/chromedriver.exe";
		File file = Utils.getResourceFile(chromeDriverPath);
		screenshotsCounter = Utils.initScreenShootsCounter();

		System.setProperty("webdriver.chrome.driver", file.getAbsolutePath());

		driver = new ChromeDriver();
		wait = new WebDriverWait(driver, 20);
	}

	public static void main(String[] args) throws FileNotFoundException {
		new Main().run();
	}

	private void run() {
		Map<String, Set<ZipCodeDataHolder>> pageUrlWithZipCodes = new HashMap<>();  // <url, zipCodes>

		List<String> citiesUrl = null;
		File file = new File(CITIES_URLS);
		if (file.exists() && file.isFile()) {
			citiesUrl = Utils.deserialize(file.getAbsolutePath());
			System.out.println("Loaded " + citiesUrl.size() + " cities urls from file " + CITIES_URLS);
		}

		if (citiesUrl == null || citiesUrl.isEmpty()) {
			citiesUrl = new LinkedList<>();
			do {
				collectCities(citiesUrl);
			} while (!areAllCitiesFound);
		}

		Collections.shuffle(citiesUrl);

		boolean isFinished = false;
		do {
			try {
				collectZipCodes(citiesUrl, pageUrlWithZipCodes);
				isFinished = true;
			} catch (Exception e) {
				e.printStackTrace();
				System.out.println("Saving " + pageUrlWithZipCodes.size() + " entries");
				Utils.serialize(pageUrlWithZipCodes, ADDRESSES);  // saving data to avoid getting it later
				doRandomWait(1000);
				Utils.saveScreenshoot(driver, screenshotsCounter++);
				Utils.saveLastUrl(driver.getCurrentUrl());
			}
		} while (!isFinished);

		driver.quit();

		// saving data
		final List<ZipCodeDataHolder> result = new LinkedList<>();
		pageUrlWithZipCodes.forEach((k, v) -> {
			result.addAll(v);
		});

		File resultFile = new File("greekzipcodes.txt");
		try (FileWriter fw = new FileWriter(resultFile, true)) {
			if (!file.exists() || (file.isFile() && file.length() == 0)) {
				fw.write("Prefecture;City;Street-Number;Postal Code" + System.lineSeparator());
			}
			result.forEach(c -> {
				try {
					fw.write(c.toString() + System.lineSeparator());
				} catch (IOException e) {
					System.err.print("Unable to write zipcode: " + c);
					e.printStackTrace();
				}
			});
		} catch (IOException e1) {
			System.err.println(e1.getMessage());
		}
	}

	private void collectZipCodes(List<String> citiesUrl, Map<String, Set<ZipCodeDataHolder>> result) {
		String lastUrl = Utils.getLastUrl();
		if (lastUrl != null) citiesUrl.add(lastUrl);

		// loading previous data
		if (result.isEmpty()) {
			File file = new File(ADDRESSES);
			if (file.exists() && file.isFile()) {
				Map<String, Set<ZipCodeDataHolder>> previousData = Utils.deserialize(file.getAbsolutePath());
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
			if (!result.containsKey(cityUrl)) {
				driver.get(cityUrl);
				Set<ZipCodeDataHolder> dataFromTable = parsePage();
				result.put(cityUrl, dataFromTable);
			}
		}

		// saving
		Utils.serialize(result, ADDRESSES);
	}



	private void collectCities(List<String> citiesUrl) {
		// collecting data
		List<String> perfecturesUrls = getLinksInDivByClassName("https://www.xo.gr/dir-tk/", "perfecturesearches");
		Collections.shuffle(perfecturesUrls);
		closeCookiesInfoMessage();
		for (String perfectureUrl : perfecturesUrls) {
			Set<String> citiesPagesUrls = collectCitiesPages(perfectureUrl);
			for (String cityPage : citiesPagesUrls) {
				avoidRobotAlert();
				citiesUrl.addAll(getLinksInDivByClassName(cityPage, "moreCities_content"));
			}

		}

		System.out.println("Collected " + citiesUrl.size() + " cities url");

		Utils.serialize(citiesUrl, CITIES_URLS);
		areAllCitiesFound = true;
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
			doRandomWait(200);
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
		int fromInSeconds = 1;
		int toInSeconds = 3;

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
