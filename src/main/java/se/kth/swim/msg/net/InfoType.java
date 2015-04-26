/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.kth.swim.msg.net;

import java.io.Serializable;

/**
 *
 * @author fabriziodemaria
 */
public enum InfoType implements Serializable {
      NEWNODE,
      SUSPECTEDNODE,
      DEADNODE
}
