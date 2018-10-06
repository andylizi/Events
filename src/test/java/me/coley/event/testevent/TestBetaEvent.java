package me.coley.event.testevent;

import me.coley.event.Event;

public class TestBetaEvent extends Event {
	public int id;

	public TestBetaEvent() {
	}

	public TestBetaEvent(int id) {
		this.id = id;
	}
}
