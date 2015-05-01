/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package se.kth.swim;

import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 *
 * @author fabriziodemaria
 */

public final class MembershipList {
    
    private static final Logger log = LoggerFactory.getLogger(MembershipList.class);
    
    
    private final NatedAddress selfAddress;
    private final HashMap<NatedAddress,MemberInfo> neighboursNodes = new HashMap<NatedAddress,MemberInfo>();
    
    private Random rand = new Random();
    
    public MembershipList(Set<NatedAddress> addresses, NatedAddress selfAddress){
        
        this.selfAddress=selfAddress;
        for(NatedAddress na : addresses){
            add(na,false,0);
        }
    }
    
    //default if no info are provided
    public void add(NatedAddress n){
        neighboursNodes.put(n,new MemberInfo(false,0, n.getId()));
    }
    public void add(NatedAddress n, boolean suspected, Integer incarnationNumber){
        neighboursNodes.put(n,new MemberInfo(suspected,incarnationNumber, n.getId()));
    }
    
    public void remove(NatedAddress n){
        if(neighboursNodes.containsKey(n))
            neighboursNodes.remove(n);
    }
    
    public void remove(Integer id){
        if(lookById(id)!=null){
            neighboursNodes.remove(lookById(id));
        }
        
    }
    
    public boolean contains(NatedAddress na){
        return neighboursNodes.containsKey(na);
    }
    
    public boolean contains(Integer id){
        return lookById(id)!=null;
    }
    
    
    public boolean isSuspected(NatedAddress n){
        if(!neighboursNodes.keySet().contains(n)) {
            return false;
        }
        return neighboursNodes.get(n).getSuspected();
    }
    
    public boolean isSuspected(Integer id){
        if(lookById(id)!=null){
            return (neighboursNodes.get(lookById(id)).getSuspected());
        }
        return false;
    }
    
    public void suspectNode(NatedAddress n){
        if(neighboursNodes.containsKey(n)){
            neighboursNodes.get(n).setSuspected(true);
        } else {
            //cannot mark
        }
    }
    
    public void suspectNode(Integer id){
        if(lookById(id)!=null){
            neighboursNodes.get(lookById(id)).setSuspected(true);
        }
    }
    
    
    public void unsuspectNode(NatedAddress n){
        if(neighboursNodes.containsKey(n)){
            neighboursNodes.get(n).setSuspected(false);
        } else {
            //cannot mark
        }
    }
    
    public void unsuspectNode(Integer id){
        if(lookById(id)!=null){
            neighboursNodes.get(lookById(id)).setSuspected(false);
        }
    }
    
    public boolean isEmpty() {
        return neighboursNodes.isEmpty();
    }
    
    public int size() {
        return neighboursNodes.size();
    }
    
    public NatedAddress randomNode() {
        int randomNum = rand.nextInt(((size()-1) - 0) + 1) + 0;
        //TODO improve this
        return (NatedAddress) neighboursNodes.keySet().toArray()[randomNum];
    }
    
    
    void printNeighbour() {
        log.info("NNN " + neighboursNodes.size() );
        
        /*
        System.out.print("" + selfAddress.getId() + " xxxx");
            
            for(NatedAddress na : neighboursNodes.keySet()){
                System.out.print(" - " + na.getId());
            }
            System.out.println();
        */
    }
   
    /*
    
    
    SUPPORTING CODE
    
    
    */
    
    private NatedAddress lookById(Integer id){
        for(NatedAddress na : neighboursNodes.keySet()){
            if(na.getId()==id){
                return na;
            }
        }
        return null;
    }

    
    
    private static class MemberInfo {
        
        
        private boolean suspected;
        private Integer incarnationNumber;
        private Integer nodeID;
        
        public MemberInfo(boolean suspected, Integer incarnationNumber, Integer nodeID) {
            this.suspected=suspected;
            this.incarnationNumber=incarnationNumber;
            this.nodeID = nodeID;
        }
        
        /**
         * @return the suspected
         */
        public boolean isSuspected() {
            return suspected;
        }
        
        /**
         * @param suspected the suspected to set
         */
        public void setSuspected(boolean suspected) {
            this.suspected = suspected;
        }
        
        /**
         * @return the incarnationNumber
         */
        public Integer getIncarnationNumber() {
            return incarnationNumber;
        }
        
        /**
         * @param incarnationNumber the incarnationNumber to set
         */
        public void setIncarnationNumber(Integer incarnationNumber) {
            this.incarnationNumber = incarnationNumber;
        }
        
        /**
         * @return the nodeID
         */
        public Integer getNodeID() {
            return nodeID;
        }
        
        /**
         * @param nodeID the nodeID to set
         */
        public void setNodeID(Integer nodeID) {
            this.nodeID = nodeID;
        }
        
        private boolean getSuspected() {
            return this.suspected;
        }
        
        
    }
    
}
