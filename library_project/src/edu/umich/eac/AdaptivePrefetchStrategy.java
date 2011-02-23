package edu.umich.eac;

import edu.umich.eac.PrefetchStrategy;
import edu.umich.eac.FetchFuture;

class AdaptivePrefetchStrategy extends PrefetchStrategy {
    public void handlePrefetch(FetchFuture<?> prefetch) {
        /* Possible actions to do with a prefetch:
         * a) Issue it now
         * b) Re-schedule this decision X seconds from now
         * 
         * How to pick between these?
         * 
         * if the time is right and I have enough supply:
         *   issue it now
         * else if the time is right but I don't have enough supply:
         *   compute the earliest possible time that I'll have enough
         *   reschedule decision for then
         * else if the time's not right but I have enough supply:
         *   compute the priority weight of latency vs. resource conservation
         *   if latency has total priority:
         *     issue now
         *   else if latency has higher priority:
         *     reschedule for earliest time that latency takes total priority
         *   else:
         *     reschedule for earliest time that latency takes higher priority
         * else if the time's not right and I don't have enough supply:
         *   reschedule for earliest time that I might have enough supply
         * 
         * 
         * Breaking down what this means a bit:
         * 1) "Enough supply" means that supply exceeds predicted demand
         *    by at least the computed threshold (Odyssey-style; 
         *    SOSP '99, Section 5.1.3, "Triggering adaptation")
         * 2) "The time is right" means that I currently have the best
         *    network conditions, for the foreseeable future, to minimize
         *    resource usage and fetch latency
         * 3) "The earliest possible time that I'll have enough"
         *    means the time when, if resource demand dropped to the
         *    baseline starting now, the supply would exceed the
         *    predicted demand by at least the computed threshold.
         * 4) "The priority weight of latency vs. resource conservation" 
         *    means the TIP-style cost/benefit analysis determining whether
         *    reducing request latency or conserving the limited resource
         *    is more important right now.  I still haven't quite nailed 
         *    this down yet.  
         *    
         *    This could be a function of (the time to the goal and the energy
         *    supply and demand) that gives a number in the range [0.0, 1.0], 
         *    where 0.0 means reducing latency is infinitely more important than
         *    conserving the resource, and 1.0 means the opposite.
         *    
         *    Issue: 
         *       This now feels redundant with the "enough supply" notion 
         *       that comes from Jason's papers; i.e. just another way to
         *       make a decision about the same question.  Is there anything
         *       else here?
         *       If this is redundant and we don't need it, the desired result
         *       is as if this function exists but always returns 0.0; we 
         *       replace this entire conditional sequence with "issue it now,"
         *       and the "priority" is already captured by the "enough supply"
         *       threshold.
         * 
         *
         * For the time being, supply and demand will be measured in percent of
         * full battery capacity rather than Joules, since the G1 doesn't
         * provide current measurements on the battery. For future reference,
         * the Nexus One does provide current measurements.
         * Alternatively, I might be able to get these measurements from the 
         * PowerTutor power monitor by parsing its ongoing log file.
         * 
         * The relation to the Odyssey energy adaptation seems fairly direct,
         * if fetch response time is our analog to fidelity.
         * 
         */
    }
}
