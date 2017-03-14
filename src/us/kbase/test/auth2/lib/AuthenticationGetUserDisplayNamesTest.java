package us.kbase.test.auth2.lib;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import static us.kbase.test.auth2.TestCommon.set;
import static us.kbase.test.auth2.lib.AuthenticationTester.initTestMocks;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.Test;

import com.google.common.base.Optional;

import us.kbase.auth2.lib.AuthUser;
import us.kbase.auth2.lib.Authentication;
import us.kbase.auth2.lib.DisplayName;
import us.kbase.auth2.lib.EmailAddress;
import us.kbase.auth2.lib.Role;
import us.kbase.auth2.lib.UserDisabledState;
import us.kbase.auth2.lib.UserName;
import us.kbase.auth2.lib.UserSearchSpec;
import us.kbase.auth2.lib.exceptions.DisabledUserException;
import us.kbase.auth2.lib.exceptions.ErrorType;
import us.kbase.auth2.lib.exceptions.IllegalParameterException;
import us.kbase.auth2.lib.exceptions.InvalidTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchTokenException;
import us.kbase.auth2.lib.exceptions.NoSuchUserException;
import us.kbase.auth2.lib.exceptions.UnauthorizedException;
import us.kbase.auth2.lib.storage.AuthStorage;
import us.kbase.auth2.lib.token.HashedToken;
import us.kbase.auth2.lib.token.IncomingToken;
import us.kbase.auth2.lib.token.TokenType;
import us.kbase.test.auth2.TestCommon;
import us.kbase.test.auth2.lib.AuthenticationTester.TestMocks;

public class AuthenticationGetUserDisplayNamesTest {

	@Test
	public void getDisplayNamesSet() throws Exception {
		// includes test of removing root user
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		
		final Map<UserName, DisplayName> expected = new HashMap<>();
		expected.put(new UserName("foo"), new DisplayName("dfoo"));
		expected.put(new UserName("bar"), new DisplayName("dbar"));

		when(storage.getToken(token.getHashedToken()))
				.thenReturn(new HashedToken(UUID.randomUUID(), TokenType.LOGIN, null, "foobarhash",
						new UserName("foo"), Instant.now(), Instant.now()));
		
		when(storage.getUserDisplayNames(
				set(new UserName("foo"), new UserName("bar"), new UserName("***ROOT***"))))
			.thenReturn(expected);
				
		
		final Map<UserName, DisplayName> disp = auth.getUserDisplayNames(
				token, set(new UserName("foo"), new UserName("bar"), new UserName("***ROOT***")));
		
		assertThat("incorrect display names", disp, is(expected));
	}
	
	@Test
	public void getDisplayNamesEmptySet() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		
		when(storage.getToken(token.getHashedToken()))
				.thenReturn(new HashedToken(UUID.randomUUID(), TokenType.LOGIN, null, "foobarhash",
						new UserName("foo"), Instant.now(), Instant.now()));
		
		final Map<UserName, DisplayName> disp = auth.getUserDisplayNames(
				token, Collections.emptySet());
		
