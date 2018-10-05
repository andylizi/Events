package me.coley.event;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Basic event bus.
 *
 * @author Matt
 */
public final class Bus {
	private static final EventBus EVENT_BUS = new EventBus();

	/**
	 * Register all listener methods on {@code object} for receiving events.
	 *
	 * @param object object whose listener methods should be registered
	 * @param lookup the {@linkplain MethodHandles.Lookup Lookup} object used in {@link MethodHandle} creation
	 * @throws IllegalArgumentException if the {@code object} was previously registered
	 *                                  or there's an invalid listener method on the {@code object}
	 * @see EventBus#subscribe(Object, MethodHandles.Lookup)
	 * @since 1.3
	 */
	public static void subscribe(Object object, MethodHandles.Lookup lookup) throws IllegalArgumentException, SecurityException {
		EVENT_BUS.subscribe(object, lookup);
	}

	/**
	 * Register all listener methods on {@code object} for receiving events.
	 *
	 * @param object object whose listener methods should be registered
	 * @throws IllegalArgumentException if the {@code object} was previously registered
	 *                                  or there's an invalid listener method on the {@code object}
	 * @see EventBus#subscribe(Object)
	 */
	public void subscribe(Object object) throws IllegalArgumentException, SecurityException {
		EVENT_BUS.subscribe(object);
	}

	/**
	 * Unregister all listener methods on the {@code object}.
	 *
	 * @param object object whose listener methods should be unregistered
	 * @see EventBus#unsubscribe(Object)
	 */
	public static void unsubscribe(Object object) {
		EVENT_BUS.unsubscribe(object);
	}

	/**
	 * Posts an event to all registered listeners.
	 *
	 * @param event event to post
	 */
	public static void post(Event event) {
		EVENT_BUS.post(event);
	}

	private Bus() {}
}