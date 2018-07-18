package blockchain.types;

import java.math.BigInteger;

public class Uint256Int implements Uint256 {

	private final int number;

	/**
	 * @param value String representing the value
	 *
	 */
	public Uint256Int(int value) {
		this.number = value;
	}

	public Uint256Int() {
		this.number = 0;
	}


	/* public normal_behavior
    @ requires this.number + value.number >= 0;
    @ requires this.number + value.number <= MAX;
    @ ensures  \result = this.number + value.number;
    @*/
	@Override
	public Uint256Int sum(Uint256 value) throws Exception {
		return new Uint256Int(this.number + value.asInt());
	}


	/* public normal_behavior
    @ requires this.number - value.number >= 0;
    @ requires this.number - value.number <= MAX;
    @ ensures  \result = this.number - value.number;
    @*/
	@Override
	public Uint256Int sub(Uint256 value) throws Exception {
		return new Uint256Int(this.number - value.asInt());
	}

	/* public normal_behavior
    @ requires this.number * value.number >= 0;
    @ requires this.number * value.number <= MAX;
    @ ensures  \result = this.number * value.number;
    @*/
	@Override
	public Uint256Int mul(Uint256 value) throws Exception {
		return new Uint256Int(this.number * value.asInt());
	}

	/* public normal_behavior
    @ requires this.number / value.number >= 0;
    @ requires this.number / value.number <= MAX;
    @ ensures  \result = this.number / value.number;
    @*/
	// TODO: Take care of division by 0
	@Override
	public Uint256 div(Uint256 value) throws Exception {
		return new Uint256Int(this.number / value.asInt());
	}

	/* public normal_behavior
    @ requires this.number % value.number >= 0;
    @ requires this.number % value.number <= MAX;
    @ ensures  \result = this.number % value.number;
    @*/
	@Override
	public Uint256 mod(Uint256 value) throws Exception {
		return new Uint256Int(this.number % value.asInt());
	}

	// this == value
	@Override
	public /* pure @*/ boolean eq(Uint256 value) {
		return this.number == value.asInt();
	}

	// this < value
	@Override
	public /* pure @*/ boolean le(Uint256 value) {
		return this.number < value.asInt();
	}

	// this > value
	@Override
	public /* pure @*/ boolean gr(Uint256 value) {
		return this.number > value.asInt();
	}

	// this >= value
	@Override
	public /* pure @*/ boolean geq(Uint256 value) {
		return this.number >= value.asInt();
	}

	// this <= value
	@Override
	public /* pure @*/ boolean leq(Uint256 value) {
		return this.number <= value.asInt();
	}

	public static void main(String[] args) {
		// To be implemented
	}

	@Override
	public int asInt() {
		return number;
	}

	@Override
	public Uint256 keccak256() {
		// TODO: implement
		return Uint256.ZERO;
	}


	@Override
	public Uint256 sha3() {
		// TODO: implement
		return Uint256.ZERO;
	}

	@Override
	public BigInteger asBigInteger() {
		return BigInteger.valueOf(number);
	}

	@Override
	public Uint256 valueOf(int i) {
		return new Uint256Int(i);
	}

	// Auxiliary method for logging purposes
	@Override
	public String toString() {
		return (this.number+""); // Very dirty casting, but works for now
	}
}
