package me.coley.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Represents an event bus.
 *
 * @author Matt
 */
public class EventBus {
	/**
	 * Map of event classes to event distribution handlers.
	 */
	protected final Map<Class<? extends Event>, Handler> eventToHandler = new HashMap<>();

	/**
	 * Map of listener objects to listener invokers.
	 */
	protected final Map<Object, Set<InvokeWrapper>> listenerToInvokers = new HashMap<>();

	/**
	 * Register all listener methods on {@code object} for receiving events.
	 *
	 * @param object object whose listener methods should be registered
	 * @param lookup the {@linkplain MethodHandles.Lookup Lookup} object used in {@link MethodHandle} creation
	 * @throws IllegalArgumentException if the {@code object} was previously registered
	 *                                  or there's an invalid listener method on the {@code object}
	 * @since 1.3
	 */
	public void subscribe(Object object, MethodHandles.Lookup lookup) throws IllegalArgumentException, SecurityException {
		if (listenerToInvokers.containsKey(Objects.requireNonNull(object))) {
			throw new IllegalArgumentException("Listener already registered: " + object);
		}

		Set<InvokeWrapper> invokers = getInvokers(object, lookup);
		listenerToInvokers.put(object, invokers);
		for (InvokeWrapper invoker : invokers) {
			getHandler(invoker.eventType).subscribe(invoker);
		}
	}

	/**
	 * Register all listener methods on {@code object} for receiving events.
	 *
	 * @param object object whose listener methods should be registered
	 * @throws IllegalArgumentException if the {@code object} was previously registered
	 *                                  or there's an invalid listener method on the {@code object}
	 */
	public void subscribe(Object object) throws IllegalArgumentException, SecurityException {
		subscribe(object, MethodHandles.lookup());
	}

	/**
	 * Unregister all listener methods on the {@code object}.
	 *
	 * @param object object whose listener methods should be unregistered
	 */
	public void unsubscribe(Object object) {
		Objects.requireNonNull(object);
		Set<InvokeWrapper> invokers = listenerToInvokers.remove(object);
		if (invokers == null || invokers.isEmpty()) {
			return; // do nothing
		}

		for (InvokeWrapper invoker : invokers) {
			getHandler(invoker.eventType).unsubscribe(invoker);
		}
	}

	/**
	 * Posts an event to all registered listeners.
	 *
	 * @param event event to post
	 */
	public void post(Event event) {
		getHandler(Objects.requireNonNull(event, "event").getClass()).post(event);
	}

	/**
	 * Gets all listener methods on the {@code object}.
	 *
	 * @param lookup the {@linkplain MethodHandles.Lookup Lookup} object used in {@link MethodHandle} creation
	 * @throws IllegalArgumentException if there's an invalid listener method on the {@code object}
	 */
	protected Set<InvokeWrapper> getInvokers(Object object, MethodHandles.Lookup lookup)
			throws IllegalArgumentException, SecurityException {
		Set<InvokeWrapper> result = new LinkedHashSet<>();
		for (Method method : object.getClass().getDeclaredMethods()) {
			if (method.isAnnotationPresent(Listener.class)) {
				checkListenerMethod(method);
				result.add(InvokeWrapper.create(object, method, lookup));
			}
		}
		return result;
	}

	/**
	 * Retreives handler for the given event class.
	 *
	 * @return handler for event type
	 */
	protected Handler getHandler(Class<? extends Event> eventClass) {
		return eventToHandler.computeIfAbsent(eventClass, Handler::new);
	}

	/**
	 * Checks if the method is a valid listener method.
	 *
	 * @throws IllegalArgumentException if any check failed
	 */
	protected void checkListenerMethod(Method method) throws IllegalArgumentException {
		if (!method.isAnnotationPresent(Listener.class)) {
			throw new IllegalArgumentException("Needs @Listener annotation: " + method.toGenericString());
		}

		if (Modifier.isStatic(method.getModifiers())) {
			throw new IllegalArgumentException("Method cannot be static: " + method.toGenericString());
		}

		Class<?>[] params = method.getParameterTypes();
		if (params.length != 1) {
			throw new IllegalArgumentException("Must have exactly one parameter: " + method.toGenericString());
		}
		if (!Event.class.isAssignableFrom(params[0])) {
			throw new IllegalArgumentException("Parameter must be a subclass of the Event class: " + method.toGenericString());
		}
		if (Modifier.isAbstract(params[0].getModifiers())) {
			throw new IllegalArgumentException("Parameter type cannot be an abstract class or interface: " + method.toGenericString());
		}
	}

	/**
	 * Event distribution handler.
	 *
	 * @author Matt
	 */
	static class Handler {
		/**
		 * Event type for this handler.
		 */
		private final Class<? extends Event> eventType;

		/**
		 * Set of {@linkplain InvokeWrapper invokers} registered in this handler.
		 */
		private final NavigableSet<InvokeWrapper> invokers = new TreeSet<>(InvokeWrapper.COMPARATOR);

		/**
		 * Cache for {@code invokers}.
		 */
		@SuppressWarnings("VolatileArrayField")
		private transient volatile Object[] cache = null;

		Handler(Class<? extends Event> eventType) { this.eventType = eventType; }

		/**
		 * Adds an {@linkplain InvokeWrapper invoker} to this handler.
		 *
		 * @return {@code true} if this handler did not already contain the specified invoker
		 */
		public boolean subscribe(InvokeWrapper invoker) {
			return invalidateCache(invokers.add(invoker));
		}

