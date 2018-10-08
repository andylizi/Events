package me.coley.event.testevent;

import me.coley.event.Event;

/**
 * @author Andy Li
 */
public class TestAlphaEvent extends Event {
	public int id;

	public TestAlphaEvent() {
	}

	public TestAlphaEvent(int id) {
		this.id = id;
	}
}
