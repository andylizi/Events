package me.coley.event.testevent;

import me.coley.event.Event;

/**
 * @author Andy Li
 */
public class TestZetaEvent extends Event {
	public int id;

	public TestZetaEvent() {
	}

	public TestZetaEvent(int id) {
		this.id = id;
	}
}
