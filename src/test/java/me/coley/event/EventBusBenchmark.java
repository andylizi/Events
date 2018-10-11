package me.coley.event;

import me.coley.event.testevent.TestAlphaEvent;
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

	private EventBus bus;
	private TestAlphaEvent event;

	@Setup
	public void setup() {
		this.bus = new EventBus();
		this.bus.subscribe(new MyListener(), MethodHandles.lookup());
		this.event = new TestAlphaEvent();
	}

	@TearDown
	public void tearDown() {
		if (event.id == 0) throw new RuntimeException("listener wasn't being called!");
	}

	@Benchmark
	public void post() {
		bus.post(event);
	}

	static class MyListener {
		@Listener
		public void onEvent(TestAlphaEvent event) {
			event.id++;
		}
	}
}
