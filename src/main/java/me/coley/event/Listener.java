package me.coley.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods indicating the method should receive events.
 *
 * @author Matt
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Listener {
	/**
	 * Greater values are called first.
	 *
	 * @return Priority of event receiving.
	 */
	int priority() default 0;
}