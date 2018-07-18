package blockchain.types;

import java.math.BigInteger;

public class Uint256BigInteger implements Uint256 {
	public final BigInteger MAX = new BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935"); // 2^256 - 1
	public final BigInteger MIN = BigInteger.ZERO; // 0
	public final BigInteger ZERO = BigInteger.ZERO; // 0

	private final BigInteger number;

	/**
	 * @param value String representing the value
	 *
	 */
	public Uint256BigInteger(String value) throws Exception {
		BigInteger bigValue = new BigInteger(value);
		if ((bigValue.compareTo(MAX) == 1) || (bigValue.compareTo(BigInteger.ZERO) == -1)) {
			throw new Exception();
		}
		this.number = new BigInteger(value);
	}

	public Uint256 sum(Uint256 value) throws Exception {
		BigInteger temp = this.number.add(((Uint256BigInteger) value).asBigInteger());
		return new Uint256BigInteger(temp.toString());
	}

	public Uint256 sub(Uint256 value) throws Exception {
		BigInteger temp = this.number.subtract(((Uint256BigInteger) value).asBigInteger());
		return new Uint256BigInteger(temp.toString());
	}

	public Uint256 mul(Uint256 value) throws Exception {
		BigInteger temp = this.number.multiply(((Uint256BigInteger) value).asBigInteger());
		return new Uint256BigInteger(temp.toString());
	}

	public Uint256 div(Uint256 value) throws Exception {
		BigInteger temp = this.number.divide(((Uint256BigInteger) value).asBigInteger());
		return new Uint256BigInteger(temp.toString());
	}

	public Uint256 mod(Uint256 value) throws Exception {
		BigInteger temp = this.number.mod(((Uint256BigInteger) value).asBigInteger());
		return new Uint256BigInteger(temp.toString());
	}

	// this == value
	public /*@ pure @*/ boolean eq(Uint256 value) {
		return this.number.compareTo(((Uint256BigInteger) value).asBigInteger()) == 0; // 0: equal;
	}

	// this < value
	public /*@ pure @*/ boolean le(Uint256 value) {
		return this.number.compareTo(((Uint256BigInteger) value).asBigInteger()) == -1; // 1: greater;
	}

	// this > value
	public /*@ pure @*/ boolean gr(Uint256 value) {
		return this.number.compareTo(((Uint256BigInteger) value).asBigInteger()) == 1; // -1: less;
	}

	// this >= value
	public /*@ pure @*/ boolean geq(Uint256 value) {
		return this.number.compareTo(((Uint256BigInteger) value).asBigInteger()) >= 0; // 0: equal; 1: greater
	}

	// this <= value
	public /*@ pure @*/ boolean leq(Uint256 value) {
		return this.number.compareTo(((Uint256BigInteger) value).asBigInteger()) <= 0; // -1: less; 0: equal
	}

	// @override
	public String toString() {
		return number.toString();
	}


//	public static void main(String[] args) {
//		try {
//			Uint256BigInteger a = new Uint256BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935");
//			Uint256BigInteger b = new Uint256BigInteger("2");
//			System.out.println("a >= b: " + a.geq(b));
//
//			System.out.println(a.sum(b));
//		} catch(Exception e) {
//			System.err.println(e);
//		}
//	}

	@Override
	public int asInt() {
		return number.intValue();
	}

	@Override
	public BigInteger asBigInteger() {
		return number;
	}


	@Override
	public Uint256 valueOf(int i) {
		return new Uint256Int(i);
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
}
