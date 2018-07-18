package blockchain;

import blockchain.types.*;

public final class Transaction {
	//@ public model \locset footprint;
	//@ represents footprint = \set_union(origin.footprint, \set_union(gasprice.footprint, \set_union(\singleton(this.gasprice), \singleton(this.origin))));
	//@ public accessible \inv: footprint;

  //@ public invariant \invariant_for(gasprice);
  Uint256 gasprice;   // gas price of the transaction
  //@ public invariant \invariant_for(origin);
  Address origin;     // sender of the transaction (full call chain)

  public Transaction(Uint256 _gasprice, Address _origin) {
    gasprice = _gasprice;
    origin = _origin;
  }
}
