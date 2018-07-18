package blockchain.types;

import blockchain.Message;

public class Address {

  //@ public model \locset footprint = \set_union(\singleton(this.balance.footprint), \singleton(this.address));
  // public accessible \inv: footprint;

 //@ public invariant (\forall Address a; a.address.length == this.address.length);
 //@ public invariant (\forall Address a; a.address == this.address; a == this);
 public final byte[] address;

  //@ public invariant \invariant_for(balance);
  public Uint256 balance;


  public Address(byte[] address) {
    this.address = address;
    balance = Uint256.ZERO; // Initially the account starts with 0 wei
  }

  public Address() {
    this.address = new byte[0];
    balance = Uint256.ZERO;
  }

  /*@ public normal_behavior
    @ requires price._value <= this.balance._value;
    @ requires sender != this;
    @ ensures this.balance._value == \old(this.balance.sum(price)._value);
    @ ensures sender.balance._value == \old(sender.balance.sub(price)._value);
    @ assignable this.balance, sender.balance;
    @
    @ also
    @
	@ public normal_behavior
    @ requires price._value <= this.balance._value;
    @ requires sender == this;
    @ ensures this.balance._value == \old(this.balance._value);
    @ assignable this.balance;
    @
    @ also
    @
    @ public exceptional_behavior
    @ requires price._value > this.balance._value;
    @ signals (Exception) true;
    @ assignable \nothing;
    @*/
  public void transfer(Address sender, Uint256 price) throws Exception {
    // NOTE(raul): This function should be executed by the receiver of the money
    // who withdraws `price` from the address specified in `sender`. Just to
    // clarify, the receiver of the money will be the object executing the
    // function.

    // TODO: Discuss whether the following two if blocks are enough to determine
    // that the transfer can be carried out.

    // If the address sending the money does not have enough funds, throw an
    // exception. Sending 0 ether from a contract with 0 balance is allowed.
    if (!price.leq(this.balance)) {
      throwException();
    }

    // Deduct `price` from sender's account
    sender.balance = sender.balance.sub(price);

    // Add `price` to receiver's account
    this.balance = this.balance.sum(price);
  }


  /*@ public normal_behavior
    @ requires \invariant_for(price) && \invariant_for(sender);
    @ requires price._value <= this.balance._value;
    @ requires sender != this;
    @ ensures this.balance._value == \old(this.balance.sum(price)._value);
    @ ensures sender.balance._value == \old(sender.balance.sub(price)._value);
    @ ensures \result;
    @ assignable this.balance, sender.balance;
    @
    @ also
    @
	@ public normal_behavior
    @ requires \invariant_for(price) && \invariant_for(sender);
    @ requires price._value <= this.balance._value;
    @ requires sender == this;
    @ ensures this.balance._value == \old(this.balance._value);
    @ ensures \result;
    @ assignable this.balance;
    @
    @ also
    @
    @ public normal_behavior
    @ requires \invariant_for(price) && \invariant_for(sender);
    @ requires price._value > this.balance._value;
    @ ensures !\result;
    @ assignable \strictly_nothing;
    @*/
  public boolean send(Address sender, Uint256 price) throws Exception {
    // TODO(raul): ask about exceptions, in this method we need to throw an
    // exception because of the mathematical operations, but not because the
    // call will throw any exception.

    // returns true if sending succeeds, otherwise it returns false
    // No exception is thrown, and Ether consumption is not reverted.

    if (!price.leq(this.balance)) {
      return false;
    }

    // Deduct `price` from sender's account
    sender.balance = sender.balance.sub(price);

    // Add `price` to receiver's account
    this.balance = this.balance.sum(price);

    // Everything went fine
    return true;
  }

  /*@ public normal_behavior
    @ requires \invariant_for(a);
    @ ensures \result == (a.address.length == address.length &&
    @ 	(\forall int i;0<=i && i < address.length; address[i] == a.address[i]));
    @ accessible this.address, this.address[*], a.address, a.address[*];
    @ assignable \strictly_nothing;
    @*/
  public /*@ strictly_pure @*/ boolean eq(Address a) {
      if (a.address.length != address.length) {
        return false;
      }
      boolean result = true;  // TODO: what if a.address is longer than this.address

      /*@ loop_invariant i>=0 && i<= this.address.length &&
      @    \result == (\forall int j;0<=j && j < i; address[j] == a.address[j]);
      @ assignable \strictly_nothing;
      @ decreases address.length - i;
      @*/
      for (int i = 0; i < this.address.length ; i++) {
        result = result && (address[i] == a.address[i]); // result = result && (address[i] == a[i])
        if (!result) return false;
      }
      return result;
    }

  // Auxiliary method that throws an exception
  // (the transaction will be aborted in the catch statement of the original call)
  private void throwException() throws Exception { //TransferFailedException {
    // Update: We abort the transaction in the catch statement of original call
    // method
    throw new Exception();//TransferFailedException();
  }


  // please re-check to see if it is correct
  //The payable modifier automatically transfers the amount of money in
  // `msg.value` to  the contract that has been called by msg.sender.
  /*@ public normal_behavior
    @ requires \invariant_for(msg);
    @ requires msg.value._value <= this.balance._value;
    @ requires msg.sender != this;
    @ ensures this.balance._value == \old(this.balance.sum(msg.value)._value);
    @ ensures msg.sender.balance._value == \old(msg.sender.balance.sub(msg.value)._value);
    @ assignable this.balance, msg.sender.balance;
    @
    @ also
    @
	@ public normal_behavior
    @ requires \invariant_for(msg);
    @ requires msg.value._value <= this.balance._value;
    @ requires msg.sender == this;
    @ ensures this.balance._value == \old(this.balance._value);
    @ assignable this.balance;
    @
    @ also
    @
    @ public exceptional_behavior
    @ requires \invariant_for(msg);
    @ requires msg.value._value > this.balance._value;
    @ signals (Exception) true;
    @ assignable \nothing;
    @*/
  public void payable(Message msg) throws Exception {
    this.transfer(msg.sender, msg.value);
  }
}
