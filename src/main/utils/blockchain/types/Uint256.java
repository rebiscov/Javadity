package blockchain.types;

import java.math.BigInteger;

/**
 * The current specification models Uint256 faithfully (with overflow). 
 * If we want to verify that a contract has no operation that will overflow we
 * can amend the contracts to detect overflows and to throw exceptions.
 * 
 * Idea: introduce static boolean ghost field like ALLOW_OVERFLOW and make precodition depend on its value.
 * 
 */
public interface Uint256 {
	
	// public static final ghost \bigint MAX = (\bigint)115792089237316195423570985008687907853269984665640564039457584007913129639935;

    //@ public instance ghost \locset footprint;
	//@ public instance invariant footprint == \singleton(_value);

	//@ public instance accessible \inv: footprint;

	// one for each uint256 used in sOlidity contract
	//@ public static invariant ZERO._value == 0 && \invariant_for(ZERO);
	public static final Uint256 ZERO = new Uint256Int(0);
	//@ public static invariant ONE._value == 1 && \invariant_for(ONE);
	public static final Uint256 ONE = new Uint256Int(1);
	//@ public static invariant TWO._value == 2 && \invariant_for(TWO);
	public static final Uint256 TWO = new Uint256Int(2);


	/* public static invariant 
        (\forall Uint256 x; x._value >= 0 && x._value <= \dl_MAXUINT256()); @*/
	//@ public instance invariant _value >= 0 && _value <= \dl_MAXUINT256();
	//@ public final instance ghost \bigint _value;
	

	/*@  public normal_behavior
	  @  ensures \result._value == \dl_keccak256(this._value);
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  accessible footprint;
	  @  assignable \nothing;
	  @*/
	Uint256 keccak256();
	
	/*@  public normal_behavior
	  @  ensures \result._value == \dl_sha3(this._value);
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  accessible footprint;
	  @  assignable \nothing;
	  @*/
	Uint256 sha3();

	/*@  public normal_behavior
	  @  ensures \result == (this._value <= value._value);
	  @  accessible footprint;
	  @  assignable \strictly_nothing;
	  @*/
	boolean leq(Uint256 value);
	
	/*@  public normal_behavior
	  @  ensures \result == (this._value >= value._value);
	  @  accessible footprint;
	  @  assignable \strictly_nothing;
	  @*/
	boolean geq(Uint256 value);

	/*@  public normal_behavior
	  @  ensures \result == (this._value > value._value);
	  @  accessible footprint;
	  @  assignable \strictly_nothing;
	  @*/
	boolean gr(Uint256 value);

	/*@  public normal_behavior
	  @  ensures \result == (this._value <= value._value);
	  @  accessible footprint;
	  @  assignable \strictly_nothing;
	  @*/
	boolean le(Uint256 value);

	/*@  public normal_behavior
	  @  ensures \result == (this._value == value._value);
	  @  accessible footprint;
	  @  assignable \strictly_nothing;
	  @*/
	boolean eq(Uint256 value);

	/*@  public normal_behavior
	  @  requires \invariant_for(value);
	  @  requires value._value > 0;
	  @  ensures \result._value == (this._value % value._value); // TODO: check if % really matches modulo in Uint256
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  accessible footprint;
	  @  assignable \nothing;
	  @*/
	Uint256 mod(Uint256 value) throws Exception;

	/*@  public normal_behavior
	  @  requires \invariant_for(value);
	  @  requires value._value > 0;
	  @  ensures \result._value == (this._value / value._value); // TODO: check if / really matches div in Uint256
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  accessible footprint;
	  @  assignable \nothing;
	  @*/
	Uint256 div(Uint256 value) throws Exception;

	/*@  public normal_behavior
	  @  requires \invariant_for(value);
	  @  ensures \result._value == (this._value * value._value) % (\dl_MAXUINT256() + 1);
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  assignable \nothing;
	  @*/
	Uint256 mul(Uint256 value) throws Exception;

	/*@  public normal_behavior
	  @  requires \invariant_for(value);
	  @  ensures \result._value == (this._value >= value._value ? (\bigint) 0 : \dl_MAXUINT256() + (\bigint)1) + (this._value - value._value);
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  assignable \nothing;
	  @*/
	Uint256 sub(Uint256 value) throws Exception;

	/*@  public normal_behavior
	  @  requires \invariant_for(value);
	  @  ensures \result._value == (this._value + value._value > \dl_MAXUINT256() ? ((\bigint)-1)*\dl_MAXUINT256() - 1 : (\bigint) 0) + this._value + value._value; 
	  @  ensures \invariant_for(\result) && \fresh(\result);
	  @  assignable \nothing;
	  @*/
	Uint256 sum(Uint256 value) throws Exception;
	
	/*@ public normal_behavior
	  @ requires _value >= 0 && _value<=Integer.MAX_VALUE;
	  @ ensures \result == _value;
	  @ assignable \strictly_nothing;
	  @ 
	  @ also
	  @ 
	  @ public exceptional_behavior
	  @ requires _value < 0 && _value > Integer.MAX_VALUE;
	  @ signals (ArithmeticException) true;
	  @ assignable \nothing;
	  @*/
	int asInt();
	
	// not specified as we do not want to specify class BigInteger
	// Our solidity contract verification should not depend on
	// classes implementing this Uint256 interface.
	BigInteger asBigInteger();

	/*@ public normal_behavior
	  @ requires i >= 0;
	  @ ensures \result._value == i;
	  @ ensures \invariant_for(\result) && \fresh(\result);
	  @ assignable \nothing;
	  @ 
	  @ also
	  @ 
	  @ public exceptional_behavior
	  @ requires i < 0;
	  @ signals (ArithmeticException) true;
	  @ assignable \nothing;
	  @*/
	Uint256 valueOf(int i);

}
