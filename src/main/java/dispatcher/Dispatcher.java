package dispatcher;

import dispatcher.policies.Policy;
import driver.Driver;
import trip.Trip;
import logbook.EventType;
import driver.Message;

import java.util.*;

/**
 * Similar to CPU scheduler. The dispatcher decides which trip the driver drives next, following its policy.
 * The driver calls {@code dispatch()} on every tick it spends at the company instead of driving.
 * <p>
 * The dispatcher works the same way for every policy: it keeps the ready line, the waiting list, and the list of
 * finished trips, handles the messages, and counts how long each stretch is. The policy only decides which trip in
 * the ready line is driven next.
 */
public class Dispatcher {

    private final Driver driver;
    private final Policy policy;

    /**
     * The trip the driver is currently driving, or null if there is none.
     */
    private Trip current;

    /**
     * The ready line, in the order the trips joined it, oldest first.
     */
    private final List<Trip> readyLine = new LinkedList<>();
    private final List<Trip> waitingLine = new LinkedList<>();
    private final List<Trip> finishedTrips = new LinkedList<>();
    private int currentStretchLength = 0;
    private int lastDispatchTick = 0;

    /**
     * Creates a dispatcher for the given driver and sets the driver's dispatcher to this new dispatcher
     * by calling {@code driver.setDispatcher()}.
     *
     * @param driver the driver the dispatcher is for
     * @param policy the policy that decides which trip in the ready line is driven next
     */
    public Dispatcher(Driver driver, Policy policy) {
        this.driver = driver;
        this.policy = policy;
        driver.setDispatcher(this);
    }

