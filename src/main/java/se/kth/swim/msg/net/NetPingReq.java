/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.swim.msg.net;

import se.kth.swim.msg.Ping;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

public class NetPingReq extends NetMsg<Ping> {
    
    private NatedAddress neighbourToPing;

    public NetPingReq(NatedAddress src, NatedAddress dst, NatedAddress toPing) {
        super(src, dst, new Ping());
        neighbourToPing=toPing;
    }

    private NetPingReq(Header<NatedAddress> header, Ping content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetPingReq(newHeader, getContent());
    }
    
    public NatedAddress getToPing(){
        return neighbourToPing;
    }
    
}
