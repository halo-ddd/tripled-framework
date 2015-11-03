package eu.tripledframework.eventbus.domain.synchronous;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.tripledframework.eventbus.domain.EventBusInterceptor;
import eu.tripledframework.eventbus.domain.EventCallback;
import eu.tripledframework.eventbus.domain.EventPublisher;
import eu.tripledframework.eventbus.domain.EventSubscriber;
import eu.tripledframework.eventbus.domain.callback.ExceptionThrowingEventCallback;
import eu.tripledframework.eventbus.domain.dispatcher.EventDispatcher;
import eu.tripledframework.eventbus.domain.interceptor.InterceptorChainFactory;
import eu.tripledframework.eventbus.domain.invoker.EventHandlerInvoker;
import eu.tripledframework.eventbus.domain.invoker.EventHandlerInvokerFactory;
import eu.tripledframework.eventbus.domain.invoker.EventHandlerInvokerRepository;
import eu.tripledframework.eventbus.domain.invoker.InstanceEventHandlerInvokerFactory;

/**
 * Synchronous implementation of the CommandDispatcher.
 */
public class SynchronousEventBus implements EventPublisher, EventSubscriber {

  private final Logger logger = LoggerFactory.getLogger(SynchronousEventBus.class);

  private final EventHandlerInvokerRepository invokerRepository;
  private final InterceptorChainFactory interceptorChainFactory;
  private List<EventHandlerInvokerFactory> eventHandlerInvokerFactories;

  // constructors

  public SynchronousEventBus() {
    this.invokerRepository = new EventHandlerInvokerRepository();
    this.interceptorChainFactory = new InterceptorChainFactory();
    this.eventHandlerInvokerFactories = Collections.singletonList(new InstanceEventHandlerInvokerFactory());
  }

  public SynchronousEventBus(List<EventBusInterceptor> interceptors) {
    this.invokerRepository = new EventHandlerInvokerRepository();
    this.interceptorChainFactory = new InterceptorChainFactory(interceptors);
    this.eventHandlerInvokerFactories = Collections.singletonList(new InstanceEventHandlerInvokerFactory());
  }

  // subscribe methods

  @Override
  public void subscribe(Object eventHandler) {
    eventHandlerInvokerFactories.stream()
        .filter(cur -> cur.supports(eventHandler))
        .findFirst()
        .ifPresent(f -> f.create(eventHandler).forEach(this::subscribeInternal));
  }

  protected void subscribeInternal(EventHandlerInvoker eventHandler) {
    getLogger().info("Adding Event subscription for {}", eventHandler.toString());
    invokerRepository.addEventHandlerInvoker(eventHandler);
  }

  // publish methods

  @Override
  public void publish(Object message) {
    publish(message, new ExceptionThrowingEventCallback<>());
  }

  @Override
  public <ReturnType> void publish(Object event, Future<ReturnType> callback) {
    if (callback instanceof EventCallback) {
      publish(event, (EventCallback<ReturnType>) callback);
    } else {
      throw new IllegalArgumentException("The callback should be an instance of EventCallBack.");
    }
  }

  @Override
  public <ReturnType> void publish(Object event, EventCallback<ReturnType> callback) {
    Objects.requireNonNull(event, "The event cannot be null.");
    Objects.requireNonNull(callback, "The callback cannot be null.");
    getLogger().debug("Received an event for publication: {}", event);

    publishInternal(event, callback);

    getLogger().debug("Dispatched event {}", event);
  }

  protected <ReturnType> void publishInternal(Object event, EventCallback<ReturnType> callback) {
    new EventDispatcher<>(event, callback, invokerRepository, interceptorChainFactory)
        .dispatch();
  }


  protected Logger getLogger() {
    return logger;
  }

  // optional setters to override behaviour.

  public void setEventHandlerInvokerFactory(List<EventHandlerInvokerFactory> eventHandlerInvokerFactories) {
    if (eventHandlerInvokerFactories == null || eventHandlerInvokerFactories.isEmpty()) {
      throw new IllegalArgumentException("At least one eventHandlerInvokerFactory should be configured.");
    }
    this.eventHandlerInvokerFactories = eventHandlerInvokerFactories;
  }
}

