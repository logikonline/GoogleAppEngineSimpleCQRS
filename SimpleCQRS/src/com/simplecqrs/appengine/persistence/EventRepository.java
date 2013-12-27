package com.simplecqrs.appengine.persistence;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.UUID;

import com.simplecqrs.appengine.domain.AggregateRoot;
import com.simplecqrs.appengine.messaging.Event;

/**
 * Implementation of a simple event repository
 * @param <T>
 */
public class EventRepository<T extends AggregateRoot> implements Repository<T> {

	/**
	 * Instance of the event store
	 */
	private EventStore eventStore;
	
	/**
	 * The class type that the repository is working with
	 */
	private Class<T> aClass;
	
	public EventRepository(Class<T> aClass){
		this.aClass = aClass;
		eventStore = new AppEngineEventStore();
	}
	
	@Override
	public void save(T aggregate) throws EventCollisionException {
		eventStore.saveEvents(aggregate.getId(), aggregate.getExpectedVersion(), aggregate.getUncommittedChanges());
		aggregate.markChangesAsCommitted();
	}

	@Override
	public T getById(UUID id) throws NoSuchMethodException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		T aggregate = null;

		/*
		 * get the events from the event store
		 */
		Iterable<Event> history = eventStore.getEvents(id);
		
		/*
		 * if there aren't any items then just return null 
		 */
		if(history == null)
			return aggregate;
		
		/*
		 * create a new instance of the object by calling
		 * the constructor that takes the aggregate's id
		 */
		Constructor<T> constructor = aClass.getDeclaredConstructor(UUID.class);
		constructor.setAccessible(true);
		aggregate = constructor.newInstance(id);
		
		/*
		 * load the aggregate with the events
		 */
		aggregate.loadFromHistory(history);
			
		return aggregate;
	}
}
