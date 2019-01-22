/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rpi_energy_watch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Federico
 */
public class RPI_Energy_Watch {
    
    static EnergyTask energy = null;
    static boolean runFlag = true;
    static ServerSocket serversocket = null;
    static Socket socket = null;
    static InputStream in = null;
    static BufferedReader input = null;
    static PrintWriter output = null;
    
    public static void main(String[] args) throws IOException {
    
        if (args.length==0){
            energy = new EnergyTask();
        } else {
            energy = new EnergyTask(args[0]);
        }
        energy.start();
        
        //Start to listen on port 30001 for commands
        try {
            serversocket = new ServerSocket(30002);
        } catch (IOException ex) {
            Logger.getLogger(RPI_Energy_Watch.class.getName()).log(Level.SEVERE, null, ex);
        }
        // Loop until Kill Thread command received
        while(runFlag){
            try {
                waitRequest(); //Wait for command and process request.
            } catch (IOException ex) {
                Logger.getLogger(RPI_Energy_Watch.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        }    
        serversocket.close();
    }
    
    /**
     * This method wait for a connection, get the command.
     * @throws IOException 
     */
    private static void waitRequest() throws IOException {
       
        String request = "";
        String reply = "";
        socket = serversocket.accept();
        in = socket.getInputStream();
        input = new BufferedReader(new InputStreamReader(in));
        output = new PrintWriter(socket.getOutputStream(), true);
        request = input.readLine(); //Get Command
        reply = processRequest(request); //Process command
        output.println(reply);
        input.close();
        output.close();
    }
    
    /**
     * This method Process the command
     * @param request
     * @return 
     */
    private static String processRequest(String request){
        String reply="";
        String command="";
        int data=0;
        
        String parts[]=request.split(",");
        
        if(parts.length==1){
            command=request;
        }else{
            command=parts[0];
            data=Integer.parseInt(parts[1]);
        }
        
        switch(command){
            case "get status":
                reply=getStatus();
                break;
            
            case "reset monitor":
                reply=energy.reset_power();
                break; 
                
            case "kill thread":
                runFlag = false;
                reply=energy.killThread();
                break;   
                
            case "get log":
                reply=energy.getLogEvents();
                break;
                
               
            default:
                reply="invalid command";
        }
        return reply;
    }
    
    private static String getStatus(){
        return energy.getReport();
    }
    
    
    private static String killThread(){
        energy.killThread();
        runFlag=false;
        return "killed";
    }
    
}
