/* 
 * Copyright 2004-2006 OpenSymphony 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy 
 * of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 */
package org.quartz.simpl;

import java.util.Date;

import junit.framework.TestCase;

import org.quartz.JobDetail;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.jobs.NoOpJob;
import org.quartz.spi.JobStore;
import org.quartz.spi.SchedulerSignaler;

/**
 * Unit test for RAMJobStore.  These tests were submitted by Johannes Zillmann
 * as part of issue QUARTZ-306.
 */
public class RAMJobStoreTest extends TestCase {
    private JobStore fJobStore;
    private JobDetail fJobDetail;
    private SampleSignaler fSignaler;

    protected void setUp() throws Exception {
        this.fJobStore = new RAMJobStore();
        this.fSignaler = new SampleSignaler();
        this.fJobStore.initialize(null, this.fSignaler);

        this.fJobDetail = new JobDetail("job1", "jobGroup1", NoOpJob.class);
        this.fJobDetail.setDurability(true);
        this.fJobStore.storeJob(null, this.fJobDetail, false);
    }

    public void testAcquireNextTrigger() throws Exception {
        Trigger trigger1 = 
            new SimpleTrigger("trigger1", "triggerGroup1", this.fJobDetail.getName(), 
                    this.fJobDetail.getGroup(), new Date(System.currentTimeMillis() + 200000), 
                    new Date(System.currentTimeMillis() + 200000), 2, 2000);
        Trigger trigger2 = 
            new SimpleTrigger("trigger2", "triggerGroup1", this.fJobDetail.getName(), 
                    this.fJobDetail.getGroup(), new Date(System.currentTimeMillis() - 100000),
                    new Date(System.currentTimeMillis() + 20000), 2, 2000);
        Trigger trigger3 = 
            new SimpleTrigger("trigger1", "triggerGroup2", this.fJobDetail.getName(), 
                    this.fJobDetail.getGroup(), new Date(System.currentTimeMillis() + 100000), 
                    new Date(System.currentTimeMillis() + 200000), 2, 2000);

        trigger1.computeFirstFireTime(null);
        trigger2.computeFirstFireTime(null);
        trigger3.computeFirstFireTime(null);
        this.fJobStore.storeTrigger(null, trigger1, false);
        this.fJobStore.storeTrigger(null, trigger2, false);
        this.fJobStore.storeTrigger(null, trigger3, false);

        assertNull(this.fJobStore.acquireNextTrigger(null, 10));
        assertEquals(
            trigger2, 
            this.fJobStore.acquireNextTrigger(null, new Date(trigger1.getNextFireTime().getTime()).getTime() + 10000));
        assertEquals(
            trigger3, 
            this.fJobStore.acquireNextTrigger(null, new Date(trigger1.getNextFireTime().getTime()).getTime() + 10000));
        assertEquals(
            trigger1, 
            this.fJobStore.acquireNextTrigger(null, new Date(trigger1.getNextFireTime().getTime()).getTime() + 10000));
        assertNull(
            this.fJobStore.acquireNextTrigger(null, new Date(trigger1.getNextFireTime().getTime()).getTime() + 10000));

        // because of trigger2
        assertEquals(1, this.fSignaler.fMisfireCount);

        // release trigger3
        this.fJobStore.releaseAcquiredTrigger(null, trigger3);
        assertEquals(
            trigger3, 
            this.fJobStore.acquireNextTrigger(null, new Date(trigger1.getNextFireTime().getTime()).getTime() + 10000));
    }

    public void testTriggerStates() throws Exception {
        Trigger trigger = 
            new SimpleTrigger("trigger1", "triggerGroup1", this.fJobDetail.getName(), this.fJobDetail.getGroup(), 
                    new Date(System.currentTimeMillis() + 100000), new Date(System.currentTimeMillis() + 200000), 2, 2000);
        trigger.computeFirstFireTime(null);
        assertEquals(Trigger.STATE_NONE, this.fJobStore.getTriggerState(null, trigger.getName(), trigger.getGroup()));
        this.fJobStore.storeTrigger(null, trigger, false);
        assertEquals(Trigger.STATE_NORMAL, this.fJobStore.getTriggerState(null, trigger.getName(), trigger.getGroup()));
    
        this.fJobStore.pauseTrigger(null, trigger.getName(), trigger.getGroup());
        assertEquals(Trigger.STATE_PAUSED, this.fJobStore.getTriggerState(null, trigger.getName(), trigger.getGroup()));
    
        this.fJobStore.resumeTrigger(null, trigger.getName(), trigger.getGroup());
        assertEquals(Trigger.STATE_NORMAL, this.fJobStore.getTriggerState(null, trigger.getName(), trigger.getGroup()));
    
        trigger = this.fJobStore.acquireNextTrigger(null,
                new Date(trigger.getNextFireTime().getTime()).getTime() + 10000);
        assertNotNull(trigger);
        this.fJobStore.releaseAcquiredTrigger(null, trigger);
        trigger=this.fJobStore.acquireNextTrigger(null,
                new Date(trigger.getNextFireTime().getTime()).getTime() + 10000);
        assertNotNull(trigger);
        assertNull(this.fJobStore.acquireNextTrigger(null,
                new Date(trigger.getNextFireTime().getTime()).getTime() + 10000));
    }

    public static class SampleSignaler implements SchedulerSignaler {
        int fMisfireCount = 0;

        public void notifyTriggerListenersMisfired(Trigger trigger) {
            fMisfireCount++;
        }

        public void signalSchedulingChange() {
        }

        public void notifySchedulerListenersFinalized(Trigger trigger) {
        }
    }
}
