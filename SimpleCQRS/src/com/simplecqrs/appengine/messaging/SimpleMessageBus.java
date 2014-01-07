package com.simplecqrs.appengine.messaging;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.simplecqrs.appengine.persistence.AggregateHydrationException;
import com.simplecqrs.appengine.persistence.EventCollisionException;

/**
 * Implementation of a simple message bus for publishing
 * events and executing commands
 */
public class SimpleMessageBus implements MessageBus {

	private Map<String, CommandHandler<? extends Command>> commandHandlers;
	private Map<String, List<Class<? extends EventHandler<? extends Event>>>> eventHandlers;
	
	private SimpleMessageBus(){
		commandHandlers = new HashMap<String, CommandHandler<? extends Command>>();
		eventHandlers = new HashMap<String, List<Class<? extends EventHandler<? extends Event>>>>();
	}
	
	public static SimpleMessageBus getInstance(){
		return InstanceHolder.INSTANCE;
	}
	
	private static SimpleMessageBus create(){
		return new SimpleMessageBus();
	}
	
	private static class InstanceHolder{
		public static final SimpleMessageBus INSTANCE = SimpleMessageBus.create();
	}

	@Override
	public <T extends Command> void registerCommandHandler(Class<T> aClass, CommandHandler<T> handler) {
		String key = aClass.getName();
		
		if(!commandHandlers.containsKey(key)){
			commandHandlers.put(key, handler);
		}
	}
	
	@Override
	public <T extends Event, H extends EventHandler<T>> void registerEventHandler(Class<T> aClass, Class<H> handler) {
		
		String key = aClass.getName();
		
		if(!eventHandlers.containsKey(key)){
			eventHandlers.put(key, new ArrayList<Class<? extends EventHandler<? extends Event>>>());
		}
	
		eventHandlers.get(key).add(handler);
	}

	@Override
	public <T extends Event> void publish(T event) {
		publish(event, null);
	}
	
	@Override
	public <T extends Event> void publish(T event, String queue) {
		if(event == null || eventHandlers.isEmpty())
			return;
		
		String key = event.getClass().getName();
		
		if(!eventHandlers.containsKey(key))
			return;
		
		List<Class<? extends EventHandler<? extends Event>>> handlersForType = eventHandlers.get(key);
		
		for(Class<? extends EventHandler<? extends Event>> handler : handlersForType){
			
			Constructor<? extends EventHandler<? extends Event>> constructor = null;
			try {
				
				constructor = handler.getDeclaredConstructor(event.getClass());
				
			} catch (NoSuchMethodException | SecurityException e) {
				MessageLog.log(e);
			}
			
			constructor.setAccessible(true);
		
			try {
				
				Queue taskQueue = null;
				
				if(queue != null)
					taskQueue = QueueFactory.getQueue(queue);
				else
					taskQueue = QueueFactory.getDefaultQueue();
				
				taskQueue.addAsync(TaskOptions.Builder.withPayload((DeferredTask) constructor.newInstance(event)));
				
			} catch (InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException e) {
				MessageLog.log(e);
			}
		}
	}

	@Override
	public <T extends Command> void send(T command) throws EventCollisionException, AggregateHydrationException {
		
		if(command == null || commandHandlers.isEmpty())
			return;
		
		String key = command.getClass().getName();
		
		if(!commandHandlers.containsKey(key))
			return;
		
		CommandHandler<?> handlerForType = commandHandlers.get(key);
		
		/**
		 * Execute the first handler. Commands *should* normally
		 * have a single handler.
		 */
		@SuppressWarnings("unchecked")
		CommandHandler<T> handler = (CommandHandler<T>) handlerForType;
		handler.handle(command);
	}
}