/*
 * Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
 * 2009 Royal Institute of Technology (KTH)
 *
 * GVoD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package se.kth.swim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.InfoPiggyback;
import se.kth.swim.msg.net.InfoType;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class SwimComp extends ComponentDefinition {

    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);

    private final NatedAddress selfAddress;
    private final Set<NatedAddress> bootstrapNodes;
    private final NatedAddress aggregatorAddress;
    
    private ArrayList<InfoPiggyback> updates = new ArrayList<InfoPiggyback>();
            
    private UUID pingTimeoutId;
    private UUID statusTimeoutId;
    private HashMap<NatedAddress,UUID> ackTimeoutIds = new HashMap<NatedAddress,UUID>();

    private int receivedPings = 0;
    private int receivedPongs = 0;
    
    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.bootstrapNodes = init.bootstrapNodes;
        for(NatedAddress na : bootstrapNodes){
                 log.info("{} showing member list -> {}.", new Object[]{selfAddress.getId(), na.getId()});
            }
        this.aggregatorAddress = init.aggregatorAddress;
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleAckTimeout, timer);
        
    }

    private Handler<Start> handleStart = new Handler<Start>() {

        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress.getId()});  
            
            if (!bootstrapNodes.isEmpty()) {
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }

    };
    private Handler<Stop> handleStop = new Handler<Stop>() {

        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }

    };

    private Handler<NetPing> handlePing = new Handler<NetPing>() {

        @Override
        public void handle(NetPing event) {
            log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPings++;
            
            //Ping from unknown node
            if(!bootstrapNodes.contains(event.getHeader().getSource())){
                log.info("{} Received ping from node that is not in my membership list {}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
                bootstrapNodes.add(event.getHeader().getSource());
                
                //Maybe it is the first node in the list -> start pingTimeout
                if (pingTimeoutId == null) {
                    schedulePeriodicPing();
                }
                //save the now info to be disseminated
                 updates.add(new InfoPiggyback(InfoType.NEWNODE,event.getHeader().getSource()));
                 log.info("{} New node info added in the membership list. Info added is -> {}", new Object[]{selfAddress.getId(), updates.get(updates.size()-1).getInfoTarget().getId()});
            }
            
             
            log.info("{} sending pong to back to :{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});            
            trigger(new NetPong(selfAddress, event.getHeader().getSource(), updates), network);
        }

    };
    
        private Handler<NetPong> handlePong = new Handler<NetPong>() {

        @Override
        public void handle(NetPong event) {
            log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            
            //handle the ack mechanism
            if(ackTimeoutIds.containsKey(event.getSource())){
                System.out.println("Ricevuto ack");
                cancelAck(event.getSource());
            }
          

            if(event.getContent().infoList!=null){
                log.info("There is a list in Pong msg", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
         
                for(Object ipb2 : event.getContent().infoList){
                    InfoPiggyback ipb = (InfoPiggyback)ipb2;
                    log.info("{} Iterating update list in the Pong message {}", new Object[]{selfAddress.getId(), ipb.getInfoTarget()});
                     
                    if(ipb.getInfoType()==InfoType.NEWNODE){
                        if(!ipb.getInfoTarget().equals(selfAddress) && !bootstrapNodes.contains(ipb.getInfoTarget())){
                             log.info("{} updating via pong... Adding to the list -> {}", new Object[]{selfAddress.getId(), ipb.getInfoTarget().getId()});
                            bootstrapNodes.add(ipb.getInfoTarget());
                            //TODO ALSO UPDATE THE UPDATE LIST
                        }
                    } else if (ipb.getInfoType()==InfoType.DEADNODE){
                        if(bootstrapNodes.contains(ipb.getInfoTarget())){
                              log.info("{} updating via pong... Removing from the list -> {}", new Object[]{selfAddress.getId(), ipb.getInfoTarget().getId()});
                            bootstrapNodes.remove(ipb.getInfoTarget());
                             //TODO ALSO UPDATE THE UPDATE LIST
                        }
                    }
                }
            }
            
            receivedPongs++;
        }

    };

    private Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {

        @Override
        public void handle(PingTimeout event) {
            
            if(bootstrapNodes.isEmpty()){
                System.out.println("LISTA VUOTA");
                cancelPeriodicPing();
            } else {
                if(pingTimeoutId==null){
                    schedulePeriodicPing();
                }
            }
 
            for (NatedAddress partnerAddress : bootstrapNodes) {
                //check if still waiting for an answer from the node
                if(!ackTimeoutIds.containsKey(partnerAddress)){
                    log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});
                    scheduleAck(partnerAddress);
                    trigger(new NetPing(selfAddress, partnerAddress), network);
                } else {
                    log.info("{} FAIL sending ping to partner (waiting for ack):{}", new Object[]{selfAddress.getId(), partnerAddress});
                }
            }
            
        }

    };

    private Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {

        @Override
        public void handle(StatusTimeout event) {
            log.info("{} sending status to aggregator:{}", new Object[]{selfAddress.getId(), aggregatorAddress});
            trigger(new NetStatus(selfAddress, aggregatorAddress, new Status(receivedPings)), network);
        }

    };
    
    private Handler<AckTimeout> handleAckTimeout = new Handler<AckTimeout>() {

        @Override
        public void handle(AckTimeout event) {
            log.info("{} no answer from node: {}", new Object[]{selfAddress.getId(), event.getAddress().getId()});
              ackTimeoutIds.remove(event.getAddress());
            //unrelated info
            for(NatedAddress i : ackTimeoutIds.keySet())
                System.out.println("In the ack list " + i.getId());
            //update info list
            //maybe we can delete the old info related to the DEAD node
            updates.add(new InfoPiggyback(InfoType.DEADNODE, event.getAddress()));
            if(bootstrapNodes.contains(event.getAddress()))
                System.out.println("Removing node " + event.getAddress().getId() + " from the member list of " + selfAddress.getId());
                bootstrapNodes.remove(event.getAddress());
        }

    };

    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }

    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(10000, 10000);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }

    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }
    
    private void scheduleAck(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(20000);
        AckTimeout sc = new AckTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        ackTimeoutIds.put(address,sc.getTimeoutId());
        trigger(spt, timer);
    }

    private void cancelAck(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(ackTimeoutIds.get(address));
        System.out.println("Canceling ACK  TIMEOUT");
        trigger(cpt, timer);
        ackTimeoutIds.remove(address);
    }

    public static class SwimInit extends Init<SwimComp> {

        public final NatedAddress selfAddress;
        public final Set<NatedAddress> bootstrapNodes;
        public final NatedAddress aggregatorAddress;

        public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
        }
    }

    private static class StatusTimeout extends Timeout {

        public StatusTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }

    private static class PingTimeout extends Timeout {

        public PingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    private static class AckTimeout extends Timeout {

        private final NatedAddress address;
        
        public AckTimeout(ScheduleTimeout request, NatedAddress address) {
            super(request);
            this.address = address;
        }

        /**
         * @return the address
         */
        public NatedAddress getAddress() {
            return address;
        }
    }
}
