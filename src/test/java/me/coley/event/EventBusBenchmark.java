package me.coley.event;

import me.coley.event.testevent.TestBetaEvent;
import me.coley.event.testevent.TestDeltaEvent;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

/**
 * @author Andy Li
 */
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class EventBusBenchmark {
	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(EventBusBenchmark.class.getSimpleName())
				.build();

		new Runner(opt).run();
	}

	private EventBus bus1;
	private EventBus bus2;
	private TestDeltaEvent event;

	@Setup
	public void setup() {
		final MethodHandles.Lookup lookup = MethodHandles.lookup();
		this.bus1 = new EventBus();
		this.bus1.subscribe(MyListener.INSTANCE, lookup);
		this.bus1.subscribe(MyListener2.INSTANCE, lookup);

		this.bus2 = new EventBus();
		this.bus2.subscribe(MyListener.INSTANCE, lookup);
		this.bus2.subscribe(MyCommonTypeListener.INSTANCE, lookup);

		this.event = new TestDeltaEvent();
	}

	@TearDown
	public void tearDown() {
		if (event.id == 0) throw new RuntimeException("listener wasn't being called!");
	}

	@Benchmark
	public void post() {
		bus1.post(event);
	}

	@Benchmark
	public void post_commontype() {
		bus2.post(event);
	}

	static class MyListener {
		static final MyListener INSTANCE = new MyListener();

		@Listener
		public void onDeltaEvent(TestDeltaEvent event) {
			event.id++;
		}
	}

	static class MyListener2 {
		static final MyListener2 INSTANCE = new MyListener2();

		@Listener
		public void onDeltaEvent(TestDeltaEvent event) {
			event.id++;
		}
	}

	static class MyCommonTypeListener {
		static final MyCommonTypeListener INSTANCE = new MyCommonTypeListener();

		@Listener
		public void onBetaEvent(TestBetaEvent event) {
			event.id++;
		}
	}
}
