package eu.tripled.eventbus.synchronous;

import com.google.common.base.Preconditions;
import eu.tripled.eventbus.EventBusInterceptor;
import eu.tripled.eventbus.EventCallback;
import eu.tripled.eventbus.EventPublisher;
import eu.tripled.eventbus.EventSubscriber;
import eu.tripled.eventbus.annotation.Handles;
import eu.tripled.eventbus.callback.ExceptionThrowingEventCallback;
import eu.tripled.eventbus.dispatcher.EventDispatcher;
import eu.tripled.eventbus.interceptor.InterceptorChainFactory;
import eu.tripled.eventbus.invoker.EventHandlerInvoker;
import eu.tripled.eventbus.invoker.EventHandlerInvokerRepository;
import org.reflections.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Synchronous implementation of the CommandDispatcher.
 */
public class SynchronousEventBus implements EventPublisher, EventSubscriber {

  private final Logger logger = LoggerFactory.getLogger(SynchronousEventBus.class);

  private final EventHandlerInvokerRepository invokerRepository;
  private final InterceptorChainFactory interceptorChainFactory;

  // constructors

  public SynchronousEventBus() {
    this.invokerRepository = new EventHandlerInvokerRepository();
    this.interceptorChainFactory = new InterceptorChainFactory();
  }

  public SynchronousEventBus(List<EventBusInterceptor> interceptors) {
    this.invokerRepository = new EventHandlerInvokerRepository();
    this.interceptorChainFactory = new InterceptorChainFactory(interceptors);
  }

  // subscribe methods

  @Override
  public void subscribe(Object eventHandler) {
    Set<Method> methods = ReflectionUtils.getAllMethods(eventHandler.getClass(),
        ReflectionUtils.withAnnotation(Handles.class));

    for (Method method : methods) {
      Handles annotation = method.getAnnotation(Handles.class);
      subscribeInternal(eventHandler, annotation.value(), method);
    }
  }

  protected void subscribeInternal(Object eventHandler, Class<?> eventType, Method method) {
    EventHandlerInvoker invoker = new EventHandlerInvoker(eventType, eventHandler, method);
    invokerRepository.addEventHandlerInvoker(invoker);
  }

  // publish methods

  @Override
  public void publish(Object message) {
    publish(message, new ExceptionThrowingEventCallback<>());
  }

  @Override
  public <ReturnType> void publish(Object event, Future<ReturnType> callback) {
    Preconditions.checkArgument(callback instanceof EventCallback, "The callback should be an instance of EventCallBack.");
    publish(event, (EventCallback<ReturnType>) callback);
  }

  @Override
  public <ReturnType> void publish(Object event, EventCallback<ReturnType> callback) {
    Preconditions.checkArgument(event != null, "The event cannot be null.");
    Preconditions.checkArgument(callback != null, "The callback cannot be null.");
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

}
