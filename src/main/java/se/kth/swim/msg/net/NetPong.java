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
package se.kth.swim.msg.net;


import java.util.LinkedList;
import java.util.List;
import se.kth.swim.msg.Ping;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;


public class NetPong extends NetMsg<Ping> {
    
    private LinkedList<InfoPiggyback> updates;

    public NetPong(NatedAddress src, NatedAddress dst) {
        super(src, dst, new Ping());
    }

    private NetPong(Header<NatedAddress> header, Ping content) {
        super(header, content);
    }

    public NetPong(NatedAddress src, NatedAddress dst, LinkedList<InfoPiggyback> updates) {
        super(src, dst, new Ping());
        this.updates= new LinkedList<InfoPiggyback>(updates);
    }
    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetPong(newHeader, getContent());
    }

    /**
     * @return the updates
     */
    public LinkedList<InfoPiggyback> getUpdates() {
        return updates;
    }

    /**
     * @param updates the updates to set
     */
    public void setUpdates(LinkedList<InfoPiggyback> updates) {
        this.updates = updates;
    }

}
