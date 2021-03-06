/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javachat.server;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import JavaChat.server.ServerClient;
import JavaChat.server.UniqueIdentifier;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Scanner;
import java.util.UUID;

/**
 *
 * @author Home
 */
public class Server  {
    private List<ServerClient>  clients = new ArrayList<ServerClient>();
    private List<Integer> clientResponse=new ArrayList<>();
    private int port;
    private DatagramSocket socket;
    private boolean running=false;
    private Thread run,manage,send,receive;
    private boolean raw=false;
    private final int MAX_ATTEMPTS=5;
 
    public Server(int port) {
      
        this.port = port;
        try {
            
            socket=new DatagramSocket(port);
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        run=new Thread("Server"){
    @Override
    public void run(){
        running=true;
        System.out.println("server started on port "+port);
        manageClients();
        receive();
         Scanner sc=new Scanner(System.in);
        while(running){
           String text=sc.nextLine();
           if(!text.startsWith("/")){
               sendToAll("/m/Server: "+text+"/e/");
               continue;
           }
           text=text.substring(1);
           if(text.startsWith("raw")){
               raw=!raw;
           }else if(text.equals("clients")){
               System.out.println("Clients: ");
               System.out.println("==========");
               for(int i=0;i<clients.size();i++){
                   ServerClient c=clients.get(i);
                   System.out.println(c.name+" ("+c.getID()+"): "+c.address.toString()+": "+c.port);
               }
           }else if(text.startsWith("kick")){
               // /kick yan
               String name=text.split(" ")[1];
               int id=-1;
               boolean num=false;
               try{
                   id=Integer.parseInt(text);
                   num=true;
               }catch(NumberFormatException e){
                   num=false;
               }
               if(num){
                   boolean exists=false;
                   for(int i=0;i<clients.size();i++){
                       if(clients.get(i).getID()==id){
                           exists=true;
                           break;
                       }
                   }
                   if(exists){
                       disconnect(id,true);
                   }
                   else{
                       System.out.println("Client "+id+" doesn't exist. Check id number");
                   }
               }else{
                    for(int i=0;i<clients.size();i++){
                       ServerClient c=clients.get(i);
                       if(name.equals(c.name)){
                           disconnect(c.getID(),true);
                           break;
                       }
                       
                   }
               }
               
           }
            
        }
        
    }
        };
        run.start();
    }
       
    //it manages the clients
    private void manageClients(){
        manage=new Thread("Manage"){
            @Override
            public void run(){
                while(running){
                  
                        sendToAll("/i/server");
                        setStatus();
                        try {
                                 Thread.sleep(2000);
                         } catch (InterruptedException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                        for(int i=0;i<clients.size();i++){
                            ServerClient c=clients.get(i);
                            if(!clientResponse.contains(c.getID()))
                            {
                                if(c.attempt>=MAX_ATTEMPTS){
                                    disconnect(c.getID(),false);
                                }
                                else{
                                    c.attempt++;
                                }
                            }else{
                                clientResponse.remove(new Integer(c.getID()));
                                c.attempt=0;
                            }
                        }  
            }
            }
        };
        manage.start();
    }
    private void setStatus(){
        if(clients.size()<=0)return;
        String users="/u/";
        for(int i=0;i<clients.size()-1;i++){
            users+=clients.get(i).name+"/n/";
        }
        users+=clients.get(clients.size()-1).name+"/e/";
        sendToAll(users);
    }
    
    private void receive(){
        receive=new Thread("Receive"){
            @Override
            public void run(){
                while(running){
                    //receive
                  //  System.out.println(clients.size());
                        byte[] data=new byte[1024];
                        DatagramPacket packet=new DatagramPacket(data,data.length);
                        try {
                        socket.receive(packet);
                    } catch (IOException ex) {
                        Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                    }
                        String string=new String(packet.getData());
                        process(packet);
                     // clients.add(new ServerClient("Yan",packet.getAddress(),packet.getPort(),50));
                     // System.out.println(clients.get(0).address.toString()+":"+clients.get(0).port);

                     
                        
                        
                }
            }
        };
        receive.start();
    }
    private void sendToAll(String message){
        if(message.startsWith("/m/")){
         String text= message.substring(3);
             text= text.split("/e/")[0];
             System.out.println(message);}
       
        for(int i=0;i<clients.size();i++){
            ServerClient client=clients.get(i);
            send(message.getBytes(),client.address,client.port);
        }
    }
    private void send(final byte[] data,final InetAddress address,final int port){
        send=new Thread("Send"){
            @Override
            public void run(){
               
                    DatagramPacket packet=new DatagramPacket(data,data.length,address,port);
                     try {
                         socket.send(packet);
                     } catch (IOException ex) {
                    Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
                      }
        }
        };
        send.start();
        
    }
    private void send(String message,InetAddress address,int port){
        message+="/e/";
        send(message.getBytes(),address,port);
        
    }
    private void process(DatagramPacket packet){
        String string=new String(packet.getData());
         if(raw) System.out.println(string);
        if(string.startsWith("/c/")){
           // UUID id=UUID.randomUUID();
           //id.toString();
            int id=UniqueIdentifier.getIdentifier();
            System.out.println("Identifier : "+id);
            String name=string.split("/c/|/e/")[1];
            System.out.println(name+"("+id+") connected");
            clients.add(new ServerClient(name,packet.getAddress(),packet.getPort(),id));
            String ID="/c/"+id;
            send(ID,packet.getAddress(),packet.getPort());
        }else if(string.startsWith("/m/")){
            String message=string.substring(3,string.length());
            sendToAll(string);
        }
      else if(string.startsWith("/d/")){
            String id=string.split("/d/|/e/")[1];
            disconnect(Integer.parseInt(id),true);
        }
      else if(string.startsWith("/i/")){
          clientResponse.add(Integer.parseInt(string.split("/i/|/e/")[1]));
      }
        
        else{
            System.out.println(string);
        }
    }
    private void disconnect(int id,boolean status){
        ServerClient c=null;
        boolean existed=false;
        for(int i=0;i<clients.size();i++){
            if(clients.get(i).getID()==id){
                c=clients.get(i);
                clients.remove(i);
                existed=true;
                break;
            }
            
        }
        if(!existed)return;
        String message="";
        if(status){
            message="Client "+c.name+" ("+c.getID()+") @"+c.address.toString()+":"+c.port+" disconnected";
        }else{
            message="Client "+c.name+" ("+c.getID()+") @"+c.address.toString()+":"+c.port+" timed out";
        }
        System.out.println(message);
    }
    
    
}
