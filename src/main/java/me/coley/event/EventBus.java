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
	 * Map of listener objects to listener invokers.
	 */
	protected final Map<Object, Set<InvokeWrapper>> listenerToInvokers = new HashMap<>();

	/**
	 * Handler registry.
	 */
	protected final HandlerRegistry handlerRegistry = new HandlerRegistry();

	/**
	 * Default lookup object used in {@link #subscribe(Object)}.
	 */
	protected MethodHandles.Lookup defaultLookup = AccessHelper.defaultLookup();

	/**
	 * Registers all listener methods on {@code object} for receiving events.
	 *
	 * @param object object whose listener methods should be registered
	 * @param lookup the {@linkplain MethodHandles.Lookup Lookup object} used in {@link MethodHandle} creation
	 * @throws IllegalArgumentException if the {@code object} was previously registered
	 *                                  or there's an invalid listener method on the {@code object}
	 * @throws SecurityException        if a security manager denied access to the declared methods
	 *                                  of the class of the {@code object}, or the provided
	 *                                  {@linkplain MethodHandles.Lookup lookup object}
	 *                                  cannot access one of the listener method found in the class
	 * @see MethodHandles.Lookup
	 * @since 1.3
	 */
	public void subscribe(Object object, MethodHandles.Lookup lookup) throws IllegalArgumentException, SecurityException {
		if (listenerToInvokers.containsKey(Objects.requireNonNull(object))) {
			throw new IllegalArgumentException("Listener already registered: " + object);
		}

		Set<InvokeWrapper> invokers = getInvokers(object, lookup);
		listenerToInvokers.put(object, invokers);
		for (InvokeWrapper invoker : invokers) {
			handlerRegistry.getHandler(invoker.eventType).subscribe(invoker);
		}
	}

	/**
	 * Registers all listener methods on {@code object} for receiving events.
	 *
	 * @param object object whose listener methods should be registered
	 * @throws IllegalArgumentException if the {@code object} was previously registered
	 *                                  or there's an invalid listener method on the {@code object}
	 * @throws SecurityException        if a security manager denied access to the declared methods
	 *                                  of the class of the {@code object}, or the default
	 *                                  {@linkplain MethodHandles.Lookup lookup object} cannot access
	 *                                  one of the listener method found in the class
	 * @see #subscribe(Object, MethodHandles.Lookup)
	 * @see #setDefaultLookup(MethodHandles.Lookup)
	 */
	public void subscribe(Object object) throws IllegalArgumentException, SecurityException {
		subscribe(object, defaultLookup);
	}

	/**
	 * Unregisters all listener methods on the {@code object}.
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
			handlerRegistry.getHandler(invoker.eventType).unsubscribe(invoker);
		}
	}

	/**
	 * Posts an event to all registered listeners.
	 *
	 * @param event event to post
	 */
	public void post(Event event) {
		handlerRegistry.getHandler(Objects.requireNonNull(event, "event").getClass())
				.post(event);
	}

	/**
	 * Sets default {@linkplain MethodHandles.Lookup lookup object} used in {@link #subscribe(Object)}.
	 *
	 * @param lookup new default lookup object
	 * @since 1.3
	 */
	public void setDefaultLookup(MethodHandles.Lookup lookup) {
		this.defaultLookup = Objects.requireNonNull(lookup);
	}

	/**
	 * Gets all listener methods on the {@code object}.
	 *
	 * @param lookup the {@linkplain MethodHandles.Lookup Lookup object} used in {@link MethodHandle} creation
	 * @throws IllegalArgumentException if there's an invalid listener method on the {@code object}
	 * @throws SecurityException        if a security manager denied access to the declared methods
	 *                                  of the class of the {@code object}, or the provided
	 *                                  {@linkplain MethodHandles.Lookup lookup}
	 *                                  cannot access one of the listener method found in the class
	 */
	protected static Set<InvokeWrapper> getInvokers(Object object, MethodHandles.Lookup lookup)
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
	 * Checks if the method is a valid listener method.
	 *
	 * @throws IllegalArgumentException if any check failed
	 */
	protected static void checkListenerMethod(Method method) throws IllegalArgumentException {
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
	}

	/**
	 * Handler registry (for supertype event handling).
	 */
	static final class HandlerRegistry {
		/**
		 * Map of all registered handlers.
		 */
		private final Map<Class<? extends Event>, Handler> handlers = new HashMap<>();

		/**
		 * Gets or creates the {@linkplain Handler handler} for the specified event type.
		 *
		 * @param type the event type
		 * @return the handler for the event type
		 */
		public Handler getHandler(Class<? extends Event> type) {
			Handler handler = handlers.get(type);
			if (handler == null) {
				computeHierarchy(handler = new Handler(type));
				handlers.put(type, handler);
			}
			return handler;
		}

		/**
		 * Computes and updates the registry's handler hierarchy with the specified handler.
		 *
		 * @return {@code true} if the specified {@linkplain Handler handler} has
		 *         an association(subtype or supertype) with at least one handler
		 *         in the current registry <b>and</b> the hierarchy had been updated,
		 *         {@code false} otherwise
		 */
		boolean computeHierarchy(Handler subject) {
			boolean associationFound = false;
			for (Handler handler : handlers.values()) {
				if (subject == handler) continue;
				if (subject.isSubtypeOf(handler)) {
					associationFound |= subject.addSupertypeHandler(handler);
				} else if (handler.isSubtypeOf(subject)) {
					associationFound |= handler.addSupertypeHandler(subject);
				}
			}
			return associationFound;
		}
	}

	/**
	 * Event distribution handler.
	 */
	static class Handler {
		/**
		 * Event type for this handler.
		 */
		private final Class<? extends Event> eventType;

		/**
		 * All known handlers which event type is a supertype of this handler's event type.
		 * <p>
		 * <b>Note</b>: any modification to this collection MUST also invalidate the {@link #supertypeHandlerCache}.
		 */
		private final Set<Handler> supertypeHandlers = new HashSet<>();

		/**
		 * Set of {@linkplain InvokeWrapper invokers} registered in this handler.
		 * <p>
		 * <b>Note</b>: any modification to this collection MUST also invalidate the {@link #invokerCache}.
		 */
		private final NavigableSet<InvokeWrapper> invokers = new TreeSet<>(InvokeWrapper.COMPARATOR);

		/**
		 * Cache for the {@link #supertypeHandlers} field.
		 */
		@SuppressWarnings("VolatileArrayField")
		private transient volatile Object[] supertypeHandlerCache = null;

		/**
		 * Cache for the {@link #invokers} field.
		 */
		@SuppressWarnings("VolatileArrayField")
		private transient volatile Object[] invokerCache = null;

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
		 * Posts an event to all registered listeners in this handler and its supertype handlers.
		 *
		 * @param event event to post
		 */
		public void post(Event event) {
			invoke(event);

			if (hasSupertypeHandler()) {
				// Prevent ConcurrentModificationException
				// in cases where a registered item may register more items.
				Object[] handlerArray = supertypeHandlerCache;
				if (handlerArray == null) {
					synchronized (this) {
						if ((handlerArray = supertypeHandlerCache) == null) {
							handlerArray = supertypeHandlerCache = supertypeHandlers.toArray();
						}
					}
				}

				for (Object handler : handlerArray) {
					((Handler) handler).invoke(event);
				}
			}
		}

		/**
		 * Invokes all invokers in this handler.
		 */
		void invoke(Event event) {
			// Prevent ConcurrentModificationException
			// in cases where a registered item may register more items.
			Object[] invokerArray = invokerCache;
			if (invokerArray == null) {
				synchronized (this) {
					if ((invokerArray = invokerCache) == null) {
						invokerArray = invokerCache = invokers.toArray();
					}
				}
			}

			for (Object invoker : invokerArray) {
				((InvokeWrapper) invoker).invoke(event);
			}
		}

		/**
		 * Invalidates the {@link #invokerCache} and the {@link #supertypeHandlerCache}
		 * when {@code modified} is {@code true}.
		 *
		 * @param modified should we invalidate?
		 * @return same value as {@code modified}
		 */
		boolean invalidateCache(boolean modified) {
			if (modified) this.invokerCache = this.supertypeHandlerCache = null;
			return modified;
		}

		/**
		 * @return event type for this handler
		 */
		public Class<? extends Event> eventType() {
			return eventType;
		}

		/**
		 * Determines if the event type for this handler
		 * is a subtype of the specified {@code Class} parameter.
		 *
		 * @param cls the {@code Class} object to be checked
		 * @return {@code true} if the event type for this handler
		 *         is a subtype of the type represented by {@code cls},
		 *         {@code false} otherwise
		 */
		public boolean isSubtypeOf(Class<?> cls) {
			Class<? extends Event> type = eventType();
			return type != cls && cls.isAssignableFrom(type);
		}

		/**
		 * Determines if the event type for this handler is a subtype of
		 * the event type for the specified {@code handler} parameter.
		 *
		 * @param handler the {@code Handler} object to be checked
		 * @return {@code true} if the event type for this handler is a subtype of
		 *         the event type for the {@code handler} parameter,
		 *         {@code false} otherwise
		 */
		public boolean isSubtypeOf(Handler handler) {
			return isSubtypeOf(handler.eventType());
		}

		/**
		 * Returns {@code true} if this hander has at least one supertype handler.
		 */
		public boolean hasSupertypeHandler() {
			return !supertypeHandlers.isEmpty();
		}

		/**
		 * Gets an unmodifiable view of this handler's supertype handlers.
		 *
		 * @return an unmodifiable view
		 */
		public Set<Handler> getSupertypeHandlers() {
			return Collections.unmodifiableSet(supertypeHandlers);
		}

		/**
		 * Adds a handler as this handler's supertype handler if it isn't already present.
		 *
		 * @param handler the supertype handler to be added
		 * @return {@code true} if any modification occurred, {@code false} otherwise.
		 */
		boolean addSupertypeHandler(Handler handler) {
			if (handler == this) return false;
			return invalidateCache(supertypeHandlers.add(handler));
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
	 */
	static class InvokeWrapper implements Comparable<InvokeWrapper> {
		/**
		 * Compares Invokewrappers using their {@code priority} value,
		 * and only returns 0 when {@code o1.equals(o2)}.
		 */
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
		 *
		 * @throws SecurityException if the provided {@linkplain MethodHandles.Lookup lookup}
		 *                           cannot access the specified method
		 */
		@SuppressWarnings("unchecked")
		public static InvokeWrapper create(Object instance, Method method, MethodHandles.Lookup lookup) throws SecurityException {
			Class<? extends Event> eventType = (Class<? extends Event>) method.getParameterTypes()[0];
			int priority = method.getDeclaredAnnotation(Listener.class).priority();
			MethodHandle methodHandle = AccessHelper.unreflectMethodHandle(lookup, method);
			return new InvokeWrapper(instance, eventType, method, priority, methodHandle);
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
		 * Compares this object with another InvokeWrapper for order.
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
