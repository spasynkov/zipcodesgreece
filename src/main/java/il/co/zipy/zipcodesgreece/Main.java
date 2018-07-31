package il.co.zipy.zipcodesgreece;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
	private final WebDriver driver;
	private final WebDriverWait wait;
	private boolean tableUpdated = false;

	public Main() throws FileNotFoundException {
		String chromeDriverPath = "resources/chromedriver.exe";
		File file = getResourceFile(chromeDriverPath);

		System.setProperty("webdriver.chrome.driver", file.getAbsolutePath());

		driver = new ChromeDriver();
		wait = new WebDriverWait(driver, 10);
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
		List<ZipCodeDataHolder> result = new LinkedList<>();

		// collecting data
		List<String> perfecturesUrls = getLinksInDivByClassName("https://www.xo.gr/dir-tk/?lang=en", "perfecturesearches");
		closeCookiesInfoMessage();
		List<String> citiesUrl;
		for (String perfectureUrl : perfecturesUrls) {
			citiesUrl = getLinksInDivByClassName(perfectureUrl, "moreCities_content");
			for (String cityUrl : citiesUrl) {
				driver.get(cityUrl);
				List<ZipCodeDataHolder> dataFromTable = parsePage();
				result.addAll(dataFromTable);
			}
			doRandomWait();
		}

		driver.quit();

		// saving data
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

	private List<String> getLinksInDivByClassName(String url, String className) {
		System.out.println("Going to page " + url);
		driver.get(url);
		return driver.findElement(By.className(className)).findElements(By.tagName("a"))
				.stream().map(x -> x.getAttribute("href")).collect(Collectors.toList());
	}

	private List<ZipCodeDataHolder> parsePage() {
		List<ZipCodeDataHolder> result = new LinkedList<>();
		WebElement table;
		By tableLocator = By.id("zipTable");
		do {
			if (driver.getPageSource().contains("Are you a robot!")) {
				System.err.println("Robot alert occurs. Data could be doubled probably");
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ignored) {}
				driver.navigate().refresh();
			}

			if (tableUpdated) {
				wait.until(input -> ((JavascriptExecutor) input).executeScript("return document.readyState").equals("complete"));
				wait.until(ExpectedConditions.visibilityOfElementLocated(tableLocator));
				wait.until(ExpectedConditions.elementToBeClickable(tableLocator));
				try {
					Thread.sleep(5000);
				} catch (InterruptedException ignored) {}
			}

			table = driver.findElement(tableLocator);
			result.addAll(parseTable(table.findElement(By.tagName("tbody")).findElements(By.tagName("tr"))));
		} while (clickNext());
		System.out.println("Got " + result.size() + " element(s) in total from page\t" + driver.getCurrentUrl());
		return result;
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
		int fromInSeconds = 2;
		int toInSeconds = 10;

		int wait = (int) (fromInSeconds * 1000 + Math.random() * ((toInSeconds - fromInSeconds) * 1000));
		try {
			Thread.sleep(wait);
		} catch (InterruptedException ignored) { }
	}

	private boolean clickNext() {
		tableUpdated = false;
		try {
			//closeCookiesInfoMessage();
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
		} catch (Exception e) {}
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

	private class ZipCodeDataHolder {
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
	}
}
