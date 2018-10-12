package me.coley.event;

import me.coley.event.testevent.*;
import org.junit.*;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Andy Li
 */
public class EventBusTest {
	private EventBus bus;
	private EventMarker<Class<? extends Event>> marker;

	@Before
	public void setup() {
		this.bus = new EventBus();
		this.marker = new EventMarker<>(Class::getSimpleName);
	}

	@Test
	public void testBasicFunctionality() {
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
		marker.assertMarkedOnce("One %s should be delivered", TestAlphaEvent.class);
		marker.assertUnmarked("%s shouldn't be delivered", TestBetaEvent.class);
		bus.post(new TestBetaEvent());
		marker.assertMarkedOnce("One %s should be delivered", TestBetaEvent.class);
		marker.resetAll();

		bus.unsubscribe(object);
		bus.post(new TestAlphaEvent());
		bus.post(new TestZetaEvent());
		marker.assertUnmarked("After unsubscribing, %s shouldn't be delivered", TestAlphaEvent.class);
		marker.resetAll();

		bus.subscribe(object);
		bus.post(new TestAlphaEvent());
		marker.assertMarkedOnce("One %s should be delivered after subscribing again", TestAlphaEvent.class);
	}

	@Test
	public void testPriority() {
		List<Integer> deliveredOrder = new ArrayList<>(4);
		bus.subscribe(new Object() {
			@Listener(priority = 1)
			public void onEvent1(TestAlphaEvent event) {
				deliveredOrder.add(1);
			}

			@Listener(priority = 2)
			public void onEvent2(TestAlphaEvent event) {
				deliveredOrder.add(2);
			}

			@Listener(priority = 2)
			public void onEvent2(Event event) {
				deliveredOrder.add(2);
			}

			@Listener(priority = 3)
			public void onEvent3(TestAlphaEvent event) {
				deliveredOrder.add(3);
			}

			@Listener(priority = 4)
			public void onEvent4(Event event) {
				deliveredOrder.add(4);
			}
		});
		bus.post(new TestAlphaEvent());
		assertEquals("delivered order", Arrays.asList(1, 2, 2, 3, 4), deliveredOrder);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testSupertype1() {
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
	public void testInheritedListener() {
		new MyListenerImpl();
		bus.post(new TestAlphaEvent());
		bus.post(new TestBetaEvent());
		marker.assertMarkedNTimes("Two %ss should be delivered", TestAlphaEvent.class, 2);
		marker.assertMarkedNTimes("Two %ss should be delivered", TestBetaEvent.class, 2);
	}

	@Test
	public void testNonPublicListener() {
		bus.subscribe(new PackageListener(marker), MethodHandles.lookup());
		bus.post(new TestAlphaEvent());
		marker.assertMarkedNTimes("Two %ss should be delivered", TestAlphaEvent.class, 2);
	}

	@Test
	public void testMemberClassListenerPretendJava8() {
		testMemberClassListener0(true, false);
	}

	@Test
	public void testMemberClassListenerPretendJava9() {
		testMemberClassListener0(false, true);
	}

	private void testMemberClassListener0(boolean pretendJava8, boolean pretendJava9) {
		// Don't setAccessible(true)
		// we are in the same module, setAccessible() can bypass anything, so the test will become meaningless
		AccessHelper.trySuppressAccessControl = false;
		AccessHelper.pretendJava8 = pretendJava8;
		AccessHelper.pretendJava9 = pretendJava9;
		try {
			bus.subscribe(new MemberListener(marker), MethodHandles.lookup());
			bus.post(new TestAlphaEvent());
			marker.assertMarkedNTimes("Three %ss should be delivered", TestAlphaEvent.class, 3);
		} finally {
			AccessHelper.trySuppressAccessControl = true;
			AccessHelper.pretendJava8 = false;
			AccessHelper.pretendJava9 = false;
		}
	}

	@Test
	public void testConcurrentModification() {
		List<Integer> deliveredOrder = new ArrayList<>(3);
		Object object2 = new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event) {
				bus.unsubscribe(this);
				deliveredOrder.add(2);
			}
		};
		bus.subscribe(new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event) {
				bus.subscribe(object2);
				bus.unsubscribe(this);
				deliveredOrder.add(1);
			}
		});
		bus.post(new TestAlphaEvent());
		bus.post(new TestAlphaEvent());
		bus.post(new TestAlphaEvent());
		assertEquals("delivered order", Arrays.asList(1, 2), deliveredOrder);
	}

	@Test
	public void testSubscribeRepeatedly() {
		Object object = new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event) {
				marker.mark(TestAlphaEvent.class);
			}
		};
		bus.subscribe(object);
		bus.subscribe(object);
		bus.post(new TestAlphaEvent());
		marker.assertMarkedOnce("One %s should be delivered", TestAlphaEvent.class);
	}

	@Test
	public void testUnsubscribeRepeatedly() {
		Object object = new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event) {
				marker.mark(TestAlphaEvent.class);
			}
		};
		bus.subscribe(object);
		bus.unsubscribe(object);
		bus.unsubscribe(object);
		bus.post(new TestAlphaEvent());
		marker.assertUnmarked("After unsubscribing, %s shouldn't be delivered", TestAlphaEvent.class);
	}

	@Test
	public void testNonVoidListener() {
		bus.subscribe(new Object() {
			@Listener
			public int onAlphaEvent(TestAlphaEvent event) {
				marker.mark(TestAlphaEvent.class);
				return event.id;
			}
		});
		bus.post(new TestAlphaEvent());
		marker.assertMarkedOnce("One %s should be delivered", TestAlphaEvent.class);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener1() throws ReflectiveOperationException {
		Object object = new Object() {
			@Listener
			public void onEvent(TestAlphaEvent event, Void anotherArgument) {
				fail("Illegal listener shouldn't be called");
			}
		};
		assertFalse("Not a valid listener method", EventBus.isListenerMethod(object.getClass()
				.getDeclaredMethod("onEvent", TestAlphaEvent.class, Void.class)));
		bus.subscribe(object);
		bus.post(new TestAlphaEvent());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener2() throws ReflectiveOperationException {
		Object object = new Object() {
			@Listener
			public void onEvent(Void notAnEvent) {
				fail("Illegal listener shouldn't be called");
			}
		};
		assertFalse("Not a valid listener method", EventBus.isListenerMethod(object.getClass()
				.getDeclaredMethod("onEvent", Void.class)));
		bus.subscribe(object);
		bus.post(new TestAlphaEvent());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testIllegalListener3() throws ReflectiveOperationException {
		assertFalse("Not a valid listener method", EventBus.isListenerMethod(IllegalStaticListener.class
				.getDeclaredMethod("onEvent", TestAlphaEvent.class)));
		bus.subscribe(new IllegalStaticListener());
		bus.post(new TestAlphaEvent());
	}

	@SuppressWarnings("all")
	static final class IllegalStaticListener {
		@Listener
		public static void onEvent(TestAlphaEvent event) {
			fail("Static listener method shouldn't be called");
		}
	}

	static class MemberListener {
		private final EventMarker<Class<? extends Event>> marker;

		MemberListener(EventMarker<Class<? extends Event>> marker) { this.marker = marker; }

		@Listener
		public void publicListener(Event event) {
			marker.mark(event.getClass());
		}

		@Listener
		void packageListener(Event event) {
			marker.mark(event.getClass());
		}

		@Listener
		private void privateListener(Event event) {
			marker.mark(event.getClass());
		}
	}

	abstract class AbstractMyListener {
		{
			bus.subscribe(this, MethodHandles.lookup().in(getClass()));
		}

		@Listener
		protected abstract void onAlphaEvent(TestAlphaEvent event);

		@Listener
		private void onBetaEvent(TestBetaEvent event) {
			marker.mark(TestBetaEvent.class);
		}

		@Listener
		public void onAllEvent(Event event) {
			fail("onAllEvent() is overrode by MyListenerImpl, this method shouldn't be called");
		}
	}

	class MyListenerImpl extends AbstractMyListener {
		@Override
		protected void onAlphaEvent(TestAlphaEvent event) {
			marker.mark(TestAlphaEvent.class);
		}

		@Override
		public void onAllEvent(Event event) {
			marker.mark(event.getClass());
		}
	}
}