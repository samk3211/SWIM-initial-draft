/*
* To change this license header, choose License Headers in Project Properties.
* To change this template file, choose Tools | Templates
* and open the template in the editor.
*/

package se.kth.swim.msg.net;

import java.io.Serializable;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 *
 * @author fabriziodemaria
 */

public class InfoPiggyback implements Serializable {
    
    private InfoType infoType;
    private NatedAddress infoTarget;
    private Integer incarnationValue;
    
    public InfoPiggyback (InfoType t, NatedAddress n){
        this.infoType = t;
        this.infoTarget = n;
    }
    
    public InfoPiggyback(InfoType t, NatedAddress n, Integer i) {
        this.infoType = t;
        this.infoTarget = n;
        this.incarnationValue = i;
    }
    
    /**
     * @return the infoType
     */
    public InfoType getInfoType() {
        return infoType;
    }
    
    /**
     * @param infoType the infoType to set
     */
    public void setInfoType(InfoType infoType) {
        this.infoType = infoType;
    }
    
    /**
     * @return the infoTarget
     */
    public NatedAddress getInfoTarget() {
        return infoTarget;
    }
    
    /**
     * @param infoTarget the infoTarget to set
     */
    public void setInfoTarget(NatedAddress infoTarget) {
        this.infoTarget = infoTarget;
    }

    /**
     * @return the incarnationValue
     */
    public Integer getIncarnationValue() {
        return incarnationValue;
    }

    /**
     * @param incarnationValue the incarnationValue to set
     */
    public void setIncarnationValue(Integer incarnationValue) {
        this.incarnationValue = incarnationValue;
    }
    
    
    
}
