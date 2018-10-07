package me.coley.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Objects;

import static java.lang.invoke.MethodHandles.Lookup;

/**
 * Access helper.
 *
 * @since 1.3
 */
final class AccessHelper {
	/**
	 * Default lookup object.
	 */
	private static final Lookup DEFAULT_LOOKUP = MethodHandles.lookup();

	/**
	 * MethodHandle to {@link AccessibleObject}#trySetAccessible().
	 */
	private static final MethodHandle TRY_SET_ACCESSIBLE_METHODHANDLE;

	/**
	 * Indicate whether we should try to bypass access control using {@code setAccessible(true)}.
	 * Testing purpose only.
	 */
	static boolean trySuppressAccessControl = true;

	/** Indicate whether we should pretend we are running on Java 1.8. Testing purpose only. */
	static boolean pretendJava8 = false;

	/** Indicate whether we should pretend we are running on Java 9. Testing purpose only. */
	static boolean pretendJava9 = false;

	static {
		MethodHandle methodhandle = null;
		try {
			@SuppressWarnings("JavaReflectionMemberAccess")
			Method method = AccessibleObject.class.getMethod("trySetAccessible");
			methodhandle = MethodHandles.lookup().unreflect(method);
		} catch (NoSuchMethodException ignore) {
			// Java 1.8 or lower doesn't have trySetAccessible() method
		} catch (ReflectiveOperationException ex) {
			throw new ExceptionInInitializerError(ex);
		}
		TRY_SET_ACCESSIBLE_METHODHANDLE = methodhandle;
	}

	/**
	 * Makes a direct method handle to the {@code method}.
	 *
	 * @param lookup the {@linkplain Lookup lookup object} used in method handle creation
	 * @return a method handle which can invoke the reflected method
	 * @throws SecurityException if access checking fails and cannot be bypassed
	 * @see Lookup#unreflect(Method)
	 */
	public static MethodHandle unreflectMethodHandle(Lookup lookup, Method method) throws SecurityException {
		try {
			return unreflectMethodHandle0(lookup, method);
		} catch (IllegalAccessException ex) {
			throw new SecurityException(String.format("Cannot access method [%s] with %s lookup [%s]",
					method.toGenericString(),
					AccessHelper.isDefaultLookup(lookup) ? "default" : "provided",
					lookup), ex);
		}
	}

	/**
	 * Makes a direct method handle to the {@code method}.
	 *
	 * @param lookup the {@linkplain Lookup lookup object} used in method handle creation
	 * @return a method handle which can invoke the reflected method
	 * @throws IllegalAccessException if access checking fails and cannot be bypassed
	 * @see Lookup#unreflect(Method)
	 */
	static MethodHandle unreflectMethodHandle0(Lookup lookup, Method method) throws IllegalAccessException {
		boolean accessible = method.isAccessible();
		if ((isAtLeastJava9() && !pretendJava8) || pretendJava9) {  // Java 9+
			try {
				return lookup.unreflect(method);
			} catch (IllegalAccessException ex) {
				if (accessible) throw ex; // Already accessible but still failed? Giving up.

				boolean tryAgain = false;
				Lookup newLookup = specifyLookupClass(lookup, method);
				if (lookup != newLookup) {
					if (lookup.lookupModes() == newLookup.lookupModes()) {
						lookup = newLookup;
						tryAgain = true;
					} else try { // lookupMode downgraded
						return newLookup.unreflect(method);
					} catch (IllegalAccessException ex2) {
						ex2.addSuppressed(ex);
						ex = ex2;
					}
				}

				if (trySuppressAccessControl) try {
					tryAgain |= trySetAccessible(method);
				} catch (SecurityException ex2) {
					try {
						Throwable t = ex;
						while (t.getCause() != null) t = t.getCause();
						t.initCause(ex2);  // set the root cause of `ex` to `ex2`
					} catch (IllegalStateException ex3) {
						ex.addSuppressed(ex2);
					}
				}

				if (tryAgain) {
					return lookup.unreflect(method);
				} else {
					throw ex;
				}
			}
		} else {  // Java 1.8 or lower
			Exception suppressed = null;
			if (trySuppressAccessControl && !accessible) try {
				method.setAccessible(true);
				accessible = true;
			} catch (SecurityException ex) {
				// if the method is public, then setAccessible() is not necessary, we can safely ignore this;
				// if the method is not public, the SecurityException will be thrown
				// as a suppressed exception when calling lookup.unreflect().
				suppressed = ex;
			}

			if (!accessible) {
				Lookup newLookup = specifyLookupClass(lookup, method);
				if (lookup != newLookup) {
					if (lookup.lookupModes() == newLookup.lookupModes()) {
						lookup = newLookup;
					} else try {  // lookupMode downgraded
						return newLookup.unreflect(method);
					} catch (IllegalAccessException ex) {
						if (suppressed != null) suppressed.addSuppressed(ex);
						else suppressed = ex;
					}
				}
			}

			try {
				return lookup.unreflect(method);
			} catch (Throwable t) {
				if (suppressed != null) t.addSuppressed(suppressed);
				throw t;
			}
		}
	}

