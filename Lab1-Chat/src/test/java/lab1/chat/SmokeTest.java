package lab1.chat;
import java.nio.file.*;
public final class SmokeTest { public static void main(String[] args) throws Exception { if(!"hello".equals(Protocol.dec(Protocol.enc("hello")))) throw new AssertionError(); Path p=Path.of("test-temp"); Files.createDirectories(p); MessageStore s=new MessageStore(p); s.append("alice","MSG","hello"); if(s.history(1).isEmpty()) throw new AssertionError(); System.out.println("PASS protocol + file persistence"); } }
