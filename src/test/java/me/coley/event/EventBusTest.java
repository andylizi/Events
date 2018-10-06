package me.coley.event;

import me.coley.event.testevent.*;
import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class EventBusTest {
	private EventBus bus;

	@Before
	public void setup() {
		bus = new EventBus();
	}

	@Test
	public void testBasicFunctionality() {
		EventMarker<Class<? extends Event>> marker = new EventMarker<>(Class::getSimpleName);
		Object object = new Object() {
			@Listener
			public void onEventA(TestAlphaEvent event) {
				marker.mark(TestAlphaEvent.class);
			}

			@Listener
			public void onEventB(TestBetaEvent event) {
				marker.mark(TestBetaEvent.class);
			}
		};
		bus.subscribe(object);
		bus.post(new TestAlphaEvent());
		marker.assertMarked("%s received", TestAlphaEvent.class);
		marker.assertUnmarked("%s received before post", TestBetaEvent.class);
		bus.post(new TestBetaEvent());
		bus.post(new TestGammaEvent());
		bus.post(new TestDeltaEvent());
		bus.post(new TestEpsilonEvent());
		bus.post(new TestZetaEvent());
		marker.assertMarked("%s received", TestBetaEvent.class);
		marker.resetAll();

		bus.unsubscribe(object);
		bus.post(new TestAlphaEvent());
		bus.post(new TestZetaEvent());
		marker.assertUnmarked("%s received after unsubscribied", TestAlphaEvent.class);
		marker.resetAll();

		bus.unsubscribe(object); // should do nothing

		bus.subscribe(object);
		bus.post(new TestAlphaEvent());
		marker.assertMarked("%s received again", TestAlphaEvent.class);
	}

	@Test
	public void testPriority() {
		List<Integer> invocationOrder = new ArrayList<>(4);
		bus.subscribe(new Object() {
			@Listener(priority = 1)
			public void priority1(TestAlphaEvent event) {
				invocationOrder.add(1);
			}

			@Listener(priority = 2)
			public void priority2(TestAlphaEvent event) {
				invocationOrder.add(2);
			}

			@Listener(priority = 2)
			public void priority2_(TestAlphaEvent event) {
				invocationOrder.add(2);
			}

			@Listener(priority = 5)
			public void priority5(TestAlphaEvent event) {
				invocationOrder.add(5);
			}
		});
		bus.post(new TestAlphaEvent());
		assertEquals("invocation order", Arrays.asList(1, 2, 2, 5), invocationOrder);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSupertype1() {
		EventMarker<Class<? extends Event>> marker = new EventMarker<>(Class::getSimpleName);
		bus.subscribe(new Object() {
			@Listener
			public void onBeta(TestBetaEvent event) {
				marker.mark(event.getClass());
			}
		});
		bus.post(new TestAlphaEvent());
		bus.post(new TestBetaEvent());
		bus.post(new TestGammaEvent());
		bus.post(new TestDeltaEvent());
		bus.post(new TestEpsilonEvent());
		bus.post(new TestZetaEvent());
		marker.assertMarkedExactly(null, new Class[]{
				TestBetaEvent.class, TestGammaEvent.class, TestDeltaEvent.class, TestEpsilonEvent.class
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSupertype2() {
		EventMarker<Class<? extends Event>> marker = new EventMarker<>(Class::getSimpleName);
		bus.subscribe(new Object() {
			@Listener
			public void onGamma(TestGammaEvent event) {
				marker.mark(event.getClass());
			}

			@Listener
			public void onZeta(TestZetaEvent event) {
				marker.mark(event.getClass());
			}
		});
		bus.post(new TestGammaEvent());
		bus.post(new TestAlphaEvent());
		bus.post(new TestBetaEvent());
		bus.post(new TestEpsilonEvent());
		bus.post(new TestDeltaEvent());
		bus.post(new TestZetaEvent());
		marker.assertMarkedExactly(null, new Class[]{
				TestGammaEvent.class, TestEpsilonEvent.class, TestZetaEvent.class
		});
	}

	@Test
	public void testConcurrentModification() {
		List<Integer> invocationOrder = new ArrayList<>(3);
		Object object2 = new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event) {
				bus.unsubscribe(this);
				invocationOrder.add(2);
			}
		};
		bus.subscribe(new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event) {
				bus.subscribe(object2);
				bus.unsubscribe(this);
				invocationOrder.add(1);
			}
		});
		bus.post(new TestAlphaEvent());
		bus.post(new TestAlphaEvent());
		bus.post(new TestAlphaEvent());
		assertEquals("invocation order", Arrays.asList(1, 2), invocationOrder);
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
			public void onEvent(TestAlphaEvent event, Void anotherArgument) {
				fail("illegal listener called");
			}
		});
		bus.post(new TestAlphaEvent());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener2() {
		bus.subscribe(new IllegalStaticListener());
		bus.post(new TestAlphaEvent());
	}

	@SuppressWarnings("all")
	static final class IllegalStaticListener {
		@Listener
		public static void onEvent(TestAlphaEvent event) {
			fail("static listener called");
		}
	}
}