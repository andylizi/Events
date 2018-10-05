package me.coley.event;

import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class EventBusTest {
	private EventBus bus;

	@Before
	public void setup() {
		bus = new EventBus();
	}

	@Test
	public void testSubscribeAndUnsubscribe() {
		AtomicBoolean receivedA = new AtomicBoolean(false);
		AtomicBoolean receivedB = new AtomicBoolean(false);
		Object object = new Object() {
			@Listener
			public void onEventA(EventA event) {
				receivedA.set(true);
			}

			@Listener
			public void onEventB(EventB event) {
				receivedB.set(true);
			}
		};
		bus.subscribe(object);
		bus.post(new EventA());
		assertTrue("EventA received", receivedA.get());
		assertFalse("EventB received before post", receivedB.get());
		bus.post(new EventB());
		assertTrue("EventB received", receivedB.get());

		receivedA.set(false);
		receivedB.set(false);
		bus.unsubscribe(object);
		bus.post(new EventA());
		assertFalse("EventA received after unsubscribied", receivedA.get());

		bus.unsubscribe(object); // do nothing

		receivedA.set(false);
		receivedB.set(false);
		bus.subscribe(object);   // subscribe again
		bus.post(new EventA());
		assertTrue("EventA received again", receivedA.get());
	}

	@Test
	public void testPriority() {
		List<Integer> invocationOrder = new ArrayList<>(3);
		bus.subscribe(new Object() {
			@Listener(priority = 1)
			public void priority1(EventA eventA) {
				invocationOrder.add(1);
			}

			@Listener(priority = 2)
			public void priority2(EventA eventA) {
				invocationOrder.add(2);
			}

			@Listener(priority = 2)
			public void priority2_(EventA eventA) {
				invocationOrder.add(2);
			}

			@Listener(priority = 5)
			public void priority5(EventA eventA) {
				invocationOrder.add(5);
			}
		});
		bus.post(new EventA());
		assertEquals("invocation order", Arrays.asList(1, 2, 2, 5), invocationOrder);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testPreviouslySubscribed() {
		Object object = new Object();
		bus.subscribe(object);
		bus.subscribe(object);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener1() {
		bus.subscribe(new Object() {
			@Listener
			public void onEvent(EventA event, Void anotherArgument) {
				fail("illegal listener called");
			}
		});
		bus.post(new EventA());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener2() {
		bus.subscribe(new IllegalStaticListener());
		bus.post(new EventA());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener3() {
		bus.subscribe(new Object() {
			@Listener
			public void onEvent(EventC event) {
				fail("illegal listener called");
			}
		});
		bus.post(new EventC() {});
	}

	static final class EventA extends Event {}

	static final class EventB extends Event {}

	abstract static class EventC extends Event {}

	@SuppressWarnings("all")
	static final class IllegalStaticListener {
		@Listener
		public static void onEvent(EventA event) {
			fail("static listener called");
		}
	}
}