		assertThat("incorrect display names", disp, is(new HashMap<>()));
	}
	
	@Test
	public void getDisplayNamesSetFailNulls() throws Exception {
		final Authentication auth = initTestMocks().auth;
		
		failGetDisplayNamesSet(auth, null, Collections.emptySet(),
				new NullPointerException("token"));
		failGetDisplayNamesSet(auth, new IncomingToken("token"), null,
				new NullPointerException("userNames"));
		failGetDisplayNamesSet(auth, new IncomingToken("token"), set(new UserName("foo"), null),
				new NullPointerException("Null name in userNames"));
	}
	
	@Test
	public void getDisplayNamesSetFailBadToken() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		
		when(storage.getToken(token.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetDisplayNamesSet(auth, token, Collections.emptySet(), new InvalidTokenException());
	}
	
	@Test
	public void getDisplayNamesSetFailTooManyUsers() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");
		
		final Set<UserName> users = new HashSet<>();
		for (int i = 0; i < 10001; i++) {
			users.add(new UserName("u" + i));
		}
		
		when(storage.getToken(token.getHashedToken()))
				.thenReturn(new HashedToken(UUID.randomUUID(), TokenType.LOGIN, null, "foobarhash",
						new UserName("foo"), Instant.now(), Instant.now()));
		
		failGetDisplayNamesSet(auth, token, users,
				new IllegalParameterException("User count exceeds maximum of 10000"));
	}
	
	private void failGetDisplayNamesSet(
			final Authentication auth,
			final IncomingToken token,
			final Set<UserName> names,
			final Exception e) {
		try {
			auth.getUserDisplayNames(token, names);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}
	
	@Test
	public void getDisplayNamesSpec() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo").build();
		
		final Map<UserName, DisplayName> expected = new HashMap<>();
		expected.put(new UserName("foo"), new DisplayName("dfoo"));
		expected.put(new UserName("bar"), new DisplayName("dbar"));

		getDisplayNamesSpec(user, spec, expected);
	}
	
	@Test
	public void getDisplayNamesSpecWithRootAsAdmin() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.ADMIN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withIncludeRoot(true).build();
		
		final Map<UserName, DisplayName> expected = new HashMap<>();
		expected.put(new UserName("foo"), new DisplayName("dfoo"));
		expected.put(new UserName("bar"), new DisplayName("dbar"));
		expected.put(new UserName("***ROOT***"), new DisplayName("root"));

		getDisplayNamesSpec(user, spec, expected);
	}
	
	@Test
	public void getDisplayNamesSpecWithRootAsCreate() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.CREATE_ADMIN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withIncludeRoot(true).build();
		
		final Map<UserName, DisplayName> expected = new HashMap<>();
		expected.put(new UserName("foo"), new DisplayName("dfoo"));
		expected.put(new UserName("bar"), new DisplayName("dbar"));
		expected.put(new UserName("***ROOT***"), new DisplayName("root"));

		getDisplayNamesSpec(user, spec, expected);
	}
	
	@Test
	public void getDisplayNamesSpecWithRootAsRoot() throws Exception {
		final AuthUser user = new AuthUser(UserName.ROOT, new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.ROOT),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withIncludeRoot(true).build();
		
		final Map<UserName, DisplayName> expected = new HashMap<>();
		expected.put(new UserName("foo"), new DisplayName("dfoo"));
		expected.put(new UserName("bar"), new DisplayName("dbar"));
		expected.put(new UserName("***ROOT***"), new DisplayName("root"));

		getDisplayNamesSpec(user, spec, expected);
	}
	
	@Test
	public void getDisplayNamesSpecAllowedPermissions() throws Exception {
		final UserSearchSpec noprefix = UserSearchSpec.getBuilder().build();
		final UserSearchSpec custom = UserSearchSpec.getBuilder()
				.withSearchOnCustomRole("foo").build();
		final UserSearchSpec role = UserSearchSpec.getBuilder()
				.withSearchOnRole(Role.ADMIN).build();
		final UserSearchSpec disabled = UserSearchSpec.getBuilder()
				.withIncludeDisabled(true).build();
		
		for (final UserSearchSpec spec: Arrays.asList(noprefix, custom, role, disabled)) {
			for (final Role r: Arrays.asList(Role.ROOT, Role.CREATE_ADMIN, Role.ADMIN)) {
				final AuthUser user = new AuthUser(
						r == Role.ROOT ? UserName.ROOT : new UserName("foo"),
								new EmailAddress("f@g.com"),
						new DisplayName("foo"), Collections.emptySet(), set(r),
						Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
						new UserDisabledState());
				
				final Map<UserName, DisplayName> expected = new HashMap<>();
				expected.put(new UserName("foo"), new DisplayName("dfoo"));
				expected.put(new UserName("bar"), new DisplayName("dbar"));
				
				getDisplayNamesSpec(user, spec, expected);
				
			}
		}
	}
	
	@Test
	public void getDisplayNamesSpecFailNulls() throws Exception {
		final Authentication auth = initTestMocks().auth;
		
		failGetDisplayNamesSpec(auth, null, UserSearchSpec.getBuilder().build(),
				new NullPointerException("token"));
		
		failGetDisplayNamesSpec(auth, new IncomingToken("foo"),
				null, new NullPointerException("spec"));
	}
	
	@Test
	public void getDisplayNamesSpecFailRegex() throws Exception {
		final AuthUser user = new AuthUser(UserName.ROOT, new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.ROOT),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo").build();
		final Field m = spec.getClass().getDeclaredField("isRegex");
		m.setAccessible(true);
		m.set(spec, true);
		
		failGetDisplayNamesSpec(user, spec, new UnauthorizedException(ErrorType.UNAUTHORIZED,
				"Regex search is currently for internal use only"));
	}
	
	@Test
	public void getDisplayNamesFailBadToken() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");

		when(storage.getToken(token.getHashedToken())).thenThrow(new NoSuchTokenException("foo"));
		
		failGetDisplayNamesSpec(auth, token, UserSearchSpec.getBuilder().build(),
				new InvalidTokenException());
	}
	
	@Test
	public void getDisplayNamesFailCatastrophic() throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final IncomingToken token = new IncomingToken("foobar");

		when(storage.getToken(token.getHashedToken()))
				.thenReturn(new HashedToken(UUID.randomUUID(), TokenType.LOGIN, null, "foobarhash",
						new UserName("foo"), Instant.now(), Instant.now()));
		
		when(storage.getUser(new UserName("foo"))).thenThrow(new NoSuchUserException("foo"));
		
		failGetDisplayNamesSpec(auth, token, UserSearchSpec.getBuilder().build(),
				new RuntimeException("There seems to be an error " +
						"in the storage system. Token was valid, but no user"));
	}
	
	@Test
	public void getDisplayNamesSpecFailDisabled() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState("foo", new UserName("bar"), Instant.now()));
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo").build();
		
		failGetDisplayNamesSpec(user, spec, new DisabledUserException());
	}
	
	@Test
	public void getDisplayNamesSpecFailStdUserCustomRole() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withSearchOnCustomRole("foobar").build();
		
		failGetDisplayNamesSpec(user, spec, new UnauthorizedException(ErrorType.UNAUTHORIZED,
				"Only admins may search on roles"));
	}
	
	@Test
	public void getDisplayNamesSpecFailStdUserRole() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withSearchOnRole(Role.ADMIN).build();
		
		failGetDisplayNamesSpec(user, spec, new UnauthorizedException(ErrorType.UNAUTHORIZED,
				"Only admins may search on roles"));
	}
	
	@Test
	public void getDisplayNamesSpecFailStdUserNoPrefix() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().build();
		
		failGetDisplayNamesSpec(user, spec, new UnauthorizedException(ErrorType.UNAUTHORIZED,
				"Only admins may search without a prefix"));
	}
	
	@Test
	public void getDisplayNamesSpecFailStdUserIncludeRoot() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withIncludeRoot(true).build();
		
		failGetDisplayNamesSpec(user, spec, new UnauthorizedException(ErrorType.UNAUTHORIZED,
				"Only admins may search with root or disabled users included"));
	}
	
	@Test
	public void getDisplayNamesSpecFailStdUserIncludeDisabled() throws Exception {
		final AuthUser user = new AuthUser(new UserName("foo"), new EmailAddress("f@g.com"),
				new DisplayName("foo"), Collections.emptySet(), set(Role.DEV_TOKEN),
				Collections.emptySet(), Instant.now(), Optional.of(Instant.now()),
				new UserDisabledState());
		
		final UserSearchSpec spec = UserSearchSpec.getBuilder().withSearchPrefix("foo")
				.withIncludeDisabled(true).build();
		
		failGetDisplayNamesSpec(user, spec, new UnauthorizedException(ErrorType.UNAUTHORIZED,
				"Only admins may search with root or disabled users included"));
	}
	
	private void getDisplayNamesSpec(
			final AuthUser user,
			final UserSearchSpec spec,
			final Map<UserName, DisplayName> expected)
			throws Exception {
		final TestMocks testauth = initTestMocks();
		final AuthStorage storage = testauth.storageMock;
		final Authentication auth = testauth.auth;
		
		final Map<UserName, DisplayName> ret = new HashMap<>();
		ret.put(new UserName("foo"), new DisplayName("dfoo"));
		ret.put(new UserName("bar"), new DisplayName("dbar"));
		ret.put(new UserName("***ROOT***"), new DisplayName("root"));

		final IncomingToken token = new IncomingToken("foobar");

		when(storage.getToken(token.getHashedToken()))
				.thenReturn(new HashedToken(UUID.randomUUID(), TokenType.LOGIN, null, "foobarhash",
						user.getUserName(), Instant.now(), Instant.now()));
		
		when(storage.getUser(user.getUserName())).thenReturn(user);
		
		when(storage.getUserDisplayNames(spec, 10000)).thenReturn(ret);
		
		try {
			final Map<UserName, DisplayName> got = auth.getUserDisplayNames(token, spec);
		
			assertThat("incorrect display names", got, is(expected));
		} catch (Throwable th) {
			if (user.isDisabled()) {
				verify(storage).deleteTokens(user.getUserName());
			} else {
				verify(storage, never()).deleteTokens(user.getUserName());
			}
			throw th;
		}
	}
	
	private void failGetDisplayNamesSpec(
			final AuthUser user,
			final UserSearchSpec spec,
			final Exception e) {
		try {
			getDisplayNamesSpec(user, spec, null);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
	}

	private void failGetDisplayNamesSpec(
			final Authentication auth,
			final IncomingToken token,
			final UserSearchSpec spec,
			final Exception e) {
		try {
			auth.getUserDisplayNames(token, spec);
			fail("expected exception");
		} catch (Exception got) {
			TestCommon.assertExceptionCorrect(got, e);
		}
		// TODO Auto-generated method stub
		
	}
	
}
