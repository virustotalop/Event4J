package net.techcable.event4j;

import lombok.*;

import java.lang.reflect.Method;
import java.util.Objects;

import net.techcable.event4j.marker.MarkedEvent;

public final class RegisteredListener<E, L> implements Comparable<RegisteredListener> {
    @Getter
    private final L listener;
    @Getter
    private final Method method;
    private final MarkedEvent marked;
    private final EventExecutor<E, L> executor;

    public RegisteredListener(EventBus<?, ?> eventBus, Method method, L listener, EventExecutor<E, L> executor) {
        validate(eventBus, method);
        this.method = Objects.requireNonNull(method, "Null method");
        this.listener = Objects.requireNonNull(listener, "Null listener");
        this.executor = Objects.requireNonNull(executor, "Null executor");
        this.marked = Objects.requireNonNull(eventBus.getEventMarker().mark(method), "Null marked event");
    }

    public static <E, L> void validate(EventBus<E, L> eventBus, Method method) {
        Objects.requireNonNull(eventBus, "Null eventBus");
        Objects.requireNonNull(method, "Null method");
        if (!eventBus.getEventMarker().isMarked(method)) throw new IllegalArgumentException("Method must be an event handler: " + method.getDeclaringClass().getName() + "::" + method.getName());
        if (method.getParameterCount() != 1) throw new IllegalArgumentException("EventHandlers must have only one argument: " + method.getDeclaringClass().getName() + "::" + method.getName());
        if (!eventBus.getEventClass().isAssignableFrom(method.getParameterTypes()[0])) throw new IllegalArgumentException("EventHandler must accept one argument: " + method.getParameterTypes()[0].getSimpleName());
        if (!eventBus.getListenerClass().isAssignableFrom(method.getDeclaringClass())) throw new IllegalArgumentException("Listener " + method.getDeclaringClass() + " must be instanceof " + eventBus.getListenerClass());
    }

    public void fire(E event) {
        executor.fire(listener, event);
    }

    @SuppressWarnings("unchecked") // Ur mum's unchecked
    public Class<? extends E> getEventType() {
        return (Class<? extends E>) method.getParameterTypes()[0];
    }

    public int getPriority() {
        return marked.getPriority();
    }

    @Override
    public String toString() {
        return listener.getClass().getName() + "::" + method.getName();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this || obj == null) return false;
        if (obj instanceof RegisteredListener) {
            RegisteredListener other = (RegisteredListener) obj;
            return other.getListener() == this.getListener() && other.getMethod().equals(this.getMethod()); // Reference equality for listeners
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getMethod().hashCode() ^ System.identityHashCode(getListener());
    }

    @Override
    public int compareTo(RegisteredListener other) {
        return other.getMethod().equals(this.getMethod())
                && other.getListener() == this.getListener() ? 0
                : Integer.compare(this.getPriority(), other.getPriority());
    }
}