    /**
     * Handles all messages in the driver's message queue, and accordingly picks the driver's next trip if necessary.
     * <p>
     * Messages are handled oldest first:
     * <ul>
     *     <li>{@code STOP_SIGN} moves the current trip to the back of the ready line.</li>
     *     <li>{@code RED_LIGHT} moves the current trip to the waiting list.</li>
     *     <li>{@code GREEN_LIGHT} moves the trip with the message's vehicle ID from the waiting list to the back of the
     *     ready line.</li>
     *     <li>{@code DESTINATION} moves the current trip to the list of finished trips and logs its {@code TRIP_COMPLETE} event.</li>
     *     <li>{@code TIMER} moves the current trip to the back of the ready line, like {@code STOP_SIGN}, even in the
     *     middle of a road. Does nothing if the current trip has already left on the same tick.</li>
     * </ul>
     * Each time the driver leaves the current trip, its progress is saved by calling {@code saveProgress()} with
     * the driver's next address ({@code driver.getNextAddress()}) and the number of ticks the trip was just driven.
     * <p>
     * If there is no current trip and the ready line is not empty, the next trip is picked by calling
     * {@code policy.pickNext()} with an unmodifiable view of the ready line, so the policy cannot change it.
     * If the policy has a time slice, the driver's timer is started for the picked trip by calling
     * {@code driver.startTimer()}. The new current trip, or null if there is none, is handed to the driver by calling
     * {@code driver.setTrip()}. If the current trip has not left, do not call {@code driver.setTrip()}: the driver is
     * still in its vehicle, and {@code setTrip()} would move it back to the address last saved in the trip.
     * <p>
     * The stretch length passed to {@code saveProgress()} is the number of ticks the current trip has been driven since
     * it was picked, not counting ticks at the dispatcher. A tick at the dispatcher that only handles
     * {@code GREEN_LIGHT} messages leaves the current trip driving, so its count carries on afterward instead of
     * starting again. The current tick number is {@code driver.getClock().getTickNum()}.
     */
    public void dispatch() {
        boolean tripLeftThisTick = false;
        int currentTick = driver.getClock().getTickNum();

        if(current != null && currentTick > lastDispatchTick){
            currentStretchLength += currentTick - lastDispatchTick - 1;
        }
        lastDispatchTick = currentTick;
        // if(current == null && !readyLine.isEmpty()){
        //     current = policy.pickNext(Collections.unmodifiableList(readyLine));
        //     readyLine.remove(current);
        //     if(policy.timeSlice() != 0){
        //         driver.startTimer(policy.timeSlice());
        //     }
        //     if(tripLeftThisTick || isNull){
        //         driver.setTrip(current);
        //     }
        // }
        // if(current != null){
        //     currentStretchLength = driver.getClock().getTickNum();
        // }


        while(!driver.getMessageQueue().isEmpty()){
            Message m = driver.getMessageQueue().poll();
            if(m.type() == (Message.Type.STOP_SIGN)){
                if(current != null){
                    current.saveProgress(driver.getNextAddress(), currentStretchLength);
                    currentStretchLength = 0;
                    readyLine.add(current);
                    current = null;
                    tripLeftThisTick = true;
                }
                
            }
            else if (m.type() == (Message.Type.RED_LIGHT)){
                if(current != null){
                    current.saveProgress(driver.getNextAddress(), currentStretchLength);
                    currentStretchLength = 0;
                    waitingLine.add(current);
                    current = null;
                    tripLeftThisTick = true;
                }
            }
            else if (m.type() == (Message.Type.GREEN_LIGHT)){
                for(int i = 0; i < waitingLine.size(); i++){
                    Trip trip = waitingLine.get(i);
                    if(trip.getVehicle().id() == m.vehicleId()){
                        readyLine.add(waitingLine.get(i));
                        waitingLine.remove(waitingLine.get(i));

                        break;
                    }
                }
            }
            else if (m.type() == (Message.Type.DESTINATION)){
                if (!(current == null)){
                    current.saveProgress(driver.getNextAddress(), currentStretchLength);
                    currentStretchLength = 0;
                    finishedTrips.add(current);
                    driver.getLogbook().record(current.getVehicle(), EventType.TRIP_COMPLETE);
                    current = null;
                    tripLeftThisTick = true;
                }

            }
            else if (m.type() == (Message.Type.TIMER)){
                if(!tripLeftThisTick && current != null){
                    current.saveProgress(driver.getNextAddress(), currentStretchLength);
                    currentStretchLength = 0;
                    readyLine.add(current);

                    current = null;
                    tripLeftThisTick = true;
                }

            }

            
        }

        boolean picked = false;
        if(current == null && !readyLine.isEmpty()){
            current = policy.pickNext(Collections.unmodifiableList(readyLine));
            readyLine.remove(current);
            currentStretchLength = 0;
            if(policy.timeSlice() != 0){
                driver.startTimer(policy.timeSlice());
            }
            picked = true;

        }

        if(picked || tripLeftThisTick){
            driver.setTrip(current);
        }
    }

    /**
     * Returns the trip the driver is currently driving.
     *
     * @return the current trip, or null if there is none
     */
    public Trip getCurrent() {
        return current;
    }

    /**
     * Adds the given Trip to the back of the dispatcher's ready line and logs the trip's {@code TRIP_START} event.
     *
     * @param trip the Trip to add
     */
    public void addNewTrip(Trip trip) {
        readyLine.add(trip);
        driver.getLogbook().record(trip.getVehicle(),EventType.TRIP_START);
    }

    /**
     * Returns the total number of trips that have been added to the dispatcher and have not finished.
     * Includes the current trip, the ready line, and the waiting list.
     *
     * @return the total number of unfinished trips
     */
    public int getNumTrips() {
        int size = 0;
        //current trip
        if(current != null){
            size += 1;
        }
        size += readyLine.size();
        size += waitingLine.size();
        return size;
    }

    /**
     * Returns the number of trips in the ready line, not including the current trip.
     *
     * @return the number of trips in the ready line
     */
    public int getReadyLineSize() {
        return readyLine.size();
    }

    /**
     * Returns the number of trips in the waiting list.
     *
     * @return the number of trips in the waiting list
     */
    public int getWaitingListSize() {
        return waitingLine.size();
    }

    /**
     * Returns the list of finished trips, in the order they finished.
     *
     * @return the list of finished trips.
     */
    public List<Trip> getFinished() {
        return finishedTrips;
    }
}
