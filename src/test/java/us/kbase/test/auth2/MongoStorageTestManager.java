package us.kbase.test.auth2;

import static org.mockito.Mockito.mock;
import static us.kbase.test.auth2.TestCommon.destroyDB;
import static us.kbase.test.auth2.TestCommon.getMongoExe;
import static us.kbase.test.auth2.TestCommon.getTempDir;
import static us.kbase.test.auth2.TestCommon.isDeleteTempFiles;
import static us.kbase.test.auth2.TestCommon.stfuLoggers;
import static us.kbase.test.auth2.TestCommon.useWiredTigerEngine;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.Clock;

import org.bson.Document;

import com.github.zafarkhaja.semver.Version;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

import us.kbase.auth2.cryptutils.RandomDataGenerator;
import us.kbase.auth2.lib.storage.mongo.MongoStorage;
import us.kbase.testutils.controllers.mongo.MongoController;

public class MongoStorageTestManager {

	public MongoController mongo;
	public MongoClient mc;
	public MongoDatabase db;
	public MongoStorage storage;
	public RandomDataGenerator mockRand;
	public Clock mockClock;
	public Version mongoDBVer;
	public int indexVer;
	public boolean wiredTiger;
	
	public MongoStorageTestManager(final String dbName) throws Exception {
		stfuLoggers();
		mongo = new MongoController(getMongoExe().toString(),
				getTempDir(),
				useWiredTigerEngine());
		wiredTiger = useWiredTigerEngine();
		System.out.println(String.format("Testing against mongo executable %s on port %s",
				getMongoExe(), mongo.getServerPort()));
		mc = MongoClients.create("mongodb://localhost:" + mongo.getServerPort());
		db = mc.getDatabase(dbName);

		final Document bi = db.runCommand(new Document("buildinfo", 1));
		final String version = bi.getString("version");
		mongoDBVer = Version.valueOf(version);
		indexVer = mongoDBVer.greaterThanOrEqualTo(Version.forIntegers(3, 4)) ? 2 : 1;
		reset();
	}

	public void destroy() throws Exception {
		if (mc != null) {
			mc.close();
		}
		if (mongo != null) {
			try {
				mongo.destroy(isDeleteTempFiles());
			} catch (IOException e) {
				System.out.println("Error deleting temporarary files at: " +
						getTempDir());
				e.printStackTrace();
			}
		}
	}
	
	public void reset() throws Exception {
		destroyDB(db);
		// only drop the data, not the indexes, since creating indexes is slow and will be done
		// anyway when the new storage instance is created
		// db.drop();
		mockRand = mock(RandomDataGenerator.class);
		mockClock = mock(Clock.class);
		final Constructor<MongoStorage> con = MongoStorage.class.getDeclaredConstructor(
				MongoDatabase.class, RandomDataGenerator.class, Clock.class);
		con.setAccessible(true);
		storage = con.newInstance(db, mockRand, mockClock);
	}
}
