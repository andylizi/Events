package me.coley.event;

import me.coley.event.testevent.TestAlphaEvent;
import me.coley.event.testevent.TestBetaEvent;
import org.junit.*;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

/**
 * @author Andy Li
 */
public class AccessHelperTest {
	@Test
	// suppress warning for when compiling with Java 9+ (isAccessible() method is deprecated since Java 9)
	@SuppressWarnings("deprecation")
	public void testTrySetAccessible() throws ReflectiveOperationException {
		assumeTrue("trySetAccessible() only available to Java 9 or higher", AccessHelper.isAtLeastJava9());
		Method method = MemberClassA.class.getDeclaredMethod("privateMethod");
		assertFalse("method shouldn't accessible yet", method.isAccessible());
		assertTrue("trySetAccessible() returns false", AccessHelper.trySetAccessible(method));
		assertTrue("method should be accessible after trySetAccessible() returns true", method.isAccessible());
	}

	@Test
	public void testNarrowLookupClass() throws ReflectiveOperationException {
		MethodHandles.Lookup lookup = MethodHandles.lookup().in(AccessHelperTest.class);
		Method method = MemberClassA.class.getDeclaredMethod("privateMethod");

		MethodHandles.Lookup newLookup = AccessHelper.narrowLookupClass(lookup, method);
		assertEquals("lookupClass should change to MemberClassA", MemberClassA.class, newLookup.lookupClass());
		assertEquals("lookupModes shouldn't change", lookup.lookupModes(), newLookup.lookupModes());
	}

	@Test
	public void testNarrowLookupClass2() throws ReflectiveOperationException {
		MethodHandles.Lookup lookup = MethodHandles.lookup().in(MemberClassB.MemberClassC.class);
		Method method = MemberClassB.class.getDeclaredMethod("protectedMethod");

		MethodHandles.Lookup newLookup = AccessHelper.narrowLookupClass(lookup, method);
		assertEquals("lookupClass should change to MemberClassB", MemberClassB.class, newLookup.lookupClass());
		assertEquals("lookupModes shouldn't change", lookup.lookupModes(), newLookup.lookupModes());
	}

	@Test
	public void testNarrowLookupClass3() throws ReflectiveOperationException {
		MethodHandles.Lookup lookup = MethodHandles.lookup().in(MemberClassA.class);
		Method method = PackageListener.MemberClass.class.getDeclaredMethod("foo");

		MethodHandles.Lookup newLookup = AccessHelper.narrowLookupClass(lookup, method);
		assertEquals("lookupClass shouldn't change", lookup.lookupClass(), newLookup.lookupClass());
		assertEquals("lookupModes shouldn't change", lookup.lookupModes(), newLookup.lookupModes());
	}

	@Test
	public void testGetOutermostEnclosingClass() {
		assertEquals("MemberClassA's outermost enclosing class", AccessHelperTest.class,
				AccessHelper.getOutermostEnclosingClass(MemberClassA.class));
		assertEquals("MemberClassC's outermost enclosing class", AccessHelperTest.class,
				AccessHelper.getOutermostEnclosingClass(MemberClassB.MemberClassC.class));
		assertEquals("PackageListener.MemberClass's outermost enclosing class", PackageListener.class,
				AccessHelper.getOutermostEnclosingClass(PackageListener.MemberClass.class));
	}

	@Test
	public void testIsSamePackage() {
		assertTrue("Number and Integer should be in the same package",
				AccessHelper.isSamePackage(Number.class, Integer.class));
		assertTrue("Method and Field should be in the same package",
				AccessHelper.isSamePackage(Method.class, Field.class));
		assertFalse("List and Object shouldn't be in the same package",
				AccessHelper.isSamePackage(List.class, Object.class));
	}

	@Test
	public void testIsSamePackageMember() {
		assertTrue("MemberClassA and MemberClassB should be nestmate",
				AccessHelper.isSamePackageMember(MemberClassA.class, MemberClassB.class));
		assertFalse("MemberClassA and PackageListener.MemberClass shouldn't be nestmate",
				AccessHelper.isSamePackageMember(MemberClassA.class, PackageListener.MemberClass.class));
	}

	@Test
	public void testGetMethodsRecursively() {
		List<Method> methods = AccessHelper.getMethodsRecursively(InheritanceTestSampleC.class);
		assertEqualsIgnoreOrder("getMethodsRecursively(InheritanceTestSampleC.class)", Arrays.asList(
				"InheritanceTestSampleC.onEvent(TestBetaEvent)",
				"InheritanceTestSampleB.onEvent(Event)",
				"InheritanceTestSampleA.onAlphaEvent(TestAlphaEvent)"),
				mapMethodToName(methods));
	}

	private static List<String> mapMethodToName(List<Method> methods) {
		return methods.stream()
				.map(method -> String.format("%s.%s(%s)",
						method.getDeclaringClass().getSimpleName(),
						method.getName(),
						Arrays.stream(method.getParameterTypes())
								.map(Class::getSimpleName)
								.collect(Collectors.joining(", "))))
				.collect(Collectors.toList());
	}

	private static <T> void assertEqualsIgnoreOrder(String message, Collection<T> expected, Collection<T> actual) {
		if (expected == actual) return;
		if (expected.size() != actual.size() || !expected.containsAll(actual)) {
			assertEquals(message, expected, actual);
		}
	}

	static class MemberClassA {
		private void privateMethod() {
			fail("privateMethod() called");
		}
	}

	static class MemberClassB {
		protected void protectedMethod() {
			fail("protectedMethod() called");
		}

		static class MemberClassC {
			public void foo() {
				fail("foo() called");
			}
		}
	}

	static class InheritanceTestSampleA {
		public void onAlphaEvent(TestAlphaEvent event) {
			fail("InheritanceTestSampleA.onAlphaEvent(TestAlphaEvent) called");
		}

		public void onEvent(TestBetaEvent event) {
			fail("InheritanceTestSampleA.onEvent(TestBetaEvent) called");
		}

		public void onEvent(Event event) {
			fail("InheritanceTestSampleA.onEvent(Event) called");
		}
	}

	static class InheritanceTestSampleB extends InheritanceTestSampleA {
		@Override
		public void onEvent(Event event) {
			fail("InheritanceTestSampleB.onEvent(Event) called");
		}
	}

	static class InheritanceTestSampleC extends InheritanceTestSampleB {
		@Override
		public void onEvent(TestBetaEvent event) {
			fail("InheritanceTestSampleC.onEvent(TestBetaEvent) called");
		}
	}
}