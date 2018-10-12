package me.coley.event;

import me.coley.event.testevent.TestAlphaEvent;
import me.coley.event.testevent.TestBetaEvent;
import me.coley.event.testevent.TestDeltaEvent;
import me.coley.event.testevent.TestZetaEvent;
import org.junit.*;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
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
				"InheritanceTestSampleB.onEvent(TestDeltaEvent)",
				"InheritanceTestSampleA.onAlphaEvent(TestAlphaEvent)",
				"InheritanceTestSampleA.onZetaEvent(TestZetaEvent)",
				"InheritanceTestSampleA.onEvent(TestDeltaEvent)"),
				mapMethodsToNames(methods));
	}

	@Test
	public void testGetAnnotationRecursively() throws ReflectiveOperationException {
		testGetAnnotationRecursively0(AnnotationTestSampleC.class
				.getDeclaredMethod("onEvent", Event.class), 666);
		testGetAnnotationRecursively0(AnnotationTestSampleD.class
				.getDeclaredMethod("onAlphaEvent", TestAlphaEvent.class), 888);
		testGetAnnotationRecursively0(AnnotationTestSampleC.class
				.getDeclaredMethod("accept", TestDeltaEvent.class), 999);
	}

	private void testGetAnnotationRecursively0(Method method, int priority) {
		String name = methodToName(method);
		Listener listener = AccessHelper.getAnnotationRecursively(method, Listener.class);
		assertNotNull("getAnnotationRecursively(" + name + ") returns null", listener);
		assertEquals("Priority for " + name, priority, listener.priority());
	}

	@Test
	public void testComputeAllSupertypes() {
		assertEquals("computeAllSupertypes(InheritanceTestSampleC.class)", Arrays.asList(
				"InheritanceTestSampleB",
				"InheritanceTestSampleA",
				"Serializable"),
				mapClassesToNames(AccessHelper.computeAllSupertypes(InheritanceTestSampleC.class, new LinkedHashSet<>())));

		assertEquals("computeAllSupertypes(AnnotationTestSampleD.class)", Arrays.asList(
				"AnnotationTestSampleC",
				"AnnotationTestSampleA",
				"AnnotationTestSampleB",
				"Serializable",
				"Consumer"),
				mapClassesToNames(AccessHelper.computeAllSupertypes(AnnotationTestSampleD.class, new LinkedHashSet<>())));
	}

	@Test
	public void testIsDefaultLookUp() {
		assertTrue("defaultLookup()", AccessHelper.isDefaultLookup(AccessHelper.defaultLookup()));
		assertFalse("MethodHandles.publicLookup()", AccessHelper.isDefaultLookup(MethodHandles.publicLookup()));
	}

	private static List<String> mapMethodsToNames(Collection<Method> methods) {
		return methods.stream().map(AccessHelperTest::methodToName).collect(Collectors.toList());
	}

	private static List<String> mapClassesToNames(Collection<Class<?>> methods) {
		return methods.stream().map(Class::getSimpleName).collect(Collectors.toList());
	}

	private static String methodToName(Method method) {
		return String.format("%s.%s(%s)",
				method.getDeclaringClass().getSimpleName(),
				method.getName(),
				Arrays.stream(method.getParameterTypes())
						.map(Class::getSimpleName)
						.collect(Collectors.joining(", ")));
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

		public final void onZetaEvent(TestZetaEvent event) {
			fail("InheritanceTestSampleA.onZetaEvent(TestZetaEvent) called");
		}

		public void onEvent(TestBetaEvent event) {
			fail("InheritanceTestSampleA.onEvent(TestBetaEvent) called");
		}

		public static void onEvent(TestDeltaEvent event) {
			fail("InheritanceTestSampleA.onEvent(TestDeltaEvent) called");
		}

		public void onEvent(Event event) {
			fail("InheritanceTestSampleA.onEvent(Event) called");
		}
	}

	static class InheritanceTestSampleB extends InheritanceTestSampleA implements Serializable {
		@Override
		public void onEvent(Event event) {
			fail("InheritanceTestSampleB.onEvent(Event) called");
		}

		public static void onEvent(TestDeltaEvent event) {
			fail("InheritanceTestSampleB.onEvent(TestDeltaEvent) called");
		}
	}

	static class InheritanceTestSampleC extends InheritanceTestSampleB {
		@Override
		public void onEvent(TestBetaEvent event) {
			fail("InheritanceTestSampleC.onEvent(TestBetaEvent) called");
		}
	}

	interface AnnotationTestSampleA extends Consumer<TestDeltaEvent>, Serializable {
		@Listener(priority = 666)
		void onEvent(Event event);

		@Override
		@Listener(priority = 999)
		void accept(TestDeltaEvent event);
	}

	abstract static class AnnotationTestSampleB implements AnnotationTestSampleA {
		@Override
		public abstract void onEvent(Event event);

		@Listener(priority = 888)
		protected abstract void onAlphaEvent(TestAlphaEvent event);
	}

	abstract static class AnnotationTestSampleC extends AnnotationTestSampleB implements Serializable {
		@Override
		public void onEvent(Event event) {
			fail("AnnotationTestSampleC.onEvent() called");
		}

		@Override
		public void accept(TestDeltaEvent event) {
			fail("AnnotationTestSampleC.accept() called");
		}
	}

	static class AnnotationTestSampleD extends AnnotationTestSampleC implements AnnotationTestSampleA {
		@Override
		protected void onAlphaEvent(TestAlphaEvent event) {
			fail("AnnotationTestSampleD.onAlphaEvent() called");
		}
	}
}