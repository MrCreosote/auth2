package us.kbase.test.auth2;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.Document;
import org.ini4j.Ini;

import com.mongodb.client.MongoDatabase;

import difflib.DiffUtils;
import difflib.Patch;
import us.kbase.auth2.lib.Password;
import us.kbase.auth2.lib.TemporarySessionData;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.token.TemporaryToken;
import us.kbase.testutils.TestException;

public class TestCommon {

	public static final String MONGOEXE = "test.mongo.exe";
	public static final String MONGO_USE_WIRED_TIGER = "test.mongo.wired_tiger";
	
	public static final String TEST_TEMP_DIR = "test.temp.dir";
	public static final String KEEP_TEMP_DIR = "test.temp.dir.keep";
	
	public static final String TEST_CONFIG_FILE_PROP_NAME = "AUTH2_TEST_CONFIG";
	public static final String TEST_CONFIG_FILE_SECTION = "auth2test";
	
	public static final String REGEX_UUID =
			"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
	
	public static final String LONG101;
	public static final String LONG1001;
	static {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			sb.append("a");
		}
		final String s100 = sb.toString();
		final StringBuilder sb2 = new StringBuilder();
		for (int i = 0; i < 100; i++) {
			sb2.append(s100);
		}
		LONG101 = s100 + "a";
		LONG1001 = sb2.toString() + "a";
	}
	
	private static Map<String, String> testConfig = null;
	
	public static void stfuLoggers() {
		java.util.logging.Logger.getLogger("com.mongodb")
				.setLevel(java.util.logging.Level.OFF);
		// these don't work to shut off the jetty logger
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
				.setLevel(ch.qos.logback.classic.Level.OFF);
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("org.eclipse.jetty"))
				.setLevel(ch.qos.logback.classic.Level.OFF);
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("us.kbase"))
				.setLevel(ch.qos.logback.classic.Level.OFF);
		System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
		System.setProperty("us.kbase.LEVEL", "OFF");
		// these do work
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("us.kbase.auth2.service.exceptions.ExceptionHandler"))
				.setLevel(ch.qos.logback.classic.Level.OFF);
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("us.kbase.auth2.service.LoggingFilter"))
				.setLevel(ch.qos.logback.classic.Level.OFF);
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("us.kbase.auth2.lib.Authentication"))
				.setLevel(ch.qos.logback.classic.Level.OFF);
		((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
				.getLogger("nl.basjes.parse.useragent.UserAgentAnalyzer"))
				.setLevel(ch.qos.logback.classic.Level.OFF);
	}
	
	public static Instant inst(final long epochMillis) {
		return Instant.ofEpochMilli(epochMillis);
	}
	
	public static void assertExceptionCorrect(
			final Throwable got,
			final Throwable expected) {
		assertThat("incorrect exception. trace:\n" + ExceptionUtils.getStackTrace(got),
				got.getMessage(), is(expected.getMessage()));
		assertThat("incorrect exception type", got, instanceOf(expected.getClass()));
	}
	
	public static void assertExceptionMessageContains(
			final Exception got,
			final String expectedMessagePart) {
		assertThat("incorrect exception message. trace:\n" + ExceptionUtils.getStackTrace(got),
				got.getMessage(), containsString(expectedMessagePart));
	}
	
	/** See https://gist.github.com/vorburger/3429822
	 * Returns a free port number on localhost.
	 *
	 * Heavily inspired from org.eclipse.jdt.launching.SocketUtil (to avoid a
	 * dependency to JDT just because of this).
	 * Slightly improved with close() missing in JDT. And throws exception
	 * instead of returning -1.
	 *
	 * @return a free port number on localhost
	 * @throws IllegalStateException if unable to find a free port
	 */
	public static int findFreePort() {
		ServerSocket socket = null;
		try {
			socket = new ServerSocket(0);
			socket.setReuseAddress(true);
			int port = socket.getLocalPort();
			try {
				socket.close();
			} catch (IOException e) {
				// Ignore IOException on close()
			}
			return port;
		} catch (IOException e) {
		} finally {
			if (socket != null) {
				try {
					socket.close();
				} catch (IOException e) {
				}
			}
		}
		throw new IllegalStateException("Could not find a free TCP/IP port");
	}
	
	@SafeVarargs
	public static <T> Set<T> set(T... objects) {
		return new HashSet<T>(Arrays.asList(objects));
	}
	
	@SafeVarargs
	public static <T> List<T> list(T... objects) {
		return Arrays.asList(objects);
	}
	
	public static final Optional<String> ES = Optional.empty();
	
	public static <T> Optional<T> opt(final T obj) {
		return Optional.of(obj);
	}
	
	public static Instant now() {
		return Instant.now();
	}
	
	public static void assertClear(final byte[] bytes) {
		for (int i = 0; i < bytes.length; i++) {
			if (bytes[i] != 0) {
				fail(String.format("found non-zero byte at position %s: %s", i, bytes[i]));
			}
		}
	}
	
	public static void assertClear(final Password p) {
		assertClear(p.getPassword());
	}
	
	public static void assertClear(final char[] chars) {
		for (int i = 0; i < chars.length; i++) {
			if (chars[i] != '0') {
				fail(String.format("found char != '0' at postion %s: %s", i, chars[i]));
			}
		}
	}
	
	
	public static Path getMongoExe() {
		return Paths.get(getTestProperty(MONGOEXE)).toAbsolutePath().normalize();
	}

	public static Path getTempDir() {
		return Paths.get(getTestProperty(TEST_TEMP_DIR)).toAbsolutePath().normalize();
	}
	
	public static boolean isDeleteTempFiles() {
		return !"true".equals(getTestProperty(KEEP_TEMP_DIR));
	}

	public static boolean useWiredTigerEngine() {
		return "true".equals(getTestProperty(MONGO_USE_WIRED_TIGER));
	}
	
	private static String getTestProperty(final String propertyKey) {
		getTestConfig();
		final String prop = testConfig.get(propertyKey);
		if (prop == null || prop.trim().isEmpty()) {
			throw new TestException(String.format(
					"Property %s in section %s of test file %s is missing",
					propertyKey, TEST_CONFIG_FILE_SECTION, getConfigFilePath()));
		}
		return prop;
	}

	private static void getTestConfig() {
		if (testConfig != null) {
			return;
		}
		final Path testCfgFilePath = getConfigFilePath();
		final Ini ini;
		try {
			ini = new Ini(testCfgFilePath.toFile());
		} catch (IOException ioe) {
			throw new TestException(String.format(
					"IO Error reading the test configuration file %s: %s",
					testCfgFilePath, ioe.getMessage()), ioe);
		}
		testConfig = ini.get(TEST_CONFIG_FILE_SECTION);
		if (testConfig == null) {
			throw new TestException(String.format("No section %s found in test config file %s",
					TEST_CONFIG_FILE_SECTION, testCfgFilePath));
		}
	}

	private static Path getConfigFilePath() {
		final String testCfgFilePathStr = System.getProperty(TEST_CONFIG_FILE_PROP_NAME);
		if (testCfgFilePathStr == null || testCfgFilePathStr.trim().isEmpty()) {
			throw new TestException(String.format("Cannot get the test config file path." +
					" Ensure the java system property %s is set to the test config file location.",
					TEST_CONFIG_FILE_PROP_NAME));
		}
		return Paths.get(testCfgFilePathStr).toAbsolutePath().normalize();
	}
	
	public static void destroyDB(MongoDatabase db) {
		for (String name: db.listCollectionNames()) {
			if (!name.startsWith("system.")) {
				// dropping collection also drops indexes
				db.getCollection(name).deleteMany(new Document());
			}
		}
	}
	
	//http://quirkygba.blogspot.com/2009/11/setting-environment-variables-in-java.html
	@SuppressWarnings("unchecked")
	public static Map<String, String> getenv()
			throws NoSuchFieldException, SecurityException,
			IllegalArgumentException, IllegalAccessException {
		Map<String, String> unmodifiable = System.getenv();
		Class<?> cu = unmodifiable.getClass();
		Field m = cu.getDeclaredField("m");
		m.setAccessible(true);
		return (Map<String, String>) m.get(unmodifiable);
	}
	
	public static String getCurrentMethodName() {
		return Thread.currentThread().getStackTrace()[2].getMethodName();
	}
	
	public static void assertNoDiffs(final String got, final String expected) throws Exception {
		final Patch<String> diff = DiffUtils.diff(
				Arrays.asList(expected.split("\r\n?|\n")),
				Arrays.asList(got.split("\r\n?|\n")));
		assertThat("output does not match", diff.getDeltas(), is(Collections.emptyList()));
	}
	
	public static String getTestExpectedData(final Class<?> clazz, final String methodName)
			throws Exception {
		final String expectedFile = clazz.getSimpleName() + "_" + methodName + ".testdata";
		final InputStream is = clazz.getResourceAsStream(expectedFile);
		if (is == null) {
			throw new FileNotFoundException(expectedFile);
		}
		return IOUtils.toString(is);
	}
	
	public static String calculatePKCEChallenge(final String pkceVerifier) throws Exception{
		final MessageDigest digest = MessageDigest.getInstance("SHA-256");
		final byte[] hash = digest.digest(pkceVerifier.getBytes(StandardCharsets.UTF_8));
		final String prestrip = Base64.getUrlEncoder().encodeToString(hash);
		return prestrip.replace("=", "");
	}
	
	public static void assertCloseToNow(final long epochMillis) {
		final long now = Instant.now().toEpochMilli();
		assertThat(String.format("time (%s) not within 10000ms of now: %s", epochMillis, now),
				Math.abs(epochMillis - now) < 10000, is(true));
	}
	
	public static void assertCloseToNow(final Instant creationDate) {
		assertCloseToNow(creationDate.toEpochMilli());
	}
	
	public static void assertCloseTo(final long num, final long expected, final int range) {
		assertThat(String.format("number (%s) not within %s of target: %s",
				num, range, expected),
				Math.abs(expected - num) < range, is(true));
	}
	
	public static TemporaryToken tempToken(
			final UUID id,
			final Instant created,
			final long lifetimeMS,
			final String token)
			throws Exception {
		final TemporarySessionData data = TemporarySessionData.create(id, created, lifetimeMS)
				.link("fakestate", "fakepkce", new UserName("foo"));
		return new TemporaryToken(data, token);
	}
}
