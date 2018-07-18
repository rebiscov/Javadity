package blockchain;

import blockchain.types.*;

public final class Block {
   //@ public model \locset footprint;
   //@ represents footprint = \set_union(this.*, \set_union(coinbase.footprint, \set_union(difficulty.footprint, \set_union(gaslimit.footprint, \set_union(number.footprint, timestamp.footprint)))));
   //@ public accessible \inv: footprint;

	
  /*@ public axiom \invariant_for(coinbase); @*/
  public Address coinbase;     // current block miner’s address

  /*@ public axiom \invariant_for(difficulty); @*/
  public Uint256 difficulty;   // current block difficulty
  
  /*@ public axiom \invariant_for(gaslimit); @*/
  public Uint256 gaslimit;     // current block gaslimit
  
  /*@ public axiom \invariant_for(number); @*/
  public Uint256 number;       // current block number
  
  /*@ public axiom \invariant_for(timestamp); @*/
  public Uint256 timestamp;    // current block timestamp as seconds since unix epoch


  
  public byte[] blockhash(Uint256 blockNumber) {
    // returns hash (byte array of size 32) of the given block - only works for 256 most recent blocks excluding current
    return new byte[10]; // TODO(raul): return an appropriate value
  }
}