	static Lookup specifyLookupClass(Lookup lookup, Method method) {
		Class<?> lookupClass = lookup.lookupClass();
		Class<?> targetClass = method.getDeclaringClass();
		if (lookupClass != targetClass && isSamePackageMember(lookupClass, targetClass)) {
			// targetClass and lookupClass are nestmate, the lookupMode will not change
			return lookup.in(targetClass);
		}
		return lookup;
	}

	/**
	 * Sets the {@code accessible} flag for the specified reflected object to true if possible.
	 *
	 * @return {@code true} if the {@code accessible} flag is set to {@code true};
	 *         {@code false} if access cannot be enabled.
	 * @throws UnsupportedOperationException if the current runtime doesn't have trySetAccessible() method
	 * @throws SecurityException             if the request is denied by the security manager
	 */
	public static boolean trySetAccessible(AccessibleObject object)
			throws UnsupportedOperationException, SecurityException {
		if (!isAtLeastJava9()) throw new UnsupportedOperationException(
				new NoSuchMethodException(AccessibleObject.class.getName() + ".trySetAccessible()"));

		try {
			return (boolean) TRY_SET_ACCESSIBLE_METHODHANDLE.invoke(object);
		} catch (WrongMethodTypeException | ClassCastException ex) {
			throw new AssertionError(ex);
		} catch (RuntimeException ex) {
			throw ex;
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Checks if the current runtime version is Java 9 or higher.
	 *
	 * @return {@code true} if the current runtime version is at least Java 9, {@code false} otherwise
	 */
	public static boolean isAtLeastJava9() {
		return TRY_SET_ACCESSIBLE_METHODHANDLE != null;
	}

	/**
	 * Checks if two classes is in the same package.
	 *
	 * @return {@code true} if they're in the same package, {@code false} otherwise
	 */
	public static boolean isSamePackage(Class<?> cls1, Class<?> cls2) {
		return cls1 == cls2 || Objects.equals(cls1.getPackage().getName(), cls2.getPackage().getName());
	}

	/**
	 * Checks if two classes are nestmate.
	 *
	 * @return {@code true} if they're nestmate, {@code false} otherwise
	 */
	public static boolean isSamePackageMember(Class<?> cls1, Class<?> cls2) {
		return isSamePackage(cls1, cls2) && getOutermostEnclosingClass(cls1) == getOutermostEnclosingClass(cls2);
	}

	/**
	 * Returns the outermost enclosing class of the specified class.
	 *
	 * @return the outermost enclosing class
	 */
	public static Class<?> getOutermostEnclosingClass(Class<?> cls) {
		Class<?> enclosingClass;
		while ((enclosingClass = cls.getEnclosingClass()) != null) cls = enclosingClass;
		return cls;
	}

	/**
	 * Returns the default {@linkplain Lookup lookup object}.
	 */
	static Lookup defaultLookup() {
		return DEFAULT_LOOKUP;
	}

	/**
	 * Checks if the specified {@linkplain Lookup lookup object} is the
	 * {@linkplain #defaultLookup() default lookup object}.
	 *
	 * @return {@code true} if the {@code lookup} parameter is the same object
	 *         as {@linkplain #defaultLookup() default lookup object}, {@code false} otherwise
	 * @see #defaultLookup()
	 */
	static boolean isDefaultLookup(Lookup lookup) {
		return lookup == DEFAULT_LOOKUP;
	}

	private AccessHelper() {}
}
