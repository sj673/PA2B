package dispatcher.policies;

import trip.Trip;

import java.util.List;

/**
 * The VIP First policy.
 * The trip with the lowest priority number goes first, since it is the most important.
 * Trips with the same priority number go in the order they joined the ready line.
 */
public class VIPFirst implements Policy {

    @Override
    public Trip pickNext(List<Trip> readyLine) {
        Trip f = null;
        double lowest = Double.MAX_VALUE;
    
        for(int i = 0; i < readyLine.size(); i++){
            Trip trip = readyLine.get(i);
            if(lowest > trip.getPriority()){
                lowest = trip.getPriority();
                f = trip;
            }
        }

        return f;
    }
}
