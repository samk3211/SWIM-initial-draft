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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
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


public class SwimComp extends ComponentDefinition {

    private static final Integer DISSEMINATION_VALUE = 3;
    
    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private final NatedAddress selfAddress;
    private final MembershipList membershipList;
    //private final Set<NatedAddress> neighboursNodes;
    private final NatedAddress aggregatorAddress;
    //private Set<NatedAddress> suspectedNodes = new HashSet<NatedAddress>();
    
    
    private ArrayList<InfoPiggyback> updates = new ArrayList<InfoPiggyback>();
    private HashMap<InfoPiggyback, Integer> updatesDisseminationCounter = new HashMap<InfoPiggyback, Integer>();
    
    
    
    private UUID pingTimeoutId;
    private UUID statusTimeoutId;
    private HashMap<NatedAddress,UUID> ackTimeoutIds = new HashMap<NatedAddress,UUID>();
    private HashMap<NatedAddress,UUID> deadTimeoutIds = new HashMap<NatedAddress,UUID>();
    
    private int receivedPings = 0;
    private int receivedPongs = 0;
    
    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.membershipList = new MembershipList(init.bootstrapNodes,selfAddress);
        this.aggregatorAddress = init.aggregatorAddress;
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleAckTimeout, timer);
        subscribe(handleDeadTimeout, timer);
        
    }
    
    private Handler<Start> handleStart = new Handler<Start>() {
        
        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress.getId()});
            
            if (!membershipList.isEmpty()) {
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
            printmyneigh();
            log.info("{} received ping from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            receivedPings++;
            
            //Ping from unknown node
            if(!membershipList.contains(event.getHeader().getSource())){
                log.info("{} Received ping from node that is not in my membership list {}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
                log.info("{} adding happily the node cccc {}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
                membershipList.add(event.getHeader().getSource());
                
                //Maybe it is the first node in the list -> start pingTimeout
                if (pingTimeoutId == null) {
                    schedulePeriodicPing();
                }
                
                //save the now info to be disseminated
                updatesDisseminationCounter.put(new InfoPiggyback(InfoType.NEWNODE,event.getHeader().getSource()), DISSEMINATION_VALUE);
                
            } else {
                if(membershipList.isSuspected(event.getHeader().getSource())){
                    membershipList.unsuspectNode(event.getHeader().getSource());
                    updatesDisseminationCounter.put(new InfoPiggyback(InfoType.ALIVENODE,event.getHeader().getSource()), DISSEMINATION_VALUE);
                }
            }
            
            log.info("{} sending pong to back to :{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            updates = new ArrayList<InfoPiggyback>(updatesDisseminationCounter.keySet());
            trigger(new NetPong(selfAddress, event.getHeader().getSource(), updates), network);
            
            //decrease counters and remove 0
            Collection<InfoPiggyback> coll = new LinkedList<InfoPiggyback>();
            for(InfoPiggyback ipb : updatesDisseminationCounter.keySet()){
                coll.add(ipb);
            }
            for(InfoPiggyback ipb : coll){
                int newCounter = updatesDisseminationCounter.get(ipb);
                newCounter--;
                if(newCounter==0){
                    updatesDisseminationCounter.remove(ipb);
                } else {
                    updatesDisseminationCounter.put(ipb, newCounter);
                }
            }
            
            
        }
    };
    
    private Handler<NetPong> handlePong = new Handler<NetPong>() {
        
        @Override
        public void handle(NetPong event) {
            printmyneigh();
            log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            
            //handle the ack mechanism
            if(membershipList.contains(event.getSource())){
                System.out.println("Ricevuto ack");
                cancelAck(event.getSource());
            }
            
            //Control that the pong is from a suspected node
            if(membershipList.isSuspected(event.getHeader().getSource())){
                membershipList.unsuspectNode(event.getHeader().getSource());
                updatesDisseminationCounter.put(new InfoPiggyback(InfoType.ALIVENODE,event.getHeader().getSource()), DISSEMINATION_VALUE);
            }
            
            
            if(event.getContent().infoList!=null && event.getContent().infoList.size()>0){
                log.info("There is a list in Pong msg from ", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
                for(Object ipb2 : event.getContent().infoList){
                    InfoPiggyback ipb = (InfoPiggyback)ipb2;
                    
                    //change to switch-case man :)
                    if(ipb.getInfoType()==InfoType.NEWNODE){
                        if(!ipb.getInfoTarget().equals(selfAddress) && !membershipList.contains(ipb.getInfoTarget())){
                            //log.info("{} adding happily the node from PB info {}", new Object[]{selfAddress.getId(), ipb.getInfoTarget()});
                            membershipList.add(ipb.getInfoTarget());
                            updatesDisseminationCounter.put(new InfoPiggyback(InfoType.NEWNODE,ipb.getInfoTarget()), DISSEMINATION_VALUE);
                        }
                    } else if (ipb.getInfoType()==InfoType.DEADNODE){
                        if(membershipList.contains(ipb.getInfoTarget())){
                            //log.info("Node {} is removing node {} since thinks it is DEAD", new Object[]{selfAddress.getId(), ipb.getInfoTarget().getId()});
                            membershipList.remove(ipb.getInfoTarget());
                            updatesDisseminationCounter.put(new InfoPiggyback(InfoType.DEADNODE,ipb.getInfoTarget()), DISSEMINATION_VALUE);
                        }
                    } else if (ipb.getInfoType()==InfoType.ALIVENODE){
                        if(membershipList.isSuspected(ipb.getInfoTarget())){
                            membershipList.unsuspectNode(ipb.getInfoTarget());
                            updatesDisseminationCounter.put(new InfoPiggyback(InfoType.ALIVENODE,ipb.getInfoTarget()), DISSEMINATION_VALUE);
                            if(deadTimeoutIds.containsKey(ipb.getInfoTarget())){
                                cancelDeadTimeout(ipb.getInfoTarget());
                            }
                        }
                    } else if (ipb.getInfoType()==InfoType.SUSPECTEDNODE){
                        if(ipb.getInfoTarget().getId()==selfAddress.getId()){
                            updatesDisseminationCounter.put(new InfoPiggyback(InfoType.ALIVENODE,selfAddress), DISSEMINATION_VALUE);
                       
                        } else if (membershipList.contains(ipb.getInfoTarget()) && !membershipList.isSuspected(ipb.getInfoTarget())){
                            
//NOTE: should I add the node in suspected mode in this case?
                            
                            membershipList.suspectNode(ipb.getInfoTarget());
                            //check that there is no timeout associated to the event.getAddress already, otherwise the prev UUID is lost
                            if(!deadTimeoutIds.containsKey(ipb.getInfoTarget()))
                                scheduleDeadTimeout(ipb.getInfoTarget());
                            
                            updatesDisseminationCounter.put(new InfoPiggyback(InfoType.SUSPECTEDNODE,ipb.getInfoTarget()), DISSEMINATION_VALUE);
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
            
            if(membershipList.isEmpty()){
                cancelPeriodicPing();
                return;
            } else {
                if(pingTimeoutId==null){
                    schedulePeriodicPing();
                }
            }
            

            NatedAddress partnerAddress = membershipList.randomNode();
            
            //check if still waiting for an answer from the node
            if(!ackTimeoutIds.containsKey(partnerAddress)){
                log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});
                scheduleAck(partnerAddress);
                trigger(new NetPing(selfAddress, partnerAddress), network);
            } else {
                log.info("{} FAIL sending ping to partner (waiting for ack):{}", new Object[]{selfAddress.getId(), partnerAddress});
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
            
            cancelAck(event.getAddress());
            
            if(membershipList.isSuspected(event.getAddress())){
                //do nothing
            } else {
                membershipList.suspectNode(event.getAddress());
                log.info("{}  suspects node: {}", new Object[]{selfAddress.getId(), event.getAddress().getId()});
                updatesDisseminationCounter.put(new InfoPiggyback(InfoType.SUSPECTEDNODE, event.getAddress()),DISSEMINATION_VALUE);
                
                //check that there is no timeout associated to the event.getAddress already, otherwise the prev UUID is lost
                if(!deadTimeoutIds.containsKey(event.getAddress()))
                    scheduleDeadTimeout(event.getAddress());
            }
        }
    };
    
    private Handler<DeadTimeout> handleDeadTimeout = new Handler<DeadTimeout>() {
        
        @Override
        public void handle(DeadTimeout event) {
            printmyneigh();
            if(deadTimeoutIds.containsKey(event.getAddress()))
                cancelDeadTimeout(event.getAddress());
            
            if(membershipList.isSuspected(event.getAddress())){
                updatesDisseminationCounter.put(new InfoPiggyback(InfoType.DEADNODE, event.getAddress()),DISSEMINATION_VALUE);
                if(membershipList.contains(event.getAddress())){
                    log.info("Node {} is removing node {} since thinks it is DEAD", new Object[]{selfAddress.getId(), event.getAddress().getId()});
                    membershipList.remove(event.getAddress());
                }
                membershipList.unsuspectNode(event.getAddress());
            }
            
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
        ScheduleTimeout spt = new ScheduleTimeout(5000);
        AckTimeout sc = new AckTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        ackTimeoutIds.put(address,sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void cancelAck(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(ackTimeoutIds.get(address));
        trigger(cpt, timer);
        ackTimeoutIds.remove(address);
    }
    
    private void scheduleDeadTimeout(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(10000);
        DeadTimeout sc = new DeadTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        deadTimeoutIds.put(address,sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void cancelDeadTimeout(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(deadTimeoutIds.get(address));
        trigger(cpt, timer);
        deadTimeoutIds.remove(address);
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
    
    private static class DeadTimeout extends Timeout {
        
        private final NatedAddress address;
        
        public DeadTimeout(ScheduleTimeout request, NatedAddress address) {
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
    

    private void printmyneigh() {
            membershipList.printNeighbour();
    }
}
