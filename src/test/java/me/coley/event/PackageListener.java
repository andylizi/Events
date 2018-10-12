package me.coley.event;

class PackageListener {
	private final EventMarker<Class<? extends Event>> marker;

	public PackageListener(EventMarker<Class<? extends Event>> marker) { this.marker = marker; }

	@Listener
	public void publicListener(Event event) {
		marker.mark(event.getClass());
	}

	@Listener
	void packageListener(Event event) {
		marker.mark(event.getClass());
	}

	static class MemberClass {
		public void foo() { }
	}
}
