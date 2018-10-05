package me.coley.event;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

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
	private MyEvent event;

	@Setup
	public void setup() {
		this.bus = new EventBus();
		this.bus.subscribe(this);
		this.event = new MyEvent();
	}

	@Benchmark
	public void post() {
		bus.post(event);
	}

	@Listener
	public void onEvent(MyEvent event) {
		event.i++;
	}

	static class MyEvent extends Event { int i = 0; }
}
