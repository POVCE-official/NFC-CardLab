package cn.cardlab;
import org.junit.Test;
import static org.junit.Assert.*;
public class BytesTest {
    @Test public void hexRoundTrip() { assertEquals("0080FF",Bytes.hex(Bytes.hex("00 80 ff"))); }
    @Test(expected=IllegalArgumentException.class) public void rejectOddHex() { Bytes.hex("123"); }
    @Test(expected=IllegalArgumentException.class) public void rejectInvalidHex() { Bytes.hex("GG"); }
    @Test public void decodeUtf8Ndef() { assertEquals("hello",Bytes.ndefText(new byte[]{2,'e','n','h','e','l','l','o'})); }
    @Test(expected=IllegalArgumentException.class) public void rejectTruncatedNdef() { Bytes.ndefText(new byte[]{10,'e'}); }
    @Test(expected=IllegalArgumentException.class) public void rejectShortApdu() { Bytes.apdu("00A4"); }
}
