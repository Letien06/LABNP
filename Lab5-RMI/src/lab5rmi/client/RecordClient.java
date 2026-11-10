package lab5rmi.client;
import lab5rmi.api.*;
import java.rmi.registry.*;
import java.util.*;
import java.io.*;
public final class RecordClient {
  public static void main(String[] args) throws Exception {
    String host=args.length>0?args[0]:"127.0.0.1"; int port=args.length>1?Integer.parseInt(args[1]):1099;
    RecordService s=(RecordService)LocateRegistry.getRegistry(host,port).lookup("RecordService");
    if(args.length>2){ run(s,Arrays.copyOfRange(args,2,args.length)); return; }
    System.out.println("Connected to "+s.info()+". Commands: list, get ID, create ID VALUE, update ID VALUE, delete ID, quit");
    try(Scanner sc=new Scanner(System.in)){ while(true){System.out.print("> "); if(!sc.hasNextLine()) break; String line=sc.nextLine().trim(); if(line.equalsIgnoreCase("quit")||line.equalsIgnoreCase("exit"))break; if(line.isBlank())continue; run(s,line.split("\\s+"));}}
  }
  private static void run(RecordService s,String[] a)throws Exception { String op=a[0].toLowerCase(Locale.ROOT); switch(op){
    case "list" -> s.list().forEach(System.out::println);
    case "get" -> System.out.println(s.get(a[1]));
    case "create","update" -> {String id=a[1], value=String.join(" ",Arrays.copyOfRange(a,2,a.length)); MutationResult r=op.equals("create")?s.create(UUID.randomUUID().toString(),id,value):s.update(UUID.randomUUID().toString(),id,value); System.out.println(r);}
    case "delete" -> System.out.println(s.delete(UUID.randomUUID().toString(),a[1]));
    default -> System.out.println("Unknown command"); }
  }
}
