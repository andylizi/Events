package me.coley.event;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic event bus.
 *
 * @author Matt
 */
public class Bus {
	private final static Bus INSTANCE = new Bus();

	/**
	 * Map of event classes to event distribution handlers.
	 */
	private final Map<Class<?>, Handler> eventToHandler = new HashMap<>();

	/**
	 * Register methods in an class instance for receiving events.
	 *
	 * @param instance
	 */
	public static void subscribe(Object instance) {
		INSTANCE.subscribe_(instance);
	}

	/**
	 * Unregister methods from receiving events.
	 *
	 * @param instance
	 */
	public static void unsubscribe(Object instance) {
		INSTANCE.unsubscribe_(instance);
	}

	/**
	 * Post events to listeners.
	 *
	 * @param value
	 */
	public static void post(Event value) {
		INSTANCE.post_(value);
	}

	/**
	 * Register methods in an class instance for receiving events.
	 *
	 * @param instance
	 */
	private void subscribe_(Object instance) {
		Class<?> clazz = instance.getClass();
		for(Method method : clazz.getDeclaredMethods()) {
			if(isValid(method)) {
				method.setAccessible(true);
				Class<?> eventClazz = method.getParameterTypes()[0];
				getHandler(eventClazz).subscribe(instance, method);
			}
		}
	}

	/**
	 * Unregister methods from receiving events.
	 *
	 * @param instance
	 */
	private void unsubscribe_(Object instance) {
		Class<?> clazz = instance.getClass();
		for(Method method : clazz.getDeclaredMethods()) {
			if(isValid(method)) {
				Class<?> eventClazz = method.getParameterTypes()[0];
				getHandler(eventClazz).unsubscribe(instance, method);
			}
		}
	}

	/**
	 * Post events to listeners.
	 *
	 * @param value
	 */
	public void post_(Event value) {
		getHandler(value.getClass()).post(value);
	}

	/**
	 * Check if method can listen to events,
	 *
	 * @param method
	 * @return
	 */
	private boolean isValid(Method method) {
		//@formatter:off
		Class<?>[] types = method.getParameterTypes();
		return 
			// Check parameter for event type
			types.length == 1 &&
			Event.class.isAssignableFrom(types[0]) &&
			// Check if listener annotation exists
			method.isAnnotationPresent(Listener.class);
		//@formatter:on
	}

	/**
	 * Retreive handler for the given event class.
	 *
	 * @param eventClazz
	 *
	 * @return Handler for event type.
	 */
	private Handler getHandler(Class<?> eventClazz) {
		Handler handler = eventToHandler.get(eventClazz);
		if(handler == null) {
			eventToHandler.put(eventClazz, handler = new Handler());
		}
		return handler;
	}

	/**
	 * Event distribution handler.
	 *
	 * @author Matt
	 */
	private static class Handler {
		/**
		 * Map of method destinations.
		 */
		private final Map<String, InvokeWrapper> invokers = new HashMap<>();

		/**
		 * Add method to map.
		 *
		 * @param instance
		 * @param method
		 */
		public void subscribe(Object instance, Method method) {
			String key = instance.toString() + method.toString();
			invokers.put(key, new InvokeWrapper(instance, method));
		}

		/**
		 * Remove method from map.
		 *
		 * @param instance
		 * @param method
		 */
		public void unsubscribe(Object instance, Method method) {
			String key = instance.toString() + method.toString();
			invokers.remove(key);
		}

		/**
		 * Send data to methods in map.
		 *
		 * @param value
		 */
		public void post(Event value) {
			invokers.values().forEach(i -> i.post(value));
		}

		/**
		 * Reflection invoke wrapper.
		 *
		 * @author Matt
		 */
		private static class InvokeWrapper {
			private final Object instance;
			private final Method method;

			public InvokeWrapper(Object instance, Method method) {
				this.instance = instance;
				this.method = method;
			}

			public void post(Event value) {
				try {
					method.invoke(instance, value);
				} catch(Exception e) {}
			}
		}
	}
}