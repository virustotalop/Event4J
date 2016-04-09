package net.techcable.event4j;

import lombok.*;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.techcable.event4j.marker.EventMarker;
import net.techcable.event4j.marker.MarkedEvent;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class EventBus<E, L> {
    private final ConcurrentMap<Class<?>, HandlerList<E, L>> handlers = new ConcurrentHashMap<>();
    @Getter(AccessLevel.PROTECTED)
    private final EventMarker eventMarker;
    private final EventExecutor.Factory executorFactory;
    @Getter
    private final Class<E> eventClass;
    @Getter
    private final Class<L> listenerClass;

    /**
     * Fire the given event to all registered listeners.
     * <p>Any exceptions (even checked ones) are propagated upwards untouched.</p>
     * <p>This method is weakly consistent, and may not see listeners that are added while it is firing.</p>
     *
     * @param event the event to fire
     */
    public void fire(E event) {
        if (event == null) throw new NullPointerException("Null event"); // This should be optimized away by the JIT
        HandlerList<E, L> handler = handlers.get(event.getClass());
        if (handler == null) return; // No events of said type
        handler.fire(event);
    }

    public void unregister(L listener) {
        if (!listenerClass.isInstance(listener)) throw new IllegalArgumentException("Invalid listener type: "  + listener.getClass().getClass());
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (!eventMarker.isMarked(method)) continue; // Not a handler
            RegisteredListener<E,L> registeredListener = new RegisteredListener<>(this, method, listener, EventExecutor.empty());
            handlers.get(registeredListener.getEventType()).unregister(registeredListener);
        }
    }

    public void register(L listener) {
        if (listener == null) throw new NullPointerException("Null listener");
        if (!listenerClass.isInstance(listener))
            throw new IllegalArgumentException("Invalid listener type: " + listener.getClass().getName());
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (!eventMarker.isMarked(method)) continue; // Not a handler
            RegisteredListener<E,L> registeredListener = new RegisteredListener<>(this, method, listener , executorFactory.create(this, method));
            register(registeredListener);
        }
    }

    private void register(RegisteredListener<E,L> listener) {
        HandlerList<E, L> handlerList = handlers.computeIfAbsent(listener.getEventType(), (eventType) -> new HandlerList<>(EventBus.this));
        handlerList.register(listener);
    }

    // Constructors

    public static Builder<Object, Object> builder() {
        return new Builder<>();
    }

    public static EventBus build() {
        return builder().build();
    }

    @SuppressWarnings("unchecked") // Generics are fun!
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class Builder<E, L> {
        private Class<?> eventClass = Object.class;
        private Class<?> listenerClass = Object.class;
        private EventMarker eventMarker = m -> m.isAnnotationPresent(EventHandler.class) ? (MarkedEvent) m.getAnnotation(EventHandler.class).priority()::ordinal : null;
        private EventExecutor.Factory executorFactory = EventExecutor.Factory.getDefault();

        public <E> Builder<E, L> eventClass(Class<E> eventClass) {
            this.eventClass = Objects.requireNonNull(eventClass, "Null event class");
            return (Builder<E, L>) this;
        }

        public <L> Builder<E, L> listenerClass(Class<L> listenerClass) {
            this.listenerClass = Objects.requireNonNull(listenerClass, "Null listener class");
            return (Builder<E, L>) this;
        }

        public Builder<E, L> eventMarker(EventMarker eventMarker) {
            this.eventMarker = Objects.requireNonNull(eventMarker, "Null event marker");
            return this;
        }

        public Builder<E, L> executorFactory(EventExecutor.Factory factory) {
            this.executorFactory = Objects.requireNonNull(factory, "Null exevutor factory");
            return this;
        }

        public EventBus<E, L> build() {
            return new EventBus<>(eventMarker, executorFactory,  (Class<E>) eventClass, (Class<L>) listenerClass);
        }
    }
}