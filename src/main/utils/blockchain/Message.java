package blockchain;

import blockchain.types.*;

public final class Message {
	//@ public model \locset footprint;
	//@ represents footprint = \set_union(this.*, \set_union(sender.footprint, \set_union(gas.footprint, value.footprint)));
	//@ public accessible \inv: footprint;

	//@ public invariant \invariant_for(sender);
	public Address sender; // sender of the message (current call)
	//@ public invariant \invariant_for(gas);
	public Uint256 gas;
	// remaining gas

	//@ public invariant data.length == 10;
	public byte[] data = new byte[10]; // complete calldata
	// TODO: find out the correct size of the
	// array

	//@ public invariant \invariant_for(value);
	public  Uint256 value;              // amount of wei sent with the message
}
