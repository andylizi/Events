package me.coley.event;

import org.junit.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

	public boolean isMarkedOnce(T subject) {
		boolean result = false;
		for (T mark : marks) {
			if (Objects.equals(mark, subject)) {
				if (!result) {  // first occurrence
					result = true;
				} else {       // secound occurrence
					return false;
				}
			}
		}
		return result;
	}

	public boolean isUnmarked(T subject) {
		return !isMarked(subject);
	}

	public boolean isMarkedExactly(T[] subjects) {
		return Arrays.asList(subjects).equals(marks);
	}

	public void assertMarked(String message, T subject) {
		assertTrue(message, subject, isMarked(subject));
	}

	public void assertMarkedOnce(String message, T subject) {
		assertTrue(message, subject, isMarkedOnce(subject));
	}

	public void assertUnmarked(String message, T subject) {
		assertTrue(message, subject, isUnmarked(subject));
	}

	public void assertMarkedExactly(String message, T[] subjects) {
		List<T> expected = Arrays.asList(subjects);
		if (!expected.equals(marks)) {
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

	private void assertTrue(String message, T subject, boolean b) {
		if (message.contains("%s")) message = message.replace("%s", toStringFunction.apply(subject));
		Assert.assertTrue(message, b);
	}

	private void assertFalse(String message, T subject, boolean b) {
		if (message.contains("%s")) message = message.replace("%s", toStringFunction.apply(subject));
		Assert.assertFalse(message, b);
	}
}
