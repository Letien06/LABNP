package lab1.chat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Small dependency-free framing helper. Every TCP frame is one UTF-8 line. */
public final class Protocol {
  private Protocol() {}
  public static String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
  public static String dec(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
  public static String[] split(String line) { return line.split("\\|", -1); }
  public static String join(String... parts) { return String.join("|", parts); }
}