		/**
		 * Removes the specified {@linkplain InvokeWrapper invoker} from this handler if it's present.
		 *
		 * @return {@code true} if this handler contained the specified invoker
		 */
		public boolean unsubscribe(InvokeWrapper invoker) {
			return invalidateCache(invokers.remove(invoker));
		}

		/**
		 * Posts an event to all registered listeners in this handler.
		 *
		 * @param event event to post
		 */
		public void post(Event event) {
			// Prevent ConcurrentModificationException
			// in cases where a registered item may register more items.
			Object[] invokerArray = cache;
			if (invokerArray == null) {
				synchronized (this) {
					if ((invokerArray = cache) == null) {
						invokerArray = cache = invokers.toArray();
					}
				}
			}

			for (Object invoker : invokerArray) {
				((InvokeWrapper) invoker).invoke(event);
			}
		}

		/**
		 * @return event type for this handler
		 */
		public Class<? extends Event> eventType() {
			return eventType;
		}

		/**
		 * Invalidates the {@code invokers} cache when {@code modified} is {@code true}.
		 *
		 * @param modified should invalidate?
		 * @return same value as {@code modified}
		 */
		boolean invalidateCache(boolean modified) {
			if (modified) this.cache = null;
			return modified;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof Handler)) return false;
			return Objects.equals(eventType, ((Handler) o).eventType);
		}

		@Override
		public int hashCode() {
			return eventType.hashCode();
		}

		@Override
		public String toString() {
			return String.format("Handler{%s}", eventType.getName());
		}
	}

	/**
	 * Listener method invocation wrapper.
	 *
	 * @author Matt
	 */
	static class InvokeWrapper implements Comparable<InvokeWrapper> {
		public static final Comparator<InvokeWrapper> COMPARATOR = (o1, o2) -> {
			if (fastEqual(o1, o2)) return 0;

			// @formatter:off
			int c;
			if ((c = Integer.compare(o1.priority, o2.priority))                         != 0) return c;
			if ((c = o1.method.getName().compareTo(o2.method.getName()))                != 0) return c;
			if ((c = o1.eventType.getName().compareTo(o2.eventType.getName()))          != 0) return c;
			if ((c = Integer.compare(o1.listener.hashCode(), o2.listener.hashCode()))   != 0) return c;
			if ((c = Integer.compare(o1.hashCode(), o2.hashCode()))                     != 0) return c;
			// @formatter:on
			throw new AssertionError();  // ensures the comparator will never return 0 if the two wrapper aren't equal
		};

		/**
		 * Constructs an InvokeWrapper.
		 */
		@SuppressWarnings("unchecked")
		public static InvokeWrapper create(Object instance, Method method, MethodHandles.Lookup lookup) {
			Class<? extends Event> eventType = (Class<? extends Event>) method.getParameterTypes()[0];
			int priority = method.getDeclaredAnnotation(Listener.class).priority();
			try {
				MethodHandle methodHandle = lookup.unreflect(method);
				return new InvokeWrapper(instance, eventType, method, priority, methodHandle);
			} catch (IllegalAccessException e) {
				throw new SecurityException("Unable to create MethodHandle: " + method.toGenericString(), e);
			}
		}

		/**
		 * Listener instance. Used in invocation.
		 */
		private final Object listener;

		/**
		 * Event type which the {@code listener} listens.
		 */
		private final Class<? extends Event> eventType;

		/**
		 * Listener method.
		 */
		private final Method method;

		/**
		 * Listener priority. Lower values are called first.
		 */
		private final int priority;

		/**
		 * {@link MethodHandle} for invocation.
		 */
		private final MethodHandle methodHandle;

		InvokeWrapper(Object listener, Class<? extends Event> eventType, Method method, int priority, MethodHandle methodHandle) {
			this.listener = listener;
			this.eventType = eventType;
			this.method = method;
			this.priority = priority;
			this.methodHandle = methodHandle;
		}

		/**
		 * Invokes the listener.
		 *
		 * @param event event to post
		 * @throws RuntimeException if the underlying listener method throws an exception
		 */
		public void invoke(Event event) throws RuntimeException {
			try {
				methodHandle.invoke(listener, event);
			} catch (Throwable e) {
				throw new RuntimeException("Exception while invoking listener", e);
			}
		}

		/**
		 * Compares two InvokeWrappers using their {@code priority} value.
		 */
		@Override
		public int compareTo(InvokeWrapper o) {
			return COMPARATOR.compare(this, o);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof InvokeWrapper)) return false;
			return fastEqual(this, (InvokeWrapper) o);
		}

		@Override
		public int hashCode() {
			int n = 1;
			n = 31 * n + listener.hashCode();
			n = 31 * n + eventType.hashCode();
			n = 31 * n + method.hashCode();
			return n;
		}

		private static boolean fastEqual(InvokeWrapper o1, InvokeWrapper o2) {
			return Objects.equals(o1.listener, o2.listener) &&
					Objects.equals(o1.eventType, o2.eventType) &&
					Objects.equals(o1.method, o2.method);
		}

		@Override
		public String toString() {
			return String.format("InvokeWrapper{listener=%s, eventType=%s, method=%s(%s), priority=%d}",
					listener, eventType.getName(), method.getName(), eventType.getSimpleName(), priority);
		}
	}
}
