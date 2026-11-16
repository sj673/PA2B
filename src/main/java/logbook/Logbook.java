package logbook;

import trip.Road;
import trip.Vehicle;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * The logbook: a queue of events in the order they happened. Used by the tests to verify behavior.
 * <p>
 * In part B several drivers write to the logbook at the same time, and a reader can wait for the next event with
 * {@code take()}, so every method must be thread-safe. Keep the {@code LinkedList}: make it safe with
 * {@code synchronized}, {@code wait()} and {@code notify()} or {@code notifyAll()}, not with a class from
 * {@code java.util.concurrent}.
 */
public class Logbook {

    /**
     * The events, oldest first. It must stay a {@code LinkedList}, which is not thread-safe on its own.
     */
    private final Queue<Event> queue = new LinkedList<>();
    private int eventCounter;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition eventAvailable = lock.newCondition();

    /**
     * Creates an event with the given parameters and adds it to the logbook with
     * an event ID 1 greater than the previous event added to the logbook.
     * The first event added has ID 1.
     *
     * @param vehicle the event's vehicle
     * @param road    the event's road
     * @param type    the event's type
     */
    public void record(Vehicle vehicle, Road road, EventType type) {
        lock.lock();
        try{
            queue.add(new Event(vehicle, road, type, ++eventCounter));
            eventAvailable.signalAll();
        }finally{
            lock.unlock();
        }
        
    }

    public void record(Vehicle vehicle, EventType type) {
        lock.lock();
        try{
            record(vehicle, null, type);
            eventAvailable.signalAll();
        }finally{
            lock.unlock();
        }
    }

    public void record(EventType type) {
        lock.lock();
        try{
            record(null, null, type);
            eventAvailable.signalAll();
        }finally{
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of the logbook, or returns null if the logbook is empty.
     *
     * @return the head of the logbook, or null if the logbook is empty
     */
    public Event poll() {
        lock.lock();
        try{
            return queue.poll();
        }finally{
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of the logbook, waiting if necessary until an event becomes available.
     *
     * @return the head of the logbook
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    public Event take() throws InterruptedException {
        lock.lock();
        try{
            while(queue.isEmpty()){
                eventAvailable.await();
            }

            return poll();

        }finally{
            lock.unlock();
        }

    }

    /**
     * Removes all events from the logbook and resets the ID counter.
     */
    public void clear() {
        lock.lock();
        try{
            queue.clear();
            eventCounter = 0;
        }finally{
            lock.unlock();
        }
        
    }

    /**
     * Returns the current event ID in the sequence. Starts at 0 when no events have been added to the logbook.
     *
     * @return the ID of the most recent event, or 0 if no events have been added
     */
    public int getCurrentEventId() {
        lock.lock();
        try{
            return eventCounter;
        }finally{
            lock.unlock();
        }
        
    }

    /**
     * Returns the current size of the logbook.
     *
     * @return the number of events in the logbook
     */
    public int size() {
        lock.lock();
        try{
            return queue.size();
        }finally{
            lock.unlock();
        }
        
    }
}
