package me.coley.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class EventMarker<T> {
	private final List<T> marks = new ArrayList<>();
	private final Function<T, String> toStringFunction;

	public EventMarker(Function<T, String> toStringFunction) {
		this.toStringFunction = toStringFunction;
	}

	public EventMarker() {
		this(null);
	}

	public void mark(T subject) {
		if (isUnmarked(subject)) {
			marks.add(subject);
		}
	}

	public boolean isMarked(T subject) {
		return marks.contains(subject);
	}

	public boolean isUnmarked(T subject) {
		return !isMarked(subject);
	}

	public boolean isMarkedExactly(T[] subjects) {
		return Arrays.asList(subjects).equals(marks);
	}

	public void assertMarked(String message, T subject) {
		if (message.contains("%s")) message = message.replace("%s", toStringFunction.apply(subject));
		assertTrue(message, isMarked(subject));
	}

	public void assertUnmarked(String message, T subject) {
		if (message.contains("%s")) message = message.replace("%s", toStringFunction.apply(subject));
		assertFalse(message, isMarked(subject));
	}

	public void assertMarkedExactly(String message, T[] subjects) {
		List<T> expected = Arrays.asList(subjects);
		if(!expected.equals(marks)) {
			assertEquals(message, subjectsToString(expected.stream()), subjectsToString(marks.stream()));
		}
	}

	public boolean reset(T subject) {
		return marks.remove(subject);
	}

	public void resetAll() {
		marks.clear();
	}

	private String subjectsToString(Stream<T> subjectStream) {
		return subjectStream.map(toStringFunction).collect(Collectors.joining(", ", "[", "]"));
	}
}